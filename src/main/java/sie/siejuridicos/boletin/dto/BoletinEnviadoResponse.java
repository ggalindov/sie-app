package sie.siejuridicos.boletin.dto;

import sie.siejuridicos.boletin.BoletinEnviado;

import java.time.LocalDateTime;

public record BoletinEnviadoResponse(
        Long id,
        Integer cantidadPublicaciones,
        Integer cantidadDestinatarios,
        LocalDateTime fechaEnvio
) {
    public static BoletinEnviadoResponse desde(BoletinEnviado boletin) {
        return new BoletinEnviadoResponse(
                boletin.getId(),
                boletin.getCantidadPublicaciones(),
                boletin.getCantidadDestinatarios(),
                boletin.getFechaEnvio()
        );
    }
}
