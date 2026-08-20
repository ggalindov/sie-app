package sie.siejuridicos.faq;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PreguntaFrecuenteRepository extends JpaRepository<PreguntaFrecuente, Long> {

    // Upsert atómico por pregunta_normalizada (UNIQUE, ver V16): si ya existe, incrementa
    // el conteo y refresca la respuesta sugerida SOLO si la pregunta sigue en estado
    // CANDIDATA (evita pisar la respuesta_sugerida de una fila ya aprobada/rechazada por
    // un abogado). RETURNING id sigue el mismo patrón que
    // SuscriptorMarketingRepository.suscribirSiNoExiste para no necesitar @Modifying.
    @Query(value = "INSERT INTO preguntas_frecuentes (pregunta_normalizada, pregunta_ejemplo, respuesta_sugerida) " +
            "VALUES (:normalizada, :ejemplo, :respuestaSugerida) " +
            "ON CONFLICT (pregunta_normalizada) DO UPDATE SET " +
            "conteo = preguntas_frecuentes.conteo + 1, " +
            "fecha_actualizacion = now(), " +
            "respuesta_sugerida = CASE WHEN preguntas_frecuentes.estado = 'CANDIDATA' " +
            "THEN EXCLUDED.respuesta_sugerida ELSE preguntas_frecuentes.respuesta_sugerida END " +
            "RETURNING id", nativeQuery = true)
    List<Long> registrarOIncrementar(@Param("normalizada") String normalizada,
                                      @Param("ejemplo") String ejemplo,
                                      @Param("respuestaSugerida") String respuestaSugerida);

    List<PreguntaFrecuente> findByEstadoAndConteoGreaterThanEqualOrderByConteoDesc(
            EstadoPreguntaFrecuente estado, Integer conteoMinimo);

    List<PreguntaFrecuente> findByEstadoOrderByConteoDesc(EstadoPreguntaFrecuente estado);
}
