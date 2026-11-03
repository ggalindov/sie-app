package sie.siejuridicos.configuracion;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sie.siejuridicos.security.UsuarioInternoPrincipal;

import java.util.List;

// Pedido explícito del usuario: "implementa en el dashboard principal del administrador un
// boton para apagar los envios de correo, apagar los envios de whatsapp (boton
// independiente notificacones, boletin, marketing entre otros)" -- ADMIN_GENERAL
// únicamente, reforzado también en SecurityConfig (/api/admin/configuracion-envios/**).
@RestController
@RequestMapping("/api/admin/configuracion-envios")
@PreAuthorize("hasRole('ADMIN_GENERAL')")
public class ConfiguracionEnviosAdminController {

    private final ConfiguracionEnviosService service;

    public ConfiguracionEnviosAdminController(ConfiguracionEnviosService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<ConfiguracionEnvioResponse>> listar() {
        return ResponseEntity.ok(service.listar());
    }

    @PatchMapping("/{clave}")
    public ResponseEntity<Void> actualizar(@PathVariable ClaveConfiguracionEnvio clave,
                                            @RequestBody ActualizarConfiguracionEnvioRequest request,
                                            @AuthenticationPrincipal UsuarioInternoPrincipal principal) {
        service.actualizar(clave, request.activo(), principal.getUsuario());
        return ResponseEntity.ok().build();
    }
}
