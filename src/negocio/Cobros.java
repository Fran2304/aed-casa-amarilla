package negocio;

import java.time.LocalDateTime;
import java.time.DateTimeException;
import java.util.ArrayList;

import datos.ArregloMatriculas;
import datos.ArregloPagos;
import datos.ArregloSolicitudes;
import modelo.ConceptoPago;
import modelo.ConfiguracionCuotas;
import modelo.DatoInvalidoException;
import modelo.Matricula;
import modelo.MedioPago;
import modelo.Pago;
import modelo.ReglaDominioException;
import modelo.Solicitud;
import modelo.EstadoMatricula;
import modelo.EstadoPago;
import modelo.EstadoSolicitud;
import util.FormatoFecha;

public final class Cobros {

    public static final String MOTIVO_ANULACION_VENCIDA =
            "Anulación de pago con plazo de 72 h vencido";

    private Cobros() {
    }

    public static Pago confirmarInscripcion(Solicitud solicitud, double montoPagado,
            MedioPago medio, String numeroOperacion, LocalDateTime fechaHoraOperacion,
            String rutaComprobantePago, LocalDateTime ahora, ArregloSolicitudes solicitudes,
            ArregloMatriculas matriculas,
            ArregloPagos pagos, ConfiguracionCuotas cuotas) throws ReglaDominioException {
        if (solicitud == null) {
            throw new DatoInvalidoException("La solicitud es obligatoria.");
        }
        if (ahora == null) {
            throw new DatoInvalidoException("La fecha y hora de confirmación es obligatoria.");
        }
        if (pagos.inscripcionConfirmada(solicitud)) {
            throw new ReglaDominioException("La solicitud " + solicitud.getCodigo()
                    + " ya tiene la inscripción pagada.");
        }
        LocalDateTime habilitacionVencida = solicitud.habilitacionVencida(ahora)
                ? solicitud.getVencimientoHabilitacion()
                : null;
        ArrayList<String> avisos = Vencimientos.revisar(ahora, solicitudes, matriculas);
        exigirHabilitacionVigente(solicitud, habilitacionVencida, avisos, ahora);
        Turnos.exigirTurno(solicitud, solicitudes, matriculas);

        Pago pago = new Pago(solicitud, ConceptoPago.INSCRIPCION, null, montoPagado, medio,
                numeroOperacion, fechaHoraOperacion, ahora, rutaComprobantePago, cuotas);
        pago.confirmar();
        solicitud.confirmarInscripcion(ahora);
        pagos.agregar(pago);
        return pago;
    }

    // Un solo paso, como la inscripción: con §6.4 no hay nada que revisar entre recibir
    // y confirmar; el pago pasa las validaciones o se rechaza sin registrarse.
    public static Pago confirmarMatricula(Matricula matricula, double montoPagado,
            MedioPago medio, String numeroOperacion, LocalDateTime fechaHoraOperacion,
            String rutaComprobantePago, LocalDateTime ahora, ArregloSolicitudes solicitudes,
            ArregloMatriculas matriculas, ArregloPagos pagos, ConfiguracionCuotas cuotas)
            throws ReglaDominioException {
        if (matricula == null) {
            throw new DatoInvalidoException("La matrícula es obligatoria.");
        }
        if (ahora == null) {
            throw new DatoInvalidoException("La fecha y hora de confirmación es obligatoria.");
        }
        if (!matriculas.contiene(matricula)) {
            throw new ReglaDominioException("La matrícula no está registrada en la colección.");
        }
        if (pagos.matriculaConfirmada(matricula)) {
            throw new ReglaDominioException("La matrícula " + matricula.getCodigo()
                    + " ya tiene el pago confirmado.");
        }
        exigirMatriculaNoVencida(matricula, ahora, solicitudes, matriculas);

        Pago pago = new Pago(matricula.getSolicitud(), ConceptoPago.MATRICULA, matricula,
                montoPagado, medio, numeroOperacion, fechaHoraOperacion, ahora,
                rutaComprobantePago, cuotas);
        Transiciones.exigirTransicion(matricula.getEstado(), EstadoMatricula.ACTIVA);
        pago.confirmar();
        matricula.activar();
        pagos.agregar(pago);
        return pago;
    }

    // El personal ve el resultado final del vencimiento en el mismo momento, no una
    // PENDIENTE_PAGO que ya está muerta.
    private static void exigirMatriculaNoVencida(Matricula matricula, LocalDateTime ahora,
            ArregloSolicitudes solicitudes, ArregloMatriculas matriculas)
            throws ReglaDominioException {
        if (matricula.getEstado() != EstadoMatricula.PENDIENTE_PAGO
                || !matricula.pagoOriginalVencido(ahora)) {
            return;
        }
        ArrayList<String> avisos = Vencimientos.revisar(ahora, solicitudes, matriculas);
        if (matricula.getEstado() == EstadoMatricula.PENDIENTE_PAGO) {
            throw new ReglaDominioException("La matrícula " + matricula.getCodigo()
                    + " venció, pero no se pudo cancelar: " + Vencimientos.unir(avisos));
        }
        throw new ReglaDominioException("La matrícula " + matricula.getCodigo() + " venció el "
                + FormatoFecha.fechaHora(matricula.getVencimientoPagoOriginal())
                + "; quedó CANCELADA y la solicitud"
                + " volvió al final de la cola favorable.");
    }

    private static void exigirHabilitacionVigente(Solicitud solicitud,
            LocalDateTime vencimiento, ArrayList<String> avisos, LocalDateTime ahora)
            throws ReglaDominioException {
        if (vencimiento == null) {
            return;
        }
        if (solicitud.habilitacionVencida(ahora)) {
            throw new ReglaDominioException("La habilitación de " + solicitud.getCodigo()
                    + " venció, pero no se pudo devolver a la cola: "
                    + Vencimientos.unir(avisos));
        }
        throw new ReglaDominioException("La habilitación de " + solicitud.getCodigo()
                + " venció el " + FormatoFecha.fechaHora(vencimiento)
                + "; volvió al final de la cola sin pago.");
    }

    public static void anularPago(Pago pago, String motivo, String responsable,
            LocalDateTime fechaHora, ArregloSolicitudes solicitudes,
            ArregloMatriculas matriculas, ArregloPagos pagos) throws ReglaDominioException {
        if (pago == null || !pago.estaConfirmado()) {
            throw new ReglaDominioException("Solo se puede anular un pago confirmado.");
        }
        if (solicitudes == null || matriculas == null || pagos == null) {
            throw new DatoInvalidoException("Los datos del pago son obligatorios.");
        }
        String motivoLimpio = modelo.Validaciones.exigirNoVacio("motivo de anulación", motivo);
        String responsableLimpio = modelo.Validaciones.exigirNoVacio("responsable de anulación",
                responsable);
        if (fechaHora == null) {
            throw new DatoInvalidoException("La fecha y hora de anulación es obligatoria.");
        }
        Solicitud solicitud = pago.getSolicitud();
        Matricula matricula = pago.getMatricula();
        validarRelacion(pago, solicitud, matricula, pagos);
        if (matricula != null && !matriculas.contiene(matricula)) {
            throw new ReglaDominioException("La matrícula no está registrada en la colección.");
        }
        if (pago.getConcepto() == ConceptoPago.INSCRIPCION) {
            exigirMatriculaVigenteRelacionada(solicitud, matriculas);
        }
        boolean matriculaVencida = matricula != null && matricula.pagoOriginalVencido(fechaHora);
        LocalDateTime fechaCola;
        if (pago.getConcepto() == ConceptoPago.INSCRIPCION) {
            fechaCola = prepararAnulacionInscripcion(solicitud, fechaHora, solicitudes);
        } else {
            fechaCola = prepararAnulacionMatricula(solicitud, matricula, matriculaVencida,
                    fechaHora, solicitudes);
            exigirTransicionMatriculaAnulada(matricula, matriculaVencida);
        }
        Transiciones.exigirTransicion(EstadoPago.CONFIRMADO, EstadoPago.ANULADO);
        if (pago.getConcepto() == ConceptoPago.INSCRIPCION) {
            Transiciones.exigirTransicion(EstadoSolicitud.EN_DOCUMENTACION,
                    fechaCola == null ? EstadoSolicitud.HABILITADA_PARA_PAGO
                            : EstadoSolicitud.EN_ESPERA_SIN_PAGO);
        }
        pago.anular(motivoLimpio, responsableLimpio, fechaHora);
        aplicarReversionAnulacion(pago, solicitud, matricula, matriculaVencida, fechaHora, fechaCola,
                responsableLimpio);
    }

    private static LocalDateTime prepararAnulacionInscripcion(Solicitud solicitud,
            LocalDateTime fechaHora, ArregloSolicitudes solicitudes) throws ReglaDominioException {
        if (solicitud.getEstado() != EstadoSolicitud.EN_DOCUMENTACION) {
            throw new ReglaDominioException("La solicitud no está en documentación.");
        }
        if (fechaHora.isBefore(solicitud.getVencimientoHabilitacionOriginal())) {
            return null;
        }
        // La habilitación original vencida vuelve al final de EN_ESPERA_SIN_PAGO;
        // no se reinicia el plazo de 48 horas.
        return fechaAlFinal(solicitud, fechaHora, solicitudes, false);
    }

    private static LocalDateTime prepararAnulacionMatricula(Solicitud solicitud,
            Matricula matricula, boolean matriculaVencida, LocalDateTime fechaHora,
            ArregloSolicitudes solicitudes) throws ReglaDominioException {
        if (matricula == null || (matricula.getEstado() != EstadoMatricula.PENDIENTE_PAGO
                && matricula.getEstado() != EstadoMatricula.ACTIVA)) {
            throw new ReglaDominioException("La matrícula no está pendiente ni activa.");
        }
        if (!matriculaVencida) {
            return null;
        }
        // Al vencer el plazo original, la matrícula se cancela y la solicitud
        // vuelve al final de EN_ESPERA_FAVORABLE; la inscripción se conserva.
        if (solicitud.getEstado() != EstadoSolicitud.EN_DOCUMENTACION
                && solicitud.getEstado() != EstadoSolicitud.EN_ESPERA_FAVORABLE) {
            throw new ReglaDominioException("La solicitud no puede volver a la cola favorable.");
        }
        return fechaAlFinal(solicitud, fechaHora, solicitudes, true);
    }

    private static void exigirTransicionMatriculaAnulada(Matricula matricula,
            boolean matriculaVencida) throws ReglaDominioException {
        if (matriculaVencida) {
            Transiciones.exigirTransicion(matricula.getEstado(), EstadoMatricula.CANCELADA);
        } else if (matricula.getEstado() == EstadoMatricula.ACTIVA) {
            Transiciones.exigirTransicion(matricula.getEstado(), EstadoMatricula.PENDIENTE_PAGO);
        }
    }

    private static void aplicarReversionAnulacion(Pago pago, Solicitud solicitud,
            Matricula matricula, boolean matriculaVencida, LocalDateTime fechaHora,
            LocalDateTime fechaCola, String responsable)
            throws ReglaDominioException {
        if (pago.getConcepto() == ConceptoPago.INSCRIPCION) {
            solicitud.revertirInscripcion(fechaHora, fechaCola == null ? fechaHora : fechaCola);
        } else if (matriculaVencida) {
            matricula.cancelar(fechaHora, MOTIVO_ANULACION_VENCIDA, responsable);
            solicitud.ingresarAColaFavorable(fechaCola);
        } else if (matricula.getEstado() == EstadoMatricula.ACTIVA) {
            matricula.revertirAPendiente();
        }
    }

    private static void validarRelacion(Pago pago, Solicitud solicitud, Matricula matricula,
            ArregloPagos pagos) throws ReglaDominioException {
        if (!pagos.contiene(pago)) {
            throw new ReglaDominioException("El pago no está registrado en la colección.");
        }
        if (solicitud == null || pagos.otroConfirmado(pago)) {
            throw new ReglaDominioException("El pago contradice otro pago confirmado de la misma operación.");
        }
        if (pago.getConcepto() == ConceptoPago.INSCRIPCION && matricula != null) {
            throw new ReglaDominioException("El pago de inscripción tiene una matrícula relacionada.");
        }
        if (pago.getConcepto() == ConceptoPago.MATRICULA
                && (matricula == null || matricula.getSolicitud() != solicitud)) {
            throw new ReglaDominioException("La matrícula no pertenece al pago registrado.");
        }
    }

    private static void exigirMatriculaVigenteRelacionada(Solicitud solicitud,
            ArregloMatriculas matriculas) throws ReglaDominioException {
        for (Matricula otra : matriculas.listar()) {
            if (otra.getSolicitud() == solicitud && otra.estaVigente()) {
                throw new ReglaDominioException("No se puede anular la inscripción mientras la matrícula "
                        + "vigente de " + solicitud.getCodigo()
                        + " exista; coordine primero la cancelación de la matrícula.");
            }
        }
    }

    private static LocalDateTime fechaAlFinal(Solicitud solicitud, LocalDateTime fecha,
            ArregloSolicitudes solicitudes, boolean favorable) throws ReglaDominioException {
        LocalDateTime ultima = fecha;
        ArrayList<Solicitud> cola = favorable
                ? solicitudes.colaFavorable(solicitud.getAula())
                : solicitudes.colaSinPago(solicitud.getAula());
        for (Solicitud otra : cola) {
            if (!otra.getFechaIngresoCola().isBefore(ultima)) {
                try {
                    ultima = otra.getFechaIngresoCola().plusSeconds(1);
                } catch (DateTimeException e) {
                    throw new ReglaDominioException("No se puede calcular el final de la cola.");
                }
            }
        }
        return ultima;
    }
}
