package modelo;

import java.time.LocalDateTime;

import negocio.Transiciones;

public class Solicitud {

    private final String codigo;
    private final Alumno alumno;
    private final Aula aula;
    private final LocalDateTime fechaRegistro;
    private EstadoSolicitud estado;

    public Solicitud(String codigo, Alumno alumno, Aula aula, LocalDateTime fechaRegistro) {
        this.codigo = codigo;
        this.alumno = alumno;
        this.aula = aula;
        this.fechaRegistro = fechaRegistro;
        // El flujo no nombra un estado para "registrada, sin evaluar vacante" (§6.6) y no se
        // inventan estados: nace en el único estado de origen de la tabla de Transiciones.
        // La vía directa pasa a EN_DOCUMENTACION al confirmarse el pago de inscripción.
        this.estado = EstadoSolicitud.EN_ESPERA_SIN_PAGO;
    }

    public void cambiarEstado(EstadoSolicitud nuevo) throws TransicionInvalidaException {
        Transiciones.exigirTransicion(estado, nuevo);
        estado = nuevo;
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
