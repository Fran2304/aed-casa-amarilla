package modelo;

public class Apoderado {

    private final String dni;
    private String nombres;
    private String apellidos;
    private String celular;

    // El celular obligatorio queda a confirmar con el nido: §6.2 del flujo deja
    // pendiente la lista de datos obligatorios.
    public Apoderado(String dni, String nombres, String apellidos, String celular)
            throws DatoInvalidoException {
        this.dni = Validaciones.exigirDni(dni);
        this.nombres = Validaciones.exigirNoVacio("nombres", nombres);
        this.apellidos = Validaciones.exigirNoVacio("apellidos", apellidos);
        this.celular = Validaciones.exigirCelular(celular);
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

    public void setCelular(String celular) throws DatoInvalidoException {
        this.celular = Validaciones.exigirCelular(celular);
    }
}
