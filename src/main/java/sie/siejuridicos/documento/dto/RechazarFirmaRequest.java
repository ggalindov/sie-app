package sie.siejuridicos.documento.dto;

import jakarta.validation.constraints.Size;

// motivo es opcional (una parte puede simplemente negarse a dar explicación), pero si
// llega se limita a un tamaño razonable -- igual que el resto de campos de texto libre
// del motor de firma.
public record RechazarFirmaRequest(@Size(max = 2000, message = "El motivo no puede superar 2000 caracteres") String motivo) {
}
