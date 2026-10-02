package modelo;

import java.time.LocalDate;
import java.time.Period;

public class Alumno {

    private static final int LARGO_DNI = 8;
    private static final int LARGO_CELULAR = 9;

    private final int codAlumno;
    private final String dni;
    private String nombres;
    private String apellidos;
    private LocalDate fechaNacimiento;
    private String celular;

    public Alumno(int codAlumno, String dni, String nombres, String apellidos,
            LocalDate fechaNacimiento, String celular) throws DatoInvalidoException {
        this.codAlumno = codAlumno;
        this.dni = exigirDigitos("DNI", dni, LARGO_DNI);
        this.nombres = exigirNoVacio("nombres", nombres);
        this.apellidos = exigirNoVacio("apellidos", apellidos);
        this.fechaNacimiento = exigirFechaNacimiento(fechaNacimiento);
        this.celular = celularOpcional(celular);
    }

    public int edadAl(LocalDate corte) {
        return Period.between(fechaNacimiento, corte).getYears();
    }

    public int getCodAlumno() {
        return codAlumno;
    }

    public String getDni() {
        return dni;
    }

    public String getNombres() {
        return nombres;
    }

    public String getApellidos() {
        return apellidos;
    }

    public LocalDate getFechaNacimiento() {
        return fechaNacimiento;
    }

    public String getCelular() {
        return celular;
    }

    public String getNombreCompleto() {
        return nombres + " " + apellidos;
    }

    public void setNombres(String nombres) throws DatoInvalidoException {
        this.nombres = exigirNoVacio("nombres", nombres);
    }

    public void setApellidos(String apellidos) throws DatoInvalidoException {
        this.apellidos = exigirNoVacio("apellidos", apellidos);
    }

    public void setFechaNacimiento(LocalDate fechaNacimiento) throws DatoInvalidoException {
        this.fechaNacimiento = exigirFechaNacimiento(fechaNacimiento);
    }

    public void setCelular(String celular) throws DatoInvalidoException {
        this.celular = celularOpcional(celular);
    }

    private static String exigirNoVacio(String campo, String valor) throws DatoInvalidoException {
        if (valor == null || valor.trim().isEmpty()) {
            throw new DatoInvalidoException("El campo " + campo + " es obligatorio.");
        }
        return valor.trim();
    }

    private static String exigirDigitos(String campo, String valor, int largo)
            throws DatoInvalidoException {
        String limpio = exigirNoVacio(campo, valor);
        if (limpio.length() != largo) {
            throw new DatoInvalidoException(
                    "El " + campo + " debe tener " + largo + " dígitos.");
        }
        for (int i = 0; i < limpio.length(); i++) {
            char c = limpio.charAt(i);
            if (c < '0' || c > '9') {
                throw new DatoInvalidoException("El " + campo + " solo admite dígitos.");
            }
        }
        return limpio;
    }

    private static LocalDate exigirFechaNacimiento(LocalDate fecha) throws DatoInvalidoException {
        if (fecha == null) {
            throw new DatoInvalidoException("La fecha de nacimiento es obligatoria.");
        }
        if (fecha.isAfter(LocalDate.now())) {
            throw new DatoInvalidoException("La fecha de nacimiento no puede ser futura.");
        }
        return fecha;
    }

    private static String celularOpcional(String celular) throws DatoInvalidoException {
        if (celular == null || celular.trim().isEmpty()) {
            return "";
        }
        return exigirDigitos("celular", celular, LARGO_CELULAR);
    }
}
