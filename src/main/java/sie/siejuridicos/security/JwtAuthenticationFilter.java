package sie.siejuridicos.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIJO_BEARER = "Bearer ";

    private final JwtService jwtService;
    private final UsuarioInternoDetailsService usuarioInternoDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, UsuarioInternoDetailsService usuarioInternoDetailsService) {
        this.jwtService = jwtService;
        this.usuarioInternoDetailsService = usuarioInternoDetailsService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith(PREFIJO_BEARER)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(PREFIJO_BEARER.length());

        try {
            String correo = jwtService.extraerCorreo(token);
            if (correo != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = usuarioInternoDetailsService.loadUserByUsername(correo);
                if (jwtService.esValido(token, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (JwtException | IllegalArgumentException | AuthenticationException ex) {
            // token inválido/expirado, o el correo del token ya no corresponde a ningún usuario
            // (cuenta eliminada/renombrada después de emitido el token): se deja sin autenticar,
            // el entry point responde 401 en vez de propagar un error 500.
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
