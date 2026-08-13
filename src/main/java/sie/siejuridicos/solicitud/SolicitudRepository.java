package sie.siejuridicos.solicitud;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SolicitudRepository extends JpaRepository<Solicitud, Long>, JpaSpecificationExecutor<Solicitud> {

    // invoca fn_crear_solicitud (V6): valida duplicado exacto en 24h e inserta en una sola operación.
    // origen se pasa como texto (origen.name()) para no depender del mapeo de enums en queries nativas.
    @Query(value = "SELECT * FROM fn_crear_solicitud(:nombre, :correo, :telefono, :mensaje, :origen)", nativeQuery = true)
    Solicitud crearSolicitud(@Param("nombre") String nombre,
                              @Param("correo") String correo,
                              @Param("telefono") String telefono,
                              @Param("mensaje") String mensaje,
                              @Param("origen") String origen);

    // invoca fn_actualizar_estado_solicitud (V7): valida la transición de estado permitida.
    @Query(value = "SELECT * FROM fn_actualizar_estado_solicitud(:id, :nuevoEstado)", nativeQuery = true)
    Solicitud actualizarEstado(@Param("id") Long id, @Param("nuevoEstado") String nuevoEstado);

    // usado por el job diario de recordatorios (RecordatorioCitaScheduler)
    List<Solicitud> findByFechaCitaBetweenAndRecordatorioEnviadoFalse(LocalDateTime desde, LocalDateTime hasta);
}
