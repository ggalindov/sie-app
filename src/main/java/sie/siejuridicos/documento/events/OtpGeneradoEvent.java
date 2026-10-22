package sie.siejuridicos.documento.events;

import sie.siejuridicos.documento.DocumentoFirmante;

public record OtpGeneradoEvent(DocumentoFirmante firmante, String codigoPlano) {
}
