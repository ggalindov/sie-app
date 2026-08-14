package sie.siejuridicos.correo;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import sie.siejuridicos.articulo.Articulo;
import sie.siejuridicos.marketing.SuscriptorMarketing;
import sie.siejuridicos.solicitud.Solicitud;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

// Cada método es @Async y atrapa sus propios errores: el envío de correo es un efecto
// secundario, nunca debe hacer fallar la operación principal (crear solicitud, agendar
// cita) ni bloquear la respuesta HTTP mientras se conecta al servidor SMTP.
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final DateTimeFormatter FORMATO_FECHA_CITA =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy 'a las' h:mm a", Locale.of("es", "CO"));

    private final JavaMailSender mailSender;
    private final String remitente;
    private final String correoAdmin;
    private final String nombreFirma;
    private final String sitioWeb;
    private final String whatsappUrl;

    public EmailService(JavaMailSender mailSender,
                         @Value("${app.correo.remitente}") String remitente,
                         @Value("${app.correo.admin}") String correoAdmin,
                         @Value("${app.firma.nombre}") String nombreFirma,
                         @Value("${app.firma.sitio-web}") String sitioWeb,
                         @Value("${app.firma.whatsapp:}") String whatsappUrl) {
        this.mailSender = mailSender;
        this.remitente = remitente;
        this.correoAdmin = correoAdmin;
        this.nombreFirma = nombreFirma;
        this.sitioWeb = sitioWeb;
        this.whatsappUrl = whatsappUrl;
    }

    @Async
    public void enviarConfirmacionYPromocionSolicitud(Solicitud solicitud) {
        String cuerpo = """
                <p>Hola %s,</p>
                <p>Gracias por escribirnos. Ya recibimos tu solicitud y uno de nuestros abogados la revisará
                a la brevedad para contactarte.</p>
                <p><strong>Mensaje recibido:</strong><br>%s</p>
                %s
                <p>Mientras tanto, te contamos que en %s acompañamos a nuestros clientes en:</p>
                <ul>
                    <li>Derecho Laboral</li>
                    <li>Derecho de Familia</li>
                    <li>Derecho Civil</li>
                    <li>Derecho Mercantil</li>
                    <li>Derecho Administrativo</li>
                    <li>Derecho Constitucional</li>
                </ul>
                <p>Puedes conocer más sobre nuestro trabajo en <a href="%s">%s</a>.</p>
                """.formatted(
                escaparHtml(solicitud.getNombre()),
                escaparHtml(solicitud.getMensaje()),
                botonWhatsapp(),
                nombreFirma, sitioWeb, sitioWeb
        );
        enviarHtml(solicitud.getCorreo(), "Hemos recibido tu solicitud - " + nombreFirma, cuerpo);
    }

    @Async
    public void enviarNotificacionAdminNuevaSolicitud(Solicitud solicitud) {
        String cuerpo = """
                <p>Llegó una nueva solicitud desde %s.</p>
                <ul>
                    <li><strong>Nombre:</strong> %s</li>
                    <li><strong>Correo:</strong> %s</li>
                    <li><strong>Teléfono:</strong> %s</li>
                    <li><strong>Mensaje:</strong> %s</li>
                </ul>
                <p>Revísala en el panel administrativo.</p>
                """.formatted(
                solicitud.getOrigen(),
                escaparHtml(solicitud.getNombre()),
                escaparHtml(solicitud.getCorreo()),
                solicitud.getTelefono() == null ? "(no proporcionado)" : escaparHtml(solicitud.getTelefono()),
                escaparHtml(solicitud.getMensaje())
        );
        enviarHtml(correoAdmin, "Nueva solicitud: " + solicitud.getNombre(), cuerpo);
    }

    @Async
    public void enviarConfirmacionCita(Solicitud solicitud) {
        String cuerpo = """
                <p>Hola %s,</p>
                <p>Confirmamos tu cita con %s para el <strong>%s</strong>.</p>
                <p>Si necesitas reprogramarla, por favor contáctanos respondiendo este correo%s.</p>
                <p>¡Te esperamos!</p>
                """.formatted(
                escaparHtml(solicitud.getNombre()),
                nombreFirma,
                solicitud.getFechaCita().format(FORMATO_FECHA_CITA),
                whatsappUrl.isBlank() ? "" : " o escribiéndonos por WhatsApp"
        );
        enviarHtml(solicitud.getCorreo(), "Confirmación de tu cita - " + nombreFirma, cuerpo);
    }

    @Async
    public void enviarNotificacionAdminNuevaCita(Solicitud solicitud) {
        String cuerpo = """
                <p>Se agendó una cita para el <strong>%s</strong>.</p>
                <ul>
                    <li><strong>Cliente:</strong> %s</li>
                    <li><strong>Correo:</strong> %s</li>
                    <li><strong>Teléfono:</strong> %s</li>
                </ul>
                """.formatted(
                solicitud.getFechaCita().format(FORMATO_FECHA_CITA),
                escaparHtml(solicitud.getNombre()),
                escaparHtml(solicitud.getCorreo()),
                solicitud.getTelefono() == null ? "(no proporcionado)" : escaparHtml(solicitud.getTelefono())
        );
        enviarHtml(correoAdmin, "Nueva cita agendada: " + solicitud.getNombre(), cuerpo);
    }

    @Async
    public void enviarRecordatorioCita(Solicitud solicitud) {
        String cuerpo = """
                <p>Hola %s,</p>
                <p>Este es un recordatorio de tu cita <strong>hoy</strong> con %s, a las
                <strong>%s</strong>.</p>
                %s
                <p>¡Te esperamos!</p>
                """.formatted(
                escaparHtml(solicitud.getNombre()),
                nombreFirma,
                solicitud.getFechaCita().format(DateTimeFormatter.ofPattern("h:mm a", Locale.of("es", "CO"))),
                botonWhatsapp()
        );
        enviarHtml(solicitud.getCorreo(), "Recordatorio: tu cita es hoy - " + nombreFirma, cuerpo);
    }

    // Se dispara una sola vez, en la transición BORRADOR -> PUBLICADO (ver
    // ArticuloService.actualizar), nunca en ediciones posteriores de un artículo
    // ya publicado. Un correo por suscriptor, todos en el mismo hilo async: la
    // lista de suscriptores del boletín es pequeña, no justifica una cola de
    // envíos por separado.
    @Async
    public void enviarNotificacionNuevoArticulo(Articulo articulo, List<SuscriptorMarketing> suscriptores) {
        String urlArticulo = sitioWeb + "/blog/" + articulo.getSlug();
        String cuerpoBase = """
                <p>Hola %s,</p>
                <p>Publicamos un nuevo artículo en el blog jurídico de %s:</p>
                <h2>%s</h2>
                <p>%s</p>
                <p><a href="%s">Leer el artículo completo</a></p>
                """;
        for (SuscriptorMarketing suscriptor : suscriptores) {
            String cuerpo = cuerpoBase.formatted(
                    escaparHtml(suscriptor.getNombre()),
                    nombreFirma,
                    escaparHtml(articulo.getTitulo()),
                    articulo.getResumen() == null ? "" : escaparHtml(articulo.getResumen()),
                    urlArticulo
            );
            enviarHtml(suscriptor.getCorreo(), "Nuevo artículo: " + articulo.getTitulo(), cuerpo);
        }
    }

    private String botonWhatsapp() {
        if (whatsappUrl.isBlank()) {
            return "";
        }
        return "<p><a href=\"%s\">Escríbenos por WhatsApp</a></p>".formatted(whatsappUrl);
    }

    private void enviarHtml(String destinatario, String asunto, String cuerpoHtml) {
        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, "UTF-8");
            helper.setFrom(remitente);
            helper.setTo(destinatario);
            helper.setSubject(asunto);
            helper.setText(cuerpoHtml, true);
            mailSender.send(mensaje);
        } catch (MessagingException | MailException ex) {
            log.warn("No se pudo enviar el correo '{}' a {}: {}", asunto, destinatario, ex.getMessage());
        }
    }

    private String escaparHtml(String texto) {
        return texto == null ? "" : texto
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
