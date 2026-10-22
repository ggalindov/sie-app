package sie.siejuridicos.documento;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.common.exception.EntidadInvalidaException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;

// Código de un solo uso (OTP) que verifica que el firmante tiene control exclusivo del
// canal (correo/WhatsApp) asociado a su documento de identidad en el momento del acto --
// uno de los factores de autenticidad exigidos por el Decreto 2364/2012. Solo se guarda el
// hash del código (nunca el valor plano) en documento_otp.
@Service
public class OtpService {

    // 6 dígitos (1.000.000 de combinaciones): por debajo de eso (ej. 4 dígitos, 10.000
    // combinaciones) es débil para un OTP que gatea la firma de documentos con mérito
    // ejecutivo, por debajo de lo recomendado en NIST SP 800-63B Sec. 5.1.4.2.
    private static final int LONGITUD_CODIGO = 6;

    private final SecureRandom secureRandom = new SecureRandom();
    private final DocumentoOtpRepository documentoOtpRepository;
    private final int otpTtlMinutos;
    private final int otpIntentosMaximos;
    private final int otpReenvioCooldownSegundos;
    private final int otpMaximoCodigosDiarios;

    public OtpService(DocumentoOtpRepository documentoOtpRepository,
                       @Value("${app.documento.otp.ttl-minutos:10}") int otpTtlMinutos,
                       @Value("${app.documento.otp.intentos-maximos:5}") int otpIntentosMaximos,
                       @Value("${app.documento.otp.reenvio-cooldown-segundos:60}") int otpReenvioCooldownSegundos,
                       @Value("${app.documento.otp.maximo-codigos-diarios:10}") int otpMaximoCodigosDiarios) {
        this.documentoOtpRepository = documentoOtpRepository;
        this.otpTtlMinutos = otpTtlMinutos;
        this.otpIntentosMaximos = otpIntentosMaximos;
        this.otpReenvioCooldownSegundos = otpReenvioCooldownSegundos;
        this.otpMaximoCodigosDiarios = otpMaximoCodigosDiarios;
    }

    // Genera un código nuevo (invalidando en la práctica cualquiera anterior, ya que solo
    // se consulta el más reciente al verificar) y devuelve el valor EN CLARO para que el
    // llamador lo envíe por el canal de notificación.
    //
    // Exige un cooldown desde el último código generado para este firmante: sin él, pedir
    // un código nuevo reinicia el contador de intentos de DocumentoOtp#agotoIntentos(), así
    // que un atacante podría evadir el límite de otpIntentosMaximos pidiendo códigos nuevos
    // en ráfaga (cada uno con su propio cupo de intentos) para cubrir el espacio completo
    // de un código de 6 dígitos en minutos. El tope diario cierra el hueco restante: sin
    // él, pedir uno nuevo cada minuto daba ~7.200 intentos al día contra el mismo firmante.
    @Transactional
    public String solicitar(DocumentoFirmante firmante, String canal, String ipSolicitud) {
        documentoOtpRepository.findFirstByFirmanteIdOrderByCreadoEnDesc(firmante.getId())
                .ifPresent(anterior -> {
                    Duration transcurrido = Duration.between(anterior.getCreadoEn(), LocalDateTime.now());
                    Duration cooldown = Duration.ofSeconds(otpReenvioCooldownSegundos);
                    if (transcurrido.compareTo(cooldown) < 0) {
                        long restantes = cooldown.minus(transcurrido).toSeconds() + 1;
                        throw new EntidadInvalidaException(
                                "Espera " + restantes + " segundos antes de solicitar un nuevo código.");
                    }
                });
        long emitidosUltimoDia = documentoOtpRepository.countByFirmanteIdAndCreadoEnAfter(
                firmante.getId(), LocalDateTime.now().minusDays(1));
        if (emitidosUltimoDia >= otpMaximoCodigosDiarios) {
            throw new EntidadInvalidaException("Se alcanzó el número máximo de códigos de verificación por hoy. "
                    + "Intenta de nuevo mañana o pide a SIE Jurídicos que te reenvíe el enlace.");
        }
        String codigoPlano = generarCodigo();
        DocumentoOtp otp = new DocumentoOtp(firmante, canal, hash(codigoPlano),
                LocalDateTime.now().plusMinutes(otpTtlMinutos), ipSolicitud);
        otp.setIntentosMaximos(otpIntentosMaximos);
        documentoOtpRepository.save(otp);
        return codigoPlano;
    }

    @Transactional
    public void verificar(DocumentoFirmante firmante, String codigoIngresado) {
        DocumentoOtp otp = documentoOtpRepository.findFirstByFirmanteIdOrderByCreadoEnDesc(firmante.getId())
                .orElseThrow(() -> new OtpInvalidoException("Aún no se ha solicitado un código para este documento."));

        if (otp.agotoIntentos()) {
            throw new OtpInvalidoException("Se agotaron los intentos para este código. Solicita uno nuevo.");
        }
        if (otp.expirado()) {
            throw new OtpExpiradoException("El código ingresado venció. Solicita uno nuevo.");
        }
        if (!coincide(otp.getCodigoHash(), hash(codigoIngresado))) {
            otp.registrarIntentoFallido();
            throw new OtpInvalidoException("El código ingresado no es correcto.");
        }
        otp.marcarVerificado();
    }

    // Comparación en tiempo constante: evita que la latencia de la respuesta filtre, byte a
    // byte, cuánto del hash esperado acertó el código ingresado (timing attack).
    private boolean coincide(String hashEsperado, String hashIngresado) {
        return MessageDigest.isEqual(
                hashEsperado.getBytes(StandardCharsets.UTF_8),
                hashIngresado.getBytes(StandardCharsets.UTF_8));
    }

    private String hash(String codigoPlano) {
        return HashUtil.sha256Hex(codigoPlano.getBytes(StandardCharsets.UTF_8));
    }

    private String generarCodigo() {
        int max = (int) Math.pow(10, LONGITUD_CODIGO);
        int codigo = secureRandom.nextInt(max);
        return String.format("%0" + LONGITUD_CODIGO + "d", codigo);
    }
}
