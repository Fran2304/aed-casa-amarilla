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

import datos.ArregloAulas;
import datos.ArregloMatriculas;
import datos.ArregloSolicitudes;
import modelo.Alumno;
import modelo.Apoderado;
import modelo.Aula;
import modelo.Documento;
import modelo.EstadoMatricula;
import modelo.Matricula;
import modelo.ReglaDominioException;
import modelo.Solicitud;
import negocio.ExpedienteDocumentos;
import negocio.Matriculas;
import negocio.Turnos;
import negocio.Vacantes;
import util.FechasAdmision;

public class PanelColas extends JPanel {

    private static final String[] MESES = { "ene", "feb", "mar", "abr", "may", "jun", "jul",
            "ago", "sep", "oct", "nov", "dic" };

    // Todavía no hay flujo que llene las colas reales: la pantalla opera sobre el ejemplo
    // del diseño 04, sin mezclarlo con las solicitudes de la app.
    private final ArregloSolicitudes solicitudes = new ArregloSolicitudes();
    private final ArregloMatriculas matriculas = new ArregloMatriculas();
    private int siguienteDni = 70000001;

    private final JComboBox<Aula> selectorAula;
    private final JLabel tituloAula = new JLabel();
    private final JLabel desglose = new JLabel();
    private final JLabel cifraVacantes = new JLabel();
    private final JLabel tituloOrden = new JLabel();
    private final JPanel filas = new JPanel(new GridLayout(0, 1));
    private final JLabel tituloTurno = new JLabel();
    private final JPanel cajaEstado = new JPanel(new GridLayout(2, 1, 0, 4));
    private final JLabel estadoTurno = new JLabel();
    private final JLabel detalleTurno = new JLabel();
    private final JPanel comprobaciones = new JPanel(new GridLayout(0, 1, 0, 4));
    private final JButton confirmar = Estilos.botonPrimario("Confirmar oferta y crear matrícula");

    public PanelColas(PrincipalUI principal) {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(32, 32, 32, 34));

        ArregloAulas aulas = principal.getAulas();
        try {
            cargarEjemplo(aulas);
        } catch (ReglaDominioException e) {
            throw new IllegalStateException("El ejemplo del diseño 04 no es válido.", e);
        }

        selectorAula = new JComboBox<Aula>(aulas.listar().toArray(new Aula[0]));
        selectorAula.setFont(Estilos.contenido());
        selectorAula.setRenderer((lista, aula, indice, seleccionado, foco) -> {
            JLabel opcion = new JLabel(aula == null ? "" : aula.getNombre());
            opcion.setFont(Estilos.contenido());
            opcion.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
            return opcion;
        });
        selectorAula.addActionListener(evento -> refrescar());
        confirmar.addActionListener(evento -> confirmarOferta());

        add(crearEncabezado(), BorderLayout.NORTH);
        JScrollPane desplazable = new JScrollPane(crearCuerpo(),
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        desplazable.setBorder(null);
        desplazable.setOpaque(false);
        desplazable.getViewport().setOpaque(false);
        add(desplazable, BorderLayout.CENTER);

        refrescar();
    }

    private JPanel crearEncabezado() {
        JPanel encabezado = new JPanel(new BorderLayout());
        encabezado.setOpaque(false);
        encabezado.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));

        JPanel textos = new JPanel(new GridLayout(2, 1, 0, 4));
        textos.setOpaque(false);
        JLabel titulo = new JLabel("Colas y disponibilidad");
        titulo.setFont(Estilos.titulo());
        titulo.setForeground(Estilos.TEXTO_PRINCIPAL);
        textos.add(titulo);
        textos.add(texto("La vacante habilita una selección concreta; no a todos los integrantes"
                + " de la cola.", Estilos.SECUNDARIO));
        encabezado.add(textos, BorderLayout.CENTER);

        JPanel selector = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        selector.setOpaque(false);
        selector.add(texto("Aula", Estilos.SECUNDARIO));
        selector.add(selectorAula);
        encabezado.add(selector, BorderLayout.EAST);
        return encabezado;
    }

    private JPanel crearCuerpo() {
        JPanel cuerpo = new CuerpoAnchoFijo();
        cuerpo.setLayout(new GridBagLayout());
        cuerpo.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.BOTH;

        c.gridy = 0;
        c.gridx = 0;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 16, 16);
        cuerpo.add(crearTarjetaAula(), c);
        c.gridx = 1;
        c.weightx = 0;
        c.insets = new Insets(0, 0, 16, 0);
        cuerpo.add(crearTarjetaVacantes(), c);

        JPanel fila = new JPanel(new GridBagLayout());
        fila.setOpaque(false);
        GridBagConstraints f = new GridBagConstraints();
        f.fill = GridBagConstraints.BOTH;
        f.weighty = 1;
        f.weightx = 0.64;
        f.insets = new Insets(0, 0, 0, 16);
        fila.add(crearTarjetaOrden(), f);
        f.weightx = 0.36;
        f.insets = new Insets(0, 0, 0, 0);
        fila.add(crearTarjetaTurno(), f);
        c.gridy = 1;
        c.gridx = 0;
        c.gridwidth = 2;
        c.weightx = 1;
        c.insets = new Insets(0, 0, 0, 0);
        cuerpo.add(fila, c);

        c.gridy = 2;
        c.insets = new Insets(20, 0, 0, 0);
        cuerpo.add(texto("Invitación SIN PAGO: 48 h desde emisión; si vence, vuelve al final de"
                + " esa cola con nuevo ingreso.", Estilos.SECUNDARIO), c);

        c.gridy = 3;
        c.weighty = 1;
        cuerpo.add(Box.createVerticalGlue(), c);
        return cuerpo;
    }

    private JPanel crearTarjetaAula() {
        JPanel tarjeta = Estilos.tarjeta(new JPanel());
        tarjeta.setLayout(new BoxLayout(tarjeta, BoxLayout.Y_AXIS));
        tituloAula.setFont(Estilos.panel());
        tituloAula.setForeground(Estilos.TEXTO_PRINCIPAL);
        tarjeta.add(alinear(tituloAula));
        tarjeta.add(alinear(texto("Edad al 31 mar 2027 · única sede · cupo calculado en tiempo real",
                Estilos.SECUNDARIO)));
        tarjeta.add(Box.createVerticalStrut(12));

        JPanel caja = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        caja.setBackground(Estilos.FONDO_CREMA);
        caja.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        desglose.setFont(Estilos.fuente(Font.BOLD, 14));
        desglose.setForeground(Estilos.TEXTO_PRINCIPAL);
        caja.add(desglose);
        tarjeta.add(alinear(caja));
        return tarjeta;
    }

    private JPanel crearTarjetaVacantes() {
        JPanel tarjeta = new JPanel(new GridLayout(2, 1, 0, 6));
        tarjeta.setBackground(Estilos.ESPERA);
        tarjeta.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        tarjeta.setPreferredSize(new Dimension(180, 0));
        JLabel etiqueta = new JLabel("VACANTES");
        etiqueta.setFont(Estilos.fuente(Font.BOLD, 13));
        etiqueta.setForeground(Estilos.TEXTO_ESPERA);
        cifraVacantes.setFont(Estilos.cifra());
        cifraVacantes.setForeground(Estilos.TEXTO_PRINCIPAL);
        tarjeta.add(etiqueta);
        tarjeta.add(cifraVacantes);
        return tarjeta;
    }

    private JPanel crearTarjetaOrden() {
        JPanel tarjeta = Estilos.tarjeta(new JPanel(new BorderLayout(0, 14)));
        JPanel textos = new JPanel(new GridLayout(2, 1));
        textos.setOpaque(false);
        tituloOrden.setFont(Estilos.panel());
        tituloOrden.setForeground(Estilos.TEXTO_PRINCIPAL);
        textos.add(tituloOrden);
        textos.add(texto("FAVORABLE primero; luego SIN PAGO. Antigüedad dentro de cada cola.",
                Estilos.SECUNDARIO));
        tarjeta.add(textos, BorderLayout.NORTH);

        JPanel tabla = new JPanel(new BorderLayout());
        tabla.setOpaque(false);
        tabla.add(fila(cabecera("TURNO"), cabecera("SOLICITUD"), cabecera("INGRESO COLA"),
                cabecera("ACCIÓN"), Estilos.FONDO_CREMA), BorderLayout.NORTH);
        filas.setOpaque(false);
        tabla.add(filas, BorderLayout.CENTER);
        // En NORTH la tabla conserva su alto en vez de estirarse con la tarjeta del turno.
        JPanel contenedor = new JPanel(new BorderLayout());
        contenedor.setOpaque(false);
        contenedor.add(tabla, BorderLayout.NORTH);
        tarjeta.add(contenedor, BorderLayout.CENTER);
        return tarjeta;
    }

    private JPanel crearTarjetaTurno() {
        JPanel tarjeta = Estilos.tarjeta(new JPanel());
        tarjeta.setLayout(new BoxLayout(tarjeta, BoxLayout.Y_AXIS));
        tituloTurno.setFont(Estilos.panel());
        tituloTurno.setForeground(Estilos.TEXTO_PRINCIPAL);
        tarjeta.add(alinear(tituloTurno));
        tarjeta.add(Box.createVerticalStrut(12));

        cajaEstado.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        estadoTurno.setFont(Estilos.fuente(Font.BOLD, 13));
        detalleTurno.setFont(Estilos.fuente(Font.PLAIN, 12));
        detalleTurno.setForeground(Estilos.SECUNDARIO);
        cajaEstado.add(estadoTurno);
        cajaEstado.add(detalleTurno);
        tarjeta.add(alinear(cajaEstado));
        tarjeta.add(Box.createVerticalStrut(16));

        JLabel antes = new JLabel("Antes de crear matrícula");
        antes.setFont(Estilos.fuente(Font.BOLD, 14));
        antes.setForeground(Estilos.TEXTO_PRINCIPAL);
        tarjeta.add(alinear(antes));
        tarjeta.add(Box.createVerticalStrut(6));
        comprobaciones.setOpaque(false);
        tarjeta.add(alinear(comprobaciones));
        tarjeta.add(Box.createVerticalStrut(16));

        tarjeta.add(alinear(confirmar));
        tarjeta.add(Box.createVerticalStrut(8));
        tarjeta.add(alinear(texto("Si no acepta, no se crea.", Estilos.SECUNDARIO)));
        tarjeta.add(alinear(texto("PENDIENTE_PAGO es el primer momento que reserva vacante.",
                Estilos.SECUNDARIO)));
        return tarjeta;
    }

    private void refrescar() {
        Aula aula = (Aula) selectorAula.getSelectedItem();
        ArrayList<Matricula> vigentes = matriculas.listar();
        int pendientes = Vacantes.contar(aula, vigentes, EstadoMatricula.PENDIENTE_PAGO);
        int activas = Vacantes.contar(aula, vigentes, EstadoMatricula.ACTIVA);
        int vacantes = Vacantes.calcular(aula, vigentes);

        tituloAula.setText("Aula " + aula.getNombre() + " · " + aula.getEdadRequerida() + " años");
        desglose.setText("Capacidad " + aula.getCapacidad() + "  −  PENDIENTE_PAGO " + pendientes
                + "  −  ACTIVA " + activas + "  =  " + vacantes
                + (vacantes == 1 ? " disponible" : " disponibles"));
        cifraVacantes.setText(String.format("%02d", vacantes));
        tituloOrden.setText("Orden de atención · " + aula.getNombre());

        ArrayList<Solicitud> orden = new ArrayList<Solicitud>();
        Solicitud turno = null;
        try {
            LocalDateTime ahora = LocalDateTime.now();
            orden = Turnos.ordenPorPrioridad(aula, solicitudes, vigentes, ahora);
            turno = Turnos.siguiente(aula, solicitudes, vigentes, ahora);
        } catch (ReglaDominioException e) {
            mostrarError("No se pudo calcular el orden de atención", e);
        }
        filas.removeAll();
        for (int i = 0; i < orden.size(); i++) {
            filas.add(crearFila(i + 1, orden.get(i), orden.get(i) == turno));
        }
        if (orden.isEmpty()) {
            filas.add(fila(texto("Sin solicitudes en cola.", Estilos.SECUNDARIO), new JLabel(),
                    new JLabel(), new JLabel(), Estilos.TARJETA));
        }

        refrescarTurno(turno, vacantes);
        revalidate();
        repaint();
    }

    private void refrescarTurno(Solicitud turno, int vacantes) {
        comprobaciones.removeAll();
        if (turno == null) {
            tituloTurno.setText("Turno actual");
            cajaEstado.setBackground(Estilos.FONDO_CREMA);
            estadoTurno.setForeground(Estilos.TEXTO_PRINCIPAL);
            estadoTurno.setText(vacantes <= 0 ? "SIN VACANTE" : "SIN TURNO");
            detalleTurno.setText(vacantes <= 0 ? "Nadie avanza hasta que se libere un cupo."
                    : "No hay solicitudes esperando en esta aula.");
            confirmar.setEnabled(false);
            return;
        }

        boolean favorable = turno.estaEnColaFavorable();
        tituloTurno.setText("Turno actual · " + turno.getAlumno().getNombres());
        estadoTurno.setText(turno.getEstado().name());
        if (favorable) {
            cajaEstado.setBackground(Estilos.FAVORABLE);
            estadoTurno.setForeground(Estilos.BOTON_PRINCIPAL);
            detalleTurno.setText("Inscripción pagada, entrevista favorable.");
        } else {
            cajaEstado.setBackground(Estilos.ESPERA);
            estadoTurno.setForeground(Estilos.TEXTO_ESPERA);
            detalleTurno.setText("Sin pago: corresponde invitar a inscribirse (48 h).");
        }

        ExpedienteDocumentos expediente = turno.getExpediente();
        boolean documentosValidados = expediente != null && expediente.puedeAgendarEntrevista();
        boolean cupoLibre = vacantes > 0 && matriculas.buscarVigenteDe(turno.getAlumno()) == null;
        comprobaciones.add(comprobacion(documentosValidados, "Cuatro documentos validados"));
        comprobaciones.add(comprobacion(favorable, "Entrevista FAVORABLE"));
        comprobaciones.add(comprobacion(cupoLibre, "Recalcular cupo y matrícula vigente"));
        comprobaciones.add(comprobacion(turno.tieneOfertaAceptada(),
                "Confirmación de oferta por personal"));
        confirmar.setEnabled(favorable && !turno.tieneOfertaAceptada());
    }

    private JPanel crearFila(int posicion, Solicitud solicitud, boolean esTurno) {
        boolean favorable = solicitud.estaEnColaFavorable();
        JLabel turno = new JLabel(posicion + " · " + (favorable ? "Favorable" : "Sin pago"));
        turno.setFont(Estilos.fuente(Font.BOLD, 14));
        turno.setForeground(favorable ? Estilos.BOTON_PRINCIPAL : Estilos.TEXTO_ESPERA);
        JLabel nombre = texto(solicitud.getAlumno().getNombreCompleto() + " · "
                + solicitud.getCodigo(), Estilos.TEXTO_PRINCIPAL);
        JLabel ingreso = texto(formatear(solicitud.getFechaIngresoCola()), Estilos.TEXTO_PRINCIPAL);

        String textoAccion = "Esperar";
        if (esTurno) {
            textoAccion = favorable ? "Ofertar →" : "Invitar 48 h →";
        }
        JLabel accion = new JLabel(textoAccion);
        accion.setFont(Estilos.fuente(esTurno ? Font.BOLD : Font.PLAIN, 14));
        accion.setForeground(esTurno ? Estilos.TEXTO_PRINCIPAL : Estilos.SECUNDARIO);

        return fila(turno, nombre, ingreso, accion, esTurno ? Estilos.ESPERA : Estilos.TARJETA);
    }

    private void confirmarOferta() {
        Aula aula = (Aula) selectorAula.getSelectedItem();
        LocalDateTime ahora = LocalDateTime.now();
        Solicitud turno;
        try {
            turno = Turnos.siguiente(aula, solicitudes, matriculas.listar(), ahora);
        } catch (ReglaDominioException e) {
            mostrarError("No se pudo calcular el turno", e);
            return;
        }
        if (turno == null) {
            return;
        }
        String personal = JOptionPane.showInputDialog(this, "Personal que confirma la oferta:",
                "Confirmar oferta", JOptionPane.PLAIN_MESSAGE);
        if (personal == null) {
            return;
        }
        int respuesta = JOptionPane.showConfirmDialog(this,
                "¿El apoderado de " + turno.getAlumno().getNombreCompleto()
                        + " acepta la vacante en " + aula.getNombre() + "?",
                "Confirmar oferta", JOptionPane.YES_NO_CANCEL_OPTION);
        if (respuesta != JOptionPane.YES_OPTION && respuesta != JOptionPane.NO_OPTION) {
            return;
        }
        boolean aceptada = respuesta == JOptionPane.YES_OPTION;
        try {
            Turnos.confirmarOferta(turno, aceptada, personal, ahora, solicitudes,
                    matriculas.listar());
            if (aceptada) {
                Matricula creada = Matriculas.crear(turno, ahora, solicitudes, matriculas);
                JOptionPane.showMessageDialog(this, "Matrícula " + creada.getNumMatricula()
                        + " creada en PENDIENTE_PAGO. El pago vence el "
                        + formatear(creada.getVencimientoPagoOriginal()) + ".",
                        "Matrícula creada", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (ReglaDominioException e) {
            mostrarError("No se pudo confirmar", e);
        }
        refrescar();
    }

    private void mostrarError(String titulo, ReglaDominioException e) {
        JOptionPane.showMessageDialog(this, e.getMessage(), titulo, JOptionPane.WARNING_MESSAGE);
    }

    private JLabel comprobacion(boolean cumple, String texto) {
        return texto((cumple ? "✓  " : "☐  ") + texto,
                cumple ? Estilos.TEXTO_PRINCIPAL : Estilos.SECUNDARIO);
    }

    // Formato del diseño ("25 sep · 09:15"); el de Java para es-PE daría "25 sept.".
    private static String formatear(LocalDateTime fechaHora) {
        return fechaHora.getDayOfMonth() + " " + MESES[fechaHora.getMonthValue() - 1] + " · "
                + String.format("%02d:%02d", fechaHora.getHour(), fechaHora.getMinute());
    }

    private static JPanel fila(JComponent turno, JComponent solicitud, JComponent ingreso,
            JComponent accion, Color fondo) {
        JPanel fila = new JPanel(new GridBagLayout());
        fila.setBackground(fondo);
        fila.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Estilos.BORDE_TARJETA),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        // Ancho preferido 0: las columnas se reparten solo por peso y quedan alineadas entre filas.
        JComponent[] celdas = { turno, solicitud, ingreso, accion };
        double[] pesos = { 0.23, 0.35, 0.22, 0.2 };
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        for (int i = 0; i < celdas.length; i++) {
            celdas[i].setPreferredSize(new Dimension(0, celdas[i].getPreferredSize().height));
            c.weightx = pesos[i];
            fila.add(celdas[i], c);
        }
        return fila;
    }

    private static JLabel cabecera(String texto) {
        JLabel etiqueta = new JLabel(texto);
        etiqueta.setFont(Estilos.fuente(Font.BOLD, 12));
        etiqueta.setForeground(Estilos.SECUNDARIO);
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

    private void cargarEjemplo(ArregloAulas aulas) throws ReglaDominioException {
        Aula girasoles = aulas.buscar("AUL-01");
        Aula tulipanes = aulas.buscar("AUL-02");

        // Girasoles: 20 − 3 − 15 = 2, como el diseño. Tulipanes: 18 − 2 − 14 = 2.
        matricular(girasoles, 3, 15);
        matricular(tulipanes, 2, 14);

        favorable(girasoles, "Lucía", "Vega", LocalDateTime.of(2026, 9, 25, 9, 15));
        favorable(girasoles, "Mateo", "Rojas", LocalDateTime.of(2026, 9, 27, 11, 20));
        sinPago(girasoles, "Eva", "Díaz", LocalDateTime.of(2026, 9, 21, 8, 40));
        sinPago(girasoles, "Leo", "Silva", LocalDateTime.of(2026, 9, 26, 17, 30));
        sinPago(tulipanes, "Alma", "Torres", LocalDateTime.of(2026, 9, 22, 14, 5));
        sinPago(tulipanes, "Emilia", "Paz", LocalDateTime.of(2026, 9, 24, 10, 40));
    }

    // Recorre el flujo real (inscripción, documentos, oferta) para que cada matrícula pase
    // por Matriculas.crear. Las pendientes se crean hoy para no nacer vencidas (72 h).
    private void matricular(Aula aula, int pendientes, int activas) throws ReglaDominioException {
        LocalDateTime hoy = LocalDateTime.now();
        for (int i = 1; i <= pendientes + activas; i++) {
            boolean activa = i > pendientes;
            LocalDateTime creacion = activa ? LocalDateTime.of(2026, 9, 1, 9, 0).plusHours(i)
                    : hoy.minusHours(i);
            Solicitud solicitud = registrar(aula, "Alumno " + i, aula.getNombre());
            preparar(solicitud, creacion.minusDays(10), creacion.minusMinutes(30));
            Turnos.confirmarOferta(solicitud, true, "Personal de admisión",
                    creacion.minusMinutes(10), solicitudes, matriculas.listar());
            Matricula matricula = Matriculas.crear(solicitud, creacion, solicitudes, matriculas);
            if (activa) {
                matricula.activar();
            }
        }
    }

    private void favorable(Aula aula, String nombres, String apellidos, LocalDateTime ingreso)
            throws ReglaDominioException {
        preparar(registrar(aula, nombres, apellidos), ingreso.minusDays(10), ingreso);
    }

    private void preparar(Solicitud solicitud, LocalDateTime habilitacion,
            LocalDateTime ingresoFavorable) throws ReglaDominioException {
        solicitud.habilitarParaPago(habilitacion);
        solicitud.confirmarInscripcion(habilitacion.plusHours(1));
        for (Documento documento : solicitud.getExpediente().getDocumentos()) {
            documento.registrarEntrega(habilitacion.plusDays(2));
            documento.validar();
        }
        solicitud.ingresarAColaFavorable(ingresoFavorable);
    }

    private void sinPago(Aula aula, String nombres, String apellidos, LocalDateTime ingreso)
            throws ReglaDominioException {
        registrar(aula, nombres, apellidos).ingresarAColaSinPago(ingreso);
    }

    private Solicitud registrar(Aula aula, String nombres, String apellidos)
            throws ReglaDominioException {
        int edad = aula.getEdadRequerida();
        LocalDate nacimiento = FechasAdmision.CORTE_2027.minusYears(edad).minusMonths(6);
        Alumno alumno = new Alumno(siguienteDni, String.valueOf(siguienteDni), nombres, apellidos,
                nacimiento, "");
        siguienteDni++;
        alumno.agregarApoderado(new Apoderado(String.valueOf(siguienteDni), "Apoderado",
                apellidos, "987654321"), true);
        siguienteDni++;
        return solicitudes.registrar(alumno, aula);
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
}
