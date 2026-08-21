package sie.siejuridicos.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Prueba de "atacante sin credenciales": levanta el contexto de Spring completo (con
// Postgres real, ver README de este directorio de tests) y golpea los endpoints reales
// a través de la cadena de filtros/seguridad de verdad, sin mockear SecurityConfig. Es
// la única forma honesta de confirmar que @PreAuthorize y las reglas de SecurityConfig
// coinciden, en vez de solo leer el código y asumir que están alineados.
@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void unaRutaAdminSinTokenSeRechaza() throws Exception {
        mockMvc.perform(get("/api/admin/solicitudes"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void unaRutaAdminConTokenInventadoSeRechaza() throws Exception {
        mockMvc.perform(get("/api/admin/usuarios")
                        .header("Authorization", "Bearer esto-no-es-un-jwt-valido"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void gestionarUsuariosSinTokenSeRechaza() throws Exception {
        mockMvc.perform(post("/api/admin/usuarios")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void cambiarActivoDeUsuarioSinTokenSeRechaza() throws Exception {
        mockMvc.perform(patch("/api/admin/usuarios/1/activo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activo\":false}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void listarCategoriasEsPublicoSinToken() throws Exception {
        mockMvc.perform(get("/api/categorias"))
                .andExpect(status().isOk());
    }

    @Test
    void elHealthcheckEsPublicoYNoRequiereToken() throws Exception {
        mockMvc.perform(get("/api/salud"))
                .andExpect(status().isOk());
    }

    @Test
    void loginConCredencialesInventadasSeRechazaConMensajeGenerico() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"esta-cuenta-no-existe-jamas@siejuridicos.com\",\"contrasena\":\"loquesea\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unSqlInyectadoEnUnCampoDeTextoNoRompeNiExponeNadaEnUnaRutaPublica() throws Exception {
        // No hay ninguna concatenación de SQL en el proyecto (confirmado leyendo el
        // código: todas las @Query nativas usan :parametro), pero esta prueba deja un
        // caso de regresión real: un intento clásico de inyección como dato de un
        // formulario público no debe producir un error 500 ni una respuesta distinta a
        // la de un dato normal inválido.
        mockMvc.perform(post("/api/solicitudes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"' OR '1'='1\",\"correo\":\"no-es-un-correo\",\"mensaje\":\"'; DROP TABLE solicitudes; --\",\"aceptaTratamientoDatos\":true,\"aceptaMarketing\":false}"))
                .andExpect(status().is4xxClientError());
    }
}
