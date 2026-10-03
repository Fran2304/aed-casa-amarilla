package negocio;

import java.time.LocalDateTime;
import java.util.ArrayList;

import datos.ArregloSolicitudes;
import modelo.Aula;
import modelo.Matricula;
import modelo.Oferta;
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

    // La oferta no crea matrícula ni reserva vacante: si se acepta, la solicitud queda
    // habilitada para que #23 cree la matrícula PENDIENTE_PAGO, que es lo que reserva (§4.6).
    // Criterio del grupo para §6.3: si no se acepta, vuelve al final de la cola favorable con
    // fecha nueva, igual que una invitación vencida en la cola sin pago (§4.2).
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
            solicitud.ingresarAColaFavorable(fechaHora);
        }
        return oferta;
    }
}
