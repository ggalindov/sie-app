package sie.siejuridicos.vigilancia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sie.siejuridicos.caso.Caso;
import sie.siejuridicos.caso.CasoRepository;
import sie.siejuridicos.caso.Cliente;
import sie.siejuridicos.common.exception.RecursoNoEncontradoException;
import sie.siejuridicos.correo.EmailService;
import sie.siejuridicos.registro.RegistroSistemaService;
import sie.siejuridicos.registro.TipoRegistroSistema;
import sie.siejuridicos.vigilancia.dto.EnviarDocumentoJudicialRequest;
import sie.siejuridicos.vigilancia.dto.EnvioDocumentoJudicialResponse;

import java.util.Optional;

// El ÚNICO punto de todo el módulo de vigilancia judicial que de verdad contacta a un
// cliente -- y solo cuando un abogado, desde el panel, elige explícitamente UN documento
// candidato ya mostrado (ver VigilanciaJudicialService/DocumentoRecienteResponse) y confirma
// el envío. Nunca automático, nunca disparado por VigilanciaExternaOrquestador.
//
// Respeta app.bloqueo-total-clientes igual que el resto del sistema, pero a diferencia de
// EmailService.enviarHtmlConAdjunto() (que devuelve true también cuando el bloqueo canceló
// el envío, a propósito, para que los procesos automáticos no lo traten como una falla) esta
// clase SÍ distingue "bloqueado" de "enviado" (ver EstadoEnvioDocumentoJudicial): esto es una
// acción manual de un humano, que necesita saber con certeza si de verdad salió algo.
@Service
public class EnvioDocumentoJudicialService {

    private static final Logger log = LoggerFactory.getLogger(EnvioDocumentoJudicialService.class);

    private final CasoRepository casoRepository;
    private final PublicacionesProcesalesClient publicacionesProcesalesClient;
    private final EmailService emailService;
    private final RegistroSistemaService registroSistemaService;
    private final boolean bloqueoTotalClientes;

    public EnvioDocumentoJudicialService(CasoRepository casoRepository,
                                          PublicacionesProcesalesClient publicacionesProcesalesClient,
                                          EmailService emailService,
                                          RegistroSistemaService registroSistemaService,
                                          @Value("${app.bloqueo-total-clientes:true}") boolean bloqueoTotalClientes) {
        this.casoRepository = casoRepository;
        this.publicacionesProcesalesClient = publicacionesProcesalesClient;
        this.emailService = emailService;
        this.registroSistemaService = registroSistemaService;
        this.bloqueoTotalClientes = bloqueoTotalClientes;
    }

    // Deliberadamente SIN @Transactional envolviendo este método -- mismo bug real ya
    // documentado en CasoService.consultar(): envolver un método que además llama a
    // registroSistemaService.registrar() (Propagation.REQUIRES_NEW) en una transacción
    // propia de este servicio termina en
    // "UnexpectedRollbackException: marked as rollback-only" contra Postgres real
    // (confirmado en vivo). buscarConClientePorId() ya trae el Cliente con JOIN FETCH, así
    // que no hace falta ninguna sesión abierta después para leer cliente.getCorreo().
    public EnvioDocumentoJudicialResponse enviar(Long casoId, EnviarDocumentoJudicialRequest request) {
        Caso caso = casoRepository.buscarConClientePorId(casoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Caso no encontrado"));
        Cliente cliente = caso.getCliente();
        String correo = cliente != null ? cliente.getCorreo() : null;
        if (correo == null || correo.isBlank()) {
            return EnvioDocumentoJudicialResponse.fallido(
                    "Este cliente no tiene correo capturado todavía -- no hay a dónde enviarlo.");
        }

        if (bloqueoTotalClientes) {
            log.warn("[SEGURIDAD ACTIVA] Envío de providencia judicial CANCELADO hacia el caso {} "
                    + "(app.bloqueo-total-clientes=true).", casoId);
            registroSistemaService.registrar(TipoRegistroSistema.ENVIO_DOCUMENTO_JUDICIAL,
                    "Envío de providencia bloqueado por app.bloqueo-total-clientes (caso %d)".formatted(casoId),
                    true);
            return EnvioDocumentoJudicialResponse.bloqueadoSeguridad();
        }

        Optional<byte[]> pdf = publicacionesProcesalesClient.descargarDocumento(request.urlDocumento());
        if (pdf.isEmpty()) {
            registroSistemaService.registrar(TipoRegistroSistema.ENVIO_DOCUMENTO_JUDICIAL,
                    "No se pudo descargar el documento de Publicaciones Procesales para el caso %d".formatted(casoId),
                    false);
            return EnvioDocumentoJudicialResponse.fallido(
                    "No se pudo descargar el documento desde Publicaciones Procesales -- intenta de nuevo en unos minutos.");
        }

        String nombreMostrado = caso.getNombreEnHoja() != null ? caso.getNombreEnHoja() : cliente.getNombre();
        boolean exito = emailService.enviarProvidenciaJudicial(
                nombreMostrado, correo, caso.getRadicadoId(), request.nombreActuacion(), request.nombreArchivo(), pdf.get());

        registroSistemaService.registrar(TipoRegistroSistema.ENVIO_DOCUMENTO_JUDICIAL,
                "Providencia judicial \"%s\" %s para el caso %d (radicado %s)".formatted(
                        request.nombreActuacion(), exito ? "enviada" : "fallida", casoId, caso.getRadicadoId()),
                exito);

        return exito
                ? EnvioDocumentoJudicialResponse.enviado()
                : EnvioDocumentoJudicialResponse.fallido("El envío de correo falló -- revisa el Registro del sistema para el detalle.");
    }
}
