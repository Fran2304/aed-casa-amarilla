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
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

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
                "Fecha real dentro de la habilitación (48 h)", this::confirmar, this::anular);

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
        campoCodigo.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                descartarSiCambioCodigo();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                descartarSiCambioCodigo();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                descartarSiCambioCodigo();
            }
        });
        buscador.add(buscar);
        encabezado.add(buscador);
        encabezado.add(Box.createVerticalStrut(12));

        resumen.setOpaque(false);
        resumen.setAlignmentX(LEFT_ALIGNMENT);
        encabezado.add(resumen);
        return encabezado;
    }

    private void descartarSiCambioCodigo() {
        if (actual == null
                || campoCodigo.getText().trim().equalsIgnoreCase(actual.getCodigo())) {
            return;
        }
        actual = null;
        mostrarResumen(null);
        tarjetas.reiniciar();
    }

    private void buscar() {
        String codigo = campoCodigo.getText().trim().toUpperCase();
        actual = solicitudes.buscar(codigo);
        if (actual == null) {
            mostrarResumen(null);
            tarjetas.bloquear("No existe la solicitud " + codigo + ".");
            return;
        }

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
        LocalDateTime inicio = actual.getFechaHabilitacion();
        if (actual.getFechaRegistro().isAfter(inicio)) {
            inicio = actual.getFechaRegistro();
        }
        tarjetas.prepararPago(cuotas.getCuotaInscripcion(), inicio,
                actual.getVencimientoHabilitacion());
    }

    private void confirmar(double monto, MedioPago medio, String numeroOperacion,
            LocalDateTime fechaOperacion, String rutaComprobantePago)
            throws ReglaDominioException {
        Pago pago;
        try {
            pago = Cobros.confirmarInscripcion(actual, monto, medio, numeroOperacion,
                    fechaOperacion, rutaComprobantePago, LocalDateTime.now(), solicitudes,
                    matriculas, pagos, cuotas);
        } catch (ReglaDominioException e) {
            if (!puedePagar()) {
                buscar();
            }
            throw e;
        }
        mostrarResumen(actual);
        tarjetas.mostrarConfirmado(pago, detalleConfirmado(actual));
    }

    // Anula la inscripción confirmada por error de registro (§4.7) y refresca el panel:
    // la solicitud revierte a HABILITADA_PARA_PAGO (o al final de la cola si el plazo
    // original venció) y el pago queda ANULADO conservando su historial.
    private void anular(String motivo, String responsable) throws ReglaDominioException {
        if (actual == null) {
            throw new ReglaDominioException("Busque una solicitud antes de anular el pago.");
        }
        Pago confirmado = inscripcionConfirmada(actual);
        if (confirmado == null) {
            throw new ReglaDominioException("La solicitud " + actual.getCodigo()
                    + " no tiene una inscripción confirmada que anular.");
        }
        Cobros.anularInscripcion(confirmado, motivo, responsable, LocalDateTime.now(),
                solicitudes);
        buscar();
    }

    private boolean puedePagar() {
        if (actual.getEstado() != EstadoSolicitud.HABILITADA_PARA_PAGO) {
            return false;
        }
        try {
            Turnos.exigirTurno(actual, solicitudes, matriculas, LocalDateTime.now());
            return true;
        } catch (ReglaDominioException e) {
            return false;
        }
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
