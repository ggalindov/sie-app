package sie.siejuridicos.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import sie.siejuridicos.common.exception.RecursoNoEncontradoException;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// Mismo mecanismo que ConsultaCasoAbuseGuard, aplicado a /api/pqrs/consulta: defensa en
// profundidad contra fuerza bruta de radicados, aparte del límite general de
// RateLimitFilter. Clase propia (no se reutiliza ConsultaCasoAbuseGuard) para no mezclar
// los contadores de dos recursos distintos bajo una sola clave de IP -- un atacante
// probando radicados de PQRS no debería "gastar" ni compartir el cupo de quien consulta
// casos legítimamente desde la misma IP (p. ej. una oficina compartida).
@Component
public class PqrsConsultaAbuseGuard {

    private static final Logger log = LoggerFactory.getLogger(PqrsConsultaAbuseGuard.class);

    private static final int MAX_FALLOS_SEGUIDOS = 15;
    private static final Duration VENTANA = Duration.ofMinutes(10);
    private static final Duration DURACION_BLOQUEO = Duration.ofMinutes(30);
    private static final String MENSAJE_GENERICO = "No encontramos ninguna PQRS con ese radicado";

    private static final class Estado {
        final AtomicInteger fallos = new AtomicInteger(0);
        volatile long inicioVentanaMs;
        volatile long bloqueadoHastaMs = 0;

        Estado(long ahora) {
            this.inicioVentanaMs = ahora;
        }
    }

    private final ConcurrentHashMap<String, Estado> estadosPorIp = new ConcurrentHashMap<>();

    public void verificarNoBloqueado(String ip) {
        Estado estado = estadosPorIp.get(ip);
        if (estado == null) {
            return;
        }
        if (estado.bloqueadoHastaMs - System.currentTimeMillis() > 0) {
            throw new RecursoNoEncontradoException(MENSAJE_GENERICO);
        }
    }

    public void registrarFallo(String ip) {
        long ahora = System.currentTimeMillis();
        Estado estado = estadosPorIp.computeIfAbsent(ip, k -> new Estado(ahora));

        synchronized (estado) {
            if (ahora - estado.inicioVentanaMs >= VENTANA.toMillis()) {
                estado.inicioVentanaMs = ahora;
                estado.fallos.set(0);
            }
            int total = estado.fallos.incrementAndGet();
            if (total >= MAX_FALLOS_SEGUIDOS) {
                estado.bloqueadoHastaMs = ahora + DURACION_BLOQUEO.toMillis();
                log.warn("IP bloqueada temporalmente en consulta de PQRS tras {} intentos sin "
                        + "encontrar ningún radicado: {}", total, ip);
            }
        }
    }

    public void registrarExito(String ip) {
        estadosPorIp.remove(ip);
    }

    @Scheduled(fixedRate = 15, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    void purgarEstadosInactivos() {
        long ahora = System.currentTimeMillis();
        long umbral = Math.max(VENTANA.toMillis(), DURACION_BLOQUEO.toMillis()) * 2;
        estadosPorIp.entrySet().removeIf(entry -> (ahora - entry.getValue().inicioVentanaMs) > umbral
                && entry.getValue().bloqueadoHastaMs < ahora);
    }
}
