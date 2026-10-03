package negocio;

import java.util.ArrayList;

import datos.ArregloSolicitudes;
import modelo.Aula;
import modelo.Matricula;
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
}
