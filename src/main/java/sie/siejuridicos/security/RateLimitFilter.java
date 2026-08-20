package sie.siejuridicos.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import sie.siejuridicos.common.exception.ErrorResponse;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// Limitador de tasa en memoria (sin Redis/Bucket4j: no están en el classpath y no se
// justifica agregar una dependencia nueva para el tamaño de este proyecto), por IP y por
// ruta, con ventana fija de 1 minuto. Protege los endpoints públicos sin autenticación de
// abuso/spam/fuerza bruta: login, chatbot, solicitudes, testimonios y suscripción al
// boletín. Se agrega al filter chain de Spring Security (ver SecurityConfig), antes del
// filtro de JWT, para que también limite intentos de login sin token válido.
//
// Limitación conocida: al vivir en memoria de un solo proceso, no comparte estado entre
// instancias si el backend llegara a escalar horizontalmente (no es el caso hoy). El mapa
// de contadores se purga periódicamente para no crecer sin límite con IPs que ya no vuelven
// a pedir.
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private record RegistroLimite(String metodo, String ruta, int maxSolicitudes) {
    }

    private static final Duration VENTANA = Duration.ofMinutes(1);

    // Límites sensatos por IP y por minuto para cada endpoint público sensible.
    private static final List<RegistroLimite> LIMITES = List.of(
            new RegistroLimite("POST", "/api/auth/login", 5),
            new RegistroLimite("POST", "/api/chatbot/mensaje", 15),
            new RegistroLimite("POST", "/api/solicitudes", 10),
            new RegistroLimite("POST", "/api/testimonios", 10),
            new RegistroLimite("POST", "/api/marketing/suscriptores", 10),
            // GET /api/casos/consulta es el único endpoint público que funciona como
            // "adivina el código": aunque el alfabeto de CasoService.generarCodigoUnico
            // (31 caracteres, 6 posiciones) da ~887M combinaciones, sin límite de tasa un
            // atacante podría probar miles por minuto igual. 20/min alcanza de sobra para
            // un cliente real que se equivoca escribiendo su código varias veces.
            new RegistroLimite("GET", "/api/casos/consulta", 20),
            // Se envía como mucho una vez por pestaña/sesión (ver VisitorTracker en el
            // frontend), así que 10/min por IP es de sobra para uso legítimo y sigue
            // acotando un abuso que intente inflar el contador de visitantes.
            new RegistroLimite("POST", "/api/analitica/visita", 10)
    );

    private static final class Contador {
        volatile long inicioVentanaMs;
        final AtomicInteger conteo = new AtomicInteger(0);

        Contador(long inicioVentanaMs) {
            this.inicioVentanaMs = inicioVentanaMs;
        }
    }

    private final ConcurrentHashMap<String, Contador> contadores = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public RateLimitFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        RegistroLimite limite = encontrarLimite(request);
        if (limite == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String clave = limite.ruta() + "|" + obtenerIp(request);
        long ahora = System.currentTimeMillis();
        Contador contador = contadores.computeIfAbsent(clave, k -> new Contador(ahora));

        boolean excedido;
        synchronized (contador) {
            if (ahora - contador.inicioVentanaMs >= VENTANA.toMillis()) {
                contador.inicioVentanaMs = ahora;
                contador.conteo.set(0);
            }
            excedido = contador.conteo.incrementAndGet() > limite.maxSolicitudes();
        }

        if (excedido) {
            log.warn("Rate limit excedido: {} {} desde {}", limite.metodo(), limite.ruta(), obtenerIp(request));
            responderLimiteExcedido(response, request);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private RegistroLimite encontrarLimite(HttpServletRequest request) {
        String metodo = request.getMethod();
        String ruta = request.getRequestURI();
        for (RegistroLimite limite : LIMITES) {
            if (limite.metodo().equalsIgnoreCase(metodo) && ruta.equals(limite.ruta())) {
                return limite;
            }
        }
        return null;
    }

    // CORREGIDO tras auditoría ofensiva: la versión anterior confiaba en X-Forwarded-For
    // sin validar que viniera de un proxy real, lo que permitía evadir el rate limit por
    // completo mandando un valor distinto en cada request (confirmado en vivo: 5 intentos
    // de login sin el header devolvían 429 al 5º, pero repitiendo con un
    // X-Forwarded-For distinto en cada llamada, cada una devolvía 401 en vez de 429).
    //
    // Este despliegue no tiene ningún proxy reverso/CDN delante (ver app.cors.allowed-
    // origins, apunta directo a localhost), así que la única IP en la que se puede confiar
    // es la de la conexión TCP real. Si en el futuro se agrega un proxy de confianza
    // (nginx, un load balancer), la forma correcta de manejarlo NO es volver a leer este
    // header a mano aquí, sino configurar `server.forward-headers-strategy=native` +
    // `server.tomcat.remoteip.trusted-proxies` en application.properties: eso hace que
    // Tomcat mismo valide que el header venga de una IP de proxy conocida antes de
    // confiar en él, y request.getRemoteAddr() ya refleja la IP real corregida.
    private String obtenerIp(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private void responderLimiteExcedido(HttpServletResponse response, HttpServletRequest request) throws IOException {
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                "Demasiadas solicitudes en poco tiempo. Por favor espera un momento e inténtalo de nuevo",
                request.getRequestURI()
        );
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    // Purga contadores inactivos (ventana ya vencida hace rato) cada 10 minutos, para que
    // el mapa no crezca sin límite con IPs que ya no volvieron a pedir.
    @Scheduled(fixedRate = 10, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    void purgarContadoresInactivos() {
        long ahora = System.currentTimeMillis();
        long umbral = VENTANA.toMillis() * 5;
        contadores.entrySet().removeIf(entry -> (ahora - entry.getValue().inicioVentanaMs) > umbral);
    }
}
