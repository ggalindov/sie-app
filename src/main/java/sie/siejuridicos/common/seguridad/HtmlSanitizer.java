package sie.siejuridicos.common.seguridad;

import java.util.regex.Pattern;

/**
 * Sanitizador defensivo de HTML para contenido enriquecido (artículos de blog, notas, etc.).
 * Elimina scripts ejecutables, iframes maliciosos, manejadores de eventos (onload, onerror, onclick)
 * y esquemas de pseudoprotocolo javascript/vbscript/data que pudieran causar XSS almacenado.
 */
public final class HtmlSanitizer {

    private static final Pattern PATRON_SCRIPTS = Pattern.compile("(?i)<script[^>]*>[\\s\\S]*?</script>");
    private static final Pattern PATRON_TAGS_PELIGROSOS = Pattern.compile("(?i)</?(?:object|embed|applet|iframe|meta|base|form|input|button|svg|math)[^>]*>");
    private static final Pattern PATRON_EVENTOS_ON = Pattern.compile("(?i)\\s+on[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)");
    private static final Pattern PATRON_PROTOCOLO_JAVASCRIPT = Pattern.compile("(?i)(href|src)\\s*=\\s*(\"|')?\\s*(?:javascript|vbscript|data\\s*:\\s*text/html):[^\"'>]*(\"|')?");

    private HtmlSanitizer() {
    }

    public static String sanitizar(String html) {
        if (html == null || html.isBlank()) {
            return html;
        }

        String limpio = html;
        limpio = PATRON_SCRIPTS.matcher(limpio).replaceAll("");
        limpio = PATRON_TAGS_PELIGROSOS.matcher(limpio).replaceAll("");
        limpio = PATRON_EVENTOS_ON.matcher(limpio).replaceAll("");
        limpio = PATRON_PROTOCOLO_JAVASCRIPT.matcher(limpio).replaceAll("$1=\"#\"");

        return limpio;
    }
}
