package sie.siejuridicos.vigilancia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SeguimientoVistoUsuarioRepository extends JpaRepository<SeguimientoVistoUsuario, Long> {

    Optional<SeguimientoVistoUsuario> findByCaso_IdAndUsuario_Id(Long casoId, Long usuarioId);

    // Una sola consulta para TODOS los casos de este usuario en vez de una por caso -- lo
    // necesita CasoService.listarTodos() para calcular el badge de "sin revisar" de la
    // lista completa de casos sin incurrir en N+1 consultas.
    @Query("SELECT v.caso.id AS casoId, v.fechaVista AS fechaVista FROM SeguimientoVistoUsuario v "
            + "WHERE v.usuario.id = :usuarioId")
    List<VistoPorCaso> listarPorUsuario(@Param("usuarioId") Long usuarioId);

    interface VistoPorCaso {
        Long getCasoId();
        LocalDateTime getFechaVista();
    }
}
