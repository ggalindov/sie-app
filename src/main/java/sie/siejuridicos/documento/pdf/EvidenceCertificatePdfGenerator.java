package sie.siejuridicos.documento.pdf;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.lowagie.text.BadElementException;
import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sie.siejuridicos.documento.FechaEsCo;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.util.EnumMap;
import java.util.Map;

// PDF del "Certificado de Evidencia Técnica de Firma Electrónica": un documento SEPARADO del
// PDF original del documento (nunca se toca desde aquí), con su propio membrete simple y
// deliberadamente DISTINTO -- para que nunca pueda confundirse visualmente con el
// documento oficial -- que reúne toda la evidencia técnica capturada de cada firmante
// (incluidas las imágenes: firma manuscrita, foto del documento de identidad y foto de
// verificación humana), el estado de integridad criptográfica del documento y un código QR
// de verificación.
@Component
public class EvidenceCertificatePdfGenerator {

    private static final Logger log = LoggerFactory.getLogger(EvidenceCertificatePdfGenerator.class);

    private static final Color TINTA = new Color(0x21, 0x1C, 0x15);
    private static final Color TINTA_SUAVE = new Color(0x5A, 0x50, 0x3E);
    private static final Color DORADO = new Color(0xB8, 0x8A, 0x1D);
    private static final Color VERDE_OK = new Color(0x2E, 0x6B, 0x4E);
    private static final Color ROJO_ALERTA = new Color(0x8C, 0x2E, 0x3C);
    private static final Color FONDO_AVISO = new Color(0xF3, 0xEE, 0xE2);
    private static final Color FONDO_IMAGEN = new Color(0xFA, 0xF7, 0xF0);
    private static final Color BORDE_SUAVE = new Color(0xE3, 0xDC, 0xC8);
    private static final float MARGEN = 48f;
    private static final float MARGEN_INFERIOR = 64f;

    // Lado máximo (px) al que se reduce cualquier foto capturada por cámara antes de
    // incrustarla: las fotos de identidad/verificación humana pueden pesar varios MB a
    // resolución completa de cámara de celular. La firma manuscrita NO pasa por aquí: ya
    // llega recortada/escalada a 640x240 desde su captura.
    private static final int LADO_MAXIMO_MINIATURA_PX = 480;
    private static final float ANCHO_COLUMNA_IMAGEN = 150f;
    private static final float ALTO_COLUMNA_IMAGEN = 110f;
    private static final int LADO_QR_PX = 220;
    private static final float ANCHO_QR_PT = 80f;

    private Font fuente(int estilo, float tam, Color color) {
        return new Font(Font.HELVETICA, tam, estilo, color);
    }

    public byte[] generar(EvidenceCertificateData datos) {
        try {
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.LETTER, MARGEN, MARGEN, MARGEN, MARGEN_INFERIOR);
            PdfWriter writer = PdfWriter.getInstance(doc, salida);
            writer.setPageEvent(new PiePagina());
            doc.open();

            doc.add(new Paragraph("CERTIFICADO DE EVIDENCIA TÉCNICA DE FIRMA ELECTRÓNICA",
                    fuente(Font.BOLD, 16, TINTA)));
            doc.add(espacio(4));
            doc.add(new Paragraph("SIE Jurídicos -- Firma de abogados, Bogotá D.C., Colombia",
                    fuente(Font.NORMAL, 9, TINTA_SUAVE)));
            doc.add(new Paragraph("Certificado generado el " + FechaEsCo.largo(datos.certificadoGeneradoEn()),
                    fuente(Font.NORMAL, 8.5f, TINTA_SUAVE)));
            doc.add(espacio(10));

            doc.add(avisoLegal());
            doc.add(espacio(12));

            doc.add(seccionTitulo("Documento certificado"));
            doc.add(filaEtiquetaValor("Nombre", datos.nombreDocumento()));
            doc.add(filaEtiquetaValor("Número de secuencia", datos.numeroSecuencia() != null ? "#" + datos.numeroSecuencia() : null));
            doc.add(filaEtiquetaValor("Fecha del documento",
                    datos.fechaDocumento() != null ? FechaEsCo.largo(datos.fechaDocumento()) : null));
            doc.add(filaEtiquetaValor("Código de verificación", datos.codigoVerificacion()));
            doc.add(filaEtiquetaValor("Estado", datos.estadoDocumento()));
            doc.add(filaEtiquetaValor("Hash SHA-256 del contenido", partirLargo(datos.hashContenido())));
            doc.add(espacio(10));

            doc.add(seccionTitulo("Integridad criptográfica"));
            doc.add(filaEstado("Firma criptográfica de aplicación (Ed25519)", datos.firmaAppValida(),
                    datos.firmaAppKid() != null ? "Llave: " + datos.firmaAppKid() : "Documento aún no sellado."));
            doc.add(filaEstado("Sello de tiempo RFC 3161", datos.selloTiempoDocumentoAplicado(),
                    datos.selloTiempoDocumentoAplicado()
                            ? "Emitido por " + datos.selloTiempoDocumentoUrl() + " el "
                                    + FechaEsCo.largo(datos.selloTiempoDocumentoEn())
                            : "No se obtuvo un sello de tiempo externo para este documento."));
            doc.add(filaEstado("Cadena de hashes del timeline de eventos", datos.cadenaEventosIntegra(),
                    detalleCadenaEventos(datos)));
            doc.add(espacio(10));

            doc.add(seccionTitulo("Firmantes"));
            for (EvidenceCertificateData.FirmanteData firmante : datos.firmantes()) {
                doc.add(bloqueFirmante(firmante, datos.detalleTecnicoCompleto()));
                doc.add(espacio(8));
            }

            if (datos.urlVerificacion() != null) {
                doc.add(espacio(6));
                doc.add(bloqueVerificacionQr(datos));
            }

            doc.close();
            return salida.toByteArray();
        } catch (DocumentException ex) {
            throw new IllegalStateException("No se pudo generar el certificado de evidencia.", ex);
        }
    }

    // Para el firmante, nunca se revela el detalle técnico exacto (p.ej. en qué número de
    // secuencia del timeline se rompió la cadena de hashes) -- eso es información forense
    // interna que solo necesita el staff en una eventual disputa, y exponerla no le aporta
    // nada al firmante, solo le da pistas a quien quisiera manipular el documento.
    private String detalleCadenaEventos(EvidenceCertificateData datos) {
        if (datos.detalleTecnicoCompleto()) {
            return datos.cadenaEventosDetalle() != null ? datos.cadenaEventosDetalle() : "Sin observaciones.";
        }
        return datos.cadenaEventosIntegra()
                ? "Sin observaciones."
                : "Se detectó una inconsistencia en el registro de eventos de este documento. SIE Jurídicos "
                        + "rastrea la integridad de este documento de forma continua y ya tiene conocimiento de ello.";
    }

    private Paragraph avisoLegal() {
        PdfPTable tabla = new PdfPTable(1);
        tabla.setWidthPercentage(100);
        PdfPCell celda = new PdfPCell(new Paragraph(
                "Este documento cuenta con protección técnica integral: cadena de hashes del timeline, firma "
                        + "criptográfica de aplicación, firma digital PAdES embebida y sello de tiempo RFC 3161. "
                        + "Cualquier intento de alteración del contenido, del cifrado o de la firma electrónica "
                        + "aplicada después de firmado queda expuesto automáticamente por estos mecanismos. "
                        + "SIE Jurídicos puede confirmar en cualquier momento si un documento firmado "
                        + "electrónicamente ha sido modificado desde su emisión. Firma electrónica con evidencia "
                        + "técnica, amparada por la Ley 527 de 1999, el Decreto 2364 de 2012 y la Ley 2213 de 2022.",
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
        Paragraph p = new Paragraph(texto, fuente(Font.BOLD, 12, DORADO));
        p.setSpacingBefore(4f);
        p.setSpacingAfter(4f);
        return p;
    }

    private Paragraph filaEtiquetaValor(String etiqueta, String valor) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(etiqueta + ": ", fuente(Font.BOLD, 9.5f, TINTA)));
        p.add(new Chunk(valor != null ? valor : "(no disponible)", fuente(Font.NORMAL, 9.5f, TINTA)));
        p.setSpacingAfter(2f);
        return p;
    }

    private Paragraph filaEstado(String etiqueta, boolean ok, String detalle) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(etiqueta + ": ", fuente(Font.BOLD, 9.5f, TINTA)));
        p.add(new Chunk(ok ? "OK" : "REVISAR", fuente(Font.BOLD, 9.5f, ok ? VERDE_OK : ROJO_ALERTA)));
        p.setSpacingAfter(1f);
        Paragraph detalleP = new Paragraph(detalle, fuente(Font.NORMAL, 8.5f, TINTA_SUAVE));
        detalleP.setSpacingAfter(4f);
        Paragraph combinado = new Paragraph();
        combinado.add(p);
        combinado.add(detalleP);
        return combinado;
    }

    private PdfPTable bloqueFirmante(EvidenceCertificateData.FirmanteData f, boolean detalleTecnicoCompleto) {
        PdfPTable tabla = new PdfPTable(1);
        tabla.setWidthPercentage(100);
        Paragraph contenido = new Paragraph();
        contenido.add(new Paragraph(f.nombreCapturado() + " (" + f.tipoFirmante() + ") -- " + f.estado(),
                fuente(Font.BOLD, 10.5f, TINTA)));
        agregarLinea(contenido, "Documento de identidad", f.documentoIdentidadCapturado());
        agregarLinea(contenido, "Enlace de firma generado el", f.creadoEn() != null ? FechaEsCo.largo(f.creadoEn()) : null);
        agregarLinea(contenido, "Enlace vigente hasta", f.tokenExpiraEn() != null ? FechaEsCo.largo(f.tokenExpiraEn()) : null);
        agregarLinea(contenido, "Firmado el", f.firmadoEn() != null ? FechaEsCo.largo(f.firmadoEn()) : null);
        if (detalleTecnicoCompleto) {
            agregarLinea(contenido, "IP de captura", f.ipCaptura());
            agregarLinea(contenido, "Navegador/dispositivo (user-agent)", f.userAgentCaptura());
            agregarLinea(contenido, "Huella de dispositivo (no biométrica)", f.huellaDispositivo());
            agregarLinea(contenido, "Señales de proxy detectadas", f.senalesProxyDetectadas() != null ? f.senalesProxyDetectadas() : "Ninguna");
        } else {
            // Pedido explícito del usuario: al firmante no se le detallan los mecanismos
            // forenses propios -- solo que existen y que SIE Jurídicos los rastrea 24/7.
            agregarLinea(contenido, "Trazabilidad técnica", "Capturada y verificada internamente bajo monitoreo continuo de integridad.");
        }
        String geoTexto = f.geolocalizacionRechazada() ? "Permiso rechazado por el firmante" : f.geolocalizacionTexto();
        if (!f.geolocalizacionRechazada() && f.geolocalizacionCapturadaEn() != null) {
            geoTexto = (geoTexto != null ? geoTexto : "") + " -- capturada el " + FechaEsCo.largo(f.geolocalizacionCapturadaEn());
        }
        agregarLinea(contenido, "Geolocalización", geoTexto);
        agregarLinea(contenido, "Consentimiento aceptado el",
                f.consentimientoAceptadoEn() != null ? FechaEsCo.largo(f.consentimientoAceptadoEn()) : null);
        agregarLinea(contenido, "Verificación humana (foto de seguridad, no reconocimiento facial)",
                f.verificacionHumanaCapturada() ? "Capturada" + (detalleTecnicoCompleto ? " -- hash " + partirLargo(f.verificacionHumanaHash()) : "") : "No aplica");
        if (detalleTecnicoCompleto) {
            agregarLinea(contenido, "Hash de evidencia (SHA-256)", partirLargo(f.hashEvidencia()));
        }
        agregarLinea(contenido, "Sello de tiempo RFC 3161",
                f.selloTiempoAplicado()
                        ? "Emitido por " + f.selloTiempoUrl() + " (validado: " + (f.selloTiempoValido() ? "SÍ" : "NO") + ")"
                        : "No se obtuvo sello de tiempo para esta firma.");
        if (f.consentimientoTexto() != null && !f.consentimientoTexto().isBlank()) {
            Paragraph consentimiento = new Paragraph();
            consentimiento.setSpacingBefore(3f);
            consentimiento.add(new Chunk("Texto exacto del consentimiento aceptado:\n", fuente(Font.BOLD, 8f, TINTA)));
            consentimiento.add(new Chunk(f.consentimientoTexto(), fuente(Font.NORMAL, 7.5f, TINTA_SUAVE)));
            contenido.add(consentimiento);
        }

        PdfPCell celdaTexto = new PdfPCell(contenido);
        celdaTexto.setPadding(8f);
        celdaTexto.setBorderColor(BORDE_SUAVE);
        tabla.addCell(celdaTexto);

        PdfPTable filaImagenes = filaImagenesFirmante(f);
        if (filaImagenes != null) {
            PdfPCell celdaImagenes = new PdfPCell(filaImagenes);
            celdaImagenes.setPadding(8f);
            celdaImagenes.setPaddingTop(0f);
            celdaImagenes.setBorderColor(BORDE_SUAVE);
            tabla.addCell(celdaImagenes);
        }
        return tabla;
    }

    // Fila de 3 miniaturas (firma, documento de identidad, verificación humana); cada
    // celda muestra la imagen si existe o un texto "No disponible" si esa evidencia no se capturó.
    private PdfPTable filaImagenesFirmante(EvidenceCertificateData.FirmanteData f) {
        boolean hayAlguna = f.firmaImagenPng() != null || f.documentoIdentidadImagen() != null || f.fotoVerificacionHumana() != null;
        if (!hayAlguna) {
            return null;
        }
        PdfPTable tabla = new PdfPTable(3);
        tabla.setWidthPercentage(100);
        tabla.addCell(celdaImagen("Firma manuscrita", f.firmaImagenPng(), false));
        tabla.addCell(celdaImagen("Documento de identidad", f.documentoIdentidadImagen(), true));
        tabla.addCell(celdaImagen("Verificación humana", f.fotoVerificacionHumana(), true));
        return tabla;
    }

    private PdfPCell celdaImagen(String titulo, byte[] bytesOriginales, boolean reducirComoFoto) {
        PdfPCell celda = new PdfPCell();
        celda.setPadding(6f);
        celda.setBackgroundColor(FONDO_IMAGEN);
        celda.setBorderColor(BORDE_SUAVE);
        celda.setMinimumHeight(ALTO_COLUMNA_IMAGEN + 16f);
        celda.setHorizontalAlignment(Element.ALIGN_CENTER);
        celda.setVerticalAlignment(Element.ALIGN_MIDDLE);

        Paragraph tituloP = new Paragraph(titulo, fuente(Font.BOLD, 7, TINTA_SUAVE));
        tituloP.setAlignment(Element.ALIGN_CENTER);
        tituloP.setSpacingAfter(3f);
        celda.addElement(tituloP);

        Image imagen = cargarImagenParaCelda(bytesOriginales, reducirComoFoto);
        if (imagen != null) {
            imagen.scaleToFit(ANCHO_COLUMNA_IMAGEN, ALTO_COLUMNA_IMAGEN);
            imagen.setAlignment(Element.ALIGN_CENTER);
            celda.addElement(imagen);
        } else {
            Paragraph vacio = new Paragraph("No disponible", fuente(Font.NORMAL, 7.5f, TINTA_SUAVE));
            vacio.setAlignment(Element.ALIGN_CENTER);
            celda.addElement(vacio);
        }
        return celda;
    }

    // Nunca lanza: una imagen que no se puede leer/incrustar no debe bloquear el
    // certificado -- simplemente se muestra como "No disponible" en su celda.
    private Image cargarImagenParaCelda(byte[] bytesOriginales, boolean reducirComoFoto) {
        if (bytesOriginales == null || bytesOriginales.length == 0) {
            return null;
        }
        try {
            byte[] bytesAIncrustar = reducirComoFoto
                    ? ImagenPdfUtil.reducirAMiniatura(bytesOriginales, LADO_MAXIMO_MINIATURA_PX)
                    : bytesOriginales;
            if (bytesAIncrustar == null) {
                return null;
            }
            return Image.getInstance(bytesAIncrustar);
        } catch (Exception ex) {
            log.warn("No se pudo incrustar una imagen de evidencia en el certificado: {}", ex.getMessage());
            return null;
        }
    }

    private PdfPTable bloqueVerificacionQr(EvidenceCertificateData datos) {
        PdfPTable tabla = new PdfPTable(new float[]{1f, 4.3f});
        tabla.setWidthPercentage(100);

        PdfPCell celdaQr = new PdfPCell();
        celdaQr.setPadding(4f);
        celdaQr.setVerticalAlignment(Element.ALIGN_MIDDLE);
        Image qr = generarQr(datos.urlVerificacion());
        if (qr != null) {
            qr.scaleToFit(ANCHO_QR_PT, ANCHO_QR_PT);
            celdaQr.addElement(qr);
        }

        PdfPCell celdaTexto = new PdfPCell(new Paragraph(
                "Verificación independiente: escanea el código QR o visita el enlace para confirmar la "
                        + "autenticidad de este documento directamente en los sistemas de SIE Jurídicos.\n\n"
                        + datos.urlVerificacion(),
                fuente(Font.NORMAL, 8, TINTA_SUAVE)));
        celdaTexto.setVerticalAlignment(Element.ALIGN_MIDDLE);
        celdaTexto.setPadding(6f);

        tabla.addCell(celdaQr);
        tabla.addCell(celdaTexto);
        return tabla;
    }

    // Nunca lanza: sin QR el certificado sigue siendo válido (el enlace de texto sigue
    // impreso en las secciones anteriores), así que un fallo del encoder no debe bloquear
    // la generación.
    private Image generarQr(String contenido) {
        if (contenido == null || contenido.isBlank()) {
            return null;
        }
        try {
            Map<EncodeHintType, Object> sugerencias = new EnumMap<>(EncodeHintType.class);
            sugerencias.put(EncodeHintType.MARGIN, 1);
            sugerencias.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            BitMatrix matriz = new QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, LADO_QR_PX, LADO_QR_PX, sugerencias);

            BufferedImage imagen = new BufferedImage(LADO_QR_PX, LADO_QR_PX, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < LADO_QR_PX; x++) {
                for (int y = 0; y < LADO_QR_PX; y++) {
                    imagen.setRGB(x, y, matriz.get(x, y) ? 0x00211C15 : 0xFFFFFFFF);
                }
            }
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            ImageIO.write(imagen, "png", salida);
            return Image.getInstance(salida.toByteArray());
        } catch (WriterException | IOException | BadElementException ex) {
            log.warn("No se pudo generar el código QR de verificación del certificado: {}", ex.getMessage());
            return null;
        }
    }

    private void agregarLinea(Paragraph contenedor, String etiqueta, String valor) {
        Paragraph p = new Paragraph();
        p.add(new Chunk(etiqueta + ": ", fuente(Font.BOLD, 8.5f, TINTA)));
        p.add(new Chunk(valor != null && !valor.isBlank() ? valor : "(no disponible)",
                fuente(Font.NORMAL, 8.5f, TINTA_SUAVE)));
        contenedor.add(p);
    }

    private Paragraph espacio(float puntos) {
        Paragraph p = new Paragraph(" ");
        p.setSpacingAfter(puntos);
        return p;
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

    // Pie de página con el número de página actual. Deliberadamente NO muestra "de Y" (el
    // total de páginas): la técnica estándar de plantilla diferida de OpenPDF para el
    // total no siempre refleja el número real -- mostrar un total incorrecto en un
    // certificado con valor probatorio es peor que no mostrar total.
    private static final class PiePagina extends PdfPageEventHelper {
        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            com.lowagie.text.pdf.BaseFont baseFont;
            try {
                baseFont = com.lowagie.text.pdf.BaseFont.createFont(com.lowagie.text.pdf.BaseFont.HELVETICA,
                        com.lowagie.text.pdf.BaseFont.WINANSI, com.lowagie.text.pdf.BaseFont.NOT_EMBEDDED);
            } catch (Exception ex) {
                return;
            }
            PdfContentByte cb = writer.getDirectContent();
            String textoPagina = "Página " + writer.getPageNumber();
            float anchoTexto = baseFont.getWidthPoint(textoPagina, 8f);
            float x = document.right() - anchoTexto;
            float y = document.bottom() - 24f;

            cb.saveState();
            cb.beginText();
            cb.setFontAndSize(baseFont, 8f);
            cb.setTextMatrix(x, y);
            cb.showText(textoPagina);
            cb.endText();
            cb.restoreState();
        }
    }
}
