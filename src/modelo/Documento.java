package modelo;

import java.time.LocalDateTime;

import negocio.Transiciones;

public class Documento {

    public static final int DIAS_CORRECCION = 7;

    private final TipoDocumento tipo;
    private LocalDateTime fechaHoraEntrega;
    private LocalDateTime fechaHoraCorreccion;
    private EstadoDocumento estado;
    private String observacion;
    private LocalDateTime fechaHoraComunicacion;

    public Documento(TipoDocumento tipo) throws DatoInvalidoException {
        if (tipo == null) {
            throw new DatoInvalidoException("El tipo de documento es obligatorio.");
        }
        this.tipo = tipo;
    }

    public void registrarEntrega(LocalDateTime fechaHora) throws ReglaDominioException {
        if (fechaHora == null) {
            throw new DatoInvalidoException("La fecha y hora de entrega es obligatoria.");
        }
        if (estaEntregado()) {
            throw new ReglaDominioException(
                    "El " + tipo.getDescripcion() + " ya fue entregado.");
        }
        fechaHoraEntrega = fechaHora;
        estado = EstadoDocumento.EN_REVISION;
    }

    public void validar() throws ReglaDominioException {
        exigirEntregado();
        Transiciones.exigirTransicion(estado, EstadoDocumento.VALIDADO);
        estado = EstadoDocumento.VALIDADO;
    }

    public void observar(String motivo, LocalDateTime comunicadaEl) throws ReglaDominioException {
        exigirEntregado();
        String motivoLimpio = Validaciones.exigirNoVacio("motivo de la observación", motivo);
        if (comunicadaEl == null) {
            throw new DatoInvalidoException("La fecha y hora de comunicación es obligatoria.");
        }
        Transiciones.exigirTransicion(estado, EstadoDocumento.OBSERVADO);
        observacion = motivoLimpio;
        fechaHoraComunicacion = comunicadaEl;
        estado = EstadoDocumento.OBSERVADO;
    }

    public void entregarCorreccion(LocalDateTime fechaHora) throws ReglaDominioException {
        if (fechaHora == null) {
            throw new DatoInvalidoException("La fecha y hora de la corrección es obligatoria.");
        }
        exigirEntregado();
        Transiciones.exigirTransicion(estado, EstadoDocumento.EN_REVISION);
        if (correccionVencida(fechaHora)) {
            throw new ReglaDominioException("El plazo para corregir el "
                    + tipo.getDescripcion() + " venció el " + fechaLimiteCorreccion() + ".");
        }
        fechaHoraCorreccion = fechaHora;
        estado = EstadoDocumento.EN_REVISION;
    }

    /** Solo existe mientras el documento está observado. */
    public LocalDateTime fechaLimiteCorreccion() {
        if (estado != EstadoDocumento.OBSERVADO) {
            return null;
        }
        return fechaHoraComunicacion.plusDays(DIAS_CORRECCION);
    }

    public boolean correccionVencida(LocalDateTime ahora) {
        LocalDateTime limite = fechaLimiteCorreccion();
        return limite != null && ahora.isAfter(limite);
    }

    public boolean estaEntregado() {
        return fechaHoraEntrega != null;
    }

    public boolean estaValidado() {
        return estado == EstadoDocumento.VALIDADO;
    }

    private void exigirEntregado() throws ReglaDominioException {
        if (!estaEntregado()) {
            throw new ReglaDominioException(
                    "El " + tipo.getDescripcion() + " todavía no fue entregado.");
        }
    }

    public TipoDocumento getTipo() {
        return tipo;
    }

    public LocalDateTime getFechaHoraEntrega() {
        return fechaHoraEntrega;
    }

    public LocalDateTime getFechaHoraCorreccion() {
        return fechaHoraCorreccion;
    }

    /** null mientras el documento no se entregue. */
    public EstadoDocumento getEstado() {
        return estado;
    }

    public String getObservacion() {
        return observacion;
    }

    public LocalDateTime getFechaHoraComunicacion() {
        return fechaHoraComunicacion;
    }
}
