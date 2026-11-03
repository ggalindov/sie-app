package sie.siejuridicos.croma;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.croma.dto.CromaEstadoCuotaResponse;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

// Orquestador Multi-Key y Control de Cuota para Croma API (usecroma.com).
// Cada Key gratuita otorga 1,500 créditos mensuales (150 consultas de 10 créditos).
// Con 3 Keys configuradas, el sistema administra un pool total de 4,500 créditos (450 consultas)
// y salta automáticamente a la siguiente Key apenas la Key activa completa sus 1,500 créditos
// o reporta agotamiento remoto (HTTP 402/429).
@Service
public class CromaCreditosService {

    private static final Logger log = LoggerFactory.getLogger(CromaCreditosService.class);
    private static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");

    public record TicketConsumoCroma(
            String apiKey,
            int indiceKeyUnoBase,
            int totalKeys,
            int creditosReservados,
            int creditosPrevios
    ) {
    }

    private final CromaCreditosMensualRepository repository;
    private final int creditosPorKey;
    private final int creditosPorConsulta;
    private final List<String> apiKeys;
    private final int limiteCreditosTotal;

    public CromaCreditosService(
            CromaCreditosMensualRepository repository,
            @Value("${app.croma.creditos-por-key:1500}") int creditosPorKey,
            @Value("${app.croma.creditos-por-consulta:10}") int creditosPorConsulta,
            @Value("${app.croma.api-key:}") String apiKey1,
            @Value("${app.croma.api-key-2:}") String apiKey2,
            @Value("${app.croma.api-key-3:}") String apiKey3,
            @Value("${app.croma.api-key-4:}") String apiKey4,
            @Value("${app.croma.api-keys:}") String apiKeysExtra) {
        this.repository = repository;
        this.creditosPorKey = creditosPorKey > 0 ? creditosPorKey : 1500;
        this.creditosPorConsulta = creditosPorConsulta > 0 ? creditosPorConsulta : 10;
        this.apiKeys = construirListaKeys(apiKey1, apiKey2, apiKey3, apiKey4, apiKeysExtra);
        this.limiteCreditosTotal = Math.max(1, this.apiKeys.size()) * this.creditosPorKey;

        log.info("Orquestador Croma inicializado con {} API Key(s) activas -> Cupo mensual total: {} créditos ({} consultas)",
                this.apiKeys.size(), this.limiteCreditosTotal, this.limiteCreditosTotal / this.creditosPorConsulta);
    }

    private static List<String> construirListaKeys(String k1, String k2, String k3, String k4, String extraCsv) {
        Set<String> unicas = new LinkedHashSet<>();
        agregarSiValida(unicas, k1);
        agregarSiValida(unicas, k2);
        agregarSiValida(unicas, k3);
        agregarSiValida(unicas, k4);
        if (extraCsv != null && !extraCsv.isBlank()) {
            for (String parte : extraCsv.split("[,;\\s]+")) {
                agregarSiValida(unicas, parte);
            }
        }
        return List.copyOf(new ArrayList<>(unicas));
    }

    private static void agregarSiValida(Set<String> set, String raw) {
        if (raw != null) {
            String limpia = raw.strip();
            if (!limpia.isBlank()) {
                set.add(limpia);
            }
        }
    }

    public String mesActual() {
        return YearMonth.now(ZONA_COLOMBIA).toString();
    }

    @Transactional(readOnly = true)
    public CromaEstadoCuotaResponse obtenerEstadoCuota() {
        String mes = mesActual();
        CromaCreditosMensual registro = repository.findByMesAno(mes)
                .orElse(new CromaCreditosMensual(mes, limiteCreditosTotal));

        int consumidos = registro.getCreditosConsumidos();
        int limite = limiteCreditosTotal;
        int restantes = Math.max(0, limite - consumidos);
        int consultasRestantes = restantes / creditosPorConsulta;
        boolean tieneKey = !apiKeys.isEmpty();
        boolean disponible = tieneKey && restantes >= creditosPorConsulta;

        int totalKeys = Math.max(1, apiKeys.size());
        int indiceCeroBase = Math.min(totalKeys - 1, consumidos / creditosPorKey);
        int consumidosKeyActiva = consumidos >= limite ? creditosPorKey : (consumidos % creditosPorKey);

        return new CromaEstadoCuotaResponse(
                mes,
                consumidos,
                limite,
                restantes,
                registro.getConsultasRealizadas(),
                consultasRestantes,
                creditosPorConsulta,
                tieneKey,
                disponible,
                indiceCeroBase + 1,
                apiKeys.size(),
                consumidosKeyActiva,
                creditosPorKey
        );
    }

    @Transactional
    public TicketConsumoCroma adquirirKeyYRegistrarConsumo(int creditosRequeridos) {
        if (apiKeys.isEmpty()) {
            throw new CuotaCromaAgotadaException(
                    "No hay ninguna API Key de Croma configurada en el orquestador.");
        }

        String mes = mesActual();
        CromaCreditosMensual registro = repository.findByMesAnoParaActualizar(mes)
                .orElseGet(() -> repository.save(new CromaCreditosMensual(mes, limiteCreditosTotal)));

        if (registro.getLimiteCreditos() != limiteCreditosTotal) {
            registro.setLimiteCreditos(limiteCreditosTotal);
        }

        int consumidosPrevios = registro.getCreditosConsumidos();
        int indiceKey = consumidosPrevios / creditosPorKey;
        int usadosEnKeyActual = consumidosPrevios % creditosPorKey;

        int nuevoConsumoTotal;
        // Si la Key actual ya no tiene saldo suficiente para cubrir esta consulta completa (ej. 10 créditos),
        // saltamos automáticamente al inicio del bloque de la siguiente Key para nunca superar los 1,500 por Key.
        if (usadosEnKeyActual + creditosRequeridos > creditosPorKey) {
            int keyAgotadaUnoBase = indiceKey + 1;
            indiceKey++;
            nuevoConsumoTotal = (indiceKey * creditosPorKey) + creditosRequeridos;
            if (indiceKey < apiKeys.size()) {
                log.info("Orquestador Croma: Key #{} completó su cupo de {} créditos (150 consultas). Saltando automáticamente a Key #{}",
                        keyAgotadaUnoBase, creditosPorKey, indiceKey + 1);
            }
        } else {
            nuevoConsumoTotal = consumidosPrevios + creditosRequeridos;
        }

        if (indiceKey >= apiKeys.size() || nuevoConsumoTotal > limiteCreditosTotal) {
            int restantes = Math.max(0, limiteCreditosTotal - consumidosPrevios);
            log.warn("Orquestador Croma: Todas las {} Keys agotaron su cuota mensual ({}/{} créditos). Restantes: {}",
                    apiKeys.size(), consumidosPrevios, limiteCreditosTotal, restantes);
            throw new CuotaCromaAgotadaException(
                    "Se agotaron los " + limiteCreditosTotal + " créditos mensuales en las "
                            + apiKeys.size() + " Keys de Croma (" + (limiteCreditosTotal / creditosPorConsulta)
                            + " consultas totales). La cuota se renovará el primer día del próximo mes.");
        }

        registro.setCreditosConsumidos(nuevoConsumoTotal);
        registro.setConsultasRealizadas(registro.getConsultasRealizadas() + 1);
        registro.setFechaActualizacion(OffsetDateTime.now());
        repository.save(registro);

        String keySeleccionada = apiKeys.get(indiceKey);
        int usadosEnKeyNueva = nuevoConsumoTotal - (indiceKey * creditosPorKey);
        log.info("Orquestador Croma [Key #{}/{}]: +{} créditos en {}. Uso en Key actual: {}/{}. Total pool mes: {}/{}",
                indiceKey + 1, apiKeys.size(), creditosRequeridos, mes,
                usadosEnKeyNueva, creditosPorKey, nuevoConsumoTotal, limiteCreditosTotal);

        return new TicketConsumoCroma(
                keySeleccionada,
                indiceKey + 1,
                apiKeys.size(),
                creditosRequeridos,
                consumidosPrevios
        );
    }

    // Failover en tiempo real: si Croma responde HTTP 401/402/429 indicando que la Key actual ya no tiene saldo
    // en el servidor de Croma, el orquestador marca esa Key como completada y salta de inmediato a la siguiente Key.
    @Transactional
    public Optional<TicketConsumoCroma> saltarASiguienteKeyPorAgotamientoRemoto(TicketConsumoCroma ticketFallido) {
        int siguienteIndiceCeroBase = ticketFallido.indiceKeyUnoBase(); // porque indiceKeyUnoBase es 1-based
        if (siguienteIndiceCeroBase >= apiKeys.size()) {
            return Optional.empty();
        }

        String mes = mesActual();
        CromaCreditosMensual registro = repository.findByMesAnoParaActualizar(mes)
                .orElseGet(() -> repository.save(new CromaCreditosMensual(mes, limiteCreditosTotal)));

        if (registro.getLimiteCreditos() != limiteCreditosTotal) {
            registro.setLimiteCreditos(limiteCreditosTotal);
        }

        int baseSiguienteKey = siguienteIndiceCeroBase * creditosPorKey;
        int nuevoTotal = baseSiguienteKey + ticketFallido.creditosReservados();
        if (nuevoTotal > limiteCreditosTotal) {
            return Optional.empty();
        }

        registro.setCreditosConsumidos(nuevoTotal);
        registro.setFechaActualizacion(OffsetDateTime.now());
        repository.save(registro);

        String nuevaKey = apiKeys.get(siguienteIndiceCeroBase);
        log.warn("Orquestador Croma Failover: Key #{} reportó agotamiento/rechazo remoto. Saltando automáticamente a Key #{}/{}",
                ticketFallido.indiceKeyUnoBase(), siguienteIndiceCeroBase + 1, apiKeys.size());

        return Optional.of(new TicketConsumoCroma(
                nuevaKey,
                siguienteIndiceCeroBase + 1,
                apiKeys.size(),
                ticketFallido.creditosReservados(),
                baseSiguienteKey
        ));
    }

    @Transactional
    public void reintegrarConsumoFallido(TicketConsumoCroma ticket) {
        if (ticket == null) {
            return;
        }
        String mes = mesActual();
        repository.findByMesAnoParaActualizar(mes).ifPresent(registro -> {
            registro.setCreditosConsumidos(Math.max(0, ticket.creditosPrevios()));
            registro.setConsultasRealizadas(Math.max(0, registro.getConsultasRealizadas() - 1));
            registro.setFechaActualizacion(OffsetDateTime.now());
            repository.save(registro);
            log.info("Orquestador Croma: Reintegrados {} créditos en {} tras fallo de red/API. Total mes restaurado a: {}/{}",
                    ticket.creditosReservados(), mes, registro.getCreditosConsumidos(), registro.getLimiteCreditos());
        });
    }

    @Transactional
    public void reintegrarConsumoFallido(int creditosAReintegrar) {
        String mes = mesActual();
        repository.findByMesAnoParaActualizar(mes).ifPresent(registro -> {
            registro.setCreditosConsumidos(Math.max(0, registro.getCreditosConsumidos() - creditosAReintegrar));
            registro.setConsultasRealizadas(Math.max(0, registro.getConsultasRealizadas() - 1));
            registro.setFechaActualizacion(OffsetDateTime.now());
            repository.save(registro);
        });
    }

    public int getCreditosPorConsulta() {
        return creditosPorConsulta;
    }

    public boolean tieneApiKeyConfigurada() {
        return !apiKeys.isEmpty();
    }

    public int getTotalKeysConfiguradas() {
        return apiKeys.size();
    }
}
