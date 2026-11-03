package sie.siejuridicos.vigilancia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface BoletinPendienteRepository extends JpaRepository<BoletinPendiente, Long> {

    // Caso y actuacion ya cargados (el ciclo corre sin sesion abierta, por la pausa real entre
    // cada consulta): sin JOIN FETCH, leer el radicado lanzaria LazyInitializationException.
    @Query("SELECT p FROM BoletinPendiente p JOIN FETCH p.caso LEFT JOIN FETCH p.actuacion "
            + "WHERE p.agotado = false ORDER BY p.tipo DESC, p.fechaCreacion ASC")
    List<BoletinPendiente> listarActivos();

    long countByAgotadoFalse();

    Optional<BoletinPendiente> findByTipoAndCaso_IdAndUrlOrigen(BoletinPendiente.Tipo tipo, Long casoId, String urlOrigen);

    Optional<BoletinPendiente> findByTipoAndActuacion_Id(BoletinPendiente.Tipo tipo, Long actuacionId);

    @Modifying
    @Transactional
    @Query("DELETE FROM BoletinPendiente p WHERE p.tipo = sie.siejuridicos.vigilancia.BoletinPendiente.Tipo.DESCARGA "
            + "AND p.caso.id = :casoId AND p.urlOrigen = :url")
    int borrarDescarga(@Param("casoId") Long casoId, @Param("url") String url);
}
