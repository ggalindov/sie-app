package sie.siejuridicos.documento.pdf;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Regresión de un bug real encontrado en la auditoría del motor de firma (2026-10-22):
// CierreCustodiaPdfAppender.combinar() solo importaba la página 1 de la hoja de cierre
// generada por generarHojaCierre(), que a su vez pagina sola (vía Document.add()) en cuanto
// el contenido de muchos firmantes no cabe en una sola página. Con pocos firmantes nunca se
// notaba (todo cabía en una página); con muchos, los firmantes que caían en la página 2+ se
// perdían en silencio del PDF final -- sin ningún error. Esta prueba fuerza el desborde con
// un número alto de firmantes y confirma que TODOS aparecen en el resultado final.
class CierreCustodiaPdfAppenderTest {

    private final CierreCustodiaPdfAppender appender = new CierreCustodiaPdfAppender();

    private byte[] pdfOriginalDeUnaPagina() throws Exception {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document doc = new Document();
        PdfWriter.getInstance(doc, salida);
        doc.open();
        doc.add(new Paragraph("Contenido original del documento subido por gerencia."));
        doc.close();
        return salida.toByteArray();
    }

    private HojaCierreCustodiaData.FirmanteResumen firmante(String nombre) {
        return new HojaCierreCustodiaData.FirmanteResumen(
                nombre, "PARTE", "CC " + nombre.hashCode(),
                LocalDateTime.now(), "190.1.2.3", "Mozilla/5.0 (prueba)",
                "Ninguna", "hash-evidencia-" + nombre, null);
    }

    @Test
    void conMuchosFirmantesLaHojaDeCierreDesbordaAVariasPaginasYNingunoSePierde() throws Exception {
        List<HojaCierreCustodiaData.FirmanteResumen> firmantes = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            firmantes.add(firmante("Firmante Numero " + i));
        }
        HojaCierreCustodiaData datos = new HojaCierreCustodiaData(
                "Contrato de prueba", "ABC-123", "https://siejuridicos.com/verificar-documento?codigo=ABC-123",
                LocalDateTime.now(), firmantes);

        byte[] resultado = appender.anexar(pdfOriginalDeUnaPagina(), datos);

        try (PDDocument pdf = Loader.loadPDF(resultado)) {
            assertTrue(pdf.getNumberOfPages() >= 3,
                    "25 firmantes deben desbordar la hoja de cierre a varias páginas, más la página original.");
            String texto = new PDFTextStripper().getText(pdf);
            for (int i = 1; i <= 25; i++) {
                assertTrue(texto.contains("Firmante Numero " + i),
                        "El firmante " + i + " no aparece en el PDF final -- se perdió al paginar.");
            }
        }
    }

    @Test
    void conPocosFirmantesCabeEnUnaSolaPaginaDeCierre() throws Exception {
        List<HojaCierreCustodiaData.FirmanteResumen> firmantes = List.of(
                firmante("Gerente de Prueba"), firmante("Parte de Prueba"));
        HojaCierreCustodiaData datos = new HojaCierreCustodiaData(
                "Contrato corto", "XYZ-999", null, LocalDateTime.now(), firmantes);

        byte[] resultado = appender.anexar(pdfOriginalDeUnaPagina(), datos);

        try (PDDocument pdf = Loader.loadPDF(resultado)) {
            assertEquals(2, pdf.getNumberOfPages(), "1 página original + 1 página de cierre.");
            String texto = new PDFTextStripper().getText(pdf);
            assertTrue(texto.contains("Gerente de Prueba"));
            assertTrue(texto.contains("Parte de Prueba"));
        }
    }
}
