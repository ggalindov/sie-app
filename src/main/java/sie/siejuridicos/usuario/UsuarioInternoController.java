package sie.siejuridicos.usuario;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sie.siejuridicos.usuario.dto.ActualizarActivoRequest;
import sie.siejuridicos.usuario.dto.CrearUsuarioRequest;
import sie.siejuridicos.usuario.dto.UsuarioResponse;

import java.util.List;

// Reglas de rol reforzadas también en SecurityConfig (/api/admin/usuarios/**); @PreAuthorize
// queda como segunda capa de defensa a nivel de método.
@RestController
@RequestMapping("/api/admin/usuarios")
@PreAuthorize("hasRole('ADMIN_GENERAL')")
public class UsuarioInternoController {

    private final UsuarioInternoService usuarioInternoService;

    public UsuarioInternoController(UsuarioInternoService usuarioInternoService) {
        this.usuarioInternoService = usuarioInternoService;
    }

    @PostMapping
    public ResponseEntity<UsuarioResponse> crear(@Valid @RequestBody CrearUsuarioRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioInternoService.crearAbogado(request));
    }

    @GetMapping
    public ResponseEntity<List<UsuarioResponse>> listar() {
        return ResponseEntity.ok(usuarioInternoService.listar());
    }

    @PatchMapping("/{id}/activo")
    public ResponseEntity<UsuarioResponse> cambiarActivo(@PathVariable Long id,
                                                           @Valid @RequestBody ActualizarActivoRequest request) {
        return ResponseEntity.ok(usuarioInternoService.cambiarActivo(id, request.activo()));
    }
}
