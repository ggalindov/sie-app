package sie.siejuridicos.registro;

import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import sie.siejuridicos.registro.dto.RegistroSistemaResponse;

// Solo ADMIN_GENERAL, igual que marketing/usuarios/boletines: ver toda la actividad del
// sistema (incluidos intentos de inicio de sesión) es información más sensible que la
// operación del día a día que sí le corresponde también al rol ABOGADO.
@RestController
@RequestMapping("/api/admin/registro-sistema")
@PreAuthorize("hasRole('ADMIN_GENERAL')")
public class RegistroSistemaAdminController {

    private final RegistroSistemaService registroSistemaService;

    public RegistroSistemaAdminController(RegistroSistemaService registroSistemaService) {
        this.registroSistemaService = registroSistemaService;
    }

    @GetMapping
    public ResponseEntity<Page<RegistroSistemaResponse>> listar(
            @RequestParam(required = false) TipoRegistroSistema tipo,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "30") int tamano) {
        return ResponseEntity.ok(registroSistemaService.listar(tipo, pagina, tamano));
    }
}
