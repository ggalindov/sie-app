package sie.siejuridicos.caso;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Punto único de normalización del radicado judicial -- limpia espacios, guiones, puntos,
// paréntesis y notas adjuntas (ej. '(2026-01059)' o ceros residuales de exportación).
// Quita el ruido y extrae exactamente los 23 dígitos oficiales de la Rama Judicial.
public final class RadicadoUtil {

    private static final Pattern PATRON_23_DIGITOS = Pattern.compile("(\\d{23})");
    private static final Pattern RUIDO_INTERNO = Pattern.compile("[\\s\\-.]+");
    private static final Pattern SOLO_DIGITOS = Pattern.compile("\\d+");
    public static final int LONGITUD_RADICADO_CPNU = 23;

    private RadicadoUtil() {
    }

    // Quita espacios/guiones/puntos y extrae los 23 dígitos oficiales si vienen acompañados de
    // notas adicionales. Nunca null -- "" si la entrada es null.
    public static String limpiar(String bruto) {
        if (bruto == null) {
            return "";
        }
        String recortado = bruto.strip();
        Matcher matcher = PATRON_23_DIGITOS.matcher(recortado);
        if (matcher.find()) {
            return matcher.group(1);
        }
        String soloDigitos = recortado.replaceAll("[^0-9]", "");
        if (soloDigitos.length() == LONGITUD_RADICADO_CPNU) {
            return soloDigitos;
        }
        if (soloDigitos.length() > LONGITUD_RADICADO_CPNU) {
            return soloDigitos.substring(0, LONGITUD_RADICADO_CPNU);
        }
        return RUIDO_INTERNO.matcher(recortado).replaceAll("");
    }

    // true si, tras limpiar, el resultado son EXACTAMENTE 23 dígitos -- el único formato real
    // que el CPNU/Publicaciones Procesales puede resolver. Un radicado de otra jurisdicción
    // (comisaría de familia, inspección de policía, p. ej. "VIF 428-2026...") nunca lo cumple,
    // y eso es CORRECTO, no un error: esos casos legítimamente no tienen radicado CPNU.
    public static boolean esFormatoCpnuValido(String bruto) {
        String limpio = limpiar(bruto);
        return limpio.length() == LONGITUD_RADICADO_CPNU && SOLO_DIGITOS.matcher(limpio).matches();
    }

    // Formatea un radicado de 23 dígitos en su notación judicial canónica:
    // Depto/Mpio(5) - Corp(2) - Espec(2) - Desp(3) - Año(4) - Consecutivo(5) - Recurso(2)
    // Ej: 11001-31-03-028-2026-00094-00
    public static String formatearLegible(String bruto) {
        String limpio = limpiar(bruto);
        if (limpio.length() == LONGITUD_RADICADO_CPNU && SOLO_DIGITOS.matcher(limpio).matches()) {
            return "%s-%s-%s-%s-%s-%s-%s".formatted(
                    limpio.substring(0, 5),
                    limpio.substring(5, 7),
                    limpio.substring(7, 9),
                    limpio.substring(9, 12),
                    limpio.substring(12, 16),
                    limpio.substring(16, 21),
                    limpio.substring(21, 23)
            );
        }
        return bruto != null ? bruto.strip() : "";
    }

    // Para GUARDAR en Caso.radicadoId (ver su setter): si el dato bruto, una vez limpio, SÍ es
    // un radicado CPNU real (solo tenía ruido de formato o notas adjuntas), se guarda la versión limpia (23 dígitos).
    // Si no lo es (otra jurisdicción como Comisarías VIF, o dato genuinamente corrupto), se guarda el
    // valor original limpio de bordes.
    public static String normalizarParaAlmacenar(String bruto) {
        if (bruto == null) {
            return null;
        }
        String sinBordes = bruto.strip();
        if (sinBordes.isEmpty()) {
            return null;
        }
        String limpio = limpiar(sinBordes);
        return esFormatoCpnuValido(limpio) ? limpio : sinBordes;
    }
}
