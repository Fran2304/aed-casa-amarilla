package ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;

import modelo.MedioPago;
import modelo.Pago;
import modelo.ReglaDominioException;

public class TarjetasPago extends JPanel {

    public interface AccionConfirmar {
        void confirmar(double monto, MedioPago medio, String numeroOperacion,
                LocalDateTime fechaOperacion, String rutaComprobantePago)
                throws ReglaDominioException;
    }

    public static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final AccionConfirmar accion;

    private double cuota;
    private LocalDateTime inicioPlazo;
    private LocalDateTime finPlazo;
    private Pago pagoConfirmado;
    private boolean editable;

    private final JPanel aviso = new JPanel();
    private final JLabel tituloAviso = new JLabel(" ");
    private final JTextArea detalleAviso = textoAjustable();

    private final JTextField campoConcepto = Estilos.campo(new JTextField());
    private final JTextField campoMonto = Estilos.campo(new JTextField());
    private final JComboBox<MedioPago> comboMedio =
            Estilos.campo(new JComboBox<MedioPago>(MedioPago.values()));
    private final JTextField campoOperacion = Estilos.campo(new JTextField());
    private final JTextField campoFecha = Estilos.campo(new JTextField());
    private final JLabel enlaceComprobante = Estilos.campo(new JLabel());
    private final JButton botonAdjuntar = Estilos.botonSecundario("Adjuntar…");
    private String rutaComprobante;

    private final JLabel[] items = new JLabel[4];
    private final JLabel[] motivos = new JLabel[4];
    private final JCheckBox revisado = new JCheckBox("Comprobante revisado por el personal");
    private final JButton botonConfirmar;
    private final JTextArea mensaje = textoAjustable();

    private final JPanel boleta = new JPanel(new GridBagLayout());
    private final JTextField campoBoleta = Estilos.campo(new JTextField());
    private final JButton botonBoleta = Estilos.botonSecundario("Registrar");
    private final JLabel mensajeBoleta = new JLabel(" ");

    public TarjetasPago(String tituloPago, String concepto, String textoConfirmar,
            String textoPlazo, AccionConfirmar accion) {
        super(new GridBagLayout());
        this.accion = accion;
        setOpaque(false);
        campoConcepto.setText(concepto);
        botonConfirmar = Estilos.botonPrimario(textoConfirmar);

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.weighty = 1;
        c.weightx = 0.65;
        c.insets = new Insets(0, 0, 0, 16);
        add(crearTarjetaPago(tituloPago), c);
        c.weightx = 0.35;
        c.insets = new Insets(0, 0, 0, 0);
        add(crearTarjetaValidacion(textoPlazo), c);

        escucharCambios();
        reiniciar();
    }

    public void reiniciar() {
        esperar(Estilos.ESPERA, Estilos.TEXTO_ESPERA, "BUSQUE UNA SOLICITUD",
                "Ingrese su código para registrar el pago.");
    }

    public void prepararPago(double cuota, LocalDateTime inicioPlazo, LocalDateTime finPlazo) {
        this.cuota = cuota;
        this.inicioPlazo = inicioPlazo;
        this.finPlazo = finPlazo;
        this.pagoConfirmado = null;
        campoMonto.setText(soles(cuota));
        comboMedio.setSelectedItem(MedioPago.EFECTIVO);
        campoOperacion.setText("");
        campoFecha.setText(LocalDateTime.now().format(FORMATO_FECHA));
        mostrarComprobante(null);
        revisado.setSelected(false);
        pintarAviso(Estilos.ALERTA, Estilos.TEXTO_ALERTA,
                "EN REVISIÓN · Recibir no equivale a confirmar",
                "Plazo de pago: " + inicioPlazo.format(FORMATO_FECHA) + "  →  "
                        + finPlazo.format(FORMATO_FECHA) + ".");
        mostrarMensaje(" ", false);
        boleta.setVisible(false);
        setEditable(true);
    }

    public void mostrarConfirmado(Pago pago, String detalle) {
        this.pagoConfirmado = pago;
        campoMonto.setText(soles(pago.getMontoPagado()));
        comboMedio.setSelectedItem(pago.getMedio());
        campoOperacion.setText(pago.getNumeroOperacion());
        campoFecha.setText(pago.getFechaHoraOperacion().format(FORMATO_FECHA));
        mostrarComprobante(pago.getRutaComprobantePago());
        revisado.setSelected(true);
        pintarAviso(Estilos.FAVORABLE, Estilos.BOTON_PRINCIPAL, "PAGO CONFIRMADO", detalle);
        mostrarMensaje(" ", false);
        mostrarBoleta();
        setEditable(false);
        for (int i = 0; i < items.length; i++) {
            marcar(i, null);
        }
    }

    public void bloquear(String motivo) {
        esperar(Estilos.ALERTA, Estilos.TEXTO_ALERTA, "NO SE PUEDE REGISTRAR EL PAGO", motivo);
    }

    private void esperar(Color fondo, Color texto, String titulo, String detalle) {
        setEditable(false);
        pagoConfirmado = null;
        comboMedio.setSelectedIndex(-1);
        campoMonto.setText("");
        campoOperacion.setText("");
        campoFecha.setText("");
        mostrarComprobante(null);
        revisado.setSelected(false);
        pintarAviso(fondo, texto, titulo, detalle);
        mostrarMensaje(" ", false);
        boleta.setVisible(false);
        for (int i = 0; i < items.length; i++) {
            items[i].setText("○  " + items[i].getName());
            items[i].setForeground(Estilos.SECUNDARIO);
            motivos[i].setText(" ");
        }
    }

    private JPanel crearTarjetaPago(String tituloPago) {
        JPanel tarjeta = tarjeta();
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.weightx = 0.5;
        c.gridwidth = 2;
        c.gridy = 0;
        c.insets = new Insets(0, 0, 16, 0);
        tarjeta.add(titulo(tituloPago), c);

        c.gridy = 1;
        c.insets = new Insets(0, 0, 20, 0);
        tarjeta.add(crearAviso(), c);

        c.insets = new Insets(0, 0, 18, 0);
        c.gridy = 2;
        tarjeta.add(fila("Concepto", campoConcepto, "Monto aplicado (S/)", campoMonto), c);
        c.gridy = 3;
        tarjeta.add(fila("Medio de pago", comboMedio, "N° operación", campoOperacion), c);
        c.gridy = 4;
        tarjeta.add(fila("Fecha real (dd/mm/aaaa hh:mm)", campoFecha, "Comprobante",
                crearComprobante()), c);
        Estilos.editable(campoConcepto, false);

        JLabel nota = new JLabel("Número de operación obligatorio salvo efectivo."
                + " Cada pago conserva su monto aplicado.");
        nota.setFont(Estilos.fuente(Font.PLAIN, 12));
        nota.setForeground(Estilos.SECUNDARIO);
        c.gridy = 5;
        c.weighty = 1;
        c.insets = new Insets(4, 0, 0, 0);
        tarjeta.add(nota, c);
        return tarjeta;
    }

    private static JPanel fila(String etiquetaIzq, JComponent campoIzq, String etiquetaDer,
            JComponent campoDer) {
        JPanel fila = new JPanel(new GridLayout(1, 2, 20, 0));
        fila.setOpaque(false);
        fila.add(columna(etiquetaIzq, campoIzq));
        fila.add(columna(etiquetaDer, campoDer));
        return fila;
    }

    private static JPanel columna(String etiqueta, JComponent campo) {
        JPanel columna = new JPanel(new BorderLayout(0, 6));
        columna.setOpaque(false);
        columna.add(Estilos.etiquetaCampo(etiqueta), BorderLayout.NORTH);
        columna.add(campo, BorderLayout.CENTER);
        return columna;
    }

    private JPanel crearComprobante() {
        JPanel comprobante = new JPanel(new BorderLayout(8, 0));
        comprobante.setOpaque(false);
        enlaceComprobante.setOpaque(true);
        enlaceComprobante.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                abrirComprobante();
            }
        });
        botonAdjuntar.addActionListener(evento -> adjuntarComprobante());
        comprobante.add(enlaceComprobante, BorderLayout.CENTER);
        comprobante.add(botonAdjuntar, BorderLayout.EAST);
        return comprobante;
    }

    private void adjuntarComprobante() {
        JFileChooser selector = new JFileChooser();
        selector.setDialogTitle("Comprobante del pago");
        selector.setFileFilter(new FileNameExtensionFilter("Imágenes y PDF", "jpg", "jpeg",
                "png", "pdf"));
        if (selector.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            mostrarComprobante(selector.getSelectedFile().getAbsolutePath());
            validar();
        }
    }

    private void mostrarComprobante(String ruta) {
        rutaComprobante = ruta;
        if (ruta == null) {
            enlaceComprobante.setText("Sin archivo adjunto");
            enlaceComprobante.setForeground(Estilos.SECUNDARIO);
            enlaceComprobante.setCursor(Cursor.getDefaultCursor());
            enlaceComprobante.setToolTipText(null);
            return;
        }
        enlaceComprobante.setText("Abrir comprobante →  " + new File(ruta).getName());
        enlaceComprobante.setForeground(Estilos.BOTON_PRINCIPAL);
        enlaceComprobante.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        enlaceComprobante.setToolTipText(ruta);
    }

    private void abrirComprobante() {
        if (rutaComprobante == null) {
            return;
        }
        File archivo = new File(rutaComprobante);
        if (!archivo.isFile()) {
            mostrarMensaje("No se encuentra el archivo " + rutaComprobante + ".", true);
            return;
        }
        try {
            Desktop.getDesktop().open(archivo);
        } catch (IOException | UnsupportedOperationException e) {
            mostrarMensaje("No se pudo abrir el comprobante: " + e.getMessage(), true);
        }
    }

    private JPanel crearAviso() {
        Estilos.aviso(aviso, Estilos.ALERTA);
        aviso.setLayout(new BorderLayout(0, 2));
        tituloAviso.setFont(Estilos.fuente(Font.BOLD, 14));
        detalleAviso.setFont(Estilos.fuente(Font.PLAIN, 12));
        detalleAviso.setForeground(Estilos.SECUNDARIO);
        aviso.add(tituloAviso, BorderLayout.NORTH);
        aviso.add(detalleAviso, BorderLayout.CENTER);
        return aviso;
    }

    private JPanel crearTarjetaValidacion(String textoPlazo) {
        JPanel tarjeta = tarjeta();
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.NORTHWEST;
        c.weightx = 1;
        c.gridx = 0;
        c.gridy = 0;
        c.insets = new Insets(0, 0, 16, 0);
        tarjeta.add(titulo("Validación y cierre"), c);

        String[] textos = { "Concepto y monto aplicado", "Medio y número de operación",
                textoPlazo, "Comprobante del pago adjunto" };
        for (int i = 0; i < textos.length; i++) {
            items[i] = new JLabel();
            items[i].setName(textos[i]);
            items[i].setFont(Estilos.contenido());
            motivos[i] = new JLabel(" ");
            motivos[i].setFont(Estilos.fuente(Font.PLAIN, 12));
            motivos[i].setForeground(Estilos.TEXTO_ALERTA);
            c.gridy++;
            c.insets = new Insets(0, 0, 0, 0);
            tarjeta.add(items[i], c);
            c.gridy++;
            c.insets = new Insets(0, 22, 8, 0);
            tarjeta.add(motivos[i], c);
        }

        revisado.setFont(Estilos.contenido());
        revisado.setForeground(Estilos.TEXTO_PRINCIPAL);
        revisado.setOpaque(false);
        c.gridy++;
        c.insets = new Insets(0, 0, 18, 0);
        tarjeta.add(revisado, c);

        botonConfirmar.addActionListener(evento -> confirmar());
        c.gridy++;
        c.insets = new Insets(0, 0, 8, 0);
        tarjeta.add(botonConfirmar, c);

        mensaje.setFont(Estilos.fuente(Font.PLAIN, 13));
        c.gridy++;
        tarjeta.add(mensaje, c);

        c.gridy++;
        c.insets = new Insets(10, 0, 0, 0);
        tarjeta.add(crearBoleta(), c);

        c.gridy++;
        c.weighty = 1;
        tarjeta.add(new JLabel(" "), c);
        return tarjeta;
    }

    private JPanel crearBoleta() {
        boleta.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 0;
        c.gridwidth = 2;
        c.insets = new Insets(0, 0, 6, 0);
        boleta.add(Estilos.etiquetaCampo("N° de boleta del nido"), c);
        c.gridy = 1;
        c.gridwidth = 1;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 0, 8);
        boleta.add(campoBoleta, c);
        c.gridx = 1;
        c.weightx = 0;
        c.insets = new Insets(0, 0, 0, 0);
        boleta.add(botonBoleta, c);
        mensajeBoleta.setFont(Estilos.fuente(Font.PLAIN, 13));
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 2;
        c.insets = new Insets(6, 0, 0, 0);
        boleta.add(mensajeBoleta, c);
        botonBoleta.addActionListener(evento -> registrarBoleta());
        boleta.setVisible(false);
        return boleta;
    }

    private void escucharCambios() {
        DocumentListener alEscribir = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                validar();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                validar();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                validar();
            }
        };
        campoMonto.getDocument().addDocumentListener(alEscribir);
        campoOperacion.getDocument().addDocumentListener(alEscribir);
        campoFecha.getDocument().addDocumentListener(alEscribir);
        comboMedio.addActionListener(evento -> validar());
        revisado.addActionListener(evento -> validar());
    }

    private void validar() {
        if (!editable) {
            return;
        }
        String motivoMonto = motivoMonto();
        String motivoOperacion = motivoOperacion();
        String motivoFecha = motivoFecha();
        String motivoComprobante = motivoComprobante();
        marcar(0, motivoMonto);
        marcar(1, motivoOperacion);
        marcar(2, motivoFecha);
        marcar(3, motivoComprobante);
        botonConfirmar.setEnabled(motivoMonto == null && motivoOperacion == null
                && motivoFecha == null && motivoComprobante == null && revisado.isSelected());
    }

    private String motivoComprobante() {
        if (rutaComprobante == null) {
            return "Adjunte la captura o el voucher del pago.";
        }
        if (!new File(rutaComprobante).isFile()) {
            return "El archivo ya no existe en esa ruta.";
        }
        return null;
    }

    private String motivoMonto() {
        String texto = campoMonto.getText().trim();
        if (!texto.matches("[0-9]+([.,][0-9]{1,2})?")) {
            return "Escriba un monto como 180 o 180.50.";
        }
        double monto = Double.parseDouble(texto.replace(',', '.'));
        if (Math.round(monto * 100) != Math.round(cuota * 100)) {
            return "No coincide con la cuota vigente S/ " + soles(cuota) + ".";
        }
        return null;
    }

    private String motivoOperacion() {
        MedioPago medio = (MedioPago) comboMedio.getSelectedItem();
        if (medio != MedioPago.EFECTIVO && campoOperacion.getText().trim().isEmpty()) {
            return "Falta el n° de operación para " + medio + ".";
        }
        return null;
    }

    private String motivoFecha() {
        LocalDateTime fecha;
        try {
            fecha = leerFecha();
        } catch (DateTimeParseException e) {
            return "Use el formato dd/mm/aaaa hh:mm.";
        }
        if (fecha.isAfter(LocalDateTime.now())) {
            return "La operación no puede ser futura.";
        }
        if (fecha.isBefore(inicioPlazo)) {
            return "Es anterior al inicio del plazo (" + inicioPlazo.format(FORMATO_FECHA) + ").";
        }
        if (!fecha.isBefore(finPlazo)) {
            return "El plazo venció el " + finPlazo.format(FORMATO_FECHA) + ".";
        }
        return null;
    }

    private LocalDateTime leerFecha() {
        return LocalDateTime.parse(campoFecha.getText().trim(), FORMATO_FECHA);
    }

    private void marcar(int item, String motivo) {
        boolean cumple = motivo == null;
        items[item].setText((cumple ? "✓  " : "✗  ") + items[item].getName());
        items[item].setForeground(cumple ? Estilos.TEXTO_PRINCIPAL : Estilos.TEXTO_ALERTA);
        motivos[item].setText(cumple ? " " : motivo);
    }

    private void confirmar() {
        try {
            double monto = Double.parseDouble(campoMonto.getText().trim().replace(',', '.'));
            accion.confirmar(monto, (MedioPago) comboMedio.getSelectedItem(),
                    campoOperacion.getText().trim(), leerFecha(), rutaComprobante);
        } catch (ReglaDominioException e) {
            mostrarMensaje(e.getMessage(), true);
        }
    }

    private void registrarBoleta() {
        try {
            pagoConfirmado.registrarComprobante(campoBoleta.getText().trim());
        } catch (ReglaDominioException e) {
            mensajeBoleta.setText(e.getMessage());
            mensajeBoleta.setForeground(Estilos.TEXTO_ALERTA);
            return;
        }
        mostrarBoleta();
        mensajeBoleta.setText("Boleta registrada.");
        mensajeBoleta.setForeground(Estilos.BOTON_PRINCIPAL);
    }

    private void mostrarBoleta() {
        boolean pendiente = !pagoConfirmado.tieneComprobante();
        campoBoleta.setText(pagoConfirmado.getComprobante());
        Estilos.editable(campoBoleta, pendiente);
        botonBoleta.setVisible(pendiente);
        mensajeBoleta.setText(" ");
        boleta.setVisible(true);
    }

    private void setEditable(boolean editable) {
        this.editable = editable;
        Estilos.editable(campoMonto, editable);
        Estilos.editable(campoOperacion, editable);
        Estilos.editable(campoFecha, editable);
        comboMedio.setEnabled(editable);
        botonAdjuntar.setVisible(editable);
        revisado.setEnabled(editable);
        botonConfirmar.setEnabled(false);
        botonConfirmar.setVisible(editable);
        if (editable) {
            validar();
        }
    }

    private void pintarAviso(Color fondo, Color texto, String titulo, String detalle) {
        Estilos.aviso(aviso, fondo);
        tituloAviso.setForeground(texto);
        tituloAviso.setText(titulo);
        detalleAviso.setText(detalle);
    }

    private void mostrarMensaje(String texto, boolean esError) {
        mensaje.setText(texto);
        mensaje.setForeground(esError ? Estilos.TEXTO_ALERTA : Estilos.BOTON_PRINCIPAL);
    }

    private static JPanel tarjeta() {
        JPanel tarjeta = Estilos.tarjeta(new JPanel(new GridBagLayout()) {
            @Override
            public Dimension getPreferredSize() {
                return new Dimension(1, super.getPreferredSize().height);
            }
        });
        tarjeta.setBorder(BorderFactory.createCompoundBorder(tarjeta.getBorder(),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        return tarjeta;
    }

    private static JTextArea textoAjustable() {
        JTextArea texto = new JTextArea(" ");
        texto.setEditable(false);
        texto.setFocusable(false);
        texto.setOpaque(false);
        texto.setLineWrap(true);
        texto.setWrapStyleWord(true);
        texto.setBorder(null);
        return texto;
    }

    private static JLabel titulo(String texto) {
        JLabel titulo = new JLabel(texto);
        titulo.setFont(Estilos.tituloTarjeta());
        titulo.setForeground(Estilos.TEXTO_PRINCIPAL);
        return titulo;
    }

    private static String soles(double monto) {
        return String.format(Locale.ROOT, "%.2f", monto);
    }
}
