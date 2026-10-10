package sie.siejuridicos.tarea;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sie.siejuridicos.security.UsuarioInternoPrincipal;
import sie.siejuridicos.tarea.dto.ActualizarTareaRequest;
import sie.siejuridicos.tarea.dto.CrearTareaRequest;
import sie.siejuridicos.tarea.dto.TareaResponse;

import java.util.List;

// Mismo alcance de rol que Casos (ADMIN_GENERAL o ABOGADO), heredado de /api/admin/** en
// SecurityConfig -- pedido explícito del usuario: "esta habilidad la podra tener tanto admin
// como cualquiera de los abogados", sin restricción de "solo mis tareas" para crear/editar.
@RestController
@RequestMapping("/api/admin")
public class TareaController {

    private final TareaService tareaService;

    public TareaController(TareaService tareaService) {
        this.tareaService = tareaService;
    }

    @GetMapping("/casos/{casoId}/tareas")
    public ResponseEntity<List<TareaResponse>> listarPorCaso(@PathVariable Long casoId) {
        return ResponseEntity.ok(tareaService.listarPorCaso(casoId));
    }

    @PostMapping("/casos/{casoId}/tareas")
    public ResponseEntity<TareaResponse> crear(
            @PathVariable Long casoId,
            @Valid @RequestBody CrearTareaRequest request,
            @AuthenticationPrincipal UsuarioInternoPrincipal principal) {
        return ResponseEntity.ok(tareaService.crear(casoId, request, principal.getUsuario()));
    }

    // Página "Tareas" del panel: tareas pendientes del usuario autenticado (o de todos si es
    // ADMIN_GENERAL, ver TareaService.listarPendientes()).
    @GetMapping("/tareas")
    public ResponseEntity<List<TareaResponse>> listarPendientes(
            @AuthenticationPrincipal UsuarioInternoPrincipal principal) {
        return ResponseEntity.ok(tareaService.listarPendientes(principal.getUsuario()));
    }

    // Badge "N tareas pendientes" del listado de Casos (ver TareaService.contarPendientesPorCaso()).
    @GetMapping("/tareas/conteo-por-caso")
    public ResponseEntity<java.util.Map<Long, Long>> contarPendientesPorCaso() {
        return ResponseEntity.ok(tareaService.contarPendientesPorCaso());
    }

    @PatchMapping("/tareas/{id}")
    public ResponseEntity<TareaResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody ActualizarTareaRequest request,
            @AuthenticationPrincipal UsuarioInternoPrincipal principal) {
        return ResponseEntity.ok(tareaService.actualizar(id, request, principal.getUsuario()));
    }

    @PatchMapping("/tareas/{id}/completar")
    public ResponseEntity<TareaResponse> completar(
            @PathVariable Long id,
            @RequestParam boolean completada,
            @AuthenticationPrincipal UsuarioInternoPrincipal principal) {
        return ResponseEntity.ok(tareaService.completar(id, completada, principal.getUsuario()));
    }

    @DeleteMapping("/tareas/{id}")
    public ResponseEntity<Void> eliminar(
            @PathVariable Long id,
            @AuthenticationPrincipal UsuarioInternoPrincipal principal) {
        tareaService.eliminar(id, principal.getUsuario());
        return ResponseEntity.noContent().build();
    }
}
