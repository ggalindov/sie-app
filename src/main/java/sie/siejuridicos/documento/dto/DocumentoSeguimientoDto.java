package sie.siejuridicos.documento.dto;

import sie.siejuridicos.documento.DocumentoEvento;
import sie.siejuridicos.documento.DocumentoFirma;
import sie.siejuridicos.documento.DocumentoFirmante;
import sie.siejuridicos.documento.ResultadoVerificacionCadena;

import java.util.List;

public record DocumentoSeguimientoDto(
        String documentoId,
        String nombre,
        String estadoDocumento,
        String estadoIntegridad,
        List<DocumentoFirmanteDto> firmantes,
        List<DocumentoEventoDto> eventos,
        boolean cadenaEventosIntegra,
        String cadenaEventosDetalle
) {
    public static DocumentoSeguimientoDto from(DocumentoFirma documento, List<DocumentoFirmante> firmantes,
                                                List<DocumentoEvento> eventos, ResultadoVerificacionCadena cadena) {
        return new DocumentoSeguimientoDto(
                documento.getId().toString(),
                documento.getNombre(),
                documento.getEstadoDocumento(),
                documento.getEstadoIntegridad(),
                firmantes.stream().map(DocumentoFirmanteDto::from).toList(),
                eventos.stream().map(DocumentoEventoDto::from).toList(),
                cadena.integra(),
                cadena.detalle()
        );
    }
}
