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
            + "WHERE c.radicadoId IS NOT NULL AND (c.correoEnviado = false OR c.whatsappEnviado = false)")
    List<Caso> listarPendientesDeNotificacion();

    // Reporte periódico de casos (ver CasoService.enviarReporteSemanal()): casos con radicado ya asignado
    // que TODAVÍA no recibieron el reporte del periodo en curso según cada canal:
    // - Correo: semanal (cada lunes, inicioSemana)
    // - WhatsApp: quincenal (días 1 y 15 de cada mes, inicioQuincena)
    // Con el cupo diario compartido de envíos masivos (ver LimiteEnvioMasivoService), un lote grande
    // puede no alcanzar a procesarse en un solo día; esta consulta es lo que permite que el día siguiente
    // recoja automáticamente solo a quienes quedaron pendientes, sin repetirle el reporte a quien ya lo recibió.
    // JOIN FETCH por el mismo motivo de siempre: evita N+1 al leer cliente.getCorreo()/getTelefono() de cada uno.
    @Query("SELECT c FROM Caso c JOIN FETCH c.cliente WHERE c.radicadoId IS NOT NULL "
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

    // JOIN FETCH evita N+1 al listar (cliente es LAZY): una sola consulta en vez de una
    // extra por cada caso listado en /admin/casos.
    @Query("SELECT c FROM Caso c JOIN FETCH c.cliente ORDER BY c.fechaCreacion DESC")
    List<Caso> listarTodosConDetalle();

    // Cuántos casos comparten este mismo Cliente (ver
    // CasoService.actualizarDatosCliente()): si un cliente tiene más de un caso, la columna
    // de "nombre" de cada fila de la hoja suele traer una etiqueta corta específica de ESE
    // caso ("DDTE:SANTOYO") en vez del nombre completo real -- sobrescribir el nombre
    // compartido del cliente con eso alterna sin parar entre sincronizaciones según qué fila
    // se procesó de último. Solo tiene sentido confiar en esa columna como corrección del
    // nombre cuando el cliente tiene un único caso (sin ambigüedad de cuál fila manda).
    long countByCliente(Cliente cliente);
}
