package datos;

import java.util.ArrayList;

import modelo.ConceptoPago;
import modelo.DatoInvalidoException;
import modelo.Matricula;
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
            if (pago.getConcepto() == ConceptoPago.INSCRIPCION && pago.estaConfirmado()
                    && !pago.estaAnulado()) {
                return true;
            }
        }
        return false;
    }

    /** Pago de matrícula vigente de la matrícula dada, o {@code null} si no hay. */
    public Pago pagoMatriculaConfirmado(Matricula matricula) {
        for (Pago pago : pagos) {
            if (pago.getConcepto() == ConceptoPago.MATRICULA
                    && pago.getMatricula() == matricula && pago.estaConfirmado()
                    && !pago.estaAnulado()) {
                return pago;
            }
        }
        return null;
    }

    public ArrayList<Pago> listar() {
        return new ArrayList<Pago>(pagos);
    }
}
