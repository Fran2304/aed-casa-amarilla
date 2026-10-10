package modelo;

import java.time.LocalDateTime;
import java.util.Locale;

import negocio.Transiciones;

public class Pago {

    private final Solicitud solicitud;
    private final ConceptoPago concepto;
    private final Matricula matricula;
    private final double montoPagado;
    private final MedioPago medio;
    private final String numeroOperacion;
    private final LocalDateTime fechaHoraOperacion;
    private final LocalDateTime fechaHoraRegistro;
    private final String rutaComprobantePago;
    private String comprobante;
    private EstadoPago estado;
    private LocalDateTime fechaAnulacion;
    private String motivoAnulacion;
    private String responsableAnulacion;

    public Pago(Solicitud solicitud, ConceptoPago concepto, Matricula matricula,
            double montoPagado, MedioPago medio, String numeroOperacion,
            LocalDateTime fechaHoraOperacion, LocalDateTime fechaHoraRegistro,
            String rutaComprobantePago, ConfiguracionCuotas cuotas)
            throws ReglaDominioException {
        if (solicitud == null) {
            throw new DatoInvalidoException("La solicitud es obligatoria.");
        }
        if (concepto == null) {
            throw new DatoInvalidoException("El concepto del pago es obligatorio.");
        }
        if (medio == null) {
            throw new DatoInvalidoException("El medio de pago es obligatorio.");
        }
        if (fechaHoraOperacion == null) {
            throw new DatoInvalidoException("La fecha y hora de la operación es obligatoria.");
        }
        if (fechaHoraRegistro == null) {
            throw new DatoInvalidoException("La fecha y hora de registro es obligatoria.");
        }
        if (cuotas == null) {
            throw new DatoInvalidoException("La configuración de cuotas es obligatoria.");
        }
        String numeroLimpio = numeroOperacion(medio, numeroOperacion);
        String rutaLimpia = Validaciones.exigirNoVacio("comprobante del pago",
                rutaComprobantePago);
        if (!Double.isFinite(montoPagado) || montoPagado <= 0) {
            throw new DatoInvalidoException("El monto pagado debe ser mayor que 0.");
        }
        if (fechaHoraOperacion.isAfter(fechaHoraRegistro)) {
            throw new DatoInvalidoException(
                    "La fecha de la operación no puede ser posterior a la de registro.");
        }
        if (fechaHoraOperacion.isBefore(solicitud.getFechaRegistro())) {
            throw new DatoInvalidoException("La fecha de la operación no puede ser anterior"
                    + " al registro de la solicitud " + solicitud.getCodigo() + ".");
        }
        exigirMatricula(solicitud, concepto, matricula);
        exigirEstadoPagable(solicitud, concepto, matricula, fechaHoraOperacion,
                fechaHoraRegistro);
        exigirCuota(concepto, montoPagado, cuotas);

        this.solicitud = solicitud;
        this.concepto = concepto;
        this.matricula = matricula;
        this.montoPagado = montoPagado;
        this.medio = medio;
        this.numeroOperacion = numeroLimpio;
        this.fechaHoraOperacion = fechaHoraOperacion;
        this.fechaHoraRegistro = fechaHoraRegistro;
        this.rutaComprobantePago = rutaLimpia;
        this.comprobante = "";
        this.estado = EstadoPago.RECIBIDO;
    }

    private static String numeroOperacion(MedioPago medio, String numero)
            throws DatoInvalidoException {
        if (medio != MedioPago.EFECTIVO) {
            return Validaciones.exigirNoVacio("número de operación", numero);
        }
        if (numero == null) {
            return "";
        }
        return numero.trim();
    }

    private static void exigirMatricula(Solicitud solicitud, ConceptoPago concepto,
            Matricula matricula) throws ReglaDominioException {
        if (concepto == ConceptoPago.INSCRIPCION && matricula != null) {
            throw new ReglaDominioException(
                    "El pago de inscripción no se relaciona con una matrícula.");
        }
        if (concepto == ConceptoPago.MATRICULA) {
            if (matricula == null) {
                throw new DatoInvalidoException(
                        "El pago de matrícula exige la matrícula que liquida.");
            }
            if (matricula.getSolicitud() != solicitud) {
                throw new ReglaDominioException("La matrícula no pertenece a la solicitud "
                        + solicitud.getCodigo() + ".");
            }
        }
    }

    private static void exigirEstadoPagable(Solicitud solicitud, ConceptoPago concepto,
            Matricula matricula, LocalDateTime fechaHoraOperacion,
            LocalDateTime fechaHoraRegistro) throws ReglaDominioException {
        EstadoSolicitud estado = solicitud.getEstado();
        if (concepto == ConceptoPago.INSCRIPCION) {
            if (estado != EstadoSolicitud.HABILITADA_PARA_PAGO) {
                throw new ReglaDominioException(solicitud.getCodigo() + " está en " + estado
                        + " y no está habilitada para pagar la inscripción.");
            }
            if (fechaHoraOperacion.isBefore(solicitud.getFechaHabilitacion())) {
                throw new ReglaDominioException("La operación es anterior a la habilitación"
                        + " de " + solicitud.getCodigo() + " ("
                        + solicitud.getFechaHabilitacion() + ").");
            }
            if (solicitud.habilitacionVencida(fechaHoraOperacion)) {
                throw new ReglaDominioException("La habilitación de " + solicitud.getCodigo()
                        + " venció el " + solicitud.getVencimientoHabilitacion() + ".");
            }
            if (solicitud.habilitacionVencida(fechaHoraRegistro)) {
                throw new ReglaDominioException("El pago se presentó después del vencimiento"
                        + " de la habilitación de " + solicitud.getCodigo() + " ("
                        + solicitud.getVencimientoHabilitacion() + ").");
            }
            return;
        }
        if (matricula.getEstado() != EstadoMatricula.PENDIENTE_PAGO) {
            throw new ReglaDominioException("La matrícula de " + solicitud.getCodigo()
                    + " está en " + matricula.getEstado() + " y no espera pago.");
        }
        if (estado != EstadoSolicitud.EN_DOCUMENTACION
                && estado != EstadoSolicitud.EN_ESPERA_FAVORABLE) {
            throw new ReglaDominioException(solicitud.getCodigo() + " está en " + estado
                + " y no puede pagar la matrícula.");
        }
        if (matricula.pagoOriginalVencido(fechaHoraOperacion)) {
            throw new ReglaDominioException("La operación del pago de matrícula ocurre en o después"
                    + " del vencimiento original " + matricula.getVencimientoPagoOriginal() + ".");
        }
        if (matricula.pagoOriginalVencido(fechaHoraRegistro)) {
            throw new ReglaDominioException("El registro del pago de matrícula ocurre en o después"
                    + " del vencimiento original " + matricula.getVencimientoPagoOriginal() + ".");
        }
    }

    private static void exigirCuota(ConceptoPago concepto, double monto,
            ConfiguracionCuotas cuotas) throws ReglaDominioException {
        double cuota = concepto == ConceptoPago.INSCRIPCION
                ? cuotas.getCuotaInscripcion()
                : cuotas.getCuotaMatricula();
        if (Math.round(monto * 100) != Math.round(cuota * 100)) {
            throw new ReglaDominioException("El monto S/ " + soles(monto)
                    + " no coincide con la cuota de " + concepto.getDescripcion() + " S/ "
                    + soles(cuota) + ".");
        }
    }

    private static String soles(double monto) {
        return String.format(Locale.ROOT, "%.2f", monto);
    }

    public void confirmar() throws TransicionInvalidaException {
        Transiciones.exigirTransicion(estado, EstadoPago.CONFIRMADO);
        estado = EstadoPago.CONFIRMADO;
    }

    public void anular(String motivo, String responsable, LocalDateTime fechaHora)
            throws ReglaDominioException {
        String motivoLimpio = Validaciones.exigirNoVacio("motivo de anulación", motivo);
        String responsableLimpio = Validaciones.exigirNoVacio("responsable de anulación",
                responsable);
        if (fechaHora == null) {
            throw new DatoInvalidoException("La fecha y hora de anulación es obligatoria.");
        }
        Transiciones.exigirTransicion(estado, EstadoPago.ANULADO);
        estado = EstadoPago.ANULADO;
        fechaAnulacion = fechaHora;
        motivoAnulacion = motivoLimpio;
        responsableAnulacion = responsableLimpio;
    }

    public void registrarComprobante(String numero) throws ReglaDominioException {
        if (estado == EstadoPago.ANULADO) {
            throw new ReglaDominioException("No se puede registrar un comprobante en un pago anulado.");
        }
        String limpio = Validaciones.exigirSoloDigitos("número de comprobante", numero);
        if (tieneComprobante()) {
            throw new ReglaDominioException(
                    "El pago ya tiene registrado el comprobante " + comprobante + ".");
        }
        comprobante = limpio;
    }

    public boolean tieneComprobante() {
        return !comprobante.isEmpty();
    }

    public boolean estaConfirmado() {
        return estado == EstadoPago.CONFIRMADO;
    }

    public LocalDateTime getFechaAnulacion() { return fechaAnulacion; }

    public String getMotivoAnulacion() { return motivoAnulacion; }

    public String getResponsableAnulacion() { return responsableAnulacion; }

    public Solicitud getSolicitud() {
        return solicitud;
    }

    public ConceptoPago getConcepto() {
        return concepto;
    }

    public Matricula getMatricula() {
        return matricula;
    }

    public double getMontoPagado() {
        return montoPagado;
    }

    public MedioPago getMedio() {
        return medio;
    }

    public String getNumeroOperacion() {
        return numeroOperacion;
    }

    public LocalDateTime getFechaHoraOperacion() {
        return fechaHoraOperacion;
    }

    public LocalDateTime getFechaHoraRegistro() {
        return fechaHoraRegistro;
    }

    public String getRutaComprobantePago() {
        return rutaComprobantePago;
    }

    public String getComprobante() {
        return comprobante;
    }

    public EstadoPago getEstado() {
        return estado;
    }
}
