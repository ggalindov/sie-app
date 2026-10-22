package sie.siejuridicos.documento.dto;

import java.math.BigDecimal;

// rechazada=true cuando el navegador del firmante denegó el permiso de geolocalización -- el
// flujo de firma continúa igual (no bloqueante), pero el rechazo mismo queda como evidencia.
// En ese caso lat/lon/precisionM llegan null.
public record GeolocalizacionRequest(BigDecimal lat, BigDecimal lon, BigDecimal precisionM, boolean rechazada) {
}
