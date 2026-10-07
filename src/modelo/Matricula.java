package modelo;

import java.time.LocalDateTime;

import negocio.Transiciones;

public class Matricula {

    // Plazo de pago de matrícula: 72 horas corridas desde su creación (§4.7).
    public static final int HORAS_PAGO = 72;

    private final Solicitud solicitud;
    private final Aula aula;
    // Sin la fecha de creación los plazos de la matrícula (las 72 h) son incalculables.
    private final LocalDateTime fechaHoraCreacion;
    private EstadoMatricula estado;

    /** Nace PENDIENTE_PAGO: su creación es lo único que reserva una vacante. */
    public Matricula(Solicitud solicitud, LocalDateTime fechaHoraCreacion)
            throws DatoInvalidoException {
        if (solicitud == null) {
            throw new DatoInvalidoException("La solicitud es obligatoria.");
        }
        if (fechaHoraCreacion == null) {
            throw new DatoInvalidoException(
                    "La fecha y hora de creación de la matrícula es obligatoria.");
        }
        this.solicitud = solicitud;
        this.aula = solicitud.getAula();
        this.fechaHoraCreacion = fechaHoraCreacion;
        this.estado = EstadoMatricula.PENDIENTE_PAGO;
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

    public void cancelar() throws TransicionInvalidaException {
        Transiciones.exigirTransicion(estado, EstadoMatricula.CANCELADA);
        estado = EstadoMatricula.CANCELADA;
    }

    /**
     * Revierte la matrícula tras anular su pago confirmado: vuelve a
     * {@code PENDIENTE_PAGO} conservando el plazo original de 72 h (no lo reinicia).
     */
    public void revertirPago() throws TransicionInvalidaException {
        if (estado == EstadoMatricula.PENDIENTE_PAGO) {
            return;
        }
        Transiciones.exigirTransicion(estado, EstadoMatricula.PENDIENTE_PAGO);
        estado = EstadoMatricula.PENDIENTE_PAGO;
    }

    public LocalDateTime getFechaHoraCreacion() {
        return fechaHoraCreacion;
    }

    public LocalDateTime getVencimientoPago() {
        return fechaHoraCreacion.plusHours(HORAS_PAGO);
    }

    public boolean plazoPagoVencido(LocalDateTime ahora) {
        return !ahora.isBefore(getVencimientoPago());
    }

    public Aula getAula() {
        return aula;
    }

    public EstadoMatricula getEstado() {
        return estado;
    }
}
