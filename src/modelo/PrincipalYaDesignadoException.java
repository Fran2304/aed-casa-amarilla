package modelo;

public class PrincipalYaDesignadoException extends ReglaDominioException {

    private final Apoderado principalActual;
    private final Apoderado vinculado;

    public PrincipalYaDesignadoException(Alumno alumno, Apoderado principalActual,
            Apoderado vinculado) {
        super(vinculado.getNombreCompleto() + " quedó vinculado a "
                + alumno.getNombreCompleto() + " como adicional. "
                + principalActual.getNombreCompleto() + " sigue siendo el apoderado"
                + " principal: use designarPrincipal si de verdad quiere reemplazarlo.");
        this.principalActual = principalActual;
        this.vinculado = vinculado;
    }

    public Apoderado getPrincipalActual() {
        return principalActual;
    }

    public Apoderado getVinculado() {
        return vinculado;
    }
}
