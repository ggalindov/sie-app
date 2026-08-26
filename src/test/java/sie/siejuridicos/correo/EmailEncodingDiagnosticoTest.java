package sie.siejuridicos.correo;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Regresión del incidente real: los correos salían con "SIE JurÃ­dicos" (causa raíz en
// EmailService/application.properties, ya corregida). Esta prueba no confía en que
// nombreFirma tenga el valor correcto en memoria (eso ya lo cubre otra prueba) sino que
// reproduce el camino completo de JavaMail: arma el mensaje exactamente como
// EmailService.enviarHtml() (MimeMessageHelper, MULTIPART_MODE_RELATED, encoding UTF-8,
// setFrom con nombre), lo serializa a los bytes reales que viajarían por SMTP, y vuelve a
// parsear esos bytes crudos con el propio parser de Jakarta Mail -- igual que haría el
// cliente de correo del destinatario -- para confirmar que la tilde sobrevive intacta.
class EmailEncodingDiagnosticoTest {

    @Test
    void elNombreYElAsuntoConTildeSobrevivenLaCodificacionMime() throws Exception {
        MimeMessage mensaje = new MimeMessage(Session.getDefaultInstance(new Properties()));
        MimeMessageHelper helper = new MimeMessageHelper(mensaje, MimeMessageHelper.MULTIPART_MODE_RELATED, "UTF-8");

        String nombreFirma = "SIE Jurídicos";
        helper.setFrom("siejuridicos@gmail.com", nombreFirma);
        helper.setTo("destinatario@ejemplo.com");
        helper.setSubject("Hemos recibido tu solicitud - " + nombreFirma);
        helper.setText("<p>Cuerpo de prueba con SIE Jurídicos incluido.</p>", true);

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        mensaje.writeTo(salida);

        MimeMessage mensajeReconstruido = new MimeMessage(Session.getDefaultInstance(new Properties()),
                new ByteArrayInputStream(salida.toByteArray()));

        InternetAddress remitente = (InternetAddress) mensajeReconstruido.getFrom()[0];

        assertEquals("SIE Jurídicos", remitente.getPersonal());
        assertEquals("Hemos recibido tu solicitud - SIE Jurídicos", mensajeReconstruido.getSubject());
    }
}
