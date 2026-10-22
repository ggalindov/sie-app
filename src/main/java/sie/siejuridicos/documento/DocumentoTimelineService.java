package sie.siejuridicos.documento;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.usuario.UsuarioInterno;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

// Única puerta de entrada para registrar hitos en la línea de tiempo técnica de un
// documento (documento_eventos). Siempre estampa el hash vigente del documento en ese
// instante Y encadena el evento con el anterior del mismo documento (ver
// HashChainCalculator), para que la secuencia completa sirva como cadena de custodia
// verificable, no solo una lista de snapshots sueltos.
@Service
public class DocumentoTimelineService {

    private final DocumentoEventoRepository documentoEventoRepository;
    private final HashChainCalculator hashChainCalculator;

    public DocumentoTimelineService(DocumentoEventoRepository documentoEventoRepository,
                                     HashChainCalculator hashChainCalculator) {
        this.documentoEventoRepository = documentoEventoRepository;
        this.hashChainCalculator = hashChainCalculator;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public DocumentoEvento registrar(DocumentoFirma documento, String tipoEvento, String actorTipo, UsuarioInterno staff,
                                      DocumentoFirmante firmante, String descripcion, String ip, String userAgent) {
        List<DocumentoEvento> ultimo = documentoEventoRepository.buscarUltimoEncadenado(documento.getId(), PageRequest.of(0, 1));
        long secuencia = ultimo.isEmpty() ? 1L : ultimo.get(0).getSecuencia() + 1;
        String hashAnterior = ultimo.isEmpty() ? HashChainCalculator.GENESIS : ultimo.get(0).getHashEvento();
        // Truncado a milisegundos (más grueso que la precisión de microsegundos de Postgres,
        // así que sobrevive el viaje de ida y vuelta a la base sin perder ni un dígito): si se
        // usara la precisión completa de LocalDateTime.now() (nanosegundos), el valor
        // recargado después desde la base ya no coincidiría byte a byte con el usado aquí
        // para el HMAC, y DocumentoEventoChainVerifier reportaría una ruptura falsa en TODOS
        // los eventos, sin que nadie los haya alterado de verdad.
        LocalDateTime ocurridoEn = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        String hashDocumentoEnEvento = documento.getHashContenido();

        String hashEvento = hashChainCalculator.calcular(documento.getId(), secuencia, hashAnterior, tipoEvento,
                actorTipo, staff != null ? staff.getId() : null, firmante != null ? firmante.getId() : null,
                descripcion, ip, userAgent, hashDocumentoEnEvento, ocurridoEn);

        DocumentoEvento evento = new DocumentoEvento(documento, tipoEvento, actorTipo, staff, firmante, descripcion,
                ip, userAgent, hashDocumentoEnEvento, secuencia, hashAnterior, hashEvento, ocurridoEn);
        return documentoEventoRepository.save(evento);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public DocumentoEvento registrarSistema(DocumentoFirma documento, String tipoEvento, String descripcion) {
        return registrar(documento, tipoEvento, DocumentoEvento.ACTOR_SISTEMA, null, null, descripcion, null, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public DocumentoEvento registrarStaff(DocumentoFirma documento, String tipoEvento, UsuarioInterno staff, String descripcion) {
        return registrar(documento, tipoEvento, DocumentoEvento.ACTOR_STAFF, staff, null, descripcion, null, null);
    }
}
