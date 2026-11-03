package sie.siejuridicos.vigilancia.dto;

import sie.siejuridicos.vigilancia.ActuacionJudicial;
import sie.siejuridicos.vigilancia.DocumentoProvidenciaCandidato;

import java.time.LocalDateTime;
import java.util.List;

// Respuesta de la vista PREVIA (nunca envía nada a nadie, ver VigilanciaJudicialService y
// CasoAdminController) de la última actuación de fondo encontrada para un caso, junto con
// los documentos candidatos del boletín de esa fecha en Publicaciones Procesales. Pensada
// para que un abogado la revise y decida manualmente -- no hay ningún endpoint todavía que
// dispare un envío real a partir de esto (ver memoria del proyecto,
// vigilancia-judicial-alcance.md).
public record DocumentoRecienteResponse(
        boolean disponible,
        String mensaje,
        String nombreActuacion,
        String anotacion,
        LocalDateTime fechaActuacion,
        List<DocumentoProvidenciaCandidato> documentosCandidatos
) {
    public static DocumentoRecienteResponse noDisponible(String mensaje) {
        return new DocumentoRecienteResponse(false, mensaje, null, null, null, List.of());
    }

    public static DocumentoRecienteResponse sinActuacionFundamental() {
        return new DocumentoRecienteResponse(false,
                "El proceso no tiene todavía ninguna actuación de fondo (admisión, traslado, "
                        + "sentencia, mandamiento o medida cautelar) en el historial de la Rama Judicial.",
                null, null, null, List.of());
    }

    public static DocumentoRecienteResponse encontrada(ActuacionJudicial actuacion,
                                                         List<DocumentoProvidenciaCandidato> candidatos) {
        String mensaje = candidatos.isEmpty()
                ? "Se encontró la actuación, pero no se pudo localizar el boletín de Publicaciones "
                        + "Procesales de esa fecha (puede ser muy antigua, ver limitación de "
                        + "PublicacionesProcesalesClient)."
                : "Revisa los documentos candidatos antes de remitir cualquiera al cliente -- "
                        + "ninguno fue verificado automáticamente como el correcto.";
        return new DocumentoRecienteResponse(true, mensaje,
                actuacion.nombreActuacion(), actuacion.anotacion(), actuacion.fechaRegistro(), candidatos);
    }
}
