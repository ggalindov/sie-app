package sie.siejuridicos.pqrs;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.common.exception.EntidadInvalidaException;
import sie.siejuridicos.common.exception.RecursoNoEncontradoException;
import sie.siejuridicos.correo.EmailService;
import sie.siejuridicos.pqrs.dto.CrearPqrsRequest;
import sie.siejuridicos.pqrs.dto.PqrsAdminResponse;
import sie.siejuridicos.pqrs.dto.PqrsConsultaResponse;
import sie.siejuridicos.pqrs.dto.PqrsCreadaResponse;
import sie.siejuridicos.usuario.UsuarioInterno;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class PqrsService {

    // 15 días calendario como referencia orientativa de respuesta (inspirado en el término
    // general del derecho de petición, Ley 1755 de 2015) -- se muestra como estimado, no
    // como una promesa de cumplimiento exacto en días hábiles: calcularlo preciso
    // requeriría un calendario de festivos colombianos que este sistema no mantiene, y
    // mostrar una fecha falsamente precisa sería peor que mostrar un estimado honesto (ver
    // CLAUDE.md 4.6: nunca se fabrican afirmaciones de cumplimiento que no se pueden
    // verificar como ciertas).
    private static final int DIAS_REFERENCIA_RESPUESTA = 15;
    private static final String ALFABETO_RADICADO = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LONGITUD_SUFIJO_RADICADO = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PqrsRepository pqrsRepository;
    private final EmailService emailService;

    public PqrsService(PqrsRepository pqrsRepository, EmailService emailService) {
        this.pqrsRepository = pqrsRepository;
        this.emailService = emailService;
    }

    @Transactional
    public PqrsCreadaResponse crear(CrearPqrsRequest request) {
        Pqrs pqrs = new Pqrs();
        pqrs.setRadicado(generarRadicadoUnico());
        pqrs.setTipo(request.tipo());
        pqrs.setNombre(request.nombre());
        pqrs.setCorreo(request.correo());
        pqrs.setTelefono(request.telefono());
        pqrs.setAsunto(request.asunto());
        pqrs.setDescripcion(request.descripcion());
        pqrs.setEstado(EstadoPqrs.RECIBIDO);
        pqrs.setFechaLimiteRespuesta(LocalDateTime.now().plusDays(DIAS_REFERENCIA_RESPUESTA));
        Pqrs guardada = pqrsRepository.save(pqrs);

        emailService.enviarRadicadoPqrs(guardada);
        emailService.enviarNotificacionAdminNuevaPqrs(guardada);

        return PqrsCreadaResponse.desde(guardada);
    }

    @Transactional(readOnly = true)
    public PqrsConsultaResponse consultarPorRadicado(String radicado) {
        Pqrs pqrs = pqrsRepository.findByRadicado(radicado.trim().toUpperCase())
                .orElseThrow(() -> new RecursoNoEncontradoException("No encontramos ninguna PQRS con ese radicado"));
        return PqrsConsultaResponse.desde(pqrs);
    }

    @Transactional(readOnly = true)
    public List<PqrsAdminResponse> listarTodas() {
        return pqrsRepository.findAllByOrderByFechaCreacionDesc().stream()
                .map(PqrsAdminResponse::desde)
                .toList();
    }

    @Transactional
    public PqrsAdminResponse cambiarEstado(Long id, EstadoPqrs nuevoEstado) {
        if (nuevoEstado == EstadoPqrs.RECIBIDO) {
            throw new EntidadInvalidaException("No se puede devolver una PQRS al estado recibido");
        }
        Pqrs pqrs = obtener(id);
        pqrs.setEstado(nuevoEstado);
        return PqrsAdminResponse.desde(pqrs);
    }

    @Transactional
    public PqrsAdminResponse responder(Long id, String respuesta, UsuarioInterno usuario) {
        Pqrs pqrs = obtener(id);
        pqrs.setRespuesta(respuesta);
        pqrs.setEstado(EstadoPqrs.RESUELTO);
        pqrs.setRespondidoPor(usuario.getNombre());
        pqrs.setFechaRespuesta(LocalDateTime.now());

        emailService.enviarRespuestaPqrs(pqrs);

        return PqrsAdminResponse.desde(pqrs);
    }

    private Pqrs obtener(Long id) {
        return pqrsRepository.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la PQRS con id " + id));
    }

    // "PQRS-" + 6 caracteres alfanuméricos en mayúscula, sin caracteres ambiguos (0/O, 1/I)
    // -- mismo criterio de legibilidad que un radicado que la persona tendrá que teclear a
    // mano al consultar su estado. Reintenta en el muy improbable caso de colisión.
    private String generarRadicadoUnico() {
        for (int intento = 0; intento < 10; intento++) {
            StringBuilder sufijo = new StringBuilder(LONGITUD_SUFIJO_RADICADO);
            for (int i = 0; i < LONGITUD_SUFIJO_RADICADO; i++) {
                sufijo.append(ALFABETO_RADICADO.charAt(RANDOM.nextInt(ALFABETO_RADICADO.length())));
            }
            String candidato = "PQRS-" + sufijo;
            if (!pqrsRepository.existsByRadicado(candidato)) {
                return candidato;
            }
        }
        throw new IllegalStateException("No se pudo generar un radicado único de PQRS tras varios intentos");
    }
}
