package util;

import java.time.LocalDate;
import java.time.Period;

public final class FechasAdmision {

    public static final LocalDate CORTE_2027 = LocalDate.of(2027, 3, 31);

    private FechasAdmision() {
    }

    public static int edadAlCorte(LocalDate nacimiento) {
        return Period.between(nacimiento, CORTE_2027).getYears();
    }
}
