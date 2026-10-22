package sie.siejuridicos.documento;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;
import sie.siejuridicos.common.exception.EntidadInvalidaException;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

// Vista previa del documento para quien firma: el PDF NUNCA sale del servidor antes de la
// firma -- cada página se convierte aquí en una imagen PNG con marca de agua de "solo
// lectura", y el frontend la pinta en un lienzo sin opción de guardar. La copia oficial
// firmada la recibe cada parte por correo cuando todas firman.
@Service
public class DocumentoVistaPreviaService {

    // ~1100 px de ancho para una página carta: nítido en el celular sin ser un archivo pesado.
    private static final float DPI = 130f;
    static final int MAXIMO_PAGINAS = 60;
    private static final String MARCA = "SOLO LECTURA · SIE JURÍDICOS";

    public int contarPaginas(byte[] pdf) {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            return Math.min(documento.getNumberOfPages(), MAXIMO_PAGINAS);
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo leer el documento para la vista previa.", ex);
        }
    }

    // numero: página desde 1
    public byte[] renderizarPagina(byte[] pdf, int numero) {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            int total = Math.min(documento.getNumberOfPages(), MAXIMO_PAGINAS);
            if (numero < 1 || numero > total) {
                throw new EntidadInvalidaException("Esa página no existe en el documento.");
            }
            BufferedImage imagen = new PDFRenderer(documento).renderImageWithDPI(numero - 1, DPI, ImageType.RGB);
            marcarSoloLectura(imagen);
            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            ImageIO.write(imagen, "png", salida);
            return salida.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo preparar la vista previa del documento.", ex);
        }
    }

    // Marca de agua diagonal y tenue repetida, que no impide leer pero deja claro que no
    // es la copia oficial.
    private static void marcarSoloLectura(BufferedImage imagen) {
        Graphics2D g = imagen.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.08f));
            g.setColor(new Color(33, 28, 21));
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(24, imagen.getWidth() / 18)));
            g.rotate(Math.toRadians(-30), imagen.getWidth() / 2.0, imagen.getHeight() / 2.0);
            int paso = imagen.getHeight() / 5;
            for (int y = -imagen.getHeight(); y < imagen.getHeight() * 2; y += paso) {
                g.drawString(MARCA, -imagen.getWidth() / 2, y);
                g.drawString(MARCA, imagen.getWidth() / 2, y + paso / 2);
            }
        } finally {
            g.dispose();
        }
    }
}
