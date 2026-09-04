package sie.siejuridicos.caso;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sie.siejuridicos.caso.dto.CasoAdminResponse;
import sie.siejuridicos.caso.dto.CrearCasoRequest;
import sie.siejuridicos.caso.dto.ResumenEnvioCorreos;
import sie.siejuridicos.caso.dto.ResumenSincronizacion;

import java.util.List;

// Gestión de casos: alcance de ambos roles (como Solicitudes), lo crea "el abogado o el
// administrador" según lo pedido; hereda hasAnyRole(ADMIN_GENERAL, ABOGADO) de
// /api/admin/** en SecurityConfig, sin restricción adicional.
//
// Ya no hay endpoint de cambiar etapa: el estado del caso se lee en vivo del Google Sheets de
// la firma (ver HojaCalculoService), no se gestiona desde aquí.
@RestController
@RequestMapping("/api/admin/casos")
@PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ABOGADO')")
public class CasoAdminController {

    private final CasoService casoService;

    public CasoAdminController(CasoService casoService) {
        this.casoService = casoService;
    }

    // Respaldo manual: el flujo principal ahora es sincronizar() (ver más abajo).
    @PostMapping
    public ResponseEntity<CasoAdminResponse> crear(@Valid @RequestBody CrearCasoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(casoService.crear(request));
    }

    @GetMapping
    public ResponseEntity<List<CasoAdminResponse>> listar() {
        return ResponseEntity.ok(casoService.listarTodos());
    }

    // Botón "Actualizar" del panel: trae/actualiza todos los casos desde el Google Sheets de
    // la firma, sin necesidad de cargarlos uno por uno.
    @PostMapping("/sincronizar")
    public ResponseEntity<ResumenSincronizacion> sincronizar() {
        return ResponseEntity.ok(casoService.sincronizarDesdeHoja());
    }

    // Botón "Enviar correos pendientes" del panel: notifica de una sola vez a todos los
    // clientes con radicado asignado que todavía no recibieron su correo.
    @PostMapping("/enviar-pendientes")
    public ResponseEntity<ResumenEnvioCorreos> enviarPendientes() {
        return ResponseEntity.ok(casoService.enviarCorreosPendientes());
    }
}
