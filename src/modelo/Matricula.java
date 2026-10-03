package modelo;

import negocio.Transiciones;

public class Matricula {

    private final Solicitud solicitud;
    private final Aula aula;
    private EstadoMatricula estado;

    /** Nace PENDIENTE_PAGO: su creación es lo único que reserva una vacante. */
    public Matricula(Solicitud solicitud) throws DatoInvalidoException {
        if (solicitud == null) {
            throw new DatoInvalidoException("La solicitud es obligatoria.");
        }
        this.solicitud = solicitud;
        this.aula = solicitud.getAula();
        this.estado = EstadoMatricula.PENDIENTE_PAGO;
    }

    public boolean estaVigente() {
        return estado == EstadoMatricula.PENDIENTE_PAGO || estado == EstadoMatricula.ACTIVA;
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

    public Aula getAula() {
        return aula;
    }

    public EstadoMatricula getEstado() {
        return estado;
    }
}
