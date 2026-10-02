package ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import modelo.ConfiguracionCuotas;
import modelo.DatoInvalidoException;

// No está en el diseño: sigue la paleta de Estilos (issue #11).
public class PanelCuotas extends JPanel {

    private final ConfiguracionCuotas cuotas;
    private final JTextField campoInscripcion = new JTextField(10);
    private final JTextField campoMatricula = new JTextField(10);
    private final JLabel mensaje = new JLabel(" ");

    public PanelCuotas(ConfiguracionCuotas cuotas) {
        this.cuotas = cuotas;
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(32, 32, 32, 34));

        JLabel titulo = new JLabel("Cuotas 2027");
        titulo.setFont(Estilos.titulo());
        titulo.setForeground(Estilos.TEXTO_PRINCIPAL);
        add(titulo, BorderLayout.NORTH);

        JPanel contenedor = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 24));
        // Transparente para que se vea el fondo del panel de contenido.
        contenedor.setOpaque(false);
        contenedor.add(crearTarjeta());
        add(contenedor, BorderLayout.CENTER);

        mostrarCuotasActuales();
    }

    private JPanel crearTarjeta() {
        JPanel tarjeta = Estilos.tarjeta(new JPanel(new GridBagLayout()));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.anchor = GridBagConstraints.WEST;

        c.gridy = 0;
        tarjeta.add(etiqueta("Cuota de inscripción (S/)"), c);
        tarjeta.add(campoInscripcion, c);

        c.gridy = 1;
        tarjeta.add(etiqueta("Cuota de matrícula (S/)"), c);
        tarjeta.add(campoMatricula, c);

        JLabel nota = etiqueta("Los pagos ya registrados conservan el monto con el que se registraron.");
        nota.setForeground(Estilos.SECUNDARIO);
        c.gridy = 2;
        c.gridwidth = 2;
        tarjeta.add(nota, c);

        JButton guardar = Estilos.botonPrimario("Guardar");
        guardar.addActionListener(evento -> guardar());
        c.gridy = 3;
        tarjeta.add(guardar, c);

        mensaje.setFont(Estilos.contenido());
        c.gridy = 4;
        tarjeta.add(mensaje, c);
        return tarjeta;
    }

    private JLabel etiqueta(String texto) {
        JLabel etiqueta = new JLabel(texto);
        etiqueta.setFont(Estilos.contenido());
        etiqueta.setForeground(Estilos.TEXTO_PRINCIPAL);
        return etiqueta;
    }

    private void guardar() {
        try {
            double inscripcion = leerMonto(campoInscripcion, "cuota de inscripción");
            double matricula = leerMonto(campoMatricula, "cuota de matrícula");
            cuotas.setCuotas(inscripcion, matricula);
        } catch (DatoInvalidoException e) {
            mostrarMensaje(e.getMessage(), true);
            return;
        }
        mostrarCuotasActuales();
        mostrarMensaje("Cuotas guardadas.", false);
    }

    private double leerMonto(JTextField campo, String nombre) throws DatoInvalidoException {
        String texto = campo.getText().trim();
        // Solo dígitos y hasta 2 decimales con punto o coma. Así "1,200" (coma de miles) no se
        // convierte en 1.20 sin avisar, el monto guardado es el que se muestra, y se descartan
        // textos que parseDouble sí acepta, como "NaN", "Infinity" o "1e3".
        if (!texto.matches("[0-9]+([.,][0-9]{1,2})?")) {
            throw new DatoInvalidoException("La " + nombre + " debe ser un monto como 1200 o 180.50,"
                    + " sin separador de miles.");
        }
        return Double.parseDouble(texto.replace(',', '.'));
    }

    // Locale.ROOT fuerza el punto decimal: con la configuración regional de Windows podría
    // salir "180,00", y Double.parseDouble no lo aceptaría al volver a guardar.
    private void mostrarCuotasActuales() {
        campoInscripcion.setText(String.format(Locale.ROOT, "%.2f", cuotas.getCuotaInscripcion()));
        campoMatricula.setText(String.format(Locale.ROOT, "%.2f", cuotas.getCuotaMatricula()));
    }

    private void mostrarMensaje(String texto, boolean esError) {
        mensaje.setText(texto);
        mensaje.setForeground(esError ? Estilos.TEXTO_ALERTA : Estilos.BOTON_PRINCIPAL);
    }
}
