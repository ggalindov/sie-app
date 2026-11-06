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
// equivalente). Pedido explícito del usuario (2026-11-06): "el sistema debe detectar
// tambien si programa el estado alguna audiencia... se tiene que ver reflejada de manera
// automatica". Mismo criterio que ClasificadorMovimientoJudicial (del que es complemento,
// nunca se usa solo): texto libre sobre lo que la Rama Judicial escribió en la anotación de
// la actuación, nunca un hecho verificado por un humano -- un abogado SIEMPRE debe confirmar
// leyendo el auto real antes de agendar nada sobre esta fecha. Deliberadamente sin persistir
// nada nuevo, se calcula al vuelo sobre el mismo texto ya guardado (mismo motivo que el resto
// de este paquete: nunca queda desactualizado respecto a su fuente, no exige migración).
public final class ExtractorFechaAudiencia {

    private ExtractorFechaAudiencia() {
    }

    public record FechaAudiencia(LocalDate fecha, LocalTime hora) {
    }

    private static final Pattern FECHA_LARGA = Pattern.compile(
            "(\\d{1,2})\\s+de\\s+(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre)\\s+de\\s+(\\d{4})",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern FECHA_NUMERICA = Pattern.compile("\\b(\\d{1,2})[/-](\\d{1,2})[/-](\\d{4})\\b");

    private static final Pattern FECHA_ISO = Pattern.compile("\\b(\\d{4})-(\\d{1,2})-(\\d{1,2})\\b");

    // "9:00 a.m.", "09:00am", "2:30 p. m.", "14:30" (24h, sin am/pm).
    private static final Pattern HORA = Pattern.compile(
            "(\\d{1,2}):(\\d{2})\\s*(a\\.?\\s*m\\.?|p\\.?\\s*m\\.?)?", Pattern.CASE_INSENSITIVE);

    // Devuelve la PRIMERA fecha (futura o pasada, no se filtra aquí) reconocible en el texto,
    // con su hora si el texto trae una cerca. Nunca lanza: un texto sin fecha reconocible
    // simplemente no trae nada que mostrar en el calendario (el resto de la detección de
    // "audiencia programada" vía ClasificadorMovimientoJudicial sigue intacta igual).
    public static Optional<FechaAudiencia> extraer(String texto) {
        if (texto == null || texto.isBlank()) {
            return Optional.empty();
        }
        Optional<LocalDate> fecha = extraerFechaLarga(texto)
                .or(() -> extraerFechaIso(texto))
                .or(() -> extraerFechaNumerica(texto));
        return fecha.map(f -> new FechaAudiencia(f, extraerHora(texto).orElse(null)));
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
}
