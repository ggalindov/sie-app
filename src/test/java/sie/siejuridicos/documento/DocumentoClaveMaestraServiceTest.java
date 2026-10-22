package sie.siejuridicos.documento;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Raíz criptográfica del motor de firma (ver DocumentoClaveMaestraService): un solo secreto
// deriva la KEK de envelope encryption, la llave de la cadena de eventos y la del código de
// verificación. Estas pruebas confirman la propiedad que sostiene el aislamiento entre
// usos: misma llave maestra => siempre la misma sub-llave para una etiqueta, pero
// sub-llaves distintas entre etiquetas (nunca se reutilizan los mismos bytes para dos
// propósitos distintos).
class DocumentoClaveMaestraServiceTest {

    private static final String CLAVE_VALIDA = Base64.getEncoder().encodeToString(new byte[32]);

    private DocumentoClaveMaestraService servicio() {
        return new DocumentoClaveMaestraService(CLAVE_VALIDA);
    }

    @Test
    void derivacionEsDeterministaParaLaMismaLlaveMaestra() {
        DocumentoClaveMaestraService a = servicio();
        DocumentoClaveMaestraService b = servicio();
        assertArrayEquals(a.claveCadenaEventos(), b.claveCadenaEventos());
        assertArrayEquals(a.claveCodigoVerificacion(), b.claveCodigoVerificacion());
        assertArrayEquals(a.obtenerClaveMaestra(DocumentoClaveMaestraService.KEK_ID_ACTUAL),
                b.obtenerClaveMaestra(DocumentoClaveMaestraService.KEK_ID_ACTUAL));
    }

    @Test
    void lasTresSubLlavesSonDistintasEntreSi() {
        // Si esto fallara, un secreto filtrado para un uso (p. ej. la cadena de eventos)
        // comprometería también el cifrado del contenido del documento.
        DocumentoClaveMaestraService s = servicio();
        byte[] kek = s.obtenerClaveMaestra(DocumentoClaveMaestraService.KEK_ID_ACTUAL);
        byte[] cadena = s.claveCadenaEventos();
        byte[] codigo = s.claveCodigoVerificacion();
        assertNotEquals(Base64.getEncoder().encodeToString(kek), Base64.getEncoder().encodeToString(cadena));
        assertNotEquals(Base64.getEncoder().encodeToString(kek), Base64.getEncoder().encodeToString(codigo));
        assertNotEquals(Base64.getEncoder().encodeToString(cadena), Base64.getEncoder().encodeToString(codigo));
    }

    @Test
    void llavesMaestrasDistintasProducenSubLlavesDistintas() {
        byte[] otraClaveRaiz = new byte[32];
        otraClaveRaiz[0] = 1;
        DocumentoClaveMaestraService otra = new DocumentoClaveMaestraService(Base64.getEncoder().encodeToString(otraClaveRaiz));
        assertNotEquals(
                Base64.getEncoder().encodeToString(servicio().claveCadenaEventos()),
                Base64.getEncoder().encodeToString(otra.claveCadenaEventos()));
    }

    @Test
    void rechazaUnaLlaveQueNoDecodificaATreintaYDosBytes() {
        String claveCorta = Base64.getEncoder().encodeToString(new byte[16]);
        assertThrows(IllegalStateException.class, () -> new DocumentoClaveMaestraService(claveCorta));
    }

    @Test
    void rechazaUnaLlaveQueNoEsBase64Valido() {
        assertThrows(IllegalStateException.class, () -> new DocumentoClaveMaestraService("no-es-base64-valido!!"));
    }

    @Test
    void unKekIdDesconocidoEsRechazado() {
        DocumentoClaveMaestraService s = servicio();
        assertEquals(DocumentoClaveMaestraService.KEK_ID_ACTUAL, s.kekIdActual());
        assertThrows(IllegalStateException.class, () -> s.obtenerClaveMaestra("v99-no-existe"));
        assertFalse("v99-no-existe".equals(s.kekIdActual()));
    }
}
