package sie.siejuridicos.configuracion;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.registro.RegistroSistemaService;
import sie.siejuridicos.registro.TipoRegistroSistema;
import sie.siejuridicos.usuario.UsuarioInterno;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Cache en memoria de ConfiguracionEnvio -- EmailService/WhatsAppService consultan
// estaActivo() en CADA envío (incluyendo lotes de cientos, ver CasoService.
// enviarCorreosPendientes), así que no puede costar una consulta a la base de datos por
// cada uno. Se recarga sola al arrancar y cada vez que el admin cambia algo desde el panel
// -- nunca queda desactualizada más de lo que tarda esa misma petición PATCH.
@Service
public class ConfiguracionEnviosService {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracionEnviosService.class);

    private final ConfiguracionEnvioRepository repository;
    private final RegistroSistemaService registroSistemaService;
    private final Map<ClaveConfiguracionEnvio, Boolean> cache = new ConcurrentHashMap<>();

    public ConfiguracionEnviosService(ConfiguracionEnvioRepository repository,
                                       RegistroSistemaService registroSistemaService) {
        this.repository = repository;
        this.registroSistemaService = registroSistemaService;
    }

    @PostConstruct
    void cargar() {
        for (ClaveConfiguracionEnvio clave : ClaveConfiguracionEnvio.values()) {
            cache.put(clave, true);
        }
        for (ConfiguracionEnvio fila : repository.findAll()) {
            cache.put(fila.getClave(), fila.isActivo());
        }
    }

    // Usado directamente por EmailService/WhatsAppService antes de cada envío automático
    // (nunca para los que el propio abogado dispara a mano con un clic explícito, ver cada
    // llamador para el criterio exacto). true por defecto si, por lo que sea, la clave
    // todavía no tiene fila -- "activo" es el estado normal, apagar es la excepción
    // deliberada del admin.
    public boolean estaActivo(ClaveConfiguracionEnvio clave) {
        return cache.getOrDefault(clave, true);
    }

    public List<ConfiguracionEnvioResponse> listar() {
        return Arrays.stream(ClaveConfiguracionEnvio.values())
                .map(clave -> new ConfiguracionEnvioResponse(clave.name(), clave.getNombreVisible(), estaActivo(clave)))
                .toList();
    }

    @Transactional
    public void actualizar(ClaveConfiguracionEnvio clave, boolean activo, UsuarioInterno actor) {
        ConfiguracionEnvio fila = repository.findById(clave).orElseGet(() -> {
            ConfiguracionEnvio nueva = new ConfiguracionEnvio();
            nueva.setClave(clave);
            return nueva;
        });
        fila.setActivo(activo);
        repository.save(fila);
        cache.put(clave, activo);

        registroSistemaService.registrar(
                TipoRegistroSistema.CONFIGURACION_ENVIOS,
                "'%s' %s el envío automático de '%s'"
                        .formatted(actor.getNombre(), activo ? "activó" : "desactivó", clave.getNombreVisible()),
                true);
        log.info("Configuración de envíos: {} {}", clave, activo ? "ACTIVADO" : "DESACTIVADO");
    }
}
