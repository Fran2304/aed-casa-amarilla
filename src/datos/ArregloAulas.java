package datos;

import java.util.ArrayList;

import modelo.Aula;

public class ArregloAulas {

    private ArrayList<Aula> aulas;

    public ArregloAulas() {
        aulas = new ArrayList<Aula>();
        // Aulas de ejemplo del diseño. La tabla completa sigue pendiente (§6.2 del flujo).
        aulas.add(new Aula("AUL-01", "Girasoles", 4, 20));
        aulas.add(new Aula("AUL-02", "Tulipanes", 3, 18));
    }

    public ArrayList<Aula> listar() {
        return aulas;
    }

    public Aula buscar(String codigo) {
        for (Aula aula : aulas) {
            if (aula.getCodigo().equals(codigo)) {
                return aula;
            }
        }
        return null;
    }
}
