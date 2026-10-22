package sie.siejuridicos.documento.pdf;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.lowagie.text.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

// Extraído de EvidenceCertificatePdfGenerator (que fue el primero en necesitarlo) para
// reutilizarlo también en DocumentoPdfStamper y CierreCustodiaPdfAppender: los tres anexan
// un QR de verificación al documento, en distintos puntos del flujo de sellado.
public final class QrImageUtil {

    private static final Logger log = LoggerFactory.getLogger(QrImageUtil.class);
    private static final int COLOR_TINTA_QR = 0x00211C15;

    private QrImageUtil() {
    }

    // Nunca lanza: sin QR el documento/certificado sigue siendo válido por sus demás
    // mecanismos (el enlace de texto sigue impreso junto al QR en cada caso de uso), así
    // que un fallo del encoder no debe bloquear la generación de todo el PDF.
    public static Image generar(String contenido, int ladoPx) {
        if (contenido == null || contenido.isBlank()) {
            return null;
        }
        try {
            Map<EncodeHintType, Object> sugerencias = new EnumMap<>(EncodeHintType.class);
            sugerencias.put(EncodeHintType.MARGIN, 1);
            sugerencias.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            BitMatrix matriz = new QRCodeWriter().encode(contenido, BarcodeFormat.QR_CODE, ladoPx, ladoPx, sugerencias);

            BufferedImage imagen = new BufferedImage(ladoPx, ladoPx, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < ladoPx; x++) {
                for (int y = 0; y < ladoPx; y++) {
                    imagen.setRGB(x, y, matriz.get(x, y) ? COLOR_TINTA_QR : 0xFFFFFFFF);
                }
            }
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            ImageIO.write(imagen, "png", salida);
            return Image.getInstance(salida.toByteArray());
        } catch (WriterException | IOException | com.lowagie.text.BadElementException ex) {
            log.warn("No se pudo generar un código QR de verificación: {}", ex.getMessage());
            return null;
        }
    }
}
