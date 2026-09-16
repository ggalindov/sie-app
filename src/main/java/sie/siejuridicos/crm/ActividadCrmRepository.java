package sie.siejuridicos.crm;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActividadCrmRepository extends JpaRepository<ActividadCrm, Long> {

    List<ActividadCrm> findByClienteCrmIdOrderByFechaActividadDesc(Long clienteCrmId);

    List<ActividadCrm> findTop20ByOrderByFechaActividadDesc();
}
