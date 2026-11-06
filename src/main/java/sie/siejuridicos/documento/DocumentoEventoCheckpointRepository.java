package sie.siejuridicos.documento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentoEventoCheckpointRepository extends JpaRepository<DocumentoEventoCheckpoint, UUID> {

    // El MÁXIMO histórico, no solo el último: si por cualquier motivo un checkpoint
    // quedara desordenado, esto sigue detectando un truncamiento real (la cadena de
    // eventos solo puede crecer, nunca encogerse, ver DocumentoEventoChainVerifier).
    @Query("SELECT MAX(c.cantidadEventos) FROM DocumentoEventoCheckpoint c WHERE c.documento.id = :documentoId")
    Optional<Long> maximoCantidadEventosRegistrado(@Param("documentoId") UUID documentoId);

    // Sin ORDER BY a propósito (ver AnclaDiariaIntegridadScheduler.reverificarAnclasAnteriores):
    // el orden original del ancla se construyó ordenando los UUID con UUID.compareTo() en
    // Java, que NO coincide con el orden de bytes que usa Postgres para su tipo uuid -- hay
    // que volver a ordenar en Java con el mismo comparador exacto, nunca confiar en que el
    // ORDER BY de la consulta reproduzca el mismo orden.
    List<DocumentoEventoCheckpoint> findByFecha(LocalDate fecha);
}
