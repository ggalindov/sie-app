package sie.siejuridicos.croma;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface CromaLegalProCacheRepository extends JpaRepository<CromaLegalProCache, Long> {

    Optional<CromaLegalProCache> findByRadicado(String radicado);

    Optional<CromaLegalProCache> findFirstByCasoIdOrderByFechaExtraccionDesc(Long casoId);

    @Query("SELECT c FROM CromaLegalProCache c WHERE c.fechaExpiracion > :ahora ORDER BY c.fechaExtraccion DESC")
    List<CromaLegalProCache> listarVigentes(@Param("ahora") OffsetDateTime ahora);

    @Modifying
    @Query("DELETE FROM CromaLegalProCache c WHERE c.fechaExpiracion <= :ahora")
    int eliminarExpirados(@Param("ahora") OffsetDateTime ahora);
}
