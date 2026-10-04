package negocio;

import java.time.LocalDateTime;
import java.util.ArrayList;

import datos.ArregloAulas;
import datos.ArregloSolicitudes;
import modelo.Aula;
import modelo.DatoInvalidoException;
import modelo.Matricula;
import modelo.Oferta;
import modelo.ReglaDominioException;
import modelo.Solicitud;

public final class Turnos {

    private Turnos() {
    }

    
    public static ArrayList<Solicitud> ordenPorPrioridad(Aula aula,
            ArregloSolicitudes solicitudes, ArrayList<Matricula> matriculas,
            LocalDateTime ahora) throws ReglaDominioException {
        solicitudes.vencerHabilitaciones(ahora);
        ArrayList<Solicitud> enCola = solicitudes.colaFavorable(aula);
        enCola.addAll(solicitudes.enDocumentacion(aula));
        enCola.addAll(solicitudes.habilitadas(aula));
        enCola.addAll(solicitudes.colaSinPago(aula));

        ArrayList<Solicitud> orden = new ArrayList<Solicitud>();
        for (Solicitud solicitud : enCola) {
            if (!tieneMatriculaVigente(solicitud, matriculas)) {
                orden.add(solicitud);
            }
        }
        return orden;
    }

    // Las habilitadas y las que están en documentación ya se están atendiendo: el siguiente es
    // la primera que espera en una cola y además tiene turno según las vacantes.
    public static Solicitud siguiente(Aula aula, ArregloSolicitudes solicitudes,
            ArrayList<Matricula> matriculas, LocalDateTime ahora) throws ReglaDominioException {
        int vacantes = Vacantes.calcular(aula, matriculas);
        ArrayList<Solicitud> orden = ordenPorPrioridad(aula, solicitudes, matriculas, ahora);
        for (int i = 0; i < vacantes && i < orden.size(); i++) {
            Solicitud solicitud = orden.get(i);
            if (solicitud.estaEnColaFavorable() || solicitud.estaEnColaSinPago()) {
                return solicitud;
            }
        }
        return null;
    }

    public static void exigirTurno(Solicitud solicitud, ArregloSolicitudes solicitudes,
            ArrayList<Matricula> matriculas, LocalDateTime ahora) throws ReglaDominioException {
        if (tieneMatriculaVigente(solicitud, matriculas)) {
            throw new ReglaDominioException(solicitud.getCodigo() + " ya tiene matrícula vigente.");
        }
        Aula aula = solicitud.getAula();
        int vacantes = Vacantes.calcular(aula, matriculas);
        if (vacantes <= 0) {
            throw new ReglaDominioException("No hay vacante disponible en " + aula.getNombre()
                    + ".");
        }

        ArrayList<Solicitud> orden = ordenPorPrioridad(aula, solicitudes, matriculas, ahora);
        if (hayTurno(solicitud, orden, vacantes)) {
            return;
        }

        Solicitud primera = orden.get(0);
        throw new ReglaDominioException("Antes corresponde " + primera.getCodigo() + " ("
                + primera.getAlumno().getNombreCompleto() + ") en " + aula.getNombre() + ".");
    }

    private static boolean hayTurno(Solicitud solicitud, ArrayList<Solicitud> orden,
            int vacantes) {
        int posicion = orden.indexOf(solicitud);
        if (posicion >= 0) {
            return posicion < vacantes;
        }
        return orden.size() < vacantes;
    }

    // Revisión a demanda, sin temporizador (CLAUDE.md): procesa los vencimientos y habilita a la
    // cola sin pago que tiene turno en cada aula. Se llama al abrir la app, al mostrar Inicio o
    // Colas, con el botón «Revisar turnos» y después de liberar una vacante (cancelar o anular
    // una matrícula). Devuelve las habilitadas para que el colegio les avise por teléfono.
    public static ArrayList<Solicitud> revisarTurnos(ArregloAulas aulas,
            ArregloSolicitudes solicitudes, ArrayList<Matricula> matriculas, LocalDateTime ahora)
            throws ReglaDominioException {
        ArrayList<Solicitud> habilitadas = new ArrayList<Solicitud>();
        for (Aula aula : aulas.listar()) {
            habilitadas.addAll(habilitarConTurno(aula, solicitudes, matriculas, ahora));
        }
        return habilitadas;
    }

    // Habilita en orden a la cola sin pago que tiene turno en el aula; las favorables no se
    // tocan (reciben oferta con confirmarOferta). Debe llamarse cuando se libera una vacante:
    // revisarTurnos lo hace para todas las aulas. Devuelve las habilitadas para avisarles por
    // teléfono.
    public static ArrayList<Solicitud> habilitarConTurno(Aula aula,
            ArregloSolicitudes solicitudes, ArrayList<Matricula> matriculas, LocalDateTime ahora)
            throws ReglaDominioException {
        ArrayList<Solicitud> habilitadas = new ArrayList<Solicitud>();
        int vacantes = Vacantes.calcular(aula, matriculas);
        ArrayList<Solicitud> orden = ordenPorPrioridad(aula, solicitudes, matriculas, ahora);
        for (int i = 0; i < vacantes && i < orden.size(); i++) {
            Solicitud solicitud = orden.get(i);
            if (solicitud.estaEnColaSinPago()) {
                solicitud.habilitarParaPago(ahora);
                habilitadas.add(solicitud);
            }
        }
        return habilitadas;
    }

    public static ArrayList<Solicitud> ubicarNueva(Solicitud nueva,
            ArregloSolicitudes solicitudes, ArrayList<Matricula> matriculas, LocalDateTime fecha)
            throws ReglaDominioException {
        ArrayList<Solicitud> promovidas = habilitarConTurno(nueva.getAula(), solicitudes,
                matriculas, fecha);
        int vacantes = Vacantes.calcular(nueva.getAula(), matriculas);
        ArrayList<Solicitud> orden = ordenPorPrioridad(nueva.getAula(), solicitudes, matriculas,
                fecha);
        if (vacantes > 0 && !tieneMatriculaVigente(nueva, matriculas)
                && hayTurno(nueva, orden, vacantes)) {
            nueva.habilitarParaPago(fecha);
        } else {
            nueva.ingresarAColaSinPago(fecha);
        }
        return promovidas;
    }

    private static boolean tieneMatriculaVigente(Solicitud solicitud,
            ArrayList<Matricula> matriculas) {
        for (Matricula matricula : matriculas) {
            if (matricula.getSolicitud() == solicitud && matricula.estaVigente()) {
                return true;
            }
        }
        return false;
    }

    public static Oferta confirmarOferta(Solicitud solicitud, boolean aceptada, String personal,
            LocalDateTime fechaHora, ArregloSolicitudes solicitudes,
            ArrayList<Matricula> matriculas) throws ReglaDominioException {
        // Antes de exigirTurno, que ya usa la fecha para procesar vencimientos.
        if (fechaHora == null) {
            throw new DatoInvalidoException("La fecha y hora de la oferta es obligatoria.");
        }
        if (!solicitud.estaEnColaFavorable()) {
            throw new ReglaDominioException("Solo se ofrece vacante a solicitudes de la cola"
                    + " favorable; " + solicitud.getCodigo() + " está en "
                    + solicitud.getEstado() + ".");
        }
        if (solicitud.tieneOfertaAceptada()) {
            throw new ReglaDominioException(solicitud.getCodigo() + " ya aceptó una oferta.");
        }
        exigirTurno(solicitud, solicitudes, matriculas, fechaHora);

        Oferta oferta = new Oferta(fechaHora, personal, aceptada);
        solicitud.registrarOferta(oferta);
        if (!aceptada) {
            solicitud.ingresarAColaFavorable(fechaAlFinal(solicitud, fechaHora, solicitudes));
        }
        return oferta;
    }

    private static LocalDateTime fechaAlFinal(Solicitud rechazada, LocalDateTime fechaRechazo,
            ArregloSolicitudes solicitudes) {
        LocalDateTime fecha = fechaRechazo;
        for (Solicitud otra : solicitudes.colaFavorable(rechazada.getAula())) {
            if (otra != rechazada && !otra.getFechaIngresoCola().isBefore(fecha)) {
                fecha = otra.getFechaIngresoCola().plusSeconds(1);
            }
        }
        return fecha;
    }
}
