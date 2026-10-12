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
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.time.LocalDateTime;
import java.util.ArrayList;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import datos.ArregloAulas;
import datos.ArregloEntrevistas;
import datos.ArregloMatriculas;
import datos.ArregloSolicitudes;
import modelo.Aula;
import modelo.Entrevista;
import modelo.EstadoSolicitud;
import modelo.Matricula;
import modelo.ReglaDominioException;
import modelo.ResultadoEntrevista;
import modelo.Solicitud;
import negocio.Turnos;
import negocio.Vacantes;
import negocio.Vencimientos;
import util.FechasAdmision;
import util.FormatoFecha;
import util.Reloj;

public class PanelColas extends JPanel {

    private static final String SIN_DATO = "—";
    private static final String HECHO = "✓  ";
    private static final String PENDIENTE = "○  ";

    private final ArregloAulas aulas;
    private final ArregloSolicitudes solicitudes;
    private final ArregloEntrevistas entrevistas;
    private final ArregloMatriculas matriculas;

    private Solicitud turnoActual;
    private boolean llenandoAulas;

    private final JComboBox<Aula> comboAula = Estilos.campo(new JComboBox<Aula>());
    private final JLabel mensaje = new JLabel(" ");
    // Separado de mensaje: ofertar escribe ahí su confirmación y luego vuelve a revisar.
    private final JLabel avisoVencimientos = new JLabel(" ");

    // Habilitadas por la revisión de turnos a las que aún no se ha llamado. Se acumulan entre
    // revisiones: la siguiente ya no las devuelve, y cambiar de aula no debe borrar a quién avisar.
    private final ArrayList<Solicitud> porAvisar = new ArrayList<Solicitud>();
    private final JPanel franjaAvisar = Estilos.aviso(new JPanel(new BorderLayout(16, 0)),
            Estilos.FAVORABLE);
    private final JLabel textoAvisar = new JLabel(" ");

    private final JLabel tituloAula = new JLabel(" ");
    private final JLabel desglose = new JLabel(" ");
    private final JLabel cifraVacantes = new JLabel(" ");

    private final JLabel tituloOrden = new JLabel(" ");
    private final ModeloOrden modeloOrden = new ModeloOrden();
    private final JTable tablaOrden = new JTable(modeloOrden);
    private final JPanel contenedorTabla = new JPanel(new BorderLayout());
    private final JLabel colaVacia = new JLabel(" ");

    private final JLabel tituloTurno = new JLabel(" ");
    private final JPanel cajaEstado = new JPanel();
    private final JLabel estadoTurno = new JLabel(" ");
    private final JLabel detalleTurno = new JLabel(" ");
    private final JPanel comprobaciones = new JPanel();
    private final JLabel checkDocumentos = check();
    private final JLabel checkEntrevista = check();
    private final JLabel checkCupo = check();
    private final JLabel checkConfirmacion = check();
    private final JTextField campoPersonal = Estilos.campo(new JTextField());
    private final JButton botonAceptada = Estilos.botonPrimario("Confirmar oferta y crear matrícula");
    private final JButton botonNoAceptada = Estilos.botonSecundario("Oferta no aceptada");

    public PanelColas(ArregloAulas aulas, ArregloSolicitudes solicitudes,
            ArregloEntrevistas entrevistas, ArregloMatriculas matriculas) {
        super(new BorderLayout(0, 20));
        this.aulas = aulas;
        this.solicitudes = solicitudes;
        this.entrevistas = entrevistas;
        this.matriculas = matriculas;
        setBorder(Estilos.MARGEN_PANTALLA);

        add(crearEncabezado(), BorderLayout.NORTH);

        JPanel contenido = new JPanel(new GridBagLayout());
        contenido.setOpaque(false);
        GridBagConstraints restricciones = new GridBagConstraints();
        restricciones.gridy = 0;
        restricciones.gridwidth = 2;
        restricciones.anchor = GridBagConstraints.NORTH;
        restricciones.fill = GridBagConstraints.BOTH;
        restricciones.weightx = 1;
        restricciones.insets = new Insets(0, 0, 20, 0);
        contenido.add(crearTarjetaAula(), restricciones);
        restricciones.gridy = 1;
        restricciones.gridwidth = 1;
        restricciones.weightx = 0.62;
        restricciones.insets = new Insets(0, 0, 0, 20);
        contenido.add(crearTarjetaOrden(), restricciones);
        restricciones.weightx = 0.38;
        restricciones.insets = new Insets(0, 0, 0, 0);
        contenido.add(crearTarjetaTurno(), restricciones);

        PanelAncho envoltura = new PanelAncho();
        envoltura.add(contenido, BorderLayout.NORTH);
        JScrollPane desplazable = new JScrollPane(envoltura);
        desplazable.setBorder(null);
        desplazable.setOpaque(false);
        desplazable.getViewport().setOpaque(false);
        desplazable.getVerticalScrollBar().setUnitIncrement(16);
        add(desplazable, BorderLayout.CENTER);

        // Otras pantallas cambian matrículas, pagos y entrevistas: el orden y el cupo se
        // recalculan cada vez que se vuelve aquí, nunca se guardan.
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                actualizar();
            }
        });
        llenarAulas();
        refrescar();
    }

    private JPanel crearEncabezado() {
        JPanel encabezado = new JPanel();
        encabezado.setLayout(new BoxLayout(encabezado, BoxLayout.Y_AXIS));
        encabezado.setOpaque(false);

        JPanel filaTitulo = new JPanel(new BorderLayout(20, 0));
        filaTitulo.setOpaque(false);
        JLabel titulo = new JLabel("Colas y disponibilidad");
        titulo.setFont(Estilos.titulo());
        titulo.setForeground(Estilos.TEXTO_PRINCIPAL);
        titulo.setBorder(Estilos.MARGEN_TEXTO_AJUSTADO);
        filaTitulo.add(titulo, BorderLayout.WEST);
        filaTitulo.add(crearSelectorAula(), BorderLayout.EAST);
        encabezado.add(Estilos.alineado(filaTitulo));
        encabezado.add(Box.createVerticalStrut(4));

        JLabel subtitulo = new JLabel("La vacante habilita una selección concreta; no a todos"
                + " los integrantes de la cola.");
        subtitulo.setFont(Estilos.subtitulo());
        subtitulo.setForeground(Estilos.SECUNDARIO);
        encabezado.add(Estilos.alineado(subtitulo));
        encabezado.add(Box.createVerticalStrut(10));

        mensaje.setFont(Estilos.contenidoDestacado());
        mensaje.setForeground(Estilos.TEXTO_PRINCIPAL);
        encabezado.add(Estilos.alineado(mensaje));
        avisoVencimientos.setFont(Estilos.contenidoDestacado());
        avisoVencimientos.setForeground(Estilos.TEXTO_ALERTA);
        encabezado.add(Estilos.alineado(avisoVencimientos));
        encabezado.add(Box.createVerticalStrut(10));
        encabezado.add(Estilos.alineado(crearFranjaAvisar()));
        return encabezado;
    }

    // Franja dentro del panel y no un diálogo: se queda a la vista mientras el personal llama,
    // sin interrumpir cada vez que se vuelve a la pantalla.
    private JPanel crearFranjaAvisar() {
        textoAvisar.setFont(Estilos.detalle());
        textoAvisar.setForeground(Estilos.BOTON_PRINCIPAL);
        franjaAvisar.add(textoAvisar, BorderLayout.CENTER);

        JButton avisadas = Estilos.botonSecundario("Ya se avisó");
        avisadas.addActionListener(evento -> {
            porAvisar.clear();
            refrescarAvisar();
        });
        JPanel contenedorBoton = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        contenedorBoton.setOpaque(false);
        contenedorBoton.add(avisadas);
        franjaAvisar.add(contenedorBoton, BorderLayout.EAST);
        franjaAvisar.setVisible(false);
        return franjaAvisar;
    }

    private JPanel crearSelectorAula() {
        JPanel selector = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        selector.setOpaque(false);
        selector.add(Estilos.etiquetaCampo("Aula"));
        selector.add(Box.createHorizontalStrut(12));
        comboAula.setPreferredSize(new Dimension(220, comboAula.getPreferredSize().height));
        comboAula.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> lista, Object valor,
                    int indice, boolean seleccionado, boolean conFoco) {
                Object texto = valor;
                if (valor instanceof Aula) {
                    Aula aula = (Aula) valor;
                    texto = aula.getNombre() + " · " + aula.getEdadRequerida() + " años";
                }
                return super.getListCellRendererComponent(lista, texto, indice, seleccionado,
                        conFoco);
            }
        });
        comboAula.addActionListener(evento -> {
            if (!llenandoAulas) {
                mostrarMensaje(" ");
                actualizar();
            }
        });
        selector.add(comboAula);
        return selector;
    }

    private JPanel crearTarjetaAula() {
        JPanel tarjeta = Estilos.tarjeta(new JPanel(new BorderLayout(24, 0)));
        Estilos.rellenarTarjeta(tarjeta);

        JPanel izquierda = new JPanel();
        izquierda.setLayout(new BoxLayout(izquierda, BoxLayout.Y_AXIS));
        izquierda.setOpaque(false);
        tituloAula.setFont(Estilos.tituloTarjeta());
        tituloAula.setForeground(Estilos.TEXTO_PRINCIPAL);
        izquierda.add(Estilos.alineado(tituloAula));
        izquierda.add(Box.createVerticalStrut(6));
        JLabel detalle = new JLabel("Edad al " + FormatoFecha.dia(FechasAdmision.CORTE_2027)
                + " " + FechasAdmision.CORTE_2027.getYear()
                + " · única sede · cupo calculado en tiempo real");
        detalle.setFont(Estilos.detalle());
        detalle.setForeground(Estilos.SECUNDARIO);
        // En un BoxLayout vertical la etiqueta no pasa de su ancho preferido, que se queda
        // corto frente a lo que pinta la fuente: se le deja ocupar todo el ancho.
        detalle.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                detalle.getPreferredSize().height));
        izquierda.add(Estilos.alineado(detalle));
        izquierda.add(Box.createVerticalStrut(16));

        JPanel franja = Estilos.aviso(new JPanel(new BorderLayout()), Estilos.FONDO_CAMPO);
        desglose.setFont(Estilos.contenidoDestacado());
        desglose.setForeground(Estilos.TEXTO_PRINCIPAL);
        franja.add(desglose, BorderLayout.WEST);
        izquierda.add(Estilos.alineado(franja));
        tarjeta.add(izquierda, BorderLayout.CENTER);

        JPanel vacantes = Estilos.aviso(new JPanel(), Estilos.ESPERA);
        vacantes.setLayout(new BoxLayout(vacantes, BoxLayout.Y_AXIS));
        vacantes.setPreferredSize(new Dimension(150, 0));
        JLabel etiqueta = new JLabel("VACANTES");
        etiqueta.setFont(Estilos.etiqueta());
        etiqueta.setForeground(Estilos.TEXTO_ESPERA);
        vacantes.add(Estilos.alineado(etiqueta));
        vacantes.add(Box.createVerticalStrut(8));
        cifraVacantes.setFont(Estilos.cifra());
        cifraVacantes.setForeground(Estilos.TEXTO_PRINCIPAL);
        vacantes.add(Estilos.alineado(cifraVacantes));
        tarjeta.add(vacantes, BorderLayout.EAST);
        return tarjeta;
    }

    private JPanel crearTarjetaOrden() {
        JPanel tarjeta = Estilos.tarjeta(new JPanel());
        tarjeta.setLayout(new BoxLayout(tarjeta, BoxLayout.Y_AXIS));

        tituloOrden.setFont(Estilos.panel());
        tituloOrden.setForeground(Estilos.TEXTO_PRINCIPAL);
        tarjeta.add(Estilos.alineado(tituloOrden));
        tarjeta.add(Box.createVerticalStrut(8));
        tarjeta.add(Estilos.alineado(texto("FAVORABLE primero; luego inscripciones en curso y"
                + " SIN PAGO. Antigüedad dentro de cada cola.", 520)));
        tarjeta.add(Box.createVerticalStrut(18));

        tablaOrden.setFont(Estilos.dato());
        tablaOrden.setRowHeight(52);
        tablaOrden.setShowVerticalLines(false);
        tablaOrden.setGridColor(Estilos.BORDE_TARJETA);
        tablaOrden.setIntercellSpacing(new Dimension(0, 1));
        // La tabla solo informa: la única fila que se atiende es el turno actual.
        tablaOrden.setRowSelectionAllowed(false);
        tablaOrden.setFocusable(false);
        tablaOrden.setDefaultRenderer(Object.class, new RenderOrden());
        tablaOrden.getTableHeader().setReorderingAllowed(false);
        tablaOrden.getTableHeader().setDefaultRenderer(new RenderCabecera());
        tablaOrden.getColumnModel().getColumn(0).setPreferredWidth(190);
        tablaOrden.getColumnModel().getColumn(1).setPreferredWidth(240);
        tablaOrden.getColumnModel().getColumn(2).setPreferredWidth(140);
        tablaOrden.getColumnModel().getColumn(3).setPreferredWidth(100);

        contenedorTabla.setOpaque(false);
        contenedorTabla.add(tablaOrden.getTableHeader(), BorderLayout.NORTH);
        contenedorTabla.add(tablaOrden, BorderLayout.CENTER);
        tarjeta.add(Estilos.alineado(contenedorTabla));

        colaVacia.setFont(Estilos.contenido());
        colaVacia.setForeground(Estilos.SECUNDARIO);
        tarjeta.add(Estilos.alineado(colaVacia));
        return tarjeta;
    }

    private JPanel crearTarjetaTurno() {
        JPanel tarjeta = Estilos.tarjeta(new JPanel());
        tarjeta.setLayout(new BoxLayout(tarjeta, BoxLayout.Y_AXIS));

        tituloTurno.setFont(Estilos.panel());
        tituloTurno.setForeground(Estilos.TEXTO_PRINCIPAL);
        tarjeta.add(Estilos.alineado(tituloTurno));
        tarjeta.add(Box.createVerticalStrut(16));

        cajaEstado.setLayout(new BoxLayout(cajaEstado, BoxLayout.Y_AXIS));
        estadoTurno.setFont(Estilos.etiqueta());
        detalleTurno.setFont(Estilos.detalle());
        detalleTurno.setForeground(Estilos.TEXTO_PRINCIPAL);
        cajaEstado.add(Estilos.alineado(estadoTurno));
        cajaEstado.add(Box.createVerticalStrut(6));
        cajaEstado.add(Estilos.alineado(detalleTurno));
        tarjeta.add(Estilos.alineado(cajaEstado));
        tarjeta.add(Box.createVerticalStrut(18));

        comprobaciones.setLayout(new BoxLayout(comprobaciones, BoxLayout.Y_AXIS));
        comprobaciones.setOpaque(false);
        JLabel antes = new JLabel("Antes de crear matrícula");
        antes.setFont(Estilos.seccion());
        antes.setForeground(Estilos.TEXTO_PRINCIPAL);
        comprobaciones.add(Estilos.alineado(antes));
        comprobaciones.add(Box.createVerticalStrut(10));
        for (JLabel item : new JLabel[] { checkDocumentos, checkEntrevista, checkCupo,
                checkConfirmacion }) {
            comprobaciones.add(Estilos.alineado(item));
            comprobaciones.add(Box.createVerticalStrut(8));
        }
        comprobaciones.add(Box.createVerticalStrut(10));
        comprobaciones.add(Estilos.alineado(
                Estilos.conEtiqueta("Personal que confirma", campoPersonal)));
        comprobaciones.add(Box.createVerticalStrut(16));

        JPanel botones = new JPanel();
        botones.setLayout(new BoxLayout(botones, BoxLayout.Y_AXIS));
        botones.setOpaque(false);
        botonAceptada.addActionListener(evento -> ofertar(true));
        botonNoAceptada.addActionListener(evento -> ofertar(false));
        botones.add(Estilos.alineado(botonAceptada));
        botones.add(Box.createVerticalStrut(10));
        botones.add(Estilos.alineado(botonNoAceptada));
        comprobaciones.add(Estilos.alineado(botones));
        comprobaciones.add(Box.createVerticalStrut(14));
        comprobaciones.add(Estilos.alineado(texto("Si no acepta, no se crea matrícula y la"
                + " solicitud vuelve al final de la cola favorable. PENDIENTE_PAGO es el primer"
                + " momento en que se reserva vacante.", 300)));
        tarjeta.add(Estilos.alineado(comprobaciones));
        return tarjeta;
    }

    private void llenarAulas() {
        llenandoAulas = true;
        for (Aula aula : aulas.listar()) {
            comboAula.addItem(aula);
        }
        llenandoAulas = false;
    }

    private Aula aulaElegida() {
        return (Aula) comboAula.getSelectedItem();
    }

    private void actualizar() {
        revisarTurnos();
        refrescar();
    }

    // Vencimientos.revisar se llama aparte para mostrar sus avisos: revisarTurnos también lo
    // ejecuta, pero los descarta. Después habilita a las solicitudes sin pago con turno, así
    // la tabla muestra el mismo estado que verá confirmarOferta. Las habilitadas, de cualquier
    // aula, quedan para avisar: su plazo de 48 h ya corre.
    private void revisarTurnos() {
        LocalDateTime ahora = Reloj.porDefecto().now();
        ArrayList<String> avisos = new ArrayList<String>();
        try {
            avisos.addAll(Vencimientos.revisar(ahora, solicitudes, matriculas));
            for (Solicitud habilitada : Turnos.revisarTurnos(aulas, solicitudes, matriculas,
                    ahora)) {
                if (!porAvisar.contains(habilitada)) {
                    porAvisar.add(habilitada);
                }
            }
        } catch (ReglaDominioException e) {
            avisos.add(e.getMessage());
        }
        mostrarAvisoVencimientos(avisos);
    }

    // En el encabezado y no en un diálogo: el mismo aviso vuelve en cada revisión mientras los
    // datos no se corrijan, y se borra solo cuando la revisión ya no lo devuelve.
    private void mostrarAvisoVencimientos(ArrayList<String> avisos) {
        if (avisos.isEmpty()) {
            avisoVencimientos.setText(" ");
            return;
        }
        avisoVencimientos.setText("<html>Revisión de vencimientos incompleta:<br>"
                + Vencimientos.unir(avisos).replace("\n", "<br>") + "</html>");
    }

    private void refrescarAvisar() {
        ArrayList<Solicitud> vigentes = AvisoHabilitadas.vigentes(porAvisar);
        porAvisar.clear();
        porAvisar.addAll(vigentes);
        franjaAvisar.setVisible(!porAvisar.isEmpty());
        textoAvisar.setText("<html>" + AvisoHabilitadas.texto("Quedaron habilitadas para pago"
                + " al liberarse una vacante. Avisar a sus familias:", porAvisar, true)
                + "</html>");
        revalidate();
    }

    private void refrescar() {
        refrescarAvisar();
        Aula aula = aulaElegida();
        if (aula == null) {
            return;
        }
        Vacantes.Desglose cupo = Vacantes.desglose(aula, matriculas);
        int vacantes = cupo.getDisponibles();
        tituloAula.setText("Aula " + aula.getNombre() + " · " + aula.getEdadRequerida()
                + " años");
        desglose.setText("Capacidad " + cupo.getCapacidad() + "   −   PENDIENTE_PAGO "
                + cupo.getPendientes() + "   −   ACTIVA " + cupo.getActivas() + "   =   "
                + vacantes
                + (vacantes == 1 ? " disponible" : " disponibles"));
        cifraVacantes.setText(String.format("%02d", vacantes));

        ArrayList<Solicitud> orden = Turnos.ordenPorPrioridad(aula, solicitudes, matriculas);
        turnoActual = orden.isEmpty() ? null : orden.get(0);
        modeloOrden.mostrar(orden, vacantes);
        tituloOrden.setText("Orden de atención · " + aula.getNombre());
        contenedorTabla.setVisible(!orden.isEmpty());
        colaVacia.setVisible(orden.isEmpty());
        colaVacia.setText("No hay solicitudes en cola para " + aula.getNombre() + ".");

        refrescarTurno(vacantes);
        revalidate();
        repaint();
    }

    private void refrescarTurno(int vacantes) {
        comprobaciones.setVisible(turnoActual != null);
        if (turnoActual == null) {
            tituloTurno.setText("Turno actual");
            pintarEstado(Estilos.FONDO_CAMPO, Estilos.SECUNDARIO, "SIN TURNO",
                    "Nadie espera en esta aula.");
            return;
        }
        tituloTurno.setText("Turno actual · " + turnoActual.getAlumno().getNombres());
        boolean favorable = turnoActual.estaEnColaFavorable();
        pintarEstado(favorable ? Estilos.FAVORABLE : Estilos.ESPERA,
                favorable ? Estilos.BOTON_PRINCIPAL : Estilos.TEXTO_ESPERA,
                turnoActual.getEstado().name(), explicacion(turnoActual, vacantes));

        marcar(checkDocumentos, documentosValidados(turnoActual), "Cuatro documentos validados");
        marcar(checkEntrevista, entrevistaFavorable(turnoActual), "Entrevista FAVORABLE");
        // Sin matrícula vigente ya está garantizado: ordenPorPrioridad descarta a quien la tiene.
        marcar(checkCupo, vacantes > 0, "Recalcular cupo y matrícula vigente");
        marcar(checkConfirmacion, false, "Confirmación de oferta por personal");

        boolean ofertable = favorable && vacantes > 0;
        botonAceptada.setEnabled(ofertable);
        botonNoAceptada.setEnabled(ofertable);
    }

    private String explicacion(Solicitud solicitud, int vacantes) {
        EstadoSolicitud estado = solicitud.getEstado();
        if (estado == EstadoSolicitud.EN_ESPERA_FAVORABLE) {
            return vacantes > 0 ? "Inscripción pagada, entrevista favorable."
                    : "Sin vacantes: espera a que se libere un cupo.";
        }
        if (estado == EstadoSolicitud.EN_DOCUMENTACION) {
            return "Inscripción en curso: conserva el turno hasta obtener matrícula, rechazo o"
                    + " cancelación.";
        }
        if (estado == EstadoSolicitud.HABILITADA_PARA_PAGO) {
            return "Habilitada para pagar la inscripción hasta "
                    + FormatoFecha.diaHora(solicitud.getVencimientoHabilitacion()) + ".";
        }
        return vacantes > 0 ? "Espera sin pago." : "Espera sin pago: no hay vacantes.";
    }

    private static boolean documentosValidados(Solicitud solicitud) {
        return solicitud.getExpediente() != null
                && solicitud.getExpediente().todosValidados();
    }

    private boolean entrevistaFavorable(Solicitud solicitud) {
        Entrevista entrevista = entrevistas.buscarDe(solicitud);
        return entrevista != null && entrevista.getResultado() == ResultadoEntrevista.FAVORABLE;
    }

    private void ofertar(boolean aceptada) {
        Solicitud solicitud = turnoActual;
        if (solicitud == null) {
            return;
        }
        try {
            Turnos.confirmarOferta(solicitud, aceptada, campoPersonal.getText(),
                    Reloj.porDefecto().now(), solicitudes, matriculas);
            if (aceptada) {
                Matricula matricula = matriculas.vigenteDe(solicitud);
                mostrarMensaje("Matrícula " + matricula.getCodigo() + " creada en PENDIENTE_PAGO"
                        + " para " + solicitud.getAlumno().getNombreCompleto()
                        + ": la vacante queda reservada.");
            } else {
                mostrarMensaje(solicitud.getCodigo() + " no aceptó la oferta: vuelve al final de"
                        + " la cola favorable.");
            }
        } catch (ReglaDominioException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "No se registró la oferta",
                    JOptionPane.ERROR_MESSAGE);
        }
        actualizar();
    }

    private void pintarEstado(Color fondo, Color frente, String estado, String detalle) {
        Estilos.aviso(cajaEstado, fondo);
        estadoTurno.setForeground(frente);
        estadoTurno.setText(estado);
        detalleTurno.setText("<html><div style='width:280px'>" + detalle + "</div></html>");
    }

    private static void marcar(JLabel item, boolean cumplido, String texto) {
        item.setText((cumplido ? HECHO : PENDIENTE) + texto);
        item.setForeground(cumplido ? Estilos.BOTON_PRINCIPAL : Estilos.SECUNDARIO);
    }

    private void mostrarMensaje(String texto) {
        mensaje.setText(texto);
    }

    private static JLabel check() {
        JLabel item = new JLabel(" ");
        item.setFont(Estilos.contenido());
        return item;
    }

    private static JLabel texto(String texto, int ancho) {
        JLabel etiqueta = new JLabel("<html><div style='width:" + ancho + "px'>" + texto
                + "</div></html>");
        etiqueta.setFont(Estilos.contenido());
        etiqueta.setForeground(Estilos.SECUNDARIO);
        return etiqueta;
    }

    private static String grupo(EstadoSolicitud estado) {
        if (estado == EstadoSolicitud.EN_ESPERA_FAVORABLE) {
            return "Favorable";
        }
        if (estado == EstadoSolicitud.EN_DOCUMENTACION) {
            return "En documentación";
        }
        if (estado == EstadoSolicitud.HABILITADA_PARA_PAGO) {
            return "Habilitada";
        }
        return "Sin pago";
    }

    private static final class ModeloOrden extends AbstractTableModel {

        private static final String[] COLUMNAS = { "Turno", "Solicitud", "Ingreso cola",
                "Acción" };

        private ArrayList<Solicitud> orden = new ArrayList<Solicitud>();
        private int vacantes;

        void mostrar(ArrayList<Solicitud> nuevo, int vacantesAula) {
            orden = nuevo;
            vacantes = vacantesAula;
            fireTableDataChanged();
        }

        Solicitud solicitud(int fila) {
            return orden.get(fila);
        }

        @Override
        public int getRowCount() {
            return orden.size();
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
            Solicitud solicitud = orden.get(fila);
            if (columna == 0) {
                return (fila + 1) + " · " + grupo(solicitud.getEstado());
            }
            if (columna == 1) {
                return solicitud.getAlumno().getNombreCompleto() + " · " + solicitud.getCodigo();
            }
            if (columna == 2) {
                // Habilitadas y en documentación ya salieron de la cola: no tienen fecha de ingreso.
                return solicitud.getFechaIngresoCola() == null ? SIN_DATO
                        : FormatoFecha.diaHora(solicitud.getFechaIngresoCola());
            }
            boolean ofertable = fila == 0 && solicitud.estaEnColaFavorable() && vacantes > 0;
            return ofertable ? "Ofertar →" : "Esperar";
        }
    }

    private final class RenderOrden extends DefaultTableCellRenderer {
        private final Font fuenteDato = Estilos.dato();
        private final Font fuenteDestacada = Estilos.datoDestacado();

        @Override
        public Component getTableCellRendererComponent(JTable tabla, Object valor,
                boolean seleccionado, boolean conFoco, int fila, int columna) {
            super.getTableCellRendererComponent(tabla, valor, false, false, fila, columna);
            Solicitud solicitud = modeloOrden.solicitud(fila);
            boolean favorable = solicitud.estaEnColaFavorable();
            boolean ofertar = columna == 3 && "Ofertar →".equals(valor);
            setBorder(Estilos.MARGEN_CELDA);
            setFont(columna == 0 || ofertar ? fuenteDestacada : fuenteDato);
            if (columna == 0) {
                setForeground(favorable ? Estilos.BOTON_PRINCIPAL : Estilos.TEXTO_ESPERA);
            } else if (columna == 2 || (columna == 3 && !ofertar)) {
                setForeground(Estilos.SECUNDARIO);
            } else if (ofertar) {
                setForeground(Estilos.BOTON_PRINCIPAL);
            } else {
                setForeground(Estilos.TEXTO_PRINCIPAL);
            }
            setBackground(fila == 0 ? Estilos.ESPERA : Estilos.TARJETA);
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
