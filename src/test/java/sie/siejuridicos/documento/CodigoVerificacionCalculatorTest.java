package sie.siejuridicos.documento;

import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Código de verificación impreso en cada documento sellado (ver
// CodigoVerificacionCalculator): debe ser recomputable de forma determinista a partir de
// datos ya públicos del documento (id, número de secuencia) + una clave secreta del
// servidor, para que VerificacionPublicaService pueda confirmar cualquier código sin
// guardar uno nuevo aparte -- y para que nadie pueda fabricar uno válido sin la clave.
class CodigoVerificacionCalculatorTest {

    private static final String CLAVE_MAESTRA = Base64.getEncoder().encodeToString(new byte[32]);
    private static final UUID DOC_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private CodigoVerificacionCalculator calculadora() {
        return new CodigoVerificacionCalculator(new DocumentoClaveMaestraService(CLAVE_MAESTRA));
    }

    @Test
    void esDeterministaParaElMismoDocumentoYSecuencia() {
        CodigoVerificacionCalculator c = calculadora();
        assertEquals(c.calcular(DOC_ID, 1L), c.calcular(DOC_ID, 1L));
    }

    @Test
    void numerosDeSecuenciaDistintosProducenCodigosDistintos() {
        CodigoVerificacionCalculator c = calculadora();
        assertNotEquals(c.calcular(DOC_ID, 1L), c.calcular(DOC_ID, 2L));
    }

    @Test
    void documentosDistintosProducenCodigosDistintos() {
        CodigoVerificacionCalculator c = calculadora();
        UUID otroDocumento = UUID.fromString("22222222-2222-2222-2222-222222222222");
        assertNotEquals(c.calcular(DOC_ID, 1L), c.calcular(otroDocumento, 1L));
    }

    @Test
    void elFormatoEsSeisGruposDeCuatroCaracteresHexSeparadosPorGuion() {
        String codigo = calculadora().calcular(DOC_ID, 1L);
        assertTrue(codigo.matches("^[0-9A-F]{4}(-[0-9A-F]{4}){5}$"), "código con formato inesperado: " + codigo);
    }

    @Test
    void claveMaestraDistintaProduceCodigosDistintosParaElMismoDocumento() {
        byte[] otraRaiz = new byte[32];
        otraRaiz[0] = 7;
        CodigoVerificacionCalculator otra = new CodigoVerificacionCalculator(
                new DocumentoClaveMaestraService(Base64.getEncoder().encodeToString(otraRaiz)));
        assertNotEquals(calculadora().calcular(DOC_ID, 1L), otra.calcular(DOC_ID, 1L));
    }
}
