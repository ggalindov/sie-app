package sie.siejuridicos.documento.pdf;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

// Recorta una imagen de firma a su contenido real (ignorando el fondo blanco/transparente
// que suele rodear una firma capturada en un <canvas>) y la reencuadra en un lienzo nuevo,
// aplicando un factor de zoom. Sin esto, una firma con mucho espacio en blanco alrededor
// del trazo se vería minúscula al escalar la imagen COMPLETA (trazo + espacio vacío)
// dentro de su celda en la página de firmas del PDF -- el recorte hace que el trazo mismo
// llene el espacio disponible. Nunca lanza: si algo falla o la imagen está totalmente en
// blanco, devuelve los bytes originales sin modificar.
public final class SignatureImageProcessor {

    private static final float PADDING_FRACCION = 0.10f;
    private static final float ZOOM_MINIMO = 0.5f;
    private static final float ZOOM_MAXIMO = 2.0f;

    private SignatureImageProcessor() {
    }

    public static byte[] recortarYEscalar(byte[] pngOriginal, float zoom, int anchoLienzo, int altoLienzo) {
        if (pngOriginal == null || pngOriginal.length == 0) {
            return pngOriginal;
        }
        try {
            BufferedImage origen = ImageIO.read(new ByteArrayInputStream(pngOriginal));
            if (origen == null) {
                return pngOriginal;
            }
            Rectangle caja = detectarCajaContenido(origen);
            if (caja == null) {
                return pngOriginal;
            }

            float zoomAplicado = Math.max(ZOOM_MINIMO, Math.min(ZOOM_MAXIMO, zoom));
            float escalaBase = Math.min(
                    (anchoLienzo * (1 - 2 * PADDING_FRACCION)) / caja.width,
                    (altoLienzo * (1 - 2 * PADDING_FRACCION)) / caja.height);
            float escala = escalaBase * zoomAplicado;
            int anchoDestino = Math.max(1, Math.round(caja.width * escala));
            int altoDestino = Math.max(1, Math.round(caja.height * escala));
            int x = (anchoLienzo - anchoDestino) / 2;
            int y = (altoLienzo - altoDestino) / 2;

            BufferedImage lienzo = new BufferedImage(anchoLienzo, altoLienzo, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = lienzo.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(origen, x, y, x + anchoDestino, y + altoDestino,
                    caja.x, caja.y, caja.x + caja.width, caja.y + caja.height, null);
            g.dispose();

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            ImageIO.write(lienzo, "png", salida);
            return salida.toByteArray();
        } catch (Exception ex) {
            return pngOriginal;
        }
    }

    // null si la imagen está completamente en blanco/transparente (nada que recortar).
    private static Rectangle detectarCajaContenido(BufferedImage imagen) {
        int minX = imagen.getWidth();
        int minY = imagen.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < imagen.getHeight(); y++) {
            for (int x = 0; x < imagen.getWidth(); x++) {
                int argb = imagen.getRGB(x, y);
                int alpha = (argb >>> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                boolean esFondo = alpha < 16 || (r > 245 && g > 245 && b > 245);
                if (!esFondo) {
                    if (x < minX) minX = x;
                    if (y < minY) minY = y;
                    if (x > maxX) maxX = x;
                    if (y > maxY) maxY = y;
                }
            }
        }
        if (maxX < minX || maxY < minY) {
            return null;
        }
        return new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }
}
