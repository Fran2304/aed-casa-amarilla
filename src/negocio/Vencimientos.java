package negocio;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;

import datos.ArregloAulas;
import datos.ArregloPagos;
import datos.ArregloSolicitudes;
import modelo.ConfiguracionCuotas;
import modelo.DatoInvalidoException;
import modelo.Documento;
import modelo.EstadoMatricula;
import modelo.EstadoSolicitud;
import modelo.Matricula;
import modelo.ReglaDominioException;
import modelo.Solicitud;
import util.Reloj;

public final class Vencimientos {

    public static final String RESPONSABLE = "Motor de vencimientos";
    private static final int HORAS_PROXIMOS = 24;

    private Vencimientos() {
    }

    public static void barrer(Reloj reloj, ArregloAulas aulas, ArregloSolicitudes solicitudes,
            ArrayList<Matricula> matriculas, ArregloPagos pagos, ConfiguracionCuotas cuotas)
            throws ReglaDominioException {
        if (reloj == null || solicitudes == null || matriculas == null || pagos == null) {
            throw new DatoInvalidoException("Los datos del motor de vencimientos son obligatorios.");
        }
        LocalDateTime ahora = reloj.now();
        solicitudes.vencerHabilitaciones(ahora);
        solicitudes.cancelarDocumentacionVencida(reloj.fechaActual(), RESPONSABLE);
        barrerMatriculas(ahora, solicitudes, matriculas, pagos);
    }

    static void barrerMatriculas(LocalDateTime ahora, ArregloSolicitudes solicitudes,
            ArrayList<Matricula> matriculas, ArregloPagos pagos) throws ReglaDominioException {
        for (Matricula matricula : new ArrayList<Matricula>(matriculas)) {
            if (matricula.getEstado() != EstadoMatricula.PENDIENTE_PAGO
                    || !matricula.pagoOriginalVencido(ahora)
                    || pagos.matriculaConfirmada(matricula)) {
                continue;
            }
            Cobros.cancelarMatriculaVencida(matricula.getSolicitud(), matricula, ahora, solicitudes);
        }
    }

    static void barrerMatriculas(LocalDateTime ahora, ArregloSolicitudes solicitudes,
            ArrayList<Matricula> matriculas) throws ReglaDominioException {
        for (Matricula matricula : new ArrayList<Matricula>(matriculas)) {
            if (matricula.getEstado() == EstadoMatricula.PENDIENTE_PAGO
                    && matricula.pagoOriginalVencido(ahora)) {
                Cobros.cancelarMatriculaVencida(matricula.getSolicitud(), matricula, ahora, solicitudes);
            }
        }
    }

    public static ArrayList<Proximo> proximos(Reloj reloj, ArregloSolicitudes solicitudes,
            ArrayList<Matricula> matriculas) throws ReglaDominioException {
        if (reloj == null || solicitudes == null || matriculas == null) {
            throw new DatoInvalidoException("Los datos de consulta son obligatorios.");
        }
        LocalDateTime ahora = reloj.now();
        LocalDateTime limite = ahora.plusHours(HORAS_PROXIMOS);
        ArrayList<Proximo> resultado = new ArrayList<Proximo>();
        for (Solicitud solicitud : solicitudes.listar()) {
            if (solicitud.getEstado() == EstadoSolicitud.HABILITADA_PARA_PAGO) {
                agregarSiDentro(resultado, solicitud, "HABILITACION", solicitud.getVencimientoHabilitacion(),
                        ahora, limite);
            }
            if (solicitud.getEstado() == EstadoSolicitud.EN_DOCUMENTACION
                    && solicitud.getExpediente() != null) {
                if (!solicitud.getExpediente().entregaOportuna()) {
                    agregarPlazoDocumental(resultado, solicitud, "ENTREGA_DOCUMENTOS",
                            solicitud.getExpediente().fechaLimiteEntrega(), ahora, limite);
                }
                for (Documento documento : solicitud.getExpediente().getDocumentos()) {
                    if (documento.fechaLimiteCorreccion() != null) {
                        agregarPlazoDocumental(resultado, solicitud, "CORRECCION_DOCUMENTO",
                                documento.fechaLimiteCorreccion(), ahora, limite);
                    }
                }
            }
        }
        for (Matricula matricula : matriculas) {
            if (matricula.getEstado() == EstadoMatricula.PENDIENTE_PAGO) {
                agregarSiDentro(resultado, matricula.getSolicitud(), "MATRICULA",
                        matricula.getVencimientoPagoOriginal(), ahora, limite);
            }
        }
        return resultado;
    }

    private static void agregarSiDentro(ArrayList<Proximo> resultado, Solicitud solicitud,
            String tipo, LocalDateTime vencimiento, LocalDateTime ahora, LocalDateTime limite) {
        if (vencimiento != null && vencimiento.isAfter(ahora) && !vencimiento.isAfter(limite)) {
            resultado.add(new Proximo(solicitud.getCodigo(), solicitud.getAlumno().getCodAlumno(),
                    tipo, vencimiento));
        }
    }

    private static void agregarPlazoDocumental(ArrayList<Proximo> resultado, Solicitud solicitud,
            String tipo, LocalDate fechaLimite, LocalDateTime ahora, LocalDateTime limite) {
        if (fechaLimite.isBefore(ahora.toLocalDate())) {
            return;
        }
        LocalDateTime finDelDia = fechaLimite.atTime(LocalTime.MAX);
        if (finDelDia.isAfter(ahora) && !finDelDia.isAfter(limite)) {
            resultado.add(new Proximo(solicitud.getCodigo(), solicitud.getAlumno().getCodAlumno(),
                    tipo, finDelDia));
        }
    }

    public static final class Proximo {
        private final String codigoSolicitud;
        private final int codigoAlumno;
        private final String tipo;
        private final LocalDateTime vencimiento;

        private Proximo(String codigoSolicitud, int codigoAlumno, String tipo,
                LocalDateTime vencimiento) {
            this.codigoSolicitud = codigoSolicitud;
            this.codigoAlumno = codigoAlumno;
            this.tipo = tipo;
            this.vencimiento = vencimiento;
        }

        public String getCodigoSolicitud() { return codigoSolicitud; }
        public int getCodigoAlumno() { return codigoAlumno; }
        public String getTipo() { return tipo; }
        public LocalDateTime getVencimiento() { return vencimiento; }
    }
}
