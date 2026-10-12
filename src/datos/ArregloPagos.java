package datos;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;

import modelo.ConceptoPago;
import modelo.DatoInvalidoException;
import modelo.EstadoPago;
import modelo.Matricula;
import modelo.MedioPago;
import modelo.Pago;
import modelo.ReglaDominioException;
import modelo.Solicitud;

public class ArregloPagos {

    private ArrayList<Pago> pagos;

    public ArregloPagos() {
        pagos = new ArrayList<Pago>();
    }

    public void agregar(Pago pago) throws DatoInvalidoException {
        if (pago == null) {
            throw new DatoInvalidoException("El pago es obligatorio.");
        }
        pagos.add(pago);
    }

    public ArrayList<Pago> deSolicitud(Solicitud solicitud) {
        ArrayList<Pago> deSolicitud = new ArrayList<Pago>();
        for (Pago pago : pagos) {
            if (pago.getSolicitud() == solicitud) {
                deSolicitud.add(pago);
            }
        }
        return deSolicitud;
    }

    public boolean inscripcionConfirmada(Solicitud solicitud) {
        return inscripcionConfirmadaDe(solicitud) != null;
    }

    public Pago inscripcionConfirmadaDe(Solicitud solicitud) {
        for (Pago pago : deSolicitud(solicitud)) {
            if (pago.getConcepto() == ConceptoPago.INSCRIPCION && pago.estaConfirmado()) {
                return pago;
            }
        }
        return null;
    }

    public boolean matriculaConfirmada(Matricula matricula) {
        return matriculaConfirmadaDe(matricula) != null;
    }

    public Pago matriculaConfirmadaDe(Matricula matricula) {
        for (Pago pago : pagos) {
            if (pago.getConcepto() == ConceptoPago.MATRICULA
                    && pago.getMatricula() == matricula && pago.estaConfirmado()) {
                return pago;
            }
        }
        return null;
    }

    public boolean contiene(Pago pago) {
        return pagos.contains(pago);
    }

    public boolean otroConfirmado(Pago pago) {
        for (Pago otro : pagos) {
            if (otro != pago && otro.estaConfirmado()
                    && otro.getConcepto() == pago.getConcepto()
                    && otro.getSolicitud() == pago.getSolicitud()
                    && otro.getMatricula() == pago.getMatricula()) {
                return true;
            }
        }
        return false;
    }

    public ArrayList<Pago> listar() {
        return new ArrayList<Pago>(pagos);
    }

    public void grabar(String ruta) throws IOException {
        ArrayList<String> lineas = new ArrayList<String>();
        for (Pago pago : pagos) {
            lineas.add(EscrituraCsv.unir(
                    pago.getSolicitud().getCodigo(),
                    pago.getConcepto().name(),
                    pago.getMatricula() == null ? null
                            : String.valueOf(pago.getMatricula().getNumMatricula()),
                    String.valueOf(pago.getMontoPagado()),
                    pago.getMedio().name(),
                    pago.getNumeroOperacion(),
                    fechaHora(pago.getFechaHoraOperacion()),
                    fechaHora(pago.getFechaHoraRegistro()),
                    pago.getRutaComprobantePago(),
                    pago.getComprobante(),
                    pago.getEstado().name(),
                    fechaHora(pago.getFechaAnulacion()),
                    pago.getMotivoAnulacion(),
                    pago.getResponsableAnulacion()));
        }
        EscrituraCsv.escribir(ruta, lineas);
    }

    public void cargar(String ruta, ArregloSolicitudes solicitudes,
            ArregloMatriculas matriculas) throws IOException {
        ArrayList<Pago> leidos = new ArrayList<Pago>();
        if (!new File(ruta).exists()) {
            pagos = leidos;
            return;
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
                    Solicitud solicitud = solicitudes.buscar(EscrituraCsv.campoALeer(campos[0]));
                    ConceptoPago concepto = ConceptoPago.valueOf(EscrituraCsv.desescapar(campos[1]));
                    Matricula matricula = leerMatricula(campos[2], matriculas, concepto,
                            ruta, numeroLinea);
                    double montoPagado = Double.parseDouble(EscrituraCsv.desescapar(campos[3]));
                    MedioPago medio = MedioPago.valueOf(EscrituraCsv.desescapar(campos[4]));
                    String numeroOperacion = EscrituraCsv.campoALeer(campos[5]);
                    LocalDateTime fechaHoraOperacion = fechaHora(campos[6]);
                    LocalDateTime fechaHoraRegistro = fechaHora(campos[7]);
                    String rutaComprobantePago = EscrituraCsv.campoALeer(campos[8]);
                    String comprobante = EscrituraCsv.campoALeer(campos[9]);
                    EstadoPago estado = EstadoPago.valueOf(EscrituraCsv.desescapar(campos[10]));
                    LocalDateTime fechaAnulacion = fechaHoraOpcional(campos[11]);
                    String motivoAnulacion = EscrituraCsv.campoALeer(campos[12]);
                    String responsableAnulacion = EscrituraCsv.campoALeer(campos[13]);
                    if (solicitud == null) {
                        throw new IOException(ruta + ", línea " + numeroLinea
                                + ": la solicitud referenciada no existe.");
                    }
                    leidos.add(Pago.reconstruir(solicitud, concepto, matricula, montoPagado,
                            medio, numeroOperacion, fechaHoraOperacion, fechaHoraRegistro,
                            rutaComprobantePago, comprobante, estado, fechaAnulacion,
                            motivoAnulacion, responsableAnulacion));
                } catch (IOException e) {
                    throw e;
                } catch (IllegalArgumentException | DateTimeParseException
                        | ReglaDominioException e) {
                    throw new IOException(ruta + ", línea " + numeroLinea + ": "
                            + e.getMessage());
                }
            }
        }
        pagos = leidos;
    }

    private Matricula leerMatricula(String campo, ArregloMatriculas matriculas,
            ConceptoPago concepto, String ruta, int numeroLinea) throws IOException {
        String valor = EscrituraCsv.campoALeer(campo);
        if (valor == null) {
            if (concepto == ConceptoPago.MATRICULA) {
                throw new IOException(ruta + ", línea " + numeroLinea
                        + ": el pago de MATRICULA exige numMatricula.");
            }
            return null;
        }
        Matricula matricula = matriculas.buscar(Integer.parseInt(valor));
        if (matricula == null) {
            throw new IOException(ruta + ", línea " + numeroLinea
                    + ": la matrícula referenciada no existe.");
        }
        if (concepto == ConceptoPago.INSCRIPCION) {
            throw new IOException(ruta + ", línea " + numeroLinea
                    + ": el pago de INSCRIPCION no debe relacionarse con una matrícula.");
        }
        return matricula;
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
}
