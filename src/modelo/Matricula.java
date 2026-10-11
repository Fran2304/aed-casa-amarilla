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

    /** Nace PENDIENTE_PAGO: su creación es lo único que reserva una vacante. */
    public Matricula(Solicitud solicitud) throws DatoInvalidoException {
        this(solicitud, LocalDateTime.now());
    }

    public Matricula(Solicitud solicitud, LocalDateTime fechaHoraCreacion)
            throws DatoInvalidoException {
        this(0, solicitud, fechaHoraCreacion);
    }

    public Matricula(int numMatricula, Solicitud solicitud, LocalDateTime fechaHoraCreacion)
            throws DatoInvalidoException {
        if (numMatricula < 0) {
            throw new DatoInvalidoException("El número de matrícula no puede ser negativo.");
        }
        this.numMatricula = numMatricula;
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

    public int getNumMatricula() {
        return numMatricula;
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

    /** Sin pago registrado al cumplirse las 72 h; queda en el historial con su fecha. */
    public void cancelarPorVencimiento(LocalDateTime ahora) throws ReglaDominioException {
        if (ahora == null) {
            throw new DatoInvalidoException("La fecha y hora de cancelación es obligatoria.");
        }
        if (estado != EstadoMatricula.PENDIENTE_PAGO) {
            throw new ReglaDominioException("La matrícula " + numMatricula + " está en " + estado
                    + " y solo vence una matrícula PENDIENTE_PAGO.");
        }
        if (!pagoOriginalVencido(ahora)) {
            throw new ReglaDominioException("La matrícula " + numMatricula
                    + " todavía no venció; vence el " + vencimientoPagoOriginal + ".");
        }
        cancelar();
        fechaCancelacion = ahora;
    }

    public LocalDateTime getFechaCancelacion() {
        return fechaCancelacion;
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
