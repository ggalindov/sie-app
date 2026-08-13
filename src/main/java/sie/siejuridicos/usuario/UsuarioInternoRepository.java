package sie.siejuridicos.usuario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioInternoRepository extends JpaRepository<UsuarioInterno, Long> {

    Optional<UsuarioInterno> findByCorreo(String correo);

    boolean existsByCorreo(String correo);
}
