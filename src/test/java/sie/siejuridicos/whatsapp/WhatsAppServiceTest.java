package sie.siejuridicos.whatsapp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

// normalizarCelular() es un método estático puro: no necesita contexto de Spring ni la API
// de Meta real para probarse. La hoja de la firma trae el teléfono en formatos
// inconsistentes (con o sin indicativo, con espacios, guiones); esto confirma que todos los
// formatos reales terminan en el mismo E.164 sin "+" que exige la API de WhatsApp.
class WhatsAppServiceTest {

    @Test
    void celularDeDiezDigitosSinIndicativoQuedaConIndicativo() {
        assertEquals("573001234567", WhatsAppService.normalizarCelular("3001234567"));
    }

    @Test
    void celularConIndicativoYaPuestoQuedaIgual() {
        assertEquals("573001234567", WhatsAppService.normalizarCelular("573001234567"));
    }

    @Test
    void celularConEspaciosYGuionesSeLimpiaCorrectamente() {
        assertEquals("573001234567", WhatsAppService.normalizarCelular("+57 300 123-4567"));
        assertEquals("573001234567", WhatsAppService.normalizarCelular("300 123 4567"));
    }

    @Test
    void unTelefonoFijoSeRechaza() {
        // Colombia: un celular real siempre empieza en 3; un fijo con indicativo de ciudad
        // (ej. 601... de Bogotá) tiene el mismo largo pero no es un celular. WhatsApp es
        // solo para celulares.
        assertNull(WhatsAppService.normalizarCelular("6013456789"));
    }

    @Test
    void unValorVacioONuloSeRechaza() {
        assertNull(WhatsAppService.normalizarCelular(null));
        assertNull(WhatsAppService.normalizarCelular(""));
        assertNull(WhatsAppService.normalizarCelular("   "));
    }

    @Test
    void unTextoQueNoEsUnNumeroSeRechaza() {
        assertNull(WhatsAppService.normalizarCelular("EN PROCESO DE RADICACION"));
    }

    @Test
    void variosCorreosSeparadosPorPuntoYComaEnLaColumnaDeTelefonoSeRechaza() {
        // Mismo tipo de dato sucio ya visto en la columna de correo de un caso real (varias
        // direcciones en una sola celda): no debe colarse como si fuera un número válido.
        assertNull(WhatsAppService.normalizarCelular("3001234567; 3009876543"));
    }
}
