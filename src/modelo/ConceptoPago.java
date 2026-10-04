package modelo;

public enum ConceptoPago {
    INSCRIPCION("inscripción"),
    MATRICULA("matrícula");

    private final String descripcion;

    ConceptoPago(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
