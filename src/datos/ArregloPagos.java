package datos;

import java.util.ArrayList;

import modelo.ConceptoPago;
import modelo.DatoInvalidoException;
import modelo.Pago;
import modelo.Solicitud;
import modelo.Matricula;

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

    public boolean matriculaConfirmada(Matricula matricula) {
        for (Pago pago : pagos) {
            if (pago.getConcepto() == ConceptoPago.MATRICULA
                    && pago.getMatricula() == matricula && pago.estaConfirmado()) {
                return true;
            }
        }
        return false;
    }

    public boolean contiene(Pago pago) {
        return pagos.contains(pago);
    }

    public boolean otroConfirmado(Pago pago) {
        for (Pago otro : pagos) {
            if (otro != pago && otro.estaConfirmado()
                    && otro.getConcepto() == pago.getConcepto()
                    && otro.getSolicitud() == pago.getSolicitud()
                    && otro.getMatricula() == pago.getMatricula()) {
                return true;
            }
        }
        return false;
    }

    public ArrayList<Pago> listar() {
        return new ArrayList<Pago>(pagos);
    }
}
