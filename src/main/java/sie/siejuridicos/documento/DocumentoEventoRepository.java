package sie.siejuridicos.documento;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DocumentoEventoRepository extends JpaRepository<DocumentoEvento, UUID> {

    List<DocumentoEvento> findByDocumentoIdOrderBySecuenciaAsc(UUID documentoId);

    // Último eslabón encadenado de un documento. El lock pesimista serializa únicamente las
    // escrituras concurrentes sobre el MISMO documento -- ver DocumentoTimelineService.registrar,
    // único llamador.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM DocumentoEvento e WHERE e.documento.id = :documentoId ORDER BY e.secuencia DESC")
    List<DocumentoEvento> buscarUltimoEncadenado(@Param("documentoId") UUID documentoId, Pageable pageable);
}
