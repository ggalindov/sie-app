package sie.siejuridicos.vigilancia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProcesoCandidatoDetectadoRepository extends JpaRepository<ProcesoCandidatoDetectado, Long> {

    boolean existsByCliente_IdAndLlaveProceso(Long clienteId, String llaveProceso);

    // JOIN FETCH del cliente -- la vista de /admin/procesos-detectados necesita su nombre
    // para cada fila, y DeteccionProcesosNuevosService.buscarNuevosProcesos() corre sin
    // @Transactional (igual que SeguimientoJudicialService, por la pausa real entre cada
    // consulta), así que un cliente perezoso lanzaría LazyInitializationException.
    @Query("SELECT p FROM ProcesoCandidatoDetectado p JOIN FETCH p.cliente "
            + "WHERE p.estado = sie.siejuridicos.vigilancia.EstadoProcesoCandidato.PENDIENTE "
            + "ORDER BY p.fechaDeteccion DESC")
    List<ProcesoCandidatoDetectado> listarPendientes();

    long countByEstado(EstadoProcesoCandidato estado);
}
