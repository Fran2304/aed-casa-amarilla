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
        if (buscarDe(solicitud) != null) {
            throw new ReglaDominioException(
                    "La entrevista de " + solicitud.getCodigo() + " ya está agendada.");
        }
        Entrevista nueva = new Entrevista(solicitud, expediente, fechaHora, personal,
                responsable);
        entrevistas.add(nueva);
        return nueva;
    }

    public Entrevista buscarDe(Solicitud solicitud) {
        for (Entrevista entrevista : entrevistas) {
            if (entrevista.getSolicitud() == solicitud
                    && entrevista.getExpediente() == solicitud.getExpediente()) {
                return entrevista;
            }
        }
        return null;
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
