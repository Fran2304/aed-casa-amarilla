package negocio;

import java.time.LocalDateTime;

import datos.ArregloMatriculas;
import datos.ArregloSolicitudes;
import modelo.DatoInvalidoException;
import modelo.Matricula;
import modelo.ReglaDominioException;
import modelo.Solicitud;

public final class Matriculas {

    private Matriculas() {
    }

    // Todo se valida antes de crear (§4.6): un rechazo no consume número, no reserva vacante
    // ni modifica otras solicitudes.
    public static Matricula crear(Solicitud solicitud, LocalDateTime fechaHora,
            ArregloSolicitudes solicitudes, ArregloMatriculas matriculas)
            throws ReglaDominioException {
        if (solicitud == null) {
            throw new DatoInvalidoException("La solicitud es obligatoria.");
        }
        if (fechaHora == null) {
            throw new DatoInvalidoException("La fecha y hora de creación es obligatoria.");
        }
        if (solicitudes == null || matriculas == null) {
            throw new DatoInvalidoException("Las solicitudes y matrículas son obligatorias.");
        }

        Matricula vigente = matriculas.buscarVigenteDe(solicitud.getAlumno());
        if (vigente != null) {
            throw new ReglaDominioException(solicitud.getAlumno().getNombreCompleto()
                    + " ya tiene la matrícula " + vigente.getNumMatricula() + " en "
                    + vigente.getEstado() + ".");
        }
        // EN_ESPERA_FAVORABLE es "documentos validados y entrevista favorable" en la spec.
        if (!solicitud.estaEnColaFavorable()) {
            throw new ReglaDominioException(solicitud.getCodigo() + " está en "
                    + solicitud.getEstado() + ": la matrícula exige documentos validados y"
                    + " entrevista favorable.");
        }
        if (solicitud.getExpediente() == null) {
            throw new ReglaDominioException(solicitud.getCodigo()
                    + " no tiene expediente de documentos.");
        }
        solicitud.getExpediente().exigirDocumentosValidados();
        if (!solicitud.tieneOfertaAceptada()) {
            throw new ReglaDominioException(solicitud.getCodigo()
                    + " no tiene una oferta aceptada por el apoderado.");
        }
        Turnos.comprobarTurno(solicitud, solicitudes, matriculas.listar());

        Matricula nueva = new Matricula(matriculas.getSiguienteNumero(), solicitud, fechaHora);
        matriculas.agregar(nueva);
        return nueva;
    }
}
