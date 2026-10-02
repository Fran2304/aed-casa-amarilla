package modelo;

public class ApoderadoDuplicadoException extends ReglaDominioException {

    private final Apoderado existente;

    public ApoderadoDuplicadoException(Apoderado existente) {
        super("El DNI " + existente.getDni() + " ya está registrado como "
                + existente.getNombreCompleto() + ".");
        this.existente = existente;
    }

    public Apoderado getExistente() {
        return existente;
    }
}
