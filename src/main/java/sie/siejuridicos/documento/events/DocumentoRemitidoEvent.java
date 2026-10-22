package sie.siejuridicos.documento.events;

import sie.siejuridicos.documento.DocumentoFirmante;

// Publicado por cada firmante de parte cuando el documento ya quedó SELLADO y se remite el
// PDF final. pdfFinal son los bytes reales (ya sellados, aún sin cifrar en reposo).
public record DocumentoRemitidoEvent(DocumentoFirmante firmante, byte[] pdfFinal) {
}
