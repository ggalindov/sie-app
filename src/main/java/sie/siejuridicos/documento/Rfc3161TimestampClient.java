package sie.siejuridicos.documento;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.tsp.TSPAlgorithms;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampRequestGenerator;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;

// Sellado de tiempo RFC 3161 sobre el hash de un documento: prueba independiente del
// instante de sellado que no depende solo del reloj del propio servidor. Por defecto usa
// TSAs públicas y reconocidas (DigiCert/Sectigo), sin registro ni API key. NUNCA lanza:
// cualquier fallo (TSA inalcanzable, timeout, respuesta inválida) se loguea como advertencia
// y devuelve Optional.empty() -- el sellado de un documento no debe bloquearse jamás por la
// indisponibilidad de un servicio externo (ver DocumentoSellaInfoService, único llamador de sellar()).
@Component
public class Rfc3161TimestampClient {

    private static final Logger log = LoggerFactory.getLogger(Rfc3161TimestampClient.class);
    private static final String CONTENT_TYPE_QUERY = "application/timestamp-query";

    private final boolean habilitado;
    private final String urlPrimaria;
    private final String urlSecundaria;
    private final Duration timeoutLectura;
    private final SecureRandom secureRandom = new SecureRandom();
    private final HttpClient httpClient;

    public record ResultadoSello(byte[] token, String urlTsa, LocalDateTime selladoEn) {
    }

    public Rfc3161TimestampClient(@Value("${app.documento.rfc3161.habilitado}") boolean habilitado,
                                   @Value("${app.documento.rfc3161.url-primaria}") String urlPrimaria,
                                   @Value("${app.documento.rfc3161.url-secundaria}") String urlSecundaria,
                                   @Value("${app.documento.rfc3161.timeout-conexion-ms}") long timeoutConexionMs,
                                   @Value("${app.documento.rfc3161.timeout-lectura-ms}") long timeoutLecturaMs) {
        this.habilitado = habilitado;
        this.urlPrimaria = urlPrimaria;
        this.urlSecundaria = urlSecundaria;
        this.timeoutLectura = Duration.ofMillis(timeoutLecturaMs);
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutConexionMs)).build();
    }

    public Optional<ResultadoSello> sellar(byte[] hashSha256) {
        if (!habilitado) {
            return Optional.empty();
        }
        Optional<ResultadoSello> resultado = intentar(urlPrimaria, hashSha256);
        if (resultado.isPresent()) {
            return resultado;
        }
        return intentar(urlSecundaria, hashSha256);
    }

    private Optional<ResultadoSello> intentar(String url, byte[] hashSha256) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        try {
            TimeStampRequestGenerator generador = new TimeStampRequestGenerator();
            generador.setCertReq(true);
            BigInteger nonce = new BigInteger(64, secureRandom);
            TimeStampRequest solicitud = generador.generate(TSPAlgorithms.SHA256, hashSha256, nonce);

            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url))
                    .timeout(timeoutLectura)
                    .header("Content-Type", CONTENT_TYPE_QUERY)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(solicitud.getEncoded()))
                    .build();
            HttpResponse<byte[]> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofByteArray());
            if (httpResponse.statusCode() != 200) {
                log.warn("TSA {} respondió HTTP {} al sellar tiempo.", url, httpResponse.statusCode());
                return Optional.empty();
            }

            TimeStampResponse respuesta = new TimeStampResponse(httpResponse.body());
            respuesta.validate(solicitud);
            TimeStampToken token = respuesta.getTimeStampToken();
            if (token == null) {
                log.warn("TSA {} no otorgó un token de sellado (estado distinto de 'granted').", url);
                return Optional.empty();
            }
            LocalDateTime selladoEn = LocalDateTime.ofInstant(
                    token.getTimeStampInfo().getGenTime().toInstant(), ZoneId.systemDefault());
            return Optional.of(new ResultadoSello(token.getEncoded(), url, selladoEn));
        } catch (Exception ex) {
            log.warn("No se pudo obtener sello de tiempo RFC3161 de {}: {}", url, ex.getMessage());
            return Optional.empty();
        }
    }

    // true si el token es autoconsistente: el messageImprint coincide y su propia firma es válida.
    public boolean verificarToken(byte[] tokenBytes, byte[] hashEsperado) {
        try {
            TimeStampToken token = new TimeStampToken(new CMSSignedData(tokenBytes));
            byte[] imprint = token.getTimeStampInfo().getMessageImprintDigest();
            if (!Arrays.equals(imprint, hashEsperado)) {
                return false;
            }
            Collection<X509CertificateHolder> certificados = token.getCertificates().getMatches(token.getSID());
            Iterator<X509CertificateHolder> it = certificados.iterator();
            if (!it.hasNext()) {
                return false;
            }
            X509CertificateHolder certificadoTsa = it.next();
            token.validate(new JcaSimpleSignerInfoVerifierBuilder().build(certificadoTsa));
            return true;
        } catch (Exception ex) {
            log.warn("No se pudo verificar el token de sellado de tiempo: {}", ex.getMessage());
            return false;
        }
    }
}
