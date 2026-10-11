package negocio;

import java.time.LocalDateTime;
import java.util.ArrayList;

import datos.ArregloMatriculas;
import datos.ArregloSolicitudes;
import modelo.DatoInvalidoException;
import modelo.EstadoMatricula;
import modelo.EstadoSolicitud;
import modelo.Matricula;
import modelo.ReglaDominioException;
import modelo.Solicitud;

public final class Vencimientos {

    public static final String RESPONSABLE = "Sistema";

    public static final String MOTIVO_VENCIMIENTO = "Vencimiento de 72 h sin pago registrado";

    private Vencimientos() {
    }

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
            solicitudes.cancelarDocumentacionVencida(ahora.toLocalDate(), RESPONSABLE);
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
