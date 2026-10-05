package ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.AbstractBorder;

import modelo.EstadoSolicitud;

public final class Estilos {

    public static final Color FONDO_CREMA = new Color(0xFAF7EF);
    public static final Color TARJETA = Color.WHITE;
    public static final Color BLANCO = Color.WHITE;
    public static final Color TEXTO_PRINCIPAL = new Color(0x263B35);
    public static final Color BOTON_PRINCIPAL = new Color(0x2D6654);
    public static final Color ESPERA = new Color(0xFFF0BE);
    public static final Color TEXTO_ESPERA = new Color(0x735619);
    public static final Color FAVORABLE = new Color(0xE7F2EA);
    public static final Color ALERTA = new Color(0xFBEDE8);
    public static final Color TEXTO_ALERTA = new Color(0x9A352C);
    public static final Color SECUNDARIO = new Color(0x53635C);
    public static final Color FONDO_LATERAL = new Color(0xE7EDE5);
    public static final Color BORDE_TARJETA = new Color(0xD9E1DA);
    public static final Color FONDO_CAMPO = new Color(0xF5F7F2);

    public static final int RADIO_TARJETA = 12;
    public static final int RADIO_BOTON = 8;
    public static final int RADIO_CAMPO = 6;

    private static final String INTER = "Inter";

    private Estilos() {
    }

    public static Font titulo() {
        return fuente(Font.BOLD, 28);
    }

    public static Font panel() {
        return fuente(Font.BOLD, 20);
    }

    public static Font contenido() {
        return fuente(Font.PLAIN, 14);
    }

    public static Font cifra() {
        return fuente(Font.BOLD, 40);
    }

    public static Font fuente(int estilo, int tamano) {
        String familia = tieneInter() ? INTER : Font.SANS_SERIF;
        return new Font(familia, estilo, tamano);
    }

    public static JButton botonPrimario(String texto) {
        JButton boton = new JButton(texto);
        boton.setFont(fuente(Font.BOLD, 14));
        boton.setForeground(BLANCO);
        boton.setBackground(BOTON_PRINCIPAL);
        boton.setContentAreaFilled(false);
        boton.setOpaque(true);
        boton.setBorder(new BordeRedondeado(BOTON_PRINCIPAL, RADIO_BOTON));
        boton.setFocusPainted(false);
        boton.setMargin(new Insets(10, 16, 10, 16));
        return boton;
    }

    public static JButton botonSecundario(String texto) {
        JButton boton = new JButton(texto);
        boton.setFont(fuente(Font.BOLD, 14));
        boton.setForeground(TEXTO_PRINCIPAL);
        boton.setBackground(TARJETA);
        boton.setContentAreaFilled(false);
        boton.setOpaque(true);
        boton.setBorder(new BordeRedondeado(SECUNDARIO, RADIO_BOTON));
        boton.setFocusPainted(false);
        boton.setMargin(new Insets(10, 16, 10, 16));
        return boton;
    }

    public static JPanel tarjeta(JPanel panel) {
        panel.setBackground(TARJETA);
        panel.setBorder(new BordeRedondeado(BORDE_TARJETA, RADIO_TARJETA));
        return panel;
    }

    public static Font tituloTarjeta() {
        return fuente(Font.BOLD, 19);
    }

    // Etiqueta de campo del diseño: mayúsculas, gris y encima del campo, no a su izquierda.
    public static JLabel etiquetaCampo(String texto) {
        JLabel etiqueta = new JLabel(texto.toUpperCase());
        etiqueta.setFont(fuente(Font.BOLD, 13));
        etiqueta.setForeground(SECUNDARIO);
        return etiqueta;
    }

    public static <T extends JComponent> T campo(T campo) {
        campo.setFont(contenido());
        campo.setForeground(TEXTO_PRINCIPAL);
        campo.setBackground(FONDO_CAMPO);
        campo.setBorder(BorderFactory.createCompoundBorder(
                new BordeRedondeado(FONDO_CAMPO, RADIO_CAMPO),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        return campo;
    }

    // Sin el fondo de campo, uno de solo lectura se distingue a simple vista de uno editable.
    public static void editable(JTextField campo, boolean editable) {
        campo.setEditable(editable);
        campo.setBackground(editable ? FONDO_CAMPO : TARJETA);
        campo.setForeground(editable ? TEXTO_PRINCIPAL : SECUNDARIO);
        campo.setCursor(Cursor.getPredefinedCursor(
                editable ? Cursor.TEXT_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    // Caja de aviso (alerta, espera o confirmado): el color cambia con el estado del pago.
    public static JPanel aviso(JPanel panel, Color fondo) {
        panel.setBackground(fondo);
        panel.setBorder(new BordeRedondeado(fondo, RADIO_BOTON));
        return panel;
    }

    public static JLabel chip(EstadoSolicitud estado) {
        JLabel chip = new JLabel(textoEstado(estado));
        chip.setFont(fuente(Font.BOLD, 13));
        chip.setOpaque(true);
        chip.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        if (estado == EstadoSolicitud.EN_ESPERA_FAVORABLE) {
            chip.setBackground(FAVORABLE);
            chip.setForeground(TEXTO_PRINCIPAL);
        } else if (estado == EstadoSolicitud.CANCELADA || estado == EstadoSolicitud.RECHAZADA) {
            chip.setBackground(ALERTA);
            chip.setForeground(TEXTO_ALERTA);
        } else {
            chip.setBackground(ESPERA);
            chip.setForeground(TEXTO_ESPERA);
        }
        return chip;
    }

    public static JLabel chip(String texto) {
        JLabel chip = new JLabel(texto);
        chip.setFont(fuente(Font.BOLD, 13));
        chip.setOpaque(true);
        chip.setBackground(ESPERA);
        chip.setForeground(TEXTO_ESPERA);
        chip.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
        return chip;
    }

    private static String textoEstado(EstadoSolicitud estado) {
        return estado.name().replace('_', ' ');
    }

    private static boolean tieneInter() {
        String[] familias = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        for (String familia : familias) {
            if (INTER.equalsIgnoreCase(familia)) {
                return true;
            }
        }
        return false;
    }

    private static final class BordeRedondeado extends AbstractBorder {
        private final Color color;
        private final int radio;

        BordeRedondeado(Color color, int radio) {
            this.color = color;
            this.radio = radio;
        }

        @Override
        public void paintBorder(java.awt.Component componente, java.awt.Graphics grafico,
                int x, int y, int ancho, int alto) {
            grafico.setColor(color);
            grafico.drawRoundRect(x, y, ancho - 1, alto - 1, radio * 2, radio * 2);
        }

        @Override
        public Insets getBorderInsets(java.awt.Component componente) {
            return new Insets(radio, radio, radio, radio);
        }

        @Override
        public boolean isBorderOpaque() {
            return false;
        }
    }
}
