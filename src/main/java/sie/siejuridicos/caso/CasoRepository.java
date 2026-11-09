package sie.siejuridicos.caso;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CasoRepository extends JpaRepository<Caso, Long> {

    boolean existsByRadicadoId(String radicadoId);

    Optional<Caso> findByRadicadoId(String radicadoId);

    // Búsqueda para radicados que en la hoja vienen con anotación entre paréntesis, ej "NUMERO (2025-01386)"
    @Query("SELECT c FROM Caso c WHERE c.radicadoId LIKE CONCAT(:codigo, ' (%') OR c.radicadoId LIKE CONCAT(:codigo, '(%')")
    List<Caso> buscarPorRadicadoPrefijo(@Param("codigo") String codigo);

    // Buscador total del panel (ver sie.siejuridicos.busqueda.BusquedaService): radicadoId y
    // numeroCaso son planos (no cifrados, a diferencia del nombre del cliente), así que un
    // LIKE/ILIKE directo en SQL funciona sin tener que cargar nada en memoria.
    List<Caso> findByRadicadoIdContainingIgnoreCaseOrNumeroCasoContainingIgnoreCase(String radicadoId, String numeroCaso);

    // Llave de la sincronización automática (ver CasoService.sincronizarDesdeHoja()): decide
    // si una fila de la hoja ya existe localmente o hay que crearla. Por fuente, no solo por
    // número: el mismo número puede repetirse entre hojas distintas sin ser el mismo caso
    // (ver migración V25).
    Optional<Caso> findByFuenteAndNumeroCaso(FuenteCaso fuente, String numeroCaso);

    // Reconciliación completa de la sincronización (ver CasoService.sincronizarDesdeHoja()):
    // todos los casos ya registrados de una fuente, para detectar cuáles ya no aparecen en la
    // lectura fresca de la hoja (la fila se borró) y eliminarlos del sistema automáticamente.
    List<Caso> findByFuente(FuenteCaso fuente);

    // Casos con radicado ya asignado pero que todavía les falta AL MENOS UNO de los dos
    // canales de notificación (correo o WhatsApp, ver CasoService.enviarCorreosPendientes())
    // -- el botón "Enviar correos pendientes" del panel. JOIN FETCH por el mismo motivo que
    // listarTodosConDetalle: evita N+1 al leer cliente.getCorreo()/getTelefono() de cada uno.
    @Query("SELECT c FROM Caso c JOIN FETCH c.cliente "
            + "WHERE c.radicadoId IS NOT NULL AND ("
            + "  (c.correoEnviado = false AND c.cliente.correo IS NOT NULL) "
            + "  OR (c.whatsappEnviado = false AND c.cliente.telefono IS NOT NULL)"
            + ")")
    List<Caso> listarPendientesDeNotificacion();

    // Reporte periódico de casos (ver CasoService.enviarReporteSemanal()): casos con radicado ya asignado
    // que TODAVÍA no recibieron el reporte del periodo en curso según cada canal:
    // - Correo: semanal (cada lunes, inicioSemana)
    // - WhatsApp: quincenal (días 1 y 15 de cada mes, inicioQuincena)
    // Con el cupo diario compartido de envíos masivos (ver LimiteEnvioMasivoService), un lote grande
    // puede no alcanzar a procesarse en un solo día; esta consulta es lo que permite que el día siguiente
    // recoja automáticamente solo a quienes quedaron pendientes, sin repetirle el reporte a quien ya lo recibió.
    // JOIN FETCH por el mismo motivo de siempre: evita N+1 al leer cliente.getCorreo()/getTelefono() de cada uno.
    // c.esPrivadoCifrado = false (2026-11-05, gap real de confidencialidad encontrado en
    // vivo): un caso privado -- de Gerencia o de la bóveda personal de un abogado -- NUNCA
    // debe recibir el reporte periódico automático, "son casos de extrema sensibilidad... no
    // enviara eso reportes ni diarios, ni mensuales ni nada". Antes esto SÍ se colaba.
    @Query("SELECT c FROM Caso c JOIN FETCH c.cliente WHERE c.radicadoId IS NOT NULL "
            + "AND c.esPrivadoCifrado = false "
            + "AND ("
            + "  (c.cliente.correo IS NOT NULL AND (c.fechaUltimoReporteSemanal IS NULL OR c.fechaUltimoReporteSemanal < :inicioSemana)) "
            + "  OR (c.cliente.telefono IS NOT NULL AND (c.fechaUltimoReporteWhatsapp IS NULL OR c.fechaUltimoReporteWhatsapp < :inicioQuincena))"
            + ")")
    List<Caso> listarPendientesReporte(
            @Param("inicioSemana") LocalDateTime inicioSemana,
            @Param("inicioQuincena") LocalDateTime inicioQuincena);

    default List<Caso> listarPendientesReporteSemanal(LocalDateTime inicioPeriodo) {
        return listarPendientesReporte(inicioPeriodo, inicioPeriodo);
    }

    // JOIN FETCH evita N+1 al listar (cliente y abogadoAsignado son LAZY): una sola consulta
    // en vez de una extra por cada caso listado en /admin/casos. LEFT porque
    // abogadoAsignado es opcional (ver migración V61).
    @Query("SELECT c FROM Caso c JOIN FETCH c.cliente LEFT JOIN FETCH c.abogadoAsignado ORDER BY c.fechaCreacion DESC")
    List<Caso> listarTodosConDetalle();

    // Casos estándar no confidenciales (visibles para el panel general de casos). finalizado =
    // false (2026-11-05): un caso finalizado se muestra en su propio apartado, nunca aquí.
    @Query("SELECT c FROM Caso c JOIN FETCH c.cliente LEFT JOIN FETCH c.abogadoAsignado "
            + "WHERE c.esPrivadoCifrado = false AND c.finalizado = false ORDER BY c.fechaCreacion DESC")
    List<Caso> listarPublicosConDetalle();

    // Bóveda de Casos Privados y Cifrados de GERENCIA exclusivamente (propietarioPrivado IS
    // NULL -- ver Caso.propietarioPrivado). La bóveda personal de cada abogado es una consulta
    // APARTE (ver listarPrivadosDeAbogadoConDetalle abajo): nunca se mezclan en la misma lista.
    @Query("SELECT c FROM Caso c JOIN FETCH c.cliente LEFT JOIN FETCH c.abogadoAsignado "
            + "WHERE c.esPrivadoCifrado = true AND c.propietarioPrivado IS NULL ORDER BY c.fechaCreacion DESC")
    List<Caso> listarPrivadosConDetalle();

    // Bóveda personal de UN abogado puntual (pedido explícito del usuario, 2026-11-05:
    // "implementa la misma funcionalidad que tenia gerencia de casos privados y cifrados,
    // implementala para todos los abogados... estos casos no los podra ver nadie, ni gerencia
    // ni nadie"). Nunca incluye la bóveda de Gerencia ni la de otro abogado.
    @Query("SELECT c FROM Caso c JOIN FETCH c.cliente LEFT JOIN FETCH c.abogadoAsignado "
            + "WHERE c.esPrivadoCifrado = true AND c.propietarioPrivado.id = :abogadoId ORDER BY c.fechaCreacion DESC")
    List<Caso> listarPrivadosDeAbogadoConDetalle(@Param("abogadoId") Long abogadoId);

    // Para el tope de 20 casos privados por abogado (ver CasoService.
    // MAXIMO_CASOS_PRIVADOS_POR_ABOGADO).
    long countByEsPrivadoCifradoTrueAndPropietarioPrivado_Id(Long abogadoId);

    // Casos finalizados (2026-11-05, nuevo apartado "Casos Finalizados" -- ver
    // CasoAdminController.listarFinalizados). LEFT JOIN FETCH de finalizadoPor para mostrar
    // quién lo cerró sin N+1.
    @Query("SELECT c FROM Caso c JOIN FETCH c.cliente LEFT JOIN FETCH c.abogadoAsignado "
            + "LEFT JOIN FETCH c.finalizadoPor WHERE c.finalizado = true ORDER BY c.fechaFinalizacion DESC")
    List<Caso> listarFinalizadosConDetalle();

    // Cuántos casos comparten este mismo Cliente (ver
    // CasoService.actualizarDatosCliente()): si un cliente tiene más de un caso, la columna
    // de "nombre" de cada fila de la hoja suele traer una etiqueta corta específica de ESE
    // caso ("DDTE:SANTOYO") en vez del nombre completo real -- sobrescribir el nombre
    // compartido del cliente con eso alterna sin parar entre sincronizaciones según qué fila
    // se procesó de último. Solo tiene sentido confiar en esa columna como corrección del
    // nombre cuando el cliente tiene un único caso (sin ambigüedad de cuál fila manda).
    long countByCliente(Cliente cliente);

    List<Caso> findByClienteClienteCrmId(Long clienteCrmId);

    // Universo de casos candidatos al seguimiento judicial 24/7 (ver
    // sie.siejuridicos.vigilancia.SeguimientoJudicialService): cualquier caso con radicado,
    // sin filtrar por fuente -- RamaJudicialCpnuClient.buscarIdProceso() ya valida por su
    // cuenta si el radicadoId tiene forma de radicado CPNU de 23 dígitos, así que un caso de
    // una fuente que no usa ese formato (ej. Procesos Comisaría/ICBF) simplemente no
    // encuentra nada ahí, sin que haga falta excluirlo aquí por fuente.
    // AndFinalizadoFalse (2026-11-05): "una vez finalizado el sistema ya no le hara
    // vigilancia" -- un caso finalizado nunca vuelve a entrar al ciclo de seguimiento judicial.
    List<Caso> findByRadicadoIdIsNotNullAndFinalizadoFalse();

    // Para DeteccionProcesosNuevosService (pedido explícito del usuario: "implementa todas
    // las mejoras identificadas") -- solo clienteId + radicadoId, SIN tocar nombre/correo/
    // teléfono (esos van cifrados con CampoCifradoConverter, ver Cliente -- más seguro no
    // arriesgar si ese converter aplica igual sobre una ruta de proyección que sobre la
    // entidad completa). c.cliente.id es seguro de leer en una proyección así: es una llave
    // numérica plana, nunca pasa por el converter. c.finalizado = false (2026-11-05): un caso
    // finalizado tampoco debe seguir detectando procesos nuevos relacionados.
    @Query("SELECT c.cliente.id AS clienteId, c.radicadoId AS radicadoId "
            + "FROM Caso c WHERE c.radicadoId IS NOT NULL AND c.cliente IS NOT NULL AND c.finalizado = false")
    List<RadicadoPorCliente> listarRadicadosPorCliente();

    interface RadicadoPorCliente {
        Long getClienteId();
        String getRadicadoId();
    }

    // Para EnvioDocumentoJudicialService: necesita leer cliente.getCorreo()/getNombre() sin
    // envolver todo el método en una transacción propia (ver el comentario ahí sobre por qué
    // no -- mismo bug de Postgres con Propagation.REQUIRES_NEW ya documentado en
    // CasoService.consultar()). JOIN FETCH trae el Cliente ya inicializado en esta misma
    // consulta, sin necesidad de ninguna sesión abierta después.
    @Query("SELECT c FROM Caso c JOIN FETCH c.cliente WHERE c.id = :id")
    Optional<Caso> buscarConClientePorId(@Param("id") Long id);

    // Para CasoService.actualizar() (edición manual desde el panel, ver migración V61):
    // necesita leer y modificar tanto el Cliente (correo/teléfono) como el abogado asignado
    // en la misma operación, sin dos consultas separadas.
    @Query("SELECT c FROM Caso c JOIN FETCH c.cliente LEFT JOIN FETCH c.abogadoAsignado WHERE c.id = :id")
    Optional<Caso> buscarConClienteYAbogadoPorId(@Param("id") Long id);

    // Las 4 consultas siguientes alimentan ReporteCalidadDatosCasosService (pedido explícito
    // del usuario, 2026-11-09: "reporte de casos con fallos, radicados no correctos o
    // invalidos, correos y numeros de telefono fallidos... o que simplemente no han tenido
    // reportes correctos en las ramas", lunes 8am a gerencia). c.esPrivadoCifrado = false en
    // las 4: mismo criterio de privacidad ya aplicado en el resto de reportes automáticos de
    // este sistema -- un caso de la bóveda privada de un abogado o de Gerencia nunca debe
    // aparecer en un reporte masivo, aunque tenga datos incompletos. c.finalizado = false en
    // las 4: un caso archivado ya no es una prioridad operativa pendiente de corregir.

    @Query("SELECT c FROM Caso c JOIN FETCH c.cliente LEFT JOIN FETCH c.abogadoAsignado "
            + "WHERE c.radicadoFormatoInvalido = true AND c.finalizado = false AND c.esPrivadoCifrado = false "
            + "ORDER BY c.fechaCreacion DESC")
    List<Caso> listarConRadicadoInvalidoParaReporte();

    @Query("SELECT c FROM Caso c JOIN FETCH c.cliente LEFT JOIN FETCH c.abogadoAsignado "
            + "WHERE c.cliente.correo IS NULL AND c.finalizado = false AND c.esPrivadoCifrado = false "
            + "ORDER BY c.fechaCreacion DESC")
    List<Caso> listarSinCorreoClienteParaReporte();

    @Query("SELECT c FROM Caso c JOIN FETCH c.cliente LEFT JOIN FETCH c.abogadoAsignado "
            + "WHERE c.cliente.telefono IS NULL AND c.finalizado = false AND c.esPrivadoCifrado = false "
            + "ORDER BY c.fechaCreacion DESC")
    List<Caso> listarSinTelefonoClienteParaReporte();

    // "No han tenido reportes correctos en las ramas": radicado con formato válido pero
    // NINGUNO de los dos motores (CPNU ni SAMAI, ver SeguimientoJudicialService) logró
    // identificar nunca el proceso real -- el caso queda ciego para toda la vigilancia
    // judicial 24/7 sin que nada lo señale hoy (se confirmó en vivo el 2026-11-09 un caso
    // real así, ver radicado de San Mateo, Boyacá: 13 dígitos de despacho resueltos por
    // Croma pero cero actuaciones, "Sin proceso CPNU identificado").
    @Query("SELECT c FROM Caso c JOIN FETCH c.cliente LEFT JOIN FETCH c.abogadoAsignado "
            + "WHERE c.radicadoId IS NOT NULL AND c.radicadoFormatoInvalido = false "
            + "AND c.idProcesoCpnu IS NULL AND c.idProcesoSamai IS NULL "
            + "AND c.finalizado = false AND c.esPrivadoCifrado = false "
            + "ORDER BY c.fechaCreacion DESC")
    List<Caso> listarSinProcesoIdentificadoParaReporte();

    // Para el popup de "casos nuevos identificados" (pedido explícito del usuario,
    // 2026-11-09, ver migración V88): casos creados por CasoService.sincronizarDesdeHoja()
    // todavía sin aceptar por un gerente o abogado.
    @Query("SELECT c FROM Caso c JOIN FETCH c.cliente LEFT JOIN FETCH c.abogadoAsignado "
            + "WHERE c.pendienteAprobacion = true ORDER BY c.fechaCreacion DESC")
    List<Caso> listarPendientesAprobacion();
}
