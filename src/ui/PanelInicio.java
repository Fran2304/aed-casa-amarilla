package ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class PanelInicio extends JPanel {

    private static final Color TEXTO = new Color(0x263B35);
    private static final Color FONDO_BOTON = new Color(0x2D6654);

    public PanelInicio(PrincipalUI principal) {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(32, 32, 32, 34));

        JLabel titulo = new JLabel("Centro de operaciones");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 28f));
        titulo.setForeground(TEXTO);

        JButton botonNuevaSolicitud = new JButton("+ Nueva solicitud");
        botonNuevaSolicitud.setFont(botonNuevaSolicitud.getFont().deriveFont(Font.BOLD, 14f));
        botonNuevaSolicitud.setForeground(Color.WHITE);
        botonNuevaSolicitud.setBackground(FONDO_BOTON);
        // Sin estas dos líneas Swing pinta su botón gris con degradado e ignora el color.
        botonNuevaSolicitud.setContentAreaFilled(false);
        botonNuevaSolicitud.setOpaque(true);
        botonNuevaSolicitud.setBorderPainted(false);
        botonNuevaSolicitud.setFocusPainted(false);
        botonNuevaSolicitud.setPreferredSize(new Dimension(176, 42));
        botonNuevaSolicitud.addActionListener(evento -> principal.mostrar(PrincipalUI.SOLICITUDES));

        JPanel encabezado = new JPanel(new BorderLayout());
        // Transparente para que se vea el fondo del panel de contenido.
        encabezado.setOpaque(false);
        encabezado.add(titulo, BorderLayout.WEST);
        encabezado.add(botonNuevaSolicitud, BorderLayout.EAST);

        add(encabezado, BorderLayout.NORTH);
    }
}
