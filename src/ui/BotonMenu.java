package ui;

import java.awt.Dimension;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.SwingConstants;

public class BotonMenu extends JButton {

    private final String nombrePanel;

    public BotonMenu(String texto, String nombrePanel) {
        super(texto);
        this.nombrePanel = nombrePanel;

        setHorizontalAlignment(SwingConstants.LEFT);
        setBorder(BorderFactory.createEmptyBorder(0, 26, 0, 12));
        setForeground(Estilos.TEXTO_PRINCIPAL);
        setFocusPainted(false);
        setContentAreaFilled(false);
        setOpaque(true);
        // Sin esto, BoxLayout deja cada botón del ancho de su texto en vez de todo el menú.
        setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        setSeleccionado(false);
    }

    public String getNombrePanel() {
        return nombrePanel;
    }

    public void setSeleccionado(boolean seleccionado) {
        if (seleccionado) {
            setBackground(Estilos.ESPERA);
            setFont(Estilos.fuente(Font.BOLD, 15));
        } else {
            setBackground(Estilos.FONDO_LATERAL);
            setFont(Estilos.fuente(Font.PLAIN, 15));
        }
    }
}
