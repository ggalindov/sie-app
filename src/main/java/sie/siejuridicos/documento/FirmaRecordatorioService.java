package sie.siejuridicos.documento;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.documento.events.DocumentoListoParaFirmarEvent;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

// Lógica de recordatorios automáticos y expiración del proceso de firma de partes (ver
// FirmaRecordatorioScheduler). Cada documento se procesa en su propia transacción para que
// un fallo puntual no afecte a los demás.
//
// Recordatorio: como solo se guarda el hash del token, no se puede "reenviar" el mismo
// enlace; se emite uno NUEVO (el anterior queda inválido) y se notifica por el mismo
// canal. Solo se recuerda al firmante cuyo turno ya llegó, hasta un máximo configurable.
// Expiración: superado el plazo global del proceso, los firmantes sin resolver pasan a
// EXPIRADO, se invalidan sus enlaces y el documento queda ANULADO, con un evento en la
// cadena de custodia.
@Service
public class FirmaRecordatorioService {

    static final List<String> ESTADOS_ACTIVOS = List.of(DocumentoFirmante.ESTADO_NOTIFICADO, DocumentoFirmante.ESTADO_OTP_VERIFICADO);
    static final List<String> ESTADOS_SIN_RESOLVER = List.of(DocumentoFirmante.ESTADO_PENDIENTE,
            DocumentoFirmante.ESTADO_NOTIFICADO, DocumentoFirmante.ESTADO_OTP_VERIFICADO);

    private final DocumentoFirmaRepository documentoFirmaRepository;
    private final DocumentoFirmanteRepository documentoFirmanteRepository;
    private final SignerTokenService signerTokenService;
    private final DocumentoTimelineService documentoTimelineService;
    private final ApplicationEventPublisher eventPublisher;
    private final String sitioPublicoUrl;
    private final boolean habilitado;
    private final Duration intervalo;
    private final int maximo;
    private final Duration expiracion;

    public FirmaRecordatorioService(DocumentoFirmaRepository documentoFirmaRepository,
                                     DocumentoFirmanteRepository documentoFirmanteRepository,
                                     SignerTokenService signerTokenService,
                                     DocumentoTimelineService documentoTimelineService,
                                     ApplicationEventPublisher eventPublisher,
                                     @Value("${app.documento.sitio-web}") String sitioPublicoUrl,
                                     @Value("${app.documento.recordatorios.habilitado:true}") boolean habilitado,
                                     @Value("${app.documento.recordatorios.intervalo-horas:24}") long intervaloHoras,
                                     @Value("${app.documento.recordatorios.maximo:3}") int maximo,
                                     @Value("${app.documento.recordatorios.expiracion-dias:10}") long expiracionDias) {
        this.documentoFirmaRepository = documentoFirmaRepository;
        this.documentoFirmanteRepository = documentoFirmanteRepository;
        this.signerTokenService = signerTokenService;
        this.documentoTimelineService = documentoTimelineService;
        this.eventPublisher = eventPublisher;
        this.sitioPublicoUrl = sitioPublicoUrl;
        this.habilitado = habilitado;
        this.intervalo = Duration.ofHours(intervaloHoras);
        this.maximo = maximo;
        this.expiracion = Duration.ofDays(expiracionDias);
    }

    public boolean estaHabilitado() {
        return habilitado;
    }

    // Ids de documentos con firmantes de partes aún sin resolver (candidatos del barrido).
    @Transactional(readOnly = true)
    public List<UUID> documentosPendientes() {
        return documentoFirmanteRepository.findByEstadoIn(ESTADOS_SIN_RESOLVER).stream()
                .map(f -> f.getDocumento().getId()).distinct().toList();
    }

    // Devuelve true si el proceso del documento expiró en esta pasada.
    @Transactional
    public boolean procesarDocumento(UUID documentoId, LocalDateTime ahora) {
        DocumentoFirma documento = documentoFirmaRepository.findById(documentoId).orElse(null);
        if (documento == null || !DocumentoFirma.ESTADO_DOC_EN_FIRMA_PARTES.equals(documento.getEstadoDocumento())) {
            return false;
        }
        List<DocumentoFirmante> firmantes = documentoFirmanteRepository.findByDocumentoIdOrderByOrdenAsc(documentoId);
        LocalDateTime inicio = firmantes.stream().map(DocumentoFirmante::getCreadoEn).min(Comparator.naturalOrder()).orElse(ahora);
        if (ahora.isAfter(inicio.plus(expiracion))) {
            expirar(documento, firmantes);
            return true;
        }
        for (DocumentoFirmante firmante : firmantes) {
            if (debeRecordarse(firmante, ahora)) {
                recordar(documento, firmante);
            }
        }
        return false;
    }

    private boolean debeRecordarse(DocumentoFirmante firmante, LocalDateTime ahora) {
        if (!ESTADOS_ACTIVOS.contains(firmante.getEstado()) || firmante.getTokenHash() == null
                || firmante.getRecordatoriosEnviados() >= maximo || !signerTokenService.esSuTurno(firmante)) {
            return false;
        }
        LocalDateTime referencia = firmante.getUltimoRecordatorioEn() != null ? firmante.getUltimoRecordatorioEn()
                : firmante.getEnlaceEmitidoEn() != null ? firmante.getEnlaceEmitidoEn() : firmante.getCreadoEn();
        return !ahora.isBefore(referencia.plus(intervalo));
    }

    private void recordar(DocumentoFirma documento, DocumentoFirmante firmante) {
        String tokenPlano = signerTokenService.renovarPorRecordatorio(firmante);
        firmante.setEstado(DocumentoFirmante.ESTADO_NOTIFICADO);
        documentoFirmanteRepository.save(firmante);
        eventPublisher.publishEvent(new DocumentoListoParaFirmarEvent(firmante, sitioPublicoUrl + "/firmar/" + tokenPlano));
        documentoTimelineService.registrar(documento, DocumentoEvento.TIPO_RECORDATORIO_ENVIADO, DocumentoEvento.ACTOR_SISTEMA,
                null, firmante, "Recordatorio " + firmante.getRecordatoriosEnviados() + " de " + maximo
                        + " enviado a " + firmante.getNombreCapturado() + " con un enlace de firma nuevo.", null, null);
    }

    private void expirar(DocumentoFirma documento, List<DocumentoFirmante> firmantes) {
        int expirados = 0;
        for (DocumentoFirmante firmante : firmantes) {
            if (ESTADOS_SIN_RESOLVER.contains(firmante.getEstado())) {
                signerTokenService.revocar(firmante);
                firmante.setEstado(DocumentoFirmante.ESTADO_EXPIRADO);
                documentoFirmanteRepository.save(firmante);
                expirados++;
            }
        }
        documento.setEstadoDocumento(DocumentoFirma.ESTADO_DOC_ANULADO);
        documentoFirmaRepository.save(documento);
        documentoTimelineService.registrarSistema(documento, DocumentoEvento.TIPO_PROCESO_EXPIRADO,
                "El proceso de firma expiró tras " + expiracion.toDays() + " día(s) sin completarse; "
                        + expirados + " firmante(s) pasaron a EXPIRADO y el documento quedó ANULADO.");
    }
}
