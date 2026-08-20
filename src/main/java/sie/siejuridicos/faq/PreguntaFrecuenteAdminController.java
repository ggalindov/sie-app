package sie.siejuridicos.faq;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sie.siejuridicos.faq.dto.ModerarPreguntaFrecuenteRequest;
import sie.siejuridicos.faq.dto.PreguntaFrecuenteAdminResponse;

import java.util.List;

// A diferencia de testimonios/marketing, la FAQ sí está al alcance del rol ABOGADO (pedido
// explícito del usuario: "el abogado la aprueba con un clic"), por eso no hay restricción
// extra de rol aquí ni en SecurityConfig: hereda el hasAnyRole(ADMIN_GENERAL, ABOGADO) por
// defecto de /api/admin/**.
@RestController
@RequestMapping("/api/admin/faq")
@PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ABOGADO')")
public class PreguntaFrecuenteAdminController {

    private final PreguntaFrecuenteService preguntaFrecuenteService;

    public PreguntaFrecuenteAdminController(PreguntaFrecuenteService preguntaFrecuenteService) {
        this.preguntaFrecuenteService = preguntaFrecuenteService;
    }

    @GetMapping
    public ResponseEntity<List<PreguntaFrecuenteAdminResponse>> listarCandidatas() {
        return ResponseEntity.ok(preguntaFrecuenteService.listarCandidatas());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<PreguntaFrecuenteAdminResponse> moderar(
            @PathVariable Long id,
            @Valid @RequestBody ModerarPreguntaFrecuenteRequest request) {
        return ResponseEntity.ok(preguntaFrecuenteService.moderar(id, request.nuevoEstado(), request.respuesta()));
    }
}
