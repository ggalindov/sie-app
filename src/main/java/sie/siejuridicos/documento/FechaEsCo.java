package sie.siejuridicos.documento;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

// Formato largo en español ("10 de octubre de 2026") para fechas dentro del motor de firma
// (certificado de evidencia, página de firmas, correos) -- evita repetir el mismo patrón de
// DateTimeFormatter en cada clase.
public final class FechaEsCo {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.of("es", "CO"));

    private FechaEsCo() {
    }

    public static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");

    public static String largo(LocalDateTime fecha) {
        return fecha == null ? null : FORMATTER.format(fecha);
    }
}
