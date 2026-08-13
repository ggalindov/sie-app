package sie.siejuridicos.articulo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sie.siejuridicos.articulo.dto.ArticuloDetalleResponse;
import sie.siejuridicos.articulo.dto.ArticuloResumenResponse;

import java.util.List;

@RestController
@RequestMapping("/api/articulos")
public class ArticuloPublicoController {

    private final ArticuloService articuloService;

    public ArticuloPublicoController(ArticuloService articuloService) {
        this.articuloService = articuloService;
    }

    @GetMapping
    public ResponseEntity<List<ArticuloResumenResponse>> listar(
            @RequestParam(required = false) Long categoria,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(articuloService.listarPublicados(categoria, q));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ArticuloDetalleResponse> detalle(@PathVariable String slug) {
        return ResponseEntity.ok(articuloService.obtenerPublicadoPorSlug(slug));
    }
}
