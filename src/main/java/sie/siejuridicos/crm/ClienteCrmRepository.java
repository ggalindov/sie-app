package sie.siejuridicos.crm;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClienteCrmRepository extends JpaRepository<ClienteCrm, Long> {

    Optional<ClienteCrm> findByTelefonoHash(String telefonoHash);

    Optional<ClienteCrm> findByCorreoHash(String correoHash);

    Optional<ClienteCrm> findByCedulaNitHash(String cedulaNitHash);

    List<ClienteCrm> findByEstadoOrderByNombreAsc(EstadoClienteCrm estado);

    List<ClienteCrm> findAllByOrderByFechaCreacionDesc();
}
