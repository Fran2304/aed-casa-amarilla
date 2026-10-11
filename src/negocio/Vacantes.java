package negocio;

import datos.ArregloMatriculas;
import modelo.Aula;
import modelo.EstadoMatricula;
import modelo.Matricula;

/** Las vacantes no se guardan: se recalculan siempre desde la capacidad y las matrículas. */
public final class Vacantes {

    private Vacantes() {
    }

    public static int calcular(Aula aula, ArregloMatriculas matriculas) {
        int pendientes = contar(aula, matriculas, EstadoMatricula.PENDIENTE_PAGO);
        int activas = contar(aula, matriculas, EstadoMatricula.ACTIVA);
        return aula.getCapacidad() - pendientes - activas;
    }

    private static int contar(Aula aula, ArregloMatriculas matriculas, EstadoMatricula estado) {
        int cantidad = 0;
        for (Matricula matricula : matriculas.listar()) {
            if (matricula.getAula() == aula && matricula.getEstado() == estado) {
                cantidad++;
            }
        }
        return cantidad;
    }
}
