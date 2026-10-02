package modelo;

import java.time.LocalDateTime;

import negocio.Transiciones;

public class Pago {

    private final ConceptoPago concepto;
    private final double montoAplicado;
    private final MedioPago medio;
    private final LocalDateTime fechaOperacion;
    private final String numeroOperacion;
    private final String comprobante;
    private final String codigoSolicitud;
    private final Matricula matricula;
    private EstadoPago estado;
    private String observacion;

    public Pago(ConceptoPago concepto, double montoAplicado, MedioPago medio,
            LocalDateTime fechaOperacion, String numeroOperacion, String comprobante,
            String codigoSolicitud, Matricula matricula) throws DatoInvalidoException {
        if (concepto == null) {
            throw new DatoInvalidoException("El concepto del pago es obligatorio.");
        }
        // isFinite descarta NaN e Infinity: "NaN <= 0" es false y se colaría como monto válido.
        if (!Double.isFinite(montoAplicado) || montoAplicado <= 0) {
            throw new DatoInvalidoException("El monto aplicado debe ser mayor que 0.");
        }
        if (medio == null) {
            throw new DatoInvalidoException("El medio de pago es obligatorio.");
        }
        if (fechaOperacion == null) {
            throw new DatoInvalidoException("La fecha real de la operación es obligatoria.");
        }
        // El número de operación es obligatorio excepto para efectivo (§4.8 del flujo).
        String numero = numeroOperacion == null ? "" : numeroOperacion.trim();
        if (medio != MedioPago.EFECTIVO && numero.isEmpty()) {
            throw new DatoInvalidoException(
                    "El número de operación es obligatorio para pagos con " + medio + ".");
        }
        if (concepto == ConceptoPago.MATRICULA && matricula == null) {
            throw new DatoInvalidoException("El pago de matrícula debe indicar su matrícula.");
        }
        // El pago de inscripción ocurre antes de que exista la matrícula (§4.8 del flujo).
        if (concepto == ConceptoPago.INSCRIPCION && matricula != null) {
            throw new DatoInvalidoException("El pago de inscripción es anterior a la matrícula.");
        }
        this.concepto = concepto;
        this.montoAplicado = montoAplicado;
        this.medio = medio;
        // Se conserva la fecha real de la operación, no la fecha de registro en el sistema.
        this.fechaOperacion = fechaOperacion;
        this.numeroOperacion = numero;
        this.comprobante = Validaciones.exigirNoVacio("comprobante", comprobante);
        // Se vincula por código porque la clase Solicitud llega con el issue #7 (aún abierto).
        this.codigoSolicitud = Validaciones.exigirNoVacio("solicitud", codigoSolicitud);
        this.matricula = matricula;
        this.estado = EstadoPago.RECIBIDO;
        this.observacion = "";
    }

    // Monto distinto a la cuota vigente: observación, no confirmación (criterio del issue #12).
    public void confirmar(double cuotaVigente) throws ReglaDominioException {
        if (Double.compare(montoAplicado, cuotaVigente) != 0) {
            throw new DatoInvalidoException("El monto aplicado no coincide con la cuota vigente.");
        }
        Transiciones.exigirTransicion(estado, EstadoPago.CONFIRMADO);
        estado = EstadoPago.CONFIRMADO;
    }

    // La corrección se presenta como un pago nuevo (issue #13): un pago observado
    // no vuelve a observarse, solo puede confirmarse su reemplazo.
    public void observar(String motivo) throws ReglaDominioException {
        String motivoLimpio = Validaciones.exigirNoVacio("observación", motivo);
        Transiciones.exigirTransicion(estado, EstadoPago.OBSERVADO);
        observacion = motivoLimpio;
        estado = EstadoPago.OBSERVADO;
    }

    public boolean esMontoCorrecto(double cuotaVigente) {
        return Double.compare(montoAplicado, cuotaVigente) == 0;
    }

    public boolean estaConfirmado() {
        return estado == EstadoPago.CONFIRMADO;
    }

    public ConceptoPago getConcepto() {
        return concepto;
    }

    public double getMontoAplicado() {
        return montoAplicado;
    }

    public MedioPago getMedio() {
        return medio;
    }

    public LocalDateTime getFechaOperacion() {
        return fechaOperacion;
    }

    public String getNumeroOperacion() {
        return numeroOperacion;
    }

    public String getComprobante() {
        return comprobante;
    }

    public String getCodigoSolicitud() {
        return codigoSolicitud;
    }

    public Matricula getMatricula() {
        return matricula;
    }

    public EstadoPago getEstado() {
        return estado;
    }

    public String getObservacion() {
        return observacion;
    }
}
