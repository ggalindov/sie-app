package sie.siejuridicos.documento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import sie.siejuridicos.correo.EmailService;
import sie.siejuridicos.documento.events.AnclaIntegridadComprometidaEvent;
import sie.siejuridicos.usuario.RolUsuario;
import sie.siejuridicos.usuario.UsuarioInterno;
import sie.siejuridicos.usuario.UsuarioInternoRepository;

// Mismo patrón que DocumentoIntegridadAlertaListener, pero para una alerta a nivel de
// sistema (no de un documento puntual): el ancla diaria externa ya no coincide con el
// estado actual de los checkpoints de auditoría, señal concluyente de manipulación directa
// de la base de datos (ver AnclaDiariaIntegridadScheduler.reverificarAnclasAnteriores).
@Component
public class AnclaIntegridadAlertaListener {

    private static final Logger log = LoggerFactory.getLogger(AnclaIntegridadAlertaListener.class);

    private final UsuarioInternoRepository usuarioInternoRepository;
    private final EmailService emailService;

    public AnclaIntegridadAlertaListener(UsuarioInternoRepository usuarioInternoRepository, EmailService emailService) {
        this.usuarioInternoRepository = usuarioInternoRepository;
        this.emailService = emailService;
    }

    @EventListener
    public void onAnclaComprometida(AnclaIntegridadComprometidaEvent event) {
        for (UsuarioInterno admin : usuarioInternoRepository.findByRolAndActivoTrue(RolUsuario.ADMIN_GENERAL)) {
            if (admin.getCorreo() == null || admin.getCorreo().isBlank()) {
                continue;
            }
            try {
                emailService.enviarAlertaIntegridadAnclaComprometida(admin.getNombre(), admin.getCorreo(),
                        event.fecha(), event.detalle());
            } catch (Exception ex) {
                log.warn("No se pudo alertar la ancla comprometida del {} a {}: {}",
                        event.fecha(), admin.getCorreo(), ex.getMessage());
            }
        }
    }
}
