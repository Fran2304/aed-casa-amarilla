package ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.SwingConstants;

public class BotonMenu extends JButton {

    private static final Color FONDO_NORMAL = new Color(0xE7EDE5);
    private static final Color FONDO_SELECCIONADO = new Color(0xFFF0BE);
    private static final Color TEXTO = new Color(0x263B35);

    private final String nombrePanel;

    public BotonMenu(String texto, String nombrePanel) {
        super(texto);
        this.nombrePanel = nombrePanel;

        setHorizontalAlignment(SwingConstants.LEFT);
        setBorder(BorderFactory.createEmptyBorder(0, 26, 0, 12));
        setForeground(TEXTO);
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
            setBackground(FONDO_SELECCIONADO);
            setFont(getFont().deriveFont(Font.BOLD, 15f));
        } else {
            setBackground(FONDO_NORMAL);
            setFont(getFont().deriveFont(Font.PLAIN, 15f));
        }
    }
}
