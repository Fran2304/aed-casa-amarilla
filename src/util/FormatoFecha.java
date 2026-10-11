package util;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class FormatoFecha {

    private static final String[] MESES = { "ene", "feb", "mar", "abr", "may", "jun", "jul",
            "ago", "sep", "oct", "nov", "dic" };

    private FormatoFecha() {
    }

    public static String dia(LocalDate fecha) {
        return fecha.getDayOfMonth() + " " + MESES[fecha.getMonthValue() - 1];
    }

    public static String diaHora(LocalDateTime fecha) {
        return dia(fecha.toLocalDate())
                + String.format(" · %02d:%02d", fecha.getHour(), fecha.getMinute());
    }
}
