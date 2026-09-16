package sie.siejuridicos.solicitud;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.registro.RegistroSistemaService;
import sie.siejuridicos.registro.TipoRegistroSistema;

import java.time.LocalDateTime;
import java.util.List;

// Pedido explícito del usuario: "1 hora antes de la reunión les debe volver a mandar su
// recordatorio SIEMPRE". ANTES este job corría una vez al día (temprano en la mañana) y
// mandaba solo correo a TODAS las citas del día completo -- entre agendar una reunión a las
// 8am y que el recordatorio de las 7am ya hubiera pasado, o una reunión de última hora
// agendada después de que el job del día ya corrió, el cliente podía terminar sin ningún
// recordatorio real antes de su cita. Ahora corre cada 5 minutos y manda el recordatorio
// (correo + WhatsApp, ver SolicitudService.enviarRecordatorioCita) a cualquier cita que
// caiga en la próxima hora exacta desde este momento -- sin importar a qué hora del día sea
// la reunión, siempre llega ~1 hora antes.
@Component
public class RecordatorioCitaScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecordatorioCitaScheduler.class);

    private final SolicitudRepository solicitudRepository;
    private final SolicitudService solicitudService;
    private final RegistroSistemaService registroSistemaService;
    private final boolean bloqueoTotalClientes;

    public RecordatorioCitaScheduler(SolicitudRepository solicitudRepository, SolicitudService solicitudService,
                                      RegistroSistemaService registroSistemaService,
                                      @Value("${app.bloqueo-total-clientes:true}") boolean bloqueoTotalClientes) {
        this.solicitudRepository = solicitudRepository;
        this.solicitudService = solicitudService;
        this.registroSistemaService = registroSistemaService;
        this.bloqueoTotalClientes = bloqueoTotalClientes;
    }

    @Scheduled(cron = "${app.recordatorios.cron}")
    @Transactional
    public void enviarRecordatoriosDeLaProximaHora() {
        if (bloqueoTotalClientes) {
            return;
        }
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime enUnaHora = ahora.plusHours(1);

        List<Solicitud> citasProximas =
                solicitudRepository.findByFechaCitaBetweenAndRecordatorioEnviadoFalse(ahora, enUnaHora);
        if (citasProximas.isEmpty()) {
            return;
        }

        log.info("Enviando {} recordatorio(s) de cita a menos de 1 hora de empezar", citasProximas.size());
        for (Solicitud solicitud : citasProximas) {
            solicitudService.enviarRecordatorioCita(solicitud);
            // se marca como enviado de forma optimista (el envío es asíncrono): es preferible
            // arriesgarse a perder un recordatorio por una falla puntual de SMTP/Meta a
            // duplicarlo si el job vuelve a correr 5 minutos después. Volumen bajo (citas de
            // la próxima hora, nunca cientos a la vez como Casos/Cobros), por eso no necesita
            // el mismo throttle de pausa-entre-envíos.
            solicitud.setRecordatorioEnviado(true);
        }
        solicitudRepository.saveAll(citasProximas);

        registroSistemaService.registrar(
                TipoRegistroSistema.RECORDATORIO_CITA,
                "%d recordatorio(s) de cita enviado(s) (correo + WhatsApp)".formatted(citasProximas.size()),
                true);
    }
}
