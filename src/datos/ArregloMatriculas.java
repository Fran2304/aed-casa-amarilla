package datos;

import java.time.LocalDateTime;
import java.util.ArrayList;

import modelo.Alumno;
import modelo.DatoInvalidoException;
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
