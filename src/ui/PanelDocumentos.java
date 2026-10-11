package ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;

import datos.ArregloEntrevistas;
import modelo.Alumno;
import modelo.Apoderado;
import modelo.Documento;
import modelo.Entrevista;
import modelo.EstadoDocumento;
import modelo.EstadoSolicitud;
import modelo.ReglaDominioException;
import modelo.ResultadoEntrevista;
import modelo.Solicitud;
import modelo.TipoDocumento;
import negocio.ExpedienteDocumentos;

public class PanelDocumentos extends JPanel {

    private static final DateTimeFormatter FORMATO_FECHA_HORA =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String[] MESES = { "ene", "feb", "mar", "abr", "may", "jun", "jul",
            "ago", "sep", "oct", "nov", "dic" };

    private final Solicitud solicitud;
    private final ExpedienteDocumentos expediente;

    private final JLabel titulo = new JLabel();
    private final JLabel subtitulo = new JLabel();
    private final JLabel estadoSolicitud = new JLabel();
    private final JLabel plazoEntrega = new JLabel();
    private final JLabel plazoCorreccion = new JLabel();
    private final JLabel tituloDocumentos = new JLabel();
    private final JPanel filas = new JPanel(new GridLayout(0, 1));
    private final JPanel observaciones = new JPanel();
    private final JComboBox<TipoDocumento> selectorDocumento = new JComboBox<>(TipoDocumento.values());
    private final JPanel chipEntrevista = new JPanel();
    private final JLabel estadoEntrevista = new JLabel();
    private final JLabel ayudaEntrevista = new JLabel();
    private final ArregloEntrevistas entrevistas = new ArregloEntrevistas();
    private Entrevista entrevista;
    private final JLabel fechaEntrevista = texto("", Estilos.SECUNDARIO);
    private final JLabel integrantes = texto("", Estilos.SECUNDARIO);
    private final JLabel responsable = texto("", Estilos.SECUNDARIO);
    private final JLabel asistencia = texto("", Estilos.SECUNDARIO);
    private final JLabel inasistencias = texto("", Estilos.SECUNDARIO);
    private final JButton agendar = Estilos.botonPrimario("Agendar");
    private final JButton asistio = Estilos.botonSecundario("Asistió");
    private final JButton noAsistio = Estilos.botonSecundario("No asistió");
    private final JButton resultado = Estilos.botonSecundario("Resultado");

    public PanelDocumentos(PrincipalUI principal) {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(32, 32, 32, 34));

        // Se muestra el ejemplo del diseño para poder operar la pantalla, sin mezclarlo con
        // las solicitudes de la app.
        try {
            solicitud = crearSolicitudEjemplo(principal);
        } catch (ReglaDominioException e) {
            throw new IllegalStateException("El ejemplo del diseño 03 no es válido.", e);
        }
        expediente = solicitud.getExpediente();

        add(crearEncabezado(), BorderLayout.NORTH);
        JScrollPane desplazable = new JScrollPane(crearCuerpo(),
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        desplazable.setBorder(null);
        desplazable.getViewport().setOpaque(false);
        desplazable.setOpaque(false);
        desplazable.getVerticalScrollBar().setUnitIncrement(16);
        add(desplazable, BorderLayout.CENTER);

        refrescar();
    }

    private JPanel crearEncabezado() {
        JPanel encabezado = new JPanel();
        encabezado.setLayout(new BoxLayout(encabezado, BoxLayout.Y_AXIS));
        encabezado.setOpaque(false);

        titulo.setFont(Estilos.titulo());
        titulo.setForeground(Estilos.TEXTO_PRINCIPAL);
        subtitulo.setFont(Estilos.contenido());
        subtitulo.setForeground(Estilos.SECUNDARIO);
        encabezado.add(alinear(titulo));
        encabezado.add(Box.createVerticalStrut(4));
        encabezado.add(alinear(subtitulo));
        encabezado.add(Box.createVerticalStrut(20));

        JPanel franja = new JPanel(new BorderLayout());
        franja.setBackground(Estilos.ESPERA);
        franja.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        JPanel izquierda = new JPanel(new GridLayout(2, 1, 0, 6));
        izquierda.setOpaque(false);
        estadoSolicitud.setFont(Estilos.fuente(Font.BOLD, 13));
        estadoSolicitud.setForeground(Estilos.TEXTO_ESPERA);
        plazoEntrega.setFont(Estilos.contenido());
        plazoEntrega.setForeground(Estilos.TEXTO_PRINCIPAL);
        izquierda.add(estadoSolicitud);
        izquierda.add(plazoEntrega);
        plazoCorreccion.setFont(Estilos.fuente(Font.BOLD, 14));
        plazoCorreccion.setForeground(Estilos.TEXTO_ESPERA);
        franja.add(izquierda, BorderLayout.CENTER);
        franja.add(plazoCorreccion, BorderLayout.EAST);
        encabezado.add(alinear(franja));
        encabezado.add(Box.createVerticalStrut(20));
        return encabezado;
    }

    private JPanel crearCuerpo() {
        JPanel cuerpo = new CuerpoAnchoFijo();
        cuerpo.setLayout(new GridBagLayout());
        cuerpo.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;
        c.anchor = GridBagConstraints.NORTH;
        c.weighty = 0;

        c.gridx = 0;
        c.weightx = 0.66;
        c.insets = new Insets(0, 0, 0, 16);
        cuerpo.add(crearTarjetaDocumentos(), c);

        c.gridx = 1;
        c.weightx = 0.34;
        c.insets = new Insets(0, 0, 0, 0);
        cuerpo.add(crearTarjetaEntrevista(), c);

        JLabel nota = new JLabel("Entrevista FAVORABLE habilita evaluar matrícula, pero no crea"
                + " una matrícula ni garantiza cupo.");
        nota.setFont(Estilos.contenido());
        nota.setForeground(Estilos.SECUNDARIO);
        c.gridx = 0;
        c.gridy = 1;
        c.gridwidth = 2;
        c.insets = new Insets(20, 0, 0, 0);
        cuerpo.add(nota, c);

        // Empuja las tarjetas hacia arriba cuando sobra alto.
        c.gridy = 2;
        c.weighty = 1;
        cuerpo.add(Box.createVerticalGlue(), c);
        return cuerpo;
    }

    private JPanel crearTarjetaDocumentos() {
        JPanel tarjeta = Estilos.tarjeta(new JPanel());
        tarjeta.setLayout(new BoxLayout(tarjeta, BoxLayout.Y_AXIS));

        tituloDocumentos.setFont(Estilos.panel());
        tituloDocumentos.setForeground(Estilos.TEXTO_PRINCIPAL);
        tarjeta.add(alinear(tituloDocumentos));
        tarjeta.add(Box.createVerticalStrut(4));
        tarjeta.add(alinear(texto("Los cuatro deben entregarse dentro de 7 días calendario desde"
                + " el pago confirmado.", Estilos.SECUNDARIO)));
        tarjeta.add(alinear(texto("La validación puede concluir después.", Estilos.SECUNDARIO)));
        tarjeta.add(Box.createVerticalStrut(16));

        JPanel encabezadoTabla = fila(cabecera("DOCUMENTO"), cabecera("ENTREGADO"),
                cabecera("REVISIÓN"), Estilos.FONDO_CREMA);
        // Sin tope, BoxLayout estira la cabecera cuando la tarjeta de entrevista es más alta.
        encabezadoTabla.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                encabezadoTabla.getPreferredSize().height));
        tarjeta.add(alinear(encabezadoTabla));
        filas.setOpaque(false);
        tarjeta.add(alinear(filas));
        tarjeta.add(Box.createVerticalStrut(16));

        observaciones.setLayout(new BoxLayout(observaciones, BoxLayout.Y_AXIS));
        observaciones.setOpaque(false);
        tarjeta.add(alinear(observaciones));
        tarjeta.add(Box.createVerticalStrut(16));

        tarjeta.add(alinear(crearAcciones()));
        return tarjeta;
    }

    private JPanel crearAcciones() {
        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        acciones.setOpaque(false);
        selectorDocumento.setFont(Estilos.contenido());
        selectorDocumento.setRenderer((lista, tipo, indice, seleccionado, foco) -> {
            JLabel opcion = new JLabel(tipo == null ? "" : tipo.getDescripcion());
            opcion.setFont(Estilos.contenido());
            opcion.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
            return opcion;
        });
        acciones.add(selectorDocumento);

        JButton entregar = Estilos.botonSecundario("Registrar entrega");
        entregar.addActionListener(evento -> registrarEntrega());
        JButton validar = Estilos.botonPrimario("Validar");
        validar.addActionListener(evento -> validar());
        JButton observar = Estilos.botonSecundario("Observar");
        observar.addActionListener(evento -> observar());
        JButton corregir = Estilos.botonSecundario("Registrar corrección");
        corregir.addActionListener(evento -> registrarCorreccion());
        acciones.add(entregar);
        acciones.add(validar);
        acciones.add(observar);
        acciones.add(corregir);
        return acciones;
    }

    private JPanel crearTarjetaEntrevista() {
        JPanel tarjeta = Estilos.tarjeta(new JPanel());
        tarjeta.setLayout(new BoxLayout(tarjeta, BoxLayout.Y_AXIS));

        JLabel tituloEntrevista = new JLabel("Entrevista");
        tituloEntrevista.setFont(Estilos.panel());
        tituloEntrevista.setForeground(Estilos.TEXTO_PRINCIPAL);
        tarjeta.add(alinear(tituloEntrevista));
        tarjeta.add(Box.createVerticalStrut(14));

        chipEntrevista.setLayout(new GridLayout(2, 1, 0, 4));
        chipEntrevista.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        estadoEntrevista.setFont(Estilos.fuente(Font.BOLD, 13));
        ayudaEntrevista.setFont(Estilos.fuente(Font.PLAIN, 12));
        ayudaEntrevista.setForeground(Estilos.SECUNDARIO);
        chipEntrevista.add(estadoEntrevista);
        chipEntrevista.add(ayudaEntrevista);
        tarjeta.add(alinear(chipEntrevista));
        tarjeta.add(Box.createVerticalStrut(18));

        tarjeta.add(alinear(subtituloTarjeta("Personal y cita")));
        tarjeta.add(alinear(fechaEntrevista));
        tarjeta.add(alinear(integrantes));
        tarjeta.add(alinear(responsable));
        tarjeta.add(Box.createVerticalStrut(18));
        tarjeta.add(alinear(subtituloTarjeta("Asistencia y resultado")));
        tarjeta.add(alinear(asistencia));
        tarjeta.add(alinear(inasistencias));
        tarjeta.add(Box.createVerticalStrut(12));

        JPanel botones = new JPanel(new GridLayout(2, 2, 6, 6));
        botones.setOpaque(false);
        agendar.addActionListener(evento -> agendarEntrevista());
        asistio.addActionListener(evento -> ejecutar(() -> entrevista.registrarAsistencia()));
        noAsistio.addActionListener(evento -> registrarInasistencia());
        resultado.addActionListener(evento -> registrarResultado());
        botones.add(agendar);
        botones.add(asistio);
        botones.add(noAsistio);
        botones.add(resultado);
        botones.setMaximumSize(new Dimension(Integer.MAX_VALUE, botones.getPreferredSize().height));
        tarjeta.add(alinear(botones));
        tarjeta.add(Box.createVerticalStrut(14));
        tarjeta.add(alinear(texto("Una reprogramación por inasistencia;", Estilos.SECUNDARIO)));
        tarjeta.add(alinear(texto("segunda ausencia → CANCELADA.", Estilos.SECUNDARIO)));
        tarjeta.add(alinear(texto("NO_FAVORABLE → RECHAZADA.", Estilos.SECUNDARIO)));
        return tarjeta;
    }

    private void refrescar() {
        Alumno alumno = solicitud.getAlumno();
        titulo.setText("Expediente · " + alumno.getNombreCompleto());
        subtitulo.setText(solicitud.getCodigo() + " · " + solicitud.getAula().getNombre() + " ("
                + solicitud.getAula().getEdadRequerida() + " años) · Apoderada principal: "
                + alumno.getPrincipal().getNombreCompleto());

        estadoSolicitud.setText("ESTADO: " + solicitud.getEstado().name());
        plazoEntrega.setText("Inscripción confirmada "
                + formatear(solicitud.getFechaConfirmacionInscripcion()) + " → entrega hasta "
                + formatear(expediente.fechaLimiteEntrega()));
        LocalDate limiteCorreccion = limiteCorreccionMasProximo();
        plazoCorreccion.setText(limiteCorreccion == null ? ""
                : "Corrección: " + formatear(limiteCorreccion));

        int validados = 0;
        filas.removeAll();
        observaciones.removeAll();
        for (Documento documento : expediente.getDocumentos()) {
            if (documento.estaValidado()) {
                validados++;
            }
            filas.add(crearFila(documento));
            if (documento.getEstado() == EstadoDocumento.OBSERVADO) {
                observaciones.add(alinear(texto(documento.getObservacion() + " · comunicado "
                        + formatear(documento.getFechaComunicacion()), Estilos.TEXTO_ALERTA)));
                observaciones.add(Box.createVerticalStrut(6));
            }
        }
        if (observaciones.getComponentCount() > 0) {
            observaciones.add(alinear(texto("7 días calendario para corregir; vuelve a revisión."
                    + " Si vence sin corrección, se cancela la solicitud.", Estilos.SECUNDARIO)));
        }
        tituloDocumentos.setText("Documentos · " + validados + " de "
                + TipoDocumento.values().length + " validados");

        refrescarEntrevista();
        // Sin tope, BoxLayout le pasa al chip todo el alto que sobra en la tarjeta.
        chipEntrevista.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                chipEntrevista.getPreferredSize().height));
        revalidate();
        repaint();
    }

    private void refrescarEntrevista() {
        refrescarDatosEntrevista();
        if (entrevista != null) {
            refrescarChipConEntrevista();
            return;
        }
        if (expediente.puedeAgendarEntrevista()) {
            chipEntrevista.setBackground(Estilos.FAVORABLE);
            estadoEntrevista.setForeground(Estilos.BOTON_PRINCIPAL);
            estadoEntrevista.setText("AGENDABLE · 4 validados");
            ayudaEntrevista.setText("Ya se puede agendar la entrevista.");
            return;
        }
        int observados = 0;
        for (Documento documento : expediente.getDocumentos()) {
            if (documento.getEstado() == EstadoDocumento.OBSERVADO) {
                observados++;
            }
        }
        chipEntrevista.setBackground(Estilos.ESPERA);
        estadoEntrevista.setForeground(Estilos.TEXTO_ESPERA);
        estadoEntrevista.setText(observados > 0
                ? "BLOQUEADA · " + observados + (observados == 1 ? " observado" : " observados")
                : "BLOQUEADA · " + expediente.pendientesDeValidar().size() + " sin validar");
        ayudaEntrevista.setText("Agendable al validar los 4 documentos.");
    }

    private void refrescarDatosEntrevista() {
        boolean abierta = solicitud.estaActiva();
        if (entrevista == null) {
            fechaEntrevista.setText("Fecha y hora: —");
            integrantes.setText("Integrantes del personal (al menos 1): —");
            responsable.setText("Responsable (exactamente 1): —");
            asistencia.setText("Asistencia: — · Resultado: —");
            inasistencias.setText("Inasistencias: 0 de 2");
            agendar.setEnabled(abierta && expediente.puedeAgendarEntrevista());
            asistio.setEnabled(false);
            noAsistio.setEnabled(false);
            resultado.setEnabled(false);
            return;
        }
        Boolean asistioApoderado = entrevista.getAsistioApoderado();
        boolean sinResultado = entrevista.getResultado() == null;
        fechaEntrevista.setText("Fecha y hora: " + formatear(entrevista.getFechaHora())
                + (entrevista.getInasistencias() > 0 ? " (reprogramada)" : ""));
        integrantes.setText("Integrantes del personal: "
                + String.join(", ", entrevista.getPersonal()));
        responsable.setText("Responsable: " + entrevista.getResponsable());
        asistencia.setText("Asistencia: "
                + (Boolean.TRUE.equals(asistioApoderado) ? "asistió" : "pendiente")
                + " · Resultado: " + (sinResultado ? "pendiente" : entrevista.getResultado()));
        inasistencias.setText("Inasistencias: " + entrevista.getInasistencias() + " de 2");
        agendar.setEnabled(false);
        boolean esperaAsistencia = abierta && sinResultado && asistioApoderado == null;
        asistio.setEnabled(esperaAsistencia);
        noAsistio.setEnabled(esperaAsistencia);
        resultado.setEnabled(abierta && sinResultado && Boolean.TRUE.equals(asistioApoderado));
    }

    private void refrescarChipConEntrevista() {
        if (solicitud.getEstado() == EstadoSolicitud.CANCELADA) {
            pintarChip(Estilos.ALERTA, Estilos.TEXTO_ALERTA, "CANCELADA · 2 inasistencias",
                    "La segunda inasistencia cancela la solicitud.");
        } else if (entrevista.getResultado() == ResultadoEntrevista.NO_FAVORABLE) {
            pintarChip(Estilos.ALERTA, Estilos.TEXTO_ALERTA, "NO_FAVORABLE",
                    "La solicitud queda RECHAZADA.");
        } else if (entrevista.getResultado() == ResultadoEntrevista.FAVORABLE) {
            pintarChip(Estilos.FAVORABLE, Estilos.BOTON_PRINCIPAL, "FAVORABLE",
                    "Habilita evaluar matrícula; no garantiza cupo.");
        } else {
            pintarChip(Estilos.FAVORABLE, Estilos.BOTON_PRINCIPAL,
                    "AGENDADA · " + formatear(entrevista.getFechaHora()),
                    entrevista.getInasistencias() > 0 ? "Ya usó su única reprogramación."
                            : "Registre la asistencia del apoderado.");
        }
    }

    private void pintarChip(Color fondo, Color texto, String estado, String ayuda) {
        chipEntrevista.setBackground(fondo);
        estadoEntrevista.setForeground(texto);
        estadoEntrevista.setText(estado);
        ayudaEntrevista.setText(ayuda);
    }

    private void agendarEntrevista() {
        String fecha = JOptionPane.showInputDialog(this, "Fecha y hora (dd/mm/aaaa hh:mm):",
                LocalDate.now().plusDays(1).atTime(10, 0).format(FORMATO_FECHA_HORA));
        if (fecha == null) {
            return;
        }
        String personal = JOptionPane.showInputDialog(this,
                "Integrantes del personal (separados por coma):", "Rosa Díaz, Luis Pérez");
        if (personal == null) {
            return;
        }
        ArrayList<String> participantes = new ArrayList<String>();
        for (String nombre : personal.split(",")) {
            if (!nombre.trim().isEmpty()) {
                participantes.add(nombre.trim());
            }
        }
        String elegido = JOptionPane.showInputDialog(this, "Responsable (uno de los integrantes):",
                participantes.isEmpty() ? "" : participantes.get(0));
        if (elegido == null) {
            return;
        }
        LocalDateTime fechaHora = leerFechaHora(fecha);
        if (fechaHora == null) {
            return;
        }
        ejecutar(() -> entrevista = entrevistas.agendar(solicitud, expediente, fechaHora,
                participantes, elegido.trim()));
    }

    private void registrarInasistencia() {
        if (entrevista.getInasistencias() >= 1) {
            int respuesta = JOptionPane.showConfirmDialog(this,
                    "Es la segunda inasistencia: la solicitud quedará CANCELADA. ¿Registrar?",
                    "Segunda inasistencia", JOptionPane.YES_NO_OPTION);
            if (respuesta == JOptionPane.YES_OPTION) {
                ejecutar(() -> entrevista.registrarInasistencia(null));
            }
            return;
        }
        String fecha = JOptionPane.showInputDialog(this,
                "Única reprogramación. Nueva fecha y hora (dd/mm/aaaa hh:mm):",
                entrevista.getFechaHora().plusDays(2).format(FORMATO_FECHA_HORA));
        if (fecha == null) {
            return;
        }
        LocalDateTime reprogramada = leerFechaHora(fecha);
        if (reprogramada != null) {
            ejecutar(() -> entrevista.registrarInasistencia(reprogramada));
        }
    }

    private void registrarResultado() {
        ResultadoEntrevista[] opciones = ResultadoEntrevista.values();
        int elegido = JOptionPane.showOptionDialog(this, "Resultado de la entrevista realizada:",
                "Registrar resultado", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, opciones, opciones[0]);
        if (elegido < 0) {
            return;
        }
        ejecutar(() -> {
            if (!entrevista.isRealizada()) {
                entrevista.marcarRealizada();
            }
            entrevista.registrarResultado(opciones[elegido]);
        });
    }

    private LocalDateTime leerFechaHora(String texto) {
        try {
            return LocalDateTime.parse(texto.trim(), FORMATO_FECHA_HORA);
        } catch (DateTimeParseException e) {
            JOptionPane.showMessageDialog(this, "Escriba la fecha como 25/10/2026 10:30.",
                    "Fecha no válida", JOptionPane.WARNING_MESSAGE);
            return null;
        }
    }

    private JPanel crearFila(Documento documento) {
        JLabel nombre = texto(documento.getTipo().getDescripcion(), Estilos.TEXTO_PRINCIPAL);
        JLabel entregado = texto(documento.estaEntregado()
                ? formatear(documento.getFechaHoraEntrega()) : "—", Estilos.TEXTO_PRINCIPAL);
        JLabel revision = new JLabel(textoRevision(documento.getEstado()));
        revision.setFont(Estilos.fuente(Font.BOLD, 13));

        Color fondo = Estilos.TARJETA;
        if (documento.getEstado() == EstadoDocumento.VALIDADO) {
            revision.setForeground(Estilos.BOTON_PRINCIPAL);
        } else if (documento.getEstado() == EstadoDocumento.OBSERVADO) {
            revision.setForeground(Estilos.TEXTO_ALERTA);
            fondo = Estilos.ALERTA;
        } else {
            revision.setForeground(Estilos.TEXTO_ESPERA);
        }
        return fila(nombre, entregado, revision, fondo);
    }

    private String textoRevision(EstadoDocumento estado) {
        if (estado == null) {
            return "SIN ENTREGAR";
        }
        return estado.name().replace('_', ' ');
    }

    private void registrarEntrega() {
        ejecutar(() -> documentoSeleccionado().registrarEntrega(LocalDateTime.now()));
    }

    private void validar() {
        ejecutar(() -> documentoSeleccionado().validar());
    }

    private void registrarCorreccion() {
        ejecutar(() -> documentoSeleccionado().entregarCorreccion(LocalDateTime.now()));
    }

    private void observar() {
        Documento documento = documentoSeleccionado();
        String motivo = JOptionPane.showInputDialog(this,
                "Motivo de la observación del " + documento.getTipo().getDescripcion() + ":",
                "Observar documento", JOptionPane.PLAIN_MESSAGE);
        if (motivo == null) {
            return;
        }
        ejecutar(() -> documento.observar(motivo, LocalDate.now()));
    }

    private Documento documentoSeleccionado() {
        return expediente.getDocumento((TipoDocumento) selectorDocumento.getSelectedItem());
    }

    private void ejecutar(Accion accion) {
        try {
            accion.ejecutar();
        } catch (ReglaDominioException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "No se pudo registrar",
                    JOptionPane.WARNING_MESSAGE);
        }
        refrescar();
    }

    private interface Accion {
        void ejecutar() throws ReglaDominioException;
    }

    private static final class CuerpoAnchoFijo extends JPanel implements Scrollable {

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visible, int orientacion, int direccion) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visible, int orientacion, int direccion) {
            return visible.height;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private LocalDate limiteCorreccionMasProximo() {
        LocalDate proximo = null;
        for (Documento documento : expediente.getDocumentos()) {
            LocalDate limite = documento.fechaLimiteCorreccion();
            if (limite != null && (proximo == null || limite.isBefore(proximo))) {
                proximo = limite;
            }
        }
        return proximo;
    }

    // Formato del diseño ("23 sep · 15:00"); el de Java para es-PE daría "23 sept.".
    private static String formatear(LocalDateTime fechaHora) {
        return formatear(fechaHora.toLocalDate()) + " · "
                + String.format("%02d:%02d", fechaHora.getHour(), fechaHora.getMinute());
    }

    // Los plazos documentales son por día calendario (§4.4): se muestran sin hora.
    private static String formatear(LocalDate fecha) {
        return fecha.getDayOfMonth() + " " + MESES[fecha.getMonthValue() - 1];
    }

    private static JPanel fila(JComponent documento, JComponent entregado, JComponent revision,
            Color fondo) {
        JPanel fila = new JPanel(new GridBagLayout());
        fila.setBackground(fondo);
        fila.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Estilos.BORDE_TARJETA),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        // Ancho preferido 0: así las columnas se reparten solo por peso y quedan alineadas
        // entre filas, sin importar el largo de cada texto.
        for (JComponent celda : new JComponent[] { documento, entregado, revision }) {
            celda.setPreferredSize(new Dimension(0, celda.getPreferredSize().height));
        }
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 0.5;
        fila.add(documento, c);
        c.weightx = 0.28;
        fila.add(entregado, c);
        c.weightx = 0.22;
        fila.add(revision, c);
        return fila;
    }

    private static JLabel cabecera(String texto) {
        JLabel etiqueta = new JLabel(texto);
        etiqueta.setFont(Estilos.fuente(Font.BOLD, 12));
        etiqueta.setForeground(Estilos.SECUNDARIO);
        return etiqueta;
    }

    private static JLabel subtituloTarjeta(String texto) {
        JLabel etiqueta = new JLabel(texto);
        etiqueta.setFont(Estilos.fuente(Font.BOLD, 14));
        etiqueta.setForeground(Estilos.TEXTO_PRINCIPAL);
        etiqueta.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        return etiqueta;
    }

    private static JLabel texto(String texto, Color color) {
        JLabel etiqueta = new JLabel(texto);
        etiqueta.setFont(Estilos.contenido());
        etiqueta.setForeground(color);
        etiqueta.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        return etiqueta;
    }

    // BoxLayout centra por defecto: el diseño alinea todo a la izquierda.
    private static <T extends JComponent> T alinear(T componente) {
        componente.setAlignmentX(LEFT_ALIGNMENT);
        return componente;
    }

    private static Solicitud crearSolicitudEjemplo(PrincipalUI principal)
            throws ReglaDominioException {
        Alumno noa = new Alumno(1038, "78451236", "Noa", "Ruiz", LocalDate.of(2023, 8, 14), "");
        noa.agregarApoderado(new Apoderado("41526378", "Ana", "Ruiz", "987654321"), true);
        Solicitud solicitud = new Solicitud("SOL-1038", noa, principal.getAulas().buscar("AUL-02"),
                LocalDate.now().minusDays(6).atTime(9, 40));
        // Mismos intervalos que el diseño, contados desde hoy para que el plazo siga vigente.
        LocalDate inicio = LocalDate.now().minusDays(4);
        solicitud.habilitarParaPago(inicio.atTime(9, 0));
        solicitud.confirmarInscripcion(inicio.atTime(10, 30));
        ExpedienteDocumentos expediente = solicitud.getExpediente();
        Documento dniAlumno = expediente.getDocumento(TipoDocumento.DNI_ALUMNO);
        dniAlumno.registrarEntrega(inicio.plusDays(2).atTime(9, 0));
        dniAlumno.validar();
        Documento dniApoderado = expediente.getDocumento(TipoDocumento.DNI_APODERADO_PRINCIPAL);
        dniApoderado.registrarEntrega(inicio.plusDays(2).atTime(9, 0));
        dniApoderado.validar();
        Documento partida = expediente.getDocumento(TipoDocumento.PARTIDA_NACIMIENTO);
        partida.registrarEntrega(inicio.plusDays(3).atTime(12, 10));
        partida.validar();
        Documento carne = expediente.getDocumento(TipoDocumento.CARNE_VACUNAS);
        carne.registrarEntrega(inicio.plusDays(3).atTime(13, 20));
        carne.observar("Falta página de refuerzo", inicio.plusDays(3));
        return solicitud;
    }
}
