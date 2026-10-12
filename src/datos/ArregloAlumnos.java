package datos;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
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
        ArrayList<String> lineasAlumnos = new ArrayList<String>();
        for (Alumno alumno : alumnos) {
            lineasAlumnos.add(EscrituraCsv.unir(
                    String.valueOf(alumno.getCodAlumno()),
                    alumno.getDni(),
                    alumno.getNombres(),
                    alumno.getApellidos(),
                    alumno.getFechaNacimiento().toString(),
                    alumno.getCelular()));
        }
        EscrituraCsv.escribir(rutaAlumnos, lineasAlumnos);

        ArrayList<String> lineasVinculos = new ArrayList<String>();
        for (Alumno alumno : alumnos) {
            for (Apoderado apoderado : alumno.getApoderados()) {
                boolean esPrincipal = apoderado == alumno.getPrincipal();
                lineasVinculos.add(EscrituraCsv.unir(
                        String.valueOf(alumno.getCodAlumno()),
                        apoderado.getDni(),
                        String.valueOf(esPrincipal)));
            }
        }
        EscrituraCsv.escribir(rutaVinculos, lineasVinculos);
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
                String[] campos = EscrituraCsv.dividir(linea);
                if (campos.length != 6) {
                    throw new IOException(ruta + ", línea " + numeroLinea
                            + ": se esperaban 6 campos y hay " + campos.length + ".");
                }
                try {
                    int codAlumno = Integer.parseInt(EscrituraCsv.desescapar(campos[0]));
                    String dni = EscrituraCsv.campoALeer(campos[1]);
                    String nombres = EscrituraCsv.campoALeer(campos[2]);
                    String apellidos = EscrituraCsv.campoALeer(campos[3]);
                    LocalDate fechaNacimiento = LocalDate.parse(EscrituraCsv.desescapar(campos[4]));
                    String celular = EscrituraCsv.campoALeer(campos[5]);
                    Alumno alumno = new Alumno(codAlumno, dni, nombres, apellidos,
                            fechaNacimiento, celular);
                    if (buscarEn(leidos, codAlumno) != null) {
                        throw new IOException(ruta + ", línea " + numeroLinea
                                + ": código de alumno duplicado " + codAlumno + ".");
                    }
                    leidos.add(alumno);
                } catch (IOException e) {
                    throw e;
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
                String[] campos = EscrituraCsv.dividir(linea);
                if (campos.length != 3) {
                    throw new IOException(ruta + ", línea " + numeroLinea
                            + ": se esperaban 3 campos y hay " + campos.length + ".");
                }
                try {
                    Alumno alumno = buscarEn(leidos,
                            Integer.parseInt(EscrituraCsv.desescapar(campos[0])));
                    Apoderado apoderado = apoderados.buscar(EscrituraCsv.campoALeer(campos[1]));
                    if (alumno == null || apoderado == null) {
                        throw new IOException(ruta + ", línea " + numeroLinea
                                + ": el alumno o el apoderado no existe.");
                    }
                    alumno.agregarApoderado(apoderado,
                            "true".equals(EscrituraCsv.campoALeer(campos[2])));
                } catch (IOException e) {
                    throw e;
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
