package sie.siejuridicos.crm.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import sie.siejuridicos.crm.EstadoClienteCrm;
import sie.siejuridicos.crm.TipoClienteCrm;

public record CrearClienteCrmRequest(
        @NotNull(message = "El tipo de cliente es obligatorio")
        TipoClienteCrm tipo,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 200, message = "El nombre no puede superar 200 caracteres")
        String nombre,

        @Size(max = 50, message = "La identificación no puede superar 50 caracteres")
        String cedulaNit,

        @Email(message = "El correo no tiene un formato válido")
        @Size(max = 150, message = "El correo no puede superar 150 caracteres")
        String correo,

        @Size(max = 30, message = "El teléfono no puede superar 30 caracteres")
        String telefono,

        @Size(max = 255, message = "La dirección no puede superar 255 caracteres")
        String direccion,

        @Size(max = 100, message = "La ciudad no puede superar 100 caracteres")
        String ciudad,

        EstadoClienteCrm estado,

        @Size(max = 50, message = "La etiqueta no puede superar 50 caracteres")
        String etiqueta,

        @Size(max = 2000, message = "Las notas no pueden superar 2000 caracteres")
        String notas
) {
}
