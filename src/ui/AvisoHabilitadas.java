package ui;

import java.time.LocalDateTime;
import java.util.ArrayList;

import modelo.Apoderado;
import modelo.EstadoSolicitud;
import modelo.Solicitud;
import modelo.Validaciones;
import util.FormatoFecha;
import util.Reloj;

/**
 * A quién llamar tras una habilitación automática. El colegio avisa por teléfono, fuera del
 * sistema, y las 48 h corren desde que se habilita: Solicitudes y Colas muestran la misma lista.
 */
final class AvisoHabilitadas {

    private AvisoHabilitadas() {
    }

    // Mientras el personal estuvo en otra pantalla, una habilitada pudo pagar o vencer: solo
    // se pide llamar a quienes todavía pueden pagar.
    static ArrayList<Solicitud> vigentes(ArrayList<Solicitud> habilitadas) {
        LocalDateTime ahora = Reloj.porDefecto().now();
        ArrayList<Solicitud> vigentes = new ArrayList<Solicitud>();
        for (Solicitud habilitada : habilitadas) {
            if (habilitada.getEstado() == EstadoSolicitud.HABILITADA_PARA_PAGO
                    && !habilitada.habilitacionVencida(ahora)) {
                vigentes.add(habilitada);
            }
        }
        return vigentes;
    }

    static String texto(String encabezado, ArrayList<Solicitud> habilitadas, boolean conAula) {
        String texto = encabezado;
        for (Solicitud habilitada : habilitadas) {
            Apoderado principal = habilitada.getAlumno().getPrincipal();
            texto += "<br>· " + habilitada.getCodigo() + " · "
                    + habilitada.getAlumno().getNombreCompleto();
            if (conAula) {
                texto += " · aula " + habilitada.getAula().getNombre();
            }
            if (principal != null) {
                texto += " · " + principal.getNombreCompleto() + " "
                        + celular(principal.getCelular());
            }
            texto += " · hasta " + FormatoFecha.diaHora(habilitada.getVencimientoHabilitacion());
        }
        return texto;
    }

    static String celular(String celular) {
        if (celular == null || celular.length() != Validaciones.LARGO_CELULAR) {
            return celular;
        }
        return celular.substring(0, 3) + " " + celular.substring(3, 6) + " "
                + celular.substring(6);
    }
}
