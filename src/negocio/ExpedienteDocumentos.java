package negocio;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;

import modelo.DatoInvalidoException;
import modelo.Documento;
import modelo.ReglaDominioException;
import modelo.TipoDocumento;

public class ExpedienteDocumentos {

    public static final int DIAS_ENTREGA = 7;

    private final LocalDateTime inscripcionConfirmadaEl;
    private final Map<TipoDocumento, Documento> documentos;

    public ExpedienteDocumentos(LocalDateTime inscripcionConfirmadaEl) throws DatoInvalidoException {
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

    public LocalDateTime fechaLimiteEntrega() {
        return inscripcionConfirmadaEl.plusDays(DIAS_ENTREGA);
    }

    /**
     * El plazo inicial exige entrega, no validación: la revisión pendiente no cuenta en contra.
     * Es en días calendario: el último día cuenta completo, sin importar la hora.
     */
    public boolean entregaOportuna() {
        LocalDate ultimoDia = fechaLimiteEntrega().toLocalDate();
        for (Documento documento : documentos.values()) {
            if (!documento.estaEntregado()
                    || documento.getFechaHoraEntrega().toLocalDate().isAfter(ultimoDia)) {
                return false;
            }
        }
        return true;
    }

    // PlazosDocumentales (#17) evalúa con la fecha de hoy.
    public boolean debeCancelarse(LocalDate hoy) {
        return debeCancelarse(hoy.atStartOfDay());
    }

    public boolean debeCancelarse(LocalDateTime ahora) {
        boolean plazoVencido = ahora.toLocalDate().isAfter(fechaLimiteEntrega().toLocalDate());
        if (plazoVencido && !entregaOportuna()) {
            return true;
        }
        for (Documento documento : documentos.values()) {
            if (documento.correccionVencida(ahora)) {
                return true;
            }
        }
        return false;
    }

    public String motivoCancelacion(LocalDate hoy) {
        if (hoy.isAfter(fechaLimiteEntrega()) && !entregaOportuna()) {
            return "Venció el plazo inicial de entrega de documentos el "
                    + fechaLimiteEntrega() + ".";
        }
        for (Documento documento : documentos.values()) {
            if (documento.correccionVencida(hoy)) {
                return "Venció el plazo de corrección del " + documento.getTipo().getDescripcion()
                        + " el " + documento.fechaLimiteCorreccion() + ".";
            }
        }
        return "Incumplimiento del plazo documental.";
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

    public LocalDateTime getInscripcionConfirmadaEl() {
        return inscripcionConfirmadaEl;
    }
}
