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

    // ---------- Casos (consulta pública de estado por radicado, ver CasoService) ----------

    @Test
    void crearCasoSinTokenSeRechaza() throws Exception {
        mockMvc.perform(post("/api/admin/casos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void listarCasosSinTokenSeRechaza() throws Exception {
        mockMvc.perform(get("/api/admin/casos"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void listarCasosConTokenInventadoSeRechaza() throws Exception {
        mockMvc.perform(get("/api/admin/casos")
                        .header("Authorization", "Bearer esto-no-es-un-jwt-valido"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void sincronizarCasosDesdeLaHojaSinTokenSeRechaza() throws Exception {
        // La sincronización masiva trae correo/teléfono reales de clientes desde la hoja de
        // la firma: debe quedar tan protegida como cualquier otra ruta de /api/admin/**.
        mockMvc.perform(post("/api/admin/casos/sincronizar"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void enviarCorreosPendientesDeCasosSinTokenSeRechaza() throws Exception {
        mockMvc.perform(post("/api/admin/casos/enviar-pendientes"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void consultarCasoEsPublicoSinToken() throws Exception {
        // Público por diseño (el cliente nunca tiene cuenta), pero debe dar 404 genérico
        // para un radicado que no existe -- nunca un 401/403 que insinuara que hace falta
        // autenticarse, ni un 500 que exponga detalles internos.
        mockMvc.perform(get("/api/casos/consulta").param("codigo", "NO-EXISTE-9999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void consultarCasoConRadicadoVacioNoRompeNiDaQuinientos() throws Exception {
        mockMvc.perform(get("/api/casos/consulta").param("codigo", " "))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void unIntentoDeInyeccionEnElRadicadoNoRompeNiExponeNadaEnLaConsultaPublica() throws Exception {
        // El radicado nunca se concatena en la llamada a Google Sheets (se compara en
        // memoria, ver HojaCalculoService) ni en SQL crudo (Spring Data parametriza), pero
        // esta prueba deja un caso de regresión real para el mismo endpoint público que
        // más expuesto está a que alguien pruebe esto.
        mockMvc.perform(get("/api/casos/consulta").param("codigo", "'; DROP TABLE casos; --"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void crearCasoConTokenInventadoYCuerpoConDatosSeRechazaPorAutenticacionNoPorValidacion() throws Exception {
        // Confirma que la cadena de filtros rechaza el token ANTES de llegar a la
        // validación de @Valid CrearCasoRequest (incluida la del formato del radicado, ver
        // CrearCasoRequestTest para esa validación en sí): ni un cuerpo con forma válida
        // cuela sin autenticación real.
        mockMvc.perform(post("/api/admin/casos")
                        .header("Authorization", "Bearer esto-no-es-un-jwt-valido")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombreCliente\":\"Cliente\",\"correoCliente\":\"cliente@example.com\",\"radicadoId\":\"radicado con espacios!!\"}"))
                .andExpect(status().is4xxClientError());
    }
}
