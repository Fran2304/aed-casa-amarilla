package datos;

import java.util.ArrayList;

import modelo.ConceptoPago;
import modelo.DatoInvalidoException;
import modelo.Pago;
import modelo.Solicitud;

public class ArregloPagos {

    private ArrayList<Pago> pagos;

    public ArregloPagos() {
        pagos = new ArrayList<Pago>();
    }

    public void agregar(Pago pago) throws DatoInvalidoException {
        if (pago == null) {
            throw new DatoInvalidoException("El pago es obligatorio.");
        }
        pagos.add(pago);
    }

    public ArrayList<Pago> deSolicitud(Solicitud solicitud) {
        ArrayList<Pago> deSolicitud = new ArrayList<Pago>();
        for (Pago pago : pagos) {
            if (pago.getSolicitud() == solicitud) {
                deSolicitud.add(pago);
            }
        }
        return deSolicitud;
    }

    public boolean inscripcionConfirmada(Solicitud solicitud) {
        for (Pago pago : deSolicitud(solicitud)) {
            if (pago.getConcepto() == ConceptoPago.INSCRIPCION && pago.estaConfirmado()) {
                return true;
            }
        }
        return false;
    }

    public ArrayList<Pago> listar() {
        return new ArrayList<Pago>(pagos);
    }
}
