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

    // Todas las anclas que todavía no fueron marcadas comprometidas, para la reverificación
    // diaria (ver AnclaDiariaIntegridadScheduler.reverificarAnclasAnteriores) -- una vez
    // comprometida, una ancla nunca vuelve a "integra" solo por dejar de revisarla, así que
    // no hace falta volver a comparar una que ya se marcó.
    List<AnclaDiariaIntegridad> findByFechaBeforeAndIntegraTrue(LocalDate fecha);
}
