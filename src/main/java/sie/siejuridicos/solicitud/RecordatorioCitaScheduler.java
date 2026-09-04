package sie.siejuridicos.solicitud;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.correo.EmailService;
import sie.siejuridicos.registro.RegistroSistemaService;
import sie.siejuridicos.registro.TipoRegistroSistema;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
public class RecordatorioCitaScheduler {

    private static final Logger log = LoggerFactory.getLogger(RecordatorioCitaScheduler.class);

    private final SolicitudRepository solicitudRepository;
    private final EmailService emailService;
    private final RegistroSistemaService registroSistemaService;

    public RecordatorioCitaScheduler(SolicitudRepository solicitudRepository, EmailService emailService,
                                      RegistroSistemaService registroSistemaService) {
        this.solicitudRepository = solicitudRepository;
        this.emailService = emailService;
        this.registroSistemaService = registroSistemaService;
    }

    @Scheduled(cron = "${app.recordatorios.cron}")
    @Transactional
    public void enviarRecordatoriosDelDia() {
        LocalDate hoy = LocalDate.now();
        LocalDateTime inicio = hoy.atStartOfDay();
        LocalDateTime fin = hoy.plusDays(1).atStartOfDay();

        List<Solicitud> citasDeHoy = solicitudRepository.findByFechaCitaBetweenAndRecordatorioEnviadoFalse(inicio, fin);
        if (citasDeHoy.isEmpty()) {
            return;
        }

        log.info("Enviando {} recordatorio(s) de cita programada para hoy", citasDeHoy.size());
        for (Solicitud solicitud : citasDeHoy) {
            emailService.enviarRecordatorioCita(solicitud);
            // se marca como enviado de forma optimista (el envío es asíncrono): es preferible
            // arriesgarse a perder un recordatorio por una falla puntual de SMTP a duplicarlo
            // si el job vuelve a correr el mismo día. Volumen bajo (citas del día, nunca
            // cientos a la vez como Casos/Cobros), por eso no necesita el mismo throttle.
            solicitud.setRecordatorioEnviado(true);
        }
        solicitudRepository.saveAll(citasDeHoy);

        registroSistemaService.registrar(
                TipoRegistroSistema.RECORDATORIO_CITA,
                "%d recordatorio(s) de cita enviado(s)".formatted(citasDeHoy.size()),
                true);
    }
}
