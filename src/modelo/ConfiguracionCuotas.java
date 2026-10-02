package modelo;

// Cada pago debe copiar el monto vigente al registrarse (getCuota...), no guardar una
// referencia a esta configuración: así un cambio de cuota posterior no altera pagos ya hechos.
public class ConfiguracionCuotas {

    private double cuotaInscripcion;
    private double cuotaMatricula;

    public ConfiguracionCuotas() {
        // Valores de ejemplo del diseño para 2027.
        cuotaInscripcion = 180;
        cuotaMatricula = 850;
    }

    public double getCuotaInscripcion() {
        return cuotaInscripcion;
    }

    public double getCuotaMatricula() {
        return cuotaMatricula;
    }

    // Valida las dos antes de asignar: si una falla, no queda la mitad aplicada.
    public void setCuotas(double inscripcion, double matricula) throws DatoInvalidoException {
        exigirPositivo("cuota de inscripción", inscripcion);
        exigirPositivo("cuota de matrícula", matricula);
        cuotaInscripcion = inscripcion;
        cuotaMatricula = matricula;
    }

    // isFinite descarta NaN e Infinity: "NaN <= 0" es false y se colaría como cuota válida.
    private static void exigirPositivo(String campo, double monto) throws DatoInvalidoException {
        if (!Double.isFinite(monto) || monto <= 0) {
            throw new DatoInvalidoException("La " + campo + " debe ser mayor que 0.");
        }
    }
}
