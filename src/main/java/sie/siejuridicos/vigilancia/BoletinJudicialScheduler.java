package sie.siejuridicos.vigilancia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

// Pedido explícito del usuario: "siempre boletin nuevo que legue se tiene que enviar tanto
// al gerente, como los abogados y sus casos asignados a las 7am, a los cliente 1 vez a la
// semana... todos los viernes". Mismo patrón que RecordatorioCitaScheduler/
// SincronizacionCasosScheduler: este componente solo decide CUÁNDO, toda la lógica real
// vive en BoletinJudicialCacheService/BoletinJudicialDigestService.
//
// REVERTIDO el 2026-10-31: la búsqueda de boletines nuevos (antes un @Scheduled propio
// aquí mismo, primero continuo cada 30 min, luego dos veces al día) ahora es parte del
// ciclo nocturno único de VigilanciaExternaOrquestador, que la corre en SECUENCIA junto con
// el seguimiento judicial y la detección de procesos nuevos -- nunca en paralelo con ellos
// (ver el comentario completo en VigilanciaExternaOrquestador sobre por qué: lanzarlos en
// paralelo volvió a colgar Publicaciones Procesales esta misma sesión). digestDiario() de
// abajo SIGUE teniendo su propio fallback de sincronización, por si el ciclo nocturno de la
// 1am no alcanzó a terminar antes de las 7am -- pero ahora pasa por
// VigilanciaExternaOrquestador.sincronizarBoletinesFallback(), que respeta el MISMO mutex
// global en vez de competir con un ciclo que ya esté en curso.
@Component
public class BoletinJudicialScheduler {

    private static final Logger log = LoggerFactory.getLogger(BoletinJudicialScheduler.class);

    private final BoletinJudicialCacheService cacheService;
    private final BoletinJudicialDigestService digestService;
    private final VigilanciaExternaOrquestador orquestador;

    public BoletinJudicialScheduler(BoletinJudicialCacheService cacheService,
                                     BoletinJudicialDigestService digestService,
                                     VigilanciaExternaOrquestador orquestador) {
        this.cacheService = cacheService;
        this.digestService = digestService;
        this.orquestador = orquestador;
    }

    @Scheduled(cron = "${app.vigilancia.boletin-purga-cron}")
    public void purgar() {
        cacheService.purgarExpirados();
    }

    @Scheduled(cron = "${app.vigilancia.boletin-digest-diario-cron}")
    public void digestDiario() {
        log.info("Iniciando digest diario de boletines judiciales (gerencia + abogados)...");
        try {
            orquestador.sincronizarBoletinesFallback();
        } catch (Exception ex) {
            log.warn("Fallo sincronizando boletines nuevos antes del digest diario: {}", ex.getMessage());
        }
        try {
            digestService.enviarDigestGerente();
        } catch (Exception ex) {
            log.warn("Fallo enviando el digest de boletines a gerencia: {}", ex.getMessage());
        }
        try {
            digestService.enviarDigestAbogados();
        } catch (Exception ex) {
            log.warn("Fallo enviando el digest de boletines a abogados: {}", ex.getMessage());
        }
    }

    @Scheduled(cron = "${app.vigilancia.boletin-digest-cliente-cron}")
    public void digestClientesSemanal() {
        log.info("Iniciando digest semanal de boletines judiciales a clientes...");
        try {
            digestService.enviarDigestClientes();
        } catch (Exception ex) {
            log.warn("Fallo enviando el digest semanal de boletines a clientes: {}", ex.getMessage());
        }
    }
}
