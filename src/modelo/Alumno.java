package modelo;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;

public class Alumno {

    private final int codAlumno;
    private final String dni;
    private String nombres;
    private String apellidos;
    private LocalDate fechaNacimiento;
    private String celular;
    private ArrayList<Apoderado> apoderados;
    private Apoderado principal;

    public Alumno(int codAlumno, String dni, String nombres, String apellidos,
            LocalDate fechaNacimiento, String celular) throws DatoInvalidoException {
        this.codAlumno = codAlumno;
        this.dni = Validaciones.exigirDni(dni);
        this.nombres = Validaciones.exigirNoVacio("nombres", nombres);
        this.apellidos = Validaciones.exigirNoVacio("apellidos", apellidos);
        this.fechaNacimiento = exigirFechaNacimiento(fechaNacimiento);
        this.celular = Validaciones.celularOpcional(celular);
        this.apoderados = new ArrayList<Apoderado>();
    }

    public int edadAl(LocalDate corte) {
        return Period.between(fechaNacimiento, corte).getYears();
    }

    public void agregarApoderado(Apoderado apoderado, boolean esPrincipal)
            throws ReglaDominioException {
        if (apoderado == null) {
            throw new DatoInvalidoException("El apoderado es obligatorio.");
        }
        if (tieneApoderado(apoderado.getDni())) {
            throw new ReglaDominioException(apoderado.getNombreCompleto()
                    + " ya es apoderado de " + getNombreCompleto() + ".");
        }
        apoderados.add(apoderado);
        if (esPrincipal) {
            if (principal != null) {
                throw new PrincipalYaDesignadoException(this, principal, apoderado);
            }
            principal = apoderado;
        }
    }

    public void validarApoderados() throws ReglaDominioException {
        if (apoderados.isEmpty()) {
            throw new ReglaDominioException(
                    getNombreCompleto() + " debe tener al menos un apoderado.");
        }
        if (principal == null) {
            throw new ReglaDominioException(
                    "Designe un apoderado principal para " + getNombreCompleto() + ".");
        }
    }

    public void designarPrincipal(String dni) throws ReglaDominioException {
        Apoderado elegido = buscarApoderado(dni);
        if (elegido == null) {
            throw new ReglaDominioException("El apoderado indicado no está vinculado a "
                    + getNombreCompleto() + ".");
        }
        principal = elegido;
    }

    public boolean tieneApoderado(String dni) {
        return buscarApoderado(dni) != null;
    }

    private Apoderado buscarApoderado(String dni) {
        String buscado = Validaciones.normalizarDni(dni);
        if (buscado == null) {
            return null;
        }
        for (Apoderado apoderado : apoderados) {
            if (apoderado.getDni().equals(buscado)) {
                return apoderado;
            }
        }
        return null;
    }

    public ArrayList<Apoderado> getApoderados() {
        return new ArrayList<Apoderado>(apoderados);
    }

    public Apoderado getPrincipal() {
        return principal;
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
        this.nombres = Validaciones.exigirNoVacio("nombres", nombres);
    }

    public void setApellidos(String apellidos) throws DatoInvalidoException {
        this.apellidos = Validaciones.exigirNoVacio("apellidos", apellidos);
    }

    public void setFechaNacimiento(LocalDate fechaNacimiento) throws DatoInvalidoException {
        this.fechaNacimiento = exigirFechaNacimiento(fechaNacimiento);
    }

    public void setCelular(String celular) throws DatoInvalidoException {
        this.celular = Validaciones.celularOpcional(celular);
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
}
