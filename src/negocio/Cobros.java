package negocio;

import java.time.LocalDateTime;
import java.util.ArrayList;

import datos.ArregloPagos;
import datos.ArregloSolicitudes;
import modelo.ConceptoPago;
import modelo.ConfiguracionCuotas;
import modelo.DatoInvalidoException;
import modelo.EstadoMatricula;
import modelo.Matricula;
import modelo.MedioPago;
import modelo.Pago;
import modelo.ReglaDominioException;
import modelo.Solicitud;
import modelo.Validaciones;

public final class Cobros {

    private Cobros() {
    }

    public static Pago confirmarInscripcion(Solicitud solicitud, double montoPagado,
            MedioPago medio, String numeroOperacion, LocalDateTime fechaHoraOperacion,
            String rutaComprobantePago, LocalDateTime ahora, ArregloSolicitudes solicitudes,
            ArrayList<Matricula> matriculas,
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
        exigirHabilitacionVigente(solicitud, solicitudes, ahora);
        Turnos.exigirTurno(solicitud, solicitudes, matriculas, ahora);

        Pago pago = new Pago(solicitud, ConceptoPago.INSCRIPCION, null, montoPagado, medio,
                numeroOperacion, fechaHoraOperacion, ahora, rutaComprobantePago, cuotas);
        pago.confirmar();
        solicitud.confirmarInscripcion(ahora);
        pagos.agregar(pago);
        return pago;
    }

    private static void exigirHabilitacionVigente(Solicitud solicitud,
            ArregloSolicitudes solicitudes, LocalDateTime ahora) throws ReglaDominioException {
        if (!solicitud.habilitacionVencida(ahora)) {
            return;
        }
        LocalDateTime vencimiento = solicitud.getVencimientoHabilitacion();
        solicitudes.vencerHabilitaciones(ahora);
        throw new ReglaDominioException("La habilitación de " + solicitud.getCodigo()
                + " venció el " + vencimiento + "; volvió al final de la cola sin pago.");
    }

    /**
     * Anula un pago de inscripción {@code CONFIRMADO} por error de registro y revierte la
     * solicitud (§4.7). El pago no se edita: se marca {@code ANULADO} conservando su
     * historial. Solo {@code Personal} anula (responsable obligatorio) y el motivo también
     * lo es.
     */
    public static Pago anularInscripcion(Pago pago, String motivo, String responsable,
            LocalDateTime ahora, ArregloSolicitudes solicitudes)
            throws ReglaDominioException {
        exigirPagoAnulable(pago, ConceptoPago.INSCRIPCION, motivo, responsable, ahora);
        Solicitud solicitud = pago.getSolicitud();
        LocalDateTime reingreso = solicitudes.fechaIngresoAlFinal(solicitud, ahora);
        solicitud.revertirInscripcion(ahora, reingreso);
        return anular(pago, motivo, responsable, ahora);
    }

    /**
     * Anula un pago de matrícula {@code CONFIRMADO} por error de registro y revierte la
     * matrícula (§4.7): conserva el plazo original de 72 h y, si ya venció, la cancela y
     * devuelve la solicitud al final de {@code EN_ESPERA_FAVORABLE} conservando la
     * inscripción pagada. El pago no se edita: se marca {@code ANULADO}.
     */
    public static Pago anularMatricula(Pago pago, String motivo, String responsable,
            LocalDateTime ahora, ArregloSolicitudes solicitudes)
            throws ReglaDominioException {
        exigirPagoAnulable(pago, ConceptoPago.MATRICULA, motivo, responsable, ahora);
        revertirMatricula(pago, ahora, solicitudes);
        return anular(pago, motivo, responsable, ahora);
    }

    // Se validan estado, concepto, motivo y responsable antes de tocar la solicitud o la
    // matrícula: si algo falla, el pago sigue CONFIRMADO y no queda una operación a medias.
    private static void exigirPagoAnulable(Pago pago, ConceptoPago concepto, String motivo,
            String responsable, LocalDateTime ahora) throws ReglaDominioException {
        if (pago == null) {
            throw new DatoInvalidoException("El pago es obligatorio.");
        }
        if (ahora == null) {
            throw new DatoInvalidoException("La fecha y hora de anulación es obligatoria.");
        }
        if (pago.getConcepto() != concepto) {
            throw new ReglaDominioException("El pago no es de " + concepto.getDescripcion()
                    + ".");
        }
        if (!pago.estaConfirmado()) {
            throw new ReglaDominioException("Solo se anula un pago confirmado; el pago está en "
                    + pago.getEstado() + ".");
        }
        Validaciones.exigirNoVacio("motivo de anulación", motivo);
        Validaciones.exigirNoVacio("responsable de la anulación", responsable);
    }

    private static Pago anular(Pago pago, String motivo, String responsable,
            LocalDateTime ahora) throws ReglaDominioException {
        pago.anular(motivo, responsable, ahora);
        return pago;
    }

    private static void revertirMatricula(Pago pago, LocalDateTime ahora,
            ArregloSolicitudes solicitudes) throws ReglaDominioException {
        Matricula matricula = pago.getMatricula();
        if (matricula == null) {
            throw new ReglaDominioException("El pago de matrícula no tiene matrícula asociada.");
        }
        matricula.revertirPago();
        // Criterio §6.5 (pendiente #5): si el plazo original de 72 h ya venció al reevaluar,
        // no se conserva la reserva. La matrícula se cancela (libera la vacante) y la misma
        // solicitud vuelve al final de EN_ESPERA_FAVORABLE con fecha nueva.
        if (matricula.plazoPagoVencido(ahora)) {
            matricula.cancelar();
            Solicitud solicitud = matricula.getSolicitud();
            LocalDateTime reingreso = solicitudes.fechaIngresoAlFinal(solicitud, ahora);
            solicitud.ingresarAColaFavorable(reingreso);
        }
    }
}
