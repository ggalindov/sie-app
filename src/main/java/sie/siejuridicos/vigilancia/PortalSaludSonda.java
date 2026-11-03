package sie.siejuridicos.vigilancia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

// Sonda permanente de salud (ver V77): cada 10 minutos hace UNA peticion liviana a cada portal
// del Estado que usa el sistema y a Google como control de internet, y guarda si respondio y en
// cuanto tiempo. Sirve para (1) elegir con datos la hora del ciclo automatico y (2) separar, en
// cada caida, "el portal" de "mi conexion": si Google responde y el portal no, es el portal.
// Nunca lanza y no comparte nada con el mutex del ciclo: es trafico minimo (144 peticiones
// por dia y servicio). orTimeout() en vez de depender del timeout nativo de HttpClient, que no
// siempre se cumple con conexiones medio abiertas (hallazgo real del 2026-11-01).
@Component
public class PortalSaludSonda {

    private static final Logger log = LoggerFactory.getLogger(PortalSaludSonda.class);
    private static final long TIMEOUT_MS = 25_000L;
    private static final String RADICADO_SONDA = "11001408806420250005400";

    private final PortalSaludRepository repository;
    private final PublicacionesProcesalesClient publicacionesClient;
    private final String urlCpnu;
    private final String urlSamai;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public PortalSaludSonda(PortalSaludRepository repository,
                            PublicacionesProcesalesClient publicacionesClient,
                            @Value("${app.vigilancia.cpnu.url-base}") String urlCpnu,
                            @Value("${app.vigilancia.samai.url-base}") String urlSamai) {
        this.repository = repository;
        this.publicacionesClient = publicacionesClient;
        this.urlCpnu = urlCpnu;
        this.urlSamai = urlSamai;
    }

    @Scheduled(initialDelay = 30_000, fixedDelay = 600_000)
    public void sondear() {
        Map<String, String> objetivos = new LinkedHashMap<>();
        objetivos.put("PUBLICACIONES", publicacionesClient.construirUrlListadoDespacho("110014189033"));
        objetivos.put("CPNU", urlCpnu + "/api/v2/Procesos/Consulta/NumeroRadicacion?numero="
                + RADICADO_SONDA + "&soloActivos=false");
        objetivos.put("SAMAI", urlSamai + "/Vistas/Casos/procesos.aspx");
        objetivos.put("INTERNET", "https://www.google.com/generate_204");
        objetivos.forEach(this::medir);
    }

    // Una vez al dia: la tabla solo necesita el historico reciente.
    @Scheduled(cron = "0 15 4 * * *")
    public void depurar() {
        try {
            int borradas = repository.borrarAnteriores(OffsetDateTime.now().minusDays(60));
            if (borradas > 0) {
                log.info("Sonda de salud: {} medición(es) de más de 60 días depurada(s).", borradas);
            }
        } catch (Exception ex) {
            log.warn("Sonda de salud: no se pudo depurar el historial: {}", ex.getMessage());
        }
    }

    private void medir(String servicio, String url) {
        long inicio = System.nanoTime();
        Integer codigo = null;
        boolean ok = false;
        String error = null;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(TIMEOUT_MS))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                            + "(KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36")
                    .GET()
                    .build();
            HttpResponse<Void> respuesta = http.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .orTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .get();
            codigo = respuesta.statusCode();
            ok = codigo >= 200 && codigo < 400;
            if (!ok) {
                error = "HTTP " + codigo;
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            error = "interrumpido";
        } catch (Exception ex) {
            Throwable causa = ex.getCause() != null ? ex.getCause() : ex;
            error = causa.getClass().getSimpleName() + (causa.getMessage() != null ? ": " + causa.getMessage() : "");
        }
        int latenciaMs = (int) ((System.nanoTime() - inicio) / 1_000_000L);
        try {
            repository.save(new PortalSalud(servicio, ok, codigo, latenciaMs,
                    error != null && error.length() > 300 ? error.substring(0, 300) : error));
        } catch (Exception ex) {
            log.warn("Sonda de salud: no se pudo guardar la medición de {}: {}", servicio, ex.getMessage());
        }
    }
}
