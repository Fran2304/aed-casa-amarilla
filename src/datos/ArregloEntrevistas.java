package datos;

import java.time.LocalDateTime;
import java.util.ArrayList;

import modelo.Entrevista;
import modelo.ReglaDominioException;
import modelo.Solicitud;
import negocio.ExpedienteDocumentos;

public class ArregloEntrevistas {

    private ArrayList<Entrevista> entrevistas;

    public ArregloEntrevistas() {
        entrevistas = new ArrayList<Entrevista>();
    }

    public Entrevista agendar(Solicitud solicitud, ExpedienteDocumentos expediente,
            LocalDateTime fechaHora, ArrayList<String> personal, String responsable)
            throws ReglaDominioException {
        Entrevista nueva = new Entrevista(solicitud, expediente, fechaHora, personal,
                responsable);
        entrevistas.add(nueva);
        return nueva;
    }

    public ArrayList<Entrevista> listar() {
        return new ArrayList<Entrevista>(entrevistas);
    }

    public ArrayList<Entrevista> listarPorSolicitud(String codigoSolicitud) {
        ArrayList<Entrevista> resultado = new ArrayList<Entrevista>();
        for (Entrevista entrevista : entrevistas) {
            if (entrevista.getSolicitud().getCodigo().equals(codigoSolicitud)) {
                resultado.add(entrevista);
            }
        }
        return resultado;
    }
}
