package ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import datos.ArregloAlumnos;
import datos.ArregloApoderados;
import datos.ArregloAulas;
import datos.ArregloMatriculas;
import datos.ArregloSolicitudes;
import modelo.Alumno;
import modelo.Apoderado;
import modelo.ApoderadoDuplicadoException;
import modelo.Aula;
import modelo.DatoInvalidoException;
import modelo.EstadoSolicitud;
import modelo.ReglaDominioException;
import modelo.Solicitud;
import modelo.Validaciones;
import negocio.Turnos;
import negocio.Vacantes;
import negocio.Vencimientos;
import util.FechasAdmision;
import util.FormatoFecha;
import util.Reloj;

public class PanelSolicitudes extends JPanel {

    private static final String FORMULARIO = "FORMULARIO";
    private static final String FICHA = "FICHA";
    private static final DateTimeFormatter FORMATO_NACIMIENTO =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
    private static final int ANCHO_TEXTO_RUTA = 250;
    private static final int ANCHO_TEXTO_FICHA = 440;

    private final ArregloAulas aulas;
    private final ArregloSolicitudes solicitudes;
    private final ArregloAlumnos alumnos;
    private final ArregloApoderados apoderados;
    private final ArregloMatriculas matriculas;

    private final CardLayout vistas = new CardLayout();
    private final JPanel tarjetaIzquierda = Estilos.tarjeta(new JPanel(vistas));
    private final JPanel ficha = new JPanel();

    private final JTextField campoDni = Estilos.campo(new JTextField());
    private final JTextField campoNacimiento = Estilos.campo(new JTextField());
    private final JTextField campoNombres = Estilos.campo(new JTextField());
    private final JTextField campoApellidos = Estilos.campo(new JTextField());
    private final JComboBox<Aula> comboAula = Estilos.campo(new JComboBox<Aula>());

    private final JPanel franja = new Fila(new BorderLayout(12, 0));
    private final JLabel textoFranja = new JLabel(" ");
    private final JButton botonVerSolicitud = Estilos.botonSecundario("Ver");
    private final JPanel cajaVacantes = new Fila(new BorderLayout());
    private final JLabel textoVacantes = new JLabel(" ");

    private final JPanel listaApoderados = new Fila(null);
    private final JTextField campoDniApoderado = Estilos.campo(new JTextField());
    private final JTextField campoNombresApoderado = Estilos.campo(new JTextField());
    private final JTextField campoApellidosApoderado = Estilos.campo(new JTextField());
    private final JTextField campoCelularApoderado = Estilos.campo(new JTextField());
    private final JLabel mensajeApoderados = new JLabel(" ");

    private final JButton botonGuardar = Estilos.botonPrimario("Guardar y evaluar turno");
    private final JLabel mensajeGuardar = new JLabel(" ");

    private final ArrayList<FilaApoderado> filas = new ArrayList<FilaApoderado>();
    private String dniPrincipal;
    private Alumno alumnoExistente;
    private final JLabel avisoAlumno = new JLabel(" ");
    private String borradorNombres = "";
    private String borradorApellidos = "";
    private String borradorNacimiento = "";
    private Apoderado apoderadoSugerido;
    private Solicitud duplicada;
    private boolean franjaValida;
    private boolean actualizando;

    private Solicitud fichaActual;
    private boolean fichaRecienCreada;
    private String fichaError;
    private ArrayList<Solicitud> fichaPromovidas = new ArrayList<Solicitud>();

    public PanelSolicitudes(ArregloAulas aulas, ArregloSolicitudes solicitudes,
            ArregloAlumnos alumnos, ArregloApoderados apoderados,
            ArregloMatriculas matriculas) {
        super(new BorderLayout(0, 20));
        this.aulas = aulas;
        this.solicitudes = solicitudes;
        this.alumnos = alumnos;
        this.apoderados = apoderados;
        this.matriculas = matriculas;
        setBorder(Estilos.MARGEN_PANTALLA);

        add(crearEncabezado(), BorderLayout.NORTH);

        JPanel contenido = new JPanel(new GridBagLayout());
        contenido.setOpaque(false);
        GridBagConstraints restricciones = new GridBagConstraints();
        restricciones.gridy = 0;
        restricciones.anchor = GridBagConstraints.NORTH;
        restricciones.fill = GridBagConstraints.BOTH;
        restricciones.weightx = 0.64;
        restricciones.insets = new Insets(0, 0, 0, 20);
        Estilos.rellenarTarjeta(tarjetaIzquierda);
        tarjetaIzquierda.add(crearFormulario(), FORMULARIO);
        ficha.setLayout(new BoxLayout(ficha, BoxLayout.Y_AXIS));
        ficha.setOpaque(false);
        tarjetaIzquierda.add(ficha, FICHA);
        contenido.add(tarjetaIzquierda, restricciones);
        restricciones.weightx = 0.36;
        restricciones.insets = new Insets(0, 0, 0, 0);
        contenido.add(crearRuta(), restricciones);
        restricciones.gridy = 1;
        restricciones.gridwidth = 2;
        restricciones.insets = new Insets(24, 0, 0, 0);
        contenido.add(nota("Inscripción no reembolsable. Una nueva solicitud tras rechazo o"
                + " cancelación requiere otro pago de inscripción.", Estilos.SECUNDARIO),
                restricciones);

        PanelAncho envoltura = new PanelAncho();
        envoltura.add(contenido, BorderLayout.NORTH);
        JScrollPane desplazable = new JScrollPane(envoltura);
        desplazable.setBorder(null);
        desplazable.setOpaque(false);
        desplazable.getViewport().setOpaque(false);
        desplazable.getVerticalScrollBar().setUnitIncrement(16);
        add(desplazable, BorderLayout.CENTER);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                recalcular();
                if (fichaActual != null) {
                    String error = fichaActual.getEstado() == EstadoSolicitud.EN_ESPERA_SIN_PAGO
                            ? fichaError : null;
                    mostrarFicha(fichaActual, fichaRecienCreada, error,
                            AvisoHabilitadas.vigentes(fichaPromovidas));
                }
            }
        });
        refrescarApoderados();
        recalcular();
    }

    private JPanel crearEncabezado() {
        JPanel encabezado = new JPanel();
        encabezado.setLayout(new BoxLayout(encabezado, BoxLayout.Y_AXIS));
        encabezado.setOpaque(false);
        JLabel titulo = new JLabel("Nueva solicitud · comprobaciones");
        titulo.setFont(Estilos.titulo());
        titulo.setForeground(Estilos.TEXTO_PRINCIPAL);
        encabezado.add(Estilos.alineado(titulo));
        encabezado.add(Box.createVerticalStrut(6));
        encabezado.add(Estilos.alineado(nota("Validar antes de guardar o pedir pago · 2027 ·"
                + " Una solicitud activa por alumno.", Estilos.SECUNDARIO)));
        return encabezado;
    }

    private JPanel crearFormulario() {
        JPanel formulario = new JPanel();
        formulario.setLayout(new BoxLayout(formulario, BoxLayout.Y_AXIS));
        formulario.setOpaque(false);

        formulario.add(Estilos.alineado(tituloTarjeta("Datos del alumno y apoderados")));
        formulario.add(Box.createVerticalStrut(18));
        formulario.add(dosColumnas(Estilos.conEtiqueta("DNI alumno *", campoDni),
                Estilos.conEtiqueta("Nacimiento * (dd/mm/aaaa)", campoNacimiento)));
        formulario.add(Box.createVerticalStrut(14));
        formulario.add(dosColumnas(Estilos.conEtiqueta("Nombres *", campoNombres),
                Estilos.conEtiqueta("Apellidos *", campoApellidos)));
        formulario.add(Box.createVerticalStrut(6));
        avisoAlumno.setFont(Estilos.detalle());
        avisoAlumno.setForeground(Estilos.SECUNDARIO);
        formulario.add(Estilos.alineado(avisoAlumno));
        formulario.add(Box.createVerticalStrut(8));

        textoFranja.setFont(Estilos.etiqueta());
        franja.add(textoFranja, BorderLayout.CENTER);
        botonVerSolicitud.addActionListener(evento -> {
            if (duplicada != null) {
                mostrarFicha(duplicada, false, null, new ArrayList<Solicitud>());
            }
        });
        franja.add(botonVerSolicitud, BorderLayout.EAST);
        formulario.add(Estilos.alineado(franja));
        formulario.add(Box.createVerticalStrut(14));

        for (Aula aula : aulas.listar()) {
            comboAula.addItem(aula);
        }
        comboAula.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> lista, Object valor,
                    int indice, boolean seleccionado, boolean conFoco) {
                Object texto = valor;
                if (valor instanceof Aula) {
                    texto = textoAula((Aula) valor);
                }
                return super.getListCellRendererComponent(lista, texto, indice, seleccionado,
                        conFoco);
            }
        });
        comboAula.addActionListener(evento -> recalcular());
        textoVacantes.setFont(Estilos.etiqueta());
        cajaVacantes.add(textoVacantes, BorderLayout.CENTER);
        formulario.add(dosColumnas(Estilos.conEtiqueta("Aula solicitada *", comboAula),
                Estilos.conEtiqueta("Vacantes calculadas", cajaVacantes)));
        formulario.add(Box.createVerticalStrut(22));

        JLabel seccion = new JLabel("Apoderados · uno o más; exactamente uno principal");
        seccion.setFont(Estilos.seccion());
        seccion.setForeground(Estilos.TEXTO_PRINCIPAL);
        formulario.add(Estilos.alineado(seccion));
        formulario.add(Box.createVerticalStrut(10));
        listaApoderados.setLayout(new BoxLayout(listaApoderados, BoxLayout.Y_AXIS));
        listaApoderados.setOpaque(false);
        formulario.add(Estilos.alineado(listaApoderados));
        formulario.add(crearIngresoApoderado());
        formulario.add(Box.createVerticalStrut(6));
        mensajeApoderados.setFont(Estilos.detalle());
        mensajeApoderados.setForeground(Estilos.TEXTO_ALERTA);
        formulario.add(Estilos.alineado(mensajeApoderados));
        formulario.add(Box.createVerticalStrut(12));

        formulario.add(Estilos.alineado(texto(
                "Si existe una solicitud activa, abrir la ficha existente;"
                + " las canceladas y rechazadas siguen visibles en historial.",
                Estilos.SECUNDARIO, ANCHO_TEXTO_FICHA)));
        formulario.add(Box.createVerticalStrut(12));
        JPanel acciones = new Fila(new FlowLayout(FlowLayout.LEFT, 0, 0));
        acciones.setOpaque(false);
        botonGuardar.addActionListener(evento -> guardar());
        acciones.add(botonGuardar);
        acciones.add(Box.createHorizontalStrut(10));
        JButton botonLimpiar = Estilos.botonSecundario("Limpiar formulario");
        botonLimpiar.addActionListener(evento -> limpiar());
        acciones.add(botonLimpiar);
        acciones.add(Box.createHorizontalStrut(14));
        mensajeGuardar.setFont(Estilos.detalle());
        acciones.add(mensajeGuardar);
        formulario.add(Estilos.alineado(acciones));
        formulario.add(Box.createVerticalGlue());

        alCambiar(campoDni);
        alCambiar(campoNacimiento);
        alCambiar(campoNombres);
        alCambiar(campoApellidos);
        campoDniApoderado.getDocument().addDocumentListener(new Escucha(this::sugerirApoderado));
        return formulario;
    }

    private JPanel crearIngresoApoderado() {
        JPanel ingreso = new Fila(new GridBagLayout());
        ingreso.setOpaque(false);
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.SOUTH;
        c.insets = new Insets(4, 0, 0, 10);
        c.weightx = 0.8;
        ingreso.add(Estilos.conEtiqueta("DNI apoderado", campoDniApoderado), c);
        c.weightx = 1.2;
        ingreso.add(Estilos.conEtiqueta("Nombres", campoNombresApoderado), c);
        ingreso.add(Estilos.conEtiqueta("Apellidos", campoApellidosApoderado), c);
        c.weightx = 0.8;
        ingreso.add(Estilos.conEtiqueta("Celular", campoCelularApoderado), c);
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        c.insets = new Insets(4, 0, 0, 0);
        JButton agregar = Estilos.botonSecundario("Agregar");
        agregar.addActionListener(evento -> agregarApoderado());
        campoCelularApoderado.addActionListener(evento -> agregarApoderado());
        ingreso.add(agregar, c);
        return Estilos.alineado(ingreso);
    }

    private JPanel crearRuta() {
        JPanel ruta = Estilos.tarjeta(new JPanel());
        ruta.setLayout(new BoxLayout(ruta, BoxLayout.Y_AXIS));
        Estilos.rellenarTarjeta(ruta);

        ruta.add(Estilos.alineado(tituloTarjeta("Ruta de inscripción")));
        ruta.add(Box.createVerticalStrut(18));
        ruta.add(Estilos.alineado(pasoDestacado(Estilos.ESPERA, Estilos.TEXTO_ESPERA,
                "EN_ESPERA_SIN_PAGO",
                "Fecha de ingreso a cola. Sin cobro, revisión documental ni entrevista.")));
        ruta.add(Box.createVerticalStrut(18));
        ruta.add(Estilos.alineado(paso("Invitación al llegar su turno",
                "48 h desde que se habilita, sin reserva de vacante. Corre incluso durante la"
                        + " recepción y verificación del pago.")));
        ruta.add(Box.createVerticalStrut(18));
        ruta.add(Estilos.alineado(paso("Pago de inscripción",
                "Registrar monto aplicado, concepto, medio (efectivo, transferencia, tarjeta,"
                        + " Yape o Plin), fecha real, n° de operación salvo efectivo y"
                        + " comprobante.")));
        ruta.add(Box.createVerticalStrut(18));
        ruta.add(Estilos.alineado(pasoDestacado(Estilos.FAVORABLE, Estilos.BOTON_PRINCIPAL,
                "CONFIRMADO → EN_DOCUMENTACION",
                "Abre 7 días calendario; no reserva cupo.")));
        ruta.add(Box.createVerticalStrut(12));
        ruta.add(Estilos.alineado(texto(
                "Si vence la invitación, cerrar y mover al final de su cola"
                + " con nuevo ingreso.", Estilos.TEXTO_ESPERA, ANCHO_TEXTO_RUTA)));
        ruta.add(Box.createVerticalGlue());
        return ruta;
    }

    private void recalcular() {
        if (actualizando) {
            return;
        }
        // Las vacantes y el duplicado dependen de plazos que pudieron vencer con la
        // aplicación abierta (§4.2): una matrícula vencida aún restaría su cupo y una
        // solicitud con documentación vencida aún bloquearía el guardado.
        String avisoVencimientos = revisarVencimientos();
        revisarAlumnoExistente();
        actualizarVacantes();
        actualizarFranja();
        actualizarBotonGuardar();
        if (avisoVencimientos != null) {
            mensajeGuardar.setForeground(Estilos.TEXTO_ALERTA);
            mensajeGuardar.setText("Revisión de vencimientos incompleta: " + avisoVencimientos);
        }
    }

    private String revisarVencimientos() {
        try {
            ArrayList<String> avisos = Vencimientos.revisar(Reloj.porDefecto().now(),
                    solicitudes, matriculas);
            return avisos.isEmpty() ? null : Vencimientos.unir(avisos);
        } catch (DatoInvalidoException e) {
            return e.getMessage();
        }
    }

    private void revisarAlumnoExistente() {
        String dni = campoDni.getText().trim();
        Alumno encontrado = dniCompleto(dni) ? alumnos.buscarPorDni(dni) : null;
        if (encontrado == alumnoExistente) {
            return;
        }
        Alumno anterior = alumnoExistente;
        alumnoExistente = encontrado;

        actualizando = true;
        if (encontrado != null) {
            if (anterior == null) {
                borradorNombres = campoNombres.getText();
                borradorApellidos = campoApellidos.getText();
                borradorNacimiento = campoNacimiento.getText();
            }
            campoNombres.setText(encontrado.getNombres());
            campoApellidos.setText(encontrado.getApellidos());
            campoNacimiento.setText(encontrado.getFechaNacimiento().format(FORMATO_NACIMIENTO));
        } else if (anterior != null) {
            campoNombres.setText(borradorNombres);
            campoApellidos.setText(borradorApellidos);
            campoNacimiento.setText(borradorNacimiento);
        }
        actualizando = false;
        actualizarAvisoAlumno();
        bloquearDatosAlumno(encontrado != null);

        for (int i = filas.size() - 1; i >= 0; i--) {
            if (filas.get(i).vinculado) {
                filas.remove(i);
            }
        }
        if (encontrado != null) {
            for (Apoderado apoderado : encontrado.getApoderados()) {
                FilaApoderado pendiente = buscarFila(apoderado.getDni());
                if (pendiente != null) {
                    filas.remove(pendiente);
                }
                FilaApoderado fila = new FilaApoderado(apoderado);
                fila.vinculado = true;
                filas.add(fila);
            }
            if (encontrado.getPrincipal() != null) {
                dniPrincipal = encontrado.getPrincipal().getDni();
            }
        }
        corregirPrincipal();
        refrescarApoderados();
    }

    private void actualizarAvisoAlumno() {
        if (alumnoExistente == null) {
            avisoAlumno.setText(" ");
            return;
        }
        String texto = "Alumno ya registrado: se muestran sus datos guardados.";
        if (!(borradorNombres + borradorApellidos + borradorNacimiento).trim().isEmpty()) {
            texto += " Lo que escribiste vuelve si cambias el DNI.";
        }
        avisoAlumno.setText(texto);
    }

    private void bloquearDatosAlumno(boolean bloquear) {
        Estilos.editable(campoNombres, !bloquear);
        Estilos.editable(campoApellidos, !bloquear);
        Estilos.editable(campoNacimiento, !bloquear);
    }

    private void actualizarVacantes() {
        Aula aula = (Aula) comboAula.getSelectedItem();
        if (aula == null) {
            return;
        }
        int vacantes = Vacantes.calcular(aula, matriculas);
        if (vacantes > 0) {
            pintar(cajaVacantes, textoVacantes, Estilos.ESPERA, Estilos.TEXTO_ESPERA,
                    vacantes + (vacantes == 1 ? " disponible" : " disponibles")
                            + " · no se reservan");
        } else {
            pintar(cajaVacantes, textoVacantes, Estilos.ALERTA, Estilos.TEXTO_ALERTA,
                    "Sin vacantes · ingresará a la cola");
        }
    }

    private void actualizarFranja() {
        franjaValida = false;
        duplicada = null;
        botonVerSolicitud.setVisible(false);

        String dni = campoDni.getText().trim();
        LocalDate nacimiento = leerNacimiento();
        Aula aula = (Aula) comboAula.getSelectedItem();
        if (!dniCompleto(dni) || nacimiento == null || aula == null) {
            pintarFranja(Estilos.FONDO_CAMPO, Estilos.SECUNDARIO,
                    "Completa el DNI (8 dígitos) y el nacimiento (dd/mm/aaaa) para validar.");
            return;
        }
        if (nacimiento.isAfter(Reloj.porDefecto().fechaActual())) {
            pintarFranja(Estilos.ALERTA, Estilos.TEXTO_ALERTA,
                    "La fecha de nacimiento no puede ser futura.");
            return;
        }

        int edad = FechasAdmision.edadAlCorte(nacimiento);
        String textoEdad = edad + (edad == 1 ? " año" : " años") + " al 31 mar 2027";
        if (!aula.admiteEdad(edad)) {
            pintarFranja(Estilos.ALERTA, Estilos.TEXTO_ALERTA, textoEdad + " · el aula "
                    + aula.getNombre() + " es para " + aula.getEdadRequerida() + " años");
            return;
        }
        String textoAula = "Aula " + aula.getNombre() + " compatible";
        if (alumnoExistente != null) {
            duplicada = solicitudes.buscarActivaDe(alumnoExistente);
        }
        if (duplicada != null) {
            pintarFranja(Estilos.ALERTA, Estilos.TEXTO_ALERTA, "Ya tiene una solicitud activa"
                    + " en 2027: no se crea otra");
            botonVerSolicitud.setText("Ver " + duplicada.getCodigo());
            botonVerSolicitud.setVisible(true);
            return;
        }
        pintarFranja(Estilos.FAVORABLE, Estilos.BOTON_PRINCIPAL, "✓ " + textoEdad + " · "
                + textoAula + " · Sin solicitud activa duplicada");
        franjaValida = true;
    }

    private void pintarFranja(Color fondo, Color texto, String mensaje) {
        pintar(franja, textoFranja, fondo, texto, mensaje);
    }

    private void actualizarBotonGuardar() {
        String falta = null;
        if (alumnoExistente == null && (campoNombres.getText().trim().isEmpty()
                || campoApellidos.getText().trim().isEmpty())) {
            falta = "Completa nombres y apellidos del alumno.";
        } else if (filas.isEmpty()) {
            falta = "Agrega al menos un apoderado.";
        }
        botonGuardar.setEnabled(franjaValida && falta == null);
        mensajeGuardar.setForeground(Estilos.SECUNDARIO);
        mensajeGuardar.setText(franjaValida && falta != null ? falta : " ");
    }

    private void sugerirApoderado() {
        if (actualizando) {
            return;
        }
        String dni = campoDniApoderado.getText().trim();
        Apoderado encontrado = dniCompleto(dni) ? apoderados.buscar(dni) : null;
        if (encontrado == apoderadoSugerido) {
            return;
        }
        apoderadoSugerido = encontrado;
        actualizando = true;
        campoNombresApoderado.setText(encontrado == null ? "" : encontrado.getNombres());
        campoApellidosApoderado.setText(encontrado == null ? "" : encontrado.getApellidos());
        campoCelularApoderado.setText(encontrado == null ? "" : encontrado.getCelular());
        actualizando = false;
        bloquearDatosApoderado(encontrado != null);
    }

    private void bloquearDatosApoderado(boolean bloquear) {
        Estilos.editable(campoNombresApoderado, !bloquear);
        Estilos.editable(campoApellidosApoderado, !bloquear);
        Estilos.editable(campoCelularApoderado, !bloquear);
    }

    private void agregarApoderado() {
        mensajeApoderados.setText(" ");
        try {
            String dni = Validaciones.exigirDni(campoDniApoderado.getText());
            if (buscarFila(dni) != null) {
                throw new DatoInvalidoException("El DNI " + dni
                        + " ya está en la lista de apoderados.");
            }
            Apoderado registrado = apoderados.buscar(dni);
            FilaApoderado fila;
            if (registrado != null) {
                fila = new FilaApoderado(registrado);
            } else {
                fila = new FilaApoderado(dni,
                        Validaciones.exigirNoVacio("nombres del apoderado",
                                campoNombresApoderado.getText()),
                        Validaciones.exigirNoVacio("apellidos del apoderado",
                                campoApellidosApoderado.getText()),
                        Validaciones.exigirCelular(campoCelularApoderado.getText()));
            }
            filas.add(fila);
            corregirPrincipal();
            limpiarIngresoApoderado();
            refrescarApoderados();
            recalcular();
        } catch (DatoInvalidoException e) {
            mensajeApoderados.setText(e.getMessage());
        }
    }

    private void quitarApoderado(FilaApoderado fila) {
        filas.remove(fila);
        corregirPrincipal();
        mensajeApoderados.setText(" ");
        refrescarApoderados();
        recalcular();
    }

    private void limpiarIngresoApoderado() {
        campoDniApoderado.setText("");
        campoNombresApoderado.setText("");
        campoApellidosApoderado.setText("");
        campoCelularApoderado.setText("");
    }

    private void corregirPrincipal() {
        if (dniPrincipal != null && buscarFila(dniPrincipal) != null) {
            return;
        }
        dniPrincipal = filas.isEmpty() ? null : filas.get(0).dni;
    }

    private FilaApoderado buscarFila(String dni) {
        for (FilaApoderado fila : filas) {
            if (fila.dni.equals(dni)) {
                return fila;
            }
        }
        return null;
    }

    private void refrescarApoderados() {
        listaApoderados.removeAll();
        if (filas.isEmpty()) {
            listaApoderados.add(Estilos.alineado(nota(
                    "Aún no hay apoderados. El primero que agregues"
                    + " queda como principal.", Estilos.SECUNDARIO)));
            listaApoderados.add(Box.createVerticalStrut(10));
        }
        for (FilaApoderado fila : filas) {
            listaApoderados.add(Estilos.alineado(crearFilaApoderado(fila)));
            listaApoderados.add(Box.createVerticalStrut(10));
        }
        listaApoderados.revalidate();
        listaApoderados.repaint();
    }

    private JPanel crearFilaApoderado(FilaApoderado fila) {
        boolean esPrincipal = fila.dni.equals(dniPrincipal);
        JPanel panel = Estilos.aviso(new Fila(new BorderLayout()), Estilos.FONDO_CAMPO);
        String origen = "";
        if (fila.vinculado) {
            origen = "  ·  ya vinculado";
        } else if (fila.registrado != null) {
            origen = "  ·  ya en el padrón";
        }
        Apoderado principalActual = alumnoExistente == null ? null
                : alumnoExistente.getPrincipal();
        if (esPrincipal && principalActual != null
                && !principalActual.getDni().equals(fila.dni)) {
            origen += "  ·  reemplaza a " + principalActual.getNombreCompleto()
                    + " como principal al guardar";
        }
        JLabel texto = new JLabel((esPrincipal ? "●  Principal" : "○  Adicional") + " · "
                + fila.nombres + " " + fila.apellidos + " · DNI " + fila.dni + " · "
                + AvisoHabilitadas.celular(fila.celular) + origen);
        texto.setFont(Estilos.contenido());
        texto.setForeground(Estilos.TEXTO_PRINCIPAL);
        panel.add(texto, BorderLayout.CENTER);
        if (!esPrincipal) {
            panel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            panel.setToolTipText("Clic para marcarlo como apoderado principal");
            panel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    dniPrincipal = fila.dni;
                    refrescarApoderados();
                }
            });
        }
        if (!fila.vinculado) {
            JButton quitar = Estilos.botonSecundario("Quitar");
            quitar.addActionListener(evento -> quitarApoderado(fila));
            panel.add(quitar, BorderLayout.EAST);
        }
        return panel;
    }

    private void guardar() {
        // Entre la última tecla y el clic pudo vencer un plazo: se revisa otra vez para
        // no guardar con vacantes o un duplicado que ya no corresponden.
        recalcular();
        if (!botonGuardar.isEnabled()) {
            return;
        }
        Aula aula = (Aula) comboAula.getSelectedItem();
        Solicitud nueva = null;
        try {
            Alumno alumno = alumnoExistente;
            if (alumno == null) {
                alumno = alumnos.registrar(campoDni.getText(), campoNombres.getText(),
                        campoApellidos.getText(), leerNacimiento(), "");
                borradorNombres = campoNombres.getText();
                borradorApellidos = campoApellidos.getText();
                borradorNacimiento = campoNacimiento.getText();
                alumnoExistente = alumno;
                bloquearDatosAlumno(true);
            }
            solicitudes.exigirAdmisible(alumno, aula);
            for (FilaApoderado fila : filas) {
                if (fila.vinculado) {
                    continue;
                }
                if (fila.registrado == null) {
                    try {
                        fila.registrado = apoderados.registrar(fila.dni, fila.nombres,
                                fila.apellidos, fila.celular);
                    } catch (ApoderadoDuplicadoException e) {
                        fila.registrado = e.getExistente();
                    }
                }
                alumno.agregarApoderado(fila.registrado, false);
                fila.vinculado = true;
            }
            alumno.designarPrincipal(dniPrincipal);
            nueva = solicitudes.registrar(alumno, aula);
            ArrayList<Solicitud> promovidas = Turnos.ubicarNueva(nueva, solicitudes, matriculas,
                    Reloj.porDefecto().now());
            mostrarFicha(nueva, true, null, promovidas);
        } catch (ReglaDominioException e) {
            if (nueva != null) {
                mostrarFicha(nueva, true, e.getMessage(), new ArrayList<Solicitud>());
                return;
            }
            refrescarApoderados();
            recalcular();
            mensajeGuardar.setForeground(Estilos.TEXTO_ALERTA);
            mensajeGuardar.setText(e.getMessage());
        }
    }

    private void mostrarFicha(Solicitud solicitud, boolean recienCreada, String error,
            ArrayList<Solicitud> promovidas) {
        fichaActual = solicitud;
        fichaRecienCreada = recienCreada;
        fichaError = error;
        fichaPromovidas = promovidas;
        ficha.removeAll();

        String titulo = recienCreada ? "✓ Solicitud " + solicitud.getCodigo() + " creada"
                : "Solicitud activa existente · " + solicitud.getCodigo();
        ficha.add(Estilos.alineado(tituloTarjeta(titulo)));
        ficha.add(Box.createVerticalStrut(6));
        ficha.add(Estilos.alineado(nota("Registrada el "
                + FormatoFecha.diaHora(solicitud.getFechaRegistro()), Estilos.SECUNDARIO)));
        ficha.add(Box.createVerticalStrut(16));

        if (error != null) {
            ficha.add(Estilos.alineado(aviso(Estilos.ALERTA, Estilos.TEXTO_ALERTA,
                    "La solicitud quedó en la cola de espera, pero no se pudo evaluar su turno: "
                            + error)));
        } else if (!recienCreada) {
            ficha.add(Estilos.alineado(aviso(Estilos.ALERTA, Estilos.TEXTO_ALERTA,
                    "Ya existe una solicitud activa para este alumno en 2027; no se crea otra.")));
        } else if (solicitud.getEstado() == EstadoSolicitud.HABILITADA_PARA_PAGO) {
            ficha.add(Estilos.alineado(aviso(Estilos.FAVORABLE, Estilos.BOTON_PRINCIPAL,
                    "Guardada ahora · habilitada para pago hasta "
                            + FormatoFecha.diaHora(solicitud.getVencimientoHabilitacion())
                            + ". Comunicar la cuota de inscripción; no reserva vacante.")));
        } else if (solicitud.getEstado() == EstadoSolicitud.EN_ESPERA_SIN_PAGO) {
            ficha.add(Estilos.alineado(aviso(Estilos.ESPERA, Estilos.TEXTO_ESPERA,
                    "Guardada ahora · en cola sin pago. Sin cobro, revisión documental ni"
                            + " entrevista hasta que llegue su turno.")));
        } else {
            ficha.add(Estilos.alineado(aviso(Estilos.ESPERA, Estilos.TEXTO_ESPERA,
                    "Su estado cambió desde que se guardó; el estado actual figura abajo.")));
        }
        if (!promovidas.isEmpty()) {
            ficha.add(Box.createVerticalStrut(10));
            ficha.add(Estilos.alineado(aviso(Estilos.FAVORABLE, Estilos.BOTON_PRINCIPAL,
                    AvisoHabilitadas.texto("También quedaron habilitadas para pago. Avisar a"
                            + " sus familias:", promovidas, false))));
        }
        ficha.add(Box.createVerticalStrut(18));

        Alumno alumno = solicitud.getAlumno();
        JPanel estado = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        estado.setOpaque(false);
        estado.add(Estilos.chip(solicitud.getEstado()));
        ficha.add(dosColumnas(Estilos.conEtiqueta("Alumno", dato(alumno.getNombreCompleto()
                + " · DNI " + alumno.getDni())), Estilos.conEtiqueta("Estado", estado)));
        ficha.add(Box.createVerticalStrut(14));
        ficha.add(dosColumnas(Estilos.conEtiqueta("Aula", dato(textoAula(solicitud.getAula()))),
                Estilos.conEtiqueta("Ingreso a cola", dato(fechaOGuion(
                        solicitud.getFechaIngresoCola())))));
        ficha.add(Box.createVerticalStrut(14));
        Apoderado principal = alumno.getPrincipal();
        String textoPrincipal = principal == null ? "—"
                : principal.getNombreCompleto() + " · "
                        + AvisoHabilitadas.celular(principal.getCelular());
        ficha.add(dosColumnas(Estilos.conEtiqueta("Apoderado principal", dato(textoPrincipal)),
                Estilos.conEtiqueta("Vence habilitación", dato(fechaOGuion(
                        solicitud.getVencimientoHabilitacion())))));
        ficha.add(Box.createVerticalStrut(22));

        JPanel acciones = new Fila(new FlowLayout(FlowLayout.LEFT, 0, 0));
        acciones.setOpaque(false);
        JButton nuevaSolicitud = Estilos.botonPrimario("Nueva solicitud");
        nuevaSolicitud.addActionListener(evento -> limpiar());
        acciones.add(nuevaSolicitud);
        ficha.add(Estilos.alineado(acciones));
        ficha.add(Box.createVerticalGlue());

        ficha.revalidate();
        ficha.repaint();
        vistas.show(tarjetaIzquierda, FICHA);
    }

    private void limpiar() {
        fichaActual = null;
        fichaError = null;
        fichaPromovidas = new ArrayList<Solicitud>();
        actualizando = true;
        campoDni.setText("");
        campoNacimiento.setText("");
        campoNombres.setText("");
        campoApellidos.setText("");
        limpiarIngresoApoderado();
        if (comboAula.getItemCount() > 0) {
            comboAula.setSelectedIndex(0);
        }
        actualizando = false;
        alumnoExistente = null;
        borradorNombres = "";
        borradorApellidos = "";
        borradorNacimiento = "";
        actualizarAvisoAlumno();
        apoderadoSugerido = null;
        bloquearDatosAlumno(false);
        bloquearDatosApoderado(false);
        filas.clear();
        dniPrincipal = null;
        mensajeApoderados.setText(" ");
        refrescarApoderados();
        recalcular();
        vistas.show(tarjetaIzquierda, FORMULARIO);
    }

    private LocalDate leerNacimiento() {
        try {
            return LocalDate.parse(campoNacimiento.getText().trim(), FORMATO_NACIMIENTO);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private void alCambiar(JTextField campo) {
        campo.getDocument().addDocumentListener(new Escucha(this::recalcular));
    }

    private static boolean dniCompleto(String dni) {
        try {
            Validaciones.exigirDni(dni);
            return true;
        } catch (DatoInvalidoException e) {
            return false;
        }
    }

    private static String textoAula(Aula aula) {
        return aula.getNombre() + " · " + aula.getEdadRequerida() + " años";
    }

    private static String fechaOGuion(LocalDateTime fecha) {
        return fecha == null ? "—" : FormatoFecha.diaHora(fecha);
    }

    private static void pintar(JPanel caja, JLabel etiqueta, Color fondo, Color texto,
            String mensaje) {
        Estilos.aviso(caja, fondo);
        etiqueta.setForeground(texto);
        etiqueta.setText(mensaje);
        caja.revalidate();
        caja.repaint();
    }

    private static JPanel aviso(Color fondo, Color colorTexto, String mensaje) {
        JPanel caja = Estilos.aviso(new Fila(new BorderLayout()), fondo);
        caja.add(texto(mensaje, colorTexto, ANCHO_TEXTO_FICHA), BorderLayout.CENTER);
        return caja;
    }

    private static JPanel paso(String titulo, String detalle) {
        JPanel paso = new Fila(new BorderLayout(0, 6));
        paso.setOpaque(false);
        JLabel etiqueta = new JLabel(titulo);
        etiqueta.setFont(Estilos.contenidoDestacado());
        etiqueta.setForeground(Estilos.TEXTO_PRINCIPAL);
        paso.add(etiqueta, BorderLayout.NORTH);
        paso.add(texto(detalle, Estilos.SECUNDARIO, ANCHO_TEXTO_RUTA), BorderLayout.CENTER);
        return paso;
    }

    private static JPanel pasoDestacado(Color fondo, Color colorTexto, String estado,
            String detalle) {
        JPanel paso = Estilos.aviso(new Fila(new BorderLayout(0, 6)), fondo);
        JLabel etiqueta = new JLabel(estado);
        etiqueta.setFont(Estilos.etiqueta());
        etiqueta.setForeground(colorTexto);
        paso.add(etiqueta, BorderLayout.NORTH);
        paso.add(texto(detalle, Estilos.TEXTO_PRINCIPAL, ANCHO_TEXTO_RUTA), BorderLayout.CENTER);
        return paso;
    }

    private static JLabel tituloTarjeta(String texto) {
        JLabel titulo = new JLabel(texto);
        titulo.setFont(Estilos.tituloTarjeta());
        titulo.setForeground(Estilos.TEXTO_PRINCIPAL);
        return titulo;
    }

    private static JLabel dato(String texto) {
        JLabel dato = new JLabel(texto);
        dato.setFont(Estilos.dato());
        dato.setForeground(Estilos.TEXTO_PRINCIPAL);
        return dato;
    }

    private static JLabel texto(String texto, Color color, int ancho) {
        return nota("<html><div style='width:" + ancho + "px'>" + texto + "</div></html>",
                color);
    }

    private static JLabel nota(String texto, Color color) {
        JLabel etiqueta = new JLabel(texto);
        etiqueta.setFont(Estilos.detalle());
        etiqueta.setForeground(color);
        return etiqueta;
    }

    private static JPanel dosColumnas(JComponent izquierda, JComponent derecha) {
        JPanel fila = new Fila(new GridLayout(1, 2, 16, 0));
        fila.setOpaque(false);
        fila.add(izquierda);
        fila.add(derecha);
        return Estilos.alineado(fila);
    }

    private static final class Fila extends JPanel {

        Fila(LayoutManager layout) {
            super(layout);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    private static final class Escucha implements DocumentListener {

        private final Runnable accion;

        Escucha(Runnable accion) {
            this.accion = accion;
        }

        @Override
        public void insertUpdate(DocumentEvent e) {
            accion.run();
        }

        @Override
        public void removeUpdate(DocumentEvent e) {
            accion.run();
        }

        @Override
        public void changedUpdate(DocumentEvent e) {
            accion.run();
        }
    }

    private static final class FilaApoderado {

        final String dni;
        final String nombres;
        final String apellidos;
        final String celular;
        Apoderado registrado;
        boolean vinculado;

        FilaApoderado(Apoderado registrado) {
            this(registrado.getDni(), registrado.getNombres(), registrado.getApellidos(),
                    registrado.getCelular());
            this.registrado = registrado;
        }

        FilaApoderado(String dni, String nombres, String apellidos, String celular) {
            this.dni = dni;
            this.nombres = nombres;
            this.apellidos = apellidos;
            this.celular = celular;
        }
    }
}
