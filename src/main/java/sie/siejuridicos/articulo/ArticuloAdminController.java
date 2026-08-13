package sie.siejuridicos.articulo;

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
import org.springframework.web.bind.annotation.RestController;
import sie.siejuridicos.articulo.dto.ActualizarArticuloRequest;
import sie.siejuridicos.articulo.dto.ArticuloDetalleResponse;
import sie.siejuridicos.articulo.dto.CrearArticuloRequest;
import sie.siejuridicos.security.UsuarioInternoPrincipal;

import java.util.List;

// Reglas de rol reforzadas también en SecurityConfig (/api/admin/**); @PreAuthorize
// queda como segunda capa de defensa a nivel de método.
@RestController
@RequestMapping("/api/admin/articulos")
@PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ABOGADO')")
public class ArticuloAdminController {

    private final ArticuloService articuloService;

    public ArticuloAdminController(ArticuloService articuloService) {
        this.articuloService = articuloService;
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
}
