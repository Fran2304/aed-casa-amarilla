package datos;

import java.time.LocalDateTime;
import java.util.ArrayList;

import modelo.ConceptoPago;
import modelo.DatoInvalidoException;
import modelo.Matricula;
import modelo.MedioPago;
import modelo.Pago;
import modelo.Solicitud;

public class ArregloPagos {

    private ArrayList<Pago> pagos;

    public ArregloPagos() {
        pagos = new ArrayList<Pago>();
    }

    public Pago registrar(ConceptoPago concepto, double montoAplicado, MedioPago medio,
            LocalDateTime fechaOperacion, String numeroOperacion, String comprobante,
            Solicitud solicitud, Matricula matricula) throws DatoInvalidoException {
        Pago nuevo = new Pago(concepto, montoAplicado, medio, fechaOperacion,
                numeroOperacion, comprobante, solicitud, matricula);
        pagos.add(nuevo);
        return nuevo;
    }

    public ArrayList<Pago> listar() {
        return new ArrayList<Pago>(pagos);
    }

    public ArrayList<Pago> listarPorSolicitud(Solicitud solicitud) {
        ArrayList<Pago> resultado = new ArrayList<Pago>();
        for (Pago pago : pagos) {
            if (pago.getSolicitud() == solicitud) {
                resultado.add(pago);
            }
        }
        return resultado;
    }
}
