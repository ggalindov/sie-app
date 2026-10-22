package sie.siejuridicos.documento.dto;

import sie.siejuridicos.documento.FirmaAplicacionClave;

import java.time.LocalDateTime;

// Historial de rotación de la llave de firma de aplicación (Ed25519) -- nunca expone la privada.
public record FirmaAplicacionClaveDto(
        String kid,
        String algoritmo,
        boolean activa,
        LocalDateTime creadaEn,
        LocalDateTime rotadaEn
) {
    public static FirmaAplicacionClaveDto from(FirmaAplicacionClave clave) {
        return new FirmaAplicacionClaveDto(clave.getKid(), clave.getAlgoritmo(), clave.isActiva(), clave.getCreadaEn(), clave.getRotadaEn());
    }
}
