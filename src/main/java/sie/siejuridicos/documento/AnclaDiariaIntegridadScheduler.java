package sie.siejuridicos.documento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.documento.events.AnclaIntegridadComprometidaEvent;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
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
    private final ApplicationEventPublisher eventPublisher;

    public AnclaDiariaIntegridadScheduler(DocumentoEventoRepository documentoEventoRepository,
                                           DocumentoFirmaRepository documentoFirmaRepository,
                                           DocumentoEventoCheckpointRepository checkpointRepository,
                                           AnclaDiariaIntegridadRepository anclaRepository,
                                           Rfc3161TimestampClient rfc3161TimestampClient,
                                           ApplicationEventPublisher eventPublisher) {
        this.documentoEventoRepository = documentoEventoRepository;
        this.documentoFirmaRepository = documentoFirmaRepository;
        this.checkpointRepository = checkpointRepository;
        this.anclaRepository = anclaRepository;
        this.rfc3161TimestampClient = rfc3161TimestampClient;
        this.eventPublisher = eventPublisher;
    }

    @Scheduled(cron = "${app.documento.ancla-diaria.cron:0 0 2 * * *}")
    @Transactional
    public void generarAnclaDiaria() {
        reintentarSellosPendientes();
        // Hueco real cerrado en la auditoría del usuario (2026-11-06): el ancla se sellaba
        // con RFC 3161 pero nunca se volvía a comparar contra el estado actual de la base de
        // datos -- sin esto, alguien con acceso directo de escritura podía alterar
        // documento_eventos Y documento_eventos_checkpoints a la vez (encubriendo el
        // truncamiento que DocumentoEventoChainVerifier sí detecta por sí solo) sin que nada
        // lo notara jamás, ya que el sello externo nunca se recalculaba. Se corre ANTES de
        // generar el ancla de hoy, sobre días anteriores, en la misma transacción diaria.
        reverificarAnclasAnteriores();

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

    // Recalcula el hash agregado de cada ancla de un día ANTERIOR (nunca el de hoy, que
    // recién se está armando) a partir del estado ACTUAL de documento_eventos_checkpoints, y
    // lo compara contra el que ya quedó sellado externamente con RFC 3161 ese mismo día. Si
    // no coincide, es prueba de que esos checkpoints fueron modificados DESPUÉS del sello,
    // sin pasar por la aplicación (DocumentoEventoCheckpointRepository no expone ningún
    // método de escritura fuera de AnclaDiariaIntegridadScheduler). Nunca marca documentos
    // individuales ALTERADO desde aquí: el ancla solo prueba el agregado de un día completo,
    // no identifica cuál checkpoint cambió -- amerita alerta crítica y revisión manual, no
    // un flag automático que podría señalar al documento equivocado.
    private void reverificarAnclasAnteriores() {
        LocalDate hoy = LocalDate.now();
        List<AnclaDiariaIntegridad> anclas = anclaRepository.findByFechaBeforeAndIntegraTrue(hoy);
        LocalDateTime ahora = LocalDateTime.now();
        for (AnclaDiariaIntegridad ancla : anclas) {
            String hashRecalculado = recalcularHashAgregado(ancla.getFecha());
            if (hashRecalculado.equalsIgnoreCase(ancla.getHashAgregado())) {
                ancla.marcarVerificadaIntegra(ahora);
                continue;
            }
            String detalle = "El hash agregado recalculado a partir de los checkpoints actuales ya no coincide "
                    + "con el sellado externamente con RFC 3161 el " + ancla.getCreadoEn()
                    + " -- los registros de auditoría de ese día cambiaron después de haber sido sellados.";
            ancla.marcarComprometida(detalle, ahora);
            anclaRepository.save(ancla);
            log.error("¡ALERTA CRÍTICA! El ancla diaria de integridad del {} ya no coincide con los checkpoints "
                    + "actuales -- posible manipulación directa de la base de datos.", ancla.getFecha());
            eventPublisher.publishEvent(new AnclaIntegridadComprometidaEvent(ancla.getFecha(), detalle));
        }
    }

    // MISMO formato canónico y MISMO criterio de orden que generarAnclaDiaria() al crear el
    // ancla original -- documentoId:secuencia:hashEvento, ordenado por UUID.compareTo() en
    // Java. Nunca se usa un ORDER BY de SQL para esto: Postgres ordena su tipo uuid por bytes
    // crudos, que NO coincide con UUID.compareTo() de Java, así que confiar en el orden de la
    // consulta produciría una "alteración" falsa en días que en realidad están intactos.
    private String recalcularHashAgregado(LocalDate fecha) {
        List<DocumentoEventoCheckpoint> checkpoints = checkpointRepository.findByFecha(fecha).stream()
                .sorted(Comparator.comparing(c -> c.getDocumento().getId()))
                .toList();
        StringBuilder canonico = new StringBuilder();
        for (DocumentoEventoCheckpoint c : checkpoints) {
            canonico.append(c.getDocumento().getId()).append(':').append(c.getCantidadEventos()).append(':').append(c.getHashUltimoEvento()).append('|');
        }
        return HashUtil.sha256Hex(canonico.toString().getBytes(StandardCharsets.UTF_8));
    }

    private void sellarSiEsPosible(AnclaDiariaIntegridad ancla, String hashAgregadoHex) {
        rfc3161TimestampClient.sellar(HashUtil.hexToBytes(hashAgregadoHex))
                .ifPresent(resultado -> ancla.completarSello(resultado.token(), resultado.urlTsa()));
    }
}
