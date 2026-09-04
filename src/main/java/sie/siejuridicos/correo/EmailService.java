package sie.siejuridicos.correo;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import sie.siejuridicos.articulo.Articulo;
import sie.siejuridicos.articulo.TipoContenido;
import sie.siejuridicos.marketing.SuscriptorMarketing;
import sie.siejuridicos.solicitud.Solicitud;

import java.io.UnsupportedEncodingException;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// Cada método es @Async y atrapa sus propios errores: el envío de correo es un efecto
// secundario, nunca debe hacer fallar la operación principal (crear solicitud, agendar
// cita) ni bloquear la respuesta HTTP mientras se conecta al servidor SMTP.
//
// Todos los correos pasan por plantilla(): encabezado con el logo real de la firma,
// cuerpo específico de cada método, y pie de marca con sello e información de contacto.
// El logo y el sello se cargan una sola vez al construir el servicio (no en cada envío)
// y se embeben como imágenes inline (Content-ID), no como enlaces a una URL externa: así
// se ven desde el primer segundo en clientes de correo que bloquean imágenes remotas por
// defecto (Outlook, Gmail en la vista previa), en vez de mostrar un hueco roto hasta que
// el destinatario decide "mostrar imágenes".
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final DateTimeFormatter FORMATO_FECHA_CITA =
            DateTimeFormatter.ofPattern("EEEE d 'de' MMMM 'de' yyyy 'a las' h:mm a", Locale.of("es", "CO"));

    private static final String CID_LOGO = "logoFirma";
    private static final String CID_SELLO = "selloFirma";
    private static final String COLOR_DORADO = "#D9A925";
    private static final String COLOR_FONDO_PAGINA = "#EDE9E0";
    private static final String COLOR_TEXTO = "#2B2620";
    private static final String COLOR_PIE_FONDO = "#181611";
    private static final String COLOR_PIE_TEXTO_SUAVE = "#B9B2A2";
    private static final String COLOR_PIE_TEXTO_TENUE = "#736C5C";

    private final JavaMailSender mailSender;
    private final String remitente;
    private final String correoAdmin;
    private final String correoAvisoRedes;
    private final String nombreFirma;
    private final String sitioWeb;
    private final String whatsappUrl;
    private final String telefono;
    private final String ciudad;
    private final Resource logoResource;
    private final Resource selloResource;

    public EmailService(JavaMailSender mailSender,
                         @Value("${app.correo.remitente}") String remitente,
                         @Value("${app.correo.admin}") String correoAdmin,
                         @Value("${app.redes-sociales.correo-aviso}") String correoAvisoRedes,
                         @Value("${app.firma.nombre}") String nombreFirma,
                         @Value("${app.firma.sitio-web}") String sitioWeb,
                         @Value("${app.firma.whatsapp:}") String whatsappUrl,
                         @Value("${app.firma.telefono:}") String telefono,
                         @Value("${app.firma.ciudad:}") String ciudad) {
        this.mailSender = mailSender;
        this.remitente = remitente;
        this.correoAdmin = correoAdmin;
        this.correoAvisoRedes = correoAvisoRedes;
        this.nombreFirma = nombreFirma;
        this.sitioWeb = sitioWeb;
        this.whatsappUrl = whatsappUrl;
        this.telefono = telefono;
        this.ciudad = ciudad;
        this.logoResource = new ClassPathResource("correo/logo.png");
        this.selloResource = new ClassPathResource("correo/sello.png");
    }

    @Async
    public void enviarConfirmacionYPromocionSolicitud(Solicitud solicitud) {
        String cuerpo = """
                <p style="margin:0 0 16px;">Hola %s,</p>
                <p style="margin:0 0 16px;">Gracias por escribirnos. Hemos recibido tu solicitud y uno de
                nuestros abogados la revisará a la brevedad para ponerse en contacto contigo.</p>
                <table role="presentation" cellpadding="0" cellspacing="0" style="width:100%%;margin:0 0 16px;background-color:#F7F4EC;border-left:3px solid {{DORADO}};">
                <tr><td style="padding:14px 18px;">
                <p style="margin:0 0 4px;font-size:11px;text-transform:uppercase;letter-spacing:0.6px;color:{{DORADO}};font-family:Arial,Helvetica,sans-serif;">Mensaje recibido</p>
                <p style="margin:0;">%s</p>
                </td></tr>
                </table>
                %s
                <p style="margin:0 0 8px;">Mientras tanto, te contamos que en %s acompañamos a nuestros
                clientes en:</p>
                <ul style="margin:0 0 16px;padding-left:20px;">
                    <li>Derecho Laboral</li>
                    <li>Derecho de Familia</li>
                    <li>Derecho Civil</li>
                    <li>Derecho Mercantil</li>
                    <li>Derecho Administrativo</li>
                    <li>Derecho Constitucional</li>
                </ul>
                <p style="margin:0 0 20px;">Puedes conocer más sobre nuestro trabajo en
                <a href="%s" style="color:{{DORADO}};">%s</a>.</p>
                %s
                """.replace("{{DORADO}}", COLOR_DORADO)
                .formatted(
                        escaparHtml(solicitud.getNombre()),
                        escaparHtml(solicitud.getMensaje()),
                        botonWhatsapp(),
                        nombreFirma, sitioWeb, sitioWeb,
                        firmaCierre()
                );
        enviarHtml(solicitud.getCorreo(), "Hemos recibido tu solicitud - " + nombreFirma, cuerpo);
    }

    @Async
    public void enviarNotificacionAdminNuevaSolicitud(Solicitud solicitud) {
        String cuerpo = """
                <p style="margin:0 0 12px;">Llegó una nueva solicitud desde <strong>%s</strong>.</p>
                <table role="presentation" cellpadding="0" cellspacing="0" style="width:100%%;margin:0 0 16px;border-collapse:collapse;">
                    <tr><td style="padding:6px 0;border-bottom:1px solid #E4DFD1;color:#736C5C;width:120px;">Nombre</td><td style="padding:6px 0;border-bottom:1px solid #E4DFD1;">%s</td></tr>
                    <tr><td style="padding:6px 0;border-bottom:1px solid #E4DFD1;color:#736C5C;">Correo</td><td style="padding:6px 0;border-bottom:1px solid #E4DFD1;">%s</td></tr>
                    <tr><td style="padding:6px 0;border-bottom:1px solid #E4DFD1;color:#736C5C;">Teléfono</td><td style="padding:6px 0;border-bottom:1px solid #E4DFD1;">%s</td></tr>
                    <tr><td style="padding:6px 0;color:#736C5C;vertical-align:top;">Mensaje</td><td style="padding:6px 0;">%s</td></tr>
                </table>
                <p style="margin:0;">Revísala en el panel administrativo para darle seguimiento.</p>
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
                <p style="margin:0 0 16px;">Hola %s,</p>
                <p style="margin:0 0 16px;">Confirmamos tu cita con %s para el
                <strong>%s</strong>.</p>
                <p style="margin:0 0 20px;">Si necesitas reprogramarla, por favor contáctanos respondiendo
                este correo%s.</p>
                %s
                """.formatted(
                escaparHtml(solicitud.getNombre()),
                nombreFirma,
                solicitud.getFechaCita().format(FORMATO_FECHA_CITA),
                whatsappUrl.isBlank() ? "" : " o escribiéndonos por WhatsApp",
                firmaCierre()
        );
        enviarHtml(solicitud.getCorreo(), "Confirmación de tu cita - " + nombreFirma, cuerpo);
    }

    @Async
    public void enviarNotificacionAdminNuevaCita(Solicitud solicitud) {
        String cuerpo = """
                <p style="margin:0 0 12px;">Se agendó una nueva cita para el
                <strong>%s</strong>.</p>
                <table role="presentation" cellpadding="0" cellspacing="0" style="width:100%%;border-collapse:collapse;">
                    <tr><td style="padding:6px 0;border-bottom:1px solid #E4DFD1;color:#736C5C;width:120px;">Cliente</td><td style="padding:6px 0;border-bottom:1px solid #E4DFD1;">%s</td></tr>
                    <tr><td style="padding:6px 0;border-bottom:1px solid #E4DFD1;color:#736C5C;">Correo</td><td style="padding:6px 0;border-bottom:1px solid #E4DFD1;">%s</td></tr>
                    <tr><td style="padding:6px 0;color:#736C5C;">Teléfono</td><td style="padding:6px 0;">%s</td></tr>
                </table>
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
                <p style="margin:0 0 16px;">Hola %s,</p>
                <p style="margin:0 0 20px;">Este es un recordatorio de tu cita <strong>hoy</strong> con
                %s, a las <strong>%s</strong>.</p>
                %s
                %s
                """.formatted(
                escaparHtml(solicitud.getNombre()),
                nombreFirma,
                solicitud.getFechaCita().format(DateTimeFormatter.ofPattern("h:mm a", Locale.of("es", "CO"))),
                botonWhatsapp(),
                firmaCierre()
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
                    <table role="presentation" cellpadding="0" cellspacing="0" style="width:100%%;margin:0 0 18px;">
                    <tr><td style="border-left:3px solid %s;padding:2px 0 2px 16px;">
                    <p style="margin:0;font-size:11px;text-transform:uppercase;letter-spacing:0.6px;color:%s;font-family:Arial,Helvetica,sans-serif;">%s</p>
                    <p style="margin:4px 0 6px;font-size:17px;font-weight:bold;color:%s;">%s</p>
                    <p style="margin:0 0 8px;">%s</p>
                    <a href="%s" style="color:%s;font-weight:bold;">Leer artículo completo &rarr;</a>
                    </td></tr>
                    </table>
                    """.formatted(
                    COLOR_DORADO, COLOR_DORADO,
                    etiqueta,
                    COLOR_TEXTO, escaparHtml(articulo.getTitulo()),
                    articulo.getResumen() == null ? "" : escaparHtml(articulo.getResumen()),
                    urlArticulo, COLOR_DORADO
            ));
        }

        String asunto = publicaciones.size() == 1
                ? "Nueva publicación en " + nombreFirma
                : publicaciones.size() + " publicaciones nuevas en " + nombreFirma;

        for (SuscriptorMarketing suscriptor : suscriptores) {
            String cuerpo = """
                    <p style="margin:0 0 16px;">Hola %s,</p>
                    <p style="margin:0 0 20px;">Acabamos de publicar esto en %s:</p>
                    %s
                    %s
                    """.formatted(escaparHtml(suscriptor.getNombre()), nombreFirma, listado, firmaCierre());
            enviarHtml(suscriptor.getCorreo(), asunto, cuerpo, true);
        }
    }

    // Pedido explícito del usuario: aparte del boletín a los suscriptores (arriba), un aviso
    // aparte a la persona encargada de redes sociales de la firma -- dispara desde el mismo
    // punto (ArticuloService.notificarPublicacion, la transición real BORRADOR -> PUBLICADO),
    // pero es un correo propio, no una copia del boletín: el destinatario es interno (ver
    // app.redes-sociales.correo-aviso), no un suscriptor público, y el llamado a la acción es
    // "sube esto a redes", no "lee el artículo".
    @Async
    public void enviarAvisoRedesSociales(Articulo publicado) {
        String urlArticulo = sitioWeb + "/blog/" + publicado.getSlug();
        String etiqueta = publicado.getTipoContenido() == TipoContenido.NOTICIA ? "una noticia" : "un blog";
        String cuerpo = """
                <p style="margin:0 0 16px;">Hola,</p>
                <p style="margin:0 0 16px;">Se acaba de publicar %s nuevo en %s:</p>
                <table role="presentation" cellpadding="0" cellspacing="0" style="width:100%%;margin:0 0 18px;">
                <tr><td style="border-left:3px solid %s;padding:2px 0 2px 16px;">
                <p style="margin:0 0 6px;font-size:17px;font-weight:bold;color:%s;">%s</p>
                <a href="%s" style="color:%s;font-weight:bold;">Ver artículo &rarr;</a>
                </td></tr>
                </table>
                <p style="margin:0 0 20px;">Recuerda subirlo a las redes sociales de la firma.</p>
                %s
                """.formatted(
                etiqueta, nombreFirma,
                COLOR_DORADO,
                COLOR_TEXTO, escaparHtml(publicado.getTitulo()),
                urlArticulo, COLOR_DORADO,
                firmaCierre()
        );
        enviarHtml(correoAvisoRedes, "Nueva publicación para subir a redes: " + publicado.getTitulo(), cuerpo);
    }

    // Se dispara solo cuando la fila de suscriptores_marketing es realmente nueva (ver
    // SuscriptorMarketingService.suscribir), sin importar si la persona llegó desde el
    // formulario de "Newsletter" del home o desde el checkbox aceptaMarketing del
    // formulario de Agendar asesoría: mismo destino, mismo correo de bienvenida.
    @Async
    public void enviarBienvenidaBoletin(String nombre, String correo) {
        String cuerpo = """
                <p style="margin:0 0 16px;">Hola %s,</p>
                <p style="margin:0 0 16px;">Gracias por suscribirte al boletín de %s. A partir de ahora te
                avisaremos por este medio cuando publiquemos artículos nuevos en nuestro blog jurídico y
                cuando haya cambios normativos relevantes para tu empresa o tu caso.</p>
                <p style="margin:0 0 16px;">Como agradecimiento por suscribirte, tienes acceso a un
                descuento especial en tu primera consulta con nosotros. En los próximos días te
                escribiremos a este mismo correo con los detalles.</p>
                <p style="margin:0 0 20px;">Si en algún momento deseas dejar de recibir estos correos,
                escríbenos y con gusto te damos de baja.</p>
                <p style="margin:0 0 20px;">Puedes conocer más sobre nuestro trabajo en
                <a href="%s" style="color:%s;">%s</a>.</p>
                %s
                """.formatted(
                escaparHtml(nombre),
                nombreFirma, sitioWeb, COLOR_DORADO, sitioWeb,
                firmaCierre()
        );
        enviarHtml(correo, "Bienvenido al boletín de " + nombreFirma, cuerpo, true);
    }

    // Boletín mensual con resumen de cambios normativos (compuesto por el admin desde el
    // panel, ver BoletinService): el cuerpo llega como texto plano desde un textarea, se
    // escapa y se parte en párrafos por línea en blanco, igual que cualquier otro correo
    // HTML de este servicio. Un correo por destinatario, todos en el mismo hilo async: la
    // lista de suscriptores es pequeña (mismo razonamiento que enviarNotificacionPublicacion).
    @Async
    public void enviarBoletin(String asunto, String cuerpoTexto, List<SuscriptorMarketing> destinatarios) {
        String cuerpoHtml = escaparHtmlConParrafos(cuerpoTexto);
        for (SuscriptorMarketing suscriptor : destinatarios) {
            String cuerpo = """
                    <p style="margin:0 0 16px;">Hola %s,</p>
                    %s
                    <p style="margin:24px 0 0;font-size:12px;color:%s;">Recibes este boletín porque estás
                    suscrito a las novedades de %s. Si deseas dejar de recibirlo, escríbenos y con gusto te
                    damos de baja.</p>
                    """.formatted(escaparHtml(suscriptor.getNombre()), cuerpoHtml, COLOR_PIE_TEXTO_TENUE, nombreFirma);
            enviarHtml(suscriptor.getCorreo(), asunto, cuerpo, true);
        }
    }

    private String escaparHtmlConParrafos(String texto) {
        String[] parrafos = texto.trim().split("\\n\\s*\\n");
        StringBuilder html = new StringBuilder();
        for (String parrafo : parrafos) {
            html.append("<p style=\"margin:0 0 14px;\">")
                    .append(escaparHtml(parrafo.trim()).replace("\n", "<br>"))
                    .append("</p>");
        }
        return html.toString();
    }

    // Se envía una sola vez, al crear el caso desde el panel (ver CasoService.crear). Es la
    // única forma en que el cliente recibe su radicado: no hay cuenta ni login, solo esta
    // consulta por radicado en /consulta-caso. El estado real se lee en vivo del Google
    // Sheets de la firma (ver HojaCalculoService), por eso el correo ya no menciona un "tipo
    // de caso": ese dato ahora vive en la hoja, no se captura al registrar el caso.
    @Async
    public void enviarCodigoCaso(String nombreCliente, String correo, String radicadoId) {
        enviarCodigoCasoSincrono(nombreCliente, correo, radicadoId);
    }

    // Variante síncrona (devuelve si de verdad se envió) para el envío masivo controlado de
    // CasoService.enviarCorreosPendientes(): ese método necesita saber el resultado real de
    // cada envío -- uno por uno, con una pausa entre cada uno -- para no marcar como
    // "notificado" a alguien que en realidad nunca recibió nada, y para no disparar todos los
    // correos en paralelo (eso fue justo lo que hizo que Gmail bloqueara la cuenta a mitad de
    // un envío masivo real). La versión @Async de arriba sigue existiendo para el único caso
    // que la necesita como "dispara y olvida": crear un caso manual desde el panel, un envío
    // aislado que no debe bloquear la respuesta HTTP.
    public boolean enviarCodigoCasoSincrono(String nombreCliente, String correo, String radicadoId) {
        // Última barrera antes de mandar nada, sin importar quién llame este método: jamás se
        // envía un correo de "tu radicado es..." sin un radicado real (CasoService ya filtra
        // esto antes de llegar aquí, pero un correo con un radicado vacío/roto sería peor que
        // no mandarlo -- confundiría al cliente).
        if (radicadoId == null || radicadoId.isBlank()) {
            log.warn("Se intentó enviar el correo de radicado sin un radicado real -- se canceló el envío.");
            return false;
        }
        String cuerpo = """
                <p style="margin:0 0 16px;">Hola %s,</p>
                <p style="margin:0 0 16px;">Registramos tu caso en %s. Puedes consultar el estado de tu
                proceso en cualquier momento, sin necesidad de crear una cuenta, con tu número de
                radicado:</p>
                <table role="presentation" cellpadding="0" cellspacing="0" style="width:100%%;margin:0 0 18px;">
                <tr><td align="center" style="background-color:#F7F4EC;border:1px dashed %s;padding:16px;">
                <span style="font-size:24px;font-weight:bold;letter-spacing:3px;color:%s;font-family:Arial,Helvetica,sans-serif;">%s</span>
                </td></tr>
                </table>
                <p style="margin:0 0 8px;">Ingresa tu radicado en
                <a href="%s/consulta-caso" style="color:%s;">%s/consulta-caso</a> para ver el estado
                actual de tu proceso.</p>
                <p style="margin:0 0 20px;">Guarda este radicado en un lugar seguro: lo necesitarás para
                futuras consultas.</p>
                %s
                """.formatted(
                escaparHtml(nombreCliente),
                nombreFirma,
                COLOR_DORADO, COLOR_TEXTO, escaparHtml(radicadoId),
                sitioWeb, COLOR_DORADO, sitioWeb,
                firmaCierre()
        );
        return enviarHtml(correo, "Tu número de radicado para consultar tu caso - " + nombreFirma, cuerpo, false);
    }

    // Recordatorio mensual de cobro (ver cobro.CobroService.enviarRecordatorios(), disparado
    // el día 1 de cada mes o manualmente desde el panel). "Tipo tirilla": destaca el monto
    // como el correo de radicado destaca el código, y a propósito reafirma que el caso sigue
    // activo -- pedido explícito del usuario: "indicandoles que su proceso sigue siendo
    // trabajado por SIE".
    @Async
    public void enviarTirillaCobro(String nombreCliente, String correo, String honorariosTexto) {
        enviarTirillaCobroSincrono(nombreCliente, correo, honorariosTexto);
    }

    // Variante síncrona (devuelve si de verdad se envió), misma razón que
    // enviarCodigoCasoSincrono(): la usa el envío masivo controlado de
    // CobroService.enviarRecordatorios(), uno por uno con pausa entre cada uno.
    public boolean enviarTirillaCobroSincrono(String nombreCliente, String correo, String honorariosTexto) {
        String cuerpo = """
                <p style="margin:0 0 16px;">Hola %s,</p>
                <p style="margin:0 0 16px;">Te recordamos el valor pendiente correspondiente a los
                honorarios de tu proceso con %s este mes:</p>
                <table role="presentation" cellpadding="0" cellspacing="0" style="width:100%%;margin:0 0 18px;">
                <tr><td align="center" style="background-color:#F7F4EC;border:1px dashed %s;padding:16px;">
                <span style="font-size:26px;font-weight:bold;color:%s;font-family:Arial,Helvetica,sans-serif;">%s</span>
                </td></tr>
                </table>
                <p style="margin:0 0 16px;">Queremos que sepas que seguimos trabajando activamente en tu
                proceso: nuestro equipo continúa haciéndole seguimiento con el mismo compromiso de
                siempre.</p>
                <p style="margin:0 0 20px;">Si ya realizaste este pago o tienes alguna duda al respecto,
                por favor contáctanos respondiendo este correo%s.</p>
                %s
                """.formatted(
                escaparHtml(nombreCliente),
                nombreFirma,
                COLOR_DORADO, COLOR_TEXTO, escaparHtml(honorariosTexto),
                whatsappUrl.isBlank() ? "" : " o escribiéndonos por WhatsApp",
                firmaCierre()
        );
        return enviarHtml(correo, "Recordatorio de pago pendiente - " + nombreFirma, cuerpo, false);
    }

    private String firmaCierre() {
        return """
                <p style="margin:24px 0 0;">Atentamente,<br><strong>Equipo %s</strong></p>
                """.formatted(nombreFirma);
    }

    private String botonWhatsapp() {
        if (whatsappUrl.isBlank()) {
            return "";
        }
        return """
                <table role="presentation" cellpadding="0" cellspacing="0" style="margin:0 0 20px;">
                <tr><td style="background-color:%s;border-radius:4px;">
                <a href="%s" style="display:inline-block;padding:12px 22px;color:#181611;font-family:Arial,Helvetica,sans-serif;font-size:14px;font-weight:bold;text-decoration:none;">Escríbenos por WhatsApp</a>
                </td></tr>
                </table>
                """.formatted(COLOR_DORADO, whatsappUrl);
    }

    // Encabezado con el logo real (vía cid:, embebido, no un enlace externo), cuerpo
    // específico de cada correo y pie con el sello de marca, datos de contacto reales y
    // año dinámico. Tabla anidada en vez de flexbox/grid: es el único enfoque que se
    // renderiza de forma consistente en Outlook de escritorio (motor de Word) además de
    // los clientes basados en WebKit/Blink.
    private String plantilla(String tituloInterno, String contenidoHtml) {
        int anioActual = Year.now().getValue();
        boolean hayTelefono = telefono != null && !telefono.isBlank();
        boolean hayCiudad = ciudad != null && !ciudad.isBlank();
        // Se escapa cada dato antes de unirlo: escaparHtml(contacto) completo habría
        // convertido la entidad &middot; en &amp;middot; (texto literal roto).
        String contacto = (hayTelefono ? escaparHtml(telefono) : "")
                + (hayTelefono && hayCiudad ? " &middot; " : "")
                + (hayCiudad ? escaparHtml(ciudad) : "");

        return """
                <!DOCTYPE html>
                <html lang="es">
                <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <meta name="color-scheme" content="light">
                <title>{{TITULO}}</title>
                </head>
                <body style="margin:0;padding:0;background-color:{{FONDO}};">
                <table role="presentation" width="100%" cellpadding="0" cellspacing="0" style="background-color:{{FONDO}};padding:32px 16px;">
                <tr>
                <td align="center">
                <table role="presentation" width="600" cellpadding="0" cellspacing="0" style="max-width:600px;width:100%;background-color:#FFFFFF;">
                <tr>
                <td align="center" style="padding:30px 24px 24px;border-bottom:3px solid {{DORADO}};">
                <img src="cid:{{CID_LOGO}}" width="150" alt="{{FIRMA}}" style="display:block;max-width:150px;height:auto;border:0;">
                </td>
                </tr>
                <tr>
                <td style="padding:34px 36px;color:{{TEXTO}};font-size:15px;line-height:1.65;font-family:Georgia,'Times New Roman',serif;">
                {{CONTENIDO}}
                </td>
                </tr>
                <tr>
                <td style="background-color:{{PIE_FONDO}};padding:28px 36px;">
                <table role="presentation" width="100%" cellpadding="0" cellspacing="0">
                <tr><td style="padding-bottom:12px;"><img src="cid:{{CID_SELLO}}" width="42" alt="" style="display:block;border:0;"></td></tr>
                <tr><td style="font-family:Arial,Helvetica,sans-serif;font-size:13px;color:{{DORADO}};font-weight:bold;letter-spacing:0.6px;padding-bottom:6px;">{{FIRMA_MAYUS}}</td></tr>
                <tr><td style="font-family:Arial,Helvetica,sans-serif;font-size:12px;color:{{PIE_SUAVE}};line-height:1.7;padding-bottom:14px;">
                {{CONTACTO}}<br>
                <a href="{{SITIO}}" style="color:{{DORADO}};text-decoration:none;">{{SITIO_SIN_PROTOCOLO}}</a>
                </td></tr>
                <tr><td style="font-family:Arial,Helvetica,sans-serif;font-size:11px;color:{{PIE_TENUE}};border-top:1px solid #33301F;padding-top:14px;">
                &copy; {{ANIO}} {{FIRMA}}. Todos los derechos reservados.
                </td></tr>
                </table>
                </td>
                </tr>
                </table>
                </td>
                </tr>
                </table>
                </body>
                </html>
                """
                .replace("{{TITULO}}", escaparHtml(tituloInterno))
                .replace("{{CONTENIDO}}", contenidoHtml)
                .replace("{{FONDO}}", COLOR_FONDO_PAGINA)
                .replace("{{DORADO}}", COLOR_DORADO)
                .replace("{{TEXTO}}", COLOR_TEXTO)
                .replace("{{PIE_FONDO}}", COLOR_PIE_FONDO)
                .replace("{{PIE_SUAVE}}", COLOR_PIE_TEXTO_SUAVE)
                .replace("{{PIE_TENUE}}", COLOR_PIE_TEXTO_TENUE)
                .replace("{{FIRMA}}", escaparHtml(nombreFirma))
                .replace("{{FIRMA_MAYUS}}", escaparHtml(nombreFirma.toUpperCase(Locale.of("es", "CO"))))
                .replace("{{CONTACTO}}", contacto)
                .replace("{{SITIO}}", sitioWeb)
                .replace("{{SITIO_SIN_PROTOCOLO}}", sitioWeb.replaceFirst("^https?://", ""))
                .replace("{{ANIO}}", String.valueOf(anioActual))
                .replace("{{CID_LOGO}}", CID_LOGO)
                .replace("{{CID_SELLO}}", CID_SELLO);
    }

    private void enviarHtml(String destinatario, String asunto, String cuerpoHtml) {
        enviarHtml(destinatario, asunto, cuerpoHtml, false);
    }

    // Bug real encontrado con datos reales: varias celdas de correo en las hojas de la firma
    // traen más de un destinatario separado por ";" (ej. "a@x.com; b@y.com") -- eso es texto
    // libre de una hoja de cálculo, no una dirección RFC 5322 válida. Pasárselo tal cual a
    // MimeMessageHelper.setTo(String) revienta con "Illegal semicolon, not in group" y el
    // correo nunca sale (confirmado: 41 de 89 fallos de un envío real fueron por esto). Se
    // separa por ";" o "," y cada trozo se manda como un destinatario propio de la lista "to"
    // -- todos reciben el mismo correo, como cabría esperar de una celda con varios contactos.
    private static final Pattern SEPARADOR_CORREOS = Pattern.compile("[;,]");

    private static String[] separarCorreos(String correoCrudo) {
        return Arrays.stream(SEPARADOR_CORREOS.split(correoCrudo))
                .map(String::strip)
                .filter(s -> !s.isBlank() && s.contains("@"))
                .toArray(String[]::new);
    }

    // esBoletin=true agrega la cabecera List-Unsubscribe: Gmail y otros proveedores
    // aplican reglas de "remitente masivo" a cualquier correo con apariencia de boletín
    // (HTML con plantilla repetida, mismo remitente, múltiples destinatarios similares) y
    // penalizan en la bandeja de spam a quien no la incluya. Solo aplica a los envíos que
    // de verdad son boletín/novedades (enviarBoletin, enviarNotificacionPublicacion,
    // enviarBienvenidaBoletin): un correo transaccional 1:1 (confirmación de solicitud,
    // de cita, código de caso) no la necesita y no se beneficia de ella.
    //
    // Devuelve si el correo se le entregó de verdad al servidor SMTP del destinatario (lo más
    // cerca de "llegó" que se puede confirmar sin un proveedor con webhooks de entrega tipo
    // SES/SendGrid): antes este método no devolvía nada y quien llamaba marcaba "enviado" con
    // solo haberlo intentado, sin saber si en realidad había fallado.
    private boolean enviarHtml(String destinatario, String asunto, String cuerpoHtml, boolean esBoletin) {
        String[] destinatarios = separarCorreos(destinatario);
        if (destinatarios.length == 0) {
            log.warn("No se pudo enviar el correo '{}': '{}' no tiene ninguna dirección válida.", asunto, destinatario);
            return false;
        }
        try {
            MimeMessage mensaje = mailSender.createMimeMessage();
            // MULTIPART_MODE_MIXED_RELATED, no MULTIPART_MODE_RELATED: el modo "related" a
            // secas solo admite UN cuerpo (HTML) más los recursos inline (el logo/sello por
            // cid:) -- no dos versiones alternativas del mismo mensaje. "mixed_related" es
            // el que hace falta para que setText(textoPlano, html) más abajo arme la
            // estructura MIME correcta (multipart/alternative con texto plano + HTML,
            // envuelto en el multipart/related que trae las imágenes inline).
            MimeMessageHelper helper = new MimeMessageHelper(mensaje, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
            // Nombre del remitente fijado explícitamente aquí, codificado en UTF-8 por
            // MimeMessageHelper (RFC 2047): así el correo siempre muestra "SIE Jurídicos"
            // bien acentuado sin importar qué nombre tenga guardado la cuenta de Gmail en
            // sí (se detectó que esa cuenta tenía el nombre guardado con una codificación
            // rota, mostrando "SIE JURÃ­DICOS" en la bandeja del destinatario).
            helper.setFrom(remitente, nombreFirma);
            helper.setTo(destinatarios);
            // Un remitente coherente entre "From" y "Reply-To" (misma cuenta ya usada
            // para las notificaciones al admin) es una señal más de legitimidad para los
            // filtros de spam, además de ser simplemente lo correcto: si alguien responde,
            // la respuesta debe llegar a una bandeja que sí se revisa.
            helper.setReplyTo(correoAdmin);
            helper.setSubject(asunto);
            String htmlCompleto = plantilla(asunto, cuerpoHtml);
            // Bug real de entregabilidad encontrado en esta revisión (reportado por el
            // usuario: los correos a Hotmail/Outlook no llegaban): SPF, DKIM y el alineamiento
            // From/Reply-To ya estaban correctos (MAIL_FROM = la misma cuenta de Gmail
            // autenticada), así que no era un fallo de configuración -- Gmail nunca rechazaba
            // el envío (cero errores en los logs del servidor). Un correo enviado SOLO en HTML,
            // sin una alternativa de texto plano, es justo el patrón que los filtros de
            // Microsoft penalizan como señal de spam/phishing, incluso cuando la autenticación
            // es perfecta -- da igual cuán legítimo sea el remitente si el mensaje en sí tiene
            // esa forma. Toda la plantilla ya pasa por htmlAPlano() para generar esa versión
            // automáticamente, sin duplicar el contenido de cada correo por separado.
            helper.setText(htmlAPlano(htmlCompleto), htmlCompleto);
            helper.addInline(CID_LOGO, logoResource, "image/png");
            helper.addInline(CID_SELLO, selloResource, "image/png");
            if (esBoletin) {
                // El par List-Unsubscribe + List-Unsubscribe-Post (no uno sin el otro) es lo
                // que Gmail/Yahoo exigen desde 2024 para remitentes masivos, y Microsoft
                // también lo trata como señal de legitimidad: sin el "-Post", algunos clientes
                // de correo ni siquiera muestran el botón de "darse de baja" de un clic.
                mensaje.addHeader("List-Unsubscribe", "<mailto:" + correoAdmin + "?subject=Baja%20del%20bolet%C3%ADn>");
                mensaje.addHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
            }
            mailSender.send(mensaje);
            return true;
        } catch (MessagingException | UnsupportedEncodingException | MailException ex) {
            log.warn("No se pudo enviar el correo '{}' a {}: {}", asunto, destinatario, ex.getMessage());
            return false;
        }
    }

    private String escaparHtml(String texto) {
        return texto == null ? "" : texto
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    // Genera la alternativa de texto plano exigida por enviarHtml() (ver el comentario ahí:
    // un correo solo-HTML es en sí mismo una señal de spam para Microsoft, sin importar la
    // autenticación). No es una conversión HTML->texto de propósito general, sino la mínima
    // necesaria para ESTA plantilla propia (nunca procesa HTML de terceros ni contenido de
    // usuarios sin escapar primero, ver escaparHtml): quita <head> completo, convierte los
    // enlaces a "texto (URL)" para no perder el destino, convierte saltos de bloque en saltos
    // de línea reales, quita cualquier etiqueta restante, decodifica las entidades que esta
    // misma plantilla usa, y colapsa líneas en blanco repetidas.
    private static final Pattern HTML_HEAD = Pattern.compile("(?is)<head\\b.*?</head>");
    private static final Pattern HTML_ENLACE = Pattern.compile("(?is)<a\\s+[^>]*href=\"([^\"]*)\"[^>]*>(.*?)</a>");
    private static final Pattern HTML_SALTO_BLOQUE = Pattern.compile(
            "(?i)<br\\s*/?>|</p>|</tr>|</table>|</div>|</h[1-6]>|</li>");
    private static final Pattern HTML_ETIQUETA = Pattern.compile("<[^>]+>");
    private static final Pattern LINEAS_EN_BLANCO_REPETIDAS = Pattern.compile("\\n{3,}");

    // Sin "private" (paquete), a propósito, solo para que EmailServiceHtmlAPlanoTest pueda
    // probarlo directamente en vez de tener que armar un MimeMessage completo para inspeccionar
    // la parte de texto plano.
    static String htmlAPlano(String html) {
        String texto = HTML_HEAD.matcher(html).replaceAll("");
        texto = HTML_ENLACE.matcher(texto).replaceAll(mr -> {
            String destino = mr.group(1).strip();
            String etiqueta = mr.group(2).replaceAll("(?i)<[^>]+>", "").strip();
            return etiqueta.equals(destino) ? destino : etiqueta + " (" + destino + ")";
        });
        texto = HTML_SALTO_BLOQUE.matcher(texto).replaceAll("\n");
        texto = HTML_ETIQUETA.matcher(texto).replaceAll("");
        texto = texto
                .replace("&nbsp;", " ")
                .replace("&middot;", "-")
                .replace("&copy;", "(c)")
                .replace("&rarr;", "->")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
        texto = Arrays.stream(texto.split("\n"))
                .map(String::strip)
                .collect(Collectors.joining("\n"));
        texto = LINEAS_EN_BLANCO_REPETIDAS.matcher(texto).replaceAll("\n\n");
        return texto.strip();
    }
}
