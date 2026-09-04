package sie.siejuridicos.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import sie.siejuridicos.common.exception.RecursoNoEncontradoException;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

// Defensa en profundidad contra fuerza bruta de radicados en /api/casos/consulta, aparte
// del límite de RateLimitFilter (20/min por IP): ese límite por sí solo no frena a un
// atacante paciente que se queda justo debajo de 20/min durante horas. Este componente
// cuenta, por IP, cuántas consultas seguidas NO encontraron ningún caso registrado, y
// bloquea temporalmente esa IP tras varias seguidas -- mismo patrón que LoginAttemptService
// (fuerza bruta de contraseñas), pero la clave aquí es la IP, no una cuenta: no hay un
// "objetivo" único como un correo, el atacante prueba muchos radicados distintos desde
// pocas IPs.
//
// verificarNoBloqueado() lanza la MISMA excepción y el MISMO mensaje que "radicado no
// encontrado" (ver CasoService.consultar): a propósito, para que un atacante no pueda
// distinguir "te bloqueé por sospechoso" de "ese radicado no existe" y así no sepa si su
// intento de evadir el bloqueo (esperar, rotar de IP) está funcionando.
@Component
public class ConsultaCasoAbuseGuard {

    private static final Logger log = LoggerFactory.getLogger(ConsultaCasoAbuseGuard.class);

    private static final int MAX_FALLOS_SEGUIDOS = 15;
    private static final Duration VENTANA = Duration.ofMinutes(10);
    private static final Duration DURACION_BLOQUEO = Duration.ofMinutes(30);
    private static final String MENSAJE_GENERICO = "No encontramos ningún caso con ese radicado";

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
                log.warn("IP bloqueada temporalmente en consulta de casos tras {} intentos sin "
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
