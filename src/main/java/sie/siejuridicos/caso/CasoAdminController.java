package sie.siejuridicos.caso;

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
import sie.siejuridicos.caso.dto.ActualizarEtapaCasoRequest;
import sie.siejuridicos.caso.dto.CasoAdminResponse;
import sie.siejuridicos.caso.dto.CrearCasoRequest;

import java.util.List;

// Gestión de casos: alcance de ambos roles (como Solicitudes), lo crea "el abogado o el
// administrador" según lo pedido; hereda hasAnyRole(ADMIN_GENERAL, ABOGADO) de
// /api/admin/** en SecurityConfig, sin restricción adicional.
@RestController
@RequestMapping("/api/admin/casos")
@PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ABOGADO')")
public class CasoAdminController {

    private final CasoService casoService;

    public CasoAdminController(CasoService casoService) {
        this.casoService = casoService;
    }

    @PostMapping
    public ResponseEntity<CasoAdminResponse> crear(@Valid @RequestBody CrearCasoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(casoService.crear(request));
    }

    @GetMapping
    public ResponseEntity<List<CasoAdminResponse>> listar() {
        return ResponseEntity.ok(casoService.listarTodos());
    }

    @PatchMapping("/{id}/etapa")
    public ResponseEntity<CasoAdminResponse> actualizarEtapa(@PathVariable Long id,
                                                               @Valid @RequestBody ActualizarEtapaCasoRequest request) {
        return ResponseEntity.ok(casoService.actualizarEtapa(id, request.nuevaEtapa()));
    }
}
