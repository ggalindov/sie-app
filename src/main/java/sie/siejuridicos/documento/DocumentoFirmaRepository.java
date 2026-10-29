package sie.siejuridicos.documento;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentoFirmaRepository extends JpaRepository<DocumentoFirma, UUID> {

    List<DocumentoFirma> findByCasoIdOrderByOrdenCreacionAsc(Long casoId);

    // Documentos en un estado dado, en cualquier caso (ver DocumentoAdminController: listado
    // del panel con filtro por estado) o sin caso ligado.
    List<DocumentoFirma> findByEstadoDocumento(String estadoDocumento);

    Optional<DocumentoFirma> findByCodigoVerificacion(String codigoVerificacion);

    // Verificación pública subiendo un PDF: busca si sus bytes exactos coinciden con la
    // versión VIGENTE de algún documento (ver VerificacionPublicaService).
    Optional<DocumentoFirma> findFirstByHashContenidoIgnoreCase(String hashContenido);

    // Número de secuencia único y global del documento (no por caso), respaldado por la
    // SEQUENCE nativa documento_firma_numero_secuencia_seq (V48). Se llama en la transición
    // DILIGENCIADO -> EMITIDO.
    @Query(value = "SELECT nextval('documento_firma_numero_secuencia_seq')", nativeQuery = true)
    long siguienteNumeroSecuencia();

    // Documentos ya sellados pero sin sello de tiempo RFC 3161 (ver Rfc3161RetryScheduler).
    List<DocumentoFirma> findByRfc3161TokenIsNullAndEstadoDocumentoIn(List<String> estadosDocumento);

    List<DocumentoFirma> findTop200ByOrderByFechaCreacionDesc();

    // Buscador total del panel (ver sie.siejuridicos.busqueda.BusquedaService): nombre es
    // plano (no cifrado), LIKE/ILIKE directo en SQL.
    List<DocumentoFirma> findByNombreContainingIgnoreCase(String nombre);

    // Ids de los documentos que siguen EMITIDO esperando que se complete la compuerta de
    // firma de staff (ver DocumentoFirmaStaffRetryListener, disparado cuando un usuario
    // interno sube/cambia su firma). Solo ids, para no cargar en memoria el contenido de
    // todos de una vez.
    @Query("SELECT d.id FROM DocumentoFirma d WHERE d.estadoDocumento = :estado ORDER BY d.fechaCreacion ASC")
    List<UUID> findIdsByEstadoDocumento(@Param("estado") String estado);

    // Ids de documentos ya sellados que siguen VIGENTES (nunca marcados ALTERADO): el
    // universo del barrido proactivo de integridad.
    @Query("SELECT d.id FROM DocumentoFirma d WHERE d.estadoDocumento IN :estados AND d.estadoIntegridad = :estadoIntegridad "
            + "ORDER BY d.fechaCreacion ASC")
    List<UUID> findIdsByEstadoDocumentoInAndEstadoIntegridad(@Param("estados") List<String> estados,
                                                              @Param("estadoIntegridad") String estadoIntegridad);
}
