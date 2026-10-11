package util;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class Reloj {

    private static final Reloj POR_DEFECTO = new Reloj();
    private LocalDateTime fijado;

    public static Reloj porDefecto() {
        return POR_DEFECTO;
    }

    public LocalDateTime now() {
        return fijado == null ? LocalDateTime.now() : fijado;
    }

    public LocalDate fechaActual() {
        return now().toLocalDate();
    }

    public void fijar(LocalDateTime fechaHora) {
        if (fechaHora == null) {
            throw new IllegalArgumentException("La fecha y hora del reloj es obligatoria.");
        }
        fijado = fechaHora;
    }

    public void avanzar(java.time.Duration duracion) {
        if (duracion == null) {
            throw new IllegalArgumentException("La duración es obligatoria.");
        }
        fijar(now().plus(duracion));
    }

    public void restablecer() {
        fijado = null;
    }
}
