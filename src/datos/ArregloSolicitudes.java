package datos;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import modelo.Alumno;
import modelo.Aula;
import modelo.DatoInvalidoException;
import modelo.EstadoDocumento;
import modelo.EstadoSolicitud;
import modelo.Oferta;
import modelo.ReglaDominioException;
import modelo.Solicitud;
import modelo.SolicitudDuplicadaException;
import modelo.TipoDocumento;
import util.FechasAdmision;
import util.Reloj;

public class ArregloSolicitudes {

    private static final int PRIMER_NUMERO = 1001;

    private ArrayList<Solicitud> solicitudes;
    private int siguienteNumero;

    public ArregloSolicitudes() {
        solicitudes = new ArrayList<Solicitud>();
        siguienteNumero = PRIMER_NUMERO;
    }

    public Solicitud registrar(Alumno alumno, Aula aula) throws ReglaDominioException {
        if (alumno == null) {
            throw new DatoInvalidoException("El alumno es obligatorio.");
        }
        if (aula == null) {
            throw new DatoInvalidoException("El aula solicitada es obligatoria.");
        }

        alumno.validarApoderados();

        int edad = alumno.edadAl(FechasAdmision.CORTE_2027);
        if (edad != aula.getEdadRequerida()) {
            throw new ReglaDominioException(alumno.getNombreCompleto() + " tendrá " + edad
                    + " años al 31/03/2027 y el aula " + aula.getNombre() + " es para "
                    + aula.getEdadRequerida() + " años.");
        }

        Solicitud activa = buscarActivaDe(alumno);
        if (activa != null) {
            throw new SolicitudDuplicadaException(activa);
        }

        Solicitud nueva = new Solicitud("SOL-" + siguienteNumero, alumno, aula,
                Reloj.porDefecto().now());
        solicitudes.add(nueva);
        siguienteNumero++;
        return nueva;
    }

    public Solicitud buscarActivaDe(Alumno alumno) {
        for (Solicitud solicitud : solicitudes) {
            boolean esDelAlumno = solicitud.getAlumno().getCodAlumno() == alumno.getCodAlumno();
            if (esDelAlumno && solicitud.estaActiva()) {
                return solicitud;
            }
        }
        return null;
    }

    public Solicitud buscar(String codigo) {
        for (Solicitud solicitud : solicitudes) {
            if (solicitud.getCodigo().equals(codigo)) {
                return solicitud;
            }
        }
        return null;
    }

    public ArrayList<Solicitud> listar() {
        return new ArrayList<Solicitud>(solicitudes);
    }

    public ArrayList<Solicitud> colaSinPago(Aula aula) {
        ArrayList<Solicitud> enCola = new ArrayList<Solicitud>();
        for (Solicitud solicitud : solicitudes) {
            if (solicitud.getAula() == aula && solicitud.estaEnColaSinPago()) {
                enCola.add(solicitud);
            }
        }
        return ordenarPorIngreso(enCola);
    }

    public ArrayList<Solicitud> habilitadas(Aula aula) {
        ArrayList<Solicitud> habilitadas = new ArrayList<Solicitud>();
        for (Solicitud solicitud : solicitudes) {
            if (solicitud.getAula() == aula
                    && solicitud.getEstado() == EstadoSolicitud.HABILITADA_PARA_PAGO) {
                habilitadas.add(solicitud);
            }
        }
        return habilitadas;
    }

    public ArrayList<Solicitud> enDocumentacion(Aula aula) {
        ArrayList<Solicitud> enDocumentacion = new ArrayList<Solicitud>();
        for (Solicitud solicitud : solicitudes) {
            if (solicitud.getAula() == aula
                    && solicitud.getEstado() == EstadoSolicitud.EN_DOCUMENTACION) {
                enDocumentacion.add(solicitud);
            }
        }
        return enDocumentacion;
    }

    public void vencerHabilitaciones(LocalDateTime ahora) throws ReglaDominioException {
        if (ahora == null) {
            throw new DatoInvalidoException("La fecha de revisión es obligatoria.");
        }
        for (Solicitud solicitud : solicitudes) {
            if (solicitud.habilitacionVencida(ahora)) {
                solicitud.vencerHabilitacion(solicitud.getVencimientoHabilitacion());
            }
        }
    }

    public void cancelarDocumentacionVencida(LocalDate hoy, String responsable)
            throws ReglaDominioException {
        if (hoy == null) {
            throw new DatoInvalidoException("La fecha de revisión es obligatoria.");
        }
        for (Solicitud solicitud : solicitudes) {
            if (solicitud.getEstado() == EstadoSolicitud.EN_DOCUMENTACION
                    && solicitud.getExpediente().debeCancelarse(hoy)) {
                solicitud.cancelarPorDocumentacion(hoy.atStartOfDay(),
                        solicitud.getExpediente().motivoCancelacion(hoy), responsable);
            }
        }
    }

    public ArrayList<Solicitud> colaFavorable(Aula aula) {
        ArrayList<Solicitud> enCola = new ArrayList<Solicitud>();
        for (Solicitud solicitud : solicitudes) {
            if (solicitud.getAula() == aula && solicitud.estaEnColaFavorable()) {
                enCola.add(solicitud);
            }
        }
        return ordenarPorIngreso(enCola);
    }

    public void grabar(String rutaSolicitudes, String rutaDocumentos, String rutaOfertas)
            throws IOException {
        ArrayList<String> lineasSolicitudes = new ArrayList<String>();
        ArrayList<String> lineasDocumentos = new ArrayList<String>();
        ArrayList<String> lineasOfertas = new ArrayList<String>();
        for (Solicitud solicitud : solicitudes) {
            lineasSolicitudes.add(EscrituraCsv.unir(
                    solicitud.getCodigo(),
                    String.valueOf(solicitud.getAlumno().getCodAlumno()),
                    solicitud.getAula().getCodigo(),
                    fechaHora(solicitud.getFechaRegistro()),
                    solicitud.getEstado().name(),
                    fechaHora(solicitud.getFechaIngresoCola()),
                    fechaHora(solicitud.getFechaHabilitacion()),
                    fechaHora(solicitud.getVencimientoHabilitacionOriginal()),
                    fechaHora(solicitud.getFechaConfirmacionInscripcion()),
                    fechaHora(solicitud.getFechaCancelacion()),
                    solicitud.getMotivoCancelacion(),
                    solicitud.getResponsableCancelacion(),
                    solicitud.getExpediente() == null ? null
                            : solicitud.getExpediente().getInscripcionConfirmadaEl().toString(),
                    String.valueOf(solicitud.getInicioCicloOfertas())));
            if (solicitud.getExpediente() != null) {
                for (modelo.Documento documento : solicitud.getExpediente().getDocumentos()) {
                    lineasDocumentos.add(EscrituraCsv.unir(
                            solicitud.getCodigo(),
                            documento.getTipo().name(),
                            fechaHora(documento.getFechaHoraEntrega()),
                            fechaHora(documento.getFechaHoraCorreccion()),
                            documento.getEstado() == null ? null : documento.getEstado().name(),
                            documento.getObservacion(),
                            fechaHora(documento.getFechaHoraComunicacion())));
                }
            }
            for (Oferta oferta : solicitud.getOfertas()) {
                lineasOfertas.add(EscrituraCsv.unir(
                        solicitud.getCodigo(),
                        fechaHora(oferta.getFechaHora()),
                        oferta.getPersonal(),
                        String.valueOf(oferta.isAceptada())));
            }
        }
        EscrituraCsv.escribir(rutaSolicitudes, lineasSolicitudes);
        EscrituraCsv.escribir(rutaDocumentos, lineasDocumentos);
        EscrituraCsv.escribir(rutaOfertas, lineasOfertas);
    }

    public void cargar(String rutaSolicitudes, String rutaDocumentos, String rutaOfertas,
            ArregloAlumnos alumnos, ArregloAulas aulas) throws IOException {
        ArrayList<Solicitud> leidos = leerSolicitudes(rutaSolicitudes, alumnos, aulas);
        leerDocumentos(rutaDocumentos, leidos);
        leerOfertas(rutaOfertas, leidos);

        solicitudes = leidos;
        siguienteNumero = PRIMER_NUMERO;
        for (Solicitud solicitud : solicitudes) {
            int numero = numeroDe(solicitud.getCodigo());
            if (numero >= siguienteNumero) {
                siguienteNumero = numero + 1;
            }
        }
    }

    private ArrayList<Solicitud> leerSolicitudes(String ruta, ArregloAlumnos alumnos,
            ArregloAulas aulas) throws IOException {
        ArrayList<Solicitud> leidos = new ArrayList<Solicitud>();
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
                if (campos.length != 14) {
                    throw new IOException(ruta + ", línea " + numeroLinea
                            + ": se esperaban 14 campos y hay " + campos.length + ".");
                }
                try {
                    String codigo = EscrituraCsv.campoALeer(campos[0]);
                    int codAlumno = Integer.parseInt(EscrituraCsv.desescapar(campos[1]));
                    String codAula = EscrituraCsv.campoALeer(campos[2]);
                    LocalDateTime fechaRegistro = fechaHora(campos[3]);
                    EstadoSolicitud estado = EstadoSolicitud.valueOf(
                            EscrituraCsv.desescapar(campos[4]));
                    LocalDateTime fechaIngresoCola = fechaHoraOpcional(campos[5]);
                    LocalDateTime fechaHabilitacion = fechaHoraOpcional(campos[6]);
                    LocalDateTime vencimientoHabilitacionOriginal = fechaHoraOpcional(campos[7]);
                    LocalDateTime fechaConfirmacionInscripcion = fechaHoraOpcional(campos[8]);
                    LocalDateTime fechaCancelacion = fechaHoraOpcional(campos[9]);
                    String motivoCancelacion = EscrituraCsv.campoALeer(campos[10]);
                    String responsableCancelacion = EscrituraCsv.campoALeer(campos[11]);
                    LocalDate inscripcionConfirmadaEl = fechaOpcional(campos[12]);
                    int inicioCicloOfertas = Integer.parseInt(EscrituraCsv.desescapar(campos[13]));

                    Alumno alumno = alumnos.buscar(codAlumno);
                    Aula aula = aulas.buscar(codAula);
                    if (alumno == null || aula == null) {
                        throw new IOException(ruta + ", línea " + numeroLinea
                                + ": el alumno o el aula referenciada no existe.");
                    }
                    if (buscarEn(leidos, codigo) != null) {
                        throw new IOException(ruta + ", línea " + numeroLinea
                                + ": código de solicitud duplicado " + codigo + ".");
                    }
                    leidos.add(Solicitud.reconstruir(codigo, alumno, aula, fechaRegistro, estado,
                            fechaIngresoCola, fechaHabilitacion, vencimientoHabilitacionOriginal,
                            fechaConfirmacionInscripcion, fechaCancelacion, motivoCancelacion,
                            responsableCancelacion, inscripcionConfirmadaEl, null,
                            inicioCicloOfertas));
                } catch (IOException e) {
                    throw e;
                } catch (IllegalArgumentException | DateTimeParseException
                        | ReglaDominioException e) {
                    throw new IOException(ruta + ", línea " + numeroLinea + ": "
                            + e.getMessage());
                }
            }
        }
        return leidos;
    }

    private void leerDocumentos(String ruta, ArrayList<Solicitud> leidos) throws IOException {
        Set<String> vistos = new HashSet<String>();
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
                if (campos.length != 7) {
                    throw new IOException(ruta + ", línea " + numeroLinea
                            + ": se esperaban 7 campos y hay " + campos.length + ".");
                }
                try {
                    String codSolicitud = EscrituraCsv.campoALeer(campos[0]);
                    TipoDocumento tipo = TipoDocumento.valueOf(EscrituraCsv.desescapar(campos[1]));
                    Solicitud solicitud = buscarEn(leidos, codSolicitud);
                    if (solicitud == null || solicitud.getExpediente() == null) {
                        throw new IOException(ruta + ", línea " + numeroLinea
                                + ": la solicitud o su expediente no existe.");
                    }
                    String clave = codSolicitud + "\u0000" + tipo.name();
                    if (!vistos.add(clave)) {
                        throw new IOException(ruta + ", línea " + numeroLinea
                                + ": documento duplicado " + codSolicitud + "/"
                                + tipo.name() + ".");
                    }
                    modelo.Documento documento = solicitud.getExpediente().getDocumento(tipo);
                    if (documento == null) {
                        throw new IOException(ruta + ", línea " + numeroLinea
                                + ": tipo de documento desconocido " + tipo + ".");
                    }
                    documento.reconstruir(fechaHoraOpcional(campos[2]),
                            fechaHoraOpcional(campos[3]),
                            estadoDocumentoOpcional(campos[4]),
                            EscrituraCsv.campoALeer(campos[5]),
                            fechaHoraOpcional(campos[6]));
                } catch (IOException e) {
                    throw e;
                } catch (IllegalArgumentException | DateTimeParseException e) {
                    throw new IOException(ruta + ", línea " + numeroLinea + ": "
                            + e.getMessage());
                }
            }
        }
    }

    private void leerOfertas(String ruta, ArrayList<Solicitud> leidos) throws IOException {
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
                if (campos.length != 4) {
                    throw new IOException(ruta + ", línea " + numeroLinea
                            + ": se esperaban 4 campos y hay " + campos.length + ".");
                }
                try {
                    String codSolicitud = EscrituraCsv.campoALeer(campos[0]);
                    Solicitud solicitud = buscarEn(leidos, codSolicitud);
                    if (solicitud == null) {
                        throw new IOException(ruta + ", línea " + numeroLinea
                                + ": la solicitud referenciada no existe.");
                    }
                    Oferta oferta = new Oferta(fechaHora(campos[1]),
                            EscrituraCsv.campoALeer(campos[2]),
                            "true".equals(EscrituraCsv.campoALeer(campos[3])));
                    solicitud.registrarOferta(oferta);
                } catch (IOException e) {
                    throw e;
                } catch (NumberFormatException | DateTimeParseException
                        | ReglaDominioException e) {
                    throw new IOException(ruta + ", línea " + numeroLinea + ": "
                            + e.getMessage());
                }
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

    private static LocalDate fechaOpcional(String campo) {
        String valor = EscrituraCsv.campoALeer(campo);
        return valor == null ? null : LocalDate.parse(valor);
    }

    private static EstadoDocumento estadoDocumentoOpcional(String campo) {
        String valor = EscrituraCsv.campoALeer(campo);
        return valor == null ? null : EstadoDocumento.valueOf(valor);
    }

    private static String fechaHora(LocalDateTime fecha) {
        return fecha == null ? null : fecha.toString();
    }

    private static int numeroDe(String codigo) {
        if (codigo == null || !codigo.startsWith("SOL-")) {
            return -1;
        }
        try {
            return Integer.parseInt(codigo.substring(4));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static Solicitud buscarEn(ArrayList<Solicitud> lista, String codigo) {
        for (Solicitud solicitud : lista) {
            if (solicitud.getCodigo().equals(codigo)) {
                return solicitud;
            }
        }
        return null;
    }

    private static ArrayList<Solicitud> ordenarPorIngreso(ArrayList<Solicitud> enCola) {
        ArrayList<Solicitud> cola = new ArrayList<Solicitud>();
        for (Solicitud solicitud : enCola) {
            int posicion = cola.size();
            while (posicion > 0 && cola.get(posicion - 1).getFechaIngresoCola()
                    .isAfter(solicitud.getFechaIngresoCola())) {
                posicion--;
            }
            cola.add(posicion, solicitud);
        }
        return cola;
    }
}
