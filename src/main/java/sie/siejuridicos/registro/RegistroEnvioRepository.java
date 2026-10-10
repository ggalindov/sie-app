package sie.siejuridicos.registro;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistroEnvioRepository extends JpaRepository<RegistroEnvio, Long> {

    Page<RegistroEnvio> findAllByOrderByFechaHoraDesc(Pageable pageable);

    Page<RegistroEnvio> findByCanalOrderByFechaHoraDesc(CanalEnvio canal, Pageable pageable);

    Page<RegistroEnvio> findByTipoOrderByFechaHoraDesc(TipoEnvio tipo, Pageable pageable);

    Page<RegistroEnvio> findByCanalAndTipoOrderByFechaHoraDesc(CanalEnvio canal, TipoEnvio tipo, Pageable pageable);
}
