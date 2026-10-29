package sie.siejuridicos.documento;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AnclaDiariaIntegridadRepository extends JpaRepository<AnclaDiariaIntegridad, Long> {

    Optional<AnclaDiariaIntegridad> findByFecha(LocalDate fecha);

    // Anclas de días anteriores que la TSA externa no alcanzó a sellar a tiempo (ver
    // AnclaDiariaIntegridadScheduler#reintentarPendientes).
    List<AnclaDiariaIntegridad> findByTokenRfc3161IsNull();
}
