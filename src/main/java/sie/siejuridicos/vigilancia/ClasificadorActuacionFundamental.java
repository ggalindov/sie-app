package sie.siejuridicos.vigilancia;

import java.util.List;
import java.util.Locale;

// Filtra, de todo el historial de actuaciones de un proceso, cuáles son decisiones de fondo
// (las que de verdad le importan al cliente y corren un término) en vez de trámites de
// rutina ("pasa al despacho", "recepción memorial" sin más). Criterio tomado del plan
// original de vigilancia judicial (sección de "documentos fundamentales"), por palabras
// clave sobre el nombre de la actuación -- no hay en la API ningún campo que ya distinga
// esto, "conDocumentos" NO sirve para eso (indica si el juzgado adjuntó archivo, no si la
// decisión es de fondo; confirmado en el spike real: "Al despacho por reparto" y "Auto
// admite demanda" ambos pueden traer conDocumentos=false).
//
// Deliberadamente solo mira nombreActuacion, no anotacion (el texto libre es demasiado
// variable entre despachos para una lista de palabras clave confiable).
//
// "admite"/"inadmite" sueltos, no "admite demanda"/"inadmite demanda": en datos reales
// (verificado 2026-10-30) un despacho real escribió la actuación como "Auto Inadmite  Auto
// No Avoca" -- sin la palabra "demanda" en absoluto. Exigirla habría dejado pasar esa
// decisión de fondo real sin marcarla.
public final class ClasificadorActuacionFundamental {

    private ClasificadorActuacionFundamental() {
    }

    private static final List<String> PALABRAS_CLAVE = List.of(
            "admite",
            "inadmite",
            // Agregado tras revisar un caso real en el panel (2026-10-30): "Auto rechaza
            // demanda" (por falta de competencia, remitiendo a otro juzgado) no quedaba
            // marcado -- rechazar la demanda es tan de fondo como inadmitirla.
            "rechaza",
            "traslado",
            "decreta prueba",
            "fija audiencia",
            "fijación de audiencia",
            "sentencia",
            "mandamiento de pago",
            "mandamiento ejecutivo",
            "medida cautelar",
            "embargo"
    );

    public static boolean esFundamental(ActuacionJudicial actuacion) {
        if (actuacion.nombreActuacion() == null || actuacion.nombreActuacion().isBlank()) {
            return false;
        }
        String normalizado = actuacion.nombreActuacion().toLowerCase(Locale.ROOT);
        return PALABRAS_CLAVE.stream().anyMatch(normalizado::contains);
    }
}
