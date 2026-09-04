package sie.siejuridicos.registro;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.registro.dto.RegistroSistemaResponse;

@Service
public class RegistroSistemaService {

    private static final Logger log = LoggerFactory.getLogger(RegistroSistemaService.class);
    private static final int TAMANO_PAGINA_MAXIMO = 100;

    private final RegistroSistemaRepository registroSistemaRepository;

    public RegistroSistemaService(RegistroSistemaRepository registroSistemaRepository) {
        this.registroSistemaRepository = registroSistemaRepository;
    }

    // REQUIRES_NEW a propósito: el registro tiene que quedar guardado sin importar qué le pase
    // después a la transacción de quien llama (ej. si sincronizarDesdeHoja() sigue trabajando
    // y algo más adelante hace rollback, el registro de "se sincronizó" ya quedó confirmado
    // aparte). Nunca deja que un fallo al escribir el log tumbe el proceso real que está
    // registrando -- un error acá se traga y se loguea, como cualquier otro efecto secundario
    // no esencial del sistema.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(TipoRegistroSistema tipo, String descripcion, String detalle, boolean exitoso) {
        try {
            RegistroSistema registro = new RegistroSistema();
            registro.setTipo(tipo);
            registro.setDescripcion(descripcion);
            registro.setDetalle(detalle);
            registro.setExitoso(exitoso);
            registroSistemaRepository.save(registro);
        } catch (Exception ex) {
            log.warn("No se pudo guardar el registro del sistema (tipo={}): {}", tipo, ex.getMessage());
        }
    }

    public void registrar(TipoRegistroSistema tipo, String descripcion, boolean exitoso) {
        registrar(tipo, descripcion, null, exitoso);
    }

    @Transactional(readOnly = true)
    public Page<RegistroSistemaResponse> listar(TipoRegistroSistema tipo, int pagina, int tamano) {
        int tamanoSeguro = Math.min(Math.max(tamano, 1), TAMANO_PAGINA_MAXIMO);
        PageRequest paginacion = PageRequest.of(Math.max(pagina, 0), tamanoSeguro);
        Page<RegistroSistema> resultado = tipo != null
                ? registroSistemaRepository.findByTipoOrderByFechaHoraDesc(tipo, paginacion)
                : registroSistemaRepository.findAllByOrderByFechaHoraDesc(paginacion);
        return resultado.map(RegistroSistemaResponse::desde);
    }
}
