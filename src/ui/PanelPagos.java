package ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import datos.ArregloPagos;
import datos.ArregloSolicitudes;
import modelo.ConceptoPago;
import modelo.ConfiguracionCuotas;
import modelo.EstadoSolicitud;
import modelo.Matricula;
import modelo.MedioPago;
import modelo.Pago;
import modelo.ReglaDominioException;
import modelo.Solicitud;
import negocio.Cobros;
import negocio.Turnos;

// Pago de inscripción (issue #15). El diseño no tiene pantalla propia para inscripción:
// reutiliza las tarjetas del frame 05; la cabecera de matrícula llega con #26.
public class PanelPagos extends JPanel {

    private static final DateTimeFormatter FORMATO_DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ArregloSolicitudes solicitudes;
    private final ArregloPagos pagos;
    private final ArrayList<Matricula> matriculas;
    private final ConfiguracionCuotas cuotas;

    private Solicitud actual;

    private final JTextField campoCodigo = Estilos.campo(new JTextField(12));
    private final JPanel resumen = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    private final TarjetasPago tarjetas;

    public PanelPagos(ArregloSolicitudes solicitudes, ArregloPagos pagos,
            ArrayList<Matricula> matriculas, ConfiguracionCuotas cuotas) {
        super(new BorderLayout(0, 20));
        this.solicitudes = solicitudes;
        this.pagos = pagos;
        this.matriculas = matriculas;
        this.cuotas = cuotas;
        setBorder(BorderFactory.createEmptyBorder(32, 32, 32, 34));

        tarjetas = new TarjetasPago("Pago de inscripción · comprobante recibido",
                "Inscripción 2027", "Confirmar pago → EN_DOCUMENTACION",
                "Fecha real dentro de la habilitación (48 h)", this::confirmar);

        add(crearEncabezado(), BorderLayout.NORTH);
        JPanel centro = new JPanel(new BorderLayout());
        centro.setOpaque(false);
        centro.add(tarjetas, BorderLayout.NORTH);
        add(centro, BorderLayout.CENTER);
    }

    private JPanel crearEncabezado() {
        JPanel encabezado = new JPanel();
        encabezado.setLayout(new BoxLayout(encabezado, BoxLayout.Y_AXIS));
        encabezado.setOpaque(false);

        JLabel titulo = new JLabel("Pagos y matrículas");
        titulo.setFont(Estilos.titulo());
        titulo.setForeground(Estilos.TEXTO_PRINCIPAL);
        titulo.setAlignmentX(LEFT_ALIGNMENT);
        // BoxLayout deja la etiqueta en su ancho preferido; con el escalado de Windows se cortaba.
        titulo.setMaximumSize(new Dimension(Integer.MAX_VALUE, titulo.getPreferredSize().height));
        encabezado.add(titulo);
        encabezado.add(Box.createVerticalStrut(14));

        JPanel buscador = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buscador.setOpaque(false);
        buscador.setAlignmentX(LEFT_ALIGNMENT);
        buscador.add(Estilos.etiquetaCampo("Código de solicitud"));
        buscador.add(Box.createHorizontalStrut(12));
        buscador.add(campoCodigo);
        buscador.add(Box.createHorizontalStrut(10));
        JButton buscar = Estilos.botonSecundario("Buscar");
        buscar.addActionListener(evento -> buscar());
        campoCodigo.addActionListener(evento -> buscar());
        buscador.add(buscar);
        encabezado.add(buscador);
        encabezado.add(Box.createVerticalStrut(12));

        resumen.setOpaque(false);
        resumen.setAlignmentX(LEFT_ALIGNMENT);
        encabezado.add(resumen);
        return encabezado;
    }

    private void buscar() {
        String codigo = campoCodigo.getText().trim().toUpperCase();
        actual = solicitudes.buscar(codigo);
        if (actual == null) {
            mostrarResumen(null);
            tarjetas.bloquear("No existe la solicitud " + codigo + ".");
            return;
        }

        // Los vencimientos se aplican antes de operar (§4): una habilitación vencida devuelve
        // la solicitud al final de la cola aunque nadie haya abierto la app en ese momento.
        LocalDateTime ahora = LocalDateTime.now();
        if (actual.habilitacionVencida(ahora)) {
            LocalDateTime vencio = actual.getVencimientoHabilitacion();
            String motivo = "La habilitación venció el " + vencio.format(TarjetasPago.FORMATO_FECHA)
                    + "; la solicitud volvió al final de la cola sin pago.";
            try {
                solicitudes.vencerHabilitaciones(ahora);
            } catch (ReglaDominioException e) {
                motivo = e.getMessage();
            }
            mostrarResumen(actual);
            tarjetas.bloquear(motivo);
            return;
        }

        mostrarResumen(actual);
        Pago confirmado = inscripcionConfirmada(actual);
        if (confirmado != null) {
            tarjetas.mostrarConfirmado(confirmado, detalleConfirmado(actual));
            return;
        }
        if (actual.getEstado() != EstadoSolicitud.HABILITADA_PARA_PAGO) {
            tarjetas.bloquear(actual.getCodigo() + " está en " + actual.getEstado()
                    + " y no está habilitada para pagar la inscripción.");
            return;
        }
        try {
            Turnos.exigirTurno(actual, solicitudes, matriculas, ahora);
        } catch (ReglaDominioException e) {
            tarjetas.bloquear(e.getMessage());
            return;
        }
        // Pago rechaza operaciones anteriores a la habilitación y también al registro de la
        // solicitud: el plazo empieza en la más tardía de las dos.
        LocalDateTime inicio = actual.getFechaHabilitacion();
        if (actual.getFechaRegistro().isAfter(inicio)) {
            inicio = actual.getFechaRegistro();
        }
        // La cuota se relee en cada búsqueda: pudo cambiar en Cuotas 2027 durante la sesión.
        tarjetas.prepararPago(cuotas.getCuotaInscripcion(), inicio,
                actual.getVencimientoHabilitacion());
    }

    private void confirmar(double monto, MedioPago medio, String numeroOperacion,
            LocalDateTime fechaOperacion, String rutaComprobantePago)
            throws ReglaDominioException {
        Pago pago = Cobros.confirmarInscripcion(actual, monto, medio, numeroOperacion,
                fechaOperacion, rutaComprobantePago, LocalDateTime.now(), solicitudes,
                matriculas, pagos, cuotas);
        mostrarResumen(actual);
        tarjetas.mostrarConfirmado(pago, detalleConfirmado(actual));
    }

    private Pago inscripcionConfirmada(Solicitud solicitud) {
        for (Pago pago : pagos.deSolicitud(solicitud)) {
            if (pago.getConcepto() == ConceptoPago.INSCRIPCION && pago.estaConfirmado()) {
                return pago;
            }
        }
        return null;
    }

    private static String detalleConfirmado(Solicitud solicitud) {
        if (solicitud.getExpediente() == null) {
            return "Inscripción pagada.";
        }
        return "Solicitud en EN_DOCUMENTACION · entrega de los 4 documentos hasta el "
                + solicitud.getExpediente().fechaLimiteEntrega().format(FORMATO_DIA) + ".";
    }

    private void mostrarResumen(Solicitud solicitud) {
        resumen.removeAll();
        if (solicitud != null) {
            JLabel datos = new JLabel(solicitud.getAlumno().getNombreCompleto() + "  ·  "
                    + solicitud.getCodigo() + "  ·  Aula " + solicitud.getAula().getNombre()
                    + "     ");
            datos.setFont(Estilos.fuente(Font.PLAIN, 15));
            datos.setForeground(Estilos.TEXTO_PRINCIPAL);
            resumen.add(datos);
            resumen.add(Estilos.chip(solicitud.getEstado()));
        }
        resumen.revalidate();
        resumen.repaint();
    }
}
