package sie.siejuridicos.registro.dto;

import sie.siejuridicos.registro.CategoriaRegistroSistema;
import sie.siejuridicos.registro.RegistroSistema;
import sie.siejuridicos.registro.TipoRegistroSistema;

import java.time.LocalDateTime;

public record RegistroSistemaResponse(
        Long id,
        TipoRegistroSistema tipo,
        String tipoVisible,
        CategoriaRegistroSistema categoria,
        String categoriaVisible,
        String descripcion,
        String detalle,
        boolean exitoso,
        LocalDateTime fechaHora
) {
    public static RegistroSistemaResponse desde(RegistroSistema registro) {
        return new RegistroSistemaResponse(
                registro.getId(),
                registro.getTipo(),
                registro.getTipo().getNombreVisible(),
                registro.getTipo().getCategoria(),
                registro.getTipo().getCategoria().getNombreVisible(),
                registro.getDescripcion(),
                registro.getDetalle(),
                registro.isExitoso(),
                registro.getFechaHora()
        );
    }
}
