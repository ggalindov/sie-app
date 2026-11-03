package sie.siejuridicos.common;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilidad central para sanitización y extracción inteligente de canales de contacto
 * (correos electrónicos y números celulares colombianos con soporte WhatsApp).
 *
 * Resuelve y tolera anomalías comunes provenientes de hojas de cálculo de despacho:
 * - Múltiples correos separados por ';', ',', '/', '|', saltos de línea o puntos medios '·'.
 * - Correos con caracteres espurios en sus extremos (ej. 'pagos@rumbos.com.co·' o '<admin@x.com>').
 * - Múltiples números telefónicos en una sola celda (ej. '3124567890 / 3001234567').
 * - Formatos telefónicos con indicativos, paréntesis, guiones o puntos (ej. '+57 (312) 456-7890').
 */
public final class ContactoSanitizer {

    private static final Pattern PATRON_CORREO = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    // Coincide con celulares colombianos de 10 dígitos que inician con 3 (rango 300-359),
    // con o sin indicativo +57 o 57, y con delimitadores comunes (espacios, guiones, puntos, paréntesis).
    private static final Pattern PATRON_CELULAR_COLOMBIANO = Pattern.compile(
            "(?<!\\d)(?:(?:\\+?57)[\\s.-]*)?\\(?(3\\d{2})\\)?[\\s.-]*(\\d{3})[\\s.-]*(\\d{4})(?!\\d)");

    // Separadores para dividir cadenas con múltiples correos
    private static final Pattern SEPARADOR_CORREOS = Pattern.compile("[;,/|·•\\s\\r\\n]+");

    private ContactoSanitizer() {}

    /**
     * Extrae y normaliza TODOS los correos electrónicos válidos presentes en el texto.
     * Limpia caracteres espurios de los extremos (como puntos medios '·', comas, comillas, etc.)
     * y deduplica conservando el orden de aparición.
     */
    public static List<String> extraerCorreos(String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }
        String[] trozos = SEPARADOR_CORREOS.split(texto);
        Set<String> resultado = new LinkedHashSet<>();
        for (String t : trozos) {
            if (t == null || t.isBlank() || !t.contains("@")) {
                continue;
            }
            // Remueve caracteres no alfanuméricos al inicio y final (ej. ·, ., ;, :, <, >, ", ', etc.)
            String limpio = t.replaceAll("^[^a-zA-Z0-9]+|[^a-zA-Z0-9]+$", "").trim();
            if (PATRON_CORREO.matcher(limpio).matches()) {
                resultado.add(limpio.toLowerCase());
            }
        }
        return new ArrayList<>(resultado);
    }

    /**
     * Devuelve una cadena con todos los correos limpios y normalizados, unidos por "; ".
     * Si no se encontró ningún correo válido, devuelve null.
     */
    public static String normalizarCadenaCorreos(String texto) {
        List<String> correos = extraerCorreos(texto);
        return correos.isEmpty() ? null : String.join("; ", correos);
    }

    /**
     * Devuelve el primer correo válido encontrado en la cadena, o null si no hay ninguno.
     */
    public static String primerCorreo(String texto) {
        List<String> correos = extraerCorreos(texto);
        return correos.isEmpty() ? null : correos.get(0);
    }

    /**
     * Extrae TODOS los números celulares colombianos (10 dígitos que inician por 3) presentes en el texto
     * y los normaliza al formato E.164 exigido por Meta WhatsApp ("573XXXXXXXXX").
     */
    public static List<String> extraerCelulares(String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }
        Set<String> resultado = new LinkedHashSet<>();
        Matcher matcher = PATRON_CELULAR_COLOMBIANO.matcher(texto);
        while (matcher.find()) {
            String celular10 = matcher.group(1) + matcher.group(2) + matcher.group(3);
            resultado.add("57" + celular10);
        }
        // Red de seguridad: si no hizo match estructurado (ej. "31245678903001234567" sin separadores),
        // busca secuencias directas de 10 dígitos que inicien con 3
        if (resultado.isEmpty()) {
            String soloDigitos = texto.replaceAll("[^0-9]", "");
            Matcher subMatcher = Pattern.compile("3\\d{9}").matcher(soloDigitos);
            while (subMatcher.find()) {
                resultado.add("57" + subMatcher.group());
            }
        }
        return new ArrayList<>(resultado);
    }

    /**
     * Normaliza al primer celular colombiano válido en formato E.164 ("573XXXXXXXXX"),
     * o devuelve null si no se reconoce ningún celular.
     */
    public static String normalizarCelular(String telefono) {
        List<String> celulares = extraerCelulares(telefono);
        return celulares.isEmpty() ? null : celulares.get(0);
    }

    /**
     * Devuelve una cadena legible con todos los celulares encontrados en formato nacional ("3XXXXXXXXX / 3YYYYYYYYY").
     */
    public static String normalizarCadenaCelulares(String telefono) {
        List<String> celulares = extraerCelulares(telefono);
        if (celulares.isEmpty()) {
            return null;
        }
        List<String> nacionales = celulares.stream()
                .map(c -> c.startsWith("57") ? c.substring(2) : c)
                .toList();
        return String.join(" / ", nacionales);
    }
}
