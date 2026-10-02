package modelo;

public enum TipoDocumento {
    DNI_ALUMNO("DNI del alumno"),
    DNI_APODERADO_PRINCIPAL("DNI del apoderado principal"),
    PARTIDA_NACIMIENTO("Partida de nacimiento"),
    CARNE_VACUNAS("Carné de vacunas");

    private final String descripcion;

    TipoDocumento(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
