package datos;

import java.time.LocalDateTime;
import java.util.ArrayList;

import modelo.Alumno;
import modelo.DatoInvalidoException;
import modelo.Matricula;
import modelo.ReglaDominioException;
import modelo.Solicitud;
import negocio.ExpedienteDocumentos;
import negocio.Turnos;

public class ArregloMatriculas {

    private static final int PRIMER_NUMERO = 100001;

    private ArrayList<Matricula> matriculas;
    private int siguienteNumero;

    public ArregloMatriculas() {
        matriculas = new ArrayList<Matricula>();
        siguienteNumero = PRIMER_NUMERO;
    }

    public Matricula crear(Solicitud solicitud, ArregloSolicitudes solicitudes)
            throws ReglaDominioException {
        return crear(solicitud, LocalDateTime.now(), solicitudes);
    }

    // Todo se valida antes de crear (§4.6): un rechazo no consume número ni reserva vacante.
    public Matricula crear(Solicitud solicitud, LocalDateTime fechaHora,
            ArregloSolicitudes solicitudes) throws ReglaDominioException {
        if (solicitud == null) {
            throw new DatoInvalidoException("La solicitud es obligatoria.");
        }
        if (fechaHora == null) {
            throw new DatoInvalidoException("La fecha y hora de creación es obligatoria.");
        }
        if (solicitudes == null) {
            throw new DatoInvalidoException("Las solicitudes son obligatorias.");
        }

        Matricula vigente = buscarVigenteDe(solicitud.getAlumno());
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
        ExpedienteDocumentos expediente = solicitud.getExpediente();
        if (expediente == null || !expediente.puedeAgendarEntrevista()) {
            throw new ReglaDominioException(solicitud.getCodigo()
                    + " no tiene los cuatro documentos validados.");
        }
        if (!solicitud.tieneOfertaAceptada()) {
            throw new ReglaDominioException(solicitud.getCodigo()
                    + " no tiene una oferta aceptada por el apoderado.");
        }
        Turnos.exigirTurno(solicitud, solicitudes, matriculas, fechaHora);

        Matricula nueva = new Matricula(siguienteNumero, solicitud, fechaHora);
        matriculas.add(nueva);
        siguienteNumero++;
        return nueva;
    }

    public Matricula buscarVigenteDe(Alumno alumno) {
        for (Matricula matricula : matriculas) {
            boolean esDelAlumno = matricula.getSolicitud().getAlumno().getCodAlumno()
                    == alumno.getCodAlumno();
            if (esDelAlumno && matricula.estaVigente()) {
                return matricula;
            }
        }
        return null;
    }

    public Matricula buscar(int numMatricula) {
        for (Matricula matricula : matriculas) {
            if (matricula.getNumMatricula() == numMatricula) {
                return matricula;
            }
        }
        return null;
    }

    public ArrayList<Matricula> listar() {
        return new ArrayList<Matricula>(matriculas);
    }
}
