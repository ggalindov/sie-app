package sie.siejuridicos.documento;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

// Punto 5 de las mejoras pedidas por el usuario sobre el motor "hermano" (UCN): hoy
// (DocumentoPdfStamper) el bloque legal (Ley 527/1999, Decreto 2364/2012) solo vive en las
// páginas de firma NUEVAS que se añaden al final del documento -- si alguien solo ve una
// página suelta del ORIGINAL (impresa, reenviada, fotografiada), no hay ningún indicio ahí
// de que el documento completo del que proviene está firmado electrónicamente. Este
// estampador superpone (overlay, nunca reemplaza contenido) una línea discreta en el
// margen inferior de TODAS las páginas del PDF ya ensamblado (originales + páginas de
// firma), usando PDFBox (no OpenPDF: aquí solo necesitamos dibujar sobre páginas que ya
// existen, PDFBox lo hace sin tener que reabrir el documento como "formulario de firma"
// como sí exige OpenPDF/PdfStamper más adelante en el pipeline).
//
// DEBE correr DESPUÉS de DocumentoPdfStamper.estamparFirmas() (para que las páginas de
// firma también queden con la línea) y ANTES de PadesSigningService.firmarPdf() (para que
// la firma digital PAdES cubra también este overlay -- editarlo o borrarlo después de
// firmar rompe la firma, igual que con las páginas de firma).
//
// Nota sobre las páginas ORIGINALES que sube gerencia: no tenemos control sobre cuánto
// margen real les sobra en la parte inferior. Por eso el texto se coloca lo más pegado
// posible al borde físico de la página (a 10pt del final, fuera del área donde casi
// cualquier documento normal tiene su propio texto de cuerpo) -- mismo criterio que usan
// las plataformas de firma electrónica conocidas al estampar un pie de página sobre un PDF
// arbitrario que no generaron ellas. Para las páginas de firma SÍ tenemos control total
// (ver DocumentoPdfStamper, margen inferior ampliado a propósito a 110pt) así que ahí el
// overlay queda cómodamente dentro de una banda que sabemos vacía.
@Component
public class DocumentoMargenLegalStamper {

    private static final float TAMANO_FUENTE = 8f;
    private static final float DISTANCIA_BORDE_INFERIOR_PT = 10f;
    private static final String TEXTO_BASE =
            "Documento con firma electrónica · PAdES con sello de tiempo · Ley 527 de 1999 y Decreto 2364 de 2012";

    public byte[] estamparMargen(byte[] pdf, String codigoVerificacion) {
        String texto = codigoVerificacion != null && !codigoVerificacion.isBlank()
                ? TEXTO_BASE + " · Código: " + codigoVerificacion
                : TEXTO_BASE;
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            PDFont fuente = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            float anchoTexto = fuente.getStringWidth(texto) / 1000f * TAMANO_FUENTE;
            for (PDPage pagina : documento.getPages()) {
                estamparPagina(documento, pagina, fuente, texto, anchoTexto);
            }
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            documento.save(salida);
            return salida.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo estampar el aviso legal en el margen del documento.", ex);
        }
    }

    // Dibuja en el espacio de coordenadas "visual" de la página (ya compensado por su
    // rotación /Rotate, si tiene una): sin esto, una página escaneada en apaisado (rotada
    // 90°/270°) terminaría con el texto en el borde equivocado o de lado al abrirla en un
    // lector real, que sí respeta /Rotate para mostrarla derecha.
    private void estamparPagina(PDDocument documento, PDPage pagina, PDFont fuente, String texto, float anchoTexto)
            throws IOException {
        PDRectangle caja = pagina.getMediaBox();
        int rotacion = pagina.getRotation();
        boolean rotada90o270 = rotacion == 90 || rotacion == 270;
        float anchoVisual = rotada90o270 ? caja.getHeight() : caja.getWidth();

        try (PDPageContentStream contenido = new PDPageContentStream(
                documento, pagina, PDPageContentStream.AppendMode.APPEND, true, true)) {
            aplicarCompensacionDeRotacion(contenido, caja, rotacion);

            float x = Math.max(DISTANCIA_BORDE_INFERIOR_PT, (anchoVisual - anchoTexto) / 2f);
            float y = DISTANCIA_BORDE_INFERIOR_PT;

            // PDFBox espera los 3 componentes normalizados 0..1, no 0..255 -- gris medio discreto.
            contenido.setNonStrokingColor(90 / 255f, 90 / 255f, 90 / 255f);
            contenido.beginText();
            contenido.setFont(fuente, TAMANO_FUENTE);
            contenido.newLineAtOffset(x, y);
            contenido.showText(texto);
            contenido.endText();
        }
    }

    // Recetario estándar de PDFBox para dibujar "derecho" sobre una página con /Rotate
    // distinto de 0: el contenido que se agrega con AppendMode.APPEND se escribe en el
    // sistema de coordenadas SIN rotar de la página (el /Rotate solo afecta cómo un lector
    // la MUESTRA) -- sin esta compensación, el texto quedaría en el borde o la orientación
    // equivocada en cualquier lector real.
    private void aplicarCompensacionDeRotacion(PDPageContentStream contenido, PDRectangle caja, int rotacion)
            throws IOException {
        switch (rotacion) {
            case 90 -> {
                contenido.transform(Matrix.getRotateInstance(Math.toRadians(90), 0, 0));
                contenido.transform(Matrix.getTranslateInstance(0, -caja.getWidth()));
            }
            case 180 -> {
                contenido.transform(Matrix.getRotateInstance(Math.toRadians(180), 0, 0));
                contenido.transform(Matrix.getTranslateInstance(-caja.getWidth(), -caja.getHeight()));
            }
            case 270 -> {
                contenido.transform(Matrix.getRotateInstance(Math.toRadians(270), 0, 0));
                contenido.transform(Matrix.getTranslateInstance(-caja.getHeight(), 0));
            }
            default -> {
                // Sin rotación: el espacio de coordenadas visual ya coincide con el real.
            }
        }
    }
}
