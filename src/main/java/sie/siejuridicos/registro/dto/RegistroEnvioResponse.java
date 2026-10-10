package sie.siejuridicos.registro.dto;

import sie.siejuridicos.registro.CanalEnvio;
import sie.siejuridicos.registro.RegistroEnvio;
import sie.siejuridicos.registro.TipoEnvio;

import java.time.LocalDateTime;

public record RegistroEnvioResponse(
        Long id,
        CanalEnvio canal,
        String canalVisible,
        TipoEnvio tipo,
        String tipoVisible,
        String destinatarioNombre,
        String destinatarioContacto,
        String resumen,
        boolean exitoso,
        LocalDateTime fechaHora
) {
    public static RegistroEnvioResponse desde(RegistroEnvio registro) {
        return new RegistroEnvioResponse(
                registro.getId(),
                registro.getCanal(),
                registro.getCanal().getNombreVisible(),
                registro.getTipo(),
                registro.getTipo().getNombreVisible(),
                registro.getDestinatarioNombre(),
                registro.getDestinatarioContacto(),
                registro.getResumen(),
                registro.isExitoso(),
                registro.getFechaHora()
        );
    }
}
