package sie.siejuridicos.articulo;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sie.siejuridicos.articulo.dto.ArticuloDetalleResponse;
import sie.siejuridicos.articulo.dto.ArticuloResumenResponse;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/articulos")
public class ArticuloPublicoController {

    private final ArticuloService articuloService;
    private final ImagenArticuloService imagenArticuloService;

    public ArticuloPublicoController(ArticuloService articuloService, ImagenArticuloService imagenArticuloService) {
        this.articuloService = articuloService;
        this.imagenArticuloService = imagenArticuloService;
    }

    @GetMapping
    public ResponseEntity<List<ArticuloResumenResponse>> listar(
            @RequestParam(required = false) Long categoria,
            @RequestParam(required = false) TipoContenido tipo,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(articuloService.listarPublicados(categoria, tipo, q));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ArticuloDetalleResponse> detalle(@PathVariable String slug) {
        return ResponseEntity.ok(articuloService.obtenerPublicadoPorSlug(slug));
    }

    // Sirve las imágenes de portada subidas desde el computador (ver
    // ImagenArticuloService/ArticuloAdminController.subirImagen). Público, sin autenticación:
    // es exactamente lo que se referencia en el <img> del sitio público. Cache-Control largo
    // e "immutable" porque el nombre de archivo es un UUID nuevo en cada subida -- la misma
    // URL nunca cambia de contenido, así que el navegador puede confiar en la copia cacheada
    // para siempre sin volver a pedirla.
    @GetMapping("/imagenes/{mes}/{archivo}")
    public ResponseEntity<byte[]> imagen(@PathVariable String mes, @PathVariable String archivo) {
        byte[] contenido = imagenArticuloService.leer(mes, archivo);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(contenido);
    }
}
