package datos;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;

import modelo.Alumno;
import modelo.DatoInvalidoException;
import modelo.EstadoMatricula;
import modelo.Matricula;
import modelo.ReglaDominioException;
import modelo.Solicitud;

public class ArregloMatriculas {

    private static final int PRIMER_NUMERO = 100001;

    private ArrayList<Matricula> matriculas;
    private int siguienteNumero;

    public ArregloMatriculas() {
        matriculas = new ArrayList<Matricula>();
        siguienteNumero = PRIMER_NUMERO;
    }

    public Matricula registrar(Solicitud solicitud, LocalDateTime fechaHoraCreacion)
            throws ReglaDominioException {
        if (solicitud == null) {
            throw new DatoInvalidoException("La solicitud es obligatoria.");
        }
        Matricula vigente = vigenteDeAlumno(solicitud.getAlumno());
        if (vigente != null) {
            throw new ReglaDominioException(solicitud.getAlumno().getNombreCompleto()
                    + " ya tiene la matrícula " + vigente.getCodigo() + " en "
                    + vigente.getEstado() + ".");
        }
        Matricula nueva = new Matricula(siguienteNumero, solicitud, fechaHoraCreacion);
        matriculas.add(nueva);
        siguienteNumero++;
        return nueva;
    }

    public Matricula buscar(int numMatricula) {
        for (Matricula matricula : matriculas) {
            if (matricula.getNumMatricula() == numMatricula) {
                return matricula;
            }
        }
        return null;
    }

    public Matricula vigenteDe(Solicitud solicitud) {
        for (Matricula matricula : matriculas) {
            if (matricula.getSolicitud() == solicitud && matricula.estaVigente()) {
                return matricula;
            }
        }
        return null;
    }

    /** La vigente o, si no hay, la última cancelada: lo que la pantalla de pago muestra. */
    public Matricula ultimaDe(Solicitud solicitud) {
        Matricula ultima = null;
        for (Matricula matricula : matriculas) {
            if (matricula.getSolicitud() == solicitud) {
                if (matricula.estaVigente()) {
                    return matricula;
                }
                ultima = matricula;
            }
        }
        return ultima;
    }

    public boolean contiene(Matricula matricula) {
        return matriculas.contains(matricula);
    }

    public ArrayList<Matricula> listar() {
        return new ArrayList<Matricula>(matriculas);
    }

    public void grabar(String ruta) throws IOException {
        ArrayList<String> lineas = new ArrayList<String>();
        for (Matricula matricula : matriculas) {
            lineas.add(EscrituraCsv.unir(
                    String.valueOf(matricula.getNumMatricula()),
                    matricula.getSolicitud().getCodigo(),
                    matricula.getEstado().name(),
                    fechaHora(matricula.getFechaHoraCreacion()),
                    fechaHora(matricula.getVencimientoPagoOriginal()),
                    fechaHora(matricula.getFechaCancelacion()),
                    matricula.getMotivoCancelacion(),
                    matricula.getResponsableCancelacion()));
        }
        EscrituraCsv.escribir(ruta, lineas);
    }

    public void cargar(String ruta, ArregloSolicitudes solicitudes) throws IOException {
        ArrayList<Matricula> leidos = new ArrayList<Matricula>();
        if (!new File(ruta).exists()) {
            matriculas = leidos;
            siguienteNumero = PRIMER_NUMERO;
            return;
        }
        try (BufferedReader entrada = new BufferedReader(
                new FileReader(ruta, StandardCharsets.UTF_8))) {
            String linea;
            int numeroLinea = 0;
            while ((linea = entrada.readLine()) != null) {
                numeroLinea++;
                String[] campos = EscrituraCsv.dividir(linea);
                if (campos.length != 8) {
                    throw new IOException(ruta + ", línea " + numeroLinea
                            + ": se esperaban 8 campos y hay " + campos.length + ".");
                }
                try {
                    int numMatricula = Integer.parseInt(EscrituraCsv.desescapar(campos[0]));
                    Solicitud solicitud = solicitudes.buscar(EscrituraCsv.campoALeer(campos[1]));
                    EstadoMatricula estado = EstadoMatricula.valueOf(
                            EscrituraCsv.desescapar(campos[2]));
                    LocalDateTime fechaHoraCreacion = fechaHora(campos[3]);
                    LocalDateTime vencimientoPagoOriginal = fechaHora(campos[4]);
                    LocalDateTime fechaCancelacion = fechaHoraOpcional(campos[5]);
                    String motivoCancelacion = EscrituraCsv.campoALeer(campos[6]);
                    String responsableCancelacion = EscrituraCsv.campoALeer(campos[7]);
                    if (solicitud == null) {
                        throw new IOException(ruta + ", línea " + numeroLinea
                                + ": la solicitud referenciada no existe.");
                    }
                    if (buscarEn(leidos, numMatricula) != null) {
                        throw new IOException(ruta + ", línea " + numeroLinea
                                + ": número de matrícula duplicado " + numMatricula + ".");
                    }
                    leidos.add(Matricula.reconstruir(numMatricula, solicitud, estado,
                            fechaHoraCreacion, vencimientoPagoOriginal, fechaCancelacion,
                            motivoCancelacion, responsableCancelacion));
                } catch (IOException e) {
                    throw e;
                } catch (IllegalArgumentException | DateTimeParseException
                        | ReglaDominioException e) {
                    throw new IOException(ruta + ", línea " + numeroLinea + ": "
                            + e.getMessage());
                }
            }
        }
        matriculas = leidos;
        siguienteNumero = PRIMER_NUMERO;
        for (Matricula matricula : matriculas) {
            if (matricula.getNumMatricula() >= siguienteNumero) {
                siguienteNumero = matricula.getNumMatricula() + 1;
            }
        }
    }

    private static LocalDateTime fechaHora(String campo) {
        return LocalDateTime.parse(EscrituraCsv.desescapar(campo));
    }

    private static LocalDateTime fechaHoraOpcional(String campo) {
        String valor = EscrituraCsv.campoALeer(campo);
        return valor == null ? null : LocalDateTime.parse(valor);
    }

    private static String fechaHora(LocalDateTime fecha) {
        return fecha == null ? null : fecha.toString();
    }

    private static Matricula buscarEn(ArrayList<Matricula> lista, int numMatricula) {
        for (Matricula matricula : lista) {
            if (matricula.getNumMatricula() == numMatricula) {
                return matricula;
            }
        }
        return null;
    }

    private Matricula vigenteDeAlumno(Alumno alumno) {
        for (Matricula matricula : matriculas) {
            boolean esDelAlumno = matricula.getSolicitud().getAlumno().getCodAlumno()
                    == alumno.getCodAlumno();
            if (esDelAlumno && matricula.estaVigente()) {
                return matricula;
            }
        }
        return null;
    }
}
