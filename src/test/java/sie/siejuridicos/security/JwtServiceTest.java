package sie.siejuridicos.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import sie.siejuridicos.usuario.RolUsuario;
import sie.siejuridicos.usuario.UsuarioInterno;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Sin @SpringBootTest: JwtService no necesita el contexto completo, solo un
// Environment (MockEnvironment de spring-test, sin Mockito en el proyecto). Cubre el
// guardián de arranque agregado tras la auditoría de producción (no debe ser posible
// desplegar a producción firmando tokens con el secreto de ejemplo del repositorio) y
// el ciclo real de emisión/validación de un token.
class JwtServiceTest {

    private static final String SECRETO_PLACEHOLDER =
            "cambiar-este-secreto-en-produccion-por-uno-de-al-menos-256-bits";
    private static final String SECRETO_REAL_FUERTE =
            "un-secreto-generado-de-verdad-con-al-menos-256-bits-de-entropia-real-1234567890";

    private UsuarioInterno usuarioDePrueba() {
        UsuarioInterno u = new UsuarioInterno();
        u.setId(1L);
        u.setNombre("Abogada de Prueba");
        u.setCorreo("prueba@siejuridicos.com");
        u.setRol(RolUsuario.ABOGADO);
        return u;
    }

    @Test
    void enProduccionRechazaElSecretoDeEjemploDelRepositorio() {
        MockEnvironment prod = new MockEnvironment().withProperty("spring.profiles.active", "prod");
        prod.setActiveProfiles("prod");
        assertThrows(IllegalStateException.class,
                () -> new JwtService(SECRETO_PLACEHOLDER, 3_600_000L, prod));
    }

    @Test
    void enProduccionRechazaUnSecretoDemasiadoCorto() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        assertThrows(IllegalStateException.class,
                () -> new JwtService("demasiado-corto", 3_600_000L, prod));
    }

    @Test
    void enProduccionAceptaUnSecretoRealSuficientementeLargo() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        JwtService servicio = new JwtService(SECRETO_REAL_FUERTE, 3_600_000L, prod);
        assertEquals(3_600_000L, servicio.getExpirationMs());
    }

    @Test
    void fueraDeProduccionAceptaElSecretoDeEjemploParaNoRomperElDesarrolloLocal() {
        MockEnvironment dev = new MockEnvironment();
        // Sin perfil "prod" activo (equivalente a correr localmente con run.bat)
        JwtService servicio = new JwtService(SECRETO_PLACEHOLDER, 3_600_000L, dev);
        assertEquals(3_600_000L, servicio.getExpirationMs());
    }

    @Test
    void generaYValidaUnTokenReal() {
        MockEnvironment dev = new MockEnvironment();
        JwtService servicio = new JwtService(SECRETO_REAL_FUERTE, 3_600_000L, dev);
        UsuarioInterno usuario = usuarioDePrueba();

        String token = servicio.generarToken(usuario);
        UserDetails userDetails = new User(usuario.getCorreo(), "n/a", Collections.emptyList());

        assertTrue(servicio.esValido(token, userDetails));
        assertEquals(usuario.getCorreo(), servicio.extraerCorreo(token));
        assertEquals(RolUsuario.ABOGADO, servicio.extraerRol(token));
    }

    @Test
    void unTokenFirmadoConOtroSecretoNoEsValido() {
        MockEnvironment dev = new MockEnvironment();
        JwtService servicioOriginal = new JwtService(SECRETO_REAL_FUERTE, 3_600_000L, dev);
        JwtService servicioAtacante = new JwtService(
                "otro-secreto-totalmente-distinto-tambien-de-256-bits-o-mas-1234567890", 3_600_000L, dev);

        String tokenFalsificado = servicioAtacante.generarToken(usuarioDePrueba());
        UserDetails userDetails = new User("prueba@siejuridicos.com", "n/a", Collections.emptyList());

        // Un token firmado con una clave distinta debe rechazarse, no lanzar una
        // excepción sin capturar que tumbe el request.
        assertFalse(servicioOriginal.esValido(tokenFalsificado, userDetails));
    }

    @Test
    void unTokenParaOtroUsuarioNoEsValidoParaEsteUserDetails() {
        MockEnvironment dev = new MockEnvironment();
        JwtService servicio = new JwtService(SECRETO_REAL_FUERTE, 3_600_000L, dev);
        String token = servicio.generarToken(usuarioDePrueba());

        UserDetails otroUsuario = new User("otro@siejuridicos.com", "n/a", Collections.emptyList());
        assertFalse(servicio.esValido(token, otroUsuario));
    }
}
