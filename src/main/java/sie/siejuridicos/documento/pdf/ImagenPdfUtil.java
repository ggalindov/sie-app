package sie.siejuridicos.documento.pdf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

// Reduce (y reencuadra a JPEG) cualquier foto de evidencia antes de incrustarla en un PDF:
// una foto tomada con la cámara de un celular puede pesar varios MB a resolución completa,
// e incrustarla tal cual en un PDF lo infla y ralentiza sin ganar nada de legibilidad al
// tamaño de impresión de una miniatura de evidencia. Compartido entre
// EvidenceCertificatePdfGenerator y CierreCustodiaPdfAppender para no duplicar la misma
// lógica de reducción dos veces.
public final class ImagenPdfUtil {

    private static final Logger log = LoggerFactory.getLogger(ImagenPdfUtil.class);

    private ImagenPdfUtil() {
    }

    // Nunca lanza: si la imagen no se puede leer/reducir, devuelve null (el llamador debe
    // mostrar "No disponible" en vez de bloquear la generación del PDF por una imagen problemática).
    public static byte[] reducirAMiniatura(byte[] original, int ladoMaximoPx) {
        if (original == null || original.length == 0) {
            return null;
        }
        try {
            BufferedImage origen = ImageIO.read(new ByteArrayInputStream(original));
            if (origen == null) {
                return null;
            }
            int anchoOrigen = origen.getWidth();
            int altoOrigen = origen.getHeight();
            double escala = Math.min(1.0, (double) ladoMaximoPx / Math.max(anchoOrigen, altoOrigen));
            int anchoDestino = Math.max(1, (int) Math.round(anchoOrigen * escala));
            int altoDestino = Math.max(1, (int) Math.round(altoOrigen * escala));

            BufferedImage miniatura = new BufferedImage(anchoDestino, altoDestino, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = miniatura.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, anchoDestino, altoDestino);
            g.drawImage(origen, 0, 0, anchoDestino, altoDestino, null);
            g.dispose();

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            ImageIO.write(miniatura, "jpg", salida);
            return salida.toByteArray();
        } catch (Exception ex) {
            log.warn("No se pudo reducir una foto de evidencia a miniatura: {}", ex.getMessage());
            return null;
        }
    }
}
