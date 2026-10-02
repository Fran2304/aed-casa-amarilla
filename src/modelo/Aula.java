package modelo;

public class Aula {

    private String codigo;
    private String nombre;
    // Edad que el alumno debe tener cumplida al 31/03/2027 para entrar a esta aula.
    private int edadRequerida;
    // Las vacantes no se guardan: se calculan con la capacidad y las matrículas vigentes (issue #20).
    private int capacidad;

    public Aula(String codigo, String nombre, int edadRequerida, int capacidad) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.edadRequerida = edadRequerida;
        this.capacidad = capacidad;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public int getEdadRequerida() {
        return edadRequerida;
    }

    public int getCapacidad() {
        return capacidad;
    }
}
