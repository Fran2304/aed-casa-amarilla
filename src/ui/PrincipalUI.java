package ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
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

import datos.ArregloAulas;
import datos.ArregloSolicitudes;

public class PrincipalUI extends JFrame {

    public static final String INICIO = "INICIO";
    public static final String SOLICITUDES = "SOLICITUDES";
    public static final String COLAS = "COLAS";
    public static final String DOCUMENTOS = "DOCUMENTOS";
    public static final String ENTREVISTAS = "ENTREVISTAS";
    public static final String PAGOS = "PAGOS";
    public static final String HISTORIAL = "HISTORIAL";

    private final CardLayout tarjetas = new CardLayout();
    private final JPanel panelCentral = new JPanel(tarjetas);
    private final List<BotonMenu> botonesMenu = new ArrayList<>();
    // Una sola lista de aulas para toda la app: los paneles la piden con getAulas()
    // en vez de crear la suya y quedar desincronizados.
    private final ArregloAulas aulas = new ArregloAulas();
    private final ArregloSolicitudes solicitudes = new ArregloSolicitudes();

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

    public ArregloAulas getAulas() {
        return aulas;
    }

    public ArregloSolicitudes getSolicitudes() {
        return solicitudes;
    }

    private JPanel crearCabecera() {
        JPanel cabecera = new JPanel(new BorderLayout());
        cabecera.setPreferredSize(new Dimension(0, 68));
        cabecera.setBackground(Estilos.TEXTO_PRINCIPAL);
        cabecera.setBorder(BorderFactory.createEmptyBorder(0, 32, 0, 40));

        JLabel titulo = new JLabel("La Casa Amarilla   /   Matrícula 2027");
        titulo.setFont(Estilos.fuente(Font.BOLD, 19));
        titulo.setForeground(Estilos.BLANCO);
        JLabel sede = new JLabel("Sede San Borja · Personal");
        sede.setFont(Estilos.contenido());
        sede.setForeground(Estilos.ESPERA);

        cabecera.add(titulo, BorderLayout.WEST);
        cabecera.add(sede, BorderLayout.EAST);
        return cabecera;
    }

    private JPanel crearLateral() {
        JPanel lateral = new JPanel();
        lateral.setLayout(new BoxLayout(lateral, BoxLayout.Y_AXIS));
        lateral.setPreferredSize(new Dimension(220, 0));
        lateral.setBackground(Estilos.FONDO_LATERAL);

        JLabel seccion = new JLabel("OPERACIÓN 2027");
        seccion.setFont(Estilos.fuente(Font.BOLD, 12));
        seccion.setForeground(Estilos.SECUNDARIO);
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

    private void agregarPanel(JPanel panel, String nombrePanel) {
        panel.setBackground(Estilos.FONDO_CREMA);
        panelCentral.add(panel, nombrePanel);
    }
}
