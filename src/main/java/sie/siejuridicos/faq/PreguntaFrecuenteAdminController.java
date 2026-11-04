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

// Pedido explícito del usuario (2026-11-04): "para el rol de abogado elimina ... preguntas
// frecuentes" -- revierte la decisión anterior ("el abogado la aprueba con un clic"): ahora
// exclusivo de ADMIN_GENERAL. Regla reforzada también en SecurityConfig (/api/admin/faq/**).
@RestController
@RequestMapping("/api/admin/faq")
@PreAuthorize("hasRole('ADMIN_GENERAL')")
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
