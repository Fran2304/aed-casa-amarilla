package negocio;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import modelo.EstadoMatricula;
import modelo.EstadoPago;
import modelo.EstadoSolicitud;
import modelo.TransicionInvalidaException;

public final class Transiciones {

    private static final Map<EstadoSolicitud, Set<EstadoSolicitud>> TRANSICIONES_SOLICITUD;
    private static final Map<EstadoMatricula, Set<EstadoMatricula>> TRANSICIONES_MATRICULA;
    private static final Map<EstadoPago, Set<EstadoPago>> TRANSICIONES_PAGO;

    static {
        TRANSICIONES_SOLICITUD = new EnumMap<>(EstadoSolicitud.class);
        TRANSICIONES_SOLICITUD.put(EstadoSolicitud.EN_ESPERA_SIN_PAGO,
                Set.of(EstadoSolicitud.EN_DOCUMENTACION));
        TRANSICIONES_SOLICITUD.put(EstadoSolicitud.EN_DOCUMENTACION,
                Set.of(EstadoSolicitud.EN_ESPERA_FAVORABLE,
                        EstadoSolicitud.RECHAZADA,
                        EstadoSolicitud.CANCELADA));
        TRANSICIONES_SOLICITUD.put(EstadoSolicitud.EN_ESPERA_FAVORABLE, Set.of());
        TRANSICIONES_SOLICITUD.put(EstadoSolicitud.CANCELADA, Set.of());
        TRANSICIONES_SOLICITUD.put(EstadoSolicitud.RECHAZADA, Set.of());

        TRANSICIONES_MATRICULA = new EnumMap<>(EstadoMatricula.class);
        TRANSICIONES_MATRICULA.put(EstadoMatricula.PENDIENTE_PAGO,
                Set.of(EstadoMatricula.ACTIVA, EstadoMatricula.CANCELADA));
        TRANSICIONES_MATRICULA.put(EstadoMatricula.ACTIVA,
                Set.of(EstadoMatricula.PENDIENTE_PAGO));
        TRANSICIONES_MATRICULA.put(EstadoMatricula.CANCELADA, Set.of());

        TRANSICIONES_PAGO = new EnumMap<>(EstadoPago.class);
        TRANSICIONES_PAGO.put(EstadoPago.RECIBIDO,
                Set.of(EstadoPago.CONFIRMADO, EstadoPago.OBSERVADO));
        TRANSICIONES_PAGO.put(EstadoPago.OBSERVADO,
                Set.of(EstadoPago.CONFIRMADO));
        TRANSICIONES_PAGO.put(EstadoPago.CONFIRMADO, Set.of());
    }

    private Transiciones() {
    }

    public static boolean puedeTransicionar(EstadoSolicitud origen, EstadoSolicitud destino) {
        return origen != null
                && destino != null
                && TRANSICIONES_SOLICITUD.get(origen).contains(destino);
    }

    public static boolean puedeTransicionar(EstadoMatricula origen, EstadoMatricula destino) {
        return origen != null
                && destino != null
                && TRANSICIONES_MATRICULA.get(origen).contains(destino);
    }

    public static boolean puedeTransicionar(EstadoPago origen, EstadoPago destino) {
        return origen != null
                && destino != null
                && TRANSICIONES_PAGO.get(origen).contains(destino);
    }

    public static void exigirTransicion(EstadoSolicitud origen, EstadoSolicitud destino)
            throws TransicionInvalidaException {
        if (!puedeTransicionar(origen, destino)) {
            throw new TransicionInvalidaException(
                    "Una solicitud no puede pasar de " + origen + " a " + destino + ".");
        }
    }

    public static void exigirTransicion(EstadoMatricula origen, EstadoMatricula destino)
            throws TransicionInvalidaException {
        if (!puedeTransicionar(origen, destino)) {
            throw new TransicionInvalidaException(
                    "Una matrícula no puede pasar de " + origen + " a " + destino + ".");
        }
    }

    public static void exigirTransicion(EstadoPago origen, EstadoPago destino)
            throws TransicionInvalidaException {
        if (!puedeTransicionar(origen, destino)) {
            throw new TransicionInvalidaException(
                    "Un pago no puede pasar de " + origen + " a " + destino + ".");
        }
    }
}
