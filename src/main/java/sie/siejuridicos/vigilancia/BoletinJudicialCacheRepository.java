package sie.siejuridicos.vigilancia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BoletinJudicialCacheRepository extends JpaRepository<BoletinJudicialCache, Long> {

    // Idempotencia: nunca se vuelve a descargar/cachear el mismo boletín para el mismo caso
    // (ver migración V63, UNIQUE(id_caso, url_origen)).
    boolean existsByCaso_IdAndUrlOrigen(Long casoId, String urlOrigen);

    // BoletinJudicialCachePurgaScheduler: todo lo que ya venció, sin excepción (pedido
    // explícito del usuario: "se guarde por una semana... y luego se borren").
    List<BoletinJudicialCache> findByFechaExpiracionBefore(LocalDateTime ahora);

    // Digest diario al gerente/abogados: todos los boletines cacheados que todavía no se
    // incluyeron en ningún digest, con el caso y el abogado asignado ya cargados (evita N+1
    // al componer el correo con decenas de casos).
    @Query("SELECT b FROM BoletinJudicialCache b JOIN FETCH b.caso c LEFT JOIN FETCH c.abogadoAsignado "
            + "WHERE b.enviadoGerente = false ORDER BY b.fechaDescarga ASC")
    List<BoletinJudicialCache> listarPendientesGerente();

    // Digest diario por abogado: solo los boletines de casos que él tiene asignados, nunca
    // enviados antes (a él, el gerente tiene su propia bandera independiente arriba).
    @Query("SELECT b FROM BoletinJudicialCache b JOIN FETCH b.caso c "
            + "WHERE b.enviadoAbogado = false AND c.abogadoAsignado.id = :abogadoId ORDER BY b.fechaDescarga ASC")
    List<BoletinJudicialCache> listarPendientesPorAbogado(@Param("abogadoId") Long abogadoId);

    // Qué abogados tienen al menos un boletín pendiente -- evita recorrer a TODOS los
    // usuarios internos cada mañana para preguntarle a cada uno "¿tienes algo pendiente?".
    @Query("SELECT DISTINCT c.abogadoAsignado.id FROM BoletinJudicialCache b JOIN b.caso c "
            + "WHERE b.enviadoAbogado = false AND c.abogadoAsignado IS NOT NULL")
    List<Long> listarIdsAbogadosConPendientes();

    // Digest semanal al cliente: solo los del caso puntual, nunca enviados antes a ESE
    // cliente (un boletín SÍ puede ya estar incluido en el digest del gerente y seguir
    // pendiente para el cliente -- son destinatarios independientes), y SOLO los que
    // coinciden por nombre con la actuación esperada (ver migración V64 -- nunca se le
    // manda al cliente un documento ambiguo sin que un humano lo confirme primero).
    List<BoletinJudicialCache> findByCaso_IdAndEnviadoClienteFalseAndCoincideNombreActuacionTrue(Long casoId);

    // Para las pruebas de digest desde el panel (ver BoletinJudicialDigestService.
    // probarDigestInterno/probarDigestCliente) -- TODOS los boletines cacheados de UN caso
    // puntual, sin filtrar por "ya enviado" (una prueba repetible no debe depender de que el
    // boletín nunca se haya mandado antes).
    List<BoletinJudicialCache> findByCaso_Id(Long casoId);

    // Pre-carga por lote para enriquecer términos en el calendario sin N+1
    List<BoletinJudicialCache> findByCaso_IdIn(List<Long> casoIds);

    // Para saber qué casos tienen algo pendiente para el cliente esta semana, sin recorrer
    // los 362 casos uno por uno preguntando.
    @Query("SELECT DISTINCT b.caso.id FROM BoletinJudicialCache b "
            + "WHERE b.enviadoCliente = false AND b.coincideNombreActuacion = true")
    List<Long> listarIdsCasosConPendientesCliente();

    // Pedido explícito del usuario: un indicador (punto azul) en /admin/casos junto al de
    // "Seguimiento" para ver de un vistazo qué casos ya tienen un boletín descargable AHORA
    // MISMO, más un "contador de días para que se borre" -- ambos salen de este mismo dato:
    // la expiración MÁS PRÓXIMA de un caso (si tiene varios boletines cacheados con distinta
    // fecha, la que importa mostrar es la que se va a borrar primero). Presencia en el mapa
    // resultante = "disponible para descargar" (cualquier fila en caché cuenta, sin importar
    // si ya se envió o no a nadie: eso es independiente), y el valor en sí alimenta el
    // contador de días en CasoService.listarTodos().
    @Query("SELECT b.caso.id AS casoId, MIN(b.fechaExpiracion) AS proximaExpiracion "
            + "FROM BoletinJudicialCache b GROUP BY b.caso.id")
    List<ProximaExpiracionPorCaso> proximaExpiracionPorCaso();

    interface ProximaExpiracionPorCaso {
        Long getCasoId();
        LocalDateTime getProximaExpiracion();
    }

    // Pedido explícito del usuario: "el sistema debe de traer y darle la opcion de todos los
    // casos de ver/descargar todos los boletines que esten disponibles" -- TODOS los
    // boletines cacheados de TODOS los casos en una sola lista, para /admin/boletines (nueva
    // pantalla). JOIN FETCH de caso y cliente a propósito: esta lista se recorre completa
    // para mostrar nombre de cliente + radicado de cada fila, sin esto sería un N+1 por cada
    // boletín.
    @Query("SELECT b FROM BoletinJudicialCache b JOIN FETCH b.caso c LEFT JOIN FETCH c.cliente "
            + "ORDER BY b.fechaDescarga DESC")
    List<BoletinJudicialCache> listarTodosConCaso();

    @Query("SELECT b FROM BoletinJudicialCache b JOIN FETCH b.caso c LEFT JOIN FETCH c.cliente "
            + "WHERE c.id = :casoId ORDER BY b.fechaDescarga DESC")
    List<BoletinJudicialCache> listarConCasoPorCasoId(@Param("casoId") Long casoId);

    // Link público de descarga por correo (BoletinPublicoController) -- búsqueda por token
    // opaco, nunca por id autoincremental (evita enumeración de documentos de otros casos).
    Optional<BoletinJudicialCache> findByTokenPublico(String tokenPublico);

    // Para las pruebas de visualización del reporte diario (ver BoletinJudicialDigestService.
    // probarReporteDiario/probarReporteDiarioAbogado) -- los boletines YA ligados a un grupo
    // concreto de actuaciones (las del día que se está demostrando), para que la columna
    // "Documento" de la tabla principal tenga con qué emparejar de verdad, en vez de una
    // muestra de boletines sin relación con las actuaciones mostradas.
    List<BoletinJudicialCache> findByActuacion_IdIn(List<Long> actuacionIds);
}
