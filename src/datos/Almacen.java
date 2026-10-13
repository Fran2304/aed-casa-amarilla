package datos;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

import modelo.ConfiguracionCuotas;
import modelo.DatoInvalidoException;
import modelo.ReglaDominioException;

/**
 * Coordinador de persistencia (issue #33): retiene las 7 colecciones y la configuración de
 * cuotas, y carga/graba todo contra un directorio base (propiedad {@code casaamarilla.datos},
 * por defecto {@code datos/}).
 */
public class Almacen {

    private final File directorioBase;
    private final ArregloApoderados apoderados = new ArregloApoderados();
    private final ArregloAlumnos alumnos = new ArregloAlumnos();
    private final ArregloAulas aulas = new ArregloAulas();
    private final ConfiguracionCuotas cuotas = new ConfiguracionCuotas();
    private final ArregloSolicitudes solicitudes = new ArregloSolicitudes();
    private final ArregloMatriculas matriculas = new ArregloMatriculas();
    private final ArregloEntrevistas entrevistas = new ArregloEntrevistas();
    private final ArregloPagos pagos = new ArregloPagos();

    public Almacen() {
        this(new File(System.getProperty("casaamarilla.datos", "datos")));
    }

    public Almacen(String directorioBase) {
        this(new File(directorioBase));
    }

    public Almacen(File directorioBase) {
        this.directorioBase = directorioBase;
    }

    /** Carga en orden de dependencias; un archivo ausente deja la colección vacía (o sus seeds). */
    public void cargar() throws IOException {
        apoderados.cargar(ruta("apoderados.csv"));
        alumnos.cargar(ruta("alumnos.csv"), ruta("vinculos.csv"), apoderados);
        aulas.cargar(ruta("aulas.csv"));
        cargarCuotas(ruta("cuotas.csv"));
        solicitudes.cargar(ruta("solicitudes.csv"), ruta("documentos.csv"), ruta("ofertas.csv"),
                alumnos, aulas);
        matriculas.cargar(ruta("matriculas.csv"), solicitudes);
        entrevistas.cargar(ruta("entrevistas.csv"), solicitudes);
        pagos.cargar(ruta("pagos.csv"), solicitudes, matriculas);
    }

    /** Graba todo; es el único punto de escritura de la aplicación. */
    public void grabar() throws IOException {
        if (!directorioBase.exists() && !directorioBase.mkdirs()) {
            throw new IOException("No se pudo crear el directorio " + directorioBase + ".");
        }
        apoderados.grabar(ruta("apoderados.csv"));
        alumnos.grabar(ruta("alumnos.csv"), ruta("vinculos.csv"));
        aulas.grabar(ruta("aulas.csv"));
        grabarCuotas(ruta("cuotas.csv"));
        solicitudes.grabar(ruta("solicitudes.csv"), ruta("documentos.csv"), ruta("ofertas.csv"));
        matriculas.grabar(ruta("matriculas.csv"));
        entrevistas.grabar(ruta("entrevistas.csv"));
        pagos.grabar(ruta("pagos.csv"));
    }

    private void cargarCuotas(String ruta) throws IOException {
        if (!new File(ruta).exists()) {
            return;
        }
        try (BufferedReader entrada = new BufferedReader(
                new FileReader(ruta, StandardCharsets.UTF_8))) {
            String linea = entrada.readLine();
            if (linea == null) {
                return;
            }
            String[] campos = EscrituraCsv.dividir(linea);
            if (campos.length != 2) {
                throw new IOException(ruta + ", línea 1: se esperaban 2 campos y hay "
                        + campos.length + ".");
            }
            try {
                cuotas.setCuotas(Double.parseDouble(EscrituraCsv.desescapar(campos[0])),
                        Double.parseDouble(EscrituraCsv.desescapar(campos[1])));
            } catch (NumberFormatException | ReglaDominioException e) {
                throw new IOException(ruta + ", línea 1: " + e.getMessage());
            }
        }
    }

    private void grabarCuotas(String ruta) throws IOException {
        ArrayList<String> lineas = new ArrayList<String>();
        lineas.add(EscrituraCsv.unir(
                String.format(Locale.ROOT, "%.2f", cuotas.getCuotaInscripcion()),
                String.format(Locale.ROOT, "%.2f", cuotas.getCuotaMatricula())));
        EscrituraCsv.escribir(ruta, lineas);
    }

    private String ruta(String nombre) {
        return new File(directorioBase, nombre).getPath();
    }

    public File getDirectorioBase() {
        return directorioBase;
    }

    public ArregloApoderados getApoderados() {
        return apoderados;
    }

    public ArregloAlumnos getAlumnos() {
        return alumnos;
    }

    public ArregloAulas getAulas() {
        return aulas;
    }

    public ConfiguracionCuotas getCuotas() {
        return cuotas;
    }

    public ArregloSolicitudes getSolicitudes() {
        return solicitudes;
    }

    public ArregloMatriculas getMatriculas() {
        return matriculas;
    }

    public ArregloEntrevistas getEntrevistas() {
        return entrevistas;
    }

    public ArregloPagos getPagos() {
        return pagos;
    }
}
