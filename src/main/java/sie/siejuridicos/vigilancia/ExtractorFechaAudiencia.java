package sie.siejuridicos.vigilancia;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Gap real identificado en la auditoría del plan de desarrollo (comparando en vivo contra el
// correo "RESUMEN DE ACTUACIONES" de LUPA Jurídica, que sí incluye una "AGENDA DE
// DILIGENCIAS" con fecha y hora de cada audiencia programada -- SIE no tenía nada
// equivalente). Pedido explícito del usuario (2026-11-06): "el sistema debe detectar tambien
// si programa el estado alguna audiencia... se tiene que ver reflejada de manera automatica" y
// luego, más exigente todavía: "el sistema tiene que detectar como sea la fecha, hora y si se
// puede link de una audiencia reportada por las ramas, despachos o lo que sea" -- de ahí que
// este extractor reconozca varios formatos de fecha/hora a la vez (no uno solo) y también
// busque un enlace de videoconferencia en el mismo texto (Rama Judicial, Consejo de Estado y
// despachos individuales no tienen un formato único, cada uno redacta distinto).
//
// Mismo criterio que ClasificadorMovimientoJudicial (del que es complemento, nunca se usa
// solo): texto libre sobre lo que la Rama Judicial/despacho escribió, nunca un hecho
// verificado por un humano -- un abogado SIEMPRE debe confirmar leyendo el auto real antes de
// agendar nada sobre esta fecha o de unirse al enlace. Deliberadamente sin persistir nada
// nuevo, se calcula al vuelo sobre el mismo texto ya guardado (mismo motivo que el resto de
// este paquete: nunca queda desactualizado respecto a su fuente, no exige migración).
public final class ExtractorFechaAudiencia {

    private ExtractorFechaAudiencia() {
    }

    public record FechaAudiencia(LocalDate fecha, LocalTime hora, String enlace) {
    }

    private static final Pattern FECHA_LARGA = Pattern.compile(
            "(\\d{1,2})\\s+de\\s+(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre)\\s+de\\s+(\\d{4})",
            Pattern.CASE_INSENSITIVE);

    // "15 de dic de 2026", "15 dic 2026", "15-dic-2026" -- mes abreviado, formato que usan
    // algunos despachos en autos escritos a mano/escaneados con OCR o plantillas propias.
    private static final Pattern FECHA_MES_ABREVIADO = Pattern.compile(
            "(\\d{1,2})\\s*(?:de\\s+|-)?(ene|feb|mar|abr|may|jun|jul|ago|sep|set|oct|nov|dic)\\.?\\s*(?:de\\s+|-)?(\\d{4})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern FECHA_NUMERICA = Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})\\b");

    // "15.11.2026" -- separador de punto, usado por algunos aplicativos de despacho distintos
    // al CPNU (ej. plantillas de Word exportadas con el formato de fecha regional del equipo).
    private static final Pattern FECHA_PUNTO = Pattern.compile("\\b(\\d{1,2})\\.(\\d{1,2})\\.(\\d{4})\\b");

    private static final Pattern FECHA_ISO = Pattern.compile("\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b");

    // "9:00 a.m.", "09:00am", "2:30 p. m.", "14:30" (24h, sin am/pm).
    private static final Pattern HORA = Pattern.compile(
            "(\\d{1,2}):(\\d{2})\\s*(a\\.?\\s*m\\.?|p\\.?\\s*m\\.?)?", Pattern.CASE_INSENSITIVE);

    // Enlace de videoconferencia: cualquier URL http(s) en el texto. Los despachos colombianos
    // usan sobre todo Microsoft Teams (el más común tras la pandemia para audiencias
    // virtuales), además de Zoom y Google Meet -- pero "como sea" (pedido explícito del
    // usuario) significa no limitarse solo a esos tres dominios: cualquier URL encontrada se
    // ofrece como enlace candidato, igual de "sin verificar" que la fecha/hora.
    private static final Pattern URL = Pattern.compile("https?://[\\w.-]+(?:/[\\w\\-./?%&=+#:,;]*)?", Pattern.CASE_INSENSITIVE);

    // Devuelve la PRIMERA fecha (futura o pasada, no se filtra aquí) reconocible en el texto,
    // con su hora y enlace si el texto trae alguno cerca. Nunca lanza: un texto sin fecha
    // reconocible simplemente no trae nada que mostrar en el calendario.
    public static Optional<FechaAudiencia> extraer(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        Optional<LocalDate> fecha = extraerFechaLarga(texto)
                .or(() -> extraerFechaMesAbreviado(texto))
                .or(() -> extraerFechaIso(texto))
                .or(() -> extraerFechaPunto(texto))
                .or(() -> extraerFechaNumerica(texto));
        return fecha.map(f -> new FechaAudiencia(f, extraerHora(texto).orElse(null), extraerEnlace(texto).orElse(null)));
    }

    private static Optional<LocalDate> extraerFechaLarga(String texto) {
        Matcher m = FECHA_LARGA.matcher(texto);
        if (!m.find()) {
            return Optional.empty();
        }
        try {
            int dia = Integer.parseInt(m.group(1));
            int mes = mesDesdeNombre(m.group(2));
            int anio = Integer.parseInt(m.group(3));
            return Optional.of(LocalDate.of(anio, mes, dia));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private static Optional<LocalDate> extraerFechaMesAbreviado(String texto) {
        Matcher m = FECHA_MES_ABREVIADO.matcher(texto);
        if (!m.find()) {
            return Optional.empty();
        }
        try {
            int dia = Integer.parseInt(m.group(1));
            int mes = mesDesdeAbreviatura(m.group(2));
            int anio = Integer.parseInt(m.group(3));
            return Optional.of(LocalDate.of(anio, mes, dia));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private static Optional<LocalDate> extraerFechaIso(String texto) {
        Matcher m = FECHA_ISO.matcher(texto);
        if (!m.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private static Optional<LocalDate> extraerFechaPunto(String texto) {
        Matcher m = FECHA_PUNTO.matcher(texto);
        if (!m.find()) {
            return Optional.empty();
        }
        try {
            int dia = Integer.parseInt(m.group(1));
            int mes = Integer.parseInt(m.group(2));
            int anio = Integer.parseInt(m.group(3));
            if (mes < 1 || mes > 12) {
                return Optional.empty();
            }
            return Optional.of(LocalDate.of(anio, mes, dia));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    // DD/MM/YYYY -- formato colombiano, NUNCA MM/DD/YYYY. Si el "mes" no puede ser un mes
    // válido (>12) se descarta en vez de adivinar, para no fabricar una fecha falsa.
    private static Optional<LocalDate> extraerFechaNumerica(String texto) {
        Matcher m = FECHA_NUMERICA.matcher(texto);
        if (!m.find()) {
            return Optional.empty();
        }
        try {
            int dia = Integer.parseInt(m.group(1));
            int mes = Integer.parseInt(m.group(2));
            int anio = Integer.parseInt(m.group(3));
            if (mes < 1 || mes > 12) {
                return Optional.empty();
            }
            return Optional.of(LocalDate.of(anio, mes, dia));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private static Optional<LocalTime> extraerHora(String texto) {
        Matcher m = HORA.matcher(texto);
        if (!m.find()) {
            return Optional.empty();
        }
        try {
            int hora = Integer.parseInt(m.group(1));
            int minuto = Integer.parseInt(m.group(2));
            String meridiano = m.group(3) == null ? "" : m.group(3).toLowerCase(Locale.ROOT).replace(".", "").replace(" ", "");
            if (meridiano.equals("pm") && hora < 12) {
                hora += 12;
            } else if (meridiano.equals("am") && hora == 12) {
                hora = 0;
            }
            if (hora < 0 || hora > 23 || minuto < 0 || minuto > 59) {
                return Optional.empty();
            }
            return Optional.of(LocalTime.of(hora, minuto));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private static Optional<String> extraerEnlace(String texto) {
        Matcher m = URL.matcher(texto);
        if (!m.find()) {
            return Optional.empty();
        }
        // Se recorta cualquier signo de puntuación final que haya quedado pegado al URL por
        // venir de texto corrido (ej. "...únase en https://teams.live.com/xyz." -- el punto
        // final de la oración no es parte del enlace).
        String enlace = m.group();
        while (!enlace.isEmpty() && ".,;:)]}\"'".indexOf(enlace.charAt(enlace.length() - 1)) >= 0) {
            enlace = enlace.substring(0, enlace.length() - 1);
        }
        return enlace.isBlank() ? Optional.empty() : Optional.of(enlace);
    }

    private static int mesDesdeNombre(String nombre) {
        return switch (nombre.toLowerCase(Locale.ROOT)) {
            case "enero" -> 1;
            case "febrero" -> 2;
            case "marzo" -> 3;
            case "abril" -> 4;
            case "mayo" -> 5;
            case "junio" -> 6;
            case "julio" -> 7;
            case "agosto" -> 8;
            case "septiembre", "setiembre" -> 9;
            case "octubre" -> 10;
            case "noviembre" -> 11;
            case "diciembre" -> 12;
            default -> throw new IllegalArgumentException("Mes no reconocido: " + nombre);
        };
    }

    private static int mesDesdeAbreviatura(String abreviatura) {
        return switch (abreviatura.toLowerCase(Locale.ROOT)) {
            case "ene" -> 1;
            case "feb" -> 2;
            case "mar" -> 3;
            case "abr" -> 4;
            case "may" -> 5;
            case "jun" -> 6;
            case "jul" -> 7;
            case "ago" -> 8;
            case "sep", "set" -> 9;
            case "oct" -> 10;
            case "nov" -> 11;
            case "dic" -> 12;
            default -> throw new IllegalArgumentException("Mes abreviado no reconocido: " + abreviatura);
        };
    }
}
