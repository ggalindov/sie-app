package sie.siejuridicos.security;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Simula intentos masivos / fuerza bruta a nivel de filtro HTTP: sin contexto de
// Spring, usando los mocks de servlet de spring-test (MockHttpServletRequest/Response/
// FilterChain), no hace falta levantar la aplicación completa ni una base de datos.
class RateLimitFilterTest {

    private RateLimitFilter nuevoFiltro() {
        return new RateLimitFilter(new ObjectMapper());
    }

    private MockHttpServletRequest peticionLogin(String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(ip);
        return request;
    }

    @Test
    void permiteHastaElLimiteConfiguradoParaLogin() throws ServletException, IOException {
        RateLimitFilter filtro = nuevoFiltro();
        String ip = "203.0.113.10";

        // El límite real para POST /api/auth/login es 5 por minuto (ver RateLimitFilter.LIMITES)
        for (int i = 1; i <= 5; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filtro.doFilter(peticionLogin(ip), response, new MockFilterChain());
            assertEquals(200, response.getStatus(), "la solicitud #" + i + " debería pasar (MockFilterChain no cambia el 200 por defecto)");
        }
    }

    @Test
    void bloqueaConDemasiadasSolicitudesTrasSuperarElLimite() throws ServletException, IOException {
        RateLimitFilter filtro = nuevoFiltro();
        String ip = "203.0.113.20";

        for (int i = 1; i <= 5; i++) {
            filtro.doFilter(peticionLogin(ip), new MockHttpServletResponse(), new MockFilterChain());
        }

        // El intento número 6, desde la misma IP y en la misma ventana, debe rechazarse.
        MockHttpServletResponse sexta = new MockHttpServletResponse();
        filtro.doFilter(peticionLogin(ip), sexta, new MockFilterChain());
        assertEquals(429, sexta.getStatus());
        assertTrue(sexta.getContentAsString().contains("Demasiadas solicitudes"));
    }

    @Test
    void distintasIpsTienenSuPropioContadorIndependiente() throws ServletException, IOException {
        RateLimitFilter filtro = nuevoFiltro();

        for (int i = 1; i <= 5; i++) {
            filtro.doFilter(peticionLogin("203.0.113.30"), new MockHttpServletResponse(), new MockFilterChain());
        }
        // Una IP distinta, aunque golpee la misma ruta, no hereda el conteo de la otra.
        MockHttpServletResponse respuestaOtraIp = new MockHttpServletResponse();
        filtro.doFilter(peticionLogin("203.0.113.31"), respuestaOtraIp, new MockFilterChain());
        assertEquals(200, respuestaOtraIp.getStatus());
    }

    @Test
    void rutasSinLimiteConfiguradoNuncaSeBloquean() throws ServletException, IOException {
        RateLimitFilter filtro = nuevoFiltro();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/categorias");
        request.setRemoteAddr("203.0.113.40");

        for (int i = 0; i < 50; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filtro.doFilter(request, response, new MockFilterChain());
            assertEquals(200, response.getStatus());
        }
    }
}
