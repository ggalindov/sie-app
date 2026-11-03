package sie.siejuridicos.vigilancia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

public interface PortalSaludRepository extends JpaRepository<PortalSalud, Long> {

    // Resumen por servicio y hora del dia (hora de Bogota): cuantas mediciones, cuantas
    // fallaron y la latencia media de las exitosas. Es la base para elegir la hora del ciclo.
    @Query(value = "SELECT servicio AS servicio, "
            + "CAST(EXTRACT(HOUR FROM (fecha AT TIME ZONE 'America/Bogota')) AS integer) AS hora, "
            + "COUNT(*) AS total, "
            + "COUNT(*) FILTER (WHERE NOT ok) AS fallos, "
            + "CAST(ROUND(AVG(latencia_ms) FILTER (WHERE ok)) AS integer) AS latenciaMediaMs "
            + "FROM portal_salud WHERE fecha > now() - (:horas * interval '1 hour') "
            + "GROUP BY 1, 2 ORDER BY 1, 2", nativeQuery = true)
    List<ResumenHora> resumenPorHora(@Param("horas") int horas);

    interface ResumenHora {
        String getServicio();
        Integer getHora();
        Long getTotal();
        Long getFallos();
        Integer getLatenciaMediaMs();
    }

    @Modifying
    @Transactional
    @Query("DELETE FROM PortalSalud p WHERE p.fecha < :antes")
    int borrarAnteriores(@Param("antes") OffsetDateTime antes);
}
