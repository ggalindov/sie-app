package sie.siejuridicos.documento.dto;

// A diferencia de ConsentimientoRequest, el texto NO lo manda el cliente: es fijo (ver
// sie.siejuridicos.documento.TextosLegalesFirma), así el firmante nunca podría "aceptar"
// un texto distinto al que el servidor realmente exige -- el request solo confirma el
// click explícito del checkbox.
public record AdvertenciaSuplantacionRequest(boolean aceptar) {
}
