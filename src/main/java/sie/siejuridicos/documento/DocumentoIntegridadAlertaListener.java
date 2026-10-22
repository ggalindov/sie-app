package sie.siejuridicos.documento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import sie.siejuridicos.correo.EmailService;
import sie.siejuridicos.documento.events.DocumentoAlteradoEvent;
import sie.siejuridicos.usuario.RolUsuario;
import sie.siejuridicos.usuario.UsuarioInterno;
import sie.siejuridicos.usuario.UsuarioInternoRepository;

// Reenvía DocumentoAlteradoEvent a todo administrador general activo por correo (ver
// EmailService.enviarAlertaDocumentoAlterado). A diferencia del motor de referencia, que
// define una interfaz IntegrityAlertSender con una implementación SMTP y un fallback a
// log (dos clases + condicional de Spring), aquí se reusa directamente el EmailService que
// ya existe en SIE: su propio enviarHtml() ya registra en el log y no lanza si el SMTP no
// está configurado, así que no hace falta duplicar ese mecanismo.
@Component
public class DocumentoIntegridadAlertaListener {

    private static final Logger log = LoggerFactory.getLogger(DocumentoIntegridadAlertaListener.class);

    private final UsuarioInternoRepository usuarioInternoRepository;
    private final EmailService emailService;

    public DocumentoIntegridadAlertaListener(UsuarioInternoRepository usuarioInternoRepository, EmailService emailService) {
        this.usuarioInternoRepository = usuarioInternoRepository;
        this.emailService = emailService;
    }

    @EventListener
    public void onDocumentoAlterado(DocumentoAlteradoEvent event) {
        for (UsuarioInterno admin : usuarioInternoRepository.findByRolAndActivoTrue(RolUsuario.ADMIN_GENERAL)) {
            if (admin.getCorreo() == null || admin.getCorreo().isBlank()) {
                continue;
            }
            try {
                emailService.enviarAlertaDocumentoAlterado(admin.getNombre(), admin.getCorreo(),
                        event.documento().getNombre(), event.motivo());
            } catch (Exception ex) {
                log.warn("No se pudo alertar la alteración del documento {} a {}: {}",
                        event.documento().getId(), admin.getCorreo(), ex.getMessage());
            }
        }
    }
}
