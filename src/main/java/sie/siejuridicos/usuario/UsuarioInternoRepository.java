package sie.siejuridicos.usuario;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioInternoRepository extends JpaRepository<UsuarioInterno, Long> {

    Optional<UsuarioInterno> findByCorreo(String correo);

    boolean existsByCorreo(String correo);

    // usado por EstadisticasService: tamaño y composición del equipo interno
    long countByActivoTrue();

    long countByRolAndActivoTrue(RolUsuario rol);

    // usado por SolicitudService: a quién se le puede asignar la responsabilidad de una
    // reunión desde el calendario (selector de responsables, ver
    // AgendarCitaRequest.responsablesIds) -- cualquier usuario interno activo, no solo rol
    // ABOGADO, porque el propio ADMIN_GENERAL también puede quedar como responsable de una
    // reunión.
    List<UsuarioInterno> findByActivoTrueOrderByNombreAsc();
}
