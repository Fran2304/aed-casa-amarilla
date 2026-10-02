package datos;

import java.util.ArrayList;

import modelo.Apoderado;
import modelo.ApoderadoDuplicadoException;
import modelo.ReglaDominioException;
import modelo.Validaciones;

public class ArregloApoderados {

    private ArrayList<Apoderado> apoderados;

    public ArregloApoderados() {
        apoderados = new ArrayList<Apoderado>();
    }

    public Apoderado registrar(String dni, String nombres, String apellidos, String celular)
            throws ReglaDominioException {
        String dniNormalizado = Validaciones.exigirDni(dni);
        Apoderado existente = buscar(dniNormalizado);
        if (existente != null) {
            throw new ApoderadoDuplicadoException(existente);
        }
        Apoderado nuevo = new Apoderado(dniNormalizado, nombres, apellidos, celular);
        apoderados.add(nuevo);
        return nuevo;
    }

    public Apoderado buscar(String dni) {
        String buscado = Validaciones.normalizarDni(dni);
        if (buscado == null) {
            return null;
        }
        for (Apoderado apoderado : apoderados) {
            if (apoderado.getDni().equals(buscado)) {
                return apoderado;
            }
        }
        return null;
    }

    public int cantidad() {
        return apoderados.size();
    }

    public ArrayList<Apoderado> listar() {
        return new ArrayList<Apoderado>(apoderados);
    }
}
