package ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;

import javax.swing.Box;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JList;
import javax.swing.JPanel;

import modelo.Solicitud;

class SelectorSolicitud extends JPanel {

    private static final String SIN_SELECCION = "Elige una solicitud…";

    interface Filtro {
        boolean incluye(Solicitud solicitud);
    }

    interface AccionElegir {
        void elegir(Solicitud solicitud);
    }

    private final JComboBox<Solicitud> combo = Estilos.campo(new JComboBox<Solicitud>());
    private final boolean abrirPrimera;
    private final AccionElegir alElegir;
    private boolean llenando;

    SelectorSolicitud(boolean abrirPrimera, AccionElegir alElegir) {
        super(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        this.abrirPrimera = abrirPrimera;
        this.alElegir = alElegir;
        setOpaque(false);
        add(Estilos.etiquetaCampo("Solicitud"));
        add(Box.createHorizontalStrut(12));
        combo.setPreferredSize(new Dimension(300, combo.getPreferredSize().height));
        combo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> lista, Object valor,
                    int indice, boolean seleccionado, boolean conFoco) {
                Object texto = valor;
                if (valor instanceof Solicitud) {
                    Solicitud solicitud = (Solicitud) valor;
                    texto = solicitud.getCodigo() + " · "
                            + solicitud.getAlumno().getNombreCompleto();
                } else if (valor == null && !abrirPrimera) {
                    texto = SIN_SELECCION;
                }
                Component celda = super.getListCellRendererComponent(lista, texto, indice,
                        seleccionado, conFoco);
                if (valor == null) {
                    celda.setForeground(Estilos.SECUNDARIO);
                }
                return celda;
            }
        });
        combo.addActionListener(evento -> {
            if (!llenando) {
                alElegir.elegir(getSeleccionada());
            }
        });
        add(combo);
    }

    Solicitud getSeleccionada() {
        return (Solicitud) combo.getSelectedItem();
    }

    void recargar(ArrayList<Solicitud> todas, Filtro filtro) {
        Solicitud seleccionada = getSeleccionada();
        llenando = true;
        combo.removeAllItems();
        for (Solicitud solicitud : todas) {
            if (filtro.incluye(solicitud)) {
                combo.addItem(solicitud);
            }
        }
        if (seleccionada != null && filtro.incluye(seleccionada)
                && todas.contains(seleccionada)) {
            combo.setSelectedItem(seleccionada);
        } else if (abrirPrimera && combo.getItemCount() > 0) {
            combo.setSelectedIndex(0);
        } else {
            combo.setSelectedIndex(-1);
        }
        llenando = false;
        alElegir.elegir(getSeleccionada());
    }
}
