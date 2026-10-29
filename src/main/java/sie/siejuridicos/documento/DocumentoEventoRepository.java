package sie.siejuridicos.documento;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentoEventoRepository extends JpaRepository<DocumentoEvento, UUID> {

    List<DocumentoEvento> findByDocumentoIdOrderBySecuenciaAsc(UUID documentoId);

    // Último eslabón encadenado de un documento. El lock pesimista serializa únicamente las
    // escrituras concurrentes sobre el MISMO documento -- ver DocumentoTimelineService.registrar,
    // único llamador.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM DocumentoEvento e WHERE e.documento.id = :documentoId ORDER BY e.secuencia DESC")
    List<DocumentoEvento> buscarUltimoEncadenado(@Param("documentoId") UUID documentoId, Pageable pageable);

    // Eliminación en cascada manual de un documento (ver DocumentoAdminService#eliminar).
    void deleteByDocumentoId(UUID documentoId);

    // Para el checkpoint diario (ver AnclaDiariaIntegridadScheduler): todo documento que ya
    // tenga al menos un evento, sin bloqueo pesimista -- a diferencia de
    // buscarUltimoEncadenado (que SÍ lo necesita, serializa escrituras concurrentes sobre el
    // mismo documento), este job es de solo lectura.
    @Query("SELECT DISTINCT e.documento.id FROM DocumentoEvento e")
    List<UUID> buscarDocumentoIdsConEventos();

    Optional<DocumentoEvento> findTop1ByDocumentoIdOrderBySecuenciaDesc(UUID documentoId);
}
