package sie.siejuridicos.crm;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sie.siejuridicos.crm.dto.ActividadCrmResponse;
import sie.siejuridicos.crm.dto.ActualizarClienteCrmRequest;
import sie.siejuridicos.crm.dto.CambiarEtapaPipelineRequest;
import sie.siejuridicos.crm.dto.ClienteCrmDetalleResponse;
import sie.siejuridicos.crm.dto.ClienteCrmResponse;
import sie.siejuridicos.crm.dto.ConvertirProspectoRequest;
import sie.siejuridicos.crm.dto.CrearActividadCrmRequest;
import sie.siejuridicos.crm.dto.CrearClienteCrmRequest;
import sie.siejuridicos.crm.dto.CrearTareaCrmRequest;
import sie.siejuridicos.crm.dto.CrmDashboardResponse;
import sie.siejuridicos.crm.dto.ItemPipelineResponse;
import sie.siejuridicos.crm.dto.TareaCrmResponse;

import java.util.List;

@RestController
@RequestMapping("/api/admin/crm")
@PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ABOGADO')")
public class CrmController {

    private final CrmService crmService;

    public CrmController(CrmService crmService) {
        this.crmService = crmService;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<CrmDashboardResponse> obtenerDashboard() {
        return ResponseEntity.ok(crmService.obtenerDashboard());
    }

    // =========================================================================
    // PIPELINE DE OPORTUNIDADES
    // =========================================================================

    @GetMapping("/pipeline")
    public ResponseEntity<List<ItemPipelineResponse>> obtenerPipeline() {
        return ResponseEntity.ok(crmService.obtenerPipeline());
    }

    @PatchMapping("/pipeline/{solicitudId}/etapa")
    public ResponseEntity<ItemPipelineResponse> cambiarEtapaPipeline(
            @PathVariable Long solicitudId,
            @jakarta.validation.Valid @RequestBody CambiarEtapaPipelineRequest request) {
        return ResponseEntity.ok(crmService.cambiarEtapaPipeline(solicitudId, request));
    }

    @PostMapping("/pipeline/{solicitudId}/convertir")
    public ResponseEntity<ClienteCrmResponse> convertirProspecto(
            @PathVariable Long solicitudId,
            @jakarta.validation.Valid @RequestBody ConvertirProspectoRequest request) {
        return ResponseEntity.ok(crmService.convertirProspectoACrm(solicitudId, request));
    }

    // =========================================================================
    // CLIENTES CRM 360°
    // =========================================================================

    @GetMapping("/clientes")
    public ResponseEntity<List<ClienteCrmResponse>> listarClientes(
            @RequestParam(required = false) String busqueda,
            @RequestParam(required = false) EstadoClienteCrm estado,
            @RequestParam(required = false) TipoClienteCrm tipo) {
        return ResponseEntity.ok(crmService.listarClientes(busqueda, estado, tipo));
    }

    @GetMapping("/clientes/{id}")
    public ResponseEntity<ClienteCrmDetalleResponse> obtenerDetalle(@PathVariable Long id) {
        return ResponseEntity.ok(crmService.obtenerDetalle(id));
    }

    @PostMapping("/clientes")
    public ResponseEntity<ClienteCrmResponse> crearCliente(@jakarta.validation.Valid @RequestBody CrearClienteCrmRequest request) {
        return ResponseEntity.ok(crmService.crearCliente(request));
    }

    @PutMapping("/clientes/{id}")
    public ResponseEntity<ClienteCrmResponse> actualizarCliente(
            @PathVariable Long id,
            @jakarta.validation.Valid @RequestBody ActualizarClienteCrmRequest request) {
        return ResponseEntity.ok(crmService.actualizarCliente(id, request));
    }

    @PreAuthorize("hasRole('ADMIN_GENERAL')")
    @DeleteMapping("/clientes/{id}")
    public ResponseEntity<Void> archivarCliente(@PathVariable Long id) {
        crmService.archivarCliente(id);
        return ResponseEntity.noContent().build();
    }

    // =========================================================================
    // ACTIVIDADES Y TAREAS
    // =========================================================================

    @PostMapping("/clientes/{id}/actividades")
    public ResponseEntity<ActividadCrmResponse> registrarActividad(
            @PathVariable Long id,
            @jakarta.validation.Valid @RequestBody CrearActividadCrmRequest request) {
        return ResponseEntity.ok(crmService.registrarActividad(id, request));
    }

    @PostMapping("/clientes/{id}/tareas")
    public ResponseEntity<TareaCrmResponse> crearTarea(
            @PathVariable Long id,
            @jakarta.validation.Valid @RequestBody CrearTareaCrmRequest request) {
        return ResponseEntity.ok(crmService.crearTarea(id, request));
    }

    @PatchMapping("/tareas/{tareaId}/completar")
    public ResponseEntity<TareaCrmResponse> completarTarea(
            @PathVariable Long tareaId,
            @RequestParam(defaultValue = "true") boolean completada) {
        return ResponseEntity.ok(crmService.completarTarea(tareaId, completada));
    }
}
