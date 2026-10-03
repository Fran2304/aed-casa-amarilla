package ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import datos.ArregloPagos;
import datos.ArregloSolicitudes;
import modelo.ConceptoPago;
import modelo.ConfiguracionCuotas;
import modelo.DatoInvalidoException;
import modelo.EstadoSolicitud;
import modelo.MedioPago;
import modelo.Pago;
import modelo.ReglaDominioException;
import modelo.Solicitud;

// Alcance M2 (issue #15): cubre el pago de inscripción con transición real
// a EN_DOCUMENTACION. El flujo de matrícula (confirmar → ACTIVA) necesita
// #23/#24 y se concreta en la pantalla #26.
public class PanelPagos extends JPanel {

    private static final DateTimeFormatter FORMATO_OPERACION =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FORMATO_LIMITE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ArregloPagos pagos;
    private final ArregloSolicitudes solicitudes;
    private final ConfiguracionCuotas cuotas;

    // Último pago registrado en el panel: recibe confirmar/observación.
    private Pago pagoActual;

    private final JComboBox<ConceptoPago> comboConcepto =
            new JComboBox<>(ConceptoPago.values());
    private final JTextField campoSolicitud = new JTextField(10);
    private final JTextField campoMonto = new JTextField(10);
    private final JComboBox<MedioPago> comboMedio = new JComboBox<>(MedioPago.values());
    private final JTextField campoOperacion = new JTextField(12);
    private final JLabel pistaOperacion = new JLabel(" ");
    private final JTextField campoFecha = new JTextField("28/09/2026 12:42", 14);
    private final JTextField campoComprobante = new JTextField(20);
    private final JTextField campoMotivo = new JTextField(20);
    private final JLabel chipEstado = Estilos.chip("SIN PAGO");
    private final JLabel mensaje = new JLabel(" ");
    private final JLabel limite = new JLabel(" ");

    public PanelPagos(ArregloPagos pagos, ArregloSolicitudes solicitudes,
            ConfiguracionCuotas cuotas) {
        super(new BorderLayout(0, 16));
        this.pagos = pagos;
        this.solicitudes = solicitudes;
        this.cuotas = cuotas;
        setBorder(BorderFactory.createEmptyBorder(32, 32, 32, 34));

        add(crearEncabezado(), BorderLayout.NORTH);

        JPanel centro = new JPanel(new BorderLayout(0, 16));
        centro.setOpaque(false);
        centro.add(crearTarjeta(), BorderLayout.CENTER);
        centro.add(crearAcciones(), BorderLayout.SOUTH);
        add(centro, BorderLayout.CENTER);

        comboMedio.addActionListener(e -> actualizarPistaOperacion());
        actualizarPistaOperacion();
        mostrarMontoReferencia();
    }

    private JPanel crearEncabezado() {
        JPanel encabezado = new JPanel(new BorderLayout());
        encabezado.setOpaque(false);
        JLabel titulo = new JLabel("Pagos y matrículas");
        titulo.setFont(Estilos.panel());
        titulo.setForeground(Estilos.TEXTO_PRINCIPAL);
        encabezado.add(titulo, BorderLayout.WEST);
        JLabel alerta = new JLabel("EN REVISIÓN · Recibir no equivale a confirmar.");
        alerta.setFont(Estilos.contenido());
        alerta.setForeground(Estilos.TEXTO_ALERTA);
        encabezado.add(alerta, BorderLayout.EAST);
        return encabezado;
    }

    private JPanel crearTarjeta() {
        JPanel tarjeta = Estilos.tarjeta(new JPanel(new GridLayout(0, 2, 12, 8)));
        tarjeta.setBorder(BorderFactory.createCompoundBorder(
                tarjeta.getBorder(), BorderFactory.createEmptyBorder(16, 16, 16, 16)));
        tarjeta.add(etiqueta("Concepto:"));
        tarjeta.add(comboConcepto);
        tarjeta.add(etiqueta("Solicitud (código):"));
        tarjeta.add(campoSolicitud);
        tarjeta.add(etiqueta("Monto aplicado (S/):"));
        tarjeta.add(campoMonto);
        tarjeta.add(etiqueta("Medio de pago:"));
        tarjeta.add(comboMedio);
        tarjeta.add(etiqueta("N° de operación:"));
        tarjeta.add(campoOperacion);
        tarjeta.add(etiqueta(""));
        pistaOperacion.setFont(Estilos.contenido());
        tarjeta.add(pistaOperacion);
        tarjeta.add(etiqueta("Fecha real (dd/mm/aaaa hh:mm):"));
        tarjeta.add(campoFecha);
        tarjeta.add(etiqueta("Comprobante (ruta):"));
        tarjeta.add(filaComprobante());
        tarjeta.add(etiqueta("Motivo (solo observación):"));
        tarjeta.add(campoMotivo);
        tarjeta.add(etiqueta("Estado del pago:"));
        tarjeta.add(chipEstado);
        mensaje.setFont(Estilos.contenido());
        tarjeta.add(mensaje);
        limite.setFont(Estilos.contenido());
        tarjeta.add(limite);
        return tarjeta;
    }

    private JPanel filaComprobante() {
        JPanel fila = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        fila.setOpaque(false);
        fila.add(campoComprobante);
        JButton examinar = Estilos.botonSecundario("Examinar");
        examinar.addActionListener(e -> elegirComprobante());
        fila.add(examinar);
        return fila;
    }

    private JPanel crearAcciones() {
        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.LEFT));
        acciones.setOpaque(false);
        JButton registrar = Estilos.botonPrimario("Registrar");
        registrar.addActionListener(e -> registrar());
        acciones.add(registrar);
        JButton confirmar = Estilos.botonPrimario("Confirmar pago");
        confirmar.addActionListener(e -> confirmar());
        acciones.add(confirmar);
        JButton observar = Estilos.botonSecundario("Registrar observación");
        observar.addActionListener(e -> observar());
        acciones.add(observar);
        JButton nuevo = Estilos.botonSecundario("Nuevo");
        nuevo.addActionListener(e -> nuevo());
        acciones.add(nuevo);
        return acciones;
    }

    private JLabel etiqueta(String texto) {
        JLabel etiqueta = new JLabel(texto);
        etiqueta.setFont(Estilos.contenido());
        etiqueta.setForeground(Estilos.TEXTO_PRINCIPAL);
        return etiqueta;
    }

    private void elegirComprobante() {
        JFileChooser selector = new JFileChooser();
        if (selector.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            campoComprobante.setText(selector.getSelectedFile().getAbsolutePath());
        }
    }

    // El combo de medio exige n° de operación salvo efectivo: se avisa en vivo (§4.8).
    private void actualizarPistaOperacion() {
        boolean efectivo = comboMedio.getSelectedItem() == MedioPago.EFECTIVO;
        pistaOperacion.setText(efectivo
                ? "Efectivo: n° de operación opcional."
                : "Obligatorio para " + comboMedio.getSelectedItem() + ".");
        pistaOperacion.setForeground(
                efectivo ? Estilos.SECUNDARIO : Estilos.TEXTO_ALERTA);
    }

    private void mostrarMontoReferencia() {
        campoMonto.setToolTipText(String.format(Locale.ROOT,
                "Cuota vigente: inscripción S/ %.2f · matrícula S/ %.2f.",
                cuotas.getCuotaInscripcion(), cuotas.getCuotaMatricula()));
    }

    private void registrar() {
        // La matrícula aún no tiene registro central (#23): el pago de matrícula
        // se relaciona con su matrícula en el modelo, pero en M2 no hay cuál indicar.
        if (!esInscripcion()) {
            mostrarMensaje("El pago de matrícula llega con la pantalla #26."
                    + " Por ahora registra pagos de inscripción.", true);
            return;
        }
        // La cuota pudo cambiar en Cuotas 2027 durante la sesión: releerla.
        mostrarMontoReferencia();
        limite.setText(" ");
        try {
            pagoActual = pagos.registrar(
                    (ConceptoPago) comboConcepto.getSelectedItem(),
                    leerMonto(),
                    (MedioPago) comboMedio.getSelectedItem(),
                    leerFecha(),
                    campoOperacion.getText(),
                    campoComprobante.getText(),
                    campoSolicitud.getText(),
                    null);
        } catch (DatoInvalidoException e) {
            pagoActual = null;
            mostrarMensaje(e.getMessage(), true);
            return;
        }
        actualizarChip();
        mostrarMensaje("Comprobante recibido. Recibir no equivale a confirmar.", false);
    }

    private void confirmar() {
        if (pagoActual == null) {
            mostrarMensaje("Registra primero el comprobante.", true);
            return;
        }
        if (pagoActual.estaConfirmado()) {
            mostrarMensaje("El pago ya está confirmado (solo-lectura).", true);
            return;
        }
        Solicitud solicitud = solicitudes.buscar(pagoActual.getCodigoSolicitud());
        if (solicitud == null) {
            mostrarMensaje("No existe la solicitud " + pagoActual.getCodigoSolicitud() + ".", true);
            return;
        }
        if (solicitud.getEstado() != EstadoSolicitud.EN_ESPERA_SIN_PAGO) {
            mostrarMensaje("La solicitud ya no está EN ESPERA SIN PAGO.", true);
            return;
        }
        // En M2 todo pago registrado aquí es de inscripción (registrar bloquea matrícula).
        try {
            pagoActual.confirmar(cuotas.getCuotaInscripcion());
            solicitud.cambiarEstado(EstadoSolicitud.EN_DOCUMENTACION);
        } catch (ReglaDominioException e) {
            mostrarMensaje(e.getMessage() + " Registra una observación.", true);
            return;
        }
        actualizarChip();
        setSoloLectura(true);
        // El plazo de documentos lo define el flujo: confirmación + 7 días (§4.3).
        String limiteDocs = LocalDate.now().plusDays(7).format(FORMATO_LIMITE);
        limite.setText("Nueva fecha límite de documentos: " + limiteDocs + ".");
        mostrarMensaje("Inscripción confirmada. La solicitud pasa a EN DOCUMENTACIÓN.",
                false);
    }

    private void observar() {
        if (pagoActual == null) {
            mostrarMensaje("Registra primero el comprobante.", true);
            return;
        }
        if (pagoActual.estaConfirmado()) {
            mostrarMensaje("El pago ya está confirmado (solo-lectura).", true);
            return;
        }
        try {
            pagoActual.observar(campoMotivo.getText());
        } catch (ReglaDominioException e) {
            mostrarMensaje(e.getMessage(), true);
            return;
        }
        actualizarChip();
        mostrarMensaje("Observación registrada. Solicita corrección al apoderado.", false);
    }

    private boolean esInscripcion() {
        return comboConcepto.getSelectedItem() == ConceptoPago.INSCRIPCION;
    }

    private double leerMonto() throws DatoInvalidoException {
        String texto = campoMonto.getText().trim();
        // Igual que las cuotas (#11): sin separador de miles y hasta 2 decimales.
        if (!texto.matches("[0-9]+([.,][0-9]{1,2})?")) {
            throw new DatoInvalidoException("El monto debe ser como 1200 o 180.50,"
                    + " sin separador de miles.");
        }
        double monto = Double.parseDouble(texto.replace(',', '.'));
        if (!Double.isFinite(monto) || monto <= 0) {
            throw new DatoInvalidoException("El monto aplicado debe ser mayor que 0.");
        }
        return monto;
    }

    private LocalDateTime leerFecha() throws DatoInvalidoException {
        try {
            return LocalDateTime.parse(campoFecha.getText().trim(), FORMATO_OPERACION);
        } catch (DateTimeParseException e) {
            throw new DatoInvalidoException("La fecha real usa dd/mm/aaaa hh:mm.");
        }
    }

    // Paleta semántica del tema: espera=amarillo, alerta=rojo claro,
    // confirmado=verde favorable.
    private void actualizarChip() {
        switch (pagoActual.getEstado()) {
            case CONFIRMADO:
                pintarChip("CONFIRMADO", Estilos.FAVORABLE, Estilos.TEXTO_PRINCIPAL);
                break;
            case OBSERVADO:
                pintarChip("OBSERVADO", Estilos.ALERTA, Estilos.TEXTO_ALERTA);
                break;
            default:
                pintarChip("RECIBIDO", Estilos.ESPERA, Estilos.TEXTO_ESPERA);
                break;
        }
    }

    private void pintarChip(String texto, java.awt.Color fondo, java.awt.Color letra) {
        chipEstado.setText(texto);
        chipEstado.setBackground(fondo);
        chipEstado.setForeground(letra);
    }

    // Un pago confirmado no se edita (§4.8): los campos quedan en solo-lectura.
    private void setSoloLectura(boolean soloLectura) {
        boolean editable = !soloLectura;
        comboConcepto.setEnabled(editable);
        campoSolicitud.setEditable(editable);
        campoMonto.setEditable(editable);
        comboMedio.setEnabled(editable);
        campoOperacion.setEditable(editable);
        campoFecha.setEditable(editable);
        campoComprobante.setEditable(editable);
        campoMotivo.setEditable(editable);
    }

    // Tras confirmar, el panel quedaba muerto: este botón lo deja listo
    // para el siguiente comprobante.
    private void nuevo() {
        pagoActual = null;
        setSoloLectura(false);
        chipEstado.setText("SIN PAGO");
        chipEstado.setBackground(Estilos.ESPERA);
        chipEstado.setForeground(Estilos.TEXTO_ESPERA);
        campoMotivo.setText("");
        limite.setText(" ");
        mostrarMensaje(" ", false);
    }

    private void mostrarMensaje(String texto, boolean esError) {
        mensaje.setText(texto);
        mensaje.setForeground(esError ? Estilos.TEXTO_ALERTA : Estilos.BOTON_PRINCIPAL);
    }
}
