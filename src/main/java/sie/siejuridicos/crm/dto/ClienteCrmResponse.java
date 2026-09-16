package sie.siejuridicos.crm.dto;

import sie.siejuridicos.crm.ClienteCrm;
import sie.siejuridicos.crm.EstadoClienteCrm;
import sie.siejuridicos.crm.TipoClienteCrm;

import java.time.LocalDateTime;

public record ClienteCrmResponse(
        Long id,
        TipoClienteCrm tipo,
        String nombre,
        String cedulaNit,
        String correo,
        String telefono,
        String direccion,
        String ciudad,
        EstadoClienteCrm estado,
        String etiqueta,
        String notas,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaActualizacion,
        long totalCasos,
        long totalCobros,
        boolean pagoAlDia
) {
    public static ClienteCrmResponse desde(ClienteCrm c, long totalCasos, long totalCobros, boolean pagoAlDia) {
        return new ClienteCrmResponse(
                c.getId(),
                c.getTipo(),
                c.getNombre(),
                c.getCedulaNit(),
                c.getCorreo(),
                c.getTelefono(),
                c.getDireccion(),
                c.getCiudad(),
                c.getEstado(),
                c.getEtiqueta(),
                c.getNotas(),
                c.getFechaCreacion(),
                c.getFechaActualizacion(),
                totalCasos,
                totalCobros,
                pagoAlDia
        );
    }
}
