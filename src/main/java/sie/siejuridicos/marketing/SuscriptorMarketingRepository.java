package sie.siejuridicos.marketing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SuscriptorMarketingRepository extends JpaRepository<SuscriptorMarketing, Long> {

    // ON CONFLICT DO NOTHING evita una condición de carrera si la misma persona envía
    // el formulario más de una vez (correo tiene restricción UNIQUE, ver V12).
    @Modifying
    @Query(value = "INSERT INTO suscriptores_marketing (nombre, correo) VALUES (:nombre, :correo) " +
            "ON CONFLICT (correo) DO NOTHING", nativeQuery = true)
    void suscribirSiNoExiste(@Param("nombre") String nombre, @Param("correo") String correo);

    List<SuscriptorMarketing> findByActivoTrueOrderByFechaSuscripcionDesc();
}
