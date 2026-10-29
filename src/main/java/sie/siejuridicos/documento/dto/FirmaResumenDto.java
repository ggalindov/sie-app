package sie.siejuridicos.documento.dto;

import sie.siejuridicos.documento.DocumentoFirmante;

// Lo mínimo que necesita ver el firmante externo al abrir su link único: a propósito NO
// incluye el contenido del documento (la vista previa del PDF no es objeto de este
// endpoint) ni datos de otros firmantes del mismo documento.
public record FirmaResumenDto(
        String documentoNombre,
        String nombreFirmante,
        String estadoFirmante,
        boolean identidadVerificada,
        boolean otpVerificado,
        boolean consentimientoRegistrado,
        boolean geolocalizacionRegistrada,
        boolean identidadRegistrada,
        boolean verificacionHumanaRegistrada,
        boolean advertenciaSuplantacionIdentidadAceptada,
        boolean advertenciaSuplantacionVerificacionAceptada,
        boolean firmado
) {
    public static FirmaResumenDto from(DocumentoFirmante firmante) {
        return new FirmaResumenDto(
                firmante.getDocumento().getNombre(),
                firmante.getNombreCapturado(),
                firmante.getEstado(),
                firmante.getIdentidadVerificadaEn() != null,
                DocumentoFirmante.ESTADO_OTP_VERIFICADO.equals(firmante.getEstado()) || DocumentoFirmante.ESTADO_FIRMADO.equals(firmante.getEstado()),
                firmante.getConsentimientoAceptadoEn() != null,
                firmante.getGeolocalizacionCapturadaEn() != null || firmante.isGeolocalizacionRechazada(),
                firmante.getDocumentoIdentidadImagen() != null,
                firmante.getFotoVerificacionHumana() != null,
                firmante.getAdvertenciaSuplantacionIdentidadAceptadaEn() != null,
                firmante.getAdvertenciaSuplantacionVerificacionAceptadaEn() != null,
                DocumentoFirmante.ESTADO_FIRMADO.equals(firmante.getEstado())
        );
    }
}
