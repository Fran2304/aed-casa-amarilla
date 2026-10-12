package ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import datos.ArregloAlumnos;
import datos.ArregloApoderados;
import datos.ArregloAulas;
import datos.ArregloEntrevistas;
import datos.ArregloMatriculas;
import datos.ArregloPagos;
import datos.ArregloSolicitudes;
import modelo.ConfiguracionCuotas;
import modelo.ReglaDominioException;
import negocio.Vencimientos;
import util.Reloj;

public class PrincipalUI extends JFrame {

    public static final String SOLICITUDES = "SOLICITUDES";
    public static final String COLAS = "COLAS";
    public static final String EXPEDIENTE = "EXPEDIENTE";
    public static final String PAGOS = "PAGOS";
    public static final String CUOTAS = "CUOTAS";

    private final CardLayout tarjetas = new CardLayout();
    private final JPanel panelCentral = new JPanel(tarjetas);
    private final List<BotonMenu> botonesMenu = new ArrayList<>();
    private final ArregloAulas aulas = new ArregloAulas();
    private final ArregloSolicitudes solicitudes = new ArregloSolicitudes();
    private final ArregloAlumnos alumnos = new ArregloAlumnos();
    private final ArregloApoderados apoderados = new ArregloApoderados();
    private final ConfiguracionCuotas cuotas = new ConfiguracionCuotas();
    private final ArregloPagos pagos = new ArregloPagos();
    private final ArregloEntrevistas entrevistas = new ArregloEntrevistas();
    private final ArregloMatriculas matriculas = new ArregloMatriculas();

    public PrincipalUI() {
        super("La Casa Amarilla · Matrícula 2027");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1280, 720));
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH);

        revisarVencimientos();

        setLayout(new BorderLayout());
        add(crearCabecera(), BorderLayout.NORTH);
        add(crearLateral(), BorderLayout.WEST);
        add(crearCentro(), BorderLayout.CENTER);

        try {
            Vencimientos.barrer(Reloj.porDefecto(), aulas, solicitudes, matriculas, pagos, cuotas);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudieron procesar los vencimientos al iniciar.", e);
        }
        mostrar(SOLICITUDES);
    }

    public void mostrar(String nombrePanel) {
        tarjetas.show(panelCentral, nombrePanel);
        for (BotonMenu boton : botonesMenu) {
            boolean esElActual = boton.getNombrePanel().equals(nombrePanel);
            boton.setSeleccionado(esElActual);
        }
    }

    // El cierre de la aplicación no suspende los plazos (§4.8): al abrir se revisan las
    // fechas guardadas, porque no hay un temporizador corriendo.
    public void revisarVencimientos() {
        String problema;
        try {
            ArrayList<String> avisos = Vencimientos.revisar(Reloj.porDefecto().now(), solicitudes,
                    matriculas);
            if (avisos.isEmpty()) {
                return;
            }
            problema = Vencimientos.unir(avisos);
        } catch (ReglaDominioException e) {
            problema = e.getMessage();
        }
        String mensaje = problema;
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, mensaje,
                "Revisión de vencimientos", JOptionPane.WARNING_MESSAGE));
    }

    public ArregloAulas getAulas() {
        return aulas;
    }

    public ArregloSolicitudes getSolicitudes() {
        return solicitudes;
    }

    public ArregloAlumnos getAlumnos() {
        return alumnos;
    }

    public ArregloApoderados getApoderados() {
        return apoderados;
    }

    public ConfiguracionCuotas getCuotas() {
        return cuotas;
    }

    public ArregloPagos getPagos() {
        return pagos;
    }

    public ArregloEntrevistas getEntrevistas() {
        return entrevistas;
    }

    public ArregloMatriculas getMatriculas() {
        return matriculas;
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

        agregarBotonMenu(lateral, "Solicitudes", SOLICITUDES);
        agregarBotonMenu(lateral, "Colas y vacantes", COLAS);
        agregarBotonMenu(lateral, "Expediente", EXPEDIENTE);
        agregarBotonMenu(lateral, "Pagos y matrículas", PAGOS);
        agregarBotonMenu(lateral, "Cuotas 2027", CUOTAS);

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
        agregarPanel(new PanelSolicitudes(aulas, solicitudes, alumnos, apoderados, matriculas),
                SOLICITUDES);
        agregarPanel(new PanelColas(aulas, solicitudes, entrevistas, matriculas), COLAS);
        agregarPanel(new PanelExpediente(solicitudes, entrevistas, matriculas), EXPEDIENTE);
        agregarPanel(new PanelPagos(solicitudes, pagos, matriculas, cuotas), PAGOS);
        agregarPanel(new PanelCuotas(cuotas), CUOTAS);
        return panelCentral;
    }

    private void agregarPanel(JPanel panel, String nombrePanel) {
        panel.setBackground(Estilos.FONDO_CREMA);
        panelCentral.add(panel, nombrePanel);
    }
}
