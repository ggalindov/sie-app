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

    // Regresión de un bug real encontrado en auditoría: escaparJson() solo escapaba "\",
    // comillas y "\n". Un "\r" o un "\t" (frecuentes en texto pegado desde Windows o un
    // formulario) quedaban sin escapar dentro del valor JSON, violando RFC 8259 y arriesgando
    // que Meta rechazara el envío completo de la plantilla sin aviso claro de por qué.
    @Test
    void escaparJsonConvierteSaltosDeCarroYTabsEnEspacios() {
        // "\r\n" son DOS caracteres de control, cada uno se convierte en su propio espacio
        // (de ahí el doble espacio) -- escaparJson() no intenta detectar la secuencia CRLF
        // como una sola unidad, solo escapa carácter por carácter.
        assertEquals("linea 1  linea 2", WhatsAppService.escaparJson("linea 1\r\nlinea 2"));
        assertEquals("col1 col2", WhatsAppService.escaparJson("col1\tcol2"));
    }

    @Test
    void escaparJsonEscapaBarraInvertidaYComillas() {
        assertEquals("dice: \\\"hola\\\" y C:\\\\ruta", WhatsAppService.escaparJson("dice: \"hola\" y C:\\ruta"));
    }

    @Test
    void escaparJsonEscapaCualquierOtroCaracterDeControl() {
        // 0x07 (BEL) no tiene un caso especial propio como \n/\r/\t, pero RFC 8259 igual
        // exige escaparlo: cae en la rama genérica \\u00xx.
        assertEquals("a\\u0007b", WhatsAppService.escaparJson("a\u0007b"));
    }

    @Test
    void escaparJsonConNuloDevuelveCadenaVacia() {
        assertEquals("", WhatsAppService.escaparJson(null));
    }
}
