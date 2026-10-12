package datos;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;

import modelo.Alumno;
import modelo.Aula;
import modelo.DatoInvalidoException;
import modelo.EstadoSolicitud;
import modelo.ReglaDominioException;
import modelo.Solicitud;
import modelo.SolicitudDuplicadaException;
import util.FechasAdmision;
import util.Reloj;

public class ArregloSolicitudes {

    private static final int PRIMER_NUMERO = 1001;

    private ArrayList<Solicitud> solicitudes;
    private int siguienteNumero;

    public ArregloSolicitudes() {
        solicitudes = new ArrayList<Solicitud>();
        siguienteNumero = PRIMER_NUMERO;
    }

    public Solicitud registrar(Alumno alumno, Aula aula) throws ReglaDominioException {
        exigirAlumnoYAula(alumno, aula);
        alumno.validarApoderados();
        exigirAdmisible(alumno, aula);

        Solicitud nueva = new Solicitud("SOL-" + siguienteNumero, alumno, aula,
                Reloj.porDefecto().now());
        solicitudes.add(nueva);
        siguienteNumero++;
        return nueva;
    }

    public void exigirAdmisible(Alumno alumno, Aula aula) throws ReglaDominioException {
        exigirAlumnoYAula(alumno, aula);
        // Misma función que la validación en vivo de Solicitudes: la franja y el guardado
        // no pueden discrepar sobre la edad.
        int edad = FechasAdmision.edadAlCorte(alumno.getFechaNacimiento());
        if (!aula.admiteEdad(edad)) {
            throw new ReglaDominioException(alumno.getNombreCompleto() + " tendrá " + edad
                    + " años al 31/03/2027 y el aula " + aula.getNombre() + " es para "
                    + aula.getEdadRequerida() + " años.");
        }

        Solicitud activa = buscarActivaDe(alumno);
        if (activa != null) {
            throw new SolicitudDuplicadaException(activa);
        }
    }

    private static void exigirAlumnoYAula(Alumno alumno, Aula aula)
            throws DatoInvalidoException {
        if (alumno == null) {
            throw new DatoInvalidoException("El alumno es obligatorio.");
        }
        if (aula == null) {
            throw new DatoInvalidoException("El aula solicitada es obligatoria.");
        }
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

    public void cancelarDocumentacionVencida(LocalDate hoy, String responsable)
            throws ReglaDominioException {
        if (hoy == null) {
            throw new DatoInvalidoException("La fecha de revisión es obligatoria.");
        }
        for (Solicitud solicitud : solicitudes) {
            if (solicitud.getEstado() == EstadoSolicitud.EN_DOCUMENTACION
                    && solicitud.getExpediente().debeCancelarse(hoy)) {
                solicitud.cancelarPorDocumentacion(hoy.atStartOfDay(),
                        solicitud.getExpediente().motivoCancelacion(hoy), responsable);
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
