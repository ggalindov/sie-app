package sie.siejuridicos.documento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FirmaPadesClaveRepository extends JpaRepository<FirmaPadesClave, UUID> {

    Optional<FirmaPadesClave> findByActivaTrue();

    Optional<FirmaPadesClave> findByKid(String kid);
}
