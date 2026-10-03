package negocio;

import java.time.LocalDate;

import modelo.DatoInvalidoException;
import modelo.EstadoSolicitud;
import modelo.ReglaDominioException;
import modelo.Solicitud;

/** Plazos documentales que cancelan la solicitud (§4.4 del flujo, issue #17). */
public final class PlazosDocumentales {

    private PlazosDocumentales() {
    }

    // Se evalúa contra la fecha real de hoy: si el expediente venció, la solicitud pasa a
    // CANCELADA y ya no participa en colas (queda en el historial por estaActiva).
    // Devuelve true solo si canceló en esta evaluación. El evento en el historial lo
    // registra la pantalla #29 cuando exista su modelo.
    public static boolean evaluar(Solicitud solicitud, ExpedienteDocumentos expediente,
            LocalDate hoy) throws ReglaDominioException {
        if (solicitud == null) {
            throw new DatoInvalidoException("La solicitud es obligatoria.");
        }
        if (expediente == null) {
            throw new DatoInvalidoException("El expediente es obligatorio.");
        }
        if (hoy == null) {
            throw new DatoInvalidoException("La fecha de evaluación es obligatoria.");
        }
        // Los plazos documentales solo corren en documentación: en otras fases no hay
        // nada que vencer y una solicitud terminal nunca se toca.
        if (solicitud.getEstado() != EstadoSolicitud.EN_DOCUMENTACION) {
            return false;
        }
        if (expediente.debeCancelarse(hoy)) {
            solicitud.cambiarEstado(EstadoSolicitud.CANCELADA);
            return true;
        }
        return false;
    }
}
