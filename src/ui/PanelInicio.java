package ui;

import java.awt.BorderLayout;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class PanelInicio extends JPanel {

    public PanelInicio(PrincipalUI principal) {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(32, 32, 32, 34));

        JLabel titulo = new JLabel("Centro de operaciones");
        titulo.setFont(Estilos.titulo());
        titulo.setForeground(Estilos.TEXTO_PRINCIPAL);

        JButton botonNuevaSolicitud = Estilos.botonPrimario("+ Nueva solicitud");
        botonNuevaSolicitud.addActionListener(evento -> principal.mostrar(PrincipalUI.SOLICITUDES));

        JPanel encabezado = new JPanel(new BorderLayout());
        // Transparente para que se vea el fondo del panel de contenido.
        encabezado.setOpaque(false);
        encabezado.add(titulo, BorderLayout.WEST);
        encabezado.add(botonNuevaSolicitud, BorderLayout.EAST);

        add(encabezado, BorderLayout.NORTH);
    }
}
