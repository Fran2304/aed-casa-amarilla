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
    private String comprobante;
    private EstadoPago estado;

    // Todo se valida antes de asignar: un pago que incumple una regla no se registra, por eso
    // no existe un estado OBSERVADO. La cuota se consulta pero no se guarda, para que un cambio
    // posterior de la configuración no altere el monto de pagos ya registrados.
    public Pago(Solicitud solicitud, ConceptoPago concepto, Matricula matricula,
            double montoPagado, MedioPago medio, String numeroOperacion,
            LocalDateTime fechaHoraOperacion, LocalDateTime fechaHoraRegistro,
            ConfiguracionCuotas cuotas) throws ReglaDominioException {
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
        // isFinite descarta NaN e Infinity: "NaN <= 0" es false y se colaría como monto válido.
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
        this.comprobante = "";
        this.estado = EstadoPago.RECIBIDO;
    }

    // En efectivo es opcional pero se guarda si viene; no se valida formato por medio.
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

    // La inscripción se paga antes de que exista matrícula; la matrícula, con la suya.
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

    // La inscripción solo se paga con la solicitud habilitada; la matrícula, mientras espera su
    // pago y la solicitud no se haya cancelado ni rechazado.
    private static void exigirEstadoPagable(Solicitud solicitud, ConceptoPago concepto,
            Matricula matricula, LocalDateTime fechaHoraOperacion,
            LocalDateTime fechaHoraRegistro) throws ReglaDominioException {
        EstadoSolicitud estado = solicitud.getEstado();
        if (concepto == ConceptoPago.INSCRIPCION) {
            if (estado != EstadoSolicitud.HABILITADA_PARA_PAGO) {
                throw new ReglaDominioException(solicitud.getCodigo() + " está en " + estado
                        + " y no está habilitada para pagar la inscripción.");
            }
            // La ventana es [habilitación, habilitación + 48 h): fuera de ella no se cobra
            // (§3.1).
            if (fechaHoraOperacion.isBefore(solicitud.getFechaHabilitacion())) {
                throw new ReglaDominioException("La operación es anterior a la habilitación"
                        + " de " + solicitud.getCodigo() + " ("
                        + solicitud.getFechaHabilitacion() + ").");
            }
            // No hay temporizador: sin esta revisión, una habilitación vencida aceptaría el
            // pago.
            if (solicitud.habilitacionVencida(fechaHoraOperacion)) {
                throw new ReglaDominioException("La habilitación de " + solicitud.getCodigo()
                        + " venció el " + solicitud.getVencimientoHabilitacion() + ".");
            }
            // Las 48 h siguen corriendo mientras se recibe el pago (§3.2): presentarlo tarde
            // no vale.
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
    }

    // Se compara en céntimos: con double, 180.1 + 0.2 no es exactamente 180.3.
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

    // Quién confirma y qué le pasa a la solicitud se resuelve en #13.
    public void confirmar() throws TransicionInvalidaException {
        Transiciones.exigirTransicion(estado, EstadoPago.CONFIRMADO);
        estado = EstadoPago.CONFIRMADO;
    }

    // Se puede confirmar sin comprobante: el número se emite después y se registra una vez.
    public void registrarComprobante(String numero) throws ReglaDominioException {
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

    public String getComprobante() {
        return comprobante;
    }

    public EstadoPago getEstado() {
        return estado;
    }
}
