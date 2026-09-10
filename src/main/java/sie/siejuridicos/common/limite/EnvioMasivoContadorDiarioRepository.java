package sie.siejuridicos.common.limite;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;

public interface EnvioMasivoContadorDiarioRepository extends JpaRepository<EnvioMasivoContadorDiario, LocalDate> {

    // Reserva atómicamente una unidad de cupo para HOY si no se ha alcanzado `limite` todavía
    // (ver fn_reservar_cupo_envio_masivo, migración V37). Nativa porque el UPDATE condicional
    // sin condición de carrera vive en la función SQL, no se puede expresar igual de seguro
    // como una consulta derivada de Spring Data.
    @Query(value = "SELECT fn_reservar_cupo_envio_masivo(:limite)", nativeQuery = true)
    boolean reservarCupo(@Param("limite") int limite);
}
