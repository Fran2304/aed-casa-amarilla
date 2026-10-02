package modelo;

import java.time.LocalDateTime;

public class Solicitud {

    private final String codigo;
    private final Alumno alumno;
    private final Aula aula;
    private final LocalDateTime fechaRegistro;
    // Nace en null: el flujo no nombra un estado para "registrada, sin evaluar vacante"
    // (§6.6). La evaluación de vacante y prioridad (#21) le asigna el primero.
    private EstadoSolicitud estado;

    public Solicitud(String codigo, Alumno alumno, Aula aula, LocalDateTime fechaRegistro) {
        this.codigo = codigo;
        this.alumno = alumno;
        this.aula = aula;
        this.fechaRegistro = fechaRegistro;
    }

    // Canceladas y rechazadas quedan como historial y no bloquean una solicitud nueva.
    public boolean estaActiva() {
        return estado != EstadoSolicitud.CANCELADA && estado != EstadoSolicitud.RECHAZADA;
    }

    public String getCodigo() {
        return codigo;
    }

    public Alumno getAlumno() {
        return alumno;
    }

    public Aula getAula() {
        return aula;
    }

    public LocalDateTime getFechaRegistro() {
        return fechaRegistro;
    }

    public EstadoSolicitud getEstado() {
        return estado;
    }
}
