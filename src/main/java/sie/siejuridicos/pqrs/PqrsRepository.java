package sie.siejuridicos.pqrs;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PqrsRepository extends JpaRepository<Pqrs, Long> {

    boolean existsByRadicado(String radicado);

    Optional<Pqrs> findByRadicado(String radicado);

    List<Pqrs> findAllByOrderByFechaCreacionDesc();
}
