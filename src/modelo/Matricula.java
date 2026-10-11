package modelo;

import java.time.LocalDateTime;

import negocio.Transiciones;

public class Matricula {

    private final int numMatricula;
    private final Solicitud solicitud;
    private final Aula aula;
    private EstadoMatricula estado;
    private final LocalDateTime fechaHoraCreacion;
    private final LocalDateTime vencimientoPagoOriginal;
    private LocalDateTime fechaCancelacion;
    private String motivoCancelacion;
    private String responsableCancelacion;

    /** Nace PENDIENTE_PAGO: su creación es lo único que reserva una vacante. */
    public Matricula(int numMatricula, Solicitud solicitud, LocalDateTime fechaHoraCreacion)
            throws DatoInvalidoException {
        if (solicitud == null) {
            throw new DatoInvalidoException("La solicitud es obligatoria.");
        }
        this.numMatricula = numMatricula;
        this.solicitud = solicitud;
        this.aula = solicitud.getAula();
        this.estado = EstadoMatricula.PENDIENTE_PAGO;
        if (fechaHoraCreacion == null) {
            throw new DatoInvalidoException("La fecha de creación es obligatoria.");
        }
        this.fechaHoraCreacion = fechaHoraCreacion;
        this.vencimientoPagoOriginal = fechaHoraCreacion.plusHours(72);
    }

    public boolean estaVigente() {
        return estado == EstadoMatricula.PENDIENTE_PAGO || estado == EstadoMatricula.ACTIVA;
    }

    public Solicitud getSolicitud() {
        return solicitud;
    }

    public void activar() throws TransicionInvalidaException {
        Transiciones.exigirTransicion(estado, EstadoMatricula.ACTIVA);
        estado = EstadoMatricula.ACTIVA;
    }

    // Un único punto de cancelación para el barrido de 72 h y la anulación con plazo
    // vencido: toda matrícula CANCELADA queda en el historial con su porqué.
    public void cancelar(LocalDateTime fechaHora, String motivo, String responsable)
            throws ReglaDominioException {
        String motivoLimpio = Validaciones.exigirNoVacio("motivo de cancelación", motivo);
        String responsableLimpio = Validaciones.exigirNoVacio("responsable de cancelación",
                responsable);
        if (fechaHora == null) {
            throw new DatoInvalidoException("La fecha y hora de cancelación es obligatoria.");
        }
        Transiciones.exigirTransicion(estado, EstadoMatricula.CANCELADA);
        estado = EstadoMatricula.CANCELADA;
        fechaCancelacion = fechaHora;
        motivoCancelacion = motivoLimpio;
        responsableCancelacion = responsableLimpio;
    }

    public void revertirAPendiente() throws TransicionInvalidaException {
        Transiciones.exigirTransicion(estado, EstadoMatricula.PENDIENTE_PAGO);
        estado = EstadoMatricula.PENDIENTE_PAGO;
    }

    public int getNumMatricula() {
        return numMatricula;
    }

    /** Etiqueta del diseño; el dato sigue siendo el correlativo entero de la rúbrica. */
    public String getCodigo() {
        return "MAT-" + numMatricula;
    }

    public Aula getAula() {
        return aula;
    }

    public EstadoMatricula getEstado() {
        return estado;
    }

    public LocalDateTime getFechaHoraCreacion() { return fechaHoraCreacion; }

    public LocalDateTime getVencimientoPagoOriginal() { return vencimientoPagoOriginal; }

    public LocalDateTime getFechaCancelacion() { return fechaCancelacion; }

    public String getMotivoCancelacion() { return motivoCancelacion; }

    public String getResponsableCancelacion() { return responsableCancelacion; }

    public boolean pagoOriginalVencido(LocalDateTime ahora) {
        return !ahora.isBefore(vencimientoPagoOriginal);
    }
}
