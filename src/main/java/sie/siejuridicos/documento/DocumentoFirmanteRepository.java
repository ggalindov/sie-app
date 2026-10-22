package sie.siejuridicos.documento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentoFirmanteRepository extends JpaRepository<DocumentoFirmante, UUID> {

    List<DocumentoFirmante> findByDocumentoIdOrderByOrdenAsc(UUID documentoId);

    Optional<DocumentoFirmante> findByTokenHash(String tokenHash);

    long countByDocumentoIdAndEstadoNot(UUID documentoId, String estado);

    // Firmantes de partes con un enlace vigente que aún no resolvieron (ver FirmaRecordatorioService).
    List<DocumentoFirmante> findByEstadoIn(Collection<String> estados);

    // true si algún firmante de orden anterior aún no firmó ni rechazó (firma secuencial).
    boolean existsByDocumentoIdAndOrdenLessThanAndEstadoNotIn(UUID documentoId, int orden, Collection<String> estados);

    // Firmantes ya firmados pero sin sello de tiempo (ver Rfc3161RetryScheduler).
    List<DocumentoFirmante> findByEstadoAndRfc3161TokenIsNullAndHashEvidenciaIsNotNull(String estado);
}
