package sie.siejuridicos.boletin;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BoletinEnviadoRepository extends JpaRepository<BoletinEnviado, Long> {

    List<BoletinEnviado> findAllByOrderByFechaEnvioDesc();
}
