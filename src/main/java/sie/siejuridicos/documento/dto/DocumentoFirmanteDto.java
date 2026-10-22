package sie.siejuridicos.documento.dto;

import sie.siejuridicos.documento.DocumentoFirmante;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// Vista de staff (a diferencia de FirmaResumenDto, de cara al firmante externo): expone
// metadatos técnicos de la evidencia -IP, geolocalización, rechazo de permiso- para el
// panel de seguimiento.
public record DocumentoFirmanteDto(
        String id,
        String tipoFirmante,
        String nombreCapturado,
        String estado,
        String canalNotificacion,
        LocalDateTime identidadVerificadaEn,
        String ipCaptura,
        BigDecimal geolocalizacionLat,
        BigDecimal geolocalizacionLon,
        boolean geolocalizacionRechazada,
        boolean documentoIdentidadCapturado,
        LocalDateTime firmadoEn
) {
    public static DocumentoFirmanteDto from(DocumentoFirmante firmante) {
        return new DocumentoFirmanteDto(
                firmante.getId().toString(),
                firmante.getTipoFirmante(),
                firmante.getNombreCapturado(),
                firmante.getEstado(),
                firmante.getCanalNotificacion(),
                firmante.getIdentidadVerificadaEn(),
                firmante.getIpCaptura(),
                firmante.getGeolocalizacionLat(),
                firmante.getGeolocalizacionLon(),
                firmante.isGeolocalizacionRechazada(),
                firmante.getDocumentoIdentidadImagen() != null,
                firmante.getFirmadoEn()
        );
    }
}
