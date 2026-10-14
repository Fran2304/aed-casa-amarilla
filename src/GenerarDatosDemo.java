import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.imageio.ImageIO;

import datos.Almacen;
import modelo.Alumno;
import modelo.Apoderado;
import modelo.Documento;
import modelo.Entrevista;
import modelo.Matricula;
import modelo.MedioPago;
import modelo.ReglaDominioException;
import modelo.ResultadoEntrevista;
import modelo.Solicitud;
import modelo.TipoDocumento;
import negocio.Cobros;
import negocio.Turnos;
import util.Reloj;

/** Artefacto opcional del issue #82: nunca se invoca al arrancar Main. */
public final class GenerarDatosDemo {

    private GenerarDatosDemo() {
    }

    public static void main(String[] args) throws IOException, ReglaDominioException {
        if (args.length != 0) {
            throw new IllegalArgumentException("Uso: java -cp bin GenerarDatosDemo");
        }
        Path carpeta = generar(Path.of("datos", "demo-profesor"), Reloj.porDefecto().now());
        System.out.println("Demo generada: " + carpeta);
        System.out.println("Abrir: java -Dcasaamarilla.datos=\"" + carpeta + "\" -cp bin Main");
    }

    /**
     * Cada llamada crea una carpeta exclusiva. El padre permite aislar las pruebas.
     * La hora histórica se usa solo al registrar solicitudes y se restablece antes de salir;
     * Main sigue usando el reloj real, sin congelarlo ni reiniciar plazos.
     */
    public static Path generar(Path padre, LocalDateTime ahora)
            throws IOException, ReglaDominioException {
        if (padre == null || ahora == null) {
            throw new IllegalArgumentException("El directorio y la fecha son obligatorios.");
        }
        Files.createDirectories(padre);
        Path carpeta = Files.createTempDirectory(padre.toAbsolutePath(), "demo-");
        Almacen almacen = new Almacen(carpeta.toFile());
        almacen.getCuotas().setCuotas(180, 850);
        Files.createDirectory(carpeta.resolve("comprobantes"));
        // Primero se completan los éxitos y la reserva, luego documentación y habilitación.
        // Así todas las operaciones recorren las validaciones reales de turno y vacante.
        for (int caso : new int[] {4, 5, 3, 2, 1}) {
            crearCaso(almacen, carpeta, ahora, caso);
        }
        almacen.grabar();
        return carpeta;
    }

    private static void crearCaso(Almacen almacen, Path carpeta, LocalDateTime ahora, int caso)
            throws IOException, ReglaDominioException {
        // Los cinco historiales caben en seis horas, con instantes crecientes entre casos.
        int orden = switch (caso) { case 4 -> 0; case 5 -> 1; case 3 -> 2;
            case 2 -> 3; default -> 4; };
        LocalDateTime registro = ahora.minusHours(6).plusHours(orden);
        Solicitud solicitud;
        Reloj.porDefecto().fijar(registro);
        try {
            Apoderado apoderado = almacen.getApoderados().registrar("8800000" + caso,
                    "Apoderado DEMO " + caso, "Familia Ficticia " + caso, "99900000" + caso);
            Alumno alumno = almacen.getAlumnos().registrar("7700000" + caso,
                    "Alumno DEMO " + caso, "Familia Ficticia " + caso,
                    LocalDate.of(2023, 6, 10 + caso), null);
            alumno.agregarApoderado(apoderado, true);
            solicitud = almacen.getSolicitudes().registrar(alumno,
                    almacen.getAulas().buscar("AUL-02"));
        } finally {
            Reloj.porDefecto().restablecer();
        }
        Turnos.ubicarNueva(solicitud, almacen.getSolicitudes(), almacen.getMatriculas(), registro);
        if (caso == 1) {
            return;
        }
        LocalDateTime inscripcion = registro.plusMinutes(10);
        Cobros.confirmarInscripcion(solicitud, 180, MedioPago.YAPE, "DEMO-INS-" + caso,
                inscripcion, comprobante(carpeta, caso, "inscripcion"), inscripcion,
                almacen.getSolicitudes(), almacen.getMatriculas(), almacen.getPagos(),
                almacen.getCuotas());
        for (Documento documento : solicitud.getExpediente().getDocumentos()) {
            documento.registrarEntrega(registro.plusMinutes(20));
            if (caso == 2 && documento.getTipo() == TipoDocumento.CARNE_VACUNAS) {
                documento.observar("DEMO: imagen ilegible; entregar una copia clara.",
                        registro.plusMinutes(25));
            } else {
                documento.validar();
            }
        }
        if (caso == 2) {
            return;
        }
        Entrevista entrevista = almacen.getEntrevistas().agendar(solicitud,
                solicitud.getExpediente(), registro.plusMinutes(30),
                new ArrayList<String>(List.of("Personal DEMO")), "Personal DEMO");
        entrevista.registrarAsistencia();
        entrevista.marcarRealizada();
        entrevista.registrarResultado(ResultadoEntrevista.FAVORABLE, registro.plusMinutes(35));
        Turnos.confirmarOferta(solicitud, true, "Personal DEMO", registro.plusMinutes(40),
                almacen.getSolicitudes(), almacen.getMatriculas());
        if (caso == 3) {
            return;
        }
        Matricula matricula = almacen.getMatriculas().vigenteDe(solicitud);
        LocalDateTime pagoMatricula = registro.plusMinutes(50);
        Cobros.confirmarMatricula(matricula, 850, MedioPago.TRANSFERENCIA, "DEMO-MAT-" + caso,
                pagoMatricula, comprobante(carpeta, caso, "matricula"), pagoMatricula,
                almacen.getSolicitudes(), almacen.getMatriculas(), almacen.getPagos(),
                almacen.getCuotas());
    }

    private static String comprobante(Path carpeta, int caso, String concepto) throws IOException {
        Path ruta = carpeta.resolve("comprobantes").resolve("caso-" + caso + "-" + concepto + ".png");
        BufferedImage imagen = new BufferedImage(640, 180, BufferedImage.TYPE_INT_RGB);
        Graphics2D grafico = imagen.createGraphics();
        try {
            grafico.setColor(Color.WHITE);
            grafico.fillRect(0, 0, imagen.getWidth(), imagen.getHeight());
            grafico.setColor(Color.RED);
            grafico.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
            grafico.drawString("DEMO — SIN VALOR", 30, 60);
            grafico.setColor(Color.BLACK);
            grafico.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 20));
            grafico.drawString("Caso " + caso + " · " + concepto + " · operación ficticia", 30, 110);
        } finally {
            grafico.dispose();
        }
        if (!ImageIO.write(imagen, "png", ruta.toFile())) {
            throw new IOException("No se pudo escribir el comprobante PNG " + ruta);
        }
        return ruta.toString();
    }
}
