package modelo;

import negocio.Transiciones;

public class Matricula {

    private final Aula aula;
    private EstadoMatricula estado;

    /** Nace PENDIENTE_PAGO: su creación es lo único que reserva una vacante. */
    public Matricula(Aula aula) throws DatoInvalidoException {
        if (aula == null) {
            throw new DatoInvalidoException("El aula es obligatoria.");
        }
        this.aula = aula;
        this.estado = EstadoMatricula.PENDIENTE_PAGO;
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
