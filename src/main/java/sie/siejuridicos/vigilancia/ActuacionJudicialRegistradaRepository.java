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
    // c.finalizado = false (2026-11-05): "una vez finalizado el sistema ya no le hara
    // vigilancia" -- un caso finalizado nunca vuelve a entrar al ciclo de boletines, sin
    // importar si su última actuación sigue técnicamente "sin boletín resuelto".
    @Query("SELECT a FROM ActuacionJudicialRegistrada a JOIN FETCH a.caso c "
            + "WHERE (a.fechaRegistro IS NOT NULL OR a.fechaActuacion IS NOT NULL) "
            + "AND c.finalizado = false "
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
            + "WHERE a.fechaRegistro IS NOT NULL AND a.fechaRegistro >= :desde AND c.finalizado = false")
    List<ActuacionJudicialRegistrada> conFechaRegistroDesde(
            @org.springframework.data.repository.query.Param("desde") LocalDateTime desde);

    // Actuaciones pendientes de notificar a GERENCIA en el reporte diario matutino. JOIN FETCH
    // de caso, abogado asignado y cliente para tener toda la ficha lista sin N+1.
    //
    // c.esPrivadoCifrado = false (2026-11-06, mismo hueco de confidencialidad que ya se había
    // cerrado en BoletinJudicialCacheRepository.listarPendientesGerente() el 2026-11-05, pero
    // que nunca se replicó aquí para los MOVIMIENTOS mismos, no solo sus documentos adjuntos):
    // un caso de la bóveda privada de un abogado (Caso.propietarioPrivado = ese abogado) nunca
    // debe aparecer en el reporte de Gerencia, aunque esta consulta no sepa de quién es la
    // bóveda -- cualquier caso marcado esPrivadoCifrado=true (de Gerencia o de un abogado) queda
    // fuera de todo digest automático, igual criterio que el resto de reportes de vigilancia.
    @Query("SELECT a FROM ActuacionJudicialRegistrada a "
            + "JOIN FETCH a.caso c "
            + "LEFT JOIN FETCH c.abogadoAsignado ab "
            + "LEFT JOIN FETCH c.cliente cl "
            + "WHERE a.notificadaStaff = false AND c.esPrivadoCifrado = false "
            + "ORDER BY COALESCE(a.fechaRegistro, a.fechaActuacion, a.fechaDetectada) DESC")
    List<ActuacionJudicialRegistrada> listarPendientesNotificarStaff();

    // Actuaciones pendientes de notificar para un abogado asignado específico. Bandera propia
    // (notificadaAbogado, ver migración V83) -- bug real encontrado el 2026-11-05: esto antes
    // filtraba por a.notificadaStaff, la MISMA bandera que marca enviarDigestGerente() en TODAS
    // las actuaciones del sistema; como gerencia se envía primero, para cuando corría el digest
    // de abogados siempre encontraba la lista vacía. Nunca llegaban movimientos reales a ningún
    // abogado.
    //
    // c.esPrivadoCifrado = false (2026-11-06, mismo motivo que arriba): un caso puede quedar
    // marcado privado/cifrado SIN que se le quite el abogadoAsignado que ya tenía (la bóveda
    // privada de Gerencia es un caso que YA estaba asignado a un abogado y luego Gerencia lo
    // mueve a su bóveda exclusiva) -- sin este filtro, ese abogado seguiría recibiendo cada
    // mañana el reporte de movimientos de un caso que ya no debería poder ver.
    @Query("SELECT a FROM ActuacionJudicialRegistrada a "
            + "JOIN FETCH a.caso c "
            + "LEFT JOIN FETCH c.abogadoAsignado ab "
            + "LEFT JOIN FETCH c.cliente cl "
            + "WHERE a.notificadaAbogado = false AND c.abogadoAsignado.id = :abogadoId AND c.esPrivadoCifrado = false "
            + "ORDER BY COALESCE(a.fechaRegistro, a.fechaActuacion, a.fechaDetectada) DESC")
    List<ActuacionJudicialRegistrada> listarPendientesNotificarPorAbogado(
            @org.springframework.data.repository.query.Param("abogadoId") Long abogadoId);

    // Búsqueda con caso cargado para pruebas de reporte
    @Query("SELECT a FROM ActuacionJudicialRegistrada a "
            + "JOIN FETCH a.caso c "
            + "LEFT JOIN FETCH c.abogadoAsignado ab "
            + "LEFT JOIN FETCH c.cliente cl "
            + "ORDER BY a.id DESC")
    List<ActuacionJudicialRegistrada> listarRecientesConCaso(org.springframework.data.domain.Pageable pageable);

    // Para VigilanciaAdminController.controlDiario() (pedido explícito del usuario, 2026-11-04:
    // réplica del "Control diario procesal" de LUPA) -- todas las actuaciones con fecha de
    // registro dentro del rango, caso + abogado ya cargados para poder filtrar y mostrar sin
    // N+1. Usa fechaRegistro (cuándo el despacho publicó la actuación), el mismo criterio ya
    // establecido en ultimaFechaMovimientoPorCaso/conFechaRegistroDesde de esta misma clase
    // para "cuándo pasó esto realmente".
    @Query("SELECT a FROM ActuacionJudicialRegistrada a "
            + "JOIN FETCH a.caso c "
            + "LEFT JOIN FETCH c.abogadoAsignado ab "
            + "WHERE a.fechaRegistro IS NOT NULL AND a.fechaRegistro >= :desde AND a.fechaRegistro < :hasta "
            + "ORDER BY a.fechaRegistro DESC")
    List<ActuacionJudicialRegistrada> findByFechaRegistroEntre(
            @org.springframework.data.repository.query.Param("desde") LocalDateTime desde,
            @org.springframework.data.repository.query.Param("hasta") LocalDateTime hasta);

    // Para VigilanciaAdminController.audienciasCalendario() (pedido explícito del usuario,
    // 2026-11-06: "el sistema debe detectar tambien si programa el estado alguna
    // audiencia... se tiene que ver reflejada de manera automatica"). Pre-filtro grueso en
    // SQL (acota a lo que PODRÍA mencionar una audiencia, barato de escanear) -- la
    // clasificación precisa y la extracción de fecha/hora (ver ExtractorFechaAudiencia)
    // corren después en Java sobre este conjunto ya reducido, nunca sobre la tabla completa.
    // Sin filtro de fecha de registro a propósito: la FECHA DE LA AUDIENCIA (extraída del
    // texto) puede caer mucho después de cuándo el despacho publicó el auto que la fija.
    @Query("SELECT a FROM ActuacionJudicialRegistrada a "
            + "JOIN FETCH a.caso c "
            + "LEFT JOIN FETCH c.abogadoAsignado ab "
            + "WHERE LOWER(a.nombreActuacion) LIKE '%audiencia%' OR LOWER(a.anotacion) LIKE '%audiencia%'")
    List<ActuacionJudicialRegistrada> buscarConMencionDeAudiencia();

    // GAP REAL confirmado en producción (2026-11-06, pedido explícito del usuario: "necesito
    // que el sistema traiga absolutamente todos los documentos, autos, no esta trayendo nada
    // de momento"): BoletinJudicialCacheService.construirObjetivos() solo intentaba resolver
    // documento para la ÚLTIMA actuación de cada caso o las registradas en los últimos
    // DIAS_VENTANA_BUSQUEDA días -- medido en vivo contra la base real: de 151 actuaciones
    // marcadas conDocumentos=true por la propia Rama Judicial, solo 24 (16%) tenían un boletín
    // resuelto, y el correo de solicitud al despacho (ver CorreoDespachoJudicialService) nunca
    // se había disparado NI UNA SOLA VEZ en toda la historia.
    //
    // SIN ventana de fecha A PROPÓSITO, incluso tras el ajuste del 2026-11-09 ("el sistema esta
    // trayendo boletines del 2025 y fechas anteriores... de ahora en adelante el sistema solo
    // busca los boletines de los ultimos dos meses maximo" + "deja funcional y activo el pedir
    // a despacho el auto reportado pero no subido"): esas dos peticiones NO se contradicen.
    // La ventana de 2 meses aplica al canal HEURÍSTICO de Publicaciones Procesales (el que
    // escanea boletines completos por fecha y es el que atribuía documentos viejos/equivocados
    // -- ver el chequeo de ventana en BoletinJudicialCacheService.procesarActuacion()). Esta
    // consulta, en cambio, alimenta el canal EXACTO de la CPNU (nunca trae el documento
    // equivocado, es por id de actuación) y, si tampoco hay nada ahí, el correo directo al
    // despacho -- el camino seguro para resolver justo las actuaciones viejas que el heurístico
    // ya no debe tocar. Quitarle la ventana aquí dejaría sin solución posible, para siempre,
    // cualquier actuación con documento reportado de hace más de 2 meses.
    // c.finalizado = false: un caso ya archivado no necesita más documentos nuevos.
    @Query("SELECT a FROM ActuacionJudicialRegistrada a "
            + "JOIN FETCH a.caso c "
            + "WHERE a.conDocumentos = true AND c.finalizado = false "
            + "AND a.correoDespachoSolicitadoEn IS NULL "
            + "AND NOT EXISTS (SELECT 1 FROM BoletinJudicialCache b WHERE b.actuacion = a) "
            + "ORDER BY a.fechaRegistro DESC")
    List<ActuacionJudicialRegistrada> conDocumentosSinResolverHistorico();
}
