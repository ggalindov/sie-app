package sie.siejuridicos.documento;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

// Confirma que DocumentoPdfStamper -- la página de firmas que se añade al documento OFICIAL
// que reciben las partes -- nunca pierde un firmante cuando hay tantos que no caben en una
// sola página nueva: a diferencia del bug real encontrado en CierreCustodiaPdfAppender (ver
// CierreCustodiaPdfAppenderTest), aquí concatenar() SÍ copiaba todas las páginas generadas
// desde siempre. Esta prueba lo deja fijado explícitamente para que un cambio futuro no lo
// rompa en silencio, igual que para la hoja de cierre.
class DocumentoPdfStamperTest {

    private final DocumentoPdfStamper stamper = new DocumentoPdfStamper("https://siejuridicos.com");

    private byte[] pdfOriginalDeUnaPagina() throws Exception {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document doc = new Document();
        PdfWriter.getInstance(doc, salida);
        doc.open();
        doc.add(new Paragraph("Contenido original del documento subido por gerencia."));
        doc.close();
        return salida.toByteArray();
    }

    private DocumentoFirmante firmante(DocumentoFirma documento, int orden, String nombre) {
        return new DocumentoFirmante(documento, DocumentoFirmante.TIPO_PARTE, null, null,
                DocumentoFirmante.TIPO_CONTACTO_NATURAL, nombre, "CC " + Math.abs(nombre.hashCode()), orden);
    }

    @Test
    void conMuchosFirmantesLaPaginaDeFirmasDesbordaYNingunoSePierde() throws Exception {
        DocumentoFirma documento = new DocumentoFirma(null, null, "Contrato con muchas partes", "application/pdf",
                pdfOriginalDeUnaPagina());
        documento.setCodigoVerificacion("DEF-456");

        List<DocumentoFirmante> firmantes = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            firmantes.add(firmante(documento, i, "Parte Numero " + i));
        }

        byte[] resultado = stamper.estamparFirmas(documento.getContenidoPdf(), documento, firmantes);

        try (PDDocument pdf = Loader.loadPDF(resultado)) {
            assertTrue(pdf.getNumberOfPages() >= 3,
                    "20 firmantes deben desbordar a varias páginas de firmas, más la página original.");
            String texto = new PDFTextStripper().getText(pdf);
            for (int i = 1; i <= 20; i++) {
                assertTrue(texto.contains("Parte Numero " + i),
                        "El firmante " + i + " no aparece en el documento final -- se perdió al paginar.");
            }
        }
    }
}
