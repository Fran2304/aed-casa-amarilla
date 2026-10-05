import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

import datos.ArregloAlumnos;
import datos.ArregloApoderados;
import modelo.Alumno;
import modelo.Aula;
import modelo.MedioPago;
import modelo.ReglaDominioException;
import modelo.Solicitud;
import negocio.Cobros;
import ui.PrincipalUI;

// Prueba manual del pago de inscripción (issue #15): abre la app con solicitudes en cada
// caso que la pantalla debe cubrir. No toca Main: la app real sigue arrancando vacía.
public class PruebaPagos {

    private static int siguienteDni = 70000001;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            PrincipalUI ventana = new PrincipalUI();
            try {
                cargarCasos(ventana);
            } catch (ReglaDominioException e) {
                throw new IllegalStateException("No se pudieron cargar los casos: "
                        + e.getMessage(), e);
            }
            ventana.mostrar(PrincipalUI.PAGOS);
            ventana.setVisible(true);
        });
    }

    private static void cargarCasos(PrincipalUI ventana) throws ReglaDominioException {
        ArregloAlumnos alumnos = new ArregloAlumnos();
        ArregloApoderados apoderados = new ArregloApoderados();
        Aula girasoles = ventana.getAulas().buscar("AUL-01");
        Aula tulipanes = ventana.getAulas().buscar("AUL-02");
        LocalDate cuatroAnios = LocalDate.of(2022, 6, 18);
        LocalDate tresAnios = LocalDate.of(2023, 5, 10);
        LocalDateTime ahora = LocalDateTime.now();

        Solicitud vigente = registrar(ventana, alumnos, apoderados, "Mía", "León",
                cuatroAnios, girasoles);
        vigente.habilitarParaPago(ahora.minusHours(2));

        Solicitud otraVigente = registrar(ventana, alumnos, apoderados, "Noa", "Ruiz",
                tresAnios, tulipanes);
        otraVigente.habilitarParaPago(ahora.minusHours(47));

        Solicitud enCola = registrar(ventana, alumnos, apoderados, "Eva", "Díaz",
                tresAnios, tulipanes);
        enCola.ingresarAColaSinPago(ahora);

        Solicitud pagada = registrar(ventana, alumnos, apoderados, "Lucía", "Vega",
                cuatroAnios, girasoles);
        pagada.habilitarParaPago(ahora.minusHours(1));
        String comprobante = crearComprobanteDeEjemplo();
        Cobros.confirmarInscripcion(pagada, ventana.getCuotas().getCuotaInscripcion(),
                MedioPago.YAPE, "YP-8291457", LocalDateTime.now(), comprobante,
                LocalDateTime.now(), ventana.getSolicitudes(), ventana.getMatriculas(),
                ventana.getPagos(), ventana.getCuotas());

        // Después del pago: confirmarlo revisa turnos y vencería esta habilitación al instante.
        Solicitud vencida = registrar(ventana, alumnos, apoderados, "Leo", "Silva",
                cuatroAnios, girasoles);
        vencida.habilitarParaPago(ahora.minusHours(50));

        System.out.println("Casos de prueba de Pagos:");
        System.out.println("  " + vigente.getCodigo() + "  habilitada hace 2 h (pagar)");
        System.out.println("  " + otraVigente.getCodigo() + "  habilitada hace 47 h (vence en 1 h)");
        System.out.println("  " + vencida.getCodigo() + "  habilitación vencida");
        System.out.println("  " + enCola.getCodigo() + "  en cola sin pago (no habilitada)");
        System.out.println("  " + pagada.getCodigo() + "  inscripción ya pagada (solo lectura)");
        System.out.println("  SOL-9999  no existe");
        System.out.println("Comprobante de ejemplo para adjuntar: " + comprobante);
        System.out.println("Las solicitudes se registran al abrir: la fecha de operación (con minutos)"
                + " recién es válida desde el minuto siguiente.");
    }

    // Una imagen temporal que hace de captura de Yape, para probar «Adjuntar» y «Abrir».
    private static String crearComprobanteDeEjemplo() {
        try {
            File archivo = File.createTempFile("comprobante-yape-", ".png");
            archivo.deleteOnExit();
            BufferedImage imagen = new BufferedImage(360, 200, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = imagen.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 360, 200);
            g.setColor(Color.DARK_GRAY);
            g.drawString("Yape · S/ 180.00", 30, 70);
            g.drawString("Operación YP-8291457", 30, 100);
            g.dispose();
            ImageIO.write(imagen, "png", archivo);
            return archivo.getAbsolutePath();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo crear el comprobante de ejemplo.", e);
        }
    }

    private static Solicitud registrar(PrincipalUI ventana, ArregloAlumnos alumnos,
            ArregloApoderados apoderados, String nombres, String apellidos,
            LocalDate nacimiento, Aula aula) throws ReglaDominioException {
        Alumno alumno = alumnos.registrar(String.valueOf(siguienteDni++), nombres, apellidos,
                nacimiento, null);
        alumno.agregarApoderado(apoderados.registrar(String.valueOf(siguienteDni++),
                "Apoderado de", nombres, "999111222"), true);
        return ventana.getSolicitudes().registrar(alumno, aula);
    }
}
