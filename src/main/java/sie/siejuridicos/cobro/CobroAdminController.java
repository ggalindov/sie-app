package sie.siejuridicos.cobro;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sie.siejuridicos.cobro.dto.ActualizarRespuestaCobroRequest;
import sie.siejuridicos.cobro.dto.ClienteCobroResponse;
import sie.siejuridicos.cobro.dto.ResumenEnvioRecordatoriosCobros;
import sie.siejuridicos.cobro.dto.ResumenSincronizacionCobros;
import sie.siejuridicos.cobro.dto.SimularRespuestaCobroRequest;
import sie.siejuridicos.whatsapp.WhatsAppService;

import java.util.List;

// Mismo alcance de rol que Casos (ADMIN_GENERAL o ABOGADO), heredado de /api/admin/** en
// SecurityConfig.
@RestController
@RequestMapping("/api/admin/cobros")
// Pedido explícito del usuario: Cobros Pendientes queda restringido solo al administrador
// general, el rol ABOGADO pierde el acceso por completo (antes lo compartían ambos roles).
@PreAuthorize("hasRole('ADMIN_GENERAL')")
public class CobroAdminController {

    private final CobroService cobroService;
    private final boolean bloqueoTotalClientes;

    public CobroAdminController(CobroService cobroService,
                                @org.springframework.beans.factory.annotation.Value("${app.bloqueo-total-clientes:true}") boolean bloqueoTotalClientes) {
        this.cobroService = cobroService;
        this.bloqueoTotalClientes = bloqueoTotalClientes;
    }

    @GetMapping
    public ResponseEntity<List<ClienteCobroResponse>> listar() {
        return ResponseEntity.ok(cobroService.listarActivos());
    }

    // Botón "Actualizar" del panel: trae/actualiza todos los clientes activos desde las dos
    // pestañas del Google Sheets de cobros, y elimina del sistema los que ya no estén.
    @PostMapping("/sincronizar")
    public ResponseEntity<ResumenSincronizacionCobros> sincronizar() {
        return ResponseEntity.ok(cobroService.sincronizarDesdeHoja());
    }

    // Botón "Enviar recordatorios" del panel: dispara de una vez el mismo envío que corre
    // automáticamente el día 1 de cada mes.
    @PostMapping("/enviar-recordatorios")
    public ResponseEntity<ResumenEnvioRecordatoriosCobros> enviarRecordatorios() {
        return ResponseEntity.ok(cobroService.enviarRecordatorios());
    }

    // Permite al administrador marcar o corregir manualmente la respuesta (Sí / No / Limpiar)
    // y el estado de pago del mes directamente en el sistema
    @PatchMapping("/{id}/respuesta")
    public ResponseEntity<ClienteCobroResponse> actualizarRespuesta(
            @PathVariable Long id,
            @RequestBody ActualizarRespuestaCobroRequest request) {
        return ResponseEntity.ok(cobroService.actualizarRespuestaManual(
                id, request.respuesta(), request.pagoEsteMes()));
    }

    // Endpoint de prueba y simulación para verificar en local la respuesta de pago del cliente de test (3126029742)
    @PostMapping("/simular-respuesta")
    public ResponseEntity<List<ClienteCobroResponse>> simularRespuesta(
            @RequestBody SimularRespuestaCobroRequest request) {
        if (!bloqueoTotalClientes) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }
        String normalizado = WhatsAppService.normalizarCelular(request.telefono());
        if (normalizado == null && request.telefono() != null) {
            normalizado = request.telefono().replaceAll("[^0-9]", "");
        }
        return ResponseEntity.ok(cobroService.registrarRespuesta(normalizado, request.respuesta()));
    }
}
