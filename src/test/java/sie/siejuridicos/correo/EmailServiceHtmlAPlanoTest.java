package sie.siejuridicos.correo;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Bug real de entregabilidad encontrado en esta revisión (reportado por el usuario: los
// correos a Hotmail/Outlook no llegaban, sin ningún error de SMTP en los logs -- SPF/DKIM/
// alineamiento From-Reply-To ya estaban correctos). Un correo enviado SOLO en HTML, sin
// alternativa de texto plano, es en sí mismo una señal que los filtros de Microsoft
// penalizan. Estas pruebas cubren htmlAPlano(), la conversión que ahora genera esa
// alternativa (ver EmailService.enviarHtml()).
class EmailServiceHtmlAPlanoTest {

    @Test
    void quitaEtiquetasYConservaElTexto() {
        String resultado = EmailService.htmlAPlano("<p>Hola <strong>Juan</strong>, gracias.</p>");
        assertEquals("Hola Juan, gracias.", resultado);
    }

    @Test
    void convierteSaltosDeBloqueEnSaltosDeLineaReales() {
        String resultado = EmailService.htmlAPlano("<p>Primera línea.</p><p>Segunda línea.</p>");
        assertEquals("Primera línea.\nSegunda línea.", resultado);
    }

    @Test
    void unEnlaceIncluyeElDestinoEntreParentesis() {
        String resultado = EmailService.htmlAPlano(
                "<a href=\"https://siejuridicos.com/consulta-caso\" style=\"color:red;\">Consulta tu caso</a>");
        assertEquals("Consulta tu caso (https://siejuridicos.com/consulta-caso)", resultado);
    }

    // Cuando el propio texto visible del enlace ya ES la URL (ej. el pie de la plantilla
    // muestra el sitio web como texto de su propio enlace), repetirla sería redundante.
    @Test
    void unEnlaceCuyoTextoYaEsLaUrlNoLaRepite() {
        String resultado = EmailService.htmlAPlano(
                "<a href=\"https://siejuridicos.com\">https://siejuridicos.com</a>");
        assertEquals("https://siejuridicos.com", resultado);
    }

    @Test
    void decodificaLasEntidadesQueUsaLaPlantilla() {
        String resultado = EmailService.htmlAPlano("Bogot&aacute; &amp; alrededores &middot; &copy; 2026");
        // &aacute; no está en la lista decodificada a propósito (esta plantilla nunca la usa,
        // ver comentario de htmlAPlano) -- lo que importa es que las que SÍ usa queden bien.
        assertTrue(resultado.contains("&"));
        assertTrue(resultado.contains("-"));
        assertTrue(resultado.contains("(c)"));
    }

    @Test
    void quitaPorCompletoElContenidoDeHead() {
        String resultado = EmailService.htmlAPlano(
                "<html><head><title>Asunto interno</title></head><body><p>Contenido real.</p></body></html>");
        assertFalse(resultado.contains("Asunto interno"));
        assertTrue(resultado.contains("Contenido real."));
    }

    @Test
    void colapsaVariasLineasEnBlancoSeguidas() {
        String resultado = EmailService.htmlAPlano("<p>Uno</p><br><br><br><p>Dos</p>");
        assertFalse(resultado.contains("\n\n\n"), "No deben quedar 3+ saltos de línea seguidos.");
    }

    @Test
    void nuncaQuedaUnaEtiquetaSinCerrarEnElResultado() {
        String resultado = EmailService.htmlAPlano(
                "<table role=\"presentation\"><tr><td style=\"padding:6px;\">Dato</td></tr></table>");
        assertFalse(resultado.contains("<"));
        assertFalse(resultado.contains(">"));
        assertTrue(resultado.contains("Dato"));
    }
}
