package sie.siejuridicos.documento.events;

import sie.siejuridicos.documento.DocumentoFirmante;

// Publicado cuando un DocumentoFirmante (staff o parte externa) ya tiene token emitido y
// listo para notificar. urlFirma es el link público completo (/firmar/{token}).
public record DocumentoListoParaFirmarEvent(DocumentoFirmante firmante, String urlFirma) {
}
