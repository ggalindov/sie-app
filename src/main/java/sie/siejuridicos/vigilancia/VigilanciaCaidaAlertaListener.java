package sie.siejuridicos.vigilancia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import sie.siejuridicos.correo.EmailService;
import sie.siejuridicos.usuario.RolUsuario;
import sie.siejuridicos.usuario.UsuarioInterno;
import sie.siejuridicos.usuario.UsuarioInternoRepository;

// Reenvía VigilanciaExternaCaidaEvent a todo administrador general activo por correo -- mismo
// patrón que DocumentoIntegridadAlertaListener (ver ese archivo para el porqué de no
// construir una interfaz/abstracción de notificación aparte: EmailService.enviarHtml() ya
// cubre el log y el fallback si el SMTP no está configurado).
//
// Pedido explícito del usuario (2026-10-31, auditoría de SIE-Vigilancia): "implementa...
// alertas, anuncios de caidas" -- antes, un bloqueo real del CPNU/Publicaciones Procesales
// (como el que pasó varias veces esta misma sesión) solo quedaba visible en los logs del
// servidor, que nadie del equipo revisa en tiempo real.
@Component
public class VigilanciaCaidaAlertaListener {

    private static final Logger log = LoggerFactory.getLogger(VigilanciaCaidaAlertaListener.class);

    private final UsuarioInternoRepository usuarioInternoRepository;
    private final EmailService emailService;

    public VigilanciaCaidaAlertaListener(UsuarioInternoRepository usuarioInternoRepository, EmailService emailService) {
        this.usuarioInternoRepository = usuarioInternoRepository;
        this.emailService = emailService;
    }

    @EventListener
    public void onCaida(VigilanciaExternaCaidaEvent event) {
        for (UsuarioInterno admin : usuarioInternoRepository.findByRolAndActivoTrue(RolUsuario.ADMIN_GENERAL)) {
            if (admin.getCorreo() == null || admin.getCorreo().isBlank()) {
                continue;
            }
            try {
                emailService.enviarAlertaVigilanciaCaida(admin.getNombre(), admin.getCorreo(),
                        event.nombreServicio(), event.detalle());
            } catch (Exception ex) {
                log.warn("No se pudo alertar la caída de {} a {}: {}",
                        event.nombreServicio(), admin.getCorreo(), ex.getMessage());
            }
        }
    }
}
