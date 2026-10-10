package sie.siejuridicos.tarea;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface TareaRepository extends JpaRepository<Tarea, Long> {

    List<Tarea> findByCasoIdOrderByCompletadaAscFechaVencimientoAsc(Long casoId);

    // "Mis tareas pendientes" (ver TareaService.listarPendientesDeUsuario): la base de la
    // página "Tareas" para un ABOGADO, que solo ve lo suyo.
    List<Tarea> findByUsuarioAsignadoIdAndCompletadaFalseOrderByFechaVencimientoAsc(Long usuarioId);

    // Vista de ADMIN_GENERAL en la página "Tareas": todas las pendientes de todos, sin
    // importar quién las creó o a quién se le asignaron.
    List<Tarea> findByCompletadaFalseOrderByFechaVencimientoAsc();

    // Cuántas tareas quedan sin completar en total y por caso puntual (ver
    // CrmService.construirClienteResponse, que necesita este conteo bulk para el directorio
    // sin disparar una consulta por cliente -- mismo criterio que
    // ClienteCobroRepository.findByActivoTrueAndClienteCrmIdIsNotNull).
    @Query("select t.caso.id, count(t) from Tarea t where t.completada = false group by t.caso.id")
    List<Object[]> contarPendientesPorCaso();
}
