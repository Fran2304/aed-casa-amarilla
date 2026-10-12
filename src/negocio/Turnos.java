package negocio;

import java.time.LocalDateTime;
import java.util.ArrayList;

import datos.ArregloAulas;
import datos.ArregloMatriculas;
import datos.ArregloSolicitudes;
import modelo.Aula;
import modelo.DatoInvalidoException;
import modelo.Oferta;
import modelo.ReglaDominioException;
import modelo.Solicitud;

public final class Turnos {

    private Turnos() {
    }

    public static ArrayList<Solicitud> ordenPorPrioridad(Aula aula,
            ArregloSolicitudes solicitudes, ArregloMatriculas matriculas) {
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

    public static Solicitud siguiente(Aula aula, ArregloSolicitudes solicitudes,
            ArregloMatriculas matriculas) {
        ArrayList<Solicitud> orden = ordenPorPrioridad(aula, solicitudes, matriculas);
        int vacantes = Vacantes.calcular(aula, matriculas);
        for (int i = 0; i < vacantes && i < orden.size(); i++) {
            Solicitud solicitud = orden.get(i);
            if (solicitud.estaEnColaFavorable() || solicitud.estaEnColaSinPago()) {
                return solicitud;
            }
        }
        return null;
    }

    public static void exigirTurno(Solicitud solicitud, ArregloSolicitudes solicitudes,
            ArregloMatriculas matriculas) throws ReglaDominioException {
        if (tieneMatriculaVigente(solicitud, matriculas)) {
            throw new ReglaDominioException(solicitud.getCodigo() + " ya tiene matrícula vigente.");
        }
        Aula aula = solicitud.getAula();
        ArrayList<Solicitud> orden = ordenPorPrioridad(aula, solicitudes, matriculas);
        int vacantes = Vacantes.calcular(aula, matriculas);
        if (vacantes <= 0) {
            throw new ReglaDominioException("No hay vacante disponible en " + aula.getNombre()
                    + ".");
        }

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

    public static ArrayList<Solicitud> revisarTurnos(ArregloAulas aulas,
            ArregloSolicitudes solicitudes, ArregloMatriculas matriculas, LocalDateTime ahora)
            throws ReglaDominioException {
        Vencimientos.revisar(ahora, solicitudes, matriculas);
        ArrayList<Solicitud> habilitadas = new ArrayList<Solicitud>();
        for (Aula aula : aulas.listar()) {
            habilitadas.addAll(habilitarConTurno(aula, solicitudes, matriculas, ahora));
        }
        return habilitadas;
    }

    private static ArrayList<Solicitud> habilitarConTurno(Aula aula,
            ArregloSolicitudes solicitudes, ArregloMatriculas matriculas, LocalDateTime ahora)
            throws ReglaDominioException {
        ArrayList<Solicitud> habilitadas = new ArrayList<Solicitud>();
        ArrayList<Solicitud> orden = ordenPorPrioridad(aula, solicitudes, matriculas);
        int vacantes = Vacantes.calcular(aula, matriculas);
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
            ArregloSolicitudes solicitudes, ArregloMatriculas matriculas, LocalDateTime fecha)
            throws ReglaDominioException {
        Vencimientos.revisar(fecha, solicitudes, matriculas);
        nueva.ingresarAColaSinPago(fecha);
        ArrayList<Solicitud> promovidas = habilitarConTurno(nueva.getAula(), solicitudes,
                matriculas, fecha);
        promovidas.remove(nueva);
        return promovidas;
    }

    private static boolean tieneMatriculaVigente(Solicitud solicitud,
            ArregloMatriculas matriculas) {
        return matriculas.vigenteDe(solicitud) != null;
    }

    public static Oferta confirmarOferta(Solicitud solicitud, boolean aceptada, String personal,
            LocalDateTime fechaHora, ArregloSolicitudes solicitudes,
            ArregloMatriculas matriculas) throws ReglaDominioException {
        if (fechaHora == null) {
            throw new DatoInvalidoException("La fecha y hora de la oferta es obligatoria.");
        }
        ArrayList<String> avisos = Vencimientos.revisar(fechaHora, solicitudes, matriculas);
        if (!solicitud.estaEnColaFavorable()) {
            throw new ReglaDominioException("Solo se ofrece vacante a solicitudes de la cola"
                    + " favorable; " + solicitud.getCodigo() + " está en "
                    + solicitud.getEstado() + ".");
        }
        if (solicitud.tieneOfertaAceptada()) {
            throw new ReglaDominioException(solicitud.getCodigo() + " ya aceptó una oferta.");
        }
        try {
            exigirTurno(solicitud, solicitudes, matriculas);
        } catch (ReglaDominioException e) {
            if (avisos.isEmpty()) {
                throw e;
            }
            throw new ReglaDominioException(e.getMessage()
                    + " Revisión de vencimientos incompleta: " + Vencimientos.unir(avisos));
        }

        Oferta oferta = new Oferta(fechaHora, personal, aceptada);
        // Aceptar la oferta y crear la matrícula son una sola operación: el flujo no
        // nombra un estado de "oferta aceptada sin matrícula".
        if (aceptada) {
            matriculas.registrar(solicitud, fechaHora);
        }
        solicitud.registrarOferta(oferta);
        if (!aceptada) {
            solicitud.ingresarAColaFavorable(
                    fechaAlFinalFavorable(solicitud, fechaHora, solicitudes));
        }
        return oferta;
    }

    public static LocalDateTime fechaAlFinalFavorable(Solicitud entrante, LocalDateTime fecha,
            ArregloSolicitudes solicitudes) {
        for (Solicitud otra : solicitudes.colaFavorable(entrante.getAula())) {
            if (otra != entrante && !otra.getFechaIngresoCola().isBefore(fecha)) {
                fecha = otra.getFechaIngresoCola().plusSeconds(1);
            }
        }
        return fecha;
    }
}
