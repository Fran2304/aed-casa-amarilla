package negocio;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;

import datos.ArregloAulas;
import datos.ArregloMatriculas;
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

    // La matrícula la audita "Motor de vencimientos" (§4.7); la documentación conserva
    // el responsable "Sistema" que ya usaba PanelExpediente (issue #23 no lo cambió).
    public static final String RESPONSABLE = "Motor de vencimientos";
    public static final String RESPONSABLE_DOCUMENTACION = "Sistema";
    public static final String MOTIVO_VENCIMIENTO = "Vencimiento de 72 h sin pago registrado";
    private static final int HORAS_PROXIMOS = 24;

    private Vencimientos() {
    }

    /** Barrido global al arrancar y en los bordes pre-vacantes, con el reloj inyectable. */
    public static void barrer(Reloj reloj, ArregloAulas aulas, ArregloSolicitudes solicitudes,
            ArregloMatriculas matriculas, ArregloPagos pagos, ConfiguracionCuotas cuotas)
            throws ReglaDominioException {
        if (reloj == null || solicitudes == null || matriculas == null || pagos == null) {
            throw new DatoInvalidoException("Los datos del motor de vencimientos son obligatorios.");
        }
        LocalDateTime ahora = reloj.now();
        solicitudes.vencerHabilitaciones(ahora);
        solicitudes.cancelarDocumentacionVencida(reloj.fechaActual(), RESPONSABLE_DOCUMENTACION);
        barrerMatriculas(ahora, solicitudes, matriculas, pagos);
    }

    static void barrerMatriculas(LocalDateTime ahora, ArregloSolicitudes solicitudes,
            ArregloMatriculas matriculas, ArregloPagos pagos) throws ReglaDominioException {
        for (Matricula matricula : matriculas.listar()) {
            if (matricula.getEstado() != EstadoMatricula.PENDIENTE_PAGO
                    || !matricula.pagoOriginalVencido(ahora)
                    || pagos.matriculaConfirmada(matricula)) {
                continue;
            }
            Cobros.cancelarMatriculaVencida(matricula.getSolicitud(), matricula, ahora, solicitudes);
        }
    }

    /** Consulta de negocio de los vencimientos dentro de las próximas 24 horas. */
    public static ArrayList<Proximo> proximos(Reloj reloj, ArregloSolicitudes solicitudes,
            ArregloMatriculas matriculas) throws ReglaDominioException {
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
        for (Matricula matricula : matriculas.listar()) {
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

    /**
     * Revisión puntual en los bordes de negocio (pago, turno, oferta): barre los
     * vencimientos y devuelve avisos en vez de lanzar, para que la pantalla los muestre.
     */
    public static ArrayList<String> revisar(LocalDateTime ahora, ArregloSolicitudes solicitudes,
            ArregloMatriculas matriculas) throws DatoInvalidoException {
        if (ahora == null) {
            throw new DatoInvalidoException("La fecha de revisión es obligatoria.");
        }
        ArrayList<String> avisos = new ArrayList<String>();
        cancelarMatriculasVencidas(ahora, solicitudes, matriculas, avisos);
        try {
            solicitudes.vencerHabilitaciones(ahora);
        } catch (ReglaDominioException e) {
            avisos.add(e.getMessage());
        }
        try {
            solicitudes.cancelarDocumentacionVencida(ahora.toLocalDate(), RESPONSABLE_DOCUMENTACION);
        } catch (ReglaDominioException e) {
            avisos.add(e.getMessage());
        }
        return avisos;
    }

    public static String unir(ArrayList<String> avisos) {
        return String.join("\n", avisos);
    }

    private static void cancelarMatriculasVencidas(LocalDateTime ahora,
            ArregloSolicitudes solicitudes, ArregloMatriculas matriculas,
            ArrayList<String> avisos) {
        for (Matricula matricula : matriculas.listar()) {
            if (matricula.getEstado() == EstadoMatricula.PENDIENTE_PAGO
                    && matricula.pagoOriginalVencido(ahora)) {
                try {
                    cancelarVencida(matricula, ahora, solicitudes);
                } catch (ReglaDominioException e) {
                    avisos.add(matricula.getCodigo() + ": " + e.getMessage());
                }
            }
        }
    }

    // Se reingresa con la fecha del barrido y no con la del vencimiento: con la app
    // cerrada el barrido puede correr días después, y la solicitud debe quedar al final.
    private static void cancelarVencida(Matricula matricula, LocalDateTime ahora,
            ArregloSolicitudes solicitudes) throws ReglaDominioException {
        Solicitud solicitud = matricula.getSolicitud();
        if (solicitud.getEstado() != EstadoSolicitud.EN_ESPERA_FAVORABLE) {
            throw new ReglaDominioException(solicitud.getCodigo() + " está en "
                    + solicitud.getEstado() + " y no puede volver a la cola favorable;"
                    + " la matrícula sigue PENDIENTE_PAGO.");
        }
        LocalDateTime fechaCola = Turnos.fechaAlFinalFavorable(solicitud, ahora, solicitudes);
        matricula.cancelar(ahora, MOTIVO_VENCIMIENTO, RESPONSABLE);
        solicitud.ingresarAColaFavorable(fechaCola);
    }
}
