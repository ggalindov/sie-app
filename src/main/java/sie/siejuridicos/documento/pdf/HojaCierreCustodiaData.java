package sie.siejuridicos.documento.pdf;

import java.time.LocalDateTime;
import java.util.List;

// Datos ya resueltos (sin entidades JPA) para la Hoja de Cierre de Custodia que
// CierreCustodiaPdfAppender anexa como ÚLTIMA página del documento oficial ya sellado -- un
// resumen de auditoría integrado, al estilo DocuSign/Adobe Sign, para que quien reciba el
// PDF no tenga que buscar el certificado de evidencia por separado para ver quién firmó y cómo.
//
// Deliberadamente NO incluye el hash SHA-256 final ni el sello de tiempo RFC 3161 del
// documento: ambos se calculan DESPUÉS de anexar esta hoja (la hoja misma pasa a formar
// parte del contenido que se hashea/sella/firma con PAdES), así que cualquier valor
// impreso aquí quedaría desactualizado en el momento en que el lector lo vea. Esos valores
// definitivos se consultan en el certificado de evidencia técnica (generado bajo demanda,
// siempre refleja el estado final real) o en la verificación pública por código.
public record HojaCierreCustodiaData(
        String nombreDocumento,
        String codigoVerificacion,
        String urlVerificacion,
        LocalDateTime cierreGeneradoEn,
        List<FirmanteResumen> firmantes
) {
    public record FirmanteResumen(
            String nombreCapturado,
            String tipoFirmante,
            String documentoIdentidadCapturado,
            LocalDateTime firmadoEn,
            String ipCaptura,
            String userAgentCaptura,
            String senalesProxyDetectadas,
            String hashEvidencia,
            byte[] fotoVerificacionHumana
    ) {
    }
}
