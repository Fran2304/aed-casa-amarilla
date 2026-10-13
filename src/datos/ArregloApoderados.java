package datos;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import modelo.Apoderado;
import modelo.ApoderadoDuplicadoException;
import modelo.ReglaDominioException;
import modelo.Validaciones;

public class ArregloApoderados {

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

    public void grabar(String ruta) throws IOException {
        ArrayList<String> lineas = new ArrayList<String>();
        for (Apoderado apoderado : apoderados) {
            lineas.add(EscrituraCsv.unir(
                    apoderado.getDni(),
                    apoderado.getNombres(),
                    apoderado.getApellidos(),
                    apoderado.getCelular()));
        }
        EscrituraCsv.escribir(ruta, lineas);
    }

    public void cargar(String ruta) throws IOException {
        if (!new File(ruta).exists()) {
            apoderados.clear();
            return;
        }
        ArrayList<Apoderado> leidos = new ArrayList<Apoderado>();
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
                    Apoderado apoderado = new Apoderado(
                            EscrituraCsv.campoALeer(campos[0]),
                            EscrituraCsv.campoALeer(campos[1]),
                            EscrituraCsv.campoALeer(campos[2]),
                            EscrituraCsv.campoALeer(campos[3]));
                    if (buscarEn(leidos, apoderado.getDni()) != null) {
                        throw new IOException(ruta + ", línea " + numeroLinea
                                + ": DNI de apoderado duplicado " + apoderado.getDni() + ".");
                    }
                    leidos.add(apoderado);
                } catch (IOException e) {
                    throw e;
                } catch (ReglaDominioException e) {
                    throw new IOException(ruta + ", línea " + numeroLinea + ": "
                            + e.getMessage());
                }
            }
        }
        apoderados = leidos;
    }

    private static Apoderado buscarEn(ArrayList<Apoderado> lista, String dni) {
        for (Apoderado apoderado : lista) {
            if (apoderado.getDni().equals(dni)) {
                return apoderado;
            }
        }
        return null;
    }
}
