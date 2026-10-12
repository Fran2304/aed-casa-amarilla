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
        return desglose(aula, matriculas).getDisponibles();
    }

    public static Desglose desglose(Aula aula, ArregloMatriculas matriculas) {
        int pendientes = 0;
        int activas = 0;
        for (Matricula matricula : matriculas.listar()) {
            if (matricula.getAula() != aula) {
                continue;
            }
            if (matricula.getEstado() == EstadoMatricula.PENDIENTE_PAGO) {
                pendientes++;
            } else if (matricula.getEstado() == EstadoMatricula.ACTIVA) {
                activas++;
            }
        }
        return new Desglose(aula.getCapacidad(), pendientes, activas);
    }

    public static final class Desglose {
        private final int capacidad;
        private final int pendientes;
        private final int activas;

        private Desglose(int capacidad, int pendientes, int activas) {
            this.capacidad = capacidad;
            this.pendientes = pendientes;
            this.activas = activas;
        }

        public int getCapacidad() { return capacidad; }
        public int getPendientes() { return pendientes; }
        public int getActivas() { return activas; }
        public int getDisponibles() { return capacidad - pendientes - activas; }
    }
}
