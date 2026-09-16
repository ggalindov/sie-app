package sie.siejuridicos.crm;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TareaCrmRepository extends JpaRepository<TareaCrm, Long> {

    List<TareaCrm> findByClienteCrmIdOrderByFechaCreacionDesc(Long clienteCrmId);

    List<TareaCrm> findByCompletadaFalseOrderByFechaVencimientoAsc();
}
