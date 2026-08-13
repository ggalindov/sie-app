package sie.siejuridicos.testimonio.dto;

import sie.siejuridicos.testimonio.EstadoTestimonio;
import sie.siejuridicos.testimonio.TestimonioPublico;

import java.time.LocalDateTime;

public record TestimonioAdminResponse(
        Long id,
        String nombre,
        String empresa,
        String cargo,
        String cita,
        Integer calificacion,
        String correo,
        EstadoTestimonio estado,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaModeracion
) {
    public static TestimonioAdminResponse desde(TestimonioPublico testimonio) {
        return new TestimonioAdminResponse(
                testimonio.getId(),
                testimonio.getNombre(),
                testimonio.getEmpresa(),
                testimonio.getCargo(),
                testimonio.getCita(),
                testimonio.getCalificacion(),
                testimonio.getCorreo(),
                testimonio.getEstado(),
                testimonio.getFechaCreacion(),
                testimonio.getFechaModeracion()
        );
    }
}
