package modelo;

public class SolicitudDuplicadaException extends ReglaDominioException {

    private final Solicitud existente;

    public SolicitudDuplicadaException(Solicitud existente) {
        super("Ya existe " + existente.getCodigo() + " para "
                + existente.getAlumno().getNombreCompleto() + " en 2027.");
        this.existente = existente;
    }

    public Solicitud getExistente() {
        return existente;
    }
}
