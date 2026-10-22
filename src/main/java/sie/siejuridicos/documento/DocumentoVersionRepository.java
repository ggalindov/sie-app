package sie.siejuridicos.documento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentoVersionRepository extends JpaRepository<DocumentoVersion, UUID> {

    List<DocumentoVersion> findByDocumentoId(UUID documentoId);

    Optional<DocumentoVersion> findFirstByHashContenidoIgnoreCase(String hashContenido);

    long countByDocumentoId(UUID documentoId);
}
