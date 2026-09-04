package sie.siejuridicos.cobro;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sie.siejuridicos.cobro.dto.ClienteCobroResponse;
import sie.siejuridicos.cobro.dto.ResumenEnvioRecordatoriosCobros;
import sie.siejuridicos.cobro.dto.ResumenSincronizacionCobros;

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

    public CobroAdminController(CobroService cobroService) {
        this.cobroService = cobroService;
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
}
