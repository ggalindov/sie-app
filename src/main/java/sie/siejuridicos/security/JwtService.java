package sie.siejuridicos.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import sie.siejuridicos.usuario.RolUsuario;
import sie.siejuridicos.usuario.UsuarioInterno;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

@Service
public class JwtService {

    // El mismo valor de respaldo que trae application.properties si JWT_SECRET no está
    // en el entorno: sirve para desarrollo local (run.bat sí exporta uno real), pero
    // firmar tokens de producción con un secreto que está publicado tal cual en el
    // propio repositorio anularía por completo la firma del JWT. Keys.hmacShaKeyFor ya
    // rechaza un secreto demasiado corto para HS256 (menos de 256 bits) lanzando
    // WeakKeyException, pero no tiene forma de saber que ESTE valor específico, aunque
    // mide lo suficiente, es un placeholder conocido y no un secreto real.
    private static final String SECRETO_PLACEHOLDER = "cambiar-este-secreto-en-produccion-por-uno-de-al-menos-256-bits";
    private static final int LONGITUD_MINIMA_BYTES = 32; // 256 bits, mínimo real para HS256

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                       @Value("${app.jwt.expiration-ms}") long expirationMs,
                       Environment environment) {
        boolean esProduccion = List.of(environment.getActiveProfiles()).contains("prod");
        if (esProduccion) {
            if (SECRETO_PLACEHOLDER.equals(secret)) {
                throw new IllegalStateException(
                        "JWT_SECRET sigue siendo el valor de ejemplo del repositorio. "
                                + "Genera uno real (openssl rand -base64 48) y configúralo en .env.prod antes de desplegar.");
            }
            if (secret.getBytes(StandardCharsets.UTF_8).length < LONGITUD_MINIMA_BYTES) {
                throw new IllegalStateException(
                        "JWT_SECRET mide menos de 256 bits. Genera uno real (openssl rand -base64 48) "
                                + "y configúralo en .env.prod antes de desplegar.");
            }
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public String generarToken(UsuarioInterno usuario) {
        Date ahora = new Date();
        Date expiracion = new Date(ahora.getTime() + expirationMs);
        return Jwts.builder()
                .subject(usuario.getCorreo())
                .claim("id", usuario.getId())
                .claim("nombre", usuario.getNombre())
                .claim("rol", usuario.getRol().name())
                .issuedAt(ahora)
                .expiration(expiracion)
                .signWith(signingKey)
                .compact();
    }

    public String extraerCorreo(String token) {
        return extraerClaims(token).getSubject();
    }

    public RolUsuario extraerRol(String token) {
        return RolUsuario.valueOf(extraerClaims(token).get("rol", String.class));
    }

    public boolean esValido(String token, UserDetails userDetails) {
        try {
            Claims claims = extraerClaims(token);
            return claims.getSubject().equals(userDetails.getUsername())
                    && claims.getExpiration().after(new Date());
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    private Claims extraerClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
