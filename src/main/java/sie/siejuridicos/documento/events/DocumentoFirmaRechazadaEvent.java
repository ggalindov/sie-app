package sie.siejuridicos.documento.events;

import sie.siejuridicos.documento.DocumentoFirma;

// Publicado cuando una parte rechaza firmar (ver DocumentoFirmaService#registrarRechazoDeParte).
// Dispara una alerta a gerencia: el proceso completo queda ANULADO de inmediato, alguien
// tiene que decidir qué sigue (volver a emitir, contactar a la parte, etc.), no es un evento
// silencioso que un admin descubra solo si entra a revisar el panel.
public record DocumentoFirmaRechazadaEvent(DocumentoFirma documento, String nombreFirmante, String motivo) {
}
