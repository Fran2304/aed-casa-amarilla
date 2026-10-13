package datos;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * Helper compartido de escape/desescape para los archivos CSV (decisión 9 de la spec).
 *
 * <p>Cada campo se escapa antes de unir con {@code ;} y se desescapa al leer, de modo que
 * un valor con {@code ;}, {@code ,} o saltos de línea sobrevive la ida y vuelta:
 * {@code \} → {@code \\}, {@code ;} → {@code \;}, salto de línea → {@code \n}.
 */
public final class EscrituraCsv {

    private EscrituraCsv() {
    }

    /** Escapa un campo: un valor nulo se escribe como campo vacío. */
    public static String escapar(String valor) {
        if (valor == null) {
            return "";
        }
        StringBuilder salida = new StringBuilder(valor.length());
        for (int i = 0; i < valor.length(); i++) {
            char c = valor.charAt(i);
            switch (c) {
                case '\\':
                    salida.append("\\\\");
                    break;
                case ';':
                    salida.append("\\;");
                    break;
                case '\n':
                    salida.append("\\n");
                    break;
                case '\r':
                    salida.append("\\r");
                    break;
                default:
                    salida.append(c);
            }
        }
        return salida.toString();
    }

    /** Deshace el escape aplicado por {@link #escapar(String)}. */
    public static String desescapar(String campo) {
        if (campo == null) {
            return null;
        }
        StringBuilder salida = new StringBuilder(campo.length());
        for (int i = 0; i < campo.length(); i++) {
            char c = campo.charAt(i);
            if (c == '\\' && i + 1 < campo.length()) {
                char siguiente = campo.charAt(i + 1);
                switch (siguiente) {
                    case '\\':
                        salida.append('\\');
                        i++;
                        break;
                    case ';':
                        salida.append(';');
                        i++;
                        break;
                    case 'n':
                        salida.append('\n');
                        i++;
                        break;
                    case 'r':
                        salida.append('\r');
                        i++;
                        break;
                    default:
                        salida.append(c);
                }
            } else {
                salida.append(c);
            }
        }
        return salida.toString();
    }

    /**
     * Divide una línea respetando el escape: un {@code ;} precedido por {@code \} no separa.
     * Devuelve los campos aún escapados (para desescapar luego, uno a uno).
     */
    public static String[] dividir(String linea) {
        ArrayList<String> campos = new ArrayList<String>();
        StringBuilder actual = new StringBuilder();
        for (int i = 0; i < linea.length(); i++) {
            char c = linea.charAt(i);
            if (c == '\\' && i + 1 < linea.length()) {
                actual.append(c).append(linea.charAt(i + 1));
                i++;
            } else if (c == ';') {
                campos.add(actual.toString());
                actual.setLength(0);
            } else {
                actual.append(c);
            }
        }
        campos.add(actual.toString());
        return campos.toArray(new String[0]);
    }

    /** Escapa cada campo y los une con {@code ;}. */
    public static String unir(String... campos) {
        StringBuilder linea = new StringBuilder();
        for (int i = 0; i < campos.length; i++) {
            if (i > 0) {
                linea.append(';');
            }
            linea.append(escapar(campos[i]));
        }
        return linea.toString();
    }

    /** Campo nullable al leer: vacío → {@code null}; si no, desescapado. */
    public static String campoALeer(String campo) {
        if (campo == null || campo.isEmpty()) {
            return null;
        }
        return desescapar(campo);
    }

    /** Escribe líneas en un archivo UTF-8 y comprueba el error retenido del {@link PrintWriter}. */
    public static void escribir(String ruta, ArrayList<String> lineas) throws IOException {
        try (PrintWriter salida = new PrintWriter(new FileWriter(ruta, StandardCharsets.UTF_8))) {
            for (String linea : lineas) {
                salida.println(linea);
            }
            salida.flush();
            if (salida.checkError()) {
                throw new IOException("No se pudo escribir el archivo " + ruta + ".");
            }
        }
    }
}
