package sie.siejuridicos.cobro;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import sie.siejuridicos.cobro.dto.ClienteCobroResponse;
import sie.siejuridicos.cobro.dto.ResumenEnvioRecordatorioPrueba;
import sie.siejuridicos.cobro.dto.ResumenEnvioRecordatoriosCobros;
import sie.siejuridicos.cobro.dto.ResumenSincronizacionCobros;
import sie.siejuridicos.cobro.dto.SaludWebhookWhatsAppResponse;
import sie.siejuridicos.cobro.dto.SimularRespuestaCobroRequest;
import sie.siejuridicos.registro.RegistroSistemaService;
import sie.siejuridicos.registro.TipoRegistroSistema;
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
    private final RegistroSistemaService registroSistemaService;
    private final boolean bloqueoTotalClientes;

    public CobroAdminController(CobroService cobroService,
                                RegistroSistemaService registroSistemaService,
                                @org.springframework.beans.factory.annotation.Value("${app.bloqueo-total-clientes:true}") boolean bloqueoTotalClientes) {
        this.cobroService = cobroService;
        this.registroSistemaService = registroSistemaService;
        this.bloqueoTotalClientes = bloqueoTotalClientes;
    }

    @GetMapping
    public ResponseEntity<List<ClienteCobroResponse>> listar() {
        return ResponseEntity.ok(cobroService.listarActivos());
    }

    // Pedido explícito del usuario (2026-11-06): "revisa porque el aceptar o rechazar pagos
    // en whatsapp no se esta reflejando" -- auditoría en producción (logs del backend y del
    // borde Caddy) confirmó que Meta nunca entregó un evento real a nuestro webhook; las
    // únicas 2 llamadas registradas en todo el historial disponible fueron pruebas manuales
    // con curl/Python, nunca tráfico real de WhatsApp Cloud API. El problema está en la
    // suscripción del webhook del Meta Business Manager (fuera del alcance de este sistema),
    // no en el código. Este endpoint deja la evidencia visible en el panel para detectar
    // automáticamente si esto vuelve a pasar, en vez de descubrirlo semanas después.
    @GetMapping("/salud-webhook")
    public ResponseEntity<SaludWebhookWhatsAppResponse> saludWebhook() {
        var ultima = registroSistemaService.listar(TipoRegistroSistema.RESPUESTA_WEBHOOK_WHATSAPP_COBRO, 0, 1)
                .stream().findFirst();
        return ResponseEntity.ok(new SaludWebhookWhatsAppResponse(ultima.map(r -> r.fechaHora()).orElse(null)));
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

    // Botón "Enviar recordatorio de prueba" del panel: dispara un único recordatorio aislado al
    // número de prueba fijo de la firma (3126029742), sin tocar ni consumir cupo del lote real de
    // clientes -- ver CobroService.enviarRecordatorioDePrueba(). A diferencia de
    // /simular-respuesta, SÍ funciona en producción (bloqueoTotalClientes no aplica aquí: el
    // propio método ya trae su propia comprobación de seguridad contra el número de prueba).
    @PostMapping("/enviar-recordatorio-prueba")
    public ResponseEntity<ResumenEnvioRecordatorioPrueba> enviarRecordatorioDePrueba() {
        return ResponseEntity.ok(cobroService.enviarRecordatorioDePrueba());
    }

    public record ActualizarEstadoPagoRequest(Boolean pagoEsteMes, String respuesta) {}

    // Permite al administrador ajustar o confirmar directamente el pago de un cliente
    @org.springframework.web.bind.annotation.PutMapping("/{id}/estado-pago")
    public ResponseEntity<ClienteCobroResponse> actualizarEstadoPago(
            @org.springframework.web.bind.annotation.PathVariable Long id,
            @RequestBody ActualizarEstadoPagoRequest request) {
        return ResponseEntity.ok(cobroService.actualizarEstadoPagoManual(id, request.pagoEsteMes(), request.respuesta()));
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
