package sie.siejuridicos.documento;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

// Acumula, paso a paso, la evidencia técnica que sostiene el valor probatorio de la firma
// de una parte externa (Decreto 2364/2012: identificación, control exclusivo,
// confiabilidad) y cierra el paquete con un hash de evidencia único al momento de firmar,
// calculado sobre TODO lo capturado (consentimiento, geolocalización, documento de
// identidad, imagen de firma) más el hash del documento que se estaba firmando -- si la
// contraparte alega fraude o suplantación, este hash permite demostrar que ese conjunto
// exacto de evidencias no fue alterado después del acto.
@Service
public class EvidenceCaptureService {

    public void registrarConsentimiento(DocumentoFirmante firmante, String textoAceptado) {
        firmante.registrarConsentimiento(textoAceptado);
    }

    public void registrarGeolocalizacion(DocumentoFirmante firmante, BigDecimal lat, BigDecimal lon, BigDecimal precisionM) {
        firmante.registrarGeolocalizacion(lat, lon, precisionM);
    }

    public void registrarGeolocalizacionRechazada(DocumentoFirmante firmante) {
        firmante.registrarGeolocalizacionRechazada();
    }

    public void registrarIdentidad(DocumentoFirmante firmante, byte[] imagen, String contentType) {
        firmante.registrarDocumentoIdentidad(imagen, contentType);
    }

    public void registrarCapturaTecnica(DocumentoFirmante firmante, String ip, String userAgent) {
        firmante.registrarCapturaTecnica(ip, userAgent);
    }

    // Huella de dispositivo NO biométrica y señales heurísticas de proxy (ver
    // ProxyHeuristicsAnalyzer): contexto adicional, opcional -- si el navegador no la
    // envía (ej. bloqueada por el propio navegador), el resto del flujo sigue sin bloquearse.
    public void registrarContextoDispositivo(DocumentoFirmante firmante, String huellaDispositivo,
                                              String contextoDispositivoJson, String senalesProxyDetectadas) {
        firmante.registrarContextoDispositivo(huellaDispositivo, contextoDispositivoJson, senalesProxyDetectadas);
    }

    // Foto tomada con la cámara del dispositivo justo antes de firmar: NO es reconocimiento
    // facial ni biometría de matching, solo se conserva como evidencia de seguridad
    // adicional (ver DocumentoFirmante.registrarVerificacionHumana).
    public void registrarVerificacionHumana(DocumentoFirmante firmante, byte[] imagen, String contentType) {
        firmante.registrarVerificacionHumana(imagen, contentType);
    }

    // Debe llamarse con el hash del documento (DocumentoFirma.hashContenido) vigente en el
    // instante exacto de la firma, antes de que el sellado final lo vuelva a recalcular.
    // versionFormulaEvidencia distingue la fórmula v1 (sin huella/señales de proxy) de la
    // v2 (con ellas) y v3 (con la foto de verificación humana): los hash_evidencia ya
    // persistidos NUNCA se recalculan retroactivamente al subir de versión -- solo las
    // firmas nuevas usan la fórmula ampliada.
    public String calcularHashEvidencia(DocumentoFirmante firmante, byte[] firmaPng, String hashDocumentoAlFirmar) {
        StringBuilder canonico = new StringBuilder();
        canonico.append(firmante.getDocumento().getId()).append('|');
        canonico.append(firmante.getId()).append('|');
        canonico.append(firmante.getNombreCapturado()).append('|');
        canonico.append(nullSeguro(firmante.getDocumentoIdentidadCapturado())).append('|');
        canonico.append(nullSeguro(firmante.getIpCaptura())).append('|');
        canonico.append(nullSeguro(firmante.getUserAgentCaptura())).append('|');
        canonico.append(firmante.isGeolocalizacionRechazada() ? "geo:rechazada" : "geo:" + firmante.getGeolocalizacionLat()
                + "," + firmante.getGeolocalizacionLon()).append('|');
        canonico.append("identidad:").append(hashBytes(firmante.getDocumentoIdentidadImagen())).append('|');
        canonico.append("firma:").append(hashBytes(firmaPng)).append('|');
        canonico.append("consentimiento:").append(nullSeguro(firmante.getConsentimientoTexto())).append('|');
        if (firmante.getVersionFormulaEvidencia() >= 2) {
            canonico.append("dispositivo:").append(nullSeguro(firmante.getHuellaDispositivo())).append('|');
            canonico.append("proxy:").append(nullSeguro(firmante.getSenalesProxyDetectadas())).append('|');
        }
        if (firmante.getVersionFormulaEvidencia() >= 3) {
            canonico.append("foto_verificacion:").append(hashBytes(firmante.getFotoVerificacionHumana())).append('|');
        }
        canonico.append("documento:").append(nullSeguro(hashDocumentoAlFirmar));
        return HashUtil.sha256Hex(canonico.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String hashBytes(byte[] datos) {
        return datos == null ? "ninguno" : HashUtil.sha256Hex(datos);
    }

    private String nullSeguro(String valor) {
        return valor == null ? "" : valor;
    }
}
