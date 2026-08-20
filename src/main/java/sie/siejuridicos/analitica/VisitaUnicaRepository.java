package sie.siejuridicos.analitica;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VisitaUnicaRepository extends JpaRepository<VisitaUnica, Long> {

    // invoca fn_registrar_visita_unica (V20): idempotente, no duplica si el visitante ya
    // se contó este mes.
    @Query(value = "SELECT fn_registrar_visita_unica(:visitanteId)", nativeQuery = true)
    boolean registrarVisita(@Param("visitanteId") String visitanteId);

    // invoca fn_contar_visitantes_mes_actual (V20): usado por EstadisticasService para el
    // contador del panel administrativo.
    @Query(value = "SELECT fn_contar_visitantes_mes_actual()", nativeQuery = true)
    long contarVisitantesMesActual();
}
