package sie.siejuridicos.documento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import sie.siejuridicos.correo.EmailService;
import sie.siejuridicos.documento.events.DocumentoFirmaRechazadaEvent;
import sie.siejuridicos.usuario.RolUsuario;
import sie.siejuridicos.usuario.UsuarioInterno;
import sie.siejuridicos.usuario.UsuarioInternoRepository;

// Mismo patrón que DocumentoIntegridadAlertaListener: reenvía el evento a todo administrador
// general activo por correo.
@Component
public class DocumentoFirmaRechazadaAlertaListener {

    private static final Logger log = LoggerFactory.getLogger(DocumentoFirmaRechazadaAlertaListener.class);

    private final UsuarioInternoRepository usuarioInternoRepository;
    private final EmailService emailService;

    public DocumentoFirmaRechazadaAlertaListener(UsuarioInternoRepository usuarioInternoRepository, EmailService emailService) {
        this.usuarioInternoRepository = usuarioInternoRepository;
        this.emailService = emailService;
    }

    @EventListener
    public void onFirmaRechazada(DocumentoFirmaRechazadaEvent event) {
        for (UsuarioInterno admin : usuarioInternoRepository.findByRolAndActivoTrue(RolUsuario.ADMIN_GENERAL)) {
            if (admin.getCorreo() == null || admin.getCorreo().isBlank()) {
                continue;
            }
            try {
                emailService.enviarAlertaFirmaRechazada(admin.getNombre(), admin.getCorreo(),
                        event.documento().getNombre(), event.nombreFirmante(), event.motivo());
            } catch (Exception ex) {
                log.warn("No se pudo alertar el rechazo de firma del documento {} a {}: {}",
                        event.documento().getId(), admin.getCorreo(), ex.getMessage());
            }
        }
    }
}
