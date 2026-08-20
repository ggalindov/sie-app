package sie.siejuridicos.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import sie.siejuridicos.common.exception.CuentaBloqueadaException;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// Bloqueo de cuenta por intentos fallidos, independiente del rate limit por IP
// (RateLimitFilter): ese limita 5 intentos/minuto por IP, pero un atacante con varias IPs
// (proxies, botnet) puede seguir probando contraseñas indefinidamente contra UNA cuenta
// específica sin que el límite por IP lo note. Este servicio cuenta los fallos por correo,
// sin importar desde qué IP vengan, y bloquea temporalmente ese correo tras varios fallos.
//
// Se cuenta el fallo tanto si el correo existe como si no (AuthService llama
// registrarFallo() en ambos casos): si solo se bloquearan correos reales, un atacante podría
// distinguir "correo real" de "correo inventado" con solo fijarse en cuál de los dos
// termina devolviendo "cuenta bloqueada" después de varios intentos — bloquear ambos por
// igual cierra ese canal lateral además de frenar la fuerza bruta.
//
// En memoria de un solo proceso, mismo criterio ya documentado en RateLimitFilter (no se
// justifica Redis para el tamaño de este proyecto; si se escala horizontalmente habría que
// mover esto a la base de datos o a un almacén compartido).
@Component
public class LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    private static final int MAX_INTENTOS = 5;
    private static final Duration VENTANA = Duration.ofMinutes(15);
    private static final Duration DURACION_BLOQUEO = Duration.ofMinutes(15);

    private static final class Estado {
        final AtomicInteger intentos = new AtomicInteger(0);
        volatile long inicioVentanaMs;
        volatile long bloqueadoHastaMs = 0;

        Estado(long ahora) {
            this.inicioVentanaMs = ahora;
        }
    }

    private final ConcurrentHashMap<String, Estado> estadosPorCorreo = new ConcurrentHashMap<>();

    /** Lanza CuentaBloqueadaException si el correo sigue en periodo de bloqueo. Llamar
        ANTES de tocar la base de datos o calcular el hash de la contraseña, para no gastar
        ese trabajo en un intento que ya se sabe que va a rechazarse. */
    public void verificarNoBloqueado(String correo) {
        Estado estado = estadosPorCorreo.get(normalizar(correo));
        if (estado == null) {
            return;
        }
        long restanteMs = estado.bloqueadoHastaMs - System.currentTimeMillis();
        if (restanteMs > 0) {
            long minutos = Math.max(1, Duration.ofMillis(restanteMs).toMinutes());
            throw new CuentaBloqueadaException(
                    "Demasiados intentos fallidos. Intenta de nuevo en " + minutos + " minuto(s)");
        }
    }

    public void registrarFallo(String correo) {
        String clave = normalizar(correo);
        long ahora = System.currentTimeMillis();
        Estado estado = estadosPorCorreo.computeIfAbsent(clave, k -> new Estado(ahora));

        synchronized (estado) {
            if (ahora - estado.inicioVentanaMs >= VENTANA.toMillis()) {
                estado.inicioVentanaMs = ahora;
                estado.intentos.set(0);
            }
            int totalIntentos = estado.intentos.incrementAndGet();
            if (totalIntentos >= MAX_INTENTOS) {
                estado.bloqueadoHastaMs = ahora + DURACION_BLOQUEO.toMillis();
                log.warn("Cuenta bloqueada temporalmente tras {} intentos fallidos: {}", totalIntentos, clave);
            }
        }
    }

    public void registrarExito(String correo) {
        estadosPorCorreo.remove(normalizar(correo));
    }

    private String normalizar(String correo) {
        return correo == null ? "" : correo.trim().toLowerCase(Locale.ROOT);
    }

    // Mismo criterio de purga que RateLimitFilter: sin esto el mapa crece sin límite con
    // correos que ya no vuelven a intentarlo.
    @Scheduled(fixedRate = 30, timeUnit = TimeUnit.MINUTES)
    void purgarEstadosInactivos() {
        long ahora = System.currentTimeMillis();
        long umbral = VENTANA.toMillis() + DURACION_BLOQUEO.toMillis();
        estadosPorCorreo.entrySet().removeIf(entry -> {
            Estado estado = entry.getValue();
            long ultimaActividad = Math.max(estado.inicioVentanaMs, estado.bloqueadoHastaMs);
            return (ahora - ultimaActividad) > umbral;
        });
    }
}
