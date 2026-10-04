package datos;

import java.time.LocalDateTime;
import java.util.ArrayList;

import modelo.Alumno;
import modelo.Aula;
import modelo.DatoInvalidoException;
import modelo.EstadoSolicitud;
import modelo.ReglaDominioException;
import modelo.Solicitud;
import modelo.SolicitudDuplicadaException;
import util.FechasAdmision;

public class ArregloSolicitudes {

    private static final int PRIMER_NUMERO = 1001;

    private ArrayList<Solicitud> solicitudes;
    private int siguienteNumero;

    public ArregloSolicitudes() {
        solicitudes = new ArrayList<Solicitud>();
        siguienteNumero = PRIMER_NUMERO;
    }

    // Las validaciones van en el orden del flujo (§4.1): si fallan varias,
    // el personal ve primero la que corresponde corregir antes.
    public Solicitud registrar(Alumno alumno, Aula aula) throws ReglaDominioException {
        if (alumno == null) {
            throw new DatoInvalidoException("El alumno es obligatorio.");
        }
        if (aula == null) {
            throw new DatoInvalidoException("El aula solicitada es obligatoria.");
        }

        alumno.validarApoderados();

        // Supuesto: edad exacta = edad del aula. Los rangos de edad por aula siguen
        // pendientes de definición (§6.2); si el nido acepta rangos, se cambia aquí.
        int edad = alumno.edadAl(FechasAdmision.CORTE_2027);
        if (edad != aula.getEdadRequerida()) {
            throw new ReglaDominioException(alumno.getNombreCompleto() + " tendrá " + edad
                    + " años al 31/03/2027 y el aula " + aula.getNombre() + " es para "
                    + aula.getEdadRequerida() + " años.");
        }

        Solicitud activa = buscarActivaDe(alumno);
        if (activa != null) {
            throw new SolicitudDuplicadaException(activa);
        }

        Solicitud nueva = new Solicitud("SOL-" + siguienteNumero, alumno, aula,
                LocalDateTime.now());
        solicitudes.add(nueva);
        siguienteNumero++;
        return nueva;
    }

    public Solicitud buscarActivaDe(Alumno alumno) {
        for (Solicitud solicitud : solicitudes) {
            boolean esDelAlumno = solicitud.getAlumno().getCodAlumno() == alumno.getCodAlumno();
            if (esDelAlumno && solicitud.estaActiva()) {
                return solicitud;
            }
        }
        return null;
    }

    public Solicitud buscar(String codigo) {
        for (Solicitud solicitud : solicitudes) {
            if (solicitud.getCodigo().equals(codigo)) {
                return solicitud;
            }
        }
        return null;
    }

    public ArrayList<Solicitud> listar() {
        return new ArrayList<Solicitud>(solicitudes);
    }

    public ArrayList<Solicitud> colaSinPago(Aula aula) {
        ArrayList<Solicitud> enCola = new ArrayList<Solicitud>();
        for (Solicitud solicitud : solicitudes) {
            if (solicitud.getAula() == aula && solicitud.estaEnColaSinPago()) {
                enCola.add(solicitud);
            }
        }
        return ordenarPorIngreso(enCola);
    }

    // Ordenadas por fecha de registro: es el orden en que se agregaron al arreglo.
    public ArrayList<Solicitud> habilitadas(Aula aula) {
        ArrayList<Solicitud> habilitadas = new ArrayList<Solicitud>();
        for (Solicitud solicitud : solicitudes) {
            if (solicitud.getAula() == aula
                    && solicitud.getEstado() == EstadoSolicitud.HABILITADA_PARA_PAGO) {
                habilitadas.add(solicitud);
            }
        }
        return habilitadas;
    }

    // Pagaron la inscripción y siguen su trámite: no reservan vacante, pero conservan su lugar
    // para que la vacante no se vuelva a ofrecer mientras avanzan (§3.2).
    public ArrayList<Solicitud> enDocumentacion(Aula aula) {
        ArrayList<Solicitud> enDocumentacion = new ArrayList<Solicitud>();
        for (Solicitud solicitud : solicitudes) {
            if (solicitud.getAula() == aula
                    && solicitud.getEstado() == EstadoSolicitud.EN_DOCUMENTACION) {
                enDocumentacion.add(solicitud);
            }
        }
        return enDocumentacion;
    }

    // Sin temporizador: los vencimientos se procesan con fechas guardadas antes de usar las
    // colas. Se reingresa con la fecha en que venció, no con la de la revisión, para que quede
    // al final de la cola tal como estaba en ese momento.
    public void vencerHabilitaciones(LocalDateTime ahora) throws ReglaDominioException {
        if (ahora == null) {
            throw new DatoInvalidoException("La fecha de revisión es obligatoria.");
        }
        for (Solicitud solicitud : solicitudes) {
            if (solicitud.habilitacionVencida(ahora)) {
                solicitud.vencerHabilitacion(solicitud.getVencimientoHabilitacion());
            }
        }
    }

    public ArrayList<Solicitud> colaFavorable(Aula aula) {
        ArrayList<Solicitud> enCola = new ArrayList<Solicitud>();
        for (Solicitud solicitud : solicitudes) {
            if (solicitud.getAula() == aula && solicitud.estaEnColaFavorable()) {
                enCola.add(solicitud);
            }
        }
        return ordenarPorIngreso(enCola);
    }

    private static ArrayList<Solicitud> ordenarPorIngreso(ArrayList<Solicitud> enCola) {
        ArrayList<Solicitud> cola = new ArrayList<Solicitud>();
        for (Solicitud solicitud : enCola) {
            int posicion = cola.size();
            while (posicion > 0 && cola.get(posicion - 1).getFechaIngresoCola()
                    .isAfter(solicitud.getFechaIngresoCola())) {
                posicion--;
            }
            cola.add(posicion, solicitud);
        }
        return cola;
    }
}
