package datos;

import java.time.LocalDateTime;
import java.util.ArrayList;

import modelo.Alumno;
import modelo.Aula;
import modelo.DatoInvalidoException;
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
}
