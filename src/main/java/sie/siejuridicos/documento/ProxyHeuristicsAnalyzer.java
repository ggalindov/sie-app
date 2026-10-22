package sie.siejuridicos.documento;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

// Señal heurística y HONESTA (no una solución antifraude robusta ni un veredicto) sobre
// indicios de proxy/intermediarios en la conexión de firma: una cadena larga de
// X-Forwarded-For o la presencia de cabeceras Via/X-Forwarded-Host sugieren más de un
// salto entre el firmante y este servidor. Queda como contexto adicional en el certificado
// de evidencia, nunca como un bloqueo automático de la firma.
@Component
public class ProxyHeuristicsAnalyzer {

    public String analizar(HttpServletRequest request) {
        StringBuilder senales = new StringBuilder();
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null) {
            int saltos = forwardedFor.split(",").length;
            if (saltos > 1) {
                senales.append("X-Forwarded-For con ").append(saltos).append(" saltos; ");
            }
        }
        String via = request.getHeader("Via");
        if (via != null && !via.isBlank()) {
            senales.append("Cabecera Via presente; ");
        }
        String forwardedHost = request.getHeader("X-Forwarded-Host");
        if (forwardedHost != null && !forwardedHost.isBlank()) {
            senales.append("X-Forwarded-Host presente; ");
        }
        return senales.isEmpty() ? null : senales.toString().trim();
    }
}
