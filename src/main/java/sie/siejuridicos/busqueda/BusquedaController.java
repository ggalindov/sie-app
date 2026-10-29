package sie.siejuridicos.busqueda;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Buscador total del panel: mismo nivel de acceso que Solicitudes/PQRS (ADMIN_GENERAL o
// ABOGADO) -- los 4 módulos que cubre (casos, clientes CRM, solicitudes, documentos para
// firma) ya son visibles para ambos roles hoy, ver BusquedaService para el porqué de
// excluir deliberadamente Usuarios/Testimonios/Marketing/Cobros.
@RestController
@RequestMapping("/api/admin/busqueda")
@PreAuthorize("hasAnyRole('ADMIN_GENERAL', 'ABOGADO')")
public class BusquedaController {

    private final BusquedaService busquedaService;

    public BusquedaController(BusquedaService busquedaService) {
        this.busquedaService = busquedaService;
    }

    @GetMapping
    public List<ResultadoBusqueda> buscar(@RequestParam(name = "q", required = false) String q) {
        return busquedaService.buscar(q);
    }
}
