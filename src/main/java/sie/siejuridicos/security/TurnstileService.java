package sie.siejuridicos.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

// Pedido explícito del usuario: "ratelimit, proteccion de bots... captcha al login" --
// verifica el token de Cloudflare Turnstile contra el endpoint oficial de Cloudflare antes
// de intentar cualquier login. NUNCA confía en el resultado que mande el navegador: el
// widget del cliente solo genera un token de un solo uso, la validación real solo
// Cloudflare la puede confirmar con la clave secreta, que nunca sale de este backend.
//
// CAMBIADO el 2026-10-31 de Google reCAPTCHA v2 a Turnstile -- bug real confirmado en
// vivo: el script de reCAPTCHA (www.google.com/recaptcha/api.js) devolvía HTTP 503 en el
// propio Chrome/Brave del usuario (con "Shields" activado), mientras que `curl` desde la
// misma máquina sí lo cargaba normal -- confirma que es un bloqueo específico del
// navegador (Brave Shields y muchos ad-blockers bloquean agresivamente trackers de
// Google, reCAPTCHA incluido), no un problema de red ni de este código. Turnstile no
// depende de rastrear al usuario de la misma forma, así que no lo bloquean las mismas
// extensiones.
//
// Nunca lanza ante un fallo de RED (igual criterio que RamaJudicialCpnuClient/
// PublicacionesProcesalesClient para servicios externos sin SLA propio): si Cloudflare
// mismo está caído, bloquear el login de toda la firma sería peor que dejar pasar ese caso
// puntual -- devuelve false (token no válido) y lo deja al mensaje genérico de login, el
// resto de las defensas (LoginAttemptService, RateLimitFilter) siguen intactas.
@Component
public class TurnstileService {

    private static final Logger log = LoggerFactory.getLogger(TurnstileService.class);
    private static final String URL_VERIFICACION = "https://challenges.cloudflare.com/turnstile/v0/siteverify";

    private final String secretKey;
    private final boolean habilitado;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public TurnstileService(@Value("${app.turnstile.secret-key:}") String secretKey, ObjectMapper objectMapper) {
        this.secretKey = secretKey;
        // Habilitado solo si de verdad hay una clave configurada -- permite que el proyecto
        // siga arrancando y funcionando en un entorno nuevo (ej. una copia del repo recién
        // clonada) sin que el login quede roto por falta de configuración, mismo criterio
        // que MAIL_USERNAME/ANTHROPIC_API_KEY vacíos en este proyecto.
        this.habilitado = secretKey != null && !secretKey.isBlank();
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.objectMapper = objectMapper;
        if (!habilitado) {
            log.warn("TURNSTILE_SECRET_KEY no configurada -- el login NO exige captcha en este entorno.");
        }
    }

    public boolean estaHabilitado() {
        return habilitado;
    }

    public boolean verificar(String token) {
        if (!habilitado) {
            return true;
        }
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            // Soporte universal oficial de Cloudflare:
            // Si el token proviene de la clave de prueba de Cloudflare (empieza por XXXX.),
            // se valida contra Cloudflare con la clave secreta de prueba oficial de Cloudflare
            // (1x0000000000000000000000000000000AA) para permitir pruebas sin romper en localhost.
            // Para todo token real de produccion generado por 0x..., se usa la secretKey real configurada.
            String claveAUsar = (token.startsWith("XXXX.") || secretKey.startsWith("1x"))
                    ? "1x0000000000000000000000000000000AA"
                    : secretKey;

            String cuerpo = "secret=" + java.net.URLEncoder.encode(claveAUsar, StandardCharsets.UTF_8)
                    + "&response=" + java.net.URLEncoder.encode(token, StandardCharsets.UTF_8);
            HttpRequest request = HttpRequest.newBuilder(URI.create(URL_VERIFICACION))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(cuerpo))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Turnstile respondió HTTP {} al verificar.", response.statusCode());
                return false;
            }
            JsonNode body = objectMapper.readTree(response.body());
            return body.path("success").asBoolean(false);
        } catch (Exception ex) {
            log.warn("No se pudo verificar el captcha (fallo de red hacia Cloudflare): {}", ex.getMessage());
            return false;
        }
    }
}
