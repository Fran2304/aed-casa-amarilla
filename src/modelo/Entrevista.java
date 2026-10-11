package modelo;

import java.time.LocalDateTime;
import java.util.ArrayList;

import negocio.ExpedienteDocumentos;

/** Entrevista de admisión: agenda, asistencia y resultado (§4.5 del flujo, issue #18). */
public class Entrevista {

    public static final int INASISTENCIAS_PERMITIDAS = 2;

    private final Solicitud solicitud;
    private final ExpedienteDocumentos expediente;
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
        // Cerrada desde el inicio: no se agenda sobre RECHAZADA/CANCELADA,
        // igual que exigirPendiente bloquea su uso posterior.
        if (!solicitud.estaActiva()) {
            throw new ReglaDominioException(
                    "La entrevista de " + solicitud.getCodigo() + " ya está cerrada.");
        }
        // Solo con los 4 documentos validados: el expediente enumera qué falta.
        if (expediente == null) {
            throw new DatoInvalidoException("El expediente es obligatorio.");
        }
        if (solicitud.getEstado() != EstadoSolicitud.EN_DOCUMENTACION
                || solicitud.getExpediente() != expediente) {
            throw new ReglaDominioException("La entrevista de " + solicitud.getCodigo()
                    + " solo se agenda con la inscripción confirmada y su expediente vigente.");
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
        this.expediente = expediente;
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
        if (inasistencias + 1 >= INASISTENCIAS_PERMITIDAS) {
            solicitud.cambiarEstado(EstadoSolicitud.CANCELADA);
            inasistencias++;
            return;
        }
        if (reprogramada == null) {
            throw new DatoInvalidoException("La fecha reprogramada es obligatoria.");
        }
        if (!reprogramada.isAfter(fechaHora)) {
            throw new DatoInvalidoException(
                    "La nueva fecha debe ser posterior a la cita a la que no asistió.");
        }
        inasistencias++;
        fechaHora = reprogramada;
        asistioApoderado = null;
    }

    public void marcarRealizada() throws ReglaDominioException {
        exigirPendiente();
        if (asistioApoderado == null || !asistioApoderado) {
            throw new ReglaDominioException(
                    "La entrevista no puede realizarse sin asistencia del apoderado.");
        }
        realizada = true;
    }

    public void registrarResultado(ResultadoEntrevista resultadoNuevo,
            LocalDateTime ingresoCola) throws ReglaDominioException {
        exigirPendiente();
        expediente.exigirAgendarEntrevista();
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
        if (ingresoCola == null) {
            throw new DatoInvalidoException("La fecha de ingreso a la cola es obligatoria.");
        }
        if (resultadoNuevo == ResultadoEntrevista.NO_FAVORABLE) {
            solicitud.cambiarEstado(EstadoSolicitud.RECHAZADA);
        } else {
            solicitud.ingresarAColaFavorable(ingresoCola);
        }
        resultado = resultadoNuevo;
    }

    // Un reingreso tras RECHAZADA/CANCELADA exige nuevo pago: esas solicitudes no
    // bloquean una nueva (Solicitud.estaActiva), así que no hay nada que liberar aquí.
    private void exigirPendiente() throws ReglaDominioException {
        if (resultado != null || !solicitud.estaActiva()) {
            throw new ReglaDominioException(
                    "La entrevista de " + solicitud.getCodigo() + " ya está cerrada.");
        }
        if (!esDeLaInscripcionVigente()) {
            throw new ReglaDominioException("La entrevista de " + solicitud.getCodigo()
                    + " corresponde a una inscripción que ya no está vigente.");
        }
    }

    public boolean esDeLaInscripcionVigente() {
        return solicitud.getEstado() == EstadoSolicitud.EN_DOCUMENTACION
                && solicitud.getExpediente() == expediente;
    }

    public Solicitud getSolicitud() {
        return solicitud;
    }

    public ExpedienteDocumentos getExpediente() {
        return expediente;
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

    // La UI del expediente (#19) pinta la asistencia pendiente/asistió desde aquí.
    public Boolean getAsistioApoderado() {
        return asistioApoderado;
    }

    public boolean isRealizada() {
        return realizada;
    }

    public ResultadoEntrevista getResultado() {
        return resultado;
    }
}
