package util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public final class FormatoFecha {

    public static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern(
            "dd/MM/yyyy HH:mm");

    private static final String[] MESES = { "ene", "feb", "mar", "abr", "may", "jun", "jul",
            "ago", "sep", "oct", "nov", "dic" };

    private FormatoFecha() {
    }

    public static String dia(LocalDate fecha) {
        return fecha.getDayOfMonth() + " " + MESES[fecha.getMonthValue() - 1];
    }

    public static LocalDateTime alMinuto(LocalDateTime fecha) {
        return fecha.truncatedTo(ChronoUnit.MINUTES);
    }

    public static String fechaHora(LocalDateTime fecha) {
        return fecha.format(FECHA_HORA);
    }

    public static String diaHora(LocalDateTime fecha) {
        return dia(fecha.toLocalDate())
                + String.format(" · %02d:%02d", fecha.getHour(), fecha.getMinute());
    }
}
