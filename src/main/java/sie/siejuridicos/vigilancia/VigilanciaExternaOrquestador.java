package sie.siejuridicos.vigilancia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import sie.siejuridicos.registro.RegistroSistemaService;
import sie.siejuridicos.registro.TipoRegistroSistema;
import sie.siejuridicos.vigilancia.dto.ResumenSeguimientoJudicial;

import java.util.concurrent.atomic.AtomicBoolean;

// Bug real encontrado en vivo (2026-10-31, pedido explícito del usuario: "ajusta el sistema
// para que eso nunca pase"): seguimiento judicial (CPNU/SAMAI), recopilación de boletines
// (Publicaciones Procesales) y detección de procesos nuevos (CPNU) son tres procesos
// distintos, cada uno con su propio mutex interno -- pero nada impedía que corrieran EN
// PARALELO entre sí (ej. un disparo manual desde el panel mientras el ciclo automático de
// otro ya estaba en curso, que es exactamente lo que pasó esta sesión). Confirmado en vivo:
// lanzar seguimiento + boletines a la vez hizo que Publicaciones Procesales volviera a
// colgarse con "Read timed out" en despachos que acababan de responder bien segundos antes
// en una prueba aislada -- el tráfico COMBINADO hacia la infraestructura de la Rama
// Judicial (que comparte WAF/CDN entre subdominios: ramajudicial.gov.co) dispara la misma
// protección anti-automatización que ya bloqueó al sistema antes en esta misma sesión,
// aunque cada proceso por separado sea respetuoso con su propia pausa.
//
// Este orquestador es el ÚNICO punto de entrada real a los tres (automático Y manual desde
// el panel, ver VigilanciaAdminController): un mutex GLOBAL (cicloEnCurso) que se toma antes
// de cualquiera de los tres y se libera solo cuando TODO terminó, así que es
// estructuralmente imposible que dos corran a la vez sin importar quién los dispare.
//
// El ciclo automático se intenta cada hora en punto, de 7:00 AM a 7:00 PM (ver
// app.vigilancia.ciclo-cron) -- pedido explícito del usuario, 2026-11-04: "el sistema no debe
// de parar de hacer busquedas", "dejemoslo con espacios de 60 minutos entre uno y otro".
// Sigue sin ser tráfico verdaderamente paralelo ni un machaque continuo: el mutex
// cicloEnCurso de más abajo hace que un disparo que llega con el ciclo anterior todavía en
// curso simplemente se ignore, así que este cron define la frecuencia MÁXIMA de arranque, no
// una garantía de 13 ciclos completos independientes por día -- con la cartera actual de
// casos de la firma, cada ciclo completo puede tardar varias horas, así que en la práctica
// esto se traduce en "arrancar el siguiente ciclo lo antes posible dentro de la ventana
// 7am-7pm en cuanto el anterior libera el mutex", en vez de esperar hasta el próximo tramo
// fijo de 6 horas como antes (7am/1pm).
//
// Los tres pasos van en SECUENCIA, nunca en paralelo entre ellos tampoco, y en este orden
// deliberado: seguimiento primero (así el historial de actuaciones queda fresco ANTES de
// que boletines lo use para buscar documentos de las actuaciones más recientes), boletines
// segundo, detección de procesos nuevos al final (la menos urgente de las tres, ver
// DeteccionProcesosNuevosScheduler original). Un fallo en un paso NUNCA debe impedir que
// los siguientes corran -- cada uno ya es robusto por sí mismo (ningún cliente externo de
// este sistema lanza, ver RamaJudicialCpnuClient/PublicacionesProcesalesClient/SamaiClient),
// pero igual se envuelve cada paso para que un error inesperado real no tumbe el ciclo
// completo.
//
// Auditoría de SIE-Vigilancia (pedido explícito del usuario, 2026-10-31: "implementa...
// exceptions, alertas, anuncios de caidas, deteccion, seguimiento para Usuarios"): cada paso
// queda registrado en RegistroSistema (visible en /admin/registro, filtro "Sincronización de
// datos") con su resultado real -- "seguimiento para usuarios" sin necesitar una página
// nueva, reutilizando la bitácora genérica que el sistema ya tiene para el resto de procesos
// automáticos. Si el cliente externo correspondiente "pareceCaido()" al terminar un paso, el
// registro queda exitoso=false y se publica VigilanciaExternaCaidaEvent, que
// VigilanciaCaidaAlertaListener reenvía por correo a todo ADMIN_GENERAL activo -- el "anuncio
// de caída" real, no solo un log que nadie revisa.
@Component
public class VigilanciaExternaOrquestador {

    private static final Logger log = LoggerFactory.getLogger(VigilanciaExternaOrquestador.class);

    private final SeguimientoJudicialService seguimientoJudicialService;
    private final BoletinJudicialCacheService boletinJudicialCacheService;
    private final DeteccionProcesosNuevosService deteccionProcesosNuevosService;
    private final RamaJudicialCpnuClient cpnuClient;
    private final SamaiClient samaiClient;
    private final PublicacionesProcesalesClient publicacionesProcesalesClient;
    private final RegistroSistemaService registroSistemaService;
    private final ApplicationEventPublisher eventPublisher;
    private final AtomicBoolean cicloEnCurso = new AtomicBoolean(false);

    public VigilanciaExternaOrquestador(SeguimientoJudicialService seguimientoJudicialService,
                                         BoletinJudicialCacheService boletinJudicialCacheService,
                                         DeteccionProcesosNuevosService deteccionProcesosNuevosService,
                                         RamaJudicialCpnuClient cpnuClient,
                                         SamaiClient samaiClient,
                                         PublicacionesProcesalesClient publicacionesProcesalesClient,
                                         RegistroSistemaService registroSistemaService,
                                         ApplicationEventPublisher eventPublisher) {
        this.seguimientoJudicialService = seguimientoJudicialService;
        this.boletinJudicialCacheService = boletinJudicialCacheService;
        this.deteccionProcesosNuevosService = deteccionProcesosNuevosService;
        this.cpnuClient = cpnuClient;
        this.samaiClient = samaiClient;
        this.publicacionesProcesalesClient = publicacionesProcesalesClient;
        this.registroSistemaService = registroSistemaService;
        this.eventPublisher = eventPublisher;
    }

    // Un intento de arranque cada hora en punto, 7:00 AM a 7:00 PM inclusive (ver el
    // comentario de cabecera de esta clase). ejecutarSiLibre() ya absorbe sin efecto los
    // disparos que llegan con el ciclo anterior todavía en curso.
    @Scheduled(cron = "${app.vigilancia.ciclo-cron:0 0 7-19 * * *}")
    public void ejecutarCicloHorario() {
        ejecutarSiLibre("ciclo automático horario (7am-7pm)");
    }

    // Disparo manual desde el panel admin (ver VigilanciaAdminController: los tres botones
    // de "sincronizar ahora" pasan por aquí, nunca directo al servicio) -- MISMO mutex que el
    // ciclo nocturno, @Async para no colgar la petición HTTP (el ciclo completo tarda horas
    // con la cartera real de la firma).
    @Async
    public void ejecutarManualAsync() {
        ejecutarSiLibre("disparo manual desde el panel");
    }

    // Fallback síncrono usado SOLO por BoletinJudicialScheduler.digestDiario() -- por si el
    // ciclo nocturno completo justo está en curso o no alcanzó a terminar antes de las 7am.
    // Respeta el MISMO mutex global: si el ciclo nocturno está en curso, no hace nada
    // (best-effort, igual que el fallback original) en vez de competir por el mismo tráfico
    // hacia Publicaciones Procesales.
    @Async
    public void sincronizarSoloBoletinesAsync() {
        sincronizarBoletinesFallback();
    }

    // Solo la etapa de reintentos de boletines (vacia la cola de pendientes sin recorrer de
    // nuevo todas las actuaciones). Mismo mutex global que el resto.
    @Async
    public void reintentarPendientesBoletinesAsync() {
        if (!cicloEnCurso.compareAndSet(false, true)) {
            log.info("Vigilancia externa: ciclo en curso, se omite el reintento de pendientes de boletines.");
            return;
        }
        try {
            boletinJudicialCacheService.reintentarPendientes();
        } finally {
            cicloEnCurso.set(false);
        }
    }

    public void sincronizarBoletinesFallback() {
        // Hallazgo del 2026-11-02: el digest de las 7am llamaba esto SIEMPRE y repetia la pasada
        // completa de boletines (unas 2.5 h con las pausas) antes de enviar los correos, aunque
        // el ciclo ya hubiera terminado horas antes. Solo es un respaldo: si una pasada termino
        // hace menos de 20 h, no se repite.
        java.time.LocalDateTime ultimoFin = boletinJudicialCacheService.progreso().fin();
        if (ultimoFin != null && ultimoFin.isAfter(java.time.LocalDateTime.now().minusHours(20))) {
            log.info("Vigilancia externa: una pasada de boletines terminó a las {} (hace menos de 20 h), "
                    + "se omite el respaldo previo al digest diario.", ultimoFin);
            return;
        }
        if (!cicloEnCurso.compareAndSet(false, true)) {
            log.info("Vigilancia externa: ciclo en curso, se omite el fallback de boletines previo al digest diario.");
            return;
        }
        try {
            ejecutarPasoBoletines();
        } finally {
            cicloEnCurso.set(false);
        }
    }

    private void ejecutarSiLibre(String origen) {
        if (!cicloEnCurso.compareAndSet(false, true)) {
            log.info("Vigilancia externa: ya hay un ciclo en curso, se ignora esta solicitud ({}).", origen);
            return;
        }
        try {
            log.info("Vigilancia externa: iniciando ciclo completo ({})...", origen);
            ejecutarPasoSeguimiento();
            ejecutarPasoBoletines();
            ejecutarPasoDeteccion();
            log.info("Vigilancia externa: ciclo completo ({}) finalizado.", origen);
        } finally {
            cicloEnCurso.set(false);
        }
    }

    private void ejecutarPasoSeguimiento() {
        try {
            ResumenSeguimientoJudicial resumen = seguimientoJudicialService.sincronizarTodos();
            String descripcion = "%d caso(s) revisado(s), %d con proceso CPNU, %d actuación(es) nueva(s) (%d de fondo)"
                    .formatted(resumen.casosRevisados(), resumen.casosConProcesoCpnu(),
                            resumen.actuacionesNuevas(), resumen.actuacionesFundamentalesNuevas());
            log.info("Vigilancia externa: seguimiento judicial terminado -- {}", descripcion);
            // Dos clientes externos distintos en este paso (CPNU para Rama Judicial
            // ordinaria, SAMAI para Consejo de Estado/contencioso-administrativo) -- cualquiera
            // de los dos pareciendo caído ya es motivo de alerta, pero el registro queda
            // exitoso=false solo si AMBOS (o el único que de verdad se usó) fallaron, nunca
            // por uno que simplemente no tenía nada que resolver en esta corrida.
            boolean cpnuCaido = cpnuClient.pareceCaido();
            boolean samaiCaido = samaiClient.pareceCaido();
            if (cpnuCaido) {
                eventPublisher.publishEvent(new VigilanciaExternaCaidaEvent("Rama Judicial (CPNU)",
                        "No respondió en la última corrida de seguimiento judicial."));
            }
            if (samaiCaido) {
                eventPublisher.publishEvent(new VigilanciaExternaCaidaEvent("SAMAI (Consejo de Estado)",
                        "No respondió en la última corrida de seguimiento judicial."));
            }
            String detalle = (cpnuCaido ? "Rama Judicial (CPNU) no respondió. " : "")
                    + (samaiCaido ? "SAMAI no respondió." : "");
            registroSistemaService.registrar(TipoRegistroSistema.SEGUIMIENTO_JUDICIAL, descripcion,
                    detalle.isBlank() ? null : detalle.strip(), !cpnuCaido && !samaiCaido);
        } catch (Exception ex) {
            log.warn("Vigilancia externa: fallo inesperado en el seguimiento judicial, se continúa con boletines: {}",
                    ex.getMessage());
            registroSistemaService.registrar(TipoRegistroSistema.SEGUIMIENTO_JUDICIAL,
                    "Fallo inesperado durante el seguimiento judicial", ex.getMessage(), false);
        }
    }

    private void ejecutarPasoBoletines() {
        try {
            boletinJudicialCacheService.sincronizarNuevosBoletines();
            log.info("Vigilancia externa: recopilación de boletines terminada.");
            boolean caido = publicacionesProcesalesClient.pareceCaido();
            long pendientes = boletinJudicialCacheService.contarPendientes();
            String detalleBoletines = (caido ? "Publicaciones Procesales no respondió (varios fallos seguidos). " : "")
                    + (pendientes > 0
                    ? pendientes + " boletín(es)/búsqueda(s) quedaron en cola y se reintentarán en el próximo ciclo."
                    : "");
            registroSistemaService.registrar(TipoRegistroSistema.VIGILANCIA_BOLETINES,
                    "Recopilación de boletines judiciales ejecutada",
                    detalleBoletines.isBlank() ? null : detalleBoletines.strip(), !caido);
            if (caido) {
                eventPublisher.publishEvent(new VigilanciaExternaCaidaEvent("Publicaciones Procesales",
                        "No respondió en la última corrida de recopilación de boletines judiciales."));
            }
        } catch (Exception ex) {
            log.warn("Vigilancia externa: fallo inesperado recopilando boletines, se continúa con detección de procesos: {}",
                    ex.getMessage());
            registroSistemaService.registrar(TipoRegistroSistema.VIGILANCIA_BOLETINES,
                    "Fallo inesperado recopilando boletines judiciales", ex.getMessage(), false);
        }
    }

    private void ejecutarPasoDeteccion() {
        try {
            deteccionProcesosNuevosService.buscarNuevosProcesos();
            log.info("Vigilancia externa: detección de procesos nuevos terminada.");
            boolean caido = cpnuClient.pareceCaido();
            registroSistemaService.registrar(TipoRegistroSistema.VIGILANCIA_DETECCION_PROCESOS,
                    "Detección de procesos nuevos por nombre de cliente ejecutada",
                    caido ? "Rama Judicial (CPNU) no respondió (varios fallos seguidos)." : null, !caido);
            if (caido) {
                eventPublisher.publishEvent(new VigilanciaExternaCaidaEvent("Rama Judicial (CPNU)",
                        "No respondió en la última corrida de detección de procesos nuevos."));
            }
        } catch (Exception ex) {
            log.warn("Vigilancia externa: fallo inesperado en la detección de procesos nuevos: {}", ex.getMessage());
            registroSistemaService.registrar(TipoRegistroSistema.VIGILANCIA_DETECCION_PROCESOS,
                    "Fallo inesperado en la detección de procesos nuevos", ex.getMessage(), false);
        }
    }
}
