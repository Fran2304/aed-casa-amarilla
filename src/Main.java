import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import ui.PrincipalUI;

public class Main {

    public static void main(String[] args) {
        // Swing no es seguro entre hilos: toda la interfaz se crea en su hilo de eventos.
        SwingUtilities.invokeLater(() -> {
            try {
                // Sin esto, macOS usa Aqua, que ignora los bordes de Estilos.campo() y aplasta
                // los combos; Metal se ve igual en todos los sistemas, el mismo que ya sale en Windows.
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception e) {
                // si no se puede, se queda el look and feel del sistema
            }
            PrincipalUI ventana = new PrincipalUI();
            ventana.setVisible(true);
        });
    }
}
