package negocio;

import java.util.ArrayList;

import datos.ArregloSolicitudes;
import modelo.Aula;
import modelo.Matricula;
import modelo.ReglaDominioException;
import modelo.Solicitud;

public final class Turnos {

    private Turnos() {
    }

    public static ArrayList<Solicitud> ordenPorPrioridad(Aula aula,
            ArregloSolicitudes solicitudes, ArrayList<Matricula> matriculas) {
        ArrayList<Solicitud> enCola = solicitudes.colaFavorable(aula);
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
            ArrayList<Matricula> matriculas) {
        if (Vacantes.calcular(aula, matriculas) <= 0) {
            return null;
        }
        ArrayList<Solicitud> orden = ordenPorPrioridad(aula, solicitudes, matriculas);
        if (orden.isEmpty()) {
            return null;
        }
        return orden.get(0);
    }

    // Con N vacantes tienen turno las N primeras del orden. Una solicitud fuera de las colas
    // (vía directa, o EN_DOCUMENTACION tras la entrevista) solo pasa si las colas no ocupan
    // todas las vacantes.
    public static void exigirTurno(Solicitud solicitud, ArregloSolicitudes solicitudes,
            ArrayList<Matricula> matriculas) throws ReglaDominioException {
        Aula aula = solicitud.getAula();
        int vacantes = Vacantes.calcular(aula, matriculas);
        if (vacantes <= 0) {
            throw new ReglaDominioException("No hay vacante disponible en " + aula.getNombre()
                    + ".");
        }

        ArrayList<Solicitud> orden = ordenPorPrioridad(aula, solicitudes, matriculas);
        int posicion = orden.indexOf(solicitud);
        boolean enCola = posicion >= 0;
        if (enCola && posicion < vacantes) {
            return;
        }
        if (!enCola && orden.size() < vacantes) {
            return;
        }

        Solicitud primera = orden.get(0);
        throw new ReglaDominioException("Antes corresponde " + primera.getCodigo() + " ("
                + primera.getAlumno().getNombreCompleto() + ") en " + aula.getNombre() + ".");
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
}
