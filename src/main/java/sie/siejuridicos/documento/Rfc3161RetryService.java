package sie.siejuridicos.documento;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

// Reintento individual, cada uno en su propia transacción, de un sello de tiempo RFC 3161
// que falló en el momento del sellado original (ver Rfc3161RetryScheduler, que orquesta el
// barrido periódico). Separado del scheduler para que @Transactional funcione (un
// @Scheduled llamando a un método propio anotado no pasa por el proxy de Spring).
@Service
public class Rfc3161RetryService {

    private final DocumentoFirmaRepository documentoFirmaRepository;
    private final DocumentoFirmanteRepository documentoFirmanteRepository;
    private final Rfc3161TimestampClient rfc3161TimestampClient;
    private final DocumentoTimelineService documentoTimelineService;

    public Rfc3161RetryService(DocumentoFirmaRepository documentoFirmaRepository,
                                DocumentoFirmanteRepository documentoFirmanteRepository,
                                Rfc3161TimestampClient rfc3161TimestampClient,
                                DocumentoTimelineService documentoTimelineService) {
        this.documentoFirmaRepository = documentoFirmaRepository;
        this.documentoFirmanteRepository = documentoFirmanteRepository;
        this.rfc3161TimestampClient = rfc3161TimestampClient;
        this.documentoTimelineService = documentoTimelineService;
    }

    // true si el reintento obtuvo un sello nuevo (para que el scheduler lo cuente/loguee).
    @Transactional
    public boolean reintentarDocumento(UUID documentoId) {
        DocumentoFirma documento = documentoFirmaRepository.findById(documentoId).orElse(null);
        if (documento == null || documento.getRfc3161Token() != null || documento.getHashContenido() == null) {
            return false; // ya no aplica (otro reintento se adelantó, o el documento cambió)
        }
        return rfc3161TimestampClient.sellar(HashUtil.hexToBytes(documento.getHashContenido()))
                .map(sello -> {
                    documento.setRfc3161Token(sello.token());
                    documento.setRfc3161UrlTsa(sello.urlTsa());
                    documento.setRfc3161SelladoEn(sello.selladoEn());
                    documentoTimelineService.registrarSistema(documento, DocumentoEvento.TIPO_SELLO_TIEMPO_APLICADO,
                            "Sello de tiempo RFC 3161 obtenido en un reintento automático de " + sello.urlTsa() + ".");
                    return true;
                })
                .orElse(false);
    }

    @Transactional
    public boolean reintentarFirmante(UUID firmanteId) {
        DocumentoFirmante firmante = documentoFirmanteRepository.findById(firmanteId).orElse(null);
        if (firmante == null || firmante.getRfc3161Token() != null || firmante.getHashEvidencia() == null) {
            return false;
        }
        return rfc3161TimestampClient.sellar(HashUtil.hexToBytes(firmante.getHashEvidencia()))
                .map(sello -> {
                    firmante.registrarSelloTiempo(sello.token(), sello.urlTsa());
                    documentoTimelineService.registrar(firmante.getDocumento(), DocumentoEvento.TIPO_SELLO_TIEMPO_APLICADO,
                            DocumentoEvento.ACTOR_SISTEMA, null, firmante,
                            "Sello de tiempo RFC 3161 de " + firmante.getNombreCapturado() + " obtenido en un reintento automático de "
                                    + sello.urlTsa() + ".",
                            null, null);
                    return true;
                })
                .orElse(false);
    }
}
