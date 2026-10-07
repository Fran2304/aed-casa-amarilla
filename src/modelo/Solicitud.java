package modelo;

import java.time.LocalDateTime;
import java.util.ArrayList;

import negocio.ExpedienteDocumentos;
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
    // La confirmación limpia la habilitación; se conserva la original para reevaluar el
    // remanente de las 48 h si luego se anula el pago de inscripción (§4.7).
    private LocalDateTime fechaHabilitacionOriginal;
    private LocalDateTime fechaConfirmacionInscripcion;
    private ExpedienteDocumentos expediente;
    private ArrayList<Oferta> ofertas;

    public Solicitud(String codigo, Alumno alumno, Aula aula, LocalDateTime fechaRegistro) {
        this.codigo = codigo;
        this.alumno = alumno;
        this.aula = aula;
        this.fechaRegistro = fechaRegistro;
        // El flujo no nombra un estado para "registrada, sin evaluar vacante" (§6.6) y no se
        // inventan estados: nace en el único estado de origen de la tabla de Transiciones.
        // Solo está en la cola cuando además tiene fecha de ingreso (ingresarAColaSinPago).
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
        if (nuevo == EstadoSolicitud.EN_DOCUMENTACION) {
            throw new TransicionInvalidaException(codigo
                    + " pasa a documentación con confirmarInscripcion y su fecha.");
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

    public void confirmarInscripcion(LocalDateTime fecha) throws ReglaDominioException {
        if (fecha == null) {
            throw new DatoInvalidoException("La fecha de confirmación de la inscripción es"
                    + " obligatoria.");
        }
        if (estado != EstadoSolicitud.HABILITADA_PARA_PAGO) {
            throw new ReglaDominioException(codigo + " está en " + estado
                    + " y no está habilitada para confirmar la inscripción.");
        }
        if (habilitacionVencida(fecha)) {
            throw new ReglaDominioException("La habilitación de " + codigo + " venció el "
                    + getVencimientoHabilitacion() + ".");
        }
        ExpedienteDocumentos nuevo = new ExpedienteDocumentos(fecha.toLocalDate());
        LocalDateTime habilitacion = fechaHabilitacion;
        aplicarTransicion(EstadoSolicitud.EN_DOCUMENTACION);
        fechaHabilitacionOriginal = habilitacion;
        fechaConfirmacionInscripcion = fecha;
        expediente = nuevo;
    }

    /**
     * Revierte una inscripción confirmada por error de registro (§4.7). Recupera la
     * habilitación original: si sus 48 h siguen vigentes, la solicitud vuelve a
     * {@code HABILITADA_PARA_PAGO} con el remanente (no se reinicia el plazo); si ya
     * venció, vuelve al final de {@code EN_ESPERA_SIN_PAGO} con {@code fechaReingreso}.
     */
    public void revertirInscripcion(LocalDateTime ahora, LocalDateTime fechaReingreso)
            throws ReglaDominioException {
        if (ahora == null) {
            throw new DatoInvalidoException("La fecha de reversión es obligatoria.");
        }
        if (fechaReingreso == null) {
            throw new DatoInvalidoException("La fecha de reingreso a la cola es obligatoria.");
        }
        if (estado != EstadoSolicitud.EN_DOCUMENTACION) {
            throw new ReglaDominioException(codigo + " está en " + estado
                    + " y no tiene una inscripción confirmada que revertir.");
        }
        if (fechaHabilitacionOriginal == null) {
            throw new ReglaDominioException(codigo + " no conserva la habilitación original"
                    + " para revertir la inscripción.");
        }
        LocalDateTime habilitacion = fechaHabilitacionOriginal;
        aplicarTransicion(EstadoSolicitud.HABILITADA_PARA_PAGO);
        fechaHabilitacion = habilitacion;
        fechaConfirmacionInscripcion = null;
        expediente = null;
        // Criterio §6.5 (pendiente #5): si el plazo original ya venció al reevaluar, no se
        // reinicia ni se mantiene la habilitación. La misma solicitud vuelve al final de
        // EN_ESPERA_SIN_PAGO con fecha nueva.
        if (habilitacionVencida(ahora)) {
            aplicarTransicion(EstadoSolicitud.EN_ESPERA_SIN_PAGO);
            ingresarAColaSinPago(fechaReingreso);
        }
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

    public LocalDateTime getFechaConfirmacionInscripcion() {
        return fechaConfirmacionInscripcion;
    }

    public ExpedienteDocumentos getExpediente() {
        return expediente;
    }

    public ArrayList<Oferta> getOfertas() {
        return new ArrayList<Oferta>(ofertas);
    }
}
