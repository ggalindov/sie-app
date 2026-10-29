package sie.siejuridicos.documento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Job diario: (1) toma un "checkpoint" de cuántos eventos tiene cada documento con al menos
// un evento (ver DocumentoEventoCheckpoint -- la detección real de truncamiento vive en
// DocumentoEventoChainVerifier, que compara contra el máximo histórico de estos
// checkpoints), y (2) sella con RFC 3161 un hash agregado de todos los checkpoints del día,
// como prueba externa adicional de que el estado de todo el historial de auditoría en esa
// fecha era exactamente ese (ver AnclaDiariaIntegridad). El sellado nunca bloquea el job:
// Rfc3161TimestampClient ya es tolerante a fallos por diseño (ver su propio comentario de
// clase) -- si la TSA externa no responde, el ancla queda con token null y se completa en
// un reintento al día siguiente (reintentarSellosPendientes), mismo espíritu que
// Rfc3161RetryScheduler ya aplica a los sellos individuales de cada documento.
@Component
public class AnclaDiariaIntegridadScheduler {

    private static final Logger log = LoggerFactory.getLogger(AnclaDiariaIntegridadScheduler.class);

    private final DocumentoEventoRepository documentoEventoRepository;
    private final DocumentoFirmaRepository documentoFirmaRepository;
    private final DocumentoEventoCheckpointRepository checkpointRepository;
    private final AnclaDiariaIntegridadRepository anclaRepository;
    private final Rfc3161TimestampClient rfc3161TimestampClient;

    public AnclaDiariaIntegridadScheduler(DocumentoEventoRepository documentoEventoRepository,
                                           DocumentoFirmaRepository documentoFirmaRepository,
                                           DocumentoEventoCheckpointRepository checkpointRepository,
                                           AnclaDiariaIntegridadRepository anclaRepository,
                                           Rfc3161TimestampClient rfc3161TimestampClient) {
        this.documentoEventoRepository = documentoEventoRepository;
        this.documentoFirmaRepository = documentoFirmaRepository;
        this.checkpointRepository = checkpointRepository;
        this.anclaRepository = anclaRepository;
        this.rfc3161TimestampClient = rfc3161TimestampClient;
    }

    @Scheduled(cron = "${app.documento.ancla-diaria.cron:0 0 2 * * *}")
    @Transactional
    public void generarAnclaDiaria() {
        reintentarSellosPendientes();

        LocalDate hoy = LocalDate.now();
        if (anclaRepository.findByFecha(hoy).isPresent()) {
            log.info("Ancla diaria de integridad: ya existe una para {}, no se repite.", hoy);
            return;
        }

        List<UUID> documentoIds = documentoEventoRepository.buscarDocumentoIdsConEventos().stream().sorted().toList();
        if (documentoIds.isEmpty()) {
            log.info("Ancla diaria de integridad: ningún documento con eventos todavía, se omite por hoy.");
            return;
        }

        LocalDateTime ahora = LocalDateTime.now();
        StringBuilder canonico = new StringBuilder();
        int checkpointsCreados = 0;
        for (UUID documentoId : documentoIds) {
            Optional<DocumentoEvento> ultimo = documentoEventoRepository.findTop1ByDocumentoIdOrderBySecuenciaDesc(documentoId);
            if (ultimo.isEmpty()) {
                continue;
            }
            DocumentoEvento evento = ultimo.get();
            DocumentoEventoCheckpoint checkpoint = new DocumentoEventoCheckpoint(
                    documentoFirmaRepository.getReferenceById(documentoId), evento.getSecuencia(), evento.getHashEvento(), hoy, ahora);
            checkpointRepository.save(checkpoint);
            checkpointsCreados++;
            canonico.append(documentoId).append(':').append(evento.getSecuencia()).append(':').append(evento.getHashEvento()).append('|');
        }

        String hashAgregado = HashUtil.sha256Hex(canonico.toString().getBytes(StandardCharsets.UTF_8));
        AnclaDiariaIntegridad ancla = new AnclaDiariaIntegridad(hoy, hashAgregado, checkpointsCreados, ahora);
        sellarSiEsPosible(ancla, hashAgregado);
        anclaRepository.save(ancla);
        log.info("Ancla diaria de integridad generada para {}: {} checkpoint(s), sellada = {}.",
                hoy, checkpointsCreados, ancla.getTokenRfc3161() != null);
    }

    // Anclas de días anteriores que quedaron sin sellar porque la TSA externa no respondió a
    // tiempo (Rfc3161TimestampClient nunca lanza, solo devuelve Optional.empty() -- ver su
    // comentario de clase). Se reintenta ANTES de generar la de hoy, no después, para que un
    // reintento tardío nunca se confunda con el sello del día actual.
    private void reintentarSellosPendientes() {
        List<AnclaDiariaIntegridad> pendientes = anclaRepository.findByTokenRfc3161IsNull();
        for (AnclaDiariaIntegridad ancla : pendientes) {
            sellarSiEsPosible(ancla, ancla.getHashAgregado());
        }
        if (!pendientes.isEmpty()) {
            log.info("Reintento de anclas diarias: {} pendiente(s) de sello de tiempo revisada(s).", pendientes.size());
        }
    }

    private void sellarSiEsPosible(AnclaDiariaIntegridad ancla, String hashAgregadoHex) {
        rfc3161TimestampClient.sellar(HashUtil.hexToBytes(hashAgregadoHex))
                .ifPresent(resultado -> ancla.completarSello(resultado.token(), resultado.urlTsa()));
    }
}
