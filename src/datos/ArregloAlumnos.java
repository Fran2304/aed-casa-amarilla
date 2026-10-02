package datos;

import java.time.LocalDate;
import java.util.ArrayList;

import modelo.Alumno;
import modelo.AlumnoDuplicadoException;
import modelo.ReglaDominioException;

public class ArregloAlumnos {

    private static final int PRIMER_CODIGO = 202010001;

    private ArrayList<Alumno> alumnos;
    private int siguienteCodigo;

    public ArregloAlumnos() {
        alumnos = new ArrayList<Alumno>();
        siguienteCodigo = PRIMER_CODIGO;
    }

    public Alumno registrar(String dni, String nombres, String apellidos,
            LocalDate fechaNacimiento, String celular) throws ReglaDominioException {
        Alumno candidato = new Alumno(siguienteCodigo, dni, nombres, apellidos,
                fechaNacimiento, celular);
        Alumno existente = buscarPorDni(candidato.getDni());
        if (existente != null) {
            throw new AlumnoDuplicadoException(existente);
        }
        alumnos.add(candidato);
        siguienteCodigo++;
        return candidato;
    }

    public Alumno buscar(int codAlumno) {
        for (Alumno alumno : alumnos) {
            if (alumno.getCodAlumno() == codAlumno) {
                return alumno;
            }
        }
        return null;
    }

    public Alumno buscarPorDni(String dni) {
        if (dni == null) {
            return null;
        }
        String buscado = dni.trim();
        for (Alumno alumno : alumnos) {
            if (alumno.getDni().equals(buscado)) {
                return alumno;
            }
        }
        return null;
    }

    public int cantidad() {
        return alumnos.size();
    }

    public int getSiguienteCodigo() {
        return siguienteCodigo;
    }

    public ArrayList<Alumno> listar() {
        return new ArrayList<Alumno>(alumnos);
    }
}
