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
import sie.siejuridicos.articulo.TipoContenido;
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

    // Boletín automático (ver ArticuloService.notificarPublicacion): se dispara de inmediato
    // en la transición BORRADOR -> PUBLICADO, nunca en ediciones posteriores de un artículo
    // ya publicado. Recibe una lista (hoy siempre de un solo artículo/noticia) en vez de un
    // único Articulo para poder reutilizar la misma plantilla si en el futuro se agrupa más
    // de una publicación en un solo envío.
    @Async
    public void enviarNotificacionPublicacion(List<Articulo> publicaciones, List<SuscriptorMarketing> suscriptores) {
        StringBuilder listado = new StringBuilder();
        for (Articulo articulo : publicaciones) {
            String urlArticulo = sitioWeb + "/blog/" + articulo.getSlug();
            String etiqueta = articulo.getTipoContenido() == TipoContenido.NOTICIA ? "Noticia" : "Blog";
            listado.append("""
                    <div style="margin-bottom:20px;">
                    <p style="margin:0;font-size:11px;text-transform:uppercase;letter-spacing:1px;color:#a08a3c;">%s</p>
                    <h3 style="margin:4px 0;">%s</h3>
                    <p style="margin:0 0 4px;">%s</p>
                    <a href="%s">Leer completo</a>
                    </div>
                    """.formatted(
                    etiqueta,
                    escaparHtml(articulo.getTitulo()),
                    articulo.getResumen() == null ? "" : escaparHtml(articulo.getResumen()),
                    urlArticulo
            ));
        }

        String asunto = publicaciones.size() == 1
                ? "Nueva publicación en " + nombreFirma
                : publicaciones.size() + " publicaciones nuevas en " + nombreFirma;

        for (SuscriptorMarketing suscriptor : suscriptores) {
            String cuerpo = """
                    <p>Hola %s,</p>
                    <p>Acabamos de publicar esto en %s:</p>
                    %s
                    """.formatted(escaparHtml(suscriptor.getNombre()), nombreFirma, listado);
            enviarHtml(suscriptor.getCorreo(), asunto, cuerpo);
        }
    }

    // Se dispara solo cuando la fila de suscriptores_marketing es realmente nueva (ver
    // SuscriptorMarketingService.suscribir), sin importar si la persona llegó desde el
    // formulario de "Newsletter" del home o desde el checkbox aceptaMarketing del
    // formulario de Agendar asesoría: mismo destino, mismo correo de bienvenida.
    @Async
    public void enviarBienvenidaBoletin(String nombre, String correo) {
        String cuerpo = """
                <p>Hola %s,</p>
                <p>Gracias por suscribirte al boletín de %s. A partir de ahora te avisaremos
                por este medio cuando publiquemos artículos nuevos en nuestro blog jurídico y
                cuando haya cambios normativos relevantes.</p>
                <p>Como agradecimiento por suscribirte, tienes acceso a un descuento especial en
                tu primera consulta con nosotros. En los próximos días te escribiremos a este
                mismo correo con los detalles.</p>
                <p>Si en algún momento deseas dejar de recibir estos correos, escríbenos y con
                gusto te damos de baja.</p>
                <p>Puedes conocer más sobre nuestro trabajo en <a href="%s">%s</a>.</p>
                """.formatted(
                escaparHtml(nombre),
                nombreFirma, sitioWeb, sitioWeb
        );
        enviarHtml(correo, "Bienvenido al boletín de " + nombreFirma, cuerpo);
    }

    // Boletín mensual con resumen de cambios normativos (compuesto por el admin desde el
    // panel, ver BoletinService): el cuerpo llega como texto plano desde un textarea, se
    // escapa y se parte en párrafos por línea en blanco, igual que cualquier otro correo
    // HTML de este servicio. Un correo por destinatario, todos en el mismo hilo async: la
    // lista de suscriptores es pequeña (mismo razonamiento que enviarNotificacionNuevoArticulo).
    @Async
    public void enviarBoletin(String asunto, String cuerpoTexto, List<SuscriptorMarketing> destinatarios) {
        String cuerpoHtml = escaparHtmlConParrafos(cuerpoTexto);
        for (SuscriptorMarketing suscriptor : destinatarios) {
            String cuerpo = """
                    <p>Hola %s,</p>
                    %s
                    <p style="margin-top:24px;font-size:12px;color:#888;">Recibes este boletín porque estás
                    suscrito a las novedades de %s. Si deseas dejar de recibirlo, escríbenos y con gusto te
                    damos de baja.</p>
                    """.formatted(escaparHtml(suscriptor.getNombre()), cuerpoHtml, nombreFirma);
            enviarHtml(suscriptor.getCorreo(), asunto, cuerpo);
        }
    }

    private String escaparHtmlConParrafos(String texto) {
        String[] parrafos = texto.trim().split("\\n\\s*\\n");
        StringBuilder html = new StringBuilder();
        for (String parrafo : parrafos) {
            html.append("<p>").append(escaparHtml(parrafo.trim()).replace("\n", "<br>")).append("</p>");
        }
        return html.toString();
    }

    // Se envía una sola vez, al crear el caso desde el panel (ver CasoService.crear). Es la
    // única forma en que el cliente recibe su código: no hay cuenta ni login, solo esta
    // consulta por código en /consulta-caso (RF: "sin necesidad de crear cuenta").
    @Async
    public void enviarCodigoCaso(String nombreCliente, String correo, String codigoUnico, String tipoCaso) {
        String cuerpo = """
                <p>Hola %s,</p>
                <p>Registramos tu caso de <strong>%s</strong> en %s. Puedes consultar el estado de tu
                proceso en cualquier momento, sin necesidad de crear una cuenta, con este código:</p>
                <p style="font-size:22px;font-weight:bold;letter-spacing:2px;margin:16px 0;">%s</p>
                <p>Ingresa el código en <a href="%s/consulta-caso">%s/consulta-caso</a> para ver en qué
                etapa va tu proceso.</p>
                <p>Guarda este código, lo necesitarás para futuras consultas.</p>
                """.formatted(
                escaparHtml(nombreCliente),
                escaparHtml(tipoCaso),
                nombreFirma,
                codigoUnico,
                sitioWeb, sitioWeb
        );
        enviarHtml(correo, "Tu código de consulta de caso - " + nombreFirma, cuerpo);
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
