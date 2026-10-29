package sie.siejuridicos.documento;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;

// Confirma que documento_eventos es append-only DE VERDAD en la base de datos (ver
// V53__documento_eventos_append_only.sql), no solo "por convención" en el código Java: un
// UPDATE o DELETE directo por SQL, sin la marca de sesión que usa exclusivamente
// DocumentoAdminService#eliminar, debe ser rechazado por Postgres mismo, no solo por el
// repositorio que nunca expone esos métodos.
@SpringBootTest
@Transactional
class DocumentoEventosAppendOnlyIntegrationTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void unUpdateDirectoSobreUnEventoYaInsertadoSeRechaza() {
        UUID eventoId = insertarDocumentoYEventoDePrueba();

        assertThrows(Exception.class, () ->
                entityManager.createNativeQuery("UPDATE documento_eventos SET descripcion = 'manipulado' WHERE id = ?1")
                        .setParameter(1, eventoId)
                        .executeUpdate());
    }

    @Test
    void unDeleteDirectoSinLaMarcaDeSesionSeRechaza() {
        UUID eventoId = insertarDocumentoYEventoDePrueba();

        assertThrows(Exception.class, () ->
                entityManager.createNativeQuery("DELETE FROM documento_eventos WHERE id = ?1")
                        .setParameter(1, eventoId)
                        .executeUpdate());
    }

    @SuppressWarnings("unchecked")
    private UUID insertarDocumentoYEventoDePrueba() {
        Long staffId = ((Number) entityManager.createNativeQuery(
                        "INSERT INTO usuarios_internos (nombre, correo, contrasena, rol) VALUES "
                                + "('Test append-only', ?1, 'x', 'ABOGADO') RETURNING id")
                        .setParameter(1, "test-append-only-" + UUID.randomUUID() + "@sie.local")
                        .getSingleResult())
                .longValue();

        UUID documentoId = UUID.randomUUID();
        entityManager.createNativeQuery(
                        "INSERT INTO documentos_firma (id, creado_por_staff_id, nombre, content_type, contenido_pdf) "
                                + "VALUES (?1, ?2, 'Documento de prueba append-only', 'application/pdf', ?3)")
                .setParameter(1, documentoId)
                .setParameter(2, staffId)
                .setParameter(3, new byte[0])
                .executeUpdate();

        UUID eventoId = UUID.randomUUID();
        entityManager.createNativeQuery(
                        "INSERT INTO documento_eventos (id, documento_id, tipo_evento, actor_tipo, secuencia, "
                                + "hash_evento, hash_evento_anterior) VALUES (?1, ?2, 'DILIGENCIADO', 'SISTEMA', 1, 'hash-evento', 'GENESIS')")
                .setParameter(1, eventoId)
                .setParameter(2, documentoId)
                .executeUpdate();
        return eventoId;
    }
}
