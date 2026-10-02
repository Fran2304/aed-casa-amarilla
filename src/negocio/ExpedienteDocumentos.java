package negocio;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;

import modelo.DatoInvalidoException;
import modelo.Documento;
import modelo.ReglaDominioException;
import modelo.TipoDocumento;

public class ExpedienteDocumentos {

    public static final int DIAS_ENTREGA = 7;

    private final LocalDate inscripcionConfirmadaEl;
    private final Map<TipoDocumento, Documento> documentos;

    public ExpedienteDocumentos(LocalDate inscripcionConfirmadaEl) throws DatoInvalidoException {
        if (inscripcionConfirmadaEl == null) {
            throw new DatoInvalidoException(
                    "La fecha de confirmación de la inscripción es obligatoria.");
        }
        this.inscripcionConfirmadaEl = inscripcionConfirmadaEl;
        this.documentos = new EnumMap<>(TipoDocumento.class);
        for (TipoDocumento tipo : TipoDocumento.values()) {
            documentos.put(tipo, new Documento(tipo));
        }
    }

    public LocalDate fechaLimiteEntrega() {
        return inscripcionConfirmadaEl.plusDays(DIAS_ENTREGA);
    }

    /** El plazo inicial exige entrega, no validación: la revisión pendiente no cuenta en contra. */
    public boolean entregaOportuna() {
        for (Documento documento : documentos.values()) {
            if (!documento.estaEntregado()
                    || documento.getFechaHoraEntrega().toLocalDate().isAfter(fechaLimiteEntrega())) {
                return false;
            }
        }
        return true;
    }

    public boolean debeCancelarse(LocalDate hoy) {
        if (hoy.isAfter(fechaLimiteEntrega()) && !entregaOportuna()) {
            return true;
        }
        for (Documento documento : documentos.values()) {
            if (documento.correccionVencida(hoy)) {
                return true;
            }
        }
        return false;
    }

    public boolean puedeAgendarEntrevista() {
        return pendientesDeValidar().isEmpty();
    }

    public void exigirAgendarEntrevista() throws ReglaDominioException {
        ArrayList<Documento> pendientes = pendientesDeValidar();
        if (!pendientes.isEmpty()) {
            StringBuilder faltan = new StringBuilder();
            for (Documento documento : pendientes) {
                if (faltan.length() > 0) {
                    faltan.append(", ");
                }
                faltan.append(documento.getTipo().getDescripcion());
            }
            throw new ReglaDominioException(
                    "No se puede agendar la entrevista. Falta validar: " + faltan + ".");
        }
    }

    public ArrayList<Documento> pendientesDeValidar() {
        ArrayList<Documento> pendientes = new ArrayList<Documento>();
        for (Documento documento : documentos.values()) {
            if (!documento.estaValidado()) {
                pendientes.add(documento);
            }
        }
        return pendientes;
    }

    public Documento getDocumento(TipoDocumento tipo) {
        return documentos.get(tipo);
    }

    public ArrayList<Documento> getDocumentos() {
        return new ArrayList<Documento>(documentos.values());
    }

    public LocalDate getInscripcionConfirmadaEl() {
        return inscripcionConfirmadaEl;
    }
}
