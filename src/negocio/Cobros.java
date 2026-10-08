package negocio;

import java.time.LocalDateTime;
import java.time.DateTimeException;
import java.util.ArrayList;

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

    public static void anularPago(Pago pago, String motivo, String responsable,
            LocalDateTime fechaHora, ArregloSolicitudes solicitudes,
            ArrayList<Matricula> matriculas, ArregloPagos pagos) throws ReglaDominioException {
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
        if (matricula != null && !matriculas.contains(matricula)) {
            throw new ReglaDominioException("La matrícula no está registrada en la colección.");
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
        aplicarReversionAnulacion(pago, solicitud, matricula, matriculaVencida, fechaHora, fechaCola);
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
            LocalDateTime fechaCola) throws ReglaDominioException {
        if (pago.getConcepto() == ConceptoPago.INSCRIPCION) {
            solicitud.revertirInscripcion(fechaHora, fechaCola == null ? fechaHora : fechaCola);
        } else if (matriculaVencida) {
            matricula.cancelar();
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
