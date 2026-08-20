package sie.siejuridicos.estadisticas;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.analitica.VisitaUnicaRepository;
import sie.siejuridicos.articulo.ArticuloRepository;
import sie.siejuridicos.articulo.EstadoArticulo;
import sie.siejuridicos.chatbot.ConversacionChatbotRepository;
import sie.siejuridicos.estadisticas.dto.EstadisticasResponse;
import sie.siejuridicos.marketing.SuscriptorMarketingRepository;
import sie.siejuridicos.solicitud.EstadoSolicitud;
import sie.siejuridicos.solicitud.OrigenSolicitud;
import sie.siejuridicos.solicitud.SolicitudRepository;
import sie.siejuridicos.testimonio.EstadoTestimonio;
import sie.siejuridicos.testimonio.TestimonioPublicoRepository;
import sie.siejuridicos.usuario.RolUsuario;
import sie.siejuridicos.usuario.UsuarioInternoRepository;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class EstadisticasService {

    private final SolicitudRepository solicitudRepository;
    private final TestimonioPublicoRepository testimonioPublicoRepository;
    private final ConversacionChatbotRepository conversacionChatbotRepository;
    private final ArticuloRepository articuloRepository;
    private final SuscriptorMarketingRepository suscriptorMarketingRepository;
    private final UsuarioInternoRepository usuarioInternoRepository;
    private final VisitaUnicaRepository visitaUnicaRepository;
    private final int limiteMensualChatbot;

    public EstadisticasService(SolicitudRepository solicitudRepository,
                                TestimonioPublicoRepository testimonioPublicoRepository,
                                ConversacionChatbotRepository conversacionChatbotRepository,
                                ArticuloRepository articuloRepository,
                                SuscriptorMarketingRepository suscriptorMarketingRepository,
                                UsuarioInternoRepository usuarioInternoRepository,
                                VisitaUnicaRepository visitaUnicaRepository,
                                @Value("${app.chatbot.limite-mensual}") int limiteMensualChatbot) {
        this.solicitudRepository = solicitudRepository;
        this.testimonioPublicoRepository = testimonioPublicoRepository;
        this.conversacionChatbotRepository = conversacionChatbotRepository;
        this.articuloRepository = articuloRepository;
        this.suscriptorMarketingRepository = suscriptorMarketingRepository;
        this.usuarioInternoRepository = usuarioInternoRepository;
        this.visitaUnicaRepository = visitaUnicaRepository;
        this.limiteMensualChatbot = limiteMensualChatbot;
    }

    @Transactional(readOnly = true)
    public EstadisticasResponse obtener() {
        Map<String, Long> solicitudesPorEstado = new LinkedHashMap<>();
        for (EstadoSolicitud estado : EstadoSolicitud.values()) {
            solicitudesPorEstado.put(estado.name(), solicitudRepository.countByEstado(estado));
        }

        Map<String, Long> solicitudesPorOrigen = new LinkedHashMap<>();
        for (OrigenSolicitud origen : OrigenSolicitud.values()) {
            solicitudesPorOrigen.put(origen.name(), solicitudRepository.countByOrigen(origen));
        }

        Map<String, Long> testimoniosPorEstado = new LinkedHashMap<>();
        for (EstadoTestimonio estado : EstadoTestimonio.values()) {
            testimoniosPorEstado.put(estado.name(), testimonioPublicoRepository.countByEstado(estado));
        }

        Map<String, Long> usuariosPorRol = new LinkedHashMap<>();
        for (RolUsuario rol : RolUsuario.values()) {
            usuariosPorRol.put(rol.name(), usuarioInternoRepository.countByRolAndActivoTrue(rol));
        }

        LocalDateTime ahora = LocalDateTime.now();

        return new EstadisticasResponse(
                solicitudesPorEstado,
                solicitudesPorOrigen,
                solicitudRepository.countByFechaCitaIsNotNull(),
                solicitudRepository.countByFechaCitaAfter(ahora),
                solicitudRepository.countByFechaCreacionAfter(ahora.minusDays(7)),
                testimoniosPorEstado,
                conversacionChatbotRepository.contarConversacionesMesActual(),
                limiteMensualChatbot,
                articuloRepository.countByEstado(EstadoArticulo.PUBLICADO),
                articuloRepository.countByEstado(EstadoArticulo.BORRADOR),
                suscriptorMarketingRepository.countByActivoTrue(),
                usuarioInternoRepository.countByActivoTrue(),
                usuariosPorRol,
                visitaUnicaRepository.contarVisitantesMesActual()
        );
    }
}
