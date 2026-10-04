package modelo;

public final class Validaciones {

    public static final int LARGO_DNI = 8;
    public static final int LARGO_CELULAR = 9;

    private Validaciones() {
    }

    public static String exigirNoVacio(String campo, String valor) throws DatoInvalidoException {
        if (valor == null || valor.trim().isEmpty()) {
            throw new DatoInvalidoException("El campo " + campo + " es obligatorio.");
        }
        return valor.trim();
    }

    public static String exigirDigitos(String campo, String valor, int largo)
            throws DatoInvalidoException {
        String limpio = exigirNoVacio(campo, valor);
        if (limpio.length() != largo) {
            throw new DatoInvalidoException(
                    "El " + campo + " debe tener " + largo + " dígitos.");
        }
        return exigirSoloDigitos(campo, limpio);
    }

    public static String exigirSoloDigitos(String campo, String valor)
            throws DatoInvalidoException {
        String limpio = exigirNoVacio(campo, valor);
        for (int i = 0; i < limpio.length(); i++) {
            char c = limpio.charAt(i);
            if (c < '0' || c > '9') {
                throw new DatoInvalidoException("El " + campo + " solo admite dígitos.");
            }
        }
        return limpio;
    }

    public static String exigirDni(String dni) throws DatoInvalidoException {
        return exigirDigitos("DNI", dni, LARGO_DNI);
    }

    public static String exigirCelular(String celular) throws DatoInvalidoException {
        return exigirDigitos("celular", celular, LARGO_CELULAR);
    }

    public static String celularOpcional(String celular) throws DatoInvalidoException {
        if (celular == null || celular.trim().isEmpty()) {
            return "";
        }
        return exigirCelular(celular);
    }

    public static String normalizarDni(String dni) {
        if (dni == null) {
            return null;
        }
        return dni.trim();
    }
}
