package sie.siejuridicos.tarea;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
    @Query("select t.caso.id, count(t) from Tarea t where t.completada = false and t.caso is not null group by t.caso.id")
    List<Object[]> contarPendientesPorCaso();

    // Registro completo de tareas de UNA persona del CRM (pedido explícito del usuario: "ahi
    // es donde se asginara las tareas por cada persona"): junta las ligadas directamente a su
    // ficha (clienteCrm) con las de cualquiera de sus casos judiciales (ver migración V46 --
    // antes, V45, una tarea solo podía existir si el cliente ya tenía un caso, y la mayoría del
    // directorio todavía no tiene ninguno).
    @Query("select t from Tarea t where t.clienteCrm.id = :clienteCrmId "
            + "or (t.caso is not null and t.caso.cliente.clienteCrmId = :clienteCrmId) "
            + "order by t.completada asc, t.fechaVencimiento asc")
    List<Tarea> buscarTodasDeLaPersona(@Param("clienteCrmId") Long clienteCrmId);
}
