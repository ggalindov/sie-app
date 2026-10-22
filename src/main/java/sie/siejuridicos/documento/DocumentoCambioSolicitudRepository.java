package sie.siejuridicos.documento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentoCambioSolicitudRepository extends JpaRepository<DocumentoCambioSolicitud, UUID> {

    List<DocumentoCambioSolicitud> findByEstadoOrderBySolicitadoEnDesc(String estado);

    List<DocumentoCambioSolicitud> findByDocumentoIdOrderBySolicitadoEnDesc(UUID documentoId);

    // Eliminación en cascada manual de un documento (ver DocumentoAdminService#eliminar).
    void deleteByDocumentoId(UUID documentoId);
}
