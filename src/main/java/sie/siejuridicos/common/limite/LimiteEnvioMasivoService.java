package sie.siejuridicos.common.limite;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// Cupo diario COMPARTIDO de envíos masivos (correo + WhatsApp), usado por CasoService
// (notificaciones de radicado, reporte semanal) y CobroService (recordatorios de cobro) --
// pedido explícito del usuario: WhatsApp Business tiene un límite real de 250 mensajes
// salientes por día, y el mismo cupo se comparte entre TODOS los orígenes porque el límite es
// de la cuenta completa, no de una funcionalidad puntual. Un envío masivo manual (300
// destinatarios, por ejemplo) procesa hasta el cupo restante de hoy y deja el resto pendiente
// -- cada llamador es responsable de detenerse en cuanto intentarReservarCupo() devuelva
// false, y de dejar sin marcar como "enviado" a los destinatarios que no alcanzó a procesar,
// para que la próxima corrida (automática al día siguiente, o un nuevo click manual) los
// recoja de nuevo sin perder a nadie ni duplicar a quien ya se le envió.
@Service
public class LimiteEnvioMasivoService {

    private final EnvioMasivoContadorDiarioRepository repository;
    private final int limiteDiario;

    public LimiteEnvioMasivoService(EnvioMasivoContadorDiarioRepository repository,
                                     @Value("${app.envios-masivos.limite-diario:250}") int limiteDiario) {
        this.repository = repository;
        this.limiteDiario = limiteDiario;
    }

    // Intenta reservar UNA unidad de cupo para hoy (un destinatario, sin importar cuántos
    // canales le toquen). Atómico a nivel de base de datos (ver fn_reservar_cupo_envio_masivo),
    // así que es seguro llamarlo desde varios orígenes (casos, cobros) sin coordinarse entre
    // sí -- cada uno simplemente para su propio bucle en cuanto esto devuelve false.
    public boolean intentarReservarCupo() {
        return repository.reservarCupo(limiteDiario);
    }
}
