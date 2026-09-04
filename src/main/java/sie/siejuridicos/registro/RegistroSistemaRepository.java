package sie.siejuridicos.registro;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistroSistemaRepository extends JpaRepository<RegistroSistema, Long> {

    Page<RegistroSistema> findAllByOrderByFechaHoraDesc(Pageable pageable);

    Page<RegistroSistema> findByTipoOrderByFechaHoraDesc(TipoRegistroSistema tipo, Pageable pageable);
}
