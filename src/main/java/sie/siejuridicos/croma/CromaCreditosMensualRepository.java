package sie.siejuridicos.croma;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface CromaCreditosMensualRepository extends JpaRepository<CromaCreditosMensual, Long> {

    Optional<CromaCreditosMensual> findByMesAno(String mesAno);

    // Bloqueo pesimista para garantizar que peticiones concurrentes nunca superen el límite de créditos
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM CromaCreditosMensual c WHERE c.mesAno = :mesAno")
    Optional<CromaCreditosMensual> findByMesAnoParaActualizar(@Param("mesAno") String mesAno);
}
