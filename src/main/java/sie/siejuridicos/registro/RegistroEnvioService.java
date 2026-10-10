package sie.siejuridicos.registro;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.registro.dto.RegistroEnvioResponse;

@Service
public class RegistroEnvioService {

    private static final Logger log = LoggerFactory.getLogger(RegistroEnvioService.class);
    private static final int TAMANO_PAGINA_MAXIMO = 100;

    private final RegistroEnvioRepository registroEnvioRepository;

    public RegistroEnvioService(RegistroEnvioRepository registroEnvioRepository) {
        this.registroEnvioRepository = registroEnvioRepository;
    }

    // REQUIRES_NEW a propósito, mismo criterio que RegistroSistemaService.registrar(): el
    // registro del envío tiene que quedar guardado sin importar qué le pase después a la
    // transacción de quien llama, y un fallo al escribir el log nunca debe tumbar el envío
    // real que está registrando (se traga la excepción y se loguea aparte).
    //
    // Se ignora silenciosamente si no hay destinatario real (ej. WhatsApp no configurado
    // antes de siquiera intentar construir un número): registrar "se intentó enviar a nada"
    // no aporta nada a la bitácora.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(CanalEnvio canal, TipoEnvio tipo, String destinatarioNombre,
                           String destinatarioContacto, String resumen, boolean exitoso) {
        if (destinatarioContacto == null || destinatarioContacto.isBlank()) {
            return;
        }
        try {
            RegistroEnvio registro = new RegistroEnvio();
            registro.setCanal(canal);
            registro.setTipo(tipo);
            registro.setDestinatarioNombre(destinatarioNombre);
            registro.setDestinatarioContacto(destinatarioContacto);
            registro.setResumen(resumen == null || resumen.isBlank() ? tipo.getNombreVisible() : resumen);
            registro.setExitoso(exitoso);
            registroEnvioRepository.save(registro);
        } catch (Exception ex) {
            log.warn("No se pudo guardar el registro de envío (canal={}, tipo={}): {}", canal, tipo, ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<RegistroEnvioResponse> listar(CanalEnvio canal, TipoEnvio tipo, int pagina, int tamano) {
        int tamanoSeguro = Math.min(Math.max(tamano, 1), TAMANO_PAGINA_MAXIMO);
        PageRequest paginacion = PageRequest.of(Math.max(pagina, 0), tamanoSeguro);
        Page<RegistroEnvio> resultado;
        if (canal != null && tipo != null) {
            resultado = registroEnvioRepository.findByCanalAndTipoOrderByFechaHoraDesc(canal, tipo, paginacion);
        } else if (canal != null) {
            resultado = registroEnvioRepository.findByCanalOrderByFechaHoraDesc(canal, paginacion);
        } else if (tipo != null) {
            resultado = registroEnvioRepository.findByTipoOrderByFechaHoraDesc(tipo, paginacion);
        } else {
            resultado = registroEnvioRepository.findAllByOrderByFechaHoraDesc(paginacion);
        }
        return resultado.map(RegistroEnvioResponse::desde);
    }
}
