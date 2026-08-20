package sie.siejuridicos.faq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.common.exception.EntidadInvalidaException;
import sie.siejuridicos.common.exception.RecursoNoEncontradoException;
import sie.siejuridicos.faq.dto.PreguntaFrecuenteAdminResponse;
import sie.siejuridicos.faq.dto.PreguntaFrecuenteResponse;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class PreguntaFrecuenteService {

    private static final Logger log = LoggerFactory.getLogger(PreguntaFrecuenteService.class);

    // Por debajo del mínimo suele ser ruido ("hola", "gracias"); por encima del máximo ya
    // es un mensaje largo y particular (poco probable que se repita letra por letra), así
    // que no vale la pena rastrearlo para FAQ. Conteo por texto normalizado exacto, sin
    // IA ni embeddings (decisión explícita del usuario).
    private static final int LONGITUD_MINIMA = 5;
    private static final int LONGITUD_MAXIMA = 300;

    private final PreguntaFrecuenteRepository preguntaFrecuenteRepository;
    private final int umbralCandidata;

    public PreguntaFrecuenteService(PreguntaFrecuenteRepository preguntaFrecuenteRepository,
                                     @Value("${app.faq.umbral-candidata:3}") int umbralCandidata) {
        this.preguntaFrecuenteRepository = preguntaFrecuenteRepository;
        this.umbralCandidata = umbralCandidata;
    }

    // Nunca debe tumbar la conversación del chatbot: cualquier fallo aquí se registra en
    // logs y se ignora, igual que ChatbotService.registrarCostoReal.
    @Transactional
    public void registrarPregunta(String preguntaOriginal, String respuestaSugerida) {
        try {
            if (preguntaOriginal == null) {
                return;
            }
            String normalizada = normalizar(preguntaOriginal);
            if (normalizada.length() < LONGITUD_MINIMA || normalizada.length() > LONGITUD_MAXIMA) {
                return;
            }
            String ejemplo = recortar(preguntaOriginal.strip(), 500);
            String sugerida = respuestaSugerida == null ? null : recortar(respuestaSugerida, 2000);
            preguntaFrecuenteRepository.registrarOIncrementar(normalizada, ejemplo, sugerida);
        } catch (RuntimeException ex) {
            log.error("No se pudo registrar la pregunta candidata a FAQ: {}", ex.getMessage(), ex);
        }
    }

    @Transactional(readOnly = true)
    public List<PreguntaFrecuenteAdminResponse> listarCandidatas() {
        return preguntaFrecuenteRepository
                .findByEstadoAndConteoGreaterThanEqualOrderByConteoDesc(EstadoPreguntaFrecuente.CANDIDATA, umbralCandidata)
                .stream()
                .map(PreguntaFrecuenteAdminResponse::desde)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PreguntaFrecuenteResponse> listarAprobadas() {
        return preguntaFrecuenteRepository.findByEstadoOrderByConteoDesc(EstadoPreguntaFrecuente.APROBADA).stream()
                .map(PreguntaFrecuenteResponse::desde)
                .toList();
    }

    @Transactional
    public PreguntaFrecuenteAdminResponse moderar(Long id, EstadoPreguntaFrecuente nuevoEstado, String respuesta) {
        if (nuevoEstado == EstadoPreguntaFrecuente.CANDIDATA) {
            throw new EntidadInvalidaException("No se puede devolver una pregunta al estado candidata");
        }
        if (nuevoEstado == EstadoPreguntaFrecuente.APROBADA && (respuesta == null || respuesta.isBlank())) {
            throw new EntidadInvalidaException("Escribe una respuesta antes de aprobar la pregunta");
        }
        PreguntaFrecuente pregunta = preguntaFrecuenteRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la pregunta con id " + id));
        pregunta.setEstado(nuevoEstado);
        if (nuevoEstado == EstadoPreguntaFrecuente.APROBADA) {
            pregunta.setRespuestaFinal(respuesta.strip());
        }
        pregunta.setFechaModeracion(LocalDateTime.now());
        return PreguntaFrecuenteAdminResponse.desde(pregunta);
    }

    private String normalizar(String texto) {
        String sinAcentos = Normalizer.normalize(texto.toLowerCase(Locale.of("es", "CO")), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sinAcentos.replaceAll("[^a-z0-9\\s]", " ").trim().replaceAll("\\s+", " ");
    }

    private String recortar(String texto, int longitudMaxima) {
        return texto.length() > longitudMaxima ? texto.substring(0, longitudMaxima) : texto;
    }
}
