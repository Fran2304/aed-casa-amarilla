package datos;

import java.util.ArrayList;

import modelo.Alumno;
import modelo.DatoInvalidoException;
import modelo.Matricula;

public class ArregloMatriculas {

    private static final int PRIMER_NUMERO = 100001;

    private ArrayList<Matricula> matriculas;
    private int siguienteNumero;

    public ArregloMatriculas() {
        matriculas = new ArrayList<Matricula>();
        siguienteNumero = PRIMER_NUMERO;
    }

    public int getSiguienteNumero() {
        return siguienteNumero;
    }

    public void agregar(Matricula matricula) throws DatoInvalidoException {
        if (matricula == null) {
            throw new DatoInvalidoException("La matrícula es obligatoria.");
        }
        if (matricula.getNumMatricula() != siguienteNumero) {
            throw new DatoInvalidoException("La matrícula debe llevar el número correlativo "
                    + siguienteNumero + ".");
        }
        matriculas.add(matricula);
        siguienteNumero++;
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
