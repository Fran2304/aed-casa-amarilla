package datos;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;

import modelo.Entrevista;
import modelo.ReglaDominioException;
import modelo.ResultadoEntrevista;
import modelo.Solicitud;
import negocio.ExpedienteDocumentos;

public class ArregloEntrevistas {

    private ArrayList<Entrevista> entrevistas;

    public ArregloEntrevistas() {
        entrevistas = new ArrayList<Entrevista>();
    }

    public Entrevista agendar(Solicitud solicitud, ExpedienteDocumentos expediente,
            LocalDateTime fechaHora, ArrayList<String> personal, String responsable)
            throws ReglaDominioException {
        if (buscarDe(solicitud) != null) {
            throw new ReglaDominioException(
                    "La entrevista de " + solicitud.getCodigo() + " ya está agendada.");
        }
        Entrevista nueva = new Entrevista(solicitud, expediente, fechaHora, personal,
                responsable);
        entrevistas.add(nueva);
        return nueva;
    }

    public Entrevista buscarDe(Solicitud solicitud) {
        for (Entrevista entrevista : entrevistas) {
            if (entrevista.getSolicitud() == solicitud
                    && entrevista.getExpediente() == solicitud.getExpediente()) {
                return entrevista;
            }
        }
        return null;
    }

    public ArrayList<Entrevista> listar() {
        return new ArrayList<Entrevista>(entrevistas);
    }

    public ArrayList<Entrevista> listarPorSolicitud(String codigoSolicitud) {
        ArrayList<Entrevista> resultado = new ArrayList<Entrevista>();
        for (Entrevista entrevista : entrevistas) {
            if (entrevista.getSolicitud().getCodigo().equals(codigoSolicitud)) {
                resultado.add(entrevista);
            }
        }
        return resultado;
    }

    public void grabar(String ruta) throws IOException {
        ArrayList<String> lineas = new ArrayList<String>();
        for (Entrevista entrevista : entrevistas) {
            lineas.add(EscrituraCsv.unir(
                    entrevista.getSolicitud().getCodigo(),
                    fechaHora(entrevista.getFechaHora()),
                    String.join(",", entrevista.getPersonal()),
                    entrevista.getResponsable(),
                    booleano(entrevista.getAsistioApoderado()),
                    String.valueOf(entrevista.isRealizada()),
                    String.valueOf(entrevista.getInasistencias()),
                    entrevista.getResultado() == null ? null : entrevista.getResultado().name()));
        }
        EscrituraCsv.escribir(ruta, lineas);
    }

    public void cargar(String ruta, ArregloSolicitudes solicitudes) throws IOException {
        ArrayList<Entrevista> leidos = new ArrayList<Entrevista>();
        if (!new File(ruta).exists()) {
            entrevistas = leidos;
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
                    Solicitud solicitud = solicitudes.buscar(EscrituraCsv.campoALeer(campos[0]));
                    if (solicitud == null) {
                        throw new IOException(ruta + ", línea " + numeroLinea
                                + ": la solicitud referenciada no existe.");
                    }
                    LocalDateTime fechaHora = fechaHora(campos[1]);
                    ArrayList<String> personal = leerPersonal(campos[2]);
                    String responsable = EscrituraCsv.campoALeer(campos[3]);
                    Boolean asistioApoderado = booleanoOpcional(campos[4]);
                    boolean realizada = "true".equals(EscrituraCsv.campoALeer(campos[5]));
                    int inasistencias = Integer.parseInt(EscrituraCsv.desescapar(campos[6]));
                    ResultadoEntrevista resultado = resultadoOpcional(campos[7]);
                    if (buscarEn(leidos, solicitud.getCodigo()) != null) {
                        throw new IOException(ruta + ", línea " + numeroLinea
                                + ": la entrevista de " + solicitud.getCodigo()
                                + " está duplicada.");
                    }
                    // La entrevista se liga al expediente actual de su solicitud (limitación
                    // documentada en la spec: un ciclo de inscripción reemplazado no se distingue).
                    leidos.add(Entrevista.reconstruir(solicitud, solicitud.getExpediente(),
                            fechaHora, personal, responsable, asistioApoderado, realizada,
                            inasistencias, resultado));
                } catch (IOException e) {
                    throw e;
                } catch (IllegalArgumentException | DateTimeParseException
                        | ReglaDominioException e) {
                    throw new IOException(ruta + ", línea " + numeroLinea + ": "
                            + e.getMessage());
                }
            }
        }
        entrevistas = leidos;
    }

    private static ArrayList<String> leerPersonal(String campo) {
        ArrayList<String> personal = new ArrayList<String>();
        for (String nombre : campo.split(",", -1)) {
            personal.add(EscrituraCsv.desescapar(nombre));
        }
        return personal;
    }

    private static Entrevista buscarEn(ArrayList<Entrevista> lista, String codigo) {
        for (Entrevista entrevista : lista) {
            if (entrevista.getSolicitud().getCodigo().equals(codigo)) {
                return entrevista;
            }
        }
        return null;
    }

    private static Boolean booleanoOpcional(String campo) {
        String valor = EscrituraCsv.campoALeer(campo);
        return valor == null ? null : "true".equals(valor);
    }

    private static ResultadoEntrevista resultadoOpcional(String campo) {
        String valor = EscrituraCsv.campoALeer(campo);
        return valor == null ? null : ResultadoEntrevista.valueOf(valor);
    }

    private static String booleano(Boolean valor) {
        return valor == null ? null : String.valueOf(valor);
    }

    private static LocalDateTime fechaHora(String campo) {
        return LocalDateTime.parse(EscrituraCsv.desescapar(campo));
    }

    private static String fechaHora(LocalDateTime fecha) {
        return fecha == null ? null : fecha.toString();
    }
}
