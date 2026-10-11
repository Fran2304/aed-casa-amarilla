package util;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Fechas cortas del diseño («22 sep · 09:00»): todo el proceso cae en un mismo año. */
public final class FormatoFecha {

    // Fijos en vez de Locale("es"): según la versión del JDK, septiembre sale «sept.».
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
