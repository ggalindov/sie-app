package sie.siejuridicos.documento;

import jakarta.servlet.http.HttpServletRequest;

// Misma decisión ya tomada y auditada en security.RateLimitFilter: NO se confía en
// X-Forwarded-For (permitiría a un firmante fabricar la IP que queda como evidencia,
// mandando un valor distinto en cada request -- el mismo hueco que ya se encontró y
// corrigió para el rate limit de login). request.getRemoteAddr() es la única IP en la que
// se puede confiar mientras este despliegue no tenga un proxy de confianza configurado vía
// server.forward-headers-strategy + server.tomcat.remoteip.trusted-proxies.
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String resolver(HttpServletRequest request) {
        return request.getRemoteAddr();
    }
}
