package sie.siejuridicos.cobro;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClienteCobroRepository extends JpaRepository<ClienteCobro, Long> {

    // Llave de la sincronización (ver CobroService.sincronizarDesdeHoja()): decide si una
    // fila de la hoja ya existe localmente o hay que crearla. Se busca sin filtrar por
    // "activo" a propósito: una fila que había desaparecido y volvió a aparecer debe
    // reactivarse, no duplicarse.
    Optional<ClienteCobro> findByTipoAndNumeroFila(TipoClienteCobro tipo, String numeroFila);

    List<ClienteCobro> findByActivoTrueOrderByNombreAsc();

    // Usada por el webhook de WhatsApp (ver CobroService.registrarRespuesta()) para saber a
    // quién le llegó la respuesta, sin descifrar el teléfono de cada fila para compararlo.
    List<ClienteCobro> findByTelefonoHashAndActivoTrue(String telefonoHash);
}
