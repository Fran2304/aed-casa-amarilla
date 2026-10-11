package ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;

import javax.swing.JPanel;
import javax.swing.Scrollable;

// Contenido de un JScrollPane que se estira al ancho visible y solo desplaza en vertical:
// así los textos largos se ajustan a la ventana en vez de abrir una barra horizontal.
final class PanelAncho extends JPanel implements Scrollable {

    PanelAncho() {
        super(new BorderLayout());
        setOpaque(false);
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientacion, int sentido) {
        return 16;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientacion, int sentido) {
        return visible.height;
    }

    @Override
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override
    public boolean getScrollableTracksViewportHeight() {
        return false;
    }
}
