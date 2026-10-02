package modelo;

public class AlumnoDuplicadoException extends ReglaDominioException {

    private final Alumno existente;

    public AlumnoDuplicadoException(Alumno existente) {
        super("El DNI " + existente.getDni() + " ya está registrado para "
                + existente.getNombreCompleto() + " (código " + existente.getCodAlumno() + ").");
        this.existente = existente;
    }

    public Alumno getExistente() {
        return existente;
    }
}
