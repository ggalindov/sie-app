package sie.siejuridicos.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import sie.siejuridicos.common.exception.CuentaBloqueadaException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Pruebas de fuerza bruta: el escenario real es un atacante intentando adivinar la
// contraseña de una cuenta, con o sin rotar de IP. No requiere contexto de Spring ni
// base de datos: LoginAttemptService es un componente en memoria puro.
class LoginAttemptServiceTest {

    private LoginAttemptService servicio;

    @BeforeEach
    void setUp() {
        servicio = new LoginAttemptService();
    }

    @Test
    void permiteIntentosPorDebajoDelLimite() {
        String correo = "abogado@siejuridicos.com";
        for (int i = 0; i < 4; i++) {
            servicio.registrarFallo(correo);
            assertDoesNotThrow(() -> servicio.verificarNoBloqueado(correo));
        }
    }

    @Test
    void bloqueaLaCuentaTrasCincoIntentosFallidos() {
        String correo = "victima@siejuridicos.com";
        for (int i = 0; i < 5; i++) {
            servicio.registrarFallo(correo);
        }
        assertThrows(CuentaBloqueadaException.class, () -> servicio.verificarNoBloqueado(correo));
    }

    @Test
    void bloqueaIgualUnCorreoQueNuncaExistio() {
        // Simula un atacante probando fuerza bruta sobre un correo inventado: debe
        // bloquearse exactamente igual que uno real, para no revelar por el propio
        // comportamiento del bloqueo qué correos existen de verdad en el sistema.
        String correoInventado = "no-existe-de-verdad@siejuridicos.com";
        for (int i = 0; i < 5; i++) {
            servicio.registrarFallo(correoInventado);
        }
        assertThrows(CuentaBloqueadaException.class, () -> servicio.verificarNoBloqueado(correoInventado));
    }

    @Test
    void unLoginExitosoLimpiaElContadorDeFallosPrevios() {
        String correo = "admin@siejuridicos.com";
        servicio.registrarFallo(correo);
        servicio.registrarFallo(correo);
        servicio.registrarFallo(correo);
        servicio.registrarExito(correo);

        // Tras el éxito, hacen falta 5 fallos NUEVOS para bloquear, no solo 2 más.
        servicio.registrarFallo(correo);
        servicio.registrarFallo(correo);
        assertDoesNotThrow(() -> servicio.verificarNoBloqueado(correo));
    }

    @Test
    void elBloqueoEsInsensibleAMayusculasYEspacios() {
        String correo = "Correo.Real@SIEJuridicos.com";
        for (int i = 0; i < 5; i++) {
            servicio.registrarFallo(correo);
        }
        // Un atacante podría intentar variar mayúsculas/espacios para evadir el
        // contador; debe seguir contando como la misma cuenta.
        assertThrows(CuentaBloqueadaException.class,
                () -> servicio.verificarNoBloqueado("  correo.real@siejuridicos.com  "));
    }

    @Test
    void cuentasDistintasNoSeAfectanEntreSi() {
        String atacada = "objetivo@siejuridicos.com";
        String otra = "inocente@siejuridicos.com";
        for (int i = 0; i < 5; i++) {
            servicio.registrarFallo(atacada);
        }
        assertThrows(CuentaBloqueadaException.class, () -> servicio.verificarNoBloqueado(atacada));
        assertDoesNotThrow(() -> servicio.verificarNoBloqueado(otra));
    }
}
