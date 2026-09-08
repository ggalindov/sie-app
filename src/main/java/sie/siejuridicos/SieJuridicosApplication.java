package sie.siejuridicos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

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
        SpringApplication.run(SieJuridicosApplication.class, args);
    }

}
