package modelo;

import java.time.LocalDateTime;
import java.util.ArrayList;

import negocio.Transiciones;

public class Solicitud {

    public static final int HORAS_HABILITACION = 48;

    private final String codigo;
    private final Alumno alumno;
    private final Aula aula;
    private final LocalDateTime fechaRegistro;
    private EstadoSolicitud estado;
    private LocalDateTime fechaIngresoCola;
    private LocalDateTime fechaHabilitacion;
    private ArrayList<Oferta> ofertas;

    public Solicitud(String codigo, Alumno alumno, Aula aula, LocalDateTime fechaRegistro) {
        this.codigo = codigo;
        this.alumno = alumno;
        this.aula = aula;
        this.fechaRegistro = fechaRegistro;
        // El flujo no nombra un estado para "registrada, sin evaluar vacante" (§6.6) y no se
        // inventan estados: nace en el único estado de origen de la tabla de Transiciones.
        // Solo está en la cola cuando además tiene fecha de ingreso (ingresarAColaSinPago).
        // Turnos.ubicarNueva la habilita para pago o la pone en la cola justo al registrarla.
        this.estado = EstadoSolicitud.EN_ESPERA_SIN_PAGO;
        this.ofertas = new ArrayList<Oferta>();
    }

    public void cambiarEstado(EstadoSolicitud nuevo) throws TransicionInvalidaException {
        if (nuevo == EstadoSolicitud.EN_ESPERA_FAVORABLE) {
            throw new TransicionInvalidaException(codigo
                    + " entra a la cola favorable con ingresarAColaFavorable y su fecha.");
        }
        if (nuevo == EstadoSolicitud.HABILITADA_PARA_PAGO) {
            throw new TransicionInvalidaException(codigo
                    + " se habilita con habilitarParaPago y su fecha.");
        }
        if (estado == EstadoSolicitud.HABILITADA_PARA_PAGO
                && nuevo == EstadoSolicitud.EN_ESPERA_SIN_PAGO) {
            throw new TransicionInvalidaException(codigo
                    + " vuelve a la cola con vencerHabilitacion, al final y con fecha nueva.");
        }
        aplicarTransicion(nuevo);
    }

    private void aplicarTransicion(EstadoSolicitud nuevo) throws TransicionInvalidaException {
        Transiciones.exigirTransicion(estado, nuevo);
        estado = nuevo;
        fechaIngresoCola = null;
        fechaHabilitacion = null;
    }

    // También sirve para reingresar tras vencer la habilitación (vencerHabilitacion): la fecha
    // nueva es la más reciente, así que la solicitud queda al final de la cola.
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

    public void habilitarParaPago(LocalDateTime fecha) throws ReglaDominioException {
        if (fecha == null) {
            throw new DatoInvalidoException("La fecha de habilitación es obligatoria.");
        }
        if (estado != EstadoSolicitud.EN_ESPERA_SIN_PAGO) {
            throw new ReglaDominioException(codigo + " está en " + estado
                    + " y solo se habilita una solicitud nueva o en espera sin pago.");
        }
        aplicarTransicion(EstadoSolicitud.HABILITADA_PARA_PAGO);
        fechaHabilitacion = fecha;
    }

    public LocalDateTime getVencimientoHabilitacion() {
        if (fechaHabilitacion == null) {
            return null;
        }
        return fechaHabilitacion.plusHours(HORAS_HABILITACION);
    }

    public boolean habilitacionVencida(LocalDateTime ahora) {
        return estado == EstadoSolicitud.HABILITADA_PARA_PAGO
                && !ahora.isBefore(getVencimientoHabilitacion());
    }

    // No hay temporizador: el vencimiento se evalúa con la fecha guardada cuando se revisa.
    public void vencerHabilitacion(LocalDateTime fecha) throws ReglaDominioException {
        if (fecha == null) {
            throw new DatoInvalidoException("La fecha de vencimiento es obligatoria.");
        }
        if (estado != EstadoSolicitud.HABILITADA_PARA_PAGO) {
            throw new ReglaDominioException(codigo + " está en " + estado
                    + " y no tiene una habilitación para pago que vencer.");
        }
        if (!habilitacionVencida(fecha)) {
            throw new ReglaDominioException("La habilitación de " + codigo + " vence el "
                    + getVencimientoHabilitacion() + ".");
        }
        aplicarTransicion(EstadoSolicitud.EN_ESPERA_SIN_PAGO);
        ingresarAColaSinPago(fecha);
    }

    public void ingresarAColaFavorable(LocalDateTime fecha) throws ReglaDominioException {
        if (fecha == null) {
            throw new DatoInvalidoException("La fecha de ingreso a la cola es obligatoria.");
        }
        if (estado != EstadoSolicitud.EN_ESPERA_FAVORABLE) {
            aplicarTransicion(EstadoSolicitud.EN_ESPERA_FAVORABLE);
        }
        fechaIngresoCola = fecha;
    }

    public boolean estaEnColaFavorable() {
        return estado == EstadoSolicitud.EN_ESPERA_FAVORABLE && fechaIngresoCola != null;
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

    public LocalDateTime getFechaHabilitacion() {
        return fechaHabilitacion;
    }

    public ArrayList<Oferta> getOfertas() {
        return new ArrayList<Oferta>(ofertas);
    }
}
