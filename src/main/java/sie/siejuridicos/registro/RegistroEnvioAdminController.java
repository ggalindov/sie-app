package sie.siejuridicos.registro;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sie.siejuridicos.registro.dto.RegistroEnvioResponse;

// Solo ADMIN_GENERAL -- igual criterio que RegistroSistemaAdminController, y más estricto
// todavía: a diferencia del registro de procesos, este SÍ expone datos reales de contacto de
// clientes (nombre, correo o teléfono de cada destinatario).
@RestController
@RequestMapping("/api/admin/registro-envios")
@PreAuthorize("hasRole('ADMIN_GENERAL')")
public class RegistroEnvioAdminController {

    private final RegistroEnvioService registroEnvioService;

    public RegistroEnvioAdminController(RegistroEnvioService registroEnvioService) {
        this.registroEnvioService = registroEnvioService;
    }

    @GetMapping
    public ResponseEntity<Page<RegistroEnvioResponse>> listar(
            @RequestParam(required = false) CanalEnvio canal,
            @RequestParam(required = false) TipoEnvio tipo,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "30") int tamano) {
        return ResponseEntity.ok(registroEnvioService.listar(canal, tipo, pagina, tamano));
    }
}
