package sie.siejuridicos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.security.Security;
import java.util.TimeZone;

@SpringBootApplication
@EnableAsync
@EnableScheduling
public class SieJuridicosApplication {

    public static void main(String[] args) {
        // Fija la zona horaria por defecto de TODA la JVM a la hora real de la firma, antes
        // de que Spring arranque -- bug real encontrado en auditoría, no hipotético: sin
        // esto, @Future/@Past de Jakarta Validation (AgendarCitaRequest.fechaHora) y los cron
        // de @Scheduled (RecordatorioCitaScheduler, RecordatorioCobroScheduler,
        // SincronizacionCasosScheduler) evalúan contra Clock.systemDefaultZone()/
        // ZoneId.systemDefault(), que en el contenedor Docker (imagen JRE Alpine, sin
        // /etc/localtime configurado) es UTC, no America/Bogota. Con Bogotá a UTC-5, eso
        // significa que agendar una reunión dentro de las próximas ~5 horas se rechazaba como
        // "fecha pasada", y los recordatorios de las 7:00 a.m./8:00 a.m. de verdad disparaban
        // a las 2:00/3:00 a.m. hora de Bogotá. En desarrollo local (Windows, zona horaria del
        // sistema operativo ya en Bogotá) nunca se reprodujo, por eso pasó desapercibido hasta
        // ahora. TimeZone.setDefault() aquí es más confiable que la variable de entorno TZ o
        // -Duser.timezone (que dependen de cómo se lance el proceso): funciona igual sin
        // importar si corre con `mvnw spring-boot:run`, dentro de Docker, o en cualquier otro
        // entorno, sin depender de configuración externa.
        TimeZone.setDefault(TimeZone.getTimeZone("America/Bogota"));

        // Bug real encontrado en vivo (pedido del usuario: "verifica porque aun el sistema
        // no esta trayendo todos los boletines"). Se investigaron dos hipótesis, AMBAS
        // descartadas con pruebas en vivo, no solo supuestas -- documentado aquí para no
        // reinvestigarlas de nuevo:
        // 1) Caché de DNS quemada contra un nodo malo del CDN: descartada, un proceso recién
        //    arrancado fallaba igual que uno con horas de uptime.
        // 2) IPv6 fantasma (el host resuelve a IPv4 Y IPv6, confirmado con nslookup, y la JVM
        //    no hace "happy eyeballs" como curl): parecía prometedora al principio (`curl -6`
        //    fallaba en 0.1s, `curl -4` respondía bien), pero quedó DESCARTADA el 2026-10-31
        //    con una prueba más completa: forzando java.net.preferIPv4Stack=true a nivel de
        //    JVM (JAVA_TOOL_OPTIONS, ver run.bat) el fallo siguió igual, Y un `curl -4` puro
        //    repetido contra la acción real de búsqueda del portal también empezó a colgarse
        //    (20s de timeout, 3/3 intentos), incluso simulando un navegador real completo
        //    (cookies de sesión + User-Agent de Chrome). La página raíz del mismo portal, sin
        //    parámetros de búsqueda, seguía respondiendo bien en ~1s.
        // CAUSA RAÍZ REAL: no es de red/IP, es del lado del portal -- un bloqueo/degradación
        // por tráfico automatizado contra esa acción puntual (mismo patrón ya documentado en
        // PublicacionesProcesalesClient.pareceCaido() y en BoletinJudicialCacheService, y que
        // ya le pasó a la Rama Judicial CPNU esta misma sesión con HTTP 403 explícito -- aquí
        // se manifiesta como un colgado silencioso en vez de un 403). La mitigación real está
        // en BoletinJudicialScheduler (dos corridas al día, no continuo) y en el espaciado
        // entre consultas, no en esta línea. Se deja preferIPv4Stack=true de todas formas:
        // es inofensiva y evita cualquier ruta IPv6 fantasma real que sí exista en otro
        // entorno, aunque no sea la causa de este bloqueo en particular.
        System.setProperty("java.net.preferIPv4Stack", "true");
        Security.setProperty("networkaddress.cache.ttl", "30");
        SpringApplication.run(SieJuridicosApplication.class, args);
    }

}
