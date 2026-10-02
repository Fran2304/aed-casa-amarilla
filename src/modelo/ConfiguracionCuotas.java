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

    public void setCuotaInscripcion(double monto) throws DatoInvalidoException {
        cuotaInscripcion = exigirPositivo("cuota de inscripción", monto);
    }

    public void setCuotaMatricula(double monto) throws DatoInvalidoException {
        cuotaMatricula = exigirPositivo("cuota de matrícula", monto);
    }

    private static double exigirPositivo(String campo, double monto) throws DatoInvalidoException {
        if (monto <= 0) {
            throw new DatoInvalidoException("La " + campo + " debe ser mayor que 0.");
        }
        return monto;
    }
}
