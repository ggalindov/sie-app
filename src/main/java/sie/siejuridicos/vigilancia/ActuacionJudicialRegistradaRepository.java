package sie.siejuridicos.vigilancia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import sie.siejuridicos.caso.Caso;

import java.time.LocalDateTime;
import java.util.List;

public interface ActuacionJudicialRegistradaRepository extends JpaRepository<ActuacionJudicialRegistrada, Long> {

    // Trae las entidades COMPLETAS (no solo sus ids) -- SeguimientoJudicialService.
    // sincronizarCaso() las usa tanto para saber cuáles actuaciones ya tenía como para
    // completar en las existentes los campos que se empezaron a capturar después (ver
    // completarCamposCapturadosDespues() y la migración V58).
    List<ActuacionJudicialRegistrada> findByCasoOrderByFechaRegistroDesc(Caso caso);

    long countByCaso(Caso caso);

    // Para el badge de "actualización sin revisar" (pedido explícito del usuario) sobre el
    // listado COMPLETO de casos: una sola consulta agregada en vez de una por caso.
    // fechaDetectada (cuándo NUESTRO sistema vio la actuación por primera vez) es la que
    // importa aquí, no fechaRegistro (cuándo el despacho la publicó) -- es el dato real de
    // "hubo una novedad de seguimiento", igual que ya usa SeguimientoJudicialService.
    @Query("SELECT a.caso.id AS casoId, MAX(a.fechaDetectada) AS ultimaFechaDetectada "
            + "FROM ActuacionJudicialRegistrada a GROUP BY a.caso.id")
    List<UltimaDeteccionPorCaso> ultimaFechaDetectadaPorCaso();

    interface UltimaDeteccionPorCaso {
        Long getCasoId();
        LocalDateTime getUltimaFechaDetectada();
    }

    // Corrección real pedida por el usuario: "un caso que tuvo movimiento hoy tiene que
    // salir arriba" -- el ordenador de /admin/casos por "fecha de último movimiento" venía
    // usando ultimaFechaDetectadaPorCaso() de arriba, pero ESA fecha es cuándo NUESTRO
    // sistema vio la actuación por primera vez, no cuándo ocurrió realmente. El historial de
    // los 362 casos reales se cargó con un backfill masivo en esta misma sesión (varios
    // cientos de actuaciones comparten el mismo minuto de fechaDetectada, el instante de esa
    // corrida), así que ordenar por fechaDetectada deja prácticamente empatados a un caso con
    // movimiento real de hoy y a uno con movimiento real de hace meses: ambos fueron
    // "detectados" en el mismo backfill. fechaRegistro (cuándo el despacho publicó la
    // actuación, con respaldo en fechaActuacion para las filas más viejas que no la tengan)
    // es el dato real de "cuándo pasó esto", y es el único criterio correcto para esta
    // pregunta. GREATEST ignora el lado nulo en vez de devolver null (a diferencia de un
    // operador de comparación normal), que es justo lo que hace falta aquí.
    @Query(value = "SELECT id_caso AS casoId, "
            + "MAX(GREATEST(COALESCE(fecha_registro, fecha_actuacion), COALESCE(fecha_actuacion, fecha_registro))) "
            + "AS ultimaFechaMovimiento "
            + "FROM actuaciones_judiciales WHERE fecha_registro IS NOT NULL OR fecha_actuacion IS NOT NULL "
            + "GROUP BY id_caso", nativeQuery = true)
    List<UltimoMovimientoPorCaso> ultimaFechaMovimientoPorCaso();

    interface UltimoMovimientoPorCaso {
        Long getCasoId();
        // LocalDateTime (no LocalDate) desde la migración V65 -- ahora trae la hora exacta
        // real del movimiento, no solo el día (pedido explícito del usuario).
        LocalDateTime getUltimaFechaMovimiento();
    }

    // Para BoletinJudicialCacheService.sincronizarNuevosBoletines() -- pedido explícito del
    // usuario: "busca todos los boletines por cada caso", no solo los de actuaciones
    // detectadas en una ventana reciente. Antes esto filtraba por fechaDetectada (cuándo
    // NUESTRO sistema vio la actuación), el mismo defecto real que ya se corrigió para el
    // ordenador de /admin/casos (ver ultimaFechaMovimientoPorCaso): el backfill masivo de
    // esta sesión deja la mayoría de las actuaciones con fechaDetectada vieja, así que
    // filtrar por esa fecha se saltaba casos con movimiento real reciente. Esta consulta trae
    // UNA sola actuación por caso -- la de fecha real (fechaRegistro, con respaldo en
    // fechaActuacion) más reciente, sin ninguna ventana de tiempo -- cubriendo TODOS los
    // casos con al menos una actuación con fecha. El límite real de cuánto se puede encontrar
    // sigue siendo del portal (solo expone los boletines más recientes por despacho, ver
    // PublicacionesProcesalesClient), no de esta consulta. JOIN FETCH del caso es
    // obligatorio, no cosmético: sincronizarNuevosBoletines() corre SIN @Transactional (a
    // propósito, por la pausa real entre cada consulta), así que un caso traído perezoso
    // lanzaría LazyInitializationException apenas se le pidiera el radicado -- el mismo bug
    // real que ya costó tiempo en TerminoProcesalService esta misma sesión.
    @Query("SELECT a FROM ActuacionJudicialRegistrada a JOIN FETCH a.caso c "
            + "WHERE (a.fechaRegistro IS NOT NULL OR a.fechaActuacion IS NOT NULL) "
            + "AND COALESCE(a.fechaRegistro, a.fechaActuacion) = ("
            + "  SELECT MAX(COALESCE(a2.fechaRegistro, a2.fechaActuacion)) FROM ActuacionJudicialRegistrada a2 "
            + "  WHERE a2.caso = a.caso AND (a2.fechaRegistro IS NOT NULL OR a2.fechaActuacion IS NOT NULL))")
    List<ActuacionJudicialRegistrada> ultimaActuacionConFechaPorCaso();

    // Todas las actuaciones con fecha de registro desde "desde", con el caso ya cargado (mismo
    // motivo que arriba). Hallazgo real del 2026-11-01: el ciclo solo buscaba el boletin de la
    // fecha de la ULTIMA actuacion de cada caso, pero el estado que contiene el boletin suele
    // ser de otra fecha reciente (caso 176: ultima actuacion 30-oct, "Fijacion estado" 23-oct)
    // -- el flujo manual, que deja elegir cualquier evento, si lo encontraba.
    @Query("SELECT a FROM ActuacionJudicialRegistrada a JOIN FETCH a.caso c "
            + "WHERE a.fechaRegistro IS NOT NULL AND a.fechaRegistro >= :desde")
    List<ActuacionJudicialRegistrada> conFechaRegistroDesde(
            @org.springframework.data.repository.query.Param("desde") LocalDateTime desde);

    // Actuaciones pendientes de notificar a staff (gerencia y abogados) en el reporte diario matutino.
    // JOIN FETCH de caso, abogado asignado y cliente para tener toda la ficha lista sin N+1.
    @Query("SELECT a FROM ActuacionJudicialRegistrada a "
            + "JOIN FETCH a.caso c "
            + "LEFT JOIN FETCH c.abogadoAsignado ab "
            + "LEFT JOIN FETCH c.cliente cl "
            + "WHERE a.notificadaStaff = false "
            + "ORDER BY COALESCE(a.fechaRegistro, a.fechaActuacion, a.fechaDetectada) DESC")
    List<ActuacionJudicialRegistrada> listarPendientesNotificarStaff();

    // Actuaciones pendientes de notificar para un abogado asignado específico
    @Query("SELECT a FROM ActuacionJudicialRegistrada a "
            + "JOIN FETCH a.caso c "
            + "LEFT JOIN FETCH c.abogadoAsignado ab "
            + "LEFT JOIN FETCH c.cliente cl "
            + "WHERE a.notificadaStaff = false AND c.abogadoAsignado.id = :abogadoId "
            + "ORDER BY COALESCE(a.fechaRegistro, a.fechaActuacion, a.fechaDetectada) DESC")
    List<ActuacionJudicialRegistrada> listarPendientesNotificarPorAbogado(
            @org.springframework.data.repository.query.Param("abogadoId") Long abogadoId);
}
