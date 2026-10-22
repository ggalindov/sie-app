package sie.siejuridicos.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import sie.siejuridicos.common.exception.ErrorResponse;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.regex.Pattern;

// Pedido explícito del usuario: el ACTO de firmar (no la descarga posterior del
// certificado/documento final, ni consultar el resumen, ni rechazar) debe hacerse
// obligatoriamente desde un celular -- "pc queda bloqueado, solo celular". Mismo criterio
// que ya usa UCN (el motor original del que este módulo se adaptó, ver decisión de diseño
// 8 del plan): un bloqueo duro, en servidor (no solo una advertencia en el frontend, que
// cualquiera podría saltarse abriendo las herramientas de desarrollador), basado en el
// User-Agent real de la petición. El regex "Mobi" aparece en el User-Agent de Chrome/
// Safari de Android e iOS (incluido iPadOS en modo "Sitio de escritorio" desactivado), y
// NUNCA en un navegador de escritorio real -- es el mismo patrón que valida UCN en
// producción. No se bloquea GET /{token} (el frontend necesita poder cargar el resumen
// desde un PC para mostrar la pantalla de "abre esto desde tu celular" en vez de un error
// genérico), ni /rechazar (decidir no firmar no es un acto de firma), ni las descargas de
// PDF ya generados.
@Component
public class MobileOnlySignatureFilter extends OncePerRequestFilter {

    private static final Pattern USER_AGENT_MOVIL = Pattern.compile("Mobi", Pattern.CASE_INSENSITIVE);

    private static final Set<String> SUFIJOS_QUE_REQUIEREN_CELULAR = Set.of(
            "/verificar-identidad",
            "/otp/reenviar",
            "/otp/verificar",
            "/consentimiento",
            "/geolocalizacion",
            "/identidad",
            "/verificacion-humana",
            "/firmar"
    );

    private final ObjectMapper objectMapper;

    public MobileOnlySignatureFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (requierePaso(request) && !esDispositivoMovil(request)) {
            responderSoloMovil(response, request);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean requierePaso(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String ruta = request.getRequestURI();
        if (!ruta.startsWith("/api/v1/firmas/")) {
            return false;
        }
        for (String sufijo : SUFIJOS_QUE_REQUIEREN_CELULAR) {
            if (ruta.endsWith(sufijo)) {
                return true;
            }
        }
        return false;
    }

    private boolean esDispositivoMovil(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return userAgent != null && USER_AGENT_MOVIL.matcher(userAgent).find();
    }

    private void responderSoloMovil(HttpServletResponse response, HttpServletRequest request) throws IOException {
        ErrorResponse body = new ErrorResponse(
                LocalDateTime.now(),
                HttpStatus.UNPROCESSABLE_ENTITY.value(),
                HttpStatus.UNPROCESSABLE_ENTITY.getReasonPhrase(),
                "Este proceso de firma solo puede completarse desde un celular. Abre este enlace en tu teléfono.",
                request.getRequestURI()
        );
        response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
