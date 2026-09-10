package sie.siejuridicos.caso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import sie.siejuridicos.caso.dto.ResumenReporteSemanal;

// Desacoplado a pedido explícito del usuario:
// - Correo: semanalmente (una vez a la semana, cada lunes) a costo $0 por SMTP.
// - WhatsApp: quincenalmente (dos veces al mes: días 1 y 15) para reducir costos de Meta Cloud API.
// Todos los clientes con un caso activo (radicado ya asignado) reciben un recordatorio para que consulten
// el estado de su proceso.
// El cron corre a DIARIO a las 8:00 AM (ver app.casos.reporte-semanal-cron): con el cupo diario compartido
// de envíos masivos (ver LimiteEnvioMasivoService), un lote grande puede no alcanzar a completarse en
// un solo día, así que la corrida diaria es lo que recoge automáticamente al día siguiente a quien
// quedó pendiente por el límite diario -- CasoService.enviarReporteSemanal() ya filtra por quién sigue
// sin el reporte del periodo en curso según su canal.
@Component
public class ReporteSemanalCasosScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReporteSemanalCasosScheduler.class);

    private final CasoService casoService;

    public ReporteSemanalCasosScheduler(CasoService casoService) {
        this.casoService = casoService;
    }

    @Scheduled(cron = "${app.casos.reporte-semanal-cron}")
    public void enviarReporteSemanal() {
        ResumenReporteSemanal resumen = casoService.enviarReporteSemanal();
        log.info("Reporte periódico de casos (Correo semanal, WhatsApp 2 veces/mes): {} caso(s) evaluados, {} correo(s) enviado(s), "
                        + "{} fallido(s); {} WhatsApp enviado(s), {} fallido(s); {} pendiente(s) por el límite diario; "
                        + "{} omitido(s) por no tener un cliente real identificable",
                resumen.casosConReporte(), resumen.correosEnviados(), resumen.correosFallidos(),
                resumen.whatsappEnviados(), resumen.whatsappFallidos(), resumen.pendientesPorLimiteDiario(),
                resumen.omitidosSinClienteReal());
    }
}
