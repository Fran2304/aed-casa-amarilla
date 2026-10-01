package ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class PrincipalUI extends JFrame {

    public static final String INICIO = "INICIO";
    public static final String SOLICITUDES = "SOLICITUDES";
    public static final String COLAS = "COLAS";
    public static final String DOCUMENTOS = "DOCUMENTOS";
    public static final String ENTREVISTAS = "ENTREVISTAS";
    public static final String PAGOS = "PAGOS";
    public static final String HISTORIAL = "HISTORIAL";

    // Colores del frame «01 · Panel de operaciones».
    private static final Color FONDO_CABECERA = new Color(0x263B35);
    private static final Color FONDO_LATERAL = new Color(0xE7EDE5);
    private static final Color FONDO_CONTENIDO = new Color(0xFAF7EF);
    private static final Color TEXTO_CLARO = Color.WHITE;
    private static final Color TEXTO_AMARILLO = new Color(0xFFF0BE);
    private static final Color TEXTO_SECUNDARIO = new Color(0x53635C);

    private final CardLayout tarjetas = new CardLayout();
    private final JPanel panelCentral = new JPanel(tarjetas);
    private final List<BotonMenu> botonesMenu = new ArrayList<>();

    public PrincipalUI() {
        super("La Casa Amarilla · Matrícula 2027");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        // El diseño es de 1440×900, pero muchas laptops del equipo son de 1366×768:
        // se abre maximizada y el panel central absorbe la diferencia.
        setMinimumSize(new Dimension(1280, 720));
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH);

        setLayout(new BorderLayout());
        add(crearCabecera(), BorderLayout.NORTH);
        add(crearLateral(), BorderLayout.WEST);
        add(crearCentro(), BorderLayout.CENTER);

        mostrar(INICIO);
    }

    public void mostrar(String nombrePanel) {
        tarjetas.show(panelCentral, nombrePanel);
        for (BotonMenu boton : botonesMenu) {
            boolean esElActual = boton.getNombrePanel().equals(nombrePanel);
            boton.setSeleccionado(esElActual);
        }
    }

    private JPanel crearCabecera() {
        JPanel cabecera = new JPanel(new BorderLayout());
        cabecera.setPreferredSize(new Dimension(0, 68));
        cabecera.setBackground(FONDO_CABECERA);
        cabecera.setBorder(BorderFactory.createEmptyBorder(0, 32, 0, 40));

        JLabel titulo = new JLabel("La Casa Amarilla   /   Matrícula 2027");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 19f));
        titulo.setForeground(TEXTO_CLARO);
        JLabel sede = new JLabel("Sede San Borja · Personal");
        sede.setFont(sede.getFont().deriveFont(Font.PLAIN, 14f));
        sede.setForeground(TEXTO_AMARILLO);

        cabecera.add(titulo, BorderLayout.WEST);
        cabecera.add(sede, BorderLayout.EAST);
        return cabecera;
    }

    private JPanel crearLateral() {
        JPanel lateral = new JPanel();
        lateral.setLayout(new BoxLayout(lateral, BoxLayout.Y_AXIS));
        lateral.setPreferredSize(new Dimension(220, 0));
        lateral.setBackground(FONDO_LATERAL);

        JLabel seccion = new JLabel("OPERACIÓN 2027");
        seccion.setFont(seccion.getFont().deriveFont(Font.BOLD, 12f));
        seccion.setForeground(TEXTO_SECUNDARIO);
        seccion.setBorder(BorderFactory.createEmptyBorder(30, 24, 16, 24));
        lateral.add(seccion);

        agregarBotonMenu(lateral, "Inicio", INICIO);
        agregarBotonMenu(lateral, "Solicitudes", SOLICITUDES);
        agregarBotonMenu(lateral, "Colas y vacantes", COLAS);
        agregarBotonMenu(lateral, "Documentos", DOCUMENTOS);
        agregarBotonMenu(lateral, "Entrevistas", ENTREVISTAS);
        agregarBotonMenu(lateral, "Pagos y matrículas", PAGOS);
        agregarBotonMenu(lateral, "Historial", HISTORIAL);

        lateral.add(Box.createVerticalGlue());
        return lateral;
    }

    private void agregarBotonMenu(JPanel lateral, String texto, String nombrePanel) {
        BotonMenu boton = new BotonMenu(texto, nombrePanel);
        boton.addActionListener(evento -> mostrar(nombrePanel));
        botonesMenu.add(boton);
        lateral.add(boton);
    }

    private JPanel crearCentro() {
        agregarPanel(new PanelInicio(this), INICIO);
        agregarPanel(new PanelSolicitudes(), SOLICITUDES);
        agregarPanel(new PanelColas(), COLAS);
        agregarPanel(new PanelDocumentos(), DOCUMENTOS);
        agregarPanel(new PanelEntrevistas(), ENTREVISTAS);
        agregarPanel(new PanelPagos(), PAGOS);
        agregarPanel(new PanelHistorial(), HISTORIAL);
        return panelCentral;
    }

    // El fondo se pone aquí para que ningún integrante tenga que repetirlo en su panel.
    private void agregarPanel(JPanel panel, String nombrePanel) {
        panel.setBackground(FONDO_CONTENIDO);
        panelCentral.add(panel, nombrePanel);
    }
}
