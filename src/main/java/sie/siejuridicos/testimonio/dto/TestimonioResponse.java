package sie.siejuridicos.testimonio.dto;

import sie.siejuridicos.testimonio.TestimonioPublico;

import java.time.LocalDateTime;

// Vista pública: nunca incluye el correo del autor.
public record TestimonioResponse(
        Long id,
        String nombre,
        String empresa,
        String cargo,
        String cita,
        Integer calificacion,
        LocalDateTime fechaCreacion
) {
    public static TestimonioResponse desde(TestimonioPublico testimonio) {
        return new TestimonioResponse(
                testimonio.getId(),
                testimonio.getNombre(),
                testimonio.getEmpresa(),
                testimonio.getCargo(),
                testimonio.getCita(),
                testimonio.getCalificacion(),
                testimonio.getFechaCreacion()
        );
    }
}
