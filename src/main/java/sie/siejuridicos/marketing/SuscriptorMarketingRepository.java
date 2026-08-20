package sie.siejuridicos.marketing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SuscriptorMarketingRepository extends JpaRepository<SuscriptorMarketing, Long> {

    // ON CONFLICT DO NOTHING evita una condición de carrera si la misma persona envía
    // el formulario más de una vez (correo tiene restricción UNIQUE, ver V12). RETURNING
    // id permite saber si realmente se insertó una fila nueva (lista no vacía) o si ya
    // existía (lista vacía), para no reenviar el correo de bienvenida a quien ya está
    // suscrito. Debe invocarse dentro de un @Transactional sin readOnly (ver
    // SuscriptorMarketingService), igual que fn_crear_solicitud en SolicitudService.
    @Query(value = "INSERT INTO suscriptores_marketing (nombre, correo) VALUES (:nombre, :correo) " +
            "ON CONFLICT (correo) DO NOTHING RETURNING id", nativeQuery = true)
    List<Long> suscribirSiNoExiste(@Param("nombre") String nombre, @Param("correo") String correo);

    List<SuscriptorMarketing> findByActivoTrueOrderByFechaSuscripcionDesc();

    // usado por EstadisticasService para el resumen del panel administrativo
    long countByActivoTrue();
}
