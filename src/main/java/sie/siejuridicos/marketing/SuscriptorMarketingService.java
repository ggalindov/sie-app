package sie.siejuridicos.marketing;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.correo.EmailService;

import java.util.List;

@Service
public class SuscriptorMarketingService {

    private final SuscriptorMarketingRepository suscriptorMarketingRepository;
    private final EmailService emailService;

    public SuscriptorMarketingService(SuscriptorMarketingRepository suscriptorMarketingRepository,
                                       EmailService emailService) {
        this.suscriptorMarketingRepository = suscriptorMarketingRepository;
        this.emailService = emailService;
    }

    // @Transactional (sin readOnly) es necesario aquí por la misma razón documentada en
    // SolicitudService.crear: el INSERT ... RETURNING de suscribirSiNoExiste falla si la
    // transacción queda en modo readOnly (el valor por defecto de los métodos de
    // repositorio de Spring Data JPA). Antes de este cambio, el controlador llamaba al
    // repositorio directamente sin este límite transaccional explícito, lo que hacía que
    // POST /api/marketing/suscriptores fallara siempre con un 500 (confirmado en vivo).
    @Transactional
    public void suscribir(String nombre, String correo) {
        List<Long> insertados = suscriptorMarketingRepository.suscribirSiNoExiste(nombre, correo);
        // Solo se envía el correo de bienvenida si la fila se insertó de verdad (correo
        // nuevo). Si ya estaba suscrito, ON CONFLICT DO NOTHING no devuelve filas y no se
        // reenvía el correo, evitando spam a alguien que ya se suscribió antes.
        if (!insertados.isEmpty()) {
            emailService.enviarBienvenidaBoletin(nombre, correo);
        }
    }
}
