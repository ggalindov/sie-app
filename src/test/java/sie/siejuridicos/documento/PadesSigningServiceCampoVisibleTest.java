package sie.siejuridicos.documento;

import com.lowagie.text.Document;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfWriter;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

// Punto 6 de las mejoras pedidas sobre el motor "hermano" (UCN): el campo de firma visible
// debe caer DENTRO de la página y dentro de la banda inferior reservada por
// DocumentoPdfStamper (MARGEN_INFERIOR_RESERVADO_PT = 110pt, ver esa clase), y por encima
// de la línea de 10pt donde DocumentoMargenLegalStamper dibuja su propio aviso -- para que
// ninguno de los dos se solape. No monta el pipeline completo de firma PKCS#7 (llave RSA
// real + TSA), solo la geometría del rectángulo, que es lo único nuevo de este cambio --
// ver el comentario de visibilidad paquete-privada en PadesSigningService.campoFirmaVisible.
class PadesSigningServiceCampoVisibleTest {

    private final PadesSigningService servicio = new PadesSigningService(null, null, null);

    private PdfReader pdfDeUnaPagina() throws Exception {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.LETTER);
        PdfWriter.getInstance(doc, salida);
        doc.open();
        doc.add(new Paragraph("Página de firma."));
        doc.close();
        return new PdfReader(salida.toByteArray());
    }

    @Test
    void elCampoQuedaDentroDeLaPaginaYEnLaBandaInferiorReservada() throws Exception {
        PdfReader reader = pdfDeUnaPagina();

        Rectangle campo = servicio.campoFirmaVisible(reader);
        Rectangle pagina = reader.getPageSizeWithRotation(reader.getNumberOfPages());

        assertTrue(campo.getLeft() >= pagina.getLeft() && campo.getRight() <= pagina.getRight(),
                "El campo debe quedar dentro del ancho de la página.");
        assertTrue(campo.getBottom() >= pagina.getBottom() && campo.getTop() <= pagina.getTop(),
                "El campo debe quedar dentro del alto de la página.");
        // Banda reservada: DocumentoPdfStamper.MARGEN_INFERIOR_RESERVADO_PT = 110pt desde
        // el borde inferior real de la página.
        assertTrue(campo.getTop() <= pagina.getBottom() + DocumentoPdfStamper.MARGEN_INFERIOR_RESERVADO_PT,
                "El campo no debe salirse de la banda inferior reservada para la página de firma.");
        // Por encima de la línea de aviso legal de margen (10pt desde el borde).
        assertTrue(campo.getBottom() > pagina.getBottom() + 10,
                "El campo no debe solaparse con el aviso legal de margen inferior.");

        reader.close();
    }
}
