package sie.siejuridicos.documento.pdf;

import java.time.LocalDateTime;
import java.util.List;

// Datos ya resueltos (sin entidades JPA ni criptografía) que necesita
// EvidenceCertificatePdfGenerator. Construido por EvidenceCertificateService dentro de su
// propia transacción -- el paquete 'pdf' nunca verifica firmas ni hashes, solo diagrama
// datos que ya le llegan calculados.
public record EvidenceCertificateData(
        String documentoId,
        String nombreDocumento,
        Long numeroSecuencia,
        LocalDateTime fechaDocumento,
        String hashContenido,
        String estadoDocumento,
        boolean firmaAppValida,
        String firmaAppKid,
        boolean selloTiempoDocumentoAplicado,
        String selloTiempoDocumentoUrl,
        LocalDateTime selloTiempoDocumentoEn,
        boolean cadenaEventosIntegra,
        String cadenaEventosDetalle,
        String codigoVerificacion,
        String urlVerificacion,
        LocalDateTime certificadoGeneradoEn,
        List<FirmanteData> firmantes,
        // Pedido explícito del usuario: al cliente/firmante no se le debe exponer
        // información técnica/forense que pueda ayudar a vulnerar la seguridad del
        // documento (IP, user-agent, huella de dispositivo, señales de proxy, hash de
        // evidencia, ni el detalle exacto de dónde se rompió la cadena de eventos). El
        // seguimiento 24/7 de integridad lo hace la firma internamente -- false para la
        // descarga pública por token (EvidenceCertificateService#generarPorFirmante),
        // true solo para el consolidado que descarga el staff autenticado.
        boolean detalleTecnicoCompleto
) {
    public record FirmanteData(
            String tipoFirmante,
            String nombreCapturado,
            String documentoIdentidadCapturado,
            String estado,
            LocalDateTime creadoEn,
            LocalDateTime tokenExpiraEn,
            String ipCaptura,
            String userAgentCaptura,
            String huellaDispositivo,
            String senalesProxyDetectadas,
            boolean geolocalizacionRechazada,
            String geolocalizacionTexto,
            LocalDateTime geolocalizacionCapturadaEn,
            String consentimientoTexto,
            LocalDateTime consentimientoAceptadoEn,
            String hashEvidencia,
            LocalDateTime firmadoEn,
            boolean selloTiempoAplicado,
            String selloTiempoUrl,
            boolean selloTiempoValido,
            byte[] firmaImagenPng,
            byte[] documentoIdentidadImagen,
            boolean verificacionHumanaCapturada,
            String verificacionHumanaHash,
            byte[] fotoVerificacionHumana
    ) {
    }
}
