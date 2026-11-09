package sie.siejuridicos.caso;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CasoCambioDetectadoRepository extends JpaRepository<CasoCambioDetectado, Long> {

    // Para el popup de /admin (ver CasoAdminController.listarCambiosPendientes()): JOIN FETCH
    // de caso + cliente para mostrar a qué caso/cliente pertenece cada cambio sin N+1.
    @Query("SELECT c FROM CasoCambioDetectado c JOIN FETCH c.caso ca LEFT JOIN FETCH ca.cliente "
            + "WHERE c.revisado = false ORDER BY c.fechaDeteccion DESC")
    List<CasoCambioDetectado> listarPendientes();

    List<CasoCambioDetectado> findAllByIdIn(List<Long> ids);
}
