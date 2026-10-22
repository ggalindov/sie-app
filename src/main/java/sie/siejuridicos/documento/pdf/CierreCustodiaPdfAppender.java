package sie.siejuridicos.documento.pdf;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfCopy;
import com.lowagie.text.pdf.PdfImportedPage;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sie.siejuridicos.documento.FechaEsCo;

import java.awt.Color;
import java.io.ByteArrayOutputStream;

// Anexa una "Hoja de Cierre de Custodia" (Closing Sheet, al estilo DocuSign/Adobe Sign)
// como ÚLTIMA página del documento oficial ya sellado -- un resumen de auditoría integrado
// en el MISMO archivo que se descarga/remite, para que quien lo reciba no tenga que buscar
// el certificado de evidencia por separado para ver quién firmó, cómo se verificó su
// identidad y si la cadena de integridad sigue intacta. No modifica el contenido de las
// páginas originales: las importa tal cual con PdfCopy y agrega la hoja nueva al final.
// Nunca lanza -- si algo falla al anexar, se registra y se devuelve el PDF ORIGINAL sin
// modificar, para que un problema en esta capa adicional no bloquee jamás la descarga.
@Component
public class CierreCustodiaPdfAppender {

    private static final Logger log = LoggerFactory.getLogger(CierreCustodiaPdfAppender.class);

    private static final Color TINTA = new Color(0x21, 0x1C, 0x15);
    private static final Color TINTA_SUAVE = new Color(0x5A, 0x50, 0x3E);
    private static final Color DORADO = new Color(0xB8, 0x8A, 0x1D);
    private static final Color FONDO_AVISO = new Color(0xF3, 0xEE, 0xE2);
    private static final Color FONDO_FIRMANTE = new Color(0xFA, 0xF7, 0xF0);
    private static final Color BORDE_SUAVE = new Color(0xE3, 0xDC, 0xC8);
    private static final float MARGEN = 42f;
    private static final int LADO_MINIATURA_PX = 300;
    private static final float ANCHO_FOTO_PT = 60f;
    private static final float ALTO_FOTO_PT = 60f;

    private Font fuente(int estilo, float tam, Color color) {
        return new Font(Font.HELVETICA, tam, estilo, color);
    }

    public byte[] anexar(byte[] pdfOriginal, HojaCierreCustodiaData datos) {
        try {
            byte[] hojaCierre = generarHojaCierre(datos);
            return combinar(pdfOriginal, hojaCierre);
        } catch (Exception ex) {
            log.warn("No se pudo anexar la hoja de cierre de custodia al documento '{}': {}",
                    datos.nombreDocumento(), ex.getMessage(), ex);
            return pdfOriginal;
        }
    }

    private byte[] combinar(byte[] pdfOriginal, byte[] hojaCierre) throws Exception {
        Document documentoCombinado = new Document();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        PdfCopy copiadora = new PdfCopy(documentoCombinado, salida);
        documentoCombinado.open();

        PdfReader lectorOriginal = new PdfReader(pdfOriginal);
        int totalPaginasOriginal = lectorOriginal.getNumberOfPages();
        for (int i = 1; i <= totalPaginasOriginal; i++) {
            PdfImportedPage pagina = copiadora.getImportedPage(lectorOriginal, i);
            copiadora.addPage(pagina);
        }

        // Bug real encontrado en auditoría (2026-10-22): con muchos firmantes, el contenido
        // de generarHojaCierre() se desborda a una 2a/3a página (iText pagina solo al
        // llenarse la primera, ver doc.add() más abajo). Importar solo la página 1 perdía en
        // silencio los firmantes que caían en las páginas siguientes -- nunca lanzaba error,
        // solo faltaban datos en el PDF final. Ahora se importan TODAS las páginas generadas.
        PdfReader lectorCierre = new PdfReader(hojaCierre);
        for (int i = 1; i <= lectorCierre.getNumberOfPages(); i++) {
            PdfImportedPage paginaCierre = copiadora.getImportedPage(lectorCierre, i);
            copiadora.addPage(paginaCierre);
        }

        documentoCombinado.close();
        return salida.toByteArray();
    }

    private byte[] generarHojaCierre(HojaCierreCustodiaData datos) throws DocumentException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.LETTER, MARGEN, MARGEN, MARGEN, MARGEN);
        PdfWriter writer = PdfWriter.getInstance(doc, salida);
        writer.setPageEvent(new PiePaginaAnexo());
        doc.open();

        doc.add(new Paragraph("HOJA DE CIERRE DE CUSTODIA", fuente(Font.BOLD, 15, TINTA)));
        doc.add(espacio(3));
        doc.add(new Paragraph("Resumen integrado de auditoría y firma electrónica -- SIE Jurídicos, Bogotá D.C., Colombia",
                fuente(Font.NORMAL, 8.5f, TINTA_SUAVE)));
        doc.add(new Paragraph("Hoja generada el " + FechaEsCo.largo(datos.cierreGeneradoEn()),
                fuente(Font.NORMAL, 8, TINTA_SUAVE)));
        doc.add(espacio(8));

        doc.add(rastroFirmaElectronica(datos));
        doc.add(espacio(8));

        doc.add(seccionTitulo("Documento"));
        doc.add(filaEtiquetaValor("Nombre", datos.nombreDocumento()));
        doc.add(filaEtiquetaValor("Código de verificación", datos.codigoVerificacion()));
        doc.add(espacio(4));

        Paragraph redireccion = new Paragraph(
                "El hash SHA-256 definitivo, el sello de tiempo RFC 3161 y la firma digital PAdES/PKCS7 "
                        + "aplicados sobre la versión final de este documento (que incluye esta misma hoja) se "
                        + "pueden consultar en cualquier momento, junto con el detalle técnico completo de cada "
                        + "firma, mediante el código de verificación impreso arriba"
                        + (datos.urlVerificacion() != null ? (" en " + datos.urlVerificacion()) : "") + ".",
                fuente(Font.NORMAL, 8, TINTA_SUAVE));
        redireccion.setSpacingAfter(6f);
        doc.add(redireccion);

        doc.add(seccionTitulo("Firmantes"));
        for (HojaCierreCustodiaData.FirmanteResumen firmante : datos.firmantes()) {
            doc.add(bloqueFirmante(firmante));
            doc.add(espacio(4));
        }

        doc.close();
        return salida.toByteArray();
    }

    // El "rastro" explícito de quién firmó, con el lenguaje de presunción de validez legal
    // (Art. 5 y 7 de la Ley 527 de 1999, Decreto 2364 de 2012, Ley 2213 de 2022): un lector
    // no tiene que interpretar campos técnicos dispersos para saber que el documento fue
    // firmado electrónicamente y por quién -- queda dicho en una sola frase.
    private Paragraph rastroFirmaElectronica(HojaCierreCustodiaData datos) {
        StringBuilder nombres = new StringBuilder();
        for (int i = 0; i < datos.firmantes().size(); i++) {
            if (i > 0) {
                nombres.append(i == datos.firmantes().size() - 1 ? " y " : ", ");
            }
            HojaCierreCustodiaData.FirmanteResumen f = datos.firmantes().get(i);
            nombres.append(f.nombreCapturado());
            if (f.documentoIdentidadCapturado() != null) {
                nombres.append(" (").append(f.documentoIdentidadCapturado()).append(")");
            }
        }

        PdfPTable tabla = new PdfPTable(1);
        tabla.setWidthPercentage(100);
        PdfPCell celda = new PdfPCell(new Paragraph(
                "Este documento fue firmado electrónicamente por: " + nombres + ". La firma electrónica aplicada "
                        + "goza de presunción de validez legal y equivale a la firma manuscrita conforme al "
                        + "artículo 7 de la Ley 527 de 1999, el Decreto 2364 de 2012 y la Ley 2213 de 2022. El "
                        + "mecanismo de identificación exacto de cada firmante -- verificación de su número de "
                        + "documento, código de un solo uso, geolocalización cuando estuvo disponible, "
                        + "fotografía de su documento de identidad y fotografía de verificación humana tomada "
                        + "al momento de firmar -- queda detallado por firmante en esta misma hoja y en el "
                        + "certificado de evidencia técnica correspondiente.",
                fuente(Font.BOLD, 8.5f, TINTA)));
        celda.setBackgroundColor(FONDO_AVISO);
        celda.setPadding(10f);
        celda.setBorderColor(new Color(0xD8, 0xCE, 0xB0));
        tabla.addCell(celda);
        Paragraph contenedor = new Paragraph();
        contenedor.add(tabla);
        return contenedor;
    }

    private Paragraph seccionTitulo(String texto) {
        Paragraph p = new Paragraph(texto, fuente(Font.BOLD, 11, DORADO));
        p.setSpacingBefore(3f);
        p.setSpacingAfter(3f);
        return p;
    }

    private Paragraph filaEtiquetaValor(String etiqueta, String valor) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(etiqueta + ": ", fuente(Font.BOLD, 9, TINTA)));
        p.add(new Chunk(valor != null ? valor : "(no disponible)", fuente(Font.NORMAL, 9, TINTA)));
        p.setSpacingAfter(2f);
        return p;
    }

    private PdfPTable bloqueFirmante(HojaCierreCustodiaData.FirmanteResumen f) {
        PdfPTable tabla = new PdfPTable(new float[]{1f, 4f});
        tabla.setWidthPercentage(100);
        // Mismo criterio que DocumentoPdfStamper: el bloque completo de un firmante se mueve
        // entero a la siguiente página si no cabe, nunca se parte a la mitad.
        tabla.setKeepTogether(true);

        PdfPCell celdaFoto = new PdfPCell();
        celdaFoto.setBackgroundColor(FONDO_FIRMANTE);
        celdaFoto.setBorderColor(BORDE_SUAVE);
        celdaFoto.setPadding(4f);
        celdaFoto.setHorizontalAlignment(Element.ALIGN_CENTER);
        celdaFoto.setVerticalAlignment(Element.ALIGN_MIDDLE);
        Image foto = cargarFoto(f.fotoVerificacionHumana());
        if (foto != null) {
            foto.scaleToFit(ANCHO_FOTO_PT, ALTO_FOTO_PT);
            celdaFoto.addElement(foto);
        } else {
            Paragraph sinFoto = new Paragraph("Sin foto", fuente(Font.NORMAL, 7, TINTA_SUAVE));
            sinFoto.setAlignment(Element.ALIGN_CENTER);
            celdaFoto.addElement(sinFoto);
        }
        tabla.addCell(celdaFoto);

        Paragraph contenido = new Paragraph();
        contenido.add(new Paragraph(f.nombreCapturado() + " (" + f.tipoFirmante() + ")",
                fuente(Font.BOLD, 9.5f, TINTA)));
        agregarLinea(contenido, "Documento", f.documentoIdentidadCapturado());
        agregarLinea(contenido, "Firmado el", f.firmadoEn() != null ? FechaEsCo.largo(f.firmadoEn()) : null);
        agregarLinea(contenido, "IP", f.ipCaptura());
        agregarLinea(contenido, "Dispositivo", f.userAgentCaptura());
        agregarLinea(contenido, "Señales de proxy", f.senalesProxyDetectadas() != null ? f.senalesProxyDetectadas() : "Ninguna");
        agregarLinea(contenido, "Hash de evidencia", partirLargo(f.hashEvidencia()));
        PdfPCell celdaTexto = new PdfPCell(contenido);
        celdaTexto.setBackgroundColor(FONDO_FIRMANTE);
        celdaTexto.setBorderColor(BORDE_SUAVE);
        celdaTexto.setPadding(6f);
        tabla.addCell(celdaTexto);

        return tabla;
    }

    private Image cargarFoto(byte[] original) {
        byte[] reducida = ImagenPdfUtil.reducirAMiniatura(original, LADO_MINIATURA_PX);
        if (reducida == null) {
            return null;
        }
        try {
            return Image.getInstance(reducida);
        } catch (Exception ex) {
            log.warn("No se pudo incrustar la foto de un firmante en la hoja de cierre: {}", ex.getMessage());
            return null;
        }
    }

    private void agregarLinea(Paragraph contenedor, String etiqueta, String valor) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(etiqueta + ": ", fuente(Font.BOLD, 7.5f, TINTA)));
        p.add(new Chunk(valor != null && !valor.isBlank() ? valor : "(no disponible)",
                fuente(Font.NORMAL, 7.5f, TINTA_SUAVE)));
        contenedor.add(p);
    }

    private Paragraph espacio(float puntos) {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(puntos);
        return p;
    }

    // Pie propio de la hoja de cierre: NO usa numeración "Página X de Y" a propósito, para
    // no competir ni contradecir el pie ya horneado en las páginas del cuerpo original.
    private static final class PiePaginaAnexo extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            BaseFont baseFont;
            try {
                baseFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            } catch (Exception ex) {
                return;
            }
            PdfContentByte contenido = writer.getDirectContent();
            contenido.saveState();
            contenido.setColorFill(TINTA_SUAVE);
            contenido.beginText();
            contenido.setFontAndSize(baseFont, 7f);
            contenido.showTextAligned(Element.ALIGN_CENTER,
                    "Anexo -- Hoja de Cierre de Custodia (no forma parte de la numeración de páginas del cuerpo del documento)",
                    PageSize.LETTER.getWidth() / 2, MARGEN / 2f, 0);
            contenido.endText();
            contenido.restoreState();
        }
    }

    private static String partirLargo(String texto) {
        if (texto == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < texto.length(); i += 32) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(texto, i, Math.min(i + 32, texto.length()));
        }
        return sb.toString();
    }
}
