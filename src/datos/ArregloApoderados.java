package datos;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import modelo.Apoderado;
import modelo.ApoderadoDuplicadoException;
import modelo.ReglaDominioException;
import modelo.Validaciones;

public class ArregloApoderados {

    private static final String SEPARADOR = ";";

    private ArrayList<Apoderado> apoderados;

    public ArregloApoderados() {
        apoderados = new ArrayList<Apoderado>();
    }

    public Apoderado registrar(String dni, String nombres, String apellidos, String celular)
            throws ReglaDominioException {
        String dniNormalizado = Validaciones.exigirDni(dni);
        Apoderado existente = buscar(dniNormalizado);
        if (existente != null) {
            throw new ApoderadoDuplicadoException(existente);
        }
        Apoderado nuevo = new Apoderado(dniNormalizado, nombres, apellidos, celular);
        apoderados.add(nuevo);
        return nuevo;
    }

    public Apoderado buscar(String dni) {
        String buscado = Validaciones.normalizarDni(dni);
        if (buscado == null) {
            return null;
        }
        for (Apoderado apoderado : apoderados) {
            if (apoderado.getDni().equals(buscado)) {
                return apoderado;
            }
        }
        return null;
    }

    public int cantidad() {
        return apoderados.size();
    }

    public ArrayList<Apoderado> listar() {
        return new ArrayList<Apoderado>(apoderados);
    }

    // Formato de apoderados.txt: dni;nombres;apellidos;celular
    public void grabar(String ruta) throws IOException {
        try (PrintWriter salida = new PrintWriter(
                new FileWriter(ruta, StandardCharsets.UTF_8))) {
            for (Apoderado apoderado : apoderados) {
                salida.println(apoderado.getDni() + SEPARADOR
                        + apoderado.getNombres() + SEPARADOR
                        + apoderado.getApellidos() + SEPARADOR
                        + apoderado.getCelular());
            }
        }
    }

    public void cargar(String ruta) throws IOException {
        // Primer arranque: todavía no se grabó nada.
        if (!new File(ruta).exists()) {
            apoderados.clear();
            return;
        }
        // Se lee en una lista aparte para no dejar la colección a medias si una línea falla.
        ArrayList<Apoderado> leidos = new ArrayList<Apoderado>();
        try (BufferedReader entrada = new BufferedReader(
                new FileReader(ruta, StandardCharsets.UTF_8))) {
            String linea;
            int numeroLinea = 0;
            while ((linea = entrada.readLine()) != null) {
                numeroLinea++;
                // Con -1, split conserva los campos vacíos del final de la línea.
                String[] campos = linea.split(SEPARADOR, -1);
                if (campos.length != 4) {
                    throw new IOException(ruta + ", línea " + numeroLinea
                            + ": se esperaban 4 campos y hay " + campos.length + ".");
                }
                try {
                    leidos.add(new Apoderado(campos[0], campos[1], campos[2], campos[3]));
                } catch (ReglaDominioException e) {
                    throw new IOException(ruta + ", línea " + numeroLinea + ": "
                            + e.getMessage());
                }
            }
        }
        apoderados = leidos;
    }
}
