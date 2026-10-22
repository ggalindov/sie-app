package sie.siejuridicos.documento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FirmaAplicacionClaveRepository extends JpaRepository<FirmaAplicacionClave, UUID> {

    Optional<FirmaAplicacionClave> findByActivaTrue();

    Optional<FirmaAplicacionClave> findByKid(String kid);
}
