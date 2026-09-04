package sie.siejuridicos.caso.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Bean Validation puro (jakarta.validation), sin contexto de Spring: rápido, y confirma
// exactamente lo que MockMvc no puede probar sin autenticarse primero (ver
// SecurityIntegrationTest.crearCasoConTokenInventadoYCuerpoConDatosSeRechazaPorAutenticacionNoPorValidacion).
class CrearCasoRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private CrearCasoRequest valido(String radicadoId) {
        return new CrearCasoRequest("Cliente Real", "cliente@example.com", "3001234567", radicadoId, null);
    }

    @Test
    void unRadicadoConSoloLetrasNumerosYGuionesEsValido() {
        assertTrue(validator.validate(valido("11001310300320210012300")).isEmpty());
        assertTrue(validator.validate(valido("SIE-2026-A1B2C3")).isEmpty());
    }

    @Test
    void unRadicadoConEspaciosSeRechaza() {
        assertFalse(validator.validate(valido("radicado con espacios")).isEmpty());
    }

    @Test
    void unRadicadoConHtmlOScriptSeRechaza() {
        // No porque haya XSS real en este flujo (el panel de admin renderiza el radicado
        // como texto en React, que escapa automáticamente), sino como defensa en
        // profundidad: nada que no sea letras/números/guiones debería llegar a guardarse.
        assertFalse(validator.validate(valido("<script>alert(1)</script>")).isEmpty());
    }

    @Test
    void unRadicadoConIntentoDeInyeccionSqlSeRechaza() {
        assertFalse(validator.validate(valido("'; DROP TABLE casos; --")).isEmpty());
    }

    @Test
    void unRadicadoVacioSeRechaza() {
        assertFalse(validator.validate(valido("")).isEmpty());
        assertFalse(validator.validate(valido("   ")).isEmpty());
    }

    @Test
    void unRadicadoDeMasDeCincuentaCaracteresSeRechaza() {
        String muyLargo = "1".repeat(51);
        Set<ConstraintViolation<CrearCasoRequest>> violaciones = validator.validate(valido(muyLargo));
        assertFalse(violaciones.isEmpty());
    }

    @Test
    void unCorreoConFormatoInvalidoSeRechaza() {
        CrearCasoRequest request = new CrearCasoRequest("Cliente", "esto-no-es-un-correo", null, "RAD-001", null);
        assertFalse(validator.validate(request).isEmpty());
    }

    @Test
    void elNombreEnBlancoSeRechaza() {
        CrearCasoRequest request = new CrearCasoRequest("   ", "cliente@example.com", null, "RAD-001", null);
        assertFalse(validator.validate(request).isEmpty());
    }
}
