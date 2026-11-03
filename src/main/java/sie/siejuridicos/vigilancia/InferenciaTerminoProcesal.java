package sie.siejuridicos.vigilancia;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

// Infiere, SOLO para actuaciones ya marcadas como de fondo (ver
// ClasificadorActuacionFundamental), cuántos días hábiles corre el término que abren y para
// cuándo vence (ver CalendarioJudicialColombiano) -- calculado al vuelo a partir de lo que ya
// está guardado en ActuacionJudicialRegistrada, nunca persistido aparte: es un dato derivado,
// no un hecho nuevo que pueda quedar desactualizado respecto a su propia fuente.
//
// Igual que el resto de este módulo, esto es una inferencia por palabras clave sobre el
// nombre de la actuación, NUNCA una certificación legal -- los plazos de cada tipo de auto
// (inadmisión 5 días, traslado ordinario 20 días, ejecutoria de recurso 3 días...) son los
// que más comúnmente se citan del Código General del Proceso, pero no se verificaron artículo
// por artículo contra el texto oficial. Un abogado debe confirmar el plazo real antes de que
// una fecha calculada aquí decida algo con consecuencias procesales.
// Pública (no package-private como el resto de esta carpeta): ActuacionRegistradaResponse
// (en el subpaquete .dto) necesita llamarla directamente para calcular el término al vuelo
// en la respuesta de la línea de tiempo, sin pasar por un servicio intermedio solo para
// resolver visibilidad de paquete.
public final class InferenciaTerminoProcesal {

    private InferenciaTerminoProcesal() {
    }

    public record TerminoInferido(String descripcion, int diasHabiles, LocalDate fechaVencimiento, EstadoTermino estado) {
    }

    public enum EstadoTermino {
        CORRIENDO, POR_VENCER, VENCIDO
    }

    // Recibe los dos campos planos en vez de ActuacionJudicial (el DTO de la API en vivo) o
    // ActuacionJudicialRegistrada (la entidad persistida) a propósito -- esto lo usan ambos
    // contextos (VigilanciaJudicialService sobre datos en vivo, y el mapeo de la línea de
    // tiempo sobre datos ya guardados) y no tiene sentido acoplarlo a ninguno de los dos.
    public static Optional<TerminoInferido> inferir(String nombreActuacion, LocalDate fechaRegistro) {
        if (fechaRegistro == null || nombreActuacion == null) {
            return Optional.empty();
        }
        String normalizado = nombreActuacion.toLowerCase(Locale.ROOT);

        Integer dias;
        String descripcion;
        if (normalizado.contains("inadmite") || normalizado.contains("rechaza")) {
            dias = 5;
            descripcion = "Subsanar la demanda (Art. 90 CGP)";
        } else if (normalizado.contains("traslado") && normalizado.contains("demanda")) {
            dias = 20;
            descripcion = "Contestar la demanda (Art. 90 CGP, término ordinario)";
        } else if (normalizado.contains("traslado")) {
            // Un "traslado" SIN la palabra "demanda" casi siempre es de un incidente, un
            // recurso o una excepción (ej. "Traslado Recurso Reposición Art. 319 C.G.P.",
            // "Traslado Art. 110 C.G.P." -- ambos vistos en datos reales de este mismo
            // sistema), NUNCA el traslado ordinario de 20 días -- confundirlos fue un error
            // real encontrado probando contra un caso real: marcar ese plazo corto como si
            // fueran 20 días habría sido mucho más grave que no inferir nada. 3 días es el
            // plazo corto más común de este tipo de traslados incidentales (Art. 110/319
            // CGP), pero sigue siendo una aproximación, no una lectura del auto real.
            dias = 3;
            descripcion = "Traslado de un incidente/recurso -- confirma el plazo exacto leyendo el auto";
        } else if (normalizado.contains("admite")) {
            // "Admite demanda" por sí sola no abre un término a cargo de la firma todavía
            // (el término lo abre el traslado posterior, ya cubierto arriba) -- no se infiere
            // nada para no duplicar o inventar un plazo donde no hay uno real.
            return Optional.empty();
        } else if (normalizado.contains("decreta prueba")) {
            dias = 3;
            descripcion = "Ejecutoria del auto que decreta pruebas (Art. 302 CGP)";
        } else if (normalizado.contains("mandamiento de pago") || normalizado.contains("mandamiento ejecutivo")) {
            dias = 3;
            descripcion = "Ejecutoria del mandamiento de pago (oponer excepciones, Art. 302 CGP)";
        } else {
            return Optional.empty();
        }

        LocalDate vencimiento = CalendarioJudicialColombiano.calcularVencimiento(fechaRegistro, dias);
        return Optional.of(new TerminoInferido(descripcion, dias, vencimiento, calcularEstado(vencimiento)));
    }

    // Cuando la propia Rama Judicial YA informa fechaInicial/fechaFinal para la actuación
    // (verificado en vivo el 2026-10-31 contra el endpoint real del CPNU, ver
    // ActuacionJudicial) esto es un HECHO publicado por el despacho, no una inferencia por
    // palabras clave -- ActuacionRegistradaResponse SIEMPRE lo prefiere sobre inferir()
    // cuando ambas fechas están presentes. No reutiliza el nombre "inferir" a propósito: no
    // hay nada inferido aquí.
    public static TerminoInferido desdeFechaOficial(LocalDate fechaInicial, LocalDate fechaFinal) {
        int dias = diasHabilesEntre(fechaInicial, fechaFinal);
        return new TerminoInferido(
                "Término informado directamente por la Rama Judicial",
                dias,
                fechaFinal,
                calcularEstado(fechaFinal));
    }

    // Pública: VigilanciaAdminController la necesita para recalcular el estado AL VUELO de
    // los términos ya persistidos en TerminoProcesal al listarlos para el calendario --
    // "estado" depende de la fecha de HOY, así que nunca se guarda tal cual en la base de
    // datos (quedaría desactualizado al día siguiente), se recalcula cada vez que se lee.
    public static EstadoTermino calcularEstado(LocalDate vencimiento) {
        LocalDate hoy = LocalDate.now();
        if (hoy.isAfter(vencimiento)) {
            return EstadoTermino.VENCIDO;
        }
        if (diasHabilesEntre(hoy, vencimiento) <= 2) {
            return EstadoTermino.POR_VENCER;
        }
        return EstadoTermino.CORRIENDO;
    }

    private static int diasHabilesEntre(LocalDate desde, LocalDate hasta) {
        int contados = 0;
        LocalDate fecha = desde;
        while (fecha.isBefore(hasta)) {
            fecha = fecha.plusDays(1);
            if (CalendarioJudicialColombiano.esHabil(fecha)) {
                contados++;
            }
        }
        return contados;
    }
}
