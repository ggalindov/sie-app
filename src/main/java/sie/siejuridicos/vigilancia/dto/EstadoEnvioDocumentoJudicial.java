package sie.siejuridicos.vigilancia.dto;

// Tres estados, nunca solo true/false -- EmailService.enviarHtmlConAdjunto() devuelve
// true tanto cuando el correo salió de verdad como cuando app.bloqueo-total-clientes lo
// canceló a propósito (para que los schedulers automáticos no traten el bloqueo de
// seguridad como una falla real). Para esta acción MANUAL del panel esa ambigüedad sería
// engañosa: un abogado que aprieta "Enviar" necesita saber con certeza si de verdad salió
// o si el entorno lo bloqueó, nunca un "listo" falso. Ver EnvioDocumentoJudicialService.
public enum EstadoEnvioDocumentoJudicial {
    ENVIADO,
    BLOQUEADO_SEGURIDAD,
    FALLIDO
}
