package sie.siejuridicos.documento.pdf;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;
import sie.siejuridicos.documento.FechaEsCo;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;

// Documento de "Presentación y Autorización" que SIE Jurídicos adjunta a TODO correo de
// solicitud de documento enviado a un despacho judicial (ver CorreoDespachoJudicialService,
// único llamador) -- pedido explícito del usuario (2026-11-04): "revisa como lo hace lupa
// para hacerlo igual". LUPA Jurídica, al no encontrar el documento oficial de una actuación
// por ningún canal automático, le escribe directamente al correo oficial del despacho
// (@cendoj.ramajudicial.gov.co) invocando el artículo 123 del Código General del Proceso
// (consulta del expediente por el apoderado de una parte, sin necesidad de auto que lo
// reconozca) y la Ley 1712 de 2014 (acceso a la información pública) -- este documento es
// la pieza que sustenta por escrito esa misma invocación legal ante el despacho, con los
// datos puntuales del caso y del apoderado que representa a la parte.
@Component
public class DocumentoPresentacionAutorizacionPdfGenerator {

    private static final Color TINTA = new Color(0x21, 0x1C, 0x15);
    private static final Color TINTA_SUAVE = new Color(0x5A, 0x50, 0x3E);
    private static final Color DORADO = new Color(0xB8, 0x8A, 0x1D);
    private static final Color FONDO_AVISO = new Color(0xF3, 0xEE, 0xE2);
    private static final Color BORDE_SUAVE = new Color(0xE3, 0xDC, 0xC8);
    private static final float MARGEN = 56f;

    public record Datos(
            String nombreFirma,
            String sitioWeb,
            String correoContacto,
            String telefono,
            String radicadoId,
            String despachoJudicial,
            String nombreCliente,
            String nombreActuacion,
            String nombreApoderado,
            LocalDateTime generadoEn
    ) {
    }

    private Font fuente(int estilo, float tam, Color color) {
        return new Font(Font.HELVETICA, tam, estilo, color);
    }

    public byte[] generar(Datos datos) {
        try {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.LETTER, MARGEN, MARGEN, MARGEN, MARGEN);
            PdfWriter.getInstance(doc, salida);
            doc.open();

            doc.add(new Paragraph("PRESENTACIÓN Y AUTORIZACIÓN PARA SOLICITUD DE INFORMACIÓN PROCESAL",
                    fuente(Font.BOLD, 15, TINTA)));
            doc.add(espacio(4));
            doc.add(new Paragraph(datos.nombreFirma() + " -- Firma de abogados, Bogotá D.C., Colombia",
                    fuente(Font.NORMAL, 9, TINTA_SUAVE)));
            doc.add(new Paragraph("Documento generado automáticamente por el sistema de gestión judicial de "
                    + datos.nombreFirma() + " el " + FechaEsCo.largo(datos.generadoEn()),
                    fuente(Font.NORMAL, 8.5f, TINTA_SUAVE)));
            doc.add(espacio(14));

            doc.add(seccionTitulo("Destinatario"));
            doc.add(parrafo("Señor(a) Juez / Secretario(a) del despacho judicial de conocimiento."));
            doc.add(espacio(10));

            doc.add(seccionTitulo("Base legal de la solicitud"));
            doc.add(avisoLegal(datos.nombreFirma()));
            doc.add(espacio(12));

            doc.add(seccionTitulo("Datos del proceso"));
            PdfPTable tabla = new PdfPTable(2);
            tabla.setWidthPercentage(100);
            tabla.setWidths(new float[]{1.1f, 2.4f});
            agregarFila(tabla, "Radicado", datos.radicadoId());
            agregarFila(tabla, "Despacho judicial", datos.despachoJudicial());
            agregarFila(tabla, "Parte representada", datos.nombreCliente());
            agregarFila(tabla, "Apoderado judicial", datos.nombreApoderado());
            agregarFila(tabla, "Actuación/pieza procesal solicitada", datos.nombreActuacion());
            doc.add(tabla);
            doc.add(espacio(14));

            doc.add(seccionTitulo("Solicitud"));
            doc.add(parrafo("En virtud de lo expuesto, se solicita respetuosamente al despacho remitir, al "
                    + "correo electrónico desde el cual se origina esta comunicación, copia de la pieza "
                    + "procesal correspondiente a la actuación identificada arriba, de la cual el propio "
                    + "despacho reportó la existencia de documento sin que este se encuentre disponible en "
                    + "el portal público de la Rama Judicial."));
            doc.add(espacio(14));

            doc.add(seccionTitulo("Datos de contacto de la firma"));
            doc.add(parrafo(datos.nombreFirma() + " -- " + datos.sitioWeb()
                    + (datos.correoContacto() != null && !datos.correoContacto().isBlank() ? " -- " + datos.correoContacto() : "")
                    + (datos.telefono() != null && !datos.telefono().isBlank() ? " -- " + datos.telefono() : "")));

            doc.close();
            return salida.toByteArray();
        } catch (DocumentException ex) {
            throw new IllegalStateException("No se pudo generar el documento de presentación y autorización.", ex);
        }
    }

    // Mismo fundamento legal que invoca LUPA Jurídica en sus solicitudes reales a los
    // despachos (confirmado leyendo un ticket real de su módulo "Piezas Procesales"): el
    // artículo 123 del CGP habilita a los apoderados a consultar el expediente de los
    // asuntos en que intervienen sin necesidad de auto que los reconozca, y la Ley 1712 de
    // 2014 respalda el acceso a la información pública de la actuación judicial.
    private PdfPTable avisoLegal(String nombreFirma) {
        PdfPTable tabla = new PdfPTable(1);
        tabla.setWidthPercentage(100);
        PdfPCell celda = new PdfPCell(new Paragraph(
                "La presente solicitud se formula al amparo del artículo 123 del Código General del Proceso, "
                        + "que faculta a los apoderados judiciales de las partes a consultar el expediente de los "
                        + "asuntos en que intervengan sin necesidad de auto que los reconozca, y de la Ley 1712 de "
                        + "2014, sobre transparencia y acceso a la información pública. " + nombreFirma
                        + " actúa como apoderado judicial de una de las partes dentro del proceso identificado en "
                        + "este documento, en los términos del poder conferido para ese asunto puntual.",
                fuente(Font.NORMAL, 9.5f, TINTA)));
        celda.setBackgroundColor(FONDO_AVISO);
        celda.setPadding(12f);
        celda.setBorderColor(BORDE_SUAVE);
        tabla.addCell(celda);
        return tabla;
    }

    private Paragraph seccionTitulo(String texto) {
        Paragraph p = new Paragraph(texto, fuente(Font.BOLD, 11.5f, DORADO));
        p.setSpacingBefore(2f);
        p.setSpacingAfter(5f);
        return p;
    }

    private Paragraph parrafo(String texto) {
        Paragraph p = new Paragraph(texto, fuente(Font.NORMAL, 9.5f, TINTA));
        p.setSpacingAfter(4f);
        return p;
    }

    private void agregarFila(PdfPTable tabla, String etiqueta, String valor) {
        PdfPCell celdaEtiqueta = new PdfPCell(new Paragraph(etiqueta, fuente(Font.BOLD, 9, TINTA_SUAVE)));
        celdaEtiqueta.setBorderColor(BORDE_SUAVE);
        celdaEtiqueta.setPadding(6f);
        celdaEtiqueta.setVerticalAlignment(Element.ALIGN_MIDDLE);

        PdfPCell celdaValor = new PdfPCell(new Paragraph(
                valor != null && !valor.isBlank() ? valor : "(no disponible)", fuente(Font.NORMAL, 9.5f, TINTA)));
        celdaValor.setBorderColor(BORDE_SUAVE);
        celdaValor.setPadding(6f);
        celdaValor.setVerticalAlignment(Element.ALIGN_MIDDLE);

        tabla.addCell(celdaEtiqueta);
        tabla.addCell(celdaValor);
    }

    private Paragraph espacio(float puntos) {
        Paragraph p = new Paragraph(new Chunk(" ", fuente(Font.NORMAL, 1, TINTA)));
        p.setSpacingAfter(puntos);
        return p;
    }
}
