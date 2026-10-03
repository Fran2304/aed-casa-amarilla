package negocio;

import java.time.LocalDateTime;
import java.util.ArrayList;

import datos.ArregloSolicitudes;
import modelo.Aula;
import modelo.Matricula;
import modelo.Oferta;
import modelo.ReglaDominioException;
import modelo.Solicitud;

/** Al liberarse una vacante, primero la cola favorable y después la sin pago (§4.2, §4.6). */
public final class Turnos {

    private Turnos() {
    }

    public static ArrayList<Solicitud> ordenPorPrioridad(Aula aula,
            ArregloSolicitudes solicitudes) {
        ArrayList<Solicitud> orden = solicitudes.colaFavorable(aula);
        orden.addAll(solicitudes.colaSinPago(aula));
        return orden;
    }

    // Una vacante no habilita a toda la cola: solo a la primera solicitud del orden.
    public static Solicitud siguiente(Aula aula, ArregloSolicitudes solicitudes,
            ArrayList<Matricula> matriculas) {
        if (Vacantes.calcular(aula, matriculas) <= 0) {
            return null;
        }
        ArrayList<Solicitud> orden = ordenPorPrioridad(aula, solicitudes);
        if (orden.isEmpty()) {
            return null;
        }
        return orden.get(0);
    }

    public static void exigirTurno(Solicitud solicitud, ArregloSolicitudes solicitudes,
            ArrayList<Matricula> matriculas) throws ReglaDominioException {
        Aula aula = solicitud.getAula();
        Solicitud turno = siguiente(aula, solicitudes, matriculas);
        if (turno == null) {
            throw new ReglaDominioException("No hay vacante disponible en " + aula.getNombre()
                    + ".");
        }
        if (turno != solicitud) {
            throw new ReglaDominioException("Antes corresponde " + turno.getCodigo() + " ("
                    + turno.getAlumno().getNombreCompleto() + ") en " + aula.getNombre() + ".");
        }
    }

    // La oferta no crea matrícula ni reserva vacante: si se acepta, la solicitud queda
    // habilitada para que #23 cree la matrícula PENDIENTE_PAGO, que es lo que reserva (§4.6).
    public static Oferta confirmarOferta(Solicitud solicitud, boolean aceptada, String personal,
            LocalDateTime fechaHora, ArregloSolicitudes solicitudes,
            ArrayList<Matricula> matriculas) throws ReglaDominioException {
        if (!solicitud.estaEnColaFavorable()) {
            throw new ReglaDominioException("Solo se ofrece vacante a solicitudes de la cola"
                    + " favorable; " + solicitud.getCodigo() + " está en "
                    + solicitud.getEstado() + ".");
        }
        exigirTurno(solicitud, solicitudes, matriculas);

        Oferta oferta = new Oferta(fechaHora, personal, aceptada);
        solicitud.registrarOferta(oferta);
        if (!aceptada) {
            solicitud.reingresarAColaFavorable(fechaHora);
        }
        return oferta;
    }
}
