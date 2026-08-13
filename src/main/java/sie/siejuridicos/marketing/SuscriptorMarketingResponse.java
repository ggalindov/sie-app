package sie.siejuridicos.marketing;

import java.time.LocalDateTime;

public record SuscriptorMarketingResponse(
        Long id,
        String nombre,
        String correo,
        LocalDateTime fechaSuscripcion
) {
    public static SuscriptorMarketingResponse desde(SuscriptorMarketing suscriptor) {
        return new SuscriptorMarketingResponse(
                suscriptor.getId(),
                suscriptor.getNombre(),
                suscriptor.getCorreo(),
                suscriptor.getFechaSuscripcion()
        );
    }
}
