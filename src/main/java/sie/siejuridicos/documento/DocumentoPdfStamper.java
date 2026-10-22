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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import sie.siejuridicos.documento.pdf.QrImageUtil;

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
    private static final int LADO_QR_PX = 180;
    private static final float ANCHO_QR_PT = 64f;

    private final String sitioPublicoUrl;

    public DocumentoPdfStamper(@Value("${app.documento.sitio-web}") String sitioPublicoUrl) {
        this.sitioPublicoUrl = sitioPublicoUrl;
    }

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

        doc.add(bloqueBaseLegal(documento));
        doc.add(new Paragraph(" "));

        for (DocumentoFirmante firmante : firmantes) {
            doc.add(bloqueFirma(firmante));
            doc.add(new Paragraph(" "));
        }

        doc.close();
        return salida.toByteArray();
    }

    // Pedido explícito del usuario: que el motor quede lo mejor adaptado posible a la
    // justicia colombiana. Esta página queda DENTRO del mismo PDF que recibe cada firmante
    // (a diferencia del certificado de evidencia, que es un archivo aparte que se puede
    // perder u olvidar reenviar) -- así el documento es autosuficiente como evidencia desde
    // el primer momento: cualquiera que lo abra, incluido un juez, ve de inmediato la base
    // legal exacta y puede escanear el QR para verificarlo de forma independiente, sin
    // depender de que alguien le entregue el certificado de evidencia por separado.
    private PdfPTable bloqueBaseLegal(DocumentoFirma documento) {
        String urlVerificacion = documento.getCodigoVerificacion() != null
                ? sitioPublicoUrl + "/verificar-documento?codigo=" + documento.getCodigoVerificacion()
                : null;

        PdfPTable tabla = new PdfPTable(urlVerificacion != null ? new float[]{1f, 3.4f} : new float[]{1f});
        tabla.setWidthPercentage(100);

        if (urlVerificacion != null) {
            PdfPCell celdaQr = new PdfPCell();
            celdaQr.setBorder(Rectangle.NO_BORDER);
            celdaQr.setPadding(4f);
            celdaQr.setVerticalAlignment(Element.ALIGN_MIDDLE);
            celdaQr.setHorizontalAlignment(Element.ALIGN_CENTER);
            Image qr = QrImageUtil.generar(urlVerificacion, LADO_QR_PX);
            if (qr != null) {
                qr.scaleToFit(ANCHO_QR_PT, ANCHO_QR_PT);
                celdaQr.addElement(qr);
            }
            tabla.addCell(celdaQr);
        }

        PdfPCell celdaTexto = new PdfPCell(new Paragraph(
                "Este documento fue firmado electrónicamente con equivalencia funcional a la firma manuscrita "
                        + "(artículo 7 de la Ley 527 de 1999), con fiabilidad verificable conforme a los atributos "
                        + "del artículo 1 del Decreto 2364 de 2012 (unicidad del firmante, verificabilidad, control "
                        + "exclusivo al momento de firmar y vinculación con el contenido mediante cadena de hashes, "
                        + "firma criptográfica, firma digital PAdES embebida y sello de tiempo RFC 3161), válida en "
                        + "trámites judiciales y extrajudiciales conforme a la Ley 2213 de 2022."
                        + (urlVerificacion != null
                                ? " Escanee el código QR o visite " + urlVerificacion + " para verificar su integridad."
                                : ""),
                FUENTE_SUBTITULO));
        celdaTexto.setBorder(Rectangle.NO_BORDER);
        celdaTexto.setPadding(4f);
        celdaTexto.setVerticalAlignment(Element.ALIGN_MIDDLE);
        tabla.addCell(celdaTexto);

        return tabla;
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
