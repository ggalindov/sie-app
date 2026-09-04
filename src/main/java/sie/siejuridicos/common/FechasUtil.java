package sie.siejuridicos.common;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

// Conversión compartida de LocalDateTime (las columnas fecha_* de todas las entidades,
// siempre escritas/leídas como UTC, ver hibernate.jdbc.time_zone=UTC en
// application.properties) a Instant para exponerlas en las respuestas de la API. Jackson
// serializa un LocalDateTime sin ninguna 'Z'/offset, así que un consumidor (el frontend, o
// cualquier otro cliente) no tiene forma de saber que ese valor es UTC y no ya la hora
// local de quien lo lee -- bug real de horario encontrado corrigiendo la hora de los
// artículos del blog, que aplica igual a cualquier otra fecha expuesta por la API. Instant
// sí serializa con 'Z' automáticamente.
public final class FechasUtil {

    private FechasUtil() {
    }

    public static Instant aInstanteUtc(LocalDateTime fecha) {
        return fecha == null ? null : fecha.toInstant(ZoneOffset.UTC);
    }
}
