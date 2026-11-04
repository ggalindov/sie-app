package sie.siejuridicos.vigilancia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sie.siejuridicos.caso.Caso;
import sie.siejuridicos.caso.CasoRepository;
import sie.siejuridicos.configuracion.ClaveConfiguracionEnvio;
import sie.siejuridicos.configuracion.ConfiguracionEnviosService;
import sie.siejuridicos.correo.EmailService;
import sie.siejuridicos.croma.CromaClient;
import sie.siejuridicos.croma.dto.CromaBuzonDespachoDto;
import sie.siejuridicos.documento.pdf.DocumentoPresentacionAutorizacionPdfGenerator;

import java.time.LocalDateTime;
import java.util.List;

// Pedido explícito del usuario (2026-11-04): "termina toda la configuracion para que caso
// que detecte que no tenga auto/boletin retorne peticion al buzon del juzgado, revisa como
// lo hace lupa para hacerlo igual". LUPA Jurídica, confirmado leyendo un ticket real de su
// propio módulo "Piezas Procesales", al no encontrar el documento oficial de una actuación
// por ningún canal automático, le escribe directamente al correo oficial del despacho
// (@cendoj.ramajudicial.gov.co) invocando el artículo 123 del CGP y la Ley 1712 de 2014.
// Este servicio replica exactamente ese flujo: lo llama BoletinJudicialCacheService.
// procesarActuacion() cuando NI el canal exacto de la CPNU NI el heurístico de Publicaciones
// Procesales encontraron el documento de una actuación que la propia Rama Judicial marcó
// con conDocumentos=true.
@Service
public class CorreoDespachoJudicialService {

    private static final Logger log = LoggerFactory.getLogger(CorreoDespachoJudicialService.class);

    // Cuánto tiempo se reutiliza el correo del despacho ya resuelto para un caso antes de
    // volver a gastar un crédito de Croma -- las bandejas oficiales de un despacho casi
    // nunca cambian, así que 90 días es un margen conservador sin desperdiciar cuota paga.
    private static final long DIAS_VIGENCIA_CACHE_CORREO_DESPACHO = 90;

    private final ConfiguracionEnviosService configuracionEnviosService;
    private final CromaClient cromaClient;
    private final EmailService emailService;
    private final DocumentoPresentacionAutorizacionPdfGenerator pdfGenerator;
    private final ActuacionJudicialRegistradaRepository actuacionRepository;
    private final CasoRepository casoRepository;
    private final String nombreFirma;
    private final String sitioWeb;
    private final String correoAdmin;
    private final String telefono;

    public CorreoDespachoJudicialService(ConfiguracionEnviosService configuracionEnviosService,
                                          CromaClient cromaClient,
                                          EmailService emailService,
                                          DocumentoPresentacionAutorizacionPdfGenerator pdfGenerator,
                                          ActuacionJudicialRegistradaRepository actuacionRepository,
                                          CasoRepository casoRepository,
                                          @Value("${app.firma.nombre}") String nombreFirma,
                                          @Value("${app.firma.sitio-web}") String sitioWeb,
                                          @Value("${app.correo.admin}") String correoAdmin,
                                          @Value("${app.firma.telefono:}") String telefono) {
        this.configuracionEnviosService = configuracionEnviosService;
        this.cromaClient = cromaClient;
        this.emailService = emailService;
        this.pdfGenerator = pdfGenerator;
        this.actuacionRepository = actuacionRepository;
        this.casoRepository = casoRepository;
        this.nombreFirma = nombreFirma;
        this.sitioWeb = sitioWeb;
        this.correoAdmin = correoAdmin;
        this.telefono = telefono;
    }

    // Único punto de entrada, llamado desde BoletinJudicialCacheService.procesarActuacion()
    // cuando ningún canal automático encontró el documento. Nunca lanza: un fallo aquí
    // jamás debe tumbar el ciclo de sincronización de boletines que lo invoca.
    public void intentarSolicitarAlDespacho(Caso caso, ActuacionJudicialRegistrada actuacion) {
        try {
            intentarSolicitarInterno(caso, actuacion);
        } catch (Exception ex) {
            log.warn("No se pudo completar la solicitud de documento al despacho para la actuación {}: {}",
                    actuacion.getId(), ex.getMessage());
        }
    }

    // Sin @Transactional a propósito: se llama por self-invocation desde
    // intentarSolicitarAlDespacho() en esta misma clase, donde Spring AOP nunca intercepta
    // la llamada (mismo gotcha ya documentado en BoletinJudicialCacheService.cachear()). No
    // hace falta de todas formas: cada escritura real (actuacionRepository.save/
    // casoRepository.save) ya va envuelta en su propia transacción por Spring Data JPA.
    private void intentarSolicitarInterno(Caso caso, ActuacionJudicialRegistrada actuacion) {
        if (!configuracionEnviosService.estaActivo(ClaveConfiguracionEnvio.CORREO_DESPACHO_JUDICIAL)) {
            return;
        }
        // Idempotencia real: nunca se le pide dos veces el mismo documento al despacho.
        if (actuacion.getCorreoDespachoSolicitadoEn() != null) {
            return;
        }
        // Solo se actúa en casos donde la firma tiene representación real de una parte --
        // es justo lo que sustenta invocar el artículo 123 del CGP ante el despacho (nunca
        // se escribe en nombre de un caso sin cliente/abogado asignado en el sistema).
        if (caso.getCliente() == null && caso.getAbogadoAsignado() == null) {
            return;
        }
        if (caso.getRadicadoId() == null || caso.getRadicadoId().isBlank()) {
            return;
        }

        String correoDespacho = resolverCorreoDespacho(caso);
        if (correoDespacho == null || correoDespacho.isBlank()) {
            // Sin buzón resuelto, nunca se adivina ni se envía nada.
            return;
        }

        String nombreCliente = nombreClienteParaRegistro(caso);
        String nombreApoderado = caso.getAbogadoAsignado() != null ? caso.getAbogadoAsignado().getNombre() : nombreFirma;

        byte[] pdf = pdfGenerator.generar(new DocumentoPresentacionAutorizacionPdfGenerator.Datos(
                nombreFirma,
                sitioWeb,
                correoAdmin,
                telefono,
                caso.getRadicadoId(),
                caso.getDespachoJudicial(),
                nombreCliente,
                actuacion.getNombreActuacion(),
                nombreApoderado,
                LocalDateTime.now()
        ));

        boolean enviado = emailService.enviarSolicitudDocumentoDespacho(
                nombreCliente, correoDespacho, caso.getRadicadoId(), caso.getDespachoJudicial(),
                actuacion.getNombreActuacion(), pdf);

        if (enviado) {
            actuacion.setCorreoDespachoSolicitadoEn(LocalDateTime.now());
            actuacionRepository.save(actuacion);
            log.info("Solicitud de documento enviada al despacho judicial ({}) para la actuación {} del caso {}.",
                    correoDespacho, actuacion.getId(), caso.getRadicadoId());
        }
    }

    // Reutiliza el correo ya cacheado en el propio Caso si todavía está vigente; solo si no
    // hay nada (o venció) consulta Croma -- consultarCorreosDespachoPorRadicado ya maneja
    // internamente créditos, failover y errores de red, nunca lanza.
    private String resolverCorreoDespacho(Caso caso) {
        if (caso.getCorreoOficialDespacho() != null && !caso.getCorreoOficialDespacho().isBlank()
                && caso.getCorreoOficialDespachoFecha() != null
                && caso.getCorreoOficialDespachoFecha().isAfter(
                        LocalDateTime.now().minusDays(DIAS_VIGENCIA_CACHE_CORREO_DESPACHO))) {
            return caso.getCorreoOficialDespacho();
        }

        List<CromaBuzonDespachoDto> buzones = cromaClient.consultarCorreosDespachoPorRadicado(
                caso.getRadicadoId(), caso.getDespachoJudicial());
        if (buzones.isEmpty()) {
            return null;
        }

        String correo = buzones.get(0).email();
        caso.setCorreoOficialDespacho(correo);
        caso.setCorreoOficialDespachoFecha(LocalDateTime.now());
        casoRepository.save(caso);
        return correo;
    }

    // Pedido explícito del usuario (2026-11-04): "mandame una prueba de como se enviaria a
    // juagdg12@gmail.com" -- genera el mismo PDF de presentación/autorización y el mismo
    // correo que intentarSolicitarInterno(), pero SIEMPRE al correoPrueba explícito (nunca al
    // buzón real del despacho) y vía EmailService.probarSolicitudDocumentoDespacho(), que
    // ignora a propósito el interruptor CORREO_DESPACHO_JUDICIAL (sigue apagado en el panel).
    // No marca correoDespachoSolicitadoEn ni toca el cache de correo del caso: es una
    // demostración, no debe interferir con la idempotencia real del flujo automático.
    public boolean probarSolicitudAlDespacho(Caso caso, String nombreActuacion, String correoPrueba) {
        String nombreCliente = nombreClienteParaRegistro(caso);
        String nombreApoderado = caso.getAbogadoAsignado() != null ? caso.getAbogadoAsignado().getNombre() : nombreFirma;

        byte[] pdf = pdfGenerator.generar(new DocumentoPresentacionAutorizacionPdfGenerator.Datos(
                nombreFirma,
                sitioWeb,
                correoAdmin,
                telefono,
                caso.getRadicadoId(),
                caso.getDespachoJudicial(),
                nombreCliente,
                nombreActuacion,
                nombreApoderado,
                LocalDateTime.now()
        ));

        return emailService.probarSolicitudDocumentoDespacho(
                nombreCliente, correoPrueba, caso.getRadicadoId(), caso.getDespachoJudicial(),
                nombreActuacion, pdf);
    }

    private String nombreClienteParaRegistro(Caso caso) {
        if (caso.getNombreEnHoja() != null && !caso.getNombreEnHoja().isBlank()) {
            return caso.getNombreEnHoja();
        }
        if (caso.getCliente() != null && caso.getCliente().getNombre() != null) {
            return caso.getCliente().getNombre();
        }
        return "Cliente";
    }
}
