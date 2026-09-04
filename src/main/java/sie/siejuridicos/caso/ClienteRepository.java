package sie.siejuridicos.caso;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    // Por el índice ciego (HMAC), no por el correo cifrado en sí (ver Cliente.correoHash):
    // "correo" ya no es comparable por igualdad directamente en SQL una vez cifrado con IV
    // aleatorio.
    Optional<Cliente> findByCorreoHash(String correoHash);
}
