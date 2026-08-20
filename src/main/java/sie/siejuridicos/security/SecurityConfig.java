package sie.siejuridicos.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

// No se define un AuthenticationManager/DaoAuthenticationProvider porque AuthService
// valida las credenciales directamente con PasswordEncoder (ver AuthService.login),
// en vez de delegar en la maquinaria estándar de Spring Security.
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final RequestSizeLimitFilter requestSizeLimitFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final List<String> corsAllowedOrigins;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                           RateLimitFilter rateLimitFilter,
                           RequestSizeLimitFilter requestSizeLimitFilter,
                           JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint,
                           JwtAccessDeniedHandler jwtAccessDeniedHandler,
                           @Value("${app.cors.allowed-origins}") List<String> corsAllowedOrigins) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.requestSizeLimitFilter = requestSizeLimitFilter;
        this.jwtAuthenticationEntryPoint = jwtAuthenticationEntryPoint;
        this.jwtAccessDeniedHandler = jwtAccessDeniedHandler;
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/salud").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/solicitudes").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/testimonios").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/testimonios").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/articulos/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categorias/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/chatbot/mensaje").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/marketing/suscriptores").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/faq").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/casos/consulta").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/analitica/visita").permitAll()
                        .requestMatchers("/api/admin/usuarios/**").hasRole("ADMIN_GENERAL")
                        // Testimonios, marketing y boletines quedan fuera del alcance del rol
                        // ABOGADO (pedido explícito: "que se queden más como abogados y ya", el
                        // lado de moderación de contenido/marketing es del administrador). FAQ y
                        // Casos sí están al alcance de ambos roles (pedido explícito para FAQ:
                        // "el abogado la aprueba con un clic"; para Casos: "esto lo crea el
                        // abogado o administrador"), por eso caen en el catch-all de abajo.
                        .requestMatchers("/api/admin/testimonios/**").hasRole("ADMIN_GENERAL")
                        .requestMatchers("/api/admin/marketing/**").hasRole("ADMIN_GENERAL")
                        .requestMatchers("/api/admin/boletines/**").hasRole("ADMIN_GENERAL")
                        .requestMatchers("/api/admin/**").hasAnyRole("ADMIN_GENERAL", "ABOGADO")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler)
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // Tamaño de payload y rate limiting corren antes que el filtro de JWT: deben
                // aplicarse incluso a solicitudes sin token (login, formularios públicos).
                .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class)
                .addFilterBefore(requestSizeLimitFilter, RateLimitFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Authorization: Bearer <jwt> no depende de cookies, así que allowCredentials queda en
    // false a propósito; el frontend (Next.js) nunca necesita enviar credenciales de navegador.
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(corsAllowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(false);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
