package negocio;

import java.time.LocalDateTime;
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

public final class Cobros {

    private Cobros() {
    }

    public static Pago confirmarInscripcion(Solicitud solicitud, double montoPagado,
            MedioPago medio, String numeroOperacion, LocalDateTime fechaHoraOperacion,
            LocalDateTime ahora, ArregloSolicitudes solicitudes, ArrayList<Matricula> matriculas,
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
                numeroOperacion, fechaHoraOperacion, ahora, cuotas);
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
}
