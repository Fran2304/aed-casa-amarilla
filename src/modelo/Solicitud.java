package modelo;

import java.time.LocalDateTime;
import java.util.ArrayList;

import negocio.Transiciones;

public class Solicitud {

    private final String codigo;
    private final Alumno alumno;
    private final Aula aula;
    private final LocalDateTime fechaRegistro;
    private EstadoSolicitud estado;
    private LocalDateTime fechaIngresoCola;
    private ArrayList<Oferta> ofertas;

    public Solicitud(String codigo, Alumno alumno, Aula aula, LocalDateTime fechaRegistro) {
        this.codigo = codigo;
        this.alumno = alumno;
        this.aula = aula;
        this.fechaRegistro = fechaRegistro;
        // El flujo no nombra un estado para "registrada, sin evaluar vacante" (§6.6) y no se
        // inventan estados: nace en el único estado de origen de la tabla de Transiciones.
        // Solo está en la cola cuando además tiene fecha de ingreso (ingresarAColaSinPago).
        // La vía directa pasa a EN_DOCUMENTACION al confirmarse el pago de inscripción.
        this.estado = EstadoSolicitud.EN_ESPERA_SIN_PAGO;
        this.ofertas = new ArrayList<Oferta>();
    }

    public void cambiarEstado(EstadoSolicitud nuevo) throws TransicionInvalidaException {
        Transiciones.exigirTransicion(estado, nuevo);
        estado = nuevo;
    }

    // También sirve para reingresar tras una invitación vencida (#9): la fecha nueva es la
    // más reciente, así que la solicitud queda al final de la cola.
    public void ingresarAColaSinPago(LocalDateTime fecha) throws ReglaDominioException {
        if (fecha == null) {
            throw new DatoInvalidoException("La fecha de ingreso a la cola es obligatoria.");
        }
        if (estado != EstadoSolicitud.EN_ESPERA_SIN_PAGO) {
            throw new ReglaDominioException(codigo + " está en " + estado
                    + " y no puede entrar a la cola de espera sin pago.");
        }
        fechaIngresoCola = fecha;
    }

    public boolean estaEnColaSinPago() {
        return estado == EstadoSolicitud.EN_ESPERA_SIN_PAGO && fechaIngresoCola != null;
    }

    public void ingresarAColaFavorable(LocalDateTime fecha) throws ReglaDominioException {
        if (fecha == null) {
            throw new DatoInvalidoException("La fecha de ingreso a la cola es obligatoria.");
        }
        if (estado != EstadoSolicitud.EN_ESPERA_FAVORABLE) {
            cambiarEstado(EstadoSolicitud.EN_ESPERA_FAVORABLE);
        }
        fechaIngresoCola = fecha;
    }

    public boolean estaEnColaFavorable() {
        return estado == EstadoSolicitud.EN_ESPERA_FAVORABLE;
    }

    public void registrarOferta(Oferta oferta) throws DatoInvalidoException {
        if (oferta == null) {
            throw new DatoInvalidoException("La oferta es obligatoria.");
        }
        ofertas.add(oferta);
    }

    public boolean tieneOfertaAceptada() {
        return !ofertas.isEmpty() && ofertas.get(ofertas.size() - 1).isAceptada();
    }

    public void exigirFueraDeColaSinPago(String operacion) throws ReglaDominioException {
        if (estaEnColaSinPago()) {
            throw new ReglaDominioException(codigo + " está en espera sin pago: no se puede "
                    + operacion + ".");
        }
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

    public LocalDateTime getFechaIngresoCola() {
        return fechaIngresoCola;
    }

    public ArrayList<Oferta> getOfertas() {
        return new ArrayList<Oferta>(ofertas);
    }
}
