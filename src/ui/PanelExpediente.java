package ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import datos.ArregloEntrevistas;
import datos.ArregloMatriculas;
import datos.ArregloSolicitudes;
import modelo.Apoderado;
import modelo.DatoInvalidoException;
import modelo.Documento;
import modelo.EstadoDocumento;
import modelo.EstadoSolicitud;
import modelo.Entrevista;
import modelo.ReglaDominioException;
import modelo.ResultadoEntrevista;
import modelo.Solicitud;
import negocio.ExpedienteDocumentos;
import negocio.Turnos;
import negocio.Vencimientos;
import util.FormatoFecha;

public class PanelExpediente extends JPanel {

    private static final DateTimeFormatter FORMATO_FECHA = TarjetasPago.FORMATO_FECHA;

    private static final String SIN_DATO = "—";

    private interface Accion {
        void ejecutar() throws ReglaDominioException;
    }

    private final ArregloSolicitudes solicitudes;
    private final ArregloEntrevistas entrevistas;
    private final ArregloMatriculas matriculas;

    private Solicitud actual;
    private boolean llenandoLista;

    private final JLabel titulo = new JLabel("Expediente");
    private final JComboBox<Solicitud> comboSolicitud = Estilos.campo(new JComboBox<Solicitud>());
    private final JLabel identificacion = new JLabel(" ");
    private final JPanel franja = new JPanel(new BorderLayout(20, 0));
    private final JLabel estadoFranja = new JLabel(" ");
    private final JLabel fechasFranja = new JLabel(" ");
    private final JLabel detalleFranja = new JLabel(" ");
    private final JLabel correccionFranja = new JLabel(" ");
    private final JLabel mensaje = new JLabel(" ");
    private final JPanel contenido = new JPanel(new GridBagLayout());
    private final JScrollPane desplazable;

    private final JLabel tituloDocumentos = new JLabel("Documentos");
    private final ModeloDocumentos modeloDocumentos = new ModeloDocumentos();
    private final JTable tablaDocumentos = new JTable(modeloDocumentos);
    private final JPanel observaciones = new JPanel();
    private final JTextField campoFechaDocumento = Estilos.campo(new JTextField(14));
    private final JButton botonEntrega = Estilos.botonPrimario("Registrar entrega");
    private final JButton botonValidar = Estilos.botonSecundario("Validar");
    private final JButton botonObservar = Estilos.botonSecundario("Observar…");

    private final JPanel cajaEstadoEntrevista = new JPanel();
    private final JLabel estadoEntrevista = new JLabel(" ");
    private final JLabel motivoEntrevista = new JLabel(" ");
    private final JPanel datosCita = new JPanel();
    private final JLabel datoFecha = dato();
    private final JLabel datoPersonal = dato();
    private final JLabel datoResponsable = dato();
    private final JPanel formularioAgenda = new JPanel();
    private final JTextField campoFechaEntrevista = Estilos.campo(new JTextField());
    private final JTextField campoPersonal = Estilos.campo(new JTextField());
    private final JComboBox<String> comboResponsable = Estilos.campo(new JComboBox<String>());
    private final JButton botonAgendar = Estilos.botonPrimario("Agendar entrevista");
    private final JLabel datoAsistencia = dato();
    private final JLabel datoResultado = dato();
    private final JLabel indicadorInasistencias = dato();
    private final JPanel accionesAsistencia = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    private final JButton botonAsistio = Estilos.botonPrimario("Asistió");
    private final JButton botonNoAsistio = Estilos.botonSecundario("No asistió…");
    private final JPanel accionesResultado = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    private final JComboBox<ResultadoEntrevista> comboResultado =
            Estilos.campo(new JComboBox<ResultadoEntrevista>(ResultadoEntrevista.values()));
    private final JButton botonResultado = Estilos.botonPrimario("Registrar resultado");

    public PanelExpediente(ArregloSolicitudes solicitudes, ArregloEntrevistas entrevistas,
            ArregloMatriculas matriculas) {
        super(new BorderLayout(0, 20));
        this.solicitudes = solicitudes;
        this.entrevistas = entrevistas;
        this.matriculas = matriculas;
        setBorder(Estilos.MARGEN_PANTALLA);

        add(crearEncabezado(), BorderLayout.NORTH);
        contenido.setOpaque(false);
        GridBagConstraints restricciones = new GridBagConstraints();
        restricciones.gridy = 0;
        restricciones.anchor = GridBagConstraints.NORTH;
        restricciones.fill = GridBagConstraints.BOTH;
        restricciones.weightx = 0.62;
        restricciones.insets = new Insets(0, 0, 0, 20);
        contenido.add(crearTarjetaDocumentos(), restricciones);
        restricciones.weightx = 0.38;
        restricciones.insets = new Insets(0, 0, 0, 0);
        contenido.add(crearTarjetaEntrevista(), restricciones);
        restricciones.gridy = 1;
        restricciones.gridwidth = 2;
        restricciones.insets = new Insets(24, 0, 0, 0);
        JLabel nota = new JLabel("Entrevista FAVORABLE habilita evaluar matrícula, pero no crea"
                + " una matrícula ni garantiza cupo.");
        nota.setFont(Estilos.contenido());
        nota.setForeground(Estilos.SECUNDARIO);
        contenido.add(nota, restricciones);

        PanelAncho envoltura = new PanelAncho();
        envoltura.add(contenido, BorderLayout.NORTH);
        desplazable = new JScrollPane(envoltura);
        desplazable.setBorder(null);
        desplazable.setOpaque(false);
        desplazable.getViewport().setOpaque(false);
        desplazable.getVerticalScrollBar().setUnitIncrement(16);
        add(desplazable, BorderLayout.CENTER);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                campoFechaDocumento.setText(LocalDateTime.now().format(FORMATO_FECHA));
                recargarLista();
            }
        });
        recargarLista();
    }

    private JPanel crearEncabezado() {
        JPanel encabezado = new JPanel();
        encabezado.setLayout(new BoxLayout(encabezado, BoxLayout.Y_AXIS));
        encabezado.setOpaque(false);

        JPanel filaTitulo = new JPanel(new BorderLayout(20, 0));
        filaTitulo.setOpaque(false);
        titulo.setFont(Estilos.titulo());
        titulo.setForeground(Estilos.TEXTO_PRINCIPAL);
        filaTitulo.add(titulo, BorderLayout.WEST);
        JPanel selector = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        selector.setOpaque(false);
        selector.add(Estilos.etiquetaCampo("Solicitud"));
        selector.add(Box.createHorizontalStrut(12));
        comboSolicitud.setPreferredSize(new Dimension(300,
                comboSolicitud.getPreferredSize().height));
        comboSolicitud.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> lista, Object valor,
                    int indice, boolean seleccionado, boolean conFoco) {
                Object texto = valor;
                if (valor instanceof Solicitud) {
                    Solicitud solicitud = (Solicitud) valor;
                    texto = solicitud.getCodigo() + " · "
                            + solicitud.getAlumno().getNombreCompleto();
                }
                return super.getListCellRendererComponent(lista, texto, indice, seleccionado,
                        conFoco);
            }
        });
        comboSolicitud.addActionListener(evento -> {
            if (!llenandoLista) {
                abrir((Solicitud) comboSolicitud.getSelectedItem());
            }
        });
        selector.add(comboSolicitud);
        filaTitulo.add(selector, BorderLayout.EAST);
        encabezado.add(alineado(filaTitulo));
        encabezado.add(Box.createVerticalStrut(4));

        identificacion.setFont(Estilos.subtitulo());
        identificacion.setForeground(Estilos.SECUNDARIO);
        encabezado.add(alineado(identificacion));
        encabezado.add(Box.createVerticalStrut(20));

        JPanel izquierda = new JPanel();
        izquierda.setLayout(new BoxLayout(izquierda, BoxLayout.Y_AXIS));
        izquierda.setOpaque(false);
        estadoFranja.setFont(Estilos.datoDestacado());
        fechasFranja.setFont(Estilos.dato());
        detalleFranja.setFont(Estilos.contenidoDestacado());
        correccionFranja.setFont(Estilos.datoDestacado());
        izquierda.add(alineado(estadoFranja));
        izquierda.add(Box.createVerticalStrut(10));
        izquierda.add(alineado(fechasFranja));
        izquierda.add(Box.createVerticalStrut(6));
        izquierda.add(alineado(detalleFranja));
        franja.add(izquierda, BorderLayout.CENTER);
        franja.add(correccionFranja, BorderLayout.EAST);
        encabezado.add(alineado(franja));
        encabezado.add(Box.createVerticalStrut(10));

        mensaje.setFont(Estilos.contenidoDestacado());
        encabezado.add(alineado(mensaje));
        return encabezado;
    }

    private JPanel crearTarjetaDocumentos() {
        JPanel tarjeta = Estilos.tarjeta(new JPanel());
        tarjeta.setLayout(new BoxLayout(tarjeta, BoxLayout.Y_AXIS));

        tituloDocumentos.setFont(Estilos.panel());
        tituloDocumentos.setForeground(Estilos.TEXTO_PRINCIPAL);
        tarjeta.add(alineado(tituloDocumentos));
        tarjeta.add(Box.createVerticalStrut(8));
        tarjeta.add(alineado(texto("Los cuatro deben entregarse dentro de "
                + ExpedienteDocumentos.DIAS_ENTREGA + " días calendario desde el pago"
                + " confirmado. La validación puede concluir después.", 560)));
        tarjeta.add(Box.createVerticalStrut(18));

        tablaDocumentos.setFont(Estilos.dato());
        tablaDocumentos.setRowHeight(52);
        tablaDocumentos.setShowVerticalLines(false);
        tablaDocumentos.setGridColor(Estilos.BORDE_TARJETA);
        tablaDocumentos.setIntercellSpacing(new Dimension(0, 1));
        tablaDocumentos.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tablaDocumentos.setDefaultRenderer(Object.class, new RenderDocumento());
        tablaDocumentos.getTableHeader().setReorderingAllowed(false);
        tablaDocumentos.getTableHeader().setDefaultRenderer(new RenderCabecera());
        tablaDocumentos.getColumnModel().getColumn(0).setPreferredWidth(260);
        tablaDocumentos.getColumnModel().getColumn(1).setPreferredWidth(170);
        tablaDocumentos.getColumnModel().getColumn(2).setPreferredWidth(130);
        tablaDocumentos.getSelectionModel().addListSelectionListener(evento -> {
            if (!evento.getValueIsAdjusting()) {
                actualizarBotonesDocumento();
            }
        });
        tarjeta.add(alineado(tablaDocumentos.getTableHeader()));
        tarjeta.add(alineado(tablaDocumentos));
        tarjeta.add(Box.createVerticalStrut(18));

        observaciones.setLayout(new BoxLayout(observaciones, BoxLayout.Y_AXIS));
        observaciones.setOpaque(false);
        tarjeta.add(alineado(observaciones));

        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        acciones.setOpaque(false);
        acciones.add(Estilos.etiquetaCampo("Fecha (dd/mm/aaaa hh:mm)"));
        acciones.add(Box.createHorizontalStrut(10));
        acciones.add(campoFechaDocumento);
        tarjeta.add(alineado(acciones));
        tarjeta.add(Box.createVerticalStrut(10));

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        botones.setOpaque(false);
        botones.add(botonEntrega);
        botones.add(Box.createHorizontalStrut(10));
        botones.add(botonValidar);
        botones.add(Box.createHorizontalStrut(10));
        botones.add(botonObservar);
        tarjeta.add(alineado(botones));

        botonEntrega.addActionListener(evento -> registrarEntrega());
        botonValidar.addActionListener(evento -> validarDocumento());
        botonObservar.addActionListener(evento -> observarDocumento());
        return tarjeta;
    }

    private JPanel crearTarjetaEntrevista() {
        JPanel tarjeta = Estilos.tarjeta(new JPanel());
        tarjeta.setLayout(new BoxLayout(tarjeta, BoxLayout.Y_AXIS));

        JLabel tituloTarjeta = new JLabel("Entrevista");
        tituloTarjeta.setFont(Estilos.panel());
        tituloTarjeta.setForeground(Estilos.TEXTO_PRINCIPAL);
        tarjeta.add(alineado(tituloTarjeta));
        tarjeta.add(Box.createVerticalStrut(16));

        cajaEstadoEntrevista.setLayout(new BoxLayout(cajaEstadoEntrevista, BoxLayout.Y_AXIS));
        estadoEntrevista.setFont(Estilos.datoDestacado());
        motivoEntrevista.setFont(Estilos.detalle());
        cajaEstadoEntrevista.add(alineado(estadoEntrevista));
        cajaEstadoEntrevista.add(Box.createVerticalStrut(6));
        cajaEstadoEntrevista.add(alineado(motivoEntrevista));
        tarjeta.add(alineado(cajaEstadoEntrevista));
        tarjeta.add(Box.createVerticalStrut(24));

        tarjeta.add(alineado(subtitulo("Personal y cita")));
        tarjeta.add(Box.createVerticalStrut(10));
        datosCita.setLayout(new BoxLayout(datosCita, BoxLayout.Y_AXIS));
        datosCita.setOpaque(false);
        datosCita.add(alineado(datoFecha));
        datosCita.add(Box.createVerticalStrut(10));
        datosCita.add(alineado(datoPersonal));
        datosCita.add(Box.createVerticalStrut(10));
        datosCita.add(alineado(datoResponsable));
        tarjeta.add(alineado(datosCita));

        formularioAgenda.setLayout(new BoxLayout(formularioAgenda, BoxLayout.Y_AXIS));
        formularioAgenda.setOpaque(false);
        formularioAgenda.add(alineado(conEtiqueta("Fecha y hora (dd/mm/aaaa hh:mm)",
                campoFechaEntrevista)));
        formularioAgenda.add(Box.createVerticalStrut(10));
        formularioAgenda.add(alineado(conEtiqueta("Integrantes del personal (separados por"
                + " comas)", campoPersonal)));
        formularioAgenda.add(Box.createVerticalStrut(10));
        formularioAgenda.add(alineado(conEtiqueta("Responsable (exactamente 1)",
                comboResponsable)));
        formularioAgenda.add(Box.createVerticalStrut(14));
        JPanel filaAgendar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        filaAgendar.setOpaque(false);
        filaAgendar.add(botonAgendar);
        formularioAgenda.add(alineado(filaAgendar));
        tarjeta.add(alineado(formularioAgenda));

        tarjeta.add(Box.createVerticalStrut(20));
        JSeparator separador = new JSeparator();
        separador.setForeground(Estilos.BORDE_TARJETA);
        separador.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        tarjeta.add(alineado(separador));
        tarjeta.add(Box.createVerticalStrut(20));

        tarjeta.add(alineado(subtitulo("Asistencia y resultado")));
        tarjeta.add(Box.createVerticalStrut(10));
        tarjeta.add(alineado(datoAsistencia));
        tarjeta.add(Box.createVerticalStrut(10));
        tarjeta.add(alineado(datoResultado));
        tarjeta.add(Box.createVerticalStrut(10));
        tarjeta.add(alineado(indicadorInasistencias));
        tarjeta.add(Box.createVerticalStrut(14));

        accionesAsistencia.setOpaque(false);
        accionesAsistencia.add(botonAsistio);
        accionesAsistencia.add(Box.createHorizontalStrut(10));
        accionesAsistencia.add(botonNoAsistio);
        tarjeta.add(alineado(accionesAsistencia));

        accionesResultado.setOpaque(false);
        comboResultado.setPreferredSize(new Dimension(170,
                comboResultado.getPreferredSize().height));
        accionesResultado.add(comboResultado);
        accionesResultado.add(Box.createHorizontalStrut(10));
        accionesResultado.add(botonResultado);
        tarjeta.add(alineado(accionesResultado));
        tarjeta.add(Box.createVerticalStrut(20));
        tarjeta.add(Box.createVerticalGlue());

        tarjeta.add(alineado(texto("Una reprogramación por inasistencia; segunda ausencia →"
                + " CANCELADA. NO_FAVORABLE → RECHAZADA.", 340)));

        campoPersonal.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                llenarResponsables();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                llenarResponsables();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                llenarResponsables();
            }
        });
        botonAgendar.addActionListener(evento -> agendar());
        botonAsistio.addActionListener(evento -> registrarAsistencia());
        botonNoAsistio.addActionListener(evento -> registrarInasistencia());
        botonResultado.addActionListener(evento -> registrarResultado());
        return tarjeta;
    }

    private void recargarLista() {
        Solicitud seleccionada = actual;
        ArrayList<Solicitud> todas = solicitudes.listar();
        llenandoLista = true;
        comboSolicitud.removeAllItems();
        for (Solicitud solicitud : todas) {
            if (solicitud.getExpediente() != null) {
                comboSolicitud.addItem(solicitud);
            }
        }
        if (seleccionada != null && seleccionada.getExpediente() != null
                && todas.contains(seleccionada)) {
            comboSolicitud.setSelectedItem(seleccionada);
        } else if (comboSolicitud.getItemCount() > 0) {
            comboSolicitud.setSelectedIndex(0);
        }
        llenandoLista = false;
        abrir((Solicitud) comboSolicitud.getSelectedItem());
    }

    private void abrir(Solicitud solicitud) {
        if (solicitud != actual) {
            mostrarMensaje(" ", false);
            campoFechaDocumento.setText(LocalDateTime.now().format(FORMATO_FECHA));
            campoFechaEntrevista.setText("");
            campoPersonal.setText("");
            tablaDocumentos.clearSelection();
        }
        boolean otraSolicitud = solicitud != actual;
        actual = solicitud;
        revisarVencimientos();
        refrescar();
        if (otraSolicitud) {
            SwingUtilities.invokeLater(() -> desplazable.getViewport()
                    .setViewPosition(new Point(0, 0)));
        }
    }

    private void revisarVencimientos() {
        try {
            ArrayList<String> avisos = Vencimientos.revisar(LocalDateTime.now(), solicitudes,
                    matriculas);
            if (!avisos.isEmpty()) {
                mostrarMensaje("Revisión de vencimientos incompleta: "
                        + Vencimientos.unir(avisos), true);
            }
        } catch (ReglaDominioException e) {
            mostrarMensaje(e.getMessage(), true);
        }
    }

    private void refrescar() {
        contenido.setVisible(actual != null);
        franja.setVisible(actual != null);
        if (actual == null) {
            titulo.setText("Expediente");
            identificacion.setText("Aún no hay solicitudes con la inscripción confirmada.");
            return;
        }
        titulo.setText("Expediente · " + actual.getAlumno().getNombreCompleto());
        Apoderado principal = actual.getAlumno().getPrincipal();
        identificacion.setText(actual.getCodigo() + " · " + actual.getAula().getNombre() + " ("
                + actual.getAula().getEdadRequerida() + " años) · Apoderado principal: "
                + (principal == null ? SIN_DATO : principal.getNombreCompleto()));
        pintarFranja();
        pintarDocumentos();
        pintarObservaciones();
        pintarEntrevista();
        revalidate();
        repaint();
    }

    private void pintarFranja() {
        ExpedienteDocumentos expediente = actual.getExpediente();
        EstadoSolicitud estado = actual.getEstado();
        Color fondo = Estilos.ESPERA;
        Color texto = Estilos.TEXTO_ESPERA;
        if (estado == EstadoSolicitud.EN_ESPERA_FAVORABLE) {
            fondo = Estilos.FAVORABLE;
            texto = Estilos.TEXTO_PRINCIPAL;
        } else if (!actual.estaActiva()) {
            fondo = Estilos.ALERTA;
            texto = Estilos.TEXTO_ALERTA;
        }
        Estilos.aviso(franja, fondo);
        estadoFranja.setText("ESTADO: " + estado.name());
        estadoFranja.setForeground(texto);
        fechasFranja.setText("Inscripción confirmada "
                + FormatoFecha.diaHora(actual.getFechaConfirmacionInscripcion())
                + " → entrega hasta " + FormatoFecha.dia(expediente.fechaLimiteEntrega()));
        fechasFranja.setForeground(Estilos.TEXTO_PRINCIPAL);
        String detalle = detalleEstado();
        detalleFranja.setText(detalle == null ? "" : detalle);
        detalleFranja.setVisible(detalle != null);
        detalleFranja.setForeground(texto);
        LocalDate correccion = proximaCorreccion(expediente);
        correccionFranja.setText(correccion == null ? ""
                : "Corrección: " + FormatoFecha.dia(correccion));
        correccionFranja.setForeground(texto);
    }

    private String detalleEstado() {
        Entrevista entrevista = entrevistas.buscarDe(actual);
        switch (actual.getEstado()) {
            case CANCELADA:
                return motivoCancelacion(entrevista) + " Sin reembolso de la inscripción.";
            case RECHAZADA:
                return "Entrevista NO_FAVORABLE. Una solicitud nueva exige un nuevo pago de"
                        + " inscripción.";
            case EN_ESPERA_FAVORABLE:
                return "Entrevista FAVORABLE · en espera favorable desde "
                        + FormatoFecha.diaHora(actual.getFechaIngresoCola())
                        + ". La vacante se ofrece desde Colas y vacantes.";
            default:
                return null;
        }
    }

    private String motivoCancelacion(Entrevista entrevista) {
        if (actual.getMotivoCancelacion() != null) {
            return actual.getMotivoCancelacion();
        }
        if (entrevista != null
                && entrevista.getInasistencias() >= Entrevista.INASISTENCIAS_PERMITIDAS) {
            return "Segunda inasistencia del apoderado a la entrevista.";
        }
        return "Solicitud CANCELADA.";
    }

    private static LocalDate proximaCorreccion(ExpedienteDocumentos expediente) {
        LocalDate proxima = null;
        for (Documento documento : expediente.getDocumentos()) {
            LocalDate limite = documento.fechaLimiteCorreccion();
            if (limite != null && (proxima == null || limite.isBefore(proxima))) {
                proxima = limite;
            }
        }
        return proxima;
    }

    private void pintarDocumentos() {
        int fila = tablaDocumentos.getSelectedRow();
        ArrayList<Documento> documentos = actual.getExpediente().getDocumentos();
        modeloDocumentos.mostrar(documentos);
        int validados = 0;
        for (Documento documento : documentos) {
            if (documento.estaValidado()) {
                validados++;
            }
        }
        tituloDocumentos.setText("Documentos · " + validados + " de " + documentos.size()
                + " validados");
        if (fila >= 0 && fila < documentos.size()) {
            tablaDocumentos.setRowSelectionInterval(fila, fila);
        }
        actualizarBotonesDocumento();
    }

    private void actualizarBotonesDocumento() {
        Documento documento = seleccionado();
        boolean enDocumentacion = actual != null
                && actual.getEstado() == EstadoSolicitud.EN_DOCUMENTACION;
        boolean editable = enDocumentacion && documento != null;
        EstadoDocumento estado = documento == null ? null : documento.getEstado();
        boolean observado = estado == EstadoDocumento.OBSERVADO;
        botonEntrega.setText(observado ? "Registrar corrección" : "Registrar entrega");
        botonEntrega.setEnabled(editable && (estado == null || observado));
        botonValidar.setEnabled(editable && estado == EstadoDocumento.EN_REVISION);
        botonObservar.setEnabled(editable && estado == EstadoDocumento.EN_REVISION);
        Estilos.editable(campoFechaDocumento, enDocumentacion);
    }

    private void pintarObservaciones() {
        observaciones.removeAll();
        ArrayList<Documento> observados = new ArrayList<Documento>();
        for (Documento documento : actual.getExpediente().getDocumentos()) {
            if (documento.getEstado() == EstadoDocumento.OBSERVADO) {
                observados.add(documento);
            }
        }
        for (Documento documento : observados) {
            String prefijo = observados.size() > 1
                    ? documento.getTipo().getDescripcion() + ": " : "";
            JLabel linea = new JLabel(prefijo + documento.getObservacion() + " · comunicado "
                    + FormatoFecha.diaHora(documento.getFechaHoraComunicacion()));
            linea.setFont(Estilos.datoDestacado());
            linea.setForeground(Estilos.TEXTO_ALERTA);
            observaciones.add(alineado(linea));
            observaciones.add(Box.createVerticalStrut(10));
        }
        if (!observados.isEmpty()) {
            observaciones.add(alineado(texto(Documento.DIAS_CORRECCION + " días calendario para"
                    + " corregir; vuelve a revisión. Si vence sin corrección, se cancela la"
                    + " solicitud.", 560)));
            observaciones.add(Box.createVerticalStrut(20));
        }
    }

    private void pintarEntrevista() {
        Entrevista entrevista = entrevistas.buscarDe(actual);
        ExpedienteDocumentos expediente = actual.getExpediente();
        boolean agendable = entrevista == null
                && actual.getEstado() == EstadoSolicitud.EN_DOCUMENTACION
                && expediente.puedeAgendarEntrevista();
        pintarEstadoEntrevista(entrevista, agendable);

        formularioAgenda.setVisible(agendable);
        datosCita.setVisible(!agendable);
        datoFecha.setText("Fecha y hora: " + (entrevista == null ? SIN_DATO
                : FormatoFecha.diaHora(entrevista.getFechaHora())));
        datoPersonal.setText("Integrantes del personal (al menos 1): " + (entrevista == null
                ? SIN_DATO : String.join(", ", entrevista.getPersonal())));
        datoResponsable.setText("Responsable (exactamente 1): " + (entrevista == null
                ? SIN_DATO : entrevista.getResponsable()));

        datoAsistencia.setText("Asistencia: " + textoAsistencia(entrevista));
        datoResultado.setText("Resultado: " + (entrevista == null
                || entrevista.getResultado() == null ? "Pendiente · FAVORABLE / NO_FAVORABLE"
                        : entrevista.getResultado().name()));
        indicadorInasistencias.setText("Inasistencias: "
                + (entrevista == null ? 0 : entrevista.getInasistencias()) + " de "
                + Entrevista.INASISTENCIAS_PERMITIDAS);

        boolean pendiente = entrevista != null && entrevista.esDeLaInscripcionVigente()
                && entrevista.getResultado() == null;
        boolean sinAsistencia = pendiente && entrevista.getAsistioApoderado() == null;
        accionesAsistencia.setVisible(sinAsistencia);
        accionesResultado.setVisible(pendiente && entrevista.isRealizada());
    }

    private void pintarEstadoEntrevista(Entrevista entrevista, boolean agendable) {
        Color fondo = Estilos.FONDO_CAMPO;
        Color texto = Estilos.SECUNDARIO;
        String estado;
        String motivo;
        if (agendable) {
            fondo = Estilos.FAVORABLE;
            texto = Estilos.TEXTO_PRINCIPAL;
            estado = "AGENDABLE";
            motivo = "Los 4 documentos están validados.";
        } else if (entrevista == null) {
            estado = "BLOQUEADA" + resumenPendientes();
            motivo = actual.getEstado() == EstadoSolicitud.EN_DOCUMENTACION
                    ? "Agendable al validar los 4 documentos."
                    : "La solicitud está " + actual.getEstado() + ".";
        } else if (entrevista.getResultado() == ResultadoEntrevista.FAVORABLE) {
            fondo = Estilos.FAVORABLE;
            texto = Estilos.TEXTO_PRINCIPAL;
            estado = "FAVORABLE";
            motivo = "La solicitud pasó a la espera favorable.";
        } else if (entrevista.getResultado() == ResultadoEntrevista.NO_FAVORABLE) {
            fondo = Estilos.ALERTA;
            texto = Estilos.TEXTO_ALERTA;
            estado = "NO_FAVORABLE";
            motivo = "La solicitud quedó RECHAZADA.";
        } else if (!actual.estaActiva()) {
            fondo = Estilos.ALERTA;
            texto = Estilos.TEXTO_ALERTA;
            estado = "CANCELADA";
            motivo = motivoCancelacion(entrevista);
        } else if (entrevista.isRealizada()) {
            fondo = Estilos.ESPERA;
            texto = Estilos.TEXTO_ESPERA;
            estado = "REALIZADA";
            motivo = "Falta registrar el resultado.";
        } else {
            fondo = Estilos.ESPERA;
            texto = Estilos.TEXTO_ESPERA;
            estado = entrevista.getInasistencias() > 0 ? "REPROGRAMADA" : "AGENDADA";
            motivo = "Cita: " + FormatoFecha.diaHora(entrevista.getFechaHora()) + ".";
        }
        Estilos.aviso(cajaEstadoEntrevista, fondo);
        estadoEntrevista.setText(estado);
        estadoEntrevista.setForeground(texto);
        motivoEntrevista.setText(motivo);
        motivoEntrevista.setForeground(texto);
    }

    private String resumenPendientes() {
        int observados = 0;
        int sinValidar = 0;
        for (Documento documento : actual.getExpediente().pendientesDeValidar()) {
            if (documento.getEstado() == EstadoDocumento.OBSERVADO) {
                observados++;
            } else {
                sinValidar++;
            }
        }
        String resumen = "";
        if (observados > 0) {
            resumen += " · " + observados + (observados == 1 ? " observado" : " observados");
        }
        if (sinValidar > 0) {
            resumen += " · " + sinValidar + " sin validar";
        }
        return resumen;
    }

    private static String textoAsistencia(Entrevista entrevista) {
        if (entrevista == null) {
            return "Pendiente";
        }
        if (Boolean.TRUE.equals(entrevista.getAsistioApoderado())) {
            return "Asistió · entrevista realizada";
        }
        if (entrevista.getInasistencias() >= Entrevista.INASISTENCIAS_PERMITIDAS) {
            return "No asistió (segunda inasistencia)";
        }
        if (entrevista.getInasistencias() > 0) {
            return "Pendiente · reprogramada tras una inasistencia";
        }
        return "Pendiente";
    }

    private void llenarResponsables() {
        Object elegido = comboResponsable.getSelectedItem();
        comboResponsable.removeAllItems();
        for (String nombre : leerPersonal()) {
            comboResponsable.addItem(nombre);
        }
        if (elegido != null) {
            comboResponsable.setSelectedItem(elegido);
        }
    }

    private ArrayList<String> leerPersonal() {
        ArrayList<String> personal = new ArrayList<String>();
        for (String parte : campoPersonal.getText().split(",")) {
            String nombre = parte.trim();
            if (!nombre.isEmpty() && !personal.contains(nombre)) {
                personal.add(nombre);
            }
        }
        return personal;
    }

    private void registrarEntrega() {
        Documento documento = seleccionado();
        ejecutar(() -> {
            LocalDateTime fecha = leerFecha(campoFechaDocumento.getText(), false);
            if (fecha.isBefore(actual.getFechaConfirmacionInscripcion())) {
                throw new DatoInvalidoException("La entrega no puede ser anterior a la"
                        + " confirmación de la inscripción ("
                        + FormatoFecha.diaHora(actual.getFechaConfirmacionInscripcion()) + ").");
            }
            if (documento.getEstado() == EstadoDocumento.OBSERVADO) {
                documento.entregarCorreccion(fecha);
            } else {
                documento.registrarEntrega(fecha);
            }
        }, documento.getTipo().getDescripcion() + ": entrega registrada.");
    }

    private void validarDocumento() {
        Documento documento = seleccionado();
        ejecutar(documento::validar, documento.getTipo().getDescripcion() + ": VALIDADO.");
    }

    private void observarDocumento() {
        Documento documento = seleccionado();
        String motivo = JOptionPane.showInputDialog(this,
                "Motivo de la observación del " + documento.getTipo().getDescripcion()
                        + ".\nSe registra como comunicada en la fecha del formulario.",
                "Observar documento", JOptionPane.PLAIN_MESSAGE);
        if (motivo == null) {
            return;
        }
        ejecutar(() -> documento.observar(motivo,
                leerFecha(campoFechaDocumento.getText(), false)),
                documento.getTipo().getDescripcion() + ": OBSERVADO.");
    }

    private void agendar() {
        ejecutar(() -> entrevistas.agendar(actual, actual.getExpediente(),
                leerFecha(campoFechaEntrevista.getText(), true), leerPersonal(),
                (String) comboResponsable.getSelectedItem()), "Entrevista agendada.");
    }

    private void registrarAsistencia() {
        Entrevista entrevista = entrevistas.buscarDe(actual);
        ejecutar(() -> {
            entrevista.registrarAsistencia();
            entrevista.marcarRealizada();
        }, "Asistencia registrada: ya se puede registrar el resultado.");
    }

    private void registrarInasistencia() {
        Entrevista entrevista = entrevistas.buscarDe(actual);
        if (entrevista.getInasistencias() + 1 < Entrevista.INASISTENCIAS_PERMITIDAS) {
            String texto = JOptionPane.showInputDialog(this,
                    "El apoderado no asistió. Se permite una sola reprogramación.\n"
                            + "Nueva fecha y hora (dd/mm/aaaa hh:mm):",
                    "Reprogramar entrevista", JOptionPane.PLAIN_MESSAGE);
            if (texto == null) {
                return;
            }
            ejecutar(() -> entrevista.registrarInasistencia(leerFecha(texto, true)),
                    "Inasistencia registrada: entrevista reprogramada.");
            return;
        }
        int respuesta = JOptionPane.showConfirmDialog(this,
                "Es la segunda inasistencia: la solicitud " + actual.getCodigo()
                        + " quedará CANCELADA.\n¿Registrar la inasistencia?",
                "Segunda inasistencia", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (respuesta != JOptionPane.YES_OPTION) {
            return;
        }
        ejecutar(() -> entrevista.registrarInasistencia(null),
                "Segunda inasistencia: la solicitud quedó CANCELADA.");
    }

    private void registrarResultado() {
        Entrevista entrevista = entrevistas.buscarDe(actual);
        ResultadoEntrevista resultado = (ResultadoEntrevista) comboResultado.getSelectedItem();
        ejecutar(() -> entrevista.registrarResultado(resultado,
                Turnos.fechaAlFinalFavorable(actual, LocalDateTime.now(), solicitudes)),
                "Resultado " + resultado + " registrado.");
    }

    private void ejecutar(Accion accion, String exito) {
        revisarVencimientos();
        if (!actual.estaActiva()) {
            refrescar();
            mostrarMensaje("La solicitud ya está " + actual.getEstado()
                    + ": no se registró la operación.", true);
            return;
        }
        try {
            accion.ejecutar();
            mostrarMensaje(exito, false);
            campoFechaDocumento.setText(LocalDateTime.now().format(FORMATO_FECHA));
        } catch (ReglaDominioException e) {
            mostrarMensaje(e.getMessage(), true);
        }
        refrescar();
    }

    private static LocalDateTime leerFecha(String texto, boolean futura)
            throws DatoInvalidoException {
        LocalDateTime fecha;
        try {
            fecha = LocalDateTime.parse(texto.trim(), FORMATO_FECHA);
        } catch (DateTimeParseException e) {
            throw new DatoInvalidoException("Use el formato dd/mm/aaaa hh:mm.");
        }
        LocalDateTime ahora = LocalDateTime.now();
        if (futura && !fecha.isAfter(ahora)) {
            throw new DatoInvalidoException("La cita debe ser en una fecha futura.");
        }
        if (!futura && fecha.isAfter(ahora)) {
            throw new DatoInvalidoException("La fecha no puede ser futura.");
        }
        return fecha;
    }

    private Documento seleccionado() {
        int fila = tablaDocumentos.getSelectedRow();
        return fila < 0 ? null : modeloDocumentos.documento(fila);
    }

    private void mostrarMensaje(String texto, boolean error) {
        mensaje.setText(texto);
        mensaje.setForeground(error ? Estilos.TEXTO_ALERTA : Estilos.TEXTO_PRINCIPAL);
    }

    private static JLabel dato() {
        JLabel dato = new JLabel(" ");
        dato.setFont(Estilos.dato());
        dato.setForeground(Estilos.SECUNDARIO);
        return dato;
    }

    private static JLabel subtitulo(String texto) {
        JLabel subtitulo = new JLabel(texto);
        subtitulo.setFont(Estilos.seccion());
        subtitulo.setForeground(Estilos.TEXTO_PRINCIPAL);
        return subtitulo;
    }

    private static JLabel texto(String texto, int ancho) {
        JLabel etiqueta = new JLabel("<html><div style='width:" + ancho + "px'>" + texto
                + "</div></html>");
        etiqueta.setFont(Estilos.contenido());
        etiqueta.setForeground(Estilos.SECUNDARIO);
        return etiqueta;
    }

    private static JPanel conEtiqueta(String etiqueta, JComponent campo) {
        JPanel columna = new JPanel(new BorderLayout(0, 6));
        columna.setOpaque(false);
        columna.add(Estilos.etiquetaCampo(etiqueta), BorderLayout.NORTH);
        columna.add(campo, BorderLayout.CENTER);
        return columna;
    }

    private static <T extends JComponent> T alineado(T componente) {
        componente.setAlignmentX(LEFT_ALIGNMENT);
        return componente;
    }

    private static final class ModeloDocumentos extends AbstractTableModel {

        private static final String[] COLUMNAS = { "Documento", "Entregado", "Revisión" };

        private ArrayList<Documento> documentos = new ArrayList<Documento>();

        void mostrar(ArrayList<Documento> nuevos) {
            documentos = nuevos;
            fireTableDataChanged();
        }

        Documento documento(int fila) {
            return documentos.get(fila);
        }

        @Override
        public int getRowCount() {
            return documentos.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNAS.length;
        }

        @Override
        public String getColumnName(int columna) {
            return COLUMNAS[columna];
        }

        @Override
        public Object getValueAt(int fila, int columna) {
            Documento documento = documentos.get(fila);
            if (columna == 0) {
                return documento.getTipo().getDescripcion();
            }
            if (columna == 2) {
                return documento.getEstado();
            }
            if (!documento.estaEntregado()) {
                return SIN_DATO;
            }
            if (documento.getFechaHoraCorreccion() != null) {
                return FormatoFecha.diaHora(documento.getFechaHoraCorreccion()) + " (corrección)";
            }
            return FormatoFecha.diaHora(documento.getFechaHoraEntrega());
        }
    }

    private final class RenderDocumento extends DefaultTableCellRenderer {
        private final Font fuenteDato = Estilos.dato();
        private final Font fuenteRevision = Estilos.datoDestacado();

        @Override
        public Component getTableCellRendererComponent(JTable tabla, Object valor,
                boolean seleccionado, boolean conFoco, int fila, int columna) {
            Documento documento = modeloDocumentos.documento(fila);
            Object texto = valor;
            if (columna == 2) {
                EstadoDocumento estado = (EstadoDocumento) valor;
                texto = estado == null ? "PENDIENTE" : estado.name().replace('_', ' ');
            }
            super.getTableCellRendererComponent(tabla, texto, false, false, fila, columna);
            setBorder(Estilos.MARGEN_CELDA);
            setFont(columna == 2 ? fuenteRevision : fuenteDato);
            setForeground(columna == 2 ? Estilos.colorTexto(documento.getEstado())
                    : Estilos.TEXTO_PRINCIPAL);
            if (seleccionado) {
                setBackground(Estilos.FONDO_LATERAL);
            } else if (documento.getEstado() == EstadoDocumento.OBSERVADO) {
                setBackground(Estilos.ALERTA);
            } else {
                setBackground(Estilos.TARJETA);
            }
            return this;
        }
    }

    private static final class RenderCabecera extends DefaultTableCellRenderer {
        private final Font fuente = Estilos.etiqueta();

        @Override
        public Component getTableCellRendererComponent(JTable tabla, Object valor,
                boolean seleccionado, boolean conFoco, int fila, int columna) {
            super.getTableCellRendererComponent(tabla, String.valueOf(valor).toUpperCase(),
                    false, false, fila, columna);
            setFont(fuente);
            setForeground(Estilos.SECUNDARIO);
            setBackground(Estilos.FONDO_CAMPO);
            setBorder(Estilos.MARGEN_CABECERA_TABLA);
            return this;
        }
    }
}
