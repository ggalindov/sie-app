package sie.siejuridicos.articulo;

import net.coobird.thumbnailator.Thumbnails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import sie.siejuridicos.common.exception.EntidadInvalidaException;
import sie.siejuridicos.common.exception.RecursoNoEncontradoException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.YearMonth;
import java.util.Iterator;
import java.util.UUID;
import java.util.regex.Pattern;

// Imagen de portada subida desde el computador (alternativa al campo de URL externa que ya
// existía, ver CrearArticuloRequest.imagenUrl) -- ambos caminos conviven: este servicio solo
// produce una URL más que termina guardada en esa misma columna.
//
// Toda imagen aceptada (JPG, PNG, GIF o BMP -- ver esHeic() sobre por qué HEIC, el formato
// por defecto de fotos de iPhone, se rechaza explícito en vez de intentarlo) se redimensiona
// (máximo 1600px de lado), se corrige según su orientación EXIF y se recomprime SIEMPRE a
// JPEG calidad 82%: una foto de celular sin editar (varios MB) queda casi siempre por debajo
// de 300 KB sin pérdida visible perceptible para una imagen de portada. Efecto colateral
// deseado: al re-renderizar el archivo desde cero se pierden los metadatos EXIF originales
// (ubicación GPS incluida), no solo el peso.
//
// Se guarda bajo una subcarpeta por mes (yyyy-MM), a pedido explícito del usuario: poder
// limpiar en el futuro un mes completo de fotos es borrar una sola carpeta del servidor, sin
// tener que identificar imagen por imagen qué artículo la sigue usando. Ojo: borrar una
// carpeta de un mes rompe la imagen de cualquier artículo (incluso ya publicado) que todavía
// apunte a una foto de ese mes -- es una decisión que le corresponde a quien administra el
// servidor, no algo que este servicio intente evitar solo.
@Service
public class ImagenArticuloService {

    private static final int LADO_MAXIMO_PX = 1600;
    private static final double CALIDAD_JPEG = 0.82;

    // Límite de píxeles totales (ancho*alto) del archivo ORIGINAL, no del resultado ya
    // redimensionado -- bug real encontrado en auditoría: el único control de tamaño antes
    // de esto era el peso en bytes (10MB, ver application.properties), no las dimensiones.
    // Un PNG con compresión agresiva puede pesar pocos MB pero declarar dimensiones enormes
    // (ej. 30000x30000, una "bomba de imagen" clásica); Thumbnailator/ImageIO necesitan
    // reservar un BufferedImage proporcional a esas dimensiones ANTES de poder redimensionar
    // nada, lo que puede agotar el heap del backend (subido a 1024m en producción, ver
    // Dockerfile) y tumbar el proceso con OutOfMemoryError -- no es una IOException normal,
    // así que el catch de más abajo no lo cubre. 50 megapíxeles es generoso de sobra para
    // cualquier foto real de celular (incluso sensores de gama alta) pero corta este caso.
    private static final long MAXIMO_PIXELES = 50_000_000L;

    // Un "mes" en la URL siempre tiene forma yyyy-MM y un archivo siempre es un UUID + ".jpg"
    // (ver subir()): cualquier otra cosa en la ruta es, como mínimo, sospechosa.
    private static final Pattern PATRON_MES = Pattern.compile("^\\d{4}-\\d{2}$");
    private static final Pattern PATRON_ARCHIVO = Pattern.compile("^[a-f0-9-]{36}\\.jpg$");

    private final Path directorioBase;

    public ImagenArticuloService(@Value("${app.almacenamiento.imagenes-articulos}") String directorioBase) {
        this.directorioBase = Path.of(directorioBase).toAbsolutePath().normalize();
    }

    /**
     * Procesa y guarda la imagen subida. Devuelve la ruta relativa ("2026-09/uuid.jpg") con
     * la que el controlador arma la URL pública completa.
     */
    public String subir(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new EntidadInvalidaException("El archivo de imagen está vacío.");
        }

        byte[] contenido;
        try {
            contenido = archivo.getBytes();
        } catch (IOException ex) {
            throw new EntidadInvalidaException("No se pudo leer el archivo subido.");
        }

        // Caso real y frecuente, no teórico: las fotos de iPhone son HEIC por defecto, y
        // javax.imageio (lo que usa Thumbnailator por debajo) no trae lector de HEIC -- es un
        // formato con licencias de patente de por medio, a diferencia de JPEG/PNG/GIF/BMP que
        // sí vienen incluidos en el JDK. No amerita una dependencia nueva solo para esto, pero
        // sin esta detección explícita el error genérico de más abajo no le da a quien sube la
        // foto ninguna pista de qué pasó ni cómo resolverlo.
        if (esHeic(contenido)) {
            throw new EntidadInvalidaException(
                    "Las fotos en formato HEIC (típico de iPhone) todavía no son compatibles. Desde la app "
                            + "Fotos comparte la imagen como JPG, o cambia Ajustes > Cámara > Formatos a "
                            + "\"Más compatible\", y vuelve a subirla.");
        }

        // Solo lee ancho/alto de la cabecera (ImageReader no decodifica los píxeles todavía):
        // hay que descartar una imagen con dimensiones absurdas ANTES de arriesgarse a
        // decodificarla completa más abajo.
        verificarDimensiones(contenido);

        String mesActual = YearMonth.now().toString();
        Path directorioMes = directorioBase.resolve(mesActual);
        String nombreArchivo = UUID.randomUUID() + ".jpg";
        Path destino = directorioMes.resolve(nombreArchivo);

        try {
            Files.createDirectories(directorioMes);
            // PNG/GIF con transparencia: si se le pasa el stream original tal cual a
            // Thumbnailator para convertir a JPEG (que no soporta canal alfa), el
            // comportamiento conocido de la conversión por defecto de ImageIO es rellenar el
            // área transparente de NEGRO -- bug real encontrado en auditoría, afecta a
            // cualquier logo o gráfico con fondo transparente (formatos aceptados
            // explícitamente más arriba). Para esos casos se decodifica una vez, se aplana a
            // mano sobre un fondo BLANCO con Graphics2D, y se le pasa esa imagen ya aplanada
            // (sin alfa) a Thumbnailator. Para JPEG/BMP (nunca tienen alfa) se mantiene el
            // camino original desde los bytes crudos, necesario para que
            // useExifOrientation(true) pueda leer la orientación EXIF real del archivo (se
            // perdería si se partiera de un BufferedImage ya decodificado en memoria).
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(contenido));
            if (original == null) {
                throw new EntidadInvalidaException(
                        "No se pudo procesar el archivo: asegúrate de que sea una imagen JPG, PNG, GIF o BMP.");
            }
            if (original.getColorModel().hasAlpha()) {
                BufferedImage aplanada = new BufferedImage(
                        original.getWidth(), original.getHeight(), BufferedImage.TYPE_INT_RGB);
                var g = aplanada.createGraphics();
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, aplanada.getWidth(), aplanada.getHeight());
                g.drawImage(original, 0, 0, null);
                g.dispose();
                Thumbnails.of(aplanada)
                        .size(LADO_MAXIMO_PX, LADO_MAXIMO_PX)
                        .outputFormat("jpg")
                        .outputQuality(CALIDAD_JPEG)
                        .toFile(destino.toFile());
            } else {
                Thumbnails.of(new ByteArrayInputStream(contenido))
                        .size(LADO_MAXIMO_PX, LADO_MAXIMO_PX)
                        .useExifOrientation(true)
                        .outputFormat("jpg")
                        .outputQuality(CALIDAD_JPEG)
                        .toFile(destino.toFile());
            }
        } catch (IOException | IllegalArgumentException ex) {
            // Best-effort: si Thumbnailator alcanzó a crear un archivo parcial/corrupto antes
            // de fallar, no debe quedar huérfano en el disco -- nadie llega a conocer su URL
            // (el método nunca retorna en este camino), pero tampoco cuesta nada limpiarlo.
            try {
                Files.deleteIfExists(destino);
            } catch (IOException ignorada) {
                // no crítico
            }
            throw new EntidadInvalidaException(
                    "No se pudo procesar el archivo: asegúrate de que sea una imagen JPG, PNG, GIF o BMP.");
        }

        return mesActual + "/" + nombreArchivo;
    }

    private static void verificarDimensiones(byte[] contenido) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(contenido))) {
            if (iis == null) {
                throw new EntidadInvalidaException(
                        "No se pudo procesar el archivo: asegúrate de que sea una imagen JPG, PNG, GIF o BMP.");
            }
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(iis);
            if (!lectores.hasNext()) {
                throw new EntidadInvalidaException(
                        "No se pudo procesar el archivo: asegúrate de que sea una imagen JPG, PNG, GIF o BMP.");
            }
            ImageReader lector = lectores.next();
            try {
                lector.setInput(iis);
                long pixeles = (long) lector.getWidth(0) * (long) lector.getHeight(0);
                if (pixeles > MAXIMO_PIXELES) {
                    throw new EntidadInvalidaException(
                            "La imagen es demasiado grande (dimensiones excesivas). Reduce su resolución e "
                                    + "inténtalo de nuevo.");
                }
            } finally {
                lector.dispose();
            }
        } catch (IOException ex) {
            throw new EntidadInvalidaException("No se pudo leer el archivo subido.");
        }
    }

    private static boolean esHeic(byte[] contenido) {
        if (contenido.length < 12) {
            return false;
        }
        boolean esCajaFtyp = contenido[4] == 'f' && contenido[5] == 't' && contenido[6] == 'y' && contenido[7] == 'p';
        if (!esCajaFtyp) {
            return false;
        }
        String marca = new String(contenido, 8, 4, StandardCharsets.US_ASCII);
        return switch (marca) {
            case "heic", "heix", "hevc", "hevx", "heim", "heis", "hevm", "hevs", "mif1", "msf1" -> true;
            default -> false;
        };
    }

    /** Lee una imagen ya subida. Nunca confía en "mes"/"archivo" tal como llegan en la URL. */
    public byte[] leer(String mes, String archivo) {
        if (!PATRON_MES.matcher(mes).matches() || !PATRON_ARCHIVO.matcher(archivo).matches()) {
            throw new RecursoNoEncontradoException("Imagen no encontrada.");
        }
        Path ruta = directorioBase.resolve(mes).resolve(archivo).normalize();
        if (!ruta.startsWith(directorioBase) || !Files.isRegularFile(ruta)) {
            throw new RecursoNoEncontradoException("Imagen no encontrada.");
        }
        try {
            return Files.readAllBytes(ruta);
        } catch (IOException ex) {
            throw new RecursoNoEncontradoException("Imagen no encontrada.");
        }
    }
}
