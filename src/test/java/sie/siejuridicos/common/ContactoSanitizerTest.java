package sie.siejuridicos.common;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ContactoSanitizerTest {

    @Test
    void extraerCorreos_conPuntosMediosYMultiplesDestinatarios() {
        String entrada = "gerencia@rumbos.com.co; subgerente@rumbos.com.co; gerente@rumbos.com.co; pagosinternos@rumbos.com.co; pagos@rumbos.com.co·";
        List<String> correos = ContactoSanitizer.extraerCorreos(entrada);

        assertEquals(5, correos.size());
        assertEquals("gerencia@rumbos.com.co", correos.get(0));
        assertEquals("subgerente@rumbos.com.co", correos.get(1));
        assertEquals("gerente@rumbos.com.co", correos.get(2));
        assertEquals("pagosinternos@rumbos.com.co", correos.get(3));
        assertEquals("pagos@rumbos.com.co", correos.get(4)); // El punto medio final '·' fue removido limpiamente

        String normalizada = ContactoSanitizer.normalizarCadenaCorreos(entrada);
        assertEquals("gerencia@rumbos.com.co; subgerente@rumbos.com.co; gerente@rumbos.com.co; pagosinternos@rumbos.com.co; pagos@rumbos.com.co", normalizada);
    }

    @Test
    void extraerCorreos_conSeparadoresMixtosYDuplicados() {
        String entrada = "admin@sie.com, contacto@sie.com / soporte@sie.com | admin@sie.com\ninfo@sie.com • <otro@sie.com>";
        List<String> correos = ContactoSanitizer.extraerCorreos(entrada);

        assertEquals(5, correos.size());
        assertEquals("admin@sie.com", correos.get(0));
        assertEquals("contacto@sie.com", correos.get(1));
        assertEquals("soporte@sie.com", correos.get(2));
        assertEquals("info@sie.com", correos.get(3));
        assertEquals("otro@sie.com", correos.get(4));
    }

    @Test
    void extraerCelulares_conMultiplesNumerosYSeparadores() {
        String entrada = "3001234567; 3009876543";
        List<String> celulares = ContactoSanitizer.extraerCelulares(entrada);

        assertEquals(2, celulares.size());
        assertEquals("573001234567", celulares.get(0));
        assertEquals("573009876543", celulares.get(1));
        assertEquals("573001234567", ContactoSanitizer.normalizarCelular(entrada));
        assertEquals("3001234567 / 3009876543", ContactoSanitizer.normalizarCadenaCelulares(entrada));
    }

    @Test
    void extraerCelulares_conFormatosVariadosEFijos() {
        String entrada = "+57 (312) 456-7890 / Tel: 6013456789 - Cel: 315.987.6543";
        List<String> celulares = ContactoSanitizer.extraerCelulares(entrada);

        assertEquals(2, celulares.size());
        assertEquals("573124567890", celulares.get(0));
        assertEquals("573159876543", celulares.get(1));
    }

    @Test
    void extraerCelulares_casosInvalidos() {
        assertTrue(ContactoSanitizer.extraerCelulares(null).isEmpty());
        assertTrue(ContactoSanitizer.extraerCelulares("   ").isEmpty());
        assertTrue(ContactoSanitizer.extraerCelulares("EN PROCESO DE RADICACION").isEmpty());
        assertTrue(ContactoSanitizer.extraerCelulares("6013456789").isEmpty());
        assertNull(ContactoSanitizer.normalizarCelular("EN PROCESO"));
    }
}
