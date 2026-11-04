package sie.siejuridicos.vigilancia;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface TerminoProcesalRepository extends JpaRepository<TerminoProcesal, Long> {

    // Evita procesar dos veces la misma actuacion (ver TerminoProcesalService) -- una
    // actuacion abre a lo sumo un termino en este modelo.
    boolean existsByActuacion_Id(Long actuacionId);

    // Para CasoService.actualizar(): cuando se le asigna abogado a un caso que YA tenia
    // terminos detectados sin tarea (el abogado no existia todavia cuando se proceso la
    // actuacion, ver TerminoProcesalService.procesarSiAplica) -- sin esto, esos terminos se
    // quedarian para siempre sin ninguna alerta activa, porque procesarSiAplica nunca
    // reprocesa una actuacion ya registrada.
    List<TerminoProcesal> findByCaso_IdAndTareaIsNull(Long casoId);

    // Para /admin/calendario (ver VigilanciaAdminController.terminosCalendario()): todos los
    // terminos cuyo vencimiento cae dentro del rango visible del mes, con el caso, la actuacion y el
    // abogado asignado ya cargados en la misma consulta (evita N+1 al armar la respuesta).
    @Query("SELECT t FROM TerminoProcesal t JOIN FETCH t.caso c JOIN FETCH c.cliente LEFT JOIN FETCH c.abogadoAsignado JOIN FETCH t.actuacion a "
            + "WHERE t.fechaVencimiento BETWEEN :desde AND :hasta "
            + "ORDER BY t.fechaVencimiento ASC")
    List<TerminoProcesal> listarEnRango(@Param("desde") LocalDate desde, @Param("hasta") LocalDate hasta);
}
