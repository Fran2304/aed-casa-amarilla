package datos;

import java.time.LocalDateTime;
import java.util.ArrayList;

import modelo.ConceptoPago;
import modelo.DatoInvalidoException;
import modelo.Matricula;
import modelo.MedioPago;
import modelo.Pago;

public class ArregloPagos {

    private ArrayList<Pago> pagos;

    public ArregloPagos() {
        pagos = new ArrayList<Pago>();
    }

    public Pago registrar(ConceptoPago concepto, double montoAplicado, MedioPago medio,
            LocalDateTime fechaOperacion, String numeroOperacion, String comprobante,
            String codigoSolicitud, Matricula matricula) throws DatoInvalidoException {
        Pago nuevo = new Pago(concepto, montoAplicado, medio, fechaOperacion,
                numeroOperacion, comprobante, codigoSolicitud, matricula);
        pagos.add(nuevo);
        return nuevo;
    }

    public ArrayList<Pago> listar() {
        return new ArrayList<Pago>(pagos);
    }

    public ArrayList<Pago> listarPorSolicitud(String codigoSolicitud) {
        ArrayList<Pago> resultado = new ArrayList<Pago>();
        for (Pago pago : pagos) {
            if (pago.getCodigoSolicitud().equals(codigoSolicitud)) {
                resultado.add(pago);
            }
        }
        return resultado;
    }
}
