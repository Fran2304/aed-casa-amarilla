package ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import datos.ArregloAulas;
import modelo.Aula;
import modelo.EstadoSolicitud;
import modelo.Matricula;
import modelo.MedioPago;
import negocio.Vacantes;
import util.FechasAdmision;

public class PanelSolicitudes extends JPanel {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final PrincipalUI principal;

    private final JTextField campoDni = new JTextField(10);
    private final JTextField campoNacimiento = new JTextField("18/06/2022", 10);
    private final JComboBox<Aula> comboAulas;
    private final JLabel indicadorVacantes = new JLabel();
    private final JLabel franja = new JLabel();

    private final JTextField campoApoNombre = new JTextField(14);
    private final JTextField campoApoDni = new JTextField(8);
    private final JTextField campoApoTelefono = new JTextField(9);
    private final JCheckBox checkPrincipal = new JCheckBox("Principal");
    private final DefaultListModel<String> modeloApoderados = new DefaultListModel<>();
    private final List<boolean[]> marcasPrincipal = new ArrayList<>();

    // Sustituto temporal del módulo de registro (#7): guarda DNIs ya vistos
    // para mostrar la ficha existente. Cuando #7 exista, reemplazar por su búsqueda.
    private final Set<String> dnisRegistrados = new HashSet<>();
    private final Map<String, String> codigoPorDni = new HashMap<>();
    private int correlativoSol = 1042;
    // Punto de conexión con el registro de matrículas (#26): cuando exista la lista
    // central, pasarla al constructor en vez de esta lista local.
    private final ArrayList<Matricula> matriculas = new ArrayList<>();

    private final JPanel tarjetasInternas = new JPanel(new java.awt.CardLayout());
    private final JPanel panelFormulario = new JPanel(new BorderLayout(0, 12));
    private final JPanel panelFicha = new JPanel(new BorderLayout(0, 12));
    private final JLabel fichaTexto = new JLabel();
    private final JButton botonVerFicha = Estilos.botonSecundario("Ver solicitud");

    public PanelSolicitudes(PrincipalUI principal) {
        super(new BorderLayout(0, 16));
        this.principal = principal;
        setBorder(BorderFactory.createEmptyBorder(32, 32, 32, 34));

        ArregloAulas aulas = principal.getAulas();
        comboAulas = new JComboBox<>(aulas.listar().toArray(new Aula[0]));

        add(crearEncabezado(), BorderLayout.NORTH);
        panelFormulario.add(crearDatosAlumno(), BorderLayout.NORTH);
        panelFormulario.add(crearApoderados(), BorderLayout.CENTER);
        panelFormulario.add(crearAcciones(), BorderLayout.SOUTH);
        tarjetasInternas.add(panelFormulario, "FORM");
        tarjetasInternas.add(panelFicha, "FICHA");
        panelFicha.add(fichaTexto, BorderLayout.CENTER);
        JPanel fichaSur = new JPanel(new FlowLayout(FlowLayout.LEFT));
        fichaSur.setOpaque(false);
        botonVerFicha.addActionListener(e -> mostrarFicha(campoDni.getText().trim()));
        fichaSur.add(botonVerFicha);
        JButton botonVolver = Estilos.botonSecundario("Nueva búsqueda");
        botonVolver.addActionListener(e -> mostrarFormulario());
        fichaSur.add(botonVolver);
        panelFicha.add(fichaSur, BorderLayout.SOUTH);
        panelFicha.setOpaque(false);

        JPanel centro = new JPanel(new BorderLayout(0, 16));
        centro.setOpaque(false);
        centro.add(tarjetasInternas, BorderLayout.CENTER);
        centro.add(crearRuta(), BorderLayout.SOUTH);
        add(centro, BorderLayout.CENTER);

        DocumentListener recalcular = soloRecalcular();
        campoDni.getDocument().addDocumentListener(recalcular);
        campoNacimiento.getDocument().addDocumentListener(recalcular);
        comboAulas.addActionListener(e -> recalculaFranja());
        recalculaFranja();
        actualizaVacantes();
    }

    private JPanel crearEncabezado() {
        JPanel encabezado = new JPanel(new BorderLayout());
        encabezado.setOpaque(false);
        JLabel titulo = new JLabel("Nueva solicitud · comprobaciones");
        titulo.setFont(Estilos.panel());
        titulo.setForeground(Estilos.TEXTO_PRINCIPAL);
        encabezado.add(titulo, BorderLayout.WEST);
        encabezado.add(Estilos.chip(EstadoSolicitud.EN_ESPERA_SIN_PAGO), BorderLayout.EAST);
        return encabezado;
    }

    private JPanel crearDatosAlumno() {
        JPanel tarjeta = Estilos.tarjeta(new JPanel(new BorderLayout(0, 8)));
        tarjeta.setBorder(BorderFactory.createCompoundBorder(
                tarjeta.getBorder(), BorderFactory.createEmptyBorder(16, 16, 16, 16)));
        JPanel campos = new JPanel(new GridLayout(3, 2, 8, 8));
        campos.setOpaque(false);
        campos.add(new JLabel("DNI alumno (8 dígitos):"));
        campos.add(campoDni);
        campos.add(new JLabel("Nacimiento (dd/mm/aaaa):"));
        campos.add(campoNacimiento);
        campos.add(new JLabel("Aula solicitada:"));
        campos.add(comboAulas);
        tarjeta.add(campos, BorderLayout.CENTER);

        JPanel pie = new JPanel(new BorderLayout());
        pie.setOpaque(false);
        indicadorVacantes.setFont(Estilos.contenido());
        pie.add(indicadorVacantes, BorderLayout.WEST);
        franja.setOpaque(true);
        franja.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        pie.add(franja, BorderLayout.SOUTH);
        tarjeta.add(pie, BorderLayout.SOUTH);
        return tarjeta;
    }

    private JPanel crearApoderados() {
        JPanel tarjeta = Estilos.tarjeta(new JPanel(new BorderLayout(0, 8)));
        tarjeta.setBorder(BorderFactory.createCompoundBorder(
                tarjeta.getBorder(), BorderFactory.createEmptyBorder(16, 16, 16, 16)));
        JLabel titulo = new JLabel("Apoderados (principal / adicional)");
        titulo.setFont(Estilos.contenido());
        tarjeta.add(titulo, BorderLayout.NORTH);

        JPanel fila = new JPanel(new FlowLayout(FlowLayout.LEFT));
        fila.setOpaque(false);
        fila.add(new JLabel("Nombre:"));
        fila.add(campoApoNombre);
        fila.add(new JLabel("DNI:"));
        fila.add(campoApoDni);
        fila.add(new JLabel("Teléfono:"));
        fila.add(campoApoTelefono);
        fila.add(checkPrincipal);
        JButton agregar = Estilos.botonSecundario("Agregar");
        agregar.addActionListener(e -> agregarApoderado());
        fila.add(agregar);
        tarjeta.add(fila, BorderLayout.CENTER);
        tarjeta.add(new JScrollPane(new JList<>(modeloApoderados)), BorderLayout.SOUTH);
        return tarjeta;
    }

    private JPanel crearAcciones() {
        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.LEFT));
        acciones.setOpaque(false);
        JButton guardar = Estilos.botonPrimario("Guardar y evaluar turno");
        guardar.addActionListener(e -> guardar());
        acciones.add(guardar);
        return acciones;
    }

    private JPanel crearRuta() {
        JPanel tarjeta = Estilos.tarjeta(new JPanel(new GridLayout(0, 1)));
        tarjeta.setBorder(BorderFactory.createCompoundBorder(
                tarjeta.getBorder(), BorderFactory.createEmptyBorder(16, 16, 16, 16)));
        JLabel titulo = new JLabel("Ruta de inscripción");
        titulo.setFont(Estilos.contenido());
        tarjeta.add(titulo);
        tarjeta.add(new JLabel("Estado inicial: EN ESPERA SIN PAGO · invitación 48 h desde la cola sin pago."));
        tarjeta.add(new JLabel("El pago exige: concepto, monto, medio, fecha real, n° operación (salvo efectivo), comprobante."));
        tarjeta.add(new JLabel("Medios admitidos: " + mediosAdmitidos() + "."));
        return tarjeta;
    }

    private static String mediosAdmitidos() {
        StringBuilder nombres = new StringBuilder();
        for (MedioPago medio : MedioPago.values()) {
            if (nombres.length() > 0) {
                nombres.append(", ");
            }
            nombres.append(medio.name().toLowerCase());
        }
        return nombres.toString();
    }

    private void agregarApoderado() {
        String nombre = campoApoNombre.getText().trim();
        String dni = campoApoDni.getText().trim();
        String telefono = campoApoTelefono.getText().trim();
        if (nombre.isEmpty() || dni.isEmpty()) {
            return;
        }
        if (checkPrincipal.isSelected()) {
            for (int i = 0; i < marcasPrincipal.size(); i++) {
                marcasPrincipal.set(i, new boolean[]{false});
            }
            for (int i = 0; i < modeloApoderados.size(); i++) {
                modeloApoderados.set(i, modeloApoderados.get(i).replace(" [principal]", " [adicional]"));
            }
        }
        marcasPrincipal.add(new boolean[]{checkPrincipal.isSelected()});
        modeloApoderados.addElement(nombre + " · " + dni + " · " + telefono
                + (checkPrincipal.isSelected() ? " [principal]" : " [adicional]"));
        campoApoNombre.setText("");
        campoApoDni.setText("");
        campoApoTelefono.setText("");
        checkPrincipal.setSelected(false);
    }

    private void guardar() {
        String dni = campoDni.getText().trim();
        if (dnisRegistrados.contains(dni)) {
            mostrarFicha(dni);
            return;
        }
        // Mientras #7 no exista, el stub exige lo mismo que el flujo §4.1:
        // al menos un apoderado y exactamente un principal.
        if (modeloApoderados.isEmpty()) {
            pintarFranja("Registra al menos un apoderado antes de guardar.", false);
            return;
        }
        int principales = 0;
        for (boolean[] marca : marcasPrincipal) {
            if (marca[0]) {
                principales++;
            }
        }
        if (principales != 1) {
            pintarFranja("Designa exactamente un apoderado principal.", false);
            return;
        }
        // TODO(#7): reemplazar por el módulo de registro con validaciones completas.
        String codigo = "SOL-" + (correlativoSol++);
        dnisRegistrados.add(dni);
        codigoPorDni.put(dni, codigo);
        franja.setText("Guardada " + codigo + " · a la cola EN_ESPERA_SIN_PAGO.");
        franja.setBackground(Estilos.FAVORABLE);
        franja.setForeground(Estilos.TEXTO_PRINCIPAL);
    }

    private void mostrarFicha(String dni) {
        String codigo = codigoPorDni.getOrDefault(dni, "SOL-1042");
        fichaTexto.setText("Ya existe solicitud activa para DNI " + dni + " → " + codigo + ".");
        botonVerFicha.setText("Ver " + codigo);
        ((java.awt.CardLayout) tarjetasInternas.getLayout()).show(tarjetasInternas, "FICHA");
        franja.setText("DNI con solicitud activa · ver " + codigo + ".");
        franja.setBackground(Estilos.ALERTA);
        franja.setForeground(Estilos.TEXTO_ALERTA);
    }

    private void mostrarFormulario() {
        ((java.awt.CardLayout) tarjetasInternas.getLayout()).show(tarjetasInternas, "FORM");
        recalculaFranja();
    }

    private void actualizaVacantes() {
        Aula aula = (Aula) comboAulas.getSelectedItem();
        if (aula == null) {
            indicadorVacantes.setText("");
            return;
        }
        // Vacantes calculadas con el módulo real (#20): no se reservan al consultar.
        int vacantes = Vacantes.calcular(aula, matriculas);
        indicadorVacantes.setText(aula.getNombre() + " · " + vacantes
                + " vacantes disponibles · no se reservan al consultar.");
    }

    private DocumentListener soloRecalcular() {
        return new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                recalculaFranja();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                recalculaFranja();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                recalculaFranja();
            }
        };
    }

    private void recalculaFranja() {
        actualizaVacantes();
        String dni = campoDni.getText().trim();
        Aula aula = (Aula) comboAulas.getSelectedItem();
        LocalDate nacimiento = parsearNacimiento();
        if (!dni.matches("\\d{8}")) {
            pintarFranja("DNI incompleto: exige 8 dígitos.", false);
            return;
        }
        if (nacimiento == null) {
            pintarFranja("Fecha inválida: usa dd/mm/aaaa.", false);
            return;
        }
        if (aula == null) {
            pintarFranja("Elige un aula.", false);
            return;
        }
        int edad = Period.between(nacimiento, FechasAdmision.CORTE_2027).getYears();
        boolean compatible = edad == aula.getEdadRequerida();
        if (dnisRegistrados.contains(dni)) {
            pintarFranja("DNI con solicitud activa · ver " + codigoPorDni.get(dni) + ".", false);
            return;
        }
        if (compatible) {
            pintarFranja(edad + " años al 31 mar 2027 · aula compatible.", true);
        } else {
            pintarFranja(edad + " años al 31 mar 2027 · no compatible con "
                    + aula.getNombre() + " (" + aula.getEdadRequerida() + " años).", false);
        }
    }

    private LocalDate parsearNacimiento() {
        try {
            return LocalDate.parse(campoNacimiento.getText().trim(), FORMATO_FECHA);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private void pintarFranja(String texto, boolean favorable) {
        franja.setText(texto);
        franja.setBackground(favorable ? Estilos.FAVORABLE : Estilos.ALERTA);
        franja.setForeground(favorable ? Estilos.TEXTO_PRINCIPAL : Estilos.TEXTO_ALERTA);
    }
}
