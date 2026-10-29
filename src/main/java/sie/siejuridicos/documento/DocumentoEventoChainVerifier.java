package sie.siejuridicos.documento;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

// Recalcula la cadena de hashes completa de un documento (ver HashChainCalculator) y
// confirma que la secuencia es consecutiva desde 1 y que cada eslabón encadena con el
// anterior. Ante cualquier ruptura, delega en DocumentIntegrityGuard -- misma puerta que
// usa VerificacionPublicaService para detecciones de alteración por hash de contenido --
// para no duplicar el flag de integridad en dos sitios distintos.
@Service
public class DocumentoEventoChainVerifier {

    private final DocumentoEventoRepository documentoEventoRepository;
    private final DocumentoEventoCheckpointRepository checkpointRepository;
    private final HashChainCalculator hashChainCalculator;
    private final DocumentIntegrityGuard documentIntegrityGuard;

    public DocumentoEventoChainVerifier(DocumentoEventoRepository documentoEventoRepository,
                                         DocumentoEventoCheckpointRepository checkpointRepository,
                                         HashChainCalculator hashChainCalculator,
                                         DocumentIntegrityGuard documentIntegrityGuard) {
        this.documentoEventoRepository = documentoEventoRepository;
        this.checkpointRepository = checkpointRepository;
        this.hashChainCalculator = hashChainCalculator;
        this.documentIntegrityGuard = documentIntegrityGuard;
    }

    @Transactional(readOnly = true)
    public ResultadoVerificacionCadena verificar(DocumentoFirma documento) {
        List<DocumentoEvento> eventos = documentoEventoRepository.findByDocumentoIdOrderBySecuenciaAsc(documento.getId());

        // Ataque de truncamiento: borrar los ÚLTIMOS eventos de la cadena no rompe ningún
        // eslabón (lo que queda sigue perfectamente encadenado), así que el resto de este
        // método no lo detectaría. documento_eventos_checkpoints es append-only y solo
        // puede crecer (ver AnclaDiariaIntegridadScheduler); si algún checkpoint pasado
        // registró MÁS eventos de los que existen ahora, eso es evidencia concluyente de
        // que algo se borró, sin importar si la cadena "se ve" íntegra.
        Long maximoRegistrado = checkpointRepository.maximoCantidadEventosRegistrado(documento.getId()).orElse(null);
        if (maximoRegistrado != null && maximoRegistrado > eventos.size()) {
            return marcarTruncada(documento, maximoRegistrado, eventos.size());
        }

        if (eventos.isEmpty()) {
            return ResultadoVerificacionCadena.pendiente();
        }

        String hashEsperadoAnterior = HashChainCalculator.GENESIS;
        long secuenciaEsperada = 1;
        for (DocumentoEvento evento : eventos) {
            if (evento.getSecuencia() != secuenciaEsperada) {
                return marcarRota(documento, evento.getSecuencia(),
                        "Salto en la secuencia de eventos (se esperaba " + secuenciaEsperada + ").");
            }
            if (!coincide(hashEsperadoAnterior, evento.getHashEventoAnterior())) {
                return marcarRota(documento, evento.getSecuencia(),
                        "El hash del evento anterior no coincide con el eslabón previo de la cadena.");
            }
            String hashRecalculado = recalcular(evento);
            if (!coincide(hashRecalculado, evento.getHashEvento())) {
                return marcarRota(documento, evento.getSecuencia(),
                        "El hash del evento no coincide con el recalculado a partir de sus datos.");
            }
            hashEsperadoAnterior = evento.getHashEvento();
            secuenciaEsperada++;
        }
        return ResultadoVerificacionCadena.cadenaIntegra();
    }

    private String recalcular(DocumentoEvento evento) {
        return hashChainCalculator.calcular(
                evento.getDocumento().getId(),
                evento.getSecuencia(),
                evento.getHashEventoAnterior(),
                evento.getTipoEvento(),
                evento.getActorTipo(),
                evento.getStaff() != null ? evento.getStaff().getId() : null,
                evento.getFirmante() != null ? evento.getFirmante().getId() : null,
                evento.getDescripcion(),
                evento.getIp(),
                evento.getUserAgent(),
                evento.getHashDocumentoEnEvento(),
                evento.getOcurridoEn());
    }

    // Comparación en tiempo constante (mismo patrón que OtpService): evita que la latencia
    // de la respuesta filtre, byte a byte, cuánto del hash esperado coincide.
    private boolean coincide(String esperado, String recibido) {
        if (esperado == null || recibido == null) {
            return esperado == recibido;
        }
        return MessageDigest.isEqual(
                esperado.getBytes(StandardCharsets.UTF_8),
                recibido.getBytes(StandardCharsets.UTF_8));
    }

    private ResultadoVerificacionCadena marcarRota(DocumentoFirma documento, long secuencia, String detalle) {
        documentIntegrityGuard.marcarAlteradoEnNuevaTransaccion(documento,
                "Cadena de eventos rota en la secuencia " + secuencia + ": " + detalle,
                DocumentoEvento.ACTOR_SISTEMA, null);
        return ResultadoVerificacionCadena.rota(secuencia, detalle);
    }

    private ResultadoVerificacionCadena marcarTruncada(DocumentoFirma documento, long cantidadEsperada, int cantidadActual) {
        String detalle = "Truncamiento detectado: un checkpoint diario registró " + cantidadEsperada
                + " evento(s) para este documento, pero hoy solo existen " + cantidadActual + ".";
        documentIntegrityGuard.marcarAlteradoEnNuevaTransaccion(documento, detalle, DocumentoEvento.ACTOR_SISTEMA, null);
        return ResultadoVerificacionCadena.rota(cantidadEsperada, detalle);
    }
}
