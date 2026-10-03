package datos;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;

import modelo.Alumno;
import modelo.AlumnoDuplicadoException;
import modelo.Apoderado;
import modelo.ReglaDominioException;
import modelo.Validaciones;

public class ArregloAlumnos {

    private static final int PRIMER_CODIGO = 202010001;
    private static final String SEPARADOR = ";";

    private ArrayList<Alumno> alumnos;
    private int siguienteCodigo;

    public ArregloAlumnos() {
        alumnos = new ArrayList<Alumno>();
        siguienteCodigo = PRIMER_CODIGO;
    }

    public Alumno registrar(String dni, String nombres, String apellidos,
            LocalDate fechaNacimiento, String celular) throws ReglaDominioException {
        String dniNormalizado = Validaciones.exigirDni(dni);
        Alumno existente = buscarPorDni(dniNormalizado);
        if (existente != null) {
            throw new AlumnoDuplicadoException(existente);
        }
        Alumno candidato = new Alumno(siguienteCodigo, dniNormalizado, nombres, apellidos,
                fechaNacimiento, celular);
        alumnos.add(candidato);
        siguienteCodigo++;
        return candidato;
    }

    public Alumno buscar(int codAlumno) {
        for (Alumno alumno : alumnos) {
            if (alumno.getCodAlumno() == codAlumno) {
                return alumno;
            }
        }
        return null;
    }

    public Alumno buscarPorDni(String dni) {
        String buscado = Validaciones.normalizarDni(dni);
        if (buscado == null) {
            return null;
        }
        for (Alumno alumno : alumnos) {
            if (alumno.getDni().equals(buscado)) {
                return alumno;
            }
        }
        return null;
    }

    public int cantidad() {
        return alumnos.size();
    }

    public int getSiguienteCodigo() {
        return siguienteCodigo;
    }

    public ArrayList<Alumno> listar() {
        return new ArrayList<Alumno>(alumnos);
    }

    public void grabar(String rutaAlumnos, String rutaVinculos) throws IOException {
        try (PrintWriter salida = new PrintWriter(
                new FileWriter(rutaAlumnos, StandardCharsets.UTF_8))) {
            for (Alumno alumno : alumnos) {
                salida.println(alumno.getCodAlumno() + SEPARADOR
                        + alumno.getDni() + SEPARADOR
                        + alumno.getNombres() + SEPARADOR
                        + alumno.getApellidos() + SEPARADOR
                        + alumno.getFechaNacimiento() + SEPARADOR
                        + alumno.getCelular());
            }
        }
        try (PrintWriter salida = new PrintWriter(
                new FileWriter(rutaVinculos, StandardCharsets.UTF_8))) {
            for (Alumno alumno : alumnos) {
                for (Apoderado apoderado : alumno.getApoderados()) {
                    boolean esPrincipal = apoderado == alumno.getPrincipal();
                    salida.println(alumno.getCodAlumno() + SEPARADOR
                            + apoderado.getDni() + SEPARADOR
                            + esPrincipal);
                }
            }
        }
    }

    public void cargar(String rutaAlumnos, String rutaVinculos, ArregloApoderados apoderados)
            throws IOException {
        ArrayList<Alumno> leidos = leerAlumnos(rutaAlumnos);
        leerVinculos(rutaVinculos, leidos, apoderados);

        alumnos = leidos;
        siguienteCodigo = PRIMER_CODIGO;
        for (Alumno alumno : alumnos) {
            if (alumno.getCodAlumno() >= siguienteCodigo) {
                siguienteCodigo = alumno.getCodAlumno() + 1;
            }
        }
    }

    private ArrayList<Alumno> leerAlumnos(String ruta) throws IOException {
        ArrayList<Alumno> leidos = new ArrayList<Alumno>();
        if (!new File(ruta).exists()) {
            return leidos;
        }
        try (BufferedReader entrada = new BufferedReader(
                new FileReader(ruta, StandardCharsets.UTF_8))) {
            String linea;
            int numeroLinea = 0;
            while ((linea = entrada.readLine()) != null) {
                numeroLinea++;
                String[] campos = linea.split(SEPARADOR, -1);
                if (campos.length != 6) {
                    throw new IOException(ruta + ", línea " + numeroLinea
                            + ": se esperaban 6 campos y hay " + campos.length + ".");
                }
                try {
                    int codAlumno = Integer.parseInt(campos[0]);
                    LocalDate fechaNacimiento = LocalDate.parse(campos[4]);
                    leidos.add(new Alumno(codAlumno, campos[1], campos[2], campos[3],
                            fechaNacimiento, campos[5]));
                } catch (NumberFormatException | DateTimeParseException
                        | ReglaDominioException e) {
                    throw new IOException(ruta + ", línea " + numeroLinea + ": "
                            + e.getMessage());
                }
            }
        }
        return leidos;
    }

    private void leerVinculos(String ruta, ArrayList<Alumno> leidos,
            ArregloApoderados apoderados) throws IOException {
        if (!new File(ruta).exists()) {
            return;
        }
        try (BufferedReader entrada = new BufferedReader(
                new FileReader(ruta, StandardCharsets.UTF_8))) {
            String linea;
            int numeroLinea = 0;
            while ((linea = entrada.readLine()) != null) {
                numeroLinea++;
                String[] campos = linea.split(SEPARADOR, -1);
                if (campos.length != 3) {
                    throw new IOException(ruta + ", línea " + numeroLinea
                            + ": se esperaban 3 campos y hay " + campos.length + ".");
                }
                try {
                    Alumno alumno = buscarEn(leidos, Integer.parseInt(campos[0]));
                    Apoderado apoderado = apoderados.buscar(campos[1]);
                    if (alumno == null || apoderado == null) {
                        throw new IOException(ruta + ", línea " + numeroLinea
                                + ": el alumno o el apoderado no existe.");
                    }
                    alumno.agregarApoderado(apoderado, campos[2].equals("true"));
                } catch (NumberFormatException | ReglaDominioException e) {
                    throw new IOException(ruta + ", línea " + numeroLinea + ": "
                            + e.getMessage());
                }
            }
        }
    }

    private static Alumno buscarEn(ArrayList<Alumno> lista, int codAlumno) {
        for (Alumno alumno : lista) {
            if (alumno.getCodAlumno() == codAlumno) {
                return alumno;
            }
        }
        return null;
    }
}
