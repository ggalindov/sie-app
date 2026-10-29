package sie.siejuridicos.documento;

import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

// Punto 5 de las mejoras pedidas sobre el motor "hermano" (UCN): confirma que el aviso
// legal de margen aparece en TODAS las páginas de un PDF multi-página (incluida una página
// apaisada, el caso más propenso a que una posición mal calculada quede fuera del área
// visible o de lado).
class DocumentoMargenLegalStamperTest {

    private final DocumentoMargenLegalStamper stamper = new DocumentoMargenLegalStamper();

    private byte[] pdfDeVariasPaginas() throws Exception {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.LETTER);
        PdfWriter.getInstance(doc, salida);
        doc.open();
        doc.add(new Paragraph("Primera página, tamaño carta."));
        doc.newPage();
        doc.setPageSize(PageSize.A4.rotate());
        doc.newPage();
        doc.add(new Paragraph("Segunda página, A4 apaisada."));
        doc.close();
        return salida.toByteArray();
    }

    @Test
    void elTextoDeMargenApareceEnTodasLasPaginas() throws Exception {
        byte[] original = pdfDeVariasPaginas();

        byte[] resultado = stamper.estamparMargen(original, "ABC-123");

        try (PDDocument pdf = Loader.loadPDF(resultado)) {
            assertTrue(pdf.getNumberOfPages() >= 2, "El PDF de prueba debe tener al menos 2 páginas.");
            for (int pagina = 1; pagina <= pdf.getNumberOfPages(); pagina++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setStartPage(pagina);
                stripper.setEndPage(pagina);
                String texto = stripper.getText(pdf);
                assertTrue(texto.contains("Firma electrónica") || texto.contains("firma electrónica"),
                        "La página " + pagina + " no tiene el aviso legal de margen.");
                assertTrue(texto.contains("ABC-123"),
                        "La página " + pagina + " no incluye el código de verificación en el aviso de margen.");
            }
        }
    }
}
