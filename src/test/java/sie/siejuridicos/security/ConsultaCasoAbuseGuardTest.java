package sie.siejuridicos.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sie.siejuridicos.common.exception.RecursoNoEncontradoException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Escenario real: un atacante probando radicados al azar (o secuenciales) contra
// /api/casos/consulta desde una IP, quedándose por debajo del límite de RateLimitFilter
// (20/min) para no disparar ese límite, pero sostenido durante horas.
class ConsultaCasoAbuseGuardTest {

    private ConsultaCasoAbuseGuard guard;

    @BeforeEach
    void setUp() {
        guard = new ConsultaCasoAbuseGuard();
    }

    @Test
    void permiteFallosPorDebajoDelLimite() {
        String ip = "203.0.113.10";
        for (int i = 0; i < 14; i++) {
            guard.registrarFallo(ip);
            assertDoesNotThrow(() -> guard.verificarNoBloqueado(ip));
        }
    }

    @Test
    void bloqueaLaIpTrasQuinceFallosSeguidos() {
        String ip = "203.0.113.20";
        for (int i = 0; i < 15; i++) {
            guard.registrarFallo(ip);
        }
        assertThrows(RecursoNoEncontradoException.class, () -> guard.verificarNoBloqueado(ip));
    }

    @Test
    void elMensajeDeBloqueoEsIdenticoAlDeRadicadoNoEncontrado() {
        // Clave para no filtrar información: un atacante no debe poder distinguir "te
        // bloqueé por sospechoso" de "ese radicado simplemente no existe" comparando el
        // texto de la respuesta.
        String ip = "203.0.113.30";
        for (int i = 0; i < 15; i++) {
            guard.registrarFallo(ip);
        }
        RecursoNoEncontradoException ex = assertThrows(RecursoNoEncontradoException.class,
                () -> guard.verificarNoBloqueado(ip));
        assertEquals("No encontramos ningún caso con ese radicado", ex.getMessage());
    }

    @Test
    void unaConsultaExitosaLimpiaElContadorDeFallosPrevios() {
        String ip = "203.0.113.40";
        guard.registrarFallo(ip);
        guard.registrarFallo(ip);
        guard.registrarFallo(ip);
        guard.registrarExito(ip);

        // Tras el éxito, hacen falta 15 fallos NUEVOS para bloquear, no solo 12 más.
        for (int i = 0; i < 12; i++) {
            guard.registrarFallo(ip);
        }
        assertDoesNotThrow(() -> guard.verificarNoBloqueado(ip));
    }

    @Test
    void ipsDistintasNoSeAfectanEntreSi() {
        String atacante = "203.0.113.50";
        String clienteReal = "198.51.100.7";
        for (int i = 0; i < 15; i++) {
            guard.registrarFallo(atacante);
        }
        assertThrows(RecursoNoEncontradoException.class, () -> guard.verificarNoBloqueado(atacante));
        assertDoesNotThrow(() -> guard.verificarNoBloqueado(clienteReal));
    }
}
