package sie.siejuridicos.documento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import sie.siejuridicos.correo.EmailService;
import sie.siejuridicos.documento.events.DocumentoListoParaFirmarEvent;
import sie.siejuridicos.documento.events.DocumentoRemitidoEvent;
import sie.siejuridicos.documento.events.OtpGeneradoEvent;
import sie.siejuridicos.whatsapp.WhatsAppService;

// Escucha los eventos de dominio del motor de firma y los despacha al canal correcto.
// El ENLACE de firma viaja por el canal que gerencia eligió al asignar la parte
// (firmante.getCanalNotificacion(), forzado a EMAIL si es empresa, ver
// DocumentoFirmaService.ParteAFirmar). El CÓDIGO OTP y el documento final, en cambio,
// SIEMPRE van por correo sin importar ese canal (pedido explícito del usuario para el OTP;
// el documento final ya iba siempre por correo porque WhatsApp no puede llevar un adjunto).
//
// IMPORTANTE: estos métodos corren de forma SÍNCRONA, en el mismo hilo y la misma
// transacción que publica el evento (Spring usa un multicaster síncrono por defecto). Eso
// es lo que permite leer aquí relaciones LAZY de DocumentoFirmante (documento, clienteCrm,
// staff) sin LazyInitializationException, pese a que el proyecto corre con
// spring.jpa.open-in-view=false. Los valores que SÍ importan (nombre, destino, nombre del
// documento) se extraen aquí a String planos antes de llamar a los métodos @Async de
// EmailService/WhatsAppService -- esos ya no tocan ninguna entidad.
@Component
public class DocumentoNotificacionListener {

    private static final Logger log = LoggerFactory.getLogger(DocumentoNotificacionListener.class);

    private final EmailService emailService;
    private final WhatsAppService whatsAppService;
    private final boolean bloqueoTotalClientes;

    public DocumentoNotificacionListener(EmailService emailService, WhatsAppService whatsAppService,
                                          @Value("${app.bloqueo-total-clientes:true}") boolean bloqueoTotalClientes) {
        this.emailService = emailService;
        this.whatsAppService = whatsAppService;
        this.bloqueoTotalClientes = bloqueoTotalClientes;
    }

    @EventListener
    public void onDocumentoListoParaFirmar(DocumentoListoParaFirmarEvent event) {
        DocumentoFirmante firmante = event.firmante();
        String nombreDocumento = firmante.getDocumento().getNombre();
        try {
            if (DocumentoFirmante.CANAL_EMAIL.equals(firmante.getCanalNotificacion())) {
                emailService.enviarEnlaceFirmaDocumento(firmante.getNombreCapturado(), firmante.getDestinoNotificacion(),
                        nombreDocumento, event.urlFirma());
            } else if (DocumentoFirmante.CANAL_WHATSAPP.equals(firmante.getCanalNotificacion())) {
                whatsAppService.enviarEnlaceFirmaDocumento(firmante.getNombreCapturado(), firmante.getDestinoNotificacion(),
                        nombreDocumento, event.urlFirma());
            } else {
                log.warn("Firmante {} sin canal de notificación válido; no se pudo enviar el enlace de firma.", firmante.getId());
            }
        } catch (Exception ex) {
            log.warn("No se pudo enviar el enlace de firma del firmante {}: {}", firmante.getId(), ex.getMessage());
        }
        // Solo con app.bloqueo-total-clientes=true -- es decir, solo cuando por diseño NO
        // hay destinatarios reales detrás de ningún correo/teléfono registrado (ver
        // EmailService/WhatsAppService) -- se imprime el enlace en texto plano, para poder
        // probar el wizard público de firma en local sin credenciales reales de SMTP/
        // WhatsApp. Nunca se habilita en producción: ahí bloqueoTotalClientes es false y
        // esta rama nunca se ejecuta.
        if (bloqueoTotalClientes) {
            log.warn("[SOLO LOCAL] Enlace de firma para {} ({}): {}", firmante.getNombreCapturado(), firmante.getId(), event.urlFirma());
        }
    }

    // Pedido explícito del usuario: el código OTP SIEMPRE se manda por correo, sin importar
    // el canal elegido para el enlace de firma (igual criterio que onDocumentoRemitido, ver
    // abajo) -- un código de un solo uso además exigía la categoría AUTHENTICATION especial
    // de Meta para WhatsApp, que requiere habilitar un permiso aparte a nivel de cuenta; al
    // ir siempre por correo, ese segundo canal/plantilla deja de ser necesario por completo.
    @EventListener
    public void onOtpGenerado(OtpGeneradoEvent event) {
        DocumentoFirmante firmante = event.firmante();
        String correo = firmante.getClienteCrm() != null ? firmante.getClienteCrm().getCorreo() : null;
        if (correo == null || correo.isBlank()) {
            log.warn("Firmante {} sin correo registrado; no se pudo enviar el código de verificación.", firmante.getId());
        } else {
            try {
                emailService.enviarCodigoOtpFirma(firmante.getNombreCapturado(), correo, event.codigoPlano());
            } catch (Exception ex) {
                log.warn("No se pudo enviar el código de verificación del firmante {}: {}", firmante.getId(), ex.getMessage());
            }
        }
        if (bloqueoTotalClientes) {
            log.warn("[SOLO LOCAL] Código OTP para {} ({}): {}", firmante.getNombreCapturado(), firmante.getId(), event.codigoPlano());
        }
    }

    // El documento final SIEMPRE se entrega por correo, sin importar el canal elegido para
    // el enlace (WhatsApp no puede llevar un adjunto generado dinámicamente) -- la
    // asignación de la parte exige correo en todos los casos precisamente para esto, ver
    // DocumentoAdminController.
    @EventListener
    public void onDocumentoRemitido(DocumentoRemitidoEvent event) {
        DocumentoFirmante firmante = event.firmante();
        if (firmante.getClienteCrm() == null || firmante.getClienteCrm().getCorreo() == null
                || firmante.getClienteCrm().getCorreo().isBlank()) {
            log.warn("Firmante {} sin correo registrado; no se pudo remitir el documento final.", firmante.getId());
            return;
        }
        try {
            emailService.enviarDocumentoFirmadoFinal(firmante.getNombreCapturado(), firmante.getClienteCrm().getCorreo(),
                    firmante.getDocumento().getNombre(), event.pdfFinal());
        } catch (Exception ex) {
            log.warn("No se pudo remitir el documento final al firmante {}: {}", firmante.getId(), ex.getMessage());
        }
    }
}
