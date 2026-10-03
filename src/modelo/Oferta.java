package modelo;

import java.time.LocalDateTime;

/** Oferta de vacante a una solicitud de la cola favorable. Queda como evento del historial. */
public class Oferta {

    private final LocalDateTime fechaHora;
    // No hay clase Personal todavía: se registra el nombre de quien confirmó la oferta.
    private final String personal;
    private final boolean aceptada;

    public Oferta(LocalDateTime fechaHora, String personal, boolean aceptada)
            throws DatoInvalidoException {
        if (fechaHora == null) {
            throw new DatoInvalidoException("La fecha y hora de la oferta es obligatoria.");
        }
        this.fechaHora = fechaHora;
        this.personal = Validaciones.exigirNoVacio("personal", personal);
        this.aceptada = aceptada;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }

    public String getPersonal() {
        return personal;
    }

    public boolean isAceptada() {
        return aceptada;
    }
}
