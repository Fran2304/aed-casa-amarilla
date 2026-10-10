package modelo;

import java.time.LocalDateTime;

import negocio.Transiciones;

public class Matricula {

    private final Solicitud solicitud;
    private final Aula aula;
    private EstadoMatricula estado;
    private final LocalDateTime fechaHoraCreacion;
    private final LocalDateTime vencimientoPagoOriginal;

    /** Nace PENDIENTE_PAGO: su creación es lo único que reserva una vacante. */
    public Matricula(Solicitud solicitud) throws DatoInvalidoException {
        this(solicitud, LocalDateTime.now());
    }

    public Matricula(Solicitud solicitud, LocalDateTime fechaHoraCreacion)
            throws DatoInvalidoException {
        if (solicitud == null) {
            throw new DatoInvalidoException("La solicitud es obligatoria.");
        }
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

    public void cancelar() throws TransicionInvalidaException {
        Transiciones.exigirTransicion(estado, EstadoMatricula.CANCELADA);
        estado = EstadoMatricula.CANCELADA;
    }

    public void revertirAPendiente() throws TransicionInvalidaException {
        Transiciones.exigirTransicion(estado, EstadoMatricula.PENDIENTE_PAGO);
        estado = EstadoMatricula.PENDIENTE_PAGO;
    }

    public Aula getAula() {
        return aula;
    }

    public EstadoMatricula getEstado() {
        return estado;
    }

    public LocalDateTime getFechaHoraCreacion() { return fechaHoraCreacion; }

    public LocalDateTime getVencimientoPagoOriginal() { return vencimientoPagoOriginal; }

    public boolean pagoOriginalVencido(LocalDateTime ahora) {
        return !ahora.isBefore(vencimientoPagoOriginal);
    }
}
