package ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import datos.ArregloMatriculas;
import datos.ArregloPagos;
import datos.ArregloSolicitudes;
import modelo.Apoderado;
import modelo.ConfiguracionCuotas;
import modelo.EstadoMatricula;
import modelo.EstadoSolicitud;
import modelo.Matricula;
import modelo.MedioPago;
import modelo.Pago;
import modelo.ReglaDominioException;
import modelo.Solicitud;
import negocio.Cobros;
import negocio.Turnos;
import negocio.Vencimientos;
import util.Reloj;

public class PanelPagos extends JPanel {

    private static final DateTimeFormatter FORMATO_DIA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String VISTA_INSCRIPCION = "INSCRIPCION";
    private static final String VISTA_MATRICULA = "MATRICULA";

    private final ArregloSolicitudes solicitudes;
    private final ArregloPagos pagos;
    private final ArregloMatriculas matriculas;
    private final ConfiguracionCuotas cuotas;

    private Solicitud actual;
    private Matricula matriculaActual;

    private final SelectorSolicitud selector = new SelectorSolicitud(false, this::elegir);
    private final JPanel resumen = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
    private final CardLayout cambioVista = new CardLayout();
    private final JPanel vistas = new JPanel(cambioVista);
    private final TarjetasPago tarjetas;
    private final TarjetasPago tarjetasMatricula;

    private final JPanel franja = new JPanel(new BorderLayout(24, 4));
    private final JLabel tituloFranja = new JLabel(" ");
    private final JLabel detalleFranja = new JLabel(" ");
    private final JLabel reservaFranja = new JLabel(" ");
    private final JPanel vencimiento = new JPanel(new BorderLayout(0, 4));
    private final JLabel tituloVencimiento = new JLabel(" ");
    private final JTextArea detalleVencimiento = TarjetasPago.textoAjustable();
    private final JScrollPane desplazable;

    public PanelPagos(ArregloSolicitudes solicitudes, ArregloPagos pagos,
            ArregloMatriculas matriculas, ConfiguracionCuotas cuotas) {
        super(new BorderLayout(0, 20));
        this.solicitudes = solicitudes;
        this.pagos = pagos;
        this.matriculas = matriculas;
        this.cuotas = cuotas;
        setBorder(Estilos.MARGEN_PANTALLA);

        tarjetas = new TarjetasPago("Pago de inscripción · comprobante recibido",
                "Inscripción 2027", "Confirmar pago → EN_DOCUMENTACION",
                "Fecha real dentro de la habilitación (48 h)", this::confirmar);
        tarjetas.configurarAnulacion(this::anular);
        tarjetasMatricula = new TarjetasPago("Pago de matrícula · comprobante recibido",
                "Matrícula 2027", "Confirmar pago → ACTIVA", "Fecha real dentro de 72 h",
                this::confirmarMatricula);
        tarjetasMatricula.configurarAnulacion(this::anular);

        add(crearEncabezado(), BorderLayout.NORTH);
        vistas.setOpaque(false);
        vistas.add(arriba(tarjetas), VISTA_INSCRIPCION);
        vistas.add(crearVistaMatricula(), VISTA_MATRICULA);
        // La franja de la matrícula suma alto a las tarjetas de pago: sin desplazamiento,
        // la tarjeta de vencimiento se corta en pantallas de 768 px.
        PanelAncho envoltura = new PanelAncho();
        envoltura.add(vistas, BorderLayout.NORTH);
        desplazable = new JScrollPane(envoltura);
        desplazable.setBorder(null);
        desplazable.setOpaque(false);
        desplazable.getViewport().setOpaque(false);
        desplazable.getVerticalScrollBar().setUnitIncrement(16);
        add(desplazable, BorderLayout.CENTER);

        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                recargarLista();
            }
        });
        recargarLista();
    }

    private JPanel crearEncabezado() {
        JPanel encabezado = new JPanel();
        encabezado.setLayout(new BoxLayout(encabezado, BoxLayout.Y_AXIS));
        encabezado.setOpaque(false);

        JPanel filaTitulo = new JPanel(new BorderLayout(20, 0));
        filaTitulo.setOpaque(false);
        JLabel titulo = new JLabel("Pagos y matrículas");
        titulo.setFont(Estilos.titulo());
        titulo.setForeground(Estilos.TEXTO_PRINCIPAL);
        filaTitulo.add(titulo, BorderLayout.WEST);
        filaTitulo.add(selector, BorderLayout.EAST);
        encabezado.add(Estilos.alineado(filaTitulo));
        encabezado.add(Box.createVerticalStrut(12));

        resumen.setOpaque(false);
        resumen.setAlignmentX(LEFT_ALIGNMENT);
        encabezado.add(resumen);
        return encabezado;
    }

    private JPanel crearVistaMatricula() {
        tituloFranja.setFont(Estilos.datoDestacado());
        detalleFranja.setFont(Estilos.contenido());
        detalleFranja.setForeground(Estilos.TEXTO_PRINCIPAL);
        reservaFranja.setFont(Estilos.datoDestacado());
        JPanel textos = new JPanel(new BorderLayout(0, 6));
        textos.setOpaque(false);
        textos.add(tituloFranja, BorderLayout.NORTH);
        textos.add(detalleFranja, BorderLayout.CENTER);
        franja.add(textos, BorderLayout.CENTER);
        franja.add(reservaFranja, BorderLayout.EAST);

        tituloVencimiento.setFont(Estilos.etiqueta());
        tituloVencimiento.setForeground(Estilos.TEXTO_ALERTA);
        detalleVencimiento.setFont(Estilos.detalle());
        detalleVencimiento.setForeground(Estilos.TEXTO_PRINCIPAL);
        detalleVencimiento.setBorder(Estilos.MARGEN_TEXTO_AJUSTADO);
        Estilos.aviso(vencimiento, Estilos.ALERTA);
        vencimiento.add(tituloVencimiento, BorderLayout.NORTH);
        vencimiento.add(detalleVencimiento, BorderLayout.CENTER);

        JLabel nota = new JLabel("Un pago confirmado no se edita: su anulación retorna a"
                + " PENDIENTE_PAGO y reevalúa el plazo original, sin reiniciarlo.");
        nota.setFont(Estilos.nota());
        nota.setForeground(Estilos.SECUNDARIO);

        tarjetasMatricula.agregarAlCierre(vencimiento);

        JPanel vista = new JPanel(new BorderLayout(0, 18));
        vista.setOpaque(false);
        vista.add(franja, BorderLayout.NORTH);
        vista.add(tarjetasMatricula, BorderLayout.CENTER);
        vista.add(nota, BorderLayout.SOUTH);
        return arriba(vista);
    }

    private static JPanel arriba(JPanel contenido) {
        JPanel contenedor = new JPanel(new BorderLayout());
        contenedor.setOpaque(false);
        contenedor.add(contenido, BorderLayout.NORTH);
        return contenedor;
    }

    private void recargarLista() {
        selector.recargar(solicitudes.listar(), solicitud -> true);
    }

    // Al volver a la pantalla la lista se recarga y llega la misma solicitud: solo se sube al
    // inicio si cambió, para no perder la posición en el pago que se estaba llenando.
    private void elegir(Solicitud solicitud) {
        if (solicitud != actual) {
            volverArriba();
        }
        actual = solicitud;
        refrescar();
    }

    private void volverArriba() {
        SwingUtilities.invokeLater(() -> desplazable.getVerticalScrollBar().setValue(0));
    }

    private void refrescar() {
        matriculaActual = null;
        cambioVista.show(vistas, VISTA_INSCRIPCION);
        if (actual == null) {
            mostrarResumen(null, null);
            tarjetas.bloquear("Elige una solicitud para ver o registrar sus pagos.");
            return;
        }

        LocalDateTime ahora = Reloj.porDefecto().now();
        LocalDateTime habilitacionVencida = actual.habilitacionVencida(ahora)
                ? actual.getVencimientoHabilitacion()
                : null;
        ArrayList<String> avisos;
        try {
            avisos = Vencimientos.revisar(ahora, solicitudes, matriculas);
        } catch (ReglaDominioException e) {
            mostrarResumen(actual, null);
            tarjetas.bloquear(e.getMessage());
            return;
        }
        mostrarSeleccion(habilitacionVencida);
        if (!avisos.isEmpty()) {
            TarjetasPago visible = matriculaActual != null ? tarjetasMatricula : tarjetas;
            visible.avisar("Revisión de vencimientos incompleta: " + Vencimientos.unir(avisos));
        }
    }

    private void mostrarSeleccion(LocalDateTime habilitacionVencida) {
        Matricula matricula = matriculas.ultimaDe(actual);
        if (matricula != null) {
            mostrarMatricula(matricula);
            return;
        }
        if (habilitacionVencida != null) {
            mostrarResumen(actual, null);
            tarjetas.bloquear("La habilitación venció el "
                    + habilitacionVencida.format(TarjetasPago.FORMATO_FECHA)
                    + "; la solicitud volvió al final de la cola sin pago.");
            return;
        }

        mostrarResumen(actual, null);
        Pago confirmado = pagos.inscripcionConfirmadaDe(actual);
        if (confirmado != null) {
            tarjetas.mostrarConfirmado(confirmado, detalleConfirmado(actual));
            return;
        }
        if (actual.getEstado() != EstadoSolicitud.HABILITADA_PARA_PAGO) {
            tarjetas.bloquear(actual.getCodigo() + " está en " + actual.getEstado()
                    + " y no está habilitada para pagar la inscripción.");
            return;
        }
        try {
            Turnos.exigirTurno(actual, solicitudes, matriculas);
        } catch (ReglaDominioException e) {
            tarjetas.bloquear(e.getMessage());
            return;
        }
        LocalDateTime inicio = actual.getFechaHabilitacion();
        if (actual.getFechaRegistro().isAfter(inicio)) {
            inicio = actual.getFechaRegistro();
        }
        tarjetas.prepararPago(actual, cuotas.getCuotaInscripcion(), inicio,
                actual.getVencimientoHabilitacion());
    }

    private void mostrarMatricula(Matricula matricula) {
        matriculaActual = matricula;
        mostrarResumen(matricula.getSolicitud(), matricula);
        pintarFranja(matricula);
        pintarVencimiento(matricula);
        Pago confirmado = pagos.matriculaConfirmadaDe(matricula);
        if (confirmado != null) {
            tarjetasMatricula.mostrarConfirmado(confirmado, "Matrícula ACTIVA · mantiene la"
                    + " vacante reservada en Aula " + matricula.getAula().getNombre() + ".");
        } else if (matricula.getEstado() == EstadoMatricula.PENDIENTE_PAGO) {
            tarjetasMatricula.prepararPago(matricula, cuotas.getCuotaMatricula(),
                    matricula.getFechaHoraCreacion(), matricula.getVencimientoPagoOriginal());
        } else {
            tarjetasMatricula.bloquear("La matrícula " + matricula.getCodigo() + " está "
                    + matricula.getEstado() + " y no espera pago.");
        }
        cambioVista.show(vistas, VISTA_MATRICULA);
    }

    private void pintarFranja(Matricula matricula) {
        EstadoMatricula estado = matricula.getEstado();
        Estilos.aviso(franja, Estilos.colorFondo(estado));
        franja.setBorder(BorderFactory.createCompoundBorder(franja.getBorder(),
                Estilos.MARGEN_FRANJA));
        tituloFranja.setForeground(Estilos.colorTexto(estado));
        tituloFranja.setText("MATRÍCULA: " + estado);
        detalleFranja.setText("Creada "
                + matricula.getFechaHoraCreacion().format(TarjetasPago.FORMATO_FECHA)
                + "  →  pago hasta "
                + matricula.getVencimientoPagoOriginal().format(TarjetasPago.FORMATO_FECHA)
                + " (72 horas corridas)");
        reservaFranja.setForeground(Estilos.colorTexto(estado));
        if (estado == EstadoMatricula.PENDIENTE_PAGO) {
            reservaFranja.setText("Reserva 1 vacante ahora");
        } else if (estado == EstadoMatricula.ACTIVA) {
            reservaFranja.setText("Vacante confirmada");
        } else {
            reservaFranja.setText("Vacante liberada");
        }
    }

    private void pintarVencimiento(Matricula matricula) {
        if (matricula.getEstado() == EstadoMatricula.ACTIVA) {
            vencimiento.setVisible(false);
            return;
        }
        if (matricula.getEstado() == EstadoMatricula.PENDIENTE_PAGO) {
            tituloVencimiento.setText("SI VENCE SIN PAGO REGISTRADO");
            detalleVencimiento.setText("Matrícula CANCELADA, libera vacante. Solicitud vuelve"
                    + " al final de EN_ESPERA_FAVORABLE con nuevo ingreso; conserva inscripción"
                    + " pagada.");
        } else {
            Solicitud solicitud = matricula.getSolicitud();
            tituloVencimiento.setText("MATRÍCULA CANCELADA");
            detalleVencimiento.setText(matricula.getMotivoCancelacion() + " · "
                    + matricula.getFechaCancelacion().format(TarjetasPago.FORMATO_FECHA) + " · "
                    + matricula.getResponsableCancelacion() + ". Vacante liberada; "
                    + solicitud.getCodigo() + " está en " + solicitud.getEstado()
                    + " y conserva la inscripción pagada.");
        }
        vencimiento.setVisible(true);
    }

    private void confirmar(double monto, MedioPago medio, String numeroOperacion,
            LocalDateTime fechaOperacion, String rutaComprobantePago)
            throws ReglaDominioException {
        Vencimientos.barrer(Reloj.porDefecto(), null, solicitudes, matriculas, pagos, cuotas);
        Pago pago;
        try {
            pago = Cobros.confirmarInscripcion(actual, monto, medio, numeroOperacion,
                    fechaOperacion, rutaComprobantePago, Reloj.porDefecto().now(), solicitudes,
                    matriculas, pagos, cuotas);
        } catch (ReglaDominioException e) {
            if (!puedePagar()) {
                volverArriba();
                refrescar();
            }
            throw e;
        }
        mostrarResumen(actual, null);
        tarjetas.mostrarConfirmado(pago, detalleConfirmado(actual));
    }

    private void confirmarMatricula(double monto, MedioPago medio, String numeroOperacion,
            LocalDateTime fechaOperacion, String rutaComprobantePago)
            throws ReglaDominioException {
        Matricula matricula = matriculaActual;
        try {
            Cobros.confirmarMatricula(matricula, monto, medio, numeroOperacion, fechaOperacion,
                    rutaComprobantePago, Reloj.porDefecto().now(), solicitudes,
                    matriculas, pagos, cuotas);
        } catch (ReglaDominioException e) {
            // Si el rechazo fue por vencimiento, la matrícula ya quedó CANCELADA:
            // se repinta para que el personal vea el estado real junto al mensaje.
            if (matricula.getEstado() != EstadoMatricula.PENDIENTE_PAGO) {
                mostrarMatricula(matricula);
            }
            throw e;
        }
        mostrarMatricula(matricula);
    }

    private void anular(Pago pago, String motivo, String responsable, LocalDateTime fechaHora)
            throws ReglaDominioException {
        Cobros.anularPago(pago, motivo, responsable, fechaHora, solicitudes, matriculas, pagos);
        volverArriba();
        refrescar();
    }

    private boolean puedePagar() {
        if (actual.getEstado() != EstadoSolicitud.HABILITADA_PARA_PAGO) {
            return false;
        }
        try {
            Turnos.exigirTurno(actual, solicitudes, matriculas);
            return true;
        } catch (ReglaDominioException e) {
            return false;
        }
    }

    private static String detalleConfirmado(Solicitud solicitud) {
        if (solicitud.getEstado() == EstadoSolicitud.CANCELADA) {
            return "Solicitud CANCELADA · " + solicitud.getMotivoCancelacion()
                    + " Sin reembolso de la inscripción.";
        }
        if (solicitud.getExpediente() == null) {
            return "Inscripción pagada.";
        }
        return "Solicitud en EN_DOCUMENTACION · entrega de los 4 documentos hasta el "
                + solicitud.getExpediente().fechaLimiteEntrega().format(FORMATO_DIA) + ".";
    }

    private void mostrarResumen(Solicitud solicitud, Matricula matricula) {
        resumen.removeAll();
        if (solicitud != null) {
            StringBuilder texto = new StringBuilder(solicitud.getAlumno().getNombreCompleto());
            if (matricula != null) {
                texto.append("  ·  ").append(matricula.getCodigo());
            }
            texto.append("  ·  ").append(solicitud.getCodigo()).append("  ·  Aula ")
                    .append(solicitud.getAula().getNombre());
            Apoderado responsable = solicitud.getAlumno().getPrincipal();
            if (matricula != null && responsable != null) {
                texto.append("  ·  Responsable de pago: ")
                        .append(responsable.getNombreCompleto());
            }
            JLabel datos = new JLabel(texto + "     ");
            datos.setFont(Estilos.dato());
            datos.setForeground(Estilos.TEXTO_PRINCIPAL);
            resumen.add(datos);
            resumen.add(Estilos.chip(solicitud.getEstado()));
            if (matricula != null) {
                resumen.add(Box.createHorizontalStrut(8));
                resumen.add(Estilos.chip(matricula.getEstado()));
            }
        }
        resumen.revalidate();
        resumen.repaint();
    }
}
