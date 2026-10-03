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
    // null mientras no entre a la cola: así se distingue la recién registrada (que aún puede
    // ir por la vía directa) de la que espera turno, sin inventar un estado (§6.6).
    // Es la fecha de la cola en la que está ahora; cuál cola, lo dice el estado.
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

    // Entrevista favorable sin vacante (§4.6): conserva la inscripción pagada y espera turno.
    public void ingresarAColaFavorable(LocalDateTime fecha) throws ReglaDominioException {
        if (fecha == null) {
            throw new DatoInvalidoException("La fecha de ingreso a la cola es obligatoria.");
        }
        cambiarEstado(EstadoSolicitud.EN_ESPERA_FAVORABLE);
        fechaIngresoCola = fecha;
    }

    public boolean estaEnColaFavorable() {
        return estado == EstadoSolicitud.EN_ESPERA_FAVORABLE;
    }

    // Criterio del grupo para §6.3 (oferta no aceptada): la solicitud vuelve al final de la
    // cola favorable con fecha nueva, igual que una invitación vencida en la cola sin pago
    // (§4.2). Conserva la inscripción pagada y no se crea matrícula.
    public void reingresarAColaFavorable(LocalDateTime fecha) throws ReglaDominioException {
        if (fecha == null) {
            throw new DatoInvalidoException("La fecha de ingreso a la cola es obligatoria.");
        }
        if (!estaEnColaFavorable()) {
            throw new ReglaDominioException(codigo + " está en " + estado
                    + " y no puede reingresar a la cola favorable.");
        }
        fechaIngresoCola = fecha;
    }

    public void registrarOferta(Oferta oferta) throws DatoInvalidoException {
        if (oferta == null) {
            throw new DatoInvalidoException("La oferta es obligatoria.");
        }
        ofertas.add(oferta);
    }

    // Vale la última oferta: una rechazada antes no impide aceptar una posterior.
    public boolean tieneOfertaAceptada() {
        return !ofertas.isEmpty() && ofertas.get(ofertas.size() - 1).isAceptada();
    }

    // Lo llaman cobro, documentos y entrevista: en la cola no se hace ninguno de los tres (§4.2).
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
