package sie.siejuridicos.caso;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CasoRepository extends JpaRepository<Caso, Long> {

    boolean existsByCodigoUnico(String codigoUnico);

    Optional<Caso> findByCodigoUnico(String codigoUnico);

    // JOIN FETCH evita N+1 al listar (cliente y categoría son LAZY): una sola consulta en
    // vez de una extra por cada caso listado en /admin/casos.
    @Query("SELECT c FROM Caso c JOIN FETCH c.cliente JOIN FETCH c.categoria ORDER BY c.fechaCreacion DESC")
    List<Caso> listarTodosConDetalle();
}
