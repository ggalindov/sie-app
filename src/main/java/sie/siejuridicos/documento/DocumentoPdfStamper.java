package sie.siejuridicos.documento;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfCopy;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

// Pieza NUEVA del motor (no existe en el motor de referencia, que regenera el documento
// completo desde una plantilla propia): en vez de generar el PDF desde cero, toma el
// documento que gerencia subió TAL CUAL y le añade una o más páginas nuevas al final con
// un bloque de firma por cada firmante (nombre, rol, documento de identidad, fecha/hora e
// imagen de la firma) -- nunca modifica ni una sola página del contenido original. Lo que
// sostiene la validez legal del documento es la firma PAdES + el hash + el sello de tiempo
// + la cadena de eventos (aplicados DESPUÉS de este estampado, sobre el resultado
// completo, ver DocumentoSellaInfoService), no la posición visual de la firma en la
// página.
@Component
public class DocumentoPdfStamper {

    private static final Font FUENTE_TITULO = new Font(Font.HELVETICA, 16, Font.BOLD);
    private static final Font FUENTE_SUBTITULO = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.GRAY);
    private static final Font FUENTE_NOMBRE = new Font(Font.HELVETICA, 11, Font.BOLD);
    private static final Font FUENTE_TEXTO = new Font(Font.HELVETICA, 9, Font.NORMAL);
    private static final Font FUENTE_SIN_FIRMA = new Font(Font.HELVETICA, 9, Font.ITALIC, Color.GRAY);
    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy, h:mm a", Locale.of("es", "CO"));

    public byte[] estamparFirmas(byte[] pdfOriginal, DocumentoFirma documento, List<DocumentoFirmante> firmantes) {
        try {
            byte[] paginasFirmas = generarPaginasFirmas(documento, firmantes);
            return concatenar(pdfOriginal, paginasFirmas);
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo estampar las páginas de firma en el documento.", ex);
        }
    }

    private byte[] generarPaginasFirmas(DocumentoFirma documento, List<DocumentoFirmante> firmantes) throws Exception {
        Document doc = new Document(PageSize.LETTER, 42, 42, 54, 42);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        PdfWriter.getInstance(doc, salida);
        doc.open();

        doc.add(new Paragraph("Página de firmas", FUENTE_TITULO));
        doc.add(new Paragraph(documento.getNombre(), FUENTE_SUBTITULO));
        if (documento.getCodigoVerificacion() != null) {
            doc.add(new Paragraph("Código de verificación: " + documento.getCodigoVerificacion(), FUENTE_SUBTITULO));
        }
        doc.add(new Paragraph(" "));

        for (DocumentoFirmante firmante : firmantes) {
            doc.add(bloqueFirma(firmante));
            doc.add(new Paragraph(" "));
        }

        doc.close();
        return salida.toByteArray();
    }

    private PdfPTable bloqueFirma(DocumentoFirmante firmante) throws Exception {
        PdfPTable tabla = new PdfPTable(2);
        tabla.setWidthPercentage(100);
        tabla.setWidths(new float[]{1.3f, 1f});
        // Evita que el bloque de UN firmante quede partido entre dos páginas cuando el
        // desborde automático de iText cae justo a la mitad (nombre/firma en una página,
        // el resto en la siguiente) -- si el bloque completo no cabe en lo que queda de la
        // página actual, se mueve entero a la siguiente en vez de partirse.
        tabla.setKeepTogether(true);

        PdfPCell celdaDatos = new PdfPCell();
        celdaDatos.setBorder(Rectangle.BOX);
        celdaDatos.setBorderColor(new Color(0xD9, 0xA9, 0x25));
        celdaDatos.setPadding(10);
        celdaDatos.addElement(new Paragraph(firmante.getNombreCapturado(), FUENTE_NOMBRE));
        celdaDatos.addElement(new Paragraph(rolVisible(firmante), FUENTE_TEXTO));
        if (firmante.getDocumentoIdentidadCapturado() != null && !firmante.getDocumentoIdentidadCapturado().isBlank()) {
            celdaDatos.addElement(new Paragraph("Documento de identidad: " + firmante.getDocumentoIdentidadCapturado(), FUENTE_TEXTO));
        }
        if (firmante.getFirmadoEn() != null) {
            celdaDatos.addElement(new Paragraph("Firmado el " + firmante.getFirmadoEn().format(FORMATO_FECHA), FUENTE_TEXTO));
        } else {
            celdaDatos.addElement(new Paragraph("Pendiente de firma", FUENTE_SIN_FIRMA));
        }
        tabla.addCell(celdaDatos);

        PdfPCell celdaFirma = new PdfPCell();
        celdaFirma.setBorder(Rectangle.BOX);
        celdaFirma.setBorderColor(new Color(0xD9, 0xA9, 0x25));
        celdaFirma.setPadding(10);
        celdaFirma.setHorizontalAlignment(Element.ALIGN_CENTER);
        celdaFirma.setVerticalAlignment(Element.ALIGN_MIDDLE);
        if (firmante.getFirmaImagenPng() != null) {
            Image imagen = Image.getInstance(firmante.getFirmaImagenPng());
            imagen.scaleToFit(170, 64);
            imagen.setAlignment(Element.ALIGN_CENTER);
            celdaFirma.addElement(imagen);
        } else {
            celdaFirma.addElement(new Paragraph("Sin firma", FUENTE_SIN_FIRMA));
        }
        tabla.addCell(celdaFirma);

        return tabla;
    }

    private String rolVisible(DocumentoFirmante firmante) {
        return switch (firmante.getTipoFirmante()) {
            case DocumentoFirmante.TIPO_STAFF_GERENTE -> "Gerente de SIE Jurídicos";
            case DocumentoFirmante.TIPO_STAFF_ABOGADO -> "Abogado de SIE Jurídicos";
            default -> firmante.esContactoEmpresa() ? "Representante de empresa" : "Firmante";
        };
    }

    // Concatena el documento original con las páginas de firma, usando PdfCopy (patrón
    // estándar de OpenPDF/iText2 para fusionar PDFs): nunca reabre ni reescribe ninguna
    // página del original, solo copia sus bytes de página tal cual y añade las nuevas al
    // final.
    private byte[] concatenar(byte[] pdfOriginal, byte[] paginasFirmas) throws Exception {
        PdfReader readerOriginal = new PdfReader(pdfOriginal);
        PdfReader readerFirmas = new PdfReader(paginasFirmas);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(readerOriginal.getPageSizeWithRotation(1));
        PdfCopy copia = new PdfCopy(documento, salida);
        documento.open();
        for (int i = 1; i <= readerOriginal.getNumberOfPages(); i++) {
            copia.addPage(copia.getImportedPage(readerOriginal, i));
        }
        for (int i = 1; i <= readerFirmas.getNumberOfPages(); i++) {
            copia.addPage(copia.getImportedPage(readerFirmas, i));
        }
        documento.close();
        return salida.toByteArray();
    }
}
