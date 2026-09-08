package sie.siejuridicos.articulo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import sie.siejuridicos.articulo.dto.ActualizarArticuloRequest;
import sie.siejuridicos.articulo.dto.ArticuloDetalleResponse;
import sie.siejuridicos.articulo.dto.CrearArticuloRequest;
import sie.siejuridicos.articulo.dto.ImagenSubidaResponse;
import sie.siejuridicos.security.UsuarioInternoPrincipal;

import java.util.List;

// Reglas de rol reforzadas también en SecurityConfig (/api/admin/**); @PreAuthorize
// queda como segunda capa de defensa a nivel de método.
@RestController
@RequestMapping("/api/admin/articulos")
@PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ABOGADO')")
public class ArticuloAdminController {

    private final ArticuloService articuloService;
    private final ImagenArticuloService imagenArticuloService;

    public ArticuloAdminController(ArticuloService articuloService, ImagenArticuloService imagenArticuloService) {
        this.articuloService = articuloService;
        this.imagenArticuloService = imagenArticuloService;
    }

    @GetMapping
    public ResponseEntity<List<ArticuloDetalleResponse>> listar() {
        return ResponseEntity.ok(articuloService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ArticuloDetalleResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(articuloService.obtenerPorId(id));
    }

    @PostMapping
    public ResponseEntity<ArticuloDetalleResponse> crear(@Valid @RequestBody CrearArticuloRequest request,
                                                           @AuthenticationPrincipal UsuarioInternoPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(articuloService.crear(request, principal.getId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ArticuloDetalleResponse> actualizar(@PathVariable Long id,
                                                                @Valid @RequestBody ActualizarArticuloRequest request) {
        return ResponseEntity.ok(articuloService.actualizar(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        articuloService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    // Sube la imagen de portada desde el computador (ver ImagenArticuloService): la
    // redimensiona, corrige orientación y recomprime, y devuelve una URL pública absoluta
    // lista para pegarse en el campo "imagenUrl" del formulario -- el resto del flujo de
    // crear/actualizar artículo no cambia, sigue siendo el mismo campo de texto de siempre.
    // La URL se arma a partir del propio request (esquema/host reales, ya corregidos detrás
    // de Caddy por server.forward-headers-strategy en producción) en vez de una propiedad de
    // configuración fija: así funciona igual en desarrollo local y en producción sin tener
    // que mantener sincronizadas dos formas de saber "cuál es mi propia URL pública".
    @PostMapping("/imagenes")
    public ResponseEntity<ImagenSubidaResponse> subirImagen(@RequestParam("archivo") MultipartFile archivo,
                                                              HttpServletRequest request) {
        String rutaRelativa = imagenArticuloService.subir(archivo);
        String base = ServletUriComponentsBuilder.fromContextPath(request).build().toUriString();
        String url = base + "/api/articulos/imagenes/" + rutaRelativa;
        return ResponseEntity.status(HttpStatus.CREATED).body(new ImagenSubidaResponse(url));
    }
}
