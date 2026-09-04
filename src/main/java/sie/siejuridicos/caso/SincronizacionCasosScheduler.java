package sie.siejuridicos.caso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import sie.siejuridicos.caso.dto.ResumenSincronizacion;

// Antes la sincronización con el Google Sheets de la firma era 100% manual (el botón
// "Actualizar" de /admin/casos): un caso nuevo, o un radicado que el despacho recién asignó
// en la hoja, se quedaba sin reflejarse en el sistema hasta que algún admin entrara al panel
// y lo disparara a mano. Bug real reportado por el usuario ("todos los de superintendencia
// están mal, no están jalando la info correctamente"): 34 casos de Superintendencia ya
// tenían su radicado asignado en la hoja real (confirmado leyéndola directamente), pero
// seguían con radicado_id vacío en el sistema porque nadie había vuelto a sincronizar desde
// que la firma los completó -- no era un bug de lectura de columnas, era que los datos
// nunca se volvían a traer.
//
// Mismo patrón que RecordatorioCitaScheduler/RecordatorioCobroScheduler: este componente
// solo decide CUÁNDO, toda la lógica real (crear/actualizar/eliminar, deduplicar radicados,
// registrar en el Registro del Sistema) sigue en CasoService.sincronizarDesdeHoja(), la
// misma que usa el botón manual -- ambos caminos terminan en el mismo lugar, así que
// "sincronizar a mano" y "esperar al próximo ciclo automático" nunca pueden divergir en
// comportamiento. El botón manual del panel sigue teniendo sentido para cuando un admin
// necesita el radicado reflejado YA, sin esperar al próximo ciclo.
@Component
public class SincronizacionCasosScheduler {

    private static final Logger log = LoggerFactory.getLogger(SincronizacionCasosScheduler.class);

    private final CasoService casoService;

    public SincronizacionCasosScheduler(CasoService casoService) {
        this.casoService = casoService;
    }

    // Corre una vez apenas la aplicación termina de arrancar (además del ciclo periódico de
    // abajo): así un reinicio/despliegue del backend nunca deja datos obsoletos esperando
    // hasta 30 minutos por el próximo ciclo -- justo el escenario que causó el reporte
    // original ("no están jalando la info correctamente").
    @EventListener(ApplicationReadyEvent.class)
    public void sincronizarAlArrancar() {
        sincronizar();
    }

    @Scheduled(cron = "${app.casos.sincronizacion-cron}")
    public void sincronizar() {
        ResumenSincronizacion resumen = casoService.sincronizarDesdeHoja();
        log.info("Sincronización automática de casos: {} fila(s) leída(s), {} nuevo(s), {} actualizado(s), "
                        + "{} eliminado(s), {} sin correo, {} radicado(s) duplicado(s)",
                resumen.filasLeidasEnHoja(), resumen.casosNuevos(), resumen.casosActualizados(),
                resumen.casosEliminados(), resumen.filasSinCorreo(), resumen.radicadosDuplicados());
    }
}
