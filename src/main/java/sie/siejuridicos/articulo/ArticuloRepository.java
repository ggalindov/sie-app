package sie.siejuridicos.articulo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ArticuloRepository extends JpaRepository<Articulo, Long> {

    Optional<Articulo> findBySlug(String slug);

    Optional<Articulo> findBySlugAndEstado(String slug, EstadoArticulo estado);

    boolean existsBySlug(String slug);

    // filtro por categoría y búsqueda de palabra clave (RF-10) usando el índice
    // de texto completo (columna generada `busqueda`) creado en V3.
    @Query(value = "SELECT a.* FROM articulos a " +
            "WHERE a.estado = 'PUBLICADO' " +
            "AND (:idCategoria IS NULL OR a.id_categoria = :idCategoria) " +
            "AND (:busqueda IS NULL OR a.busqueda @@ plainto_tsquery('spanish', :busqueda)) " +
            "ORDER BY a.fecha_publicacion DESC",
            nativeQuery = true)
    List<Articulo> buscarPublicados(@Param("idCategoria") Long idCategoria, @Param("busqueda") String busqueda);

    // invoca fn_publicar_articulo (V9): valida integridad y publica en una sola operación transaccional
    @Query(value = "SELECT * FROM fn_publicar_articulo(:id)", nativeQuery = true)
    Articulo publicarArticulo(@Param("id") Long id);
}
