package modelo;

import java.time.LocalDateTime;
import java.util.ArrayList;

import negocio.ExpedienteDocumentos;

/** Entrevista de admisión: agenda, asistencia y resultado (§4.5 del flujo, issue #18). */
public class Entrevista {

    private final Solicitud solicitud;
    private LocalDateTime fechaHora;
    private final ArrayList<String> personal;
    private final String responsable;
    private Boolean asistioApoderado;
    private boolean realizada;
    private int inasistencias;
    private ResultadoEntrevista resultado;

    public Entrevista(Solicitud solicitud, ExpedienteDocumentos expediente,
            LocalDateTime fechaHora, ArrayList<String> personal, String responsable)
            throws ReglaDominioException {
        if (solicitud == null) {
            throw new DatoInvalidoException("La solicitud es obligatoria.");
        }
        // Solo con los 4 documentos validados: el expediente enumera qué falta.
        if (expediente == null) {
            throw new DatoInvalidoException("El expediente es obligatorio.");
        }
        expediente.exigirAgendarEntrevista();
        if (fechaHora == null) {
            throw new DatoInvalidoException("La fecha y hora de la entrevista es obligatoria.");
        }
        if (personal == null || personal.isEmpty()) {
            throw new DatoInvalidoException("La entrevista exige al menos un participante.");
        }
        String responsableLimpio = Validaciones.exigirNoVacio("responsable", responsable);
        if (!personal.contains(responsableLimpio)) {
            throw new DatoInvalidoException(
                    "El responsable debe ser uno de los participantes.");
        }
        this.solicitud = solicitud;
        this.fechaHora = fechaHora;
        this.personal = new ArrayList<String>(personal);
        // Un solo String de responsable: exactamente uno por construcción.
        this.responsable = responsableLimpio;
        this.inasistencias = 0;
    }

    public void registrarAsistencia() throws ReglaDominioException {
        exigirPendiente();
        asistioApoderado = true;
    }

    // Primera inasistencia: una sola reprogramación. Segunda: solicitud CANCELADA.
    public void registrarInasistencia(LocalDateTime reprogramada)
            throws ReglaDominioException {
        exigirPendiente();
        if (inasistencias >= 1) {
            solicitud.cambiarEstado(EstadoSolicitud.CANCELADA);
            inasistencias++;
            return;
        }
        if (reprogramada == null) {
            throw new DatoInvalidoException("La fecha reprogramada es obligatoria.");
        }
        inasistencias++;
        fechaHora = reprogramada;
        asistioApoderado = null;
    }

    public void marcarRealizada() throws ReglaDominioException {
        if (asistioApoderado == null || !asistioApoderado) {
            throw new ReglaDominioException(
                    "La entrevista no puede realizarse sin asistencia del apoderado.");
        }
        realizada = true;
    }

    // NO_FAVORABLE deja la solicitud RECHAZADA en el historial. FAVORABLE permite
    // continuar, pero no garantiza vacante: la consulta llega con el issue #21.
    public void registrarResultado(ResultadoEntrevista resultadoNuevo)
            throws ReglaDominioException {
        if (!realizada) {
            throw new ReglaDominioException(
                    "La entrevista debe realizarse antes de registrar el resultado.");
        }
        if (resultado != null) {
            throw new ReglaDominioException("La entrevista ya tiene resultado registrado.");
        }
        if (resultadoNuevo == null) {
            throw new DatoInvalidoException("El resultado es obligatorio.");
        }
        resultado = resultadoNuevo;
        if (resultado == ResultadoEntrevista.NO_FAVORABLE) {
            solicitud.cambiarEstado(EstadoSolicitud.RECHAZADA);
        }
    }

    // Un reingreso tras RECHAZADA/CANCELADA exige nuevo pago: esas solicitudes no
    // bloquean una nueva (Solicitud.estaActiva), así que no hay nada que liberar aquí.
    private void exigirPendiente() throws ReglaDominioException {
        if (resultado != null || !solicitud.estaActiva()) {
            throw new ReglaDominioException(
                    "La entrevista de " + solicitud.getCodigo() + " ya está cerrada.");
        }
    }

    public Solicitud getSolicitud() {
        return solicitud;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public ArrayList<String> getPersonal() {
        return new ArrayList<String>(personal);
    }

    public String getResponsable() {
        return responsable;
    }

    public int getInasistencias() {
        return inasistencias;
    }

    public boolean isRealizada() {
        return realizada;
    }

    public ResultadoEntrevista getResultado() {
        return resultado;
    }
}
