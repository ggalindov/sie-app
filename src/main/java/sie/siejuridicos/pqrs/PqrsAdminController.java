package sie.siejuridicos.pqrs;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sie.siejuridicos.pqrs.dto.CambiarEstadoPqrsRequest;
import sie.siejuridicos.pqrs.dto.PqrsAdminResponse;
import sie.siejuridicos.pqrs.dto.ResponderPqrsRequest;
import sie.siejuridicos.security.UsuarioInternoPrincipal;

import java.util.List;

// Pedido explícito del usuario (2026-11-04): "para el rol de abogado elimina ... PQRS" --
// revierte la decisión anterior (antes al alcance de ambos roles): ahora exclusivo de
// ADMIN_GENERAL. Regla reforzada también en SecurityConfig (/api/admin/pqrs/**).
@RestController
@RequestMapping("/api/admin/pqrs")
@PreAuthorize("hasRole('ADMIN_GENERAL')")
public class PqrsAdminController {

    private final PqrsService pqrsService;

    public PqrsAdminController(PqrsService pqrsService) {
        this.pqrsService = pqrsService;
    }

    @GetMapping
    public ResponseEntity<List<PqrsAdminResponse>> listar() {
        return ResponseEntity.ok(pqrsService.listarTodas());
    }

    @PatchMapping("/{id}/estado")
    public ResponseEntity<PqrsAdminResponse> cambiarEstado(
            @PathVariable Long id,
            @Valid @RequestBody CambiarEstadoPqrsRequest request
    ) {
        return ResponseEntity.ok(pqrsService.cambiarEstado(id, request.nuevoEstado()));
    }

    @PatchMapping("/{id}/responder")
    public ResponseEntity<PqrsAdminResponse> responder(
            @PathVariable Long id,
            @Valid @RequestBody ResponderPqrsRequest request,
            @AuthenticationPrincipal UsuarioInternoPrincipal principal
    ) {
        return ResponseEntity.ok(pqrsService.responder(id, request.respuesta(), principal.getUsuario()));
    }
}
