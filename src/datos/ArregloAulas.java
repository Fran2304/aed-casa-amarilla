package datos;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import modelo.Aula;

public class ArregloAulas {

    private ArrayList<Aula> aulas;

    public ArregloAulas() {
        aulas = new ArrayList<Aula>();
        // Aulas de ejemplo del diseño. La tabla completa sigue pendiente (§6.2 del flujo).
        aulas.add(new Aula("AUL-01", "Girasoles", 4, 20));
        aulas.add(new Aula("AUL-02", "Tulipanes", 3, 18));
    }

    public ArrayList<Aula> listar() {
        return new ArrayList<Aula>(aulas);
    }

    public Aula buscar(String codigo) {
        for (Aula aula : aulas) {
            if (aula.getCodigo().equals(codigo)) {
                return aula;
            }
        }
        return null;
    }

    public void grabar(String ruta) throws IOException {
        ArrayList<String> lineas = new ArrayList<String>();
        for (Aula aula : aulas) {
            lineas.add(EscrituraCsv.unir(
                    aula.getCodigo(),
                    aula.getNombre(),
                    String.valueOf(aula.getEdadRequerida()),
                    String.valueOf(aula.getCapacidad())));
        }
        EscrituraCsv.escribir(ruta, lineas);
    }

    // Un archivo presente REPLACE los seeds; un archivo ausente conserva los seeds del ctor.
    public void cargar(String ruta) throws IOException {
        if (!new File(ruta).exists()) {
            return;
        }
        ArrayList<Aula> leidos = new ArrayList<Aula>();
        try (BufferedReader entrada = new BufferedReader(
                new FileReader(ruta, StandardCharsets.UTF_8))) {
            String linea;
            int numeroLinea = 0;
            while ((linea = entrada.readLine()) != null) {
                numeroLinea++;
                String[] campos = EscrituraCsv.dividir(linea);
                if (campos.length != 4) {
                    throw new IOException(ruta + ", línea " + numeroLinea
                            + ": se esperaban 4 campos y hay " + campos.length + ".");
                }
                try {
                    String codigo = EscrituraCsv.campoALeer(campos[0]);
                    String nombre = EscrituraCsv.campoALeer(campos[1]);
                    int edadRequerida = Integer.parseInt(EscrituraCsv.desescapar(campos[2]));
                    int capacidad = Integer.parseInt(EscrituraCsv.desescapar(campos[3]));
                    if (codigo == null || nombre == null) {
                        throw new IOException(ruta + ", línea " + numeroLinea
                                + ": código o nombre de aula vacío.");
                    }
                    if (buscarEn(leidos, codigo) != null) {
                        throw new IOException(ruta + ", línea " + numeroLinea
                                + ": código de aula duplicado " + codigo + ".");
                    }
                    leidos.add(new Aula(codigo, nombre, edadRequerida, capacidad));
                } catch (IOException e) {
                    throw e;
                } catch (NumberFormatException e) {
                    throw new IOException(ruta + ", línea " + numeroLinea + ": "
                            + e.getMessage());
                }
            }
        }
        aulas = leidos;
    }

    private static Aula buscarEn(ArrayList<Aula> lista, String codigo) {
        for (Aula aula : lista) {
            if (aula.getCodigo().equals(codigo)) {
                return aula;
            }
        }
        return null;
    }
}
