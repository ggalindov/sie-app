package sie.siejuridicos.caso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.caso.dto.ActualizarCasoRequest;
import sie.siejuridicos.caso.dto.CasoAdminResponse;
import sie.siejuridicos.caso.dto.CasoConsultaResponse;
import sie.siejuridicos.caso.dto.CrearCasoRequest;
import sie.siejuridicos.caso.dto.ResumenEnvioCorreos;
import sie.siejuridicos.caso.dto.ResumenReporteSemanal;
import sie.siejuridicos.caso.dto.ResumenSincronizacion;
import sie.siejuridicos.common.cifrado.CifradoService;
import sie.siejuridicos.common.exception.ConflictoNegocioException;
import sie.siejuridicos.common.exception.RecursoNoEncontradoException;
import sie.siejuridicos.common.limite.LimiteEnvioMasivoService;
import sie.siejuridicos.correo.EmailService;
import sie.siejuridicos.hojacalculo.HojaCalculoService;
import sie.siejuridicos.hojacalculo.dto.FilaCasoHoja;
import sie.siejuridicos.hojacalculo.dto.FilaSincronizacionHoja;
import sie.siejuridicos.hojacalculo.dto.ResultadoSincronizacionHoja;
import sie.siejuridicos.registro.RegistroSistemaService;
import sie.siejuridicos.registro.TipoRegistroSistema;
import sie.siejuridicos.usuario.UsuarioInterno;
import sie.siejuridicos.usuario.RolUsuario;
import sie.siejuridicos.usuario.UsuarioInternoRepository;
import sie.siejuridicos.vigilancia.ActuacionJudicialRegistradaRepository;
import sie.siejuridicos.vigilancia.BoletinJudicialCacheRepository;
import sie.siejuridicos.vigilancia.EstadoProcesoCandidato;
import sie.siejuridicos.vigilancia.ProcesoCandidatoDetectado;
import sie.siejuridicos.vigilancia.ProcesoCandidatoDetectadoRepository;
import sie.siejuridicos.vigilancia.SeguimientoVistoUsuarioRepository;
import sie.siejuridicos.vigilancia.TerminoProcesalService;
import sie.siejuridicos.whatsapp.WhatsAppService;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class CasoService {

    private static final Logger log = LoggerFactory.getLogger(CasoService.class);

    // Pausa entre cada envío del lote masivo de notificaciones (ver enviarCorreosPendientes):
    // el incidente real que motivó esto fue mandar ~200 correos casi en simultáneo (sin
    // ninguna pausa), lo que Gmail interpretó como comportamiento de abuso y bloqueó la
    // cuenta a mitad del lote (89 de 208 fallaron esa vez). 1.5s por envío es conservador para
    // una cuenta de Gmail normal (no Workspace) -- un lote de 200 tarda ~5 minutos, pero llega.
    private static final long PAUSA_ENTRE_ENVIOS_MS = 1500;
    // Ante un fallo transitorio (SMTP/Meta), cuánto esperar antes del único reintento.
    private static final long PAUSA_REINTENTO_MS = 4000;
    // Mismo límite que la columna radicado_id de la tabla casos (ver migración V1) -- un
    // radicado real de la Rama Judicial tiene 23 dígitos, así que cualquier valor más largo
    // es dato corrupto de la hoja, nunca un radicado legítimo.
    private static final int LONGITUD_MAXIMA_RADICADO = 50;

    private final CasoRepository casoRepository;
    private final ClienteRepository clienteRepository;
    private final EmailService emailService;
    private final WhatsAppService whatsAppService;
    private final HojaCalculoService hojaCalculoService;
    private final CifradoService cifradoService;
    private final RegistroSistemaService registroSistemaService;
    private final LimiteEnvioMasivoService limiteEnvioMasivoService;
    private final ActuacionJudicialRegistradaRepository actuacionJudicialRegistradaRepository;
    private final SeguimientoVistoUsuarioRepository seguimientoVistoUsuarioRepository;
    private final UsuarioInternoRepository usuarioInternoRepository;
    private final TerminoProcesalService terminoProcesalService;
    private final BoletinJudicialCacheRepository boletinJudicialCacheRepository;
    private final ProcesoCandidatoDetectadoRepository procesoCandidatoDetectadoRepository;
    private final CasoCambioDetectadoRepository casoCambioDetectadoRepository;

    @org.springframework.beans.factory.annotation.Autowired
    public CasoService(CasoRepository casoRepository,
                        ClienteRepository clienteRepository,
                        EmailService emailService,
                        WhatsAppService whatsAppService,
                        HojaCalculoService hojaCalculoService,
                        CifradoService cifradoService,
                        RegistroSistemaService registroSistemaService,
                        LimiteEnvioMasivoService limiteEnvioMasivoService,
                        ActuacionJudicialRegistradaRepository actuacionJudicialRegistradaRepository,
                        SeguimientoVistoUsuarioRepository seguimientoVistoUsuarioRepository,
                        UsuarioInternoRepository usuarioInternoRepository,
                        TerminoProcesalService terminoProcesalService,
                        BoletinJudicialCacheRepository boletinJudicialCacheRepository,
                        ProcesoCandidatoDetectadoRepository procesoCandidatoDetectadoRepository,
                        CasoCambioDetectadoRepository casoCambioDetectadoRepository) {
        this.casoRepository = casoRepository;
        this.clienteRepository = clienteRepository;
        this.emailService = emailService;
        this.whatsAppService = whatsAppService;
        this.hojaCalculoService = hojaCalculoService;
        this.cifradoService = cifradoService;
        this.registroSistemaService = registroSistemaService;
        this.limiteEnvioMasivoService = limiteEnvioMasivoService;
        this.actuacionJudicialRegistradaRepository = actuacionJudicialRegistradaRepository;
        this.seguimientoVistoUsuarioRepository = seguimientoVistoUsuarioRepository;
        this.usuarioInternoRepository = usuarioInternoRepository;
        this.terminoProcesalService = terminoProcesalService;
        this.boletinJudicialCacheRepository = boletinJudicialCacheRepository;
        this.procesoCandidatoDetectadoRepository = procesoCandidatoDetectadoRepository;
        this.casoCambioDetectadoRepository = casoCambioDetectadoRepository;
    }

    // Constructor de compatibilidad para suites de prueba unitarias
    public CasoService(CasoRepository casoRepository,
                        ClienteRepository clienteRepository,
                        EmailService emailService,
                        WhatsAppService whatsAppService,
                        HojaCalculoService hojaCalculoService,
                        CifradoService cifradoService,
                        RegistroSistemaService registroSistemaService,
                        LimiteEnvioMasivoService limiteEnvioMasivoService) {
        this(casoRepository, clienteRepository, emailService, whatsAppService,
                hojaCalculoService, cifradoService, registroSistemaService,
                limiteEnvioMasivoService, null, null, null, null, null, null, null);
    }

    // Respaldo manual: ya no es el flujo principal (ver sincronizarDesdeHoja()), pero se
    // mantiene para un caso puntual que por lo que sea no está aún en ninguna de las hojas
    // de la firma. Fuente MANUAL: nunca tiene una pestaña que consultar en vivo (ver
    // consultar()), se queda siempre en "aún no hay actualizaciones".
    @Transactional
    public CasoAdminResponse crear(CrearCasoRequest request, UsuarioInterno actor) {
        String radicadoId = request.radicadoId().strip();
        if (casoRepository.existsByRadicadoId(radicadoId)) {
            throw new ConflictoNegocioException("Ya existe un caso registrado con ese radicado.");
        }

        // correo está cifrado con IV aleatorio (ver Cliente): no se puede comparar por
        // igualdad directo en SQL, por eso se busca por el índice ciego (determinista) en
        // vez de por el propio correo.
        String correoHash = cifradoService.indiceCiego(request.correoCliente());
        Cliente cliente = clienteRepository.findByCorreoHash(correoHash)
                .map(existente -> {
                    existente.setNombre(request.nombreCliente());
                    existente.setTelefono(request.telefonoCliente());
                    return existente;
                })
                .orElseGet(() -> {
                    Cliente nuevo = new Cliente();
                    nuevo.setNombre(request.nombreCliente());
                    nuevo.setCorreo(request.correoCliente());
                    nuevo.setCorreoHash(correoHash);
                    nuevo.setTelefono(request.telefonoCliente());
                    return clienteRepository.save(nuevo);
                });

        Caso caso = new Caso();
        caso.setCliente(cliente);
        caso.setFuente(FuenteCaso.MANUAL);
        caso.setRadicadoId(radicadoId);
        caso.setNotasInternas(request.notasInternas());
        if (request.esPrivado()) {
            caso.setEsPrivadoCifrado(true);
            caso.setMotivoConfidencialidad(request.motivoConfidencialidad());
            // Pedido explícito del usuario (2026-11-05): un ABOGADO que crea un caso privado
            // lo crea en SU PROPIA bóveda personal (nunca en la de Gerencia), con el mismo
            // tope de MAXIMO_CASOS_PRIVADOS_POR_ABOGADO que aplica al desclasificar/clasificar
            // uno ya existente (ver marcarPrivado()).
            if (actor != null && actor.getRol() != RolUsuario.ADMIN_GENERAL) {
                long actuales = casoRepository.countByEsPrivadoCifradoTrueAndPropietarioPrivado_Id(actor.getId());
                if (actuales >= MAXIMO_CASOS_PRIVADOS_POR_ABOGADO) {
                    throw new ConflictoNegocioException(
                            "Ya tienes %d casos en tu bóveda privada (el máximo permitido). Reabre o desclasifica uno antes de agregar otro."
                                    .formatted(MAXIMO_CASOS_PRIVADOS_POR_ABOGADO));
                }
                caso.setPropietarioPrivado(actor);
            }
        }

        Caso guardado = casoRepository.save(caso);
        registrarSiRadicadoInvalido(guardado);

        // Variantes SÍNCRONAS a propósito -- bug real encontrado en auditoría (reportado por
        // el usuario: "no me llegó el WhatsApp al usuario de prueba" mientras el panel
        // mostraba "enviado"): antes se llamaba a las versiones @Async (dispara y olvida) y
        // se marcaba correoEnviado/whatsappEnviado en true de inmediato, sin esperar ninguna
        // confirmación real -- así Meta respondiera un error (plantilla no aprobada,
        // token vencido, número inválido) o que el SMTP fallara, el panel igual mostraba
        // "enviado". Un solo correo + un solo WhatsApp (nunca un lote) es una espera aceptable
        // dentro de la misma respuesta HTTP de "crear caso" -- mismo criterio de veracidad que
        // ya usa correctamente enviarCorreosPendientes() para el envío masivo.
        boolean correoExitoso = emailService.enviarCodigoCasoSincrono(
                cliente.getNombre(), cliente.getCorreo(), guardado.getRadicadoId());
        guardado.setCorreoEnviado(correoExitoso);
        if (whatsAppService.isConfigurado() && cliente.getTelefono() != null) {
            boolean whatsappExitoso = whatsAppService.enviarCodigoCasoSincrono(
                    cliente.getNombre(), cliente.getTelefono(), guardado.getRadicadoId());
            guardado.setWhatsappEnviado(whatsappExitoso);
        }

        // Caso recién creado: nunca puede tener ya una actuación "sin revisar" pendiente, ni
        // ninguna actuación detectada todavía, ni boletín en caché.
        return CasoAdminResponse.desde(guardado, false, null, null, null);
    }

    // Pedido explícito del usuario (2026-11-01): "le sale una alerta de que debe ser
    // corregido" + "el sistema debe reflejar que fallos hay" -- se llama justo después de
    // CUALQUIER punto donde se asigna/cambia un radicado (creación manual, edición desde el
    // panel, sincronización desde la hoja), nunca bloquea el guardado (ver Caso.
    // setRadicadoId()/RadicadoUtil: hay jurisdicciones reales sin radicado CPNU). Solo deja
    // trazabilidad en /admin/registro de qué caso puntual quedó así, para que un admin lo
    // note sin tener que revisar los logs del servidor.
    private void registrarSiRadicadoInvalido(Caso caso) {
        if (caso.getRadicadoId() == null || !caso.isRadicadoFormatoInvalido()) {
            return;
        }
        registroSistemaService.registrar(
                TipoRegistroSistema.RADICADO_FORMATO_INVALIDO,
                "Caso #%d (%s) quedó con el radicado '%s', que no tiene el formato de la Rama Judicial (23 dígitos)"
                        .formatted(caso.getId(), caso.getFuente().getNombreVisible(), caso.getRadicadoId()),
                "Si es un caso de otra jurisdicción (comisaría de familia, inspección de policía), puede ignorarse. "
                        + "Si esperabas seguimiento automático 24/7 para este caso, revisa el radicado.",
                false);
    }

    // Botón "Crear y adjuntar automáticamente en SIE" de /admin/procesos-detectados (pedido
    // explícito del usuario: "implementa todas las mejoras identificadas", comparando contra
    // Lupa Jurídica -- y luego, sobre la primera versión de esta pantalla: "deja dos botones,
    // CREAR Y ADJUNTAR AUTOMATICAMENTE EN SIE, NO (ES OTRA PERSONA)"). A diferencia de crear()
    // (que pide escribir nombre/correo/radicado a mano), aquí el Cliente YA es uno real del
    // sistema (el candidato se detectó buscando SU nombre) y los datos del proceso ya los
    // trajo el CPNU durante la detección -- no hay nada que volver a preguntar.
    //
    // Deliberadamente NO manda el correo/WhatsApp de "código de tu caso" que sí manda crear():
    // ese mensaje nació para cuando un cliente llena el formulario de contacto esperando una
    // confirmación inmediata. Aquí el cliente no hizo ninguna gestión en este momento -- es la
    // firma la que encontró un proceso suyo por su cuenta -- así que avisarle de buenas a
    // primeras sería una comunicación que el abogado no decidió a propósito. El seguimiento
    // 24/7 (ver SeguimientoJudicialService) sí vigila el caso desde que se crea, igual que
    // cualquier otro.
    @Transactional
    public CasoAdminResponse crearDesdeProcesoCandidato(Long candidatoId, UsuarioInterno actor) {
        ProcesoCandidatoDetectado candidato = procesoCandidatoDetectadoRepository.findById(candidatoId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Candidato no encontrado"));
        if (candidato.getEstado() != EstadoProcesoCandidato.PENDIENTE) {
            throw new ConflictoNegocioException("Este candidato ya fue revisado antes.");
        }

        String radicadoId = candidato.getLlaveProceso();
        if (casoRepository.existsByRadicadoId(radicadoId)) {
            throw new ConflictoNegocioException(
                    "Ya existe un caso registrado con ese radicado -- probablemente ya se había creado por otro medio.");
        }

        Caso caso = new Caso();
        caso.setCliente(candidato.getCliente());
        caso.setFuente(FuenteCaso.MANUAL);
        caso.setRadicadoId(radicadoId);
        // Lo que el CPNU ya trajo durante la detección por nombre -- el próximo ciclo del
        // seguimiento 24/7 completa el resto (ponente, tipo/clase/subclase de proceso,
        // ubicación del expediente, actuaciones), exactamente igual que con cualquier otro
        // caso nuevo (ver SeguimientoJudicialService.sincronizarCaso).
        caso.setSujetosProcesales(candidato.getSujetosProcesales());
        caso.setDespachoJudicial(candidato.getDespacho());
        caso.setDepartamentoJudicial(candidato.getDepartamento());
        caso.setFechaRadicacionProceso(candidato.getFechaProceso());
        caso.setNotasInternas("Creado automáticamente el %s desde \"Procesos nuevos detectados\": el sistema encontró este proceso buscando el nombre del cliente en la Rama Judicial y %s confirmó que es real."
                .formatted(LocalDate.now(), actor.getNombre()));

        Caso guardado = casoRepository.save(caso);

        candidato.setEstado(EstadoProcesoCandidato.YA_ES_CASO);
        procesoCandidatoDetectadoRepository.save(candidato);

        registroSistemaService.registrar(
                TipoRegistroSistema.CASO_CREADO_DESDE_DETECCION,
                "'%s' creó el caso #%d (radicado %s) automáticamente desde un proceso detectado para el cliente '%s'".formatted(
                        actor.getNombre(), guardado.getId(), radicadoId, candidato.getCliente().getNombre()),
                true);

        // Igual que un caso recién creado por cualquier otro medio: sin actuación "sin
        // revisar" ni boletín en caché todavía.
        return CasoAdminResponse.desde(guardado, false, null, null, null);
    }

    // Edición manual de un caso ya existente (pedido explícito del usuario: "una variable
    // para editar los casos, la idea es desligarnos del excel... un boton para editar caso
    // para administrador unicamente y asignacion de caso por abogado"). A diferencia de
    // sincronizarDesdeHoja() (que solo puede traer lo que ya está en la hoja de la firma),
    // esto permite corregir o completar datos directamente, sin depender de que el Google
    // Sheets los tenga -- el primer paso real de dejar de ser 100% dependientes de la hoja.
    //
    // numeroCaso NO se edita aquí a propósito (ver ActualizarCasoRequest). radicadoId sí se
    // valida contra duplicados, igual que en la sincronización.
    //
    // correo/telefono viven en Cliente, que puede estar COMPARTIDO entre varios casos (ver
    // CasoRepository.countByCliente) -- si el cliente de este caso tiene más de uno, NO se
    // tocan esos dos campos (se reportan como "no actualizados" en el mensaje) para no pisar
    // silenciosamente el contacto de otro caso que comparte el mismo cliente; nombreEnHoja,
    // radicadoId, notasInternas y abogadoAsignado son siempre por-caso, sin ese riesgo.
    @Transactional
    public CasoAdminResponse actualizar(Long id, ActualizarCasoRequest request, UsuarioInterno actor) {
        Caso caso = casoRepository.buscarConClienteYAbogadoPorId(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Caso no encontrado"));
        // Hueco real encontrado (2026-11-05): al abrir la edición de casos a ABOGADO además de
        // ADMIN_GENERAL (pedido explícito del usuario, misma sesión), este endpoint quedó sin
        // ninguna verificación de privacidad -- cualquier abogado podía editar un caso privado
        // de Gerencia o de OTRO abogado con solo conocer su id. "nunca se puede cruzar la
        // informacion" -- se cierra aquí.
        verificarAccesoPrivado(caso, actor);

        String nombreNuevo = request.nombreEnHoja() != null ? request.nombreEnHoja().strip() : null;
        if (nombreNuevo != null && !nombreNuevo.isBlank()) {
            caso.setNombreEnHoja(nombreNuevo);
        }

        String radicadoNuevo = request.radicadoId() != null ? request.radicadoId().strip() : null;
        if (radicadoNuevo != null && radicadoNuevo.isBlank()) {
            radicadoNuevo = null;
        }
        if (radicadoNuevo != null && radicadoNuevo.length() > LONGITUD_MAXIMA_RADICADO) {
            throw new ConflictoNegocioException(
                    "El radicado no puede tener más de " + LONGITUD_MAXIMA_RADICADO + " caracteres.");
        }
        if (!java.util.Objects.equals(radicadoNuevo, caso.getRadicadoId())) {
            if (radicadoNuevo != null) {
                boolean yaUsado = casoRepository.findByRadicadoId(radicadoNuevo)
                        .filter(otro -> !otro.getId().equals(caso.getId()))
                        .isPresent();
                if (yaUsado) {
                    throw new ConflictoNegocioException("Ese radicado ya está asignado a otro caso.");
                }
            }
            boolean eraReasignacion = caso.getRadicadoId() != null && radicadoNuevo != null;
            caso.setRadicadoId(radicadoNuevo);
            registrarSiRadicadoInvalido(caso);
            if (eraReasignacion) {
                caso.setCorreoEnviado(false);
                caso.setWhatsappEnviado(false);
            }
        }

        caso.setNotasInternas(request.notasInternas() != null && !request.notasInternas().isBlank()
                ? request.notasInternas().strip() : null);

        if (request.abogadoAsignadoId() == null) {
            caso.setAbogadoAsignado(null);
        } else if (caso.getAbogadoAsignado() == null || !caso.getAbogadoAsignado().getId().equals(request.abogadoAsignadoId())) {
            UsuarioInterno abogado = usuarioInternoRepository.findById(request.abogadoAsignadoId())
                    .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));
            caso.setAbogadoAsignado(abogado);
            // Gap real: si este caso ya tenía términos procesales detectados ANTES de tener
            // abogado (ver TerminoProcesalService.procesarSiAplica -- nunca reprocesa una
            // actuación ya registrada), se quedarían para siempre sin tarea ni alerta
            // activa. Al asignar abogado ahora, se les crea la tarea retroactivamente.
            terminoProcesalService.asignarTareasPendientes(caso, abogado);
        }

        if (request.esPrivadoCifrado() != null) {
            caso.setEsPrivadoCifrado(request.esPrivadoCifrado());
        }
        if (request.motivoConfidencialidad() != null) {
            caso.setMotivoConfidencialidad(request.motivoConfidencialidad().isBlank() ? null : request.motivoConfidencialidad().strip());
        }

        boolean contactoActualizado = actualizarContactoCliente(caso, request.correoCliente(), request.telefonoCliente());

        casoRepository.save(caso);

        registroSistemaService.registrar(
                TipoRegistroSistema.EDICION_CASO,
                "'%s' editó el caso #%d (%s)%s".formatted(
                        actor.getNombre(), caso.getId(), caso.getFuente().getNombreVisible(),
                        contactoActualizado ? "" : " -- correo/teléfono no se tocaron porque el contacto es compartido por más de un caso"),
                true);

        return CasoAdminResponse.desde(caso, false, null, null, null);
    }

    // true si de verdad se pudo actualizar correo/telefono (cliente de un solo caso); false
    // si se omitió por ser un cliente compartido (ver comentario de actualizar() arriba).
    private boolean actualizarContactoCliente(Caso caso, String correoNuevo, String telefonoNuevo) {
        if (correoNuevo == null && telefonoNuevo == null) {
            return true;
        }
        if (casoRepository.countByCliente(caso.getCliente()) > 1) {
            return false;
        }
        Cliente cliente = caso.getCliente();
        if (correoNuevo != null) {
            String correoLimpio = correoNuevo.strip();
            if (correoLimpio.isBlank()) {
                cliente.setCorreo(null);
                cliente.setCorreoHash(null);
            } else {
                String hashNuevo = cifradoService.indiceCiego(correoLimpio);
                if (!hashNuevo.equals(cliente.getCorreoHash())) {
                    Optional<Cliente> otroConEseCorreo = clienteRepository.findByCorreoHash(hashNuevo);
                    if (otroConEseCorreo.isPresent() && !otroConEseCorreo.get().getId().equals(cliente.getId())) {
                        caso.setCliente(otroConEseCorreo.get());
                    } else {
                        cliente.setCorreo(correoLimpio);
                        cliente.setCorreoHash(hashNuevo);
                    }
                }
            }
        }
        if (telefonoNuevo != null) {
            String telefonoLimpio = telefonoNuevo.strip();
            caso.getCliente().setTelefono(telefonoLimpio.isBlank() ? null : telefonoLimpio);
        }
        return true;
    }

    // Ordenado por fuente y, dentro de cada fuente, por su número de caso (pedido explícito
    // del usuario: "que todos en los paneles queden organizados por su número") -- no por
    // fecha de creación como antes. Se ordena en memoria, no con ORDER BY en la consulta,
    // porque numeroCaso es VARCHAR con formatos distintos según la fuente (dígitos puros en
    // Judiciales, huella de contenido "h-..." en Superintendencia/Procesos Comisaría, ver
    // HojaCalculoService.huellaContenido()): un ORDER BY de texto pondría "10" antes que "9".
    // numeroOrdenable() extrae solo los dígitos y compara numéricamente; los casos MANUAL (sin
    // numeroCaso) y los de huella de contenido (que nunca tuvieron un número de caso real que
    // ordenar -- extraer un dígito cualquiera de en medio del hex de la huella sería un orden
    // sin ningún sentido) van al final de su grupo, ordenados ahí por fecha_creacion DESC
    // gracias a que Comparator.thenComparingLong es un sort estable sobre el resultado de
    // listarTodosConDetalle(), que ya viene en ese orden.
    // usuarioActualId: quién está pidiendo el listado (ver CasoAdminController.listar()) --
    // decide el badge de "sin revisar" de CADA usuario por separado (pedido explícito del
    // usuario: "si es nuevo para cada persona"), null solo puede pasar si el filtro de
    // seguridad dejara pasar una petición sin autenticar, lo cual no debería ocurrir bajo
    // /api/admin/**, pero no hace que el listado falle si pasara.
    @Transactional(readOnly = true)
    public List<CasoAdminResponse> listarTodos(Long usuarioActualId) {
        Map<Long, LocalDateTime> ultimaDeteccionPorCaso = actuacionJudicialRegistradaRepository
                .ultimaFechaDetectadaPorCaso().stream()
                .collect(Collectors.toMap(
                        ActuacionJudicialRegistradaRepository.UltimaDeteccionPorCaso::getCasoId,
                        ActuacionJudicialRegistradaRepository.UltimaDeteccionPorCaso::getUltimaFechaDetectada));
        // Ver el comentario de ultimaFechaMovimientoPorCaso(): esta es la fecha REAL del
        // movimiento (cuándo lo publicó el despacho), no cuándo nuestro backfill lo detectó --
        // es la que corrige el ordenador de "última actualización" en el listado.
        Map<Long, LocalDateTime> ultimoMovimientoPorCaso = actuacionJudicialRegistradaRepository
                .ultimaFechaMovimientoPorCaso().stream()
                .collect(Collectors.toMap(
                        ActuacionJudicialRegistradaRepository.UltimoMovimientoPorCaso::getCasoId,
                        ActuacionJudicialRegistradaRepository.UltimoMovimientoPorCaso::getUltimaFechaMovimiento));
        Map<Long, LocalDateTime> fechaVistaPorCaso = usuarioActualId == null
                ? Map.of()
                : seguimientoVistoUsuarioRepository.listarPorUsuario(usuarioActualId).stream()
                        .collect(Collectors.toMap(
                                SeguimientoVistoUsuarioRepository.VistoPorCaso::getCasoId,
                                SeguimientoVistoUsuarioRepository.VistoPorCaso::getFechaVista));
        // Pedido explícito del usuario: "pon un contador en cada caso con boletin... que
        // muestre dentro de cuantos dias seran borrados" -- la expiración más próxima de
        // cada caso, convertida a días restantes desde ahora mismo (ver el comentario de
        // CasoAdminResponse.diasParaBorrarBoletin). Un caso sin ninguna fila en caché
        // simplemente no aparece en este mapa -- ausencia = sin boletín disponible.
        LocalDateTime ahora = LocalDateTime.now();
        Map<Long, Integer> diasParaBorrarPorCaso = boletinJudicialCacheRepository.proximaExpiracionPorCaso().stream()
                .collect(Collectors.toMap(
                        BoletinJudicialCacheRepository.ProximaExpiracionPorCaso::getCasoId,
                        p -> (int) Math.max(0, ChronoUnit.DAYS.between(ahora, p.getProximaExpiracion()))));

        return casoRepository.listarPublicosConDetalle().stream()
                .sorted(Comparator.comparing(Caso::getFuente).thenComparingLong(CasoService::numeroOrdenable))
                .map(caso -> {
                    LocalDateTime ultimaDeteccion = ultimaDeteccionPorCaso.get(caso.getId());
                    LocalDateTime fechaVista = fechaVistaPorCaso.get(caso.getId());
                    boolean sinRevisar = ultimaDeteccion != null
                            && (fechaVista == null || ultimaDeteccion.isAfter(fechaVista));
                    return CasoAdminResponse.desde(caso, sinRevisar, ultimaDeteccion,
                            ultimoMovimientoPorCaso.get(caso.getId()),
                            diasParaBorrarPorCaso.get(caso.getId()));
                })
                .toList();
    }

    // Máximo de casos que puede acumular UN abogado en su bóveda personal (pedido explícito
    // del usuario, 2026-11-05: "limitandolo a un maximo de 20 casos para abogados"). Sin
    // límite para la bóveda de Gerencia -- nunca se pidió un tope ahí.
    public static final int MAXIMO_CASOS_PRIVADOS_POR_ABOGADO = 20;

    // Pedido explícito del usuario (2026-11-05): "implementa la misma funcionalidad que tenia
    // gerencia de casos privados y cifrados, implementala para todos los abogados, que cada
    // uno tenga un espacio privado para casos personales o sensibles... estos casos no los
    // podra ver nadie, ni gerencia ni nadie, ni gerencia puede ver los de abogados, ni
    // abogados los de gerencia". Único punto de decisión de acceso a un caso privado -- lo
    // reutilizan otros controladores (línea de tiempo, boletines-cache, tareas, documentos)
    // para que ningún endpoint por id se salte esta regla.
    public void verificarAccesoPrivado(Caso caso, UsuarioInterno actor) {
        if (!tieneAccesoPrivado(caso, actor)) {
            if (caso.getPropietarioPrivado() == null) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Este caso es privado y exclusivo de Gerencia.");
            }
            throw new org.springframework.security.access.AccessDeniedException(
                    "Este caso es privado de otro usuario -- no tienes acceso.");
        }
    }

    // Variante en booleano para FILTRAR listas (calendario de términos, control diario) en vez
    // de bloquear una sola petición por id -- misma regla exacta que verificarAccesoPrivado().
    public boolean tieneAccesoPrivado(Caso caso, UsuarioInterno actor) {
        if (!caso.isEsPrivadoCifrado()) {
            return true;
        }
        if (caso.getPropietarioPrivado() == null) {
            return actor.getRol() == RolUsuario.ADMIN_GENERAL;
        }
        return caso.getPropietarioPrivado().getId().equals(actor.getId());
    }

    // Bóveda de Casos Privados y Cifrados. ADMIN_GENERAL ve la bóveda de Gerencia
    // (propietarioPrivado = null); un ABOGADO ve SU PROPIA bóveda personal, nunca la de
    // Gerencia ni la de otro abogado (ver migración V85 y verificarAccesoPrivado() arriba).
    @Transactional(readOnly = true)
    public List<CasoAdminResponse> listarPrivados(Long usuarioActualId) {
        UsuarioInterno usuario = usuarioActualId == null
                ? null
                : usuarioInternoRepository.findById(usuarioActualId).orElse(null);
        if (usuario == null) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "No se pudo identificar al usuario para abrir la bóveda privada.");
        }
        List<Caso> privados = usuario.getRol() == RolUsuario.ADMIN_GENERAL
                ? casoRepository.listarPrivadosConDetalle()
                : casoRepository.listarPrivadosDeAbogadoConDetalle(usuarioActualId);

        Map<Long, LocalDateTime> ultimaDeteccionPorCaso = actuacionJudicialRegistradaRepository
                .ultimaFechaDetectadaPorCaso().stream()
                .collect(Collectors.toMap(
                        ActuacionJudicialRegistradaRepository.UltimaDeteccionPorCaso::getCasoId,
                        ActuacionJudicialRegistradaRepository.UltimaDeteccionPorCaso::getUltimaFechaDetectada));
        Map<Long, LocalDateTime> ultimoMovimientoPorCaso = actuacionJudicialRegistradaRepository
                .ultimaFechaMovimientoPorCaso().stream()
                .collect(Collectors.toMap(
                        ActuacionJudicialRegistradaRepository.UltimoMovimientoPorCaso::getCasoId,
                        ActuacionJudicialRegistradaRepository.UltimoMovimientoPorCaso::getUltimaFechaMovimiento));
        Map<Long, LocalDateTime> fechaVistaPorCaso = usuarioActualId == null
                ? Map.of()
                : seguimientoVistoUsuarioRepository.listarPorUsuario(usuarioActualId).stream()
                        .collect(Collectors.toMap(
                                SeguimientoVistoUsuarioRepository.VistoPorCaso::getCasoId,
                                SeguimientoVistoUsuarioRepository.VistoPorCaso::getFechaVista));
        LocalDateTime ahora = LocalDateTime.now();
        Map<Long, Integer> diasParaBorrarPorCaso = boletinJudicialCacheRepository.proximaExpiracionPorCaso().stream()
                .collect(Collectors.toMap(
                        BoletinJudicialCacheRepository.ProximaExpiracionPorCaso::getCasoId,
                        p -> (int) Math.max(0, ChronoUnit.DAYS.between(ahora, p.getProximaExpiracion()))));

        return privados.stream()
                .sorted(Comparator.comparing(Caso::getFechaCreacion).reversed())
                .map(caso -> {
                    LocalDateTime ultimaDeteccion = ultimaDeteccionPorCaso.get(caso.getId());
                    LocalDateTime fechaVista = fechaVistaPorCaso.get(caso.getId());
                    boolean sinRevisar = ultimaDeteccion != null
                            && (fechaVista == null || ultimaDeteccion.isAfter(fechaVista));
                    return CasoAdminResponse.desde(caso, sinRevisar, ultimaDeteccion,
                            ultimoMovimientoPorCaso.get(caso.getId()),
                            diasParaBorrarPorCaso.get(caso.getId()));
                })
                .toList();
    }

    @Transactional
    public CasoAdminResponse marcarPrivado(Long id, boolean esPrivado, String motivo, UsuarioInterno actor) {
        Caso caso = casoRepository.buscarConClienteYAbogadoPorId(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Caso no encontrado"));
        boolean esAdmin = actor.getRol() == RolUsuario.ADMIN_GENERAL;

        // Pedido explícito del usuario (2026-11-05): "implementa la misma funcionalidad que
        // tenia gerencia de casos privados y cifrados, implementala para todos los abogados...
        // ni gerencia puede ver los de abogados, ni abogados los de gerencia". Si el caso YA
        // es privado de alguien, solo ese alguien (o Gerencia para su propia bóveda) puede
        // tocarlo -- nunca "robarlo" hacia la bóveda de otro ni desclasificarlo por fuera.
        if (caso.isEsPrivadoCifrado()) {
            verificarAccesoPrivado(caso, actor);
        }

        if (esPrivado && !esAdmin) {
            // Un ABOGADO solo puede sumar a SU PROPIA bóveda personal, nunca a la de Gerencia,
            // y con tope de MAXIMO_CASOS_PRIVADOS_POR_ABOGADO.
            boolean yaEsSuyo = caso.getPropietarioPrivado() != null
                    && caso.getPropietarioPrivado().getId().equals(actor.getId());
            if (!yaEsSuyo) {
                long actuales = casoRepository.countByEsPrivadoCifradoTrueAndPropietarioPrivado_Id(actor.getId());
                if (actuales >= MAXIMO_CASOS_PRIVADOS_POR_ABOGADO) {
                    throw new ConflictoNegocioException(
                            "Ya tienes %d casos en tu bóveda privada (el máximo permitido). Reabre o desclasifica uno antes de agregar otro."
                                    .formatted(MAXIMO_CASOS_PRIVADOS_POR_ABOGADO));
                }
            }
            caso.setPropietarioPrivado(actor);
        } else if (!esPrivado) {
            caso.setPropietarioPrivado(null);
        }
        // esPrivado=true con esAdmin=true: propietarioPrivado se queda null (bóveda de
        // Gerencia), que es el valor por defecto -- no hace falta tocarlo.

        caso.setEsPrivadoCifrado(esPrivado);
        if (motivo != null && !motivo.isBlank()) {
            caso.setMotivoConfidencialidad(motivo.strip());
        } else if (!esPrivado) {
            caso.setMotivoConfidencialidad(null);
        }

        casoRepository.save(caso);

        String boveda = esAdmin ? "Bóveda de Gerencia" : "bóveda personal de " + actor.getNombre();
        registroSistemaService.registrar(
                TipoRegistroSistema.EDICION_CASO,
                "'%s' %s el caso #%d (%s) como Caso Privado y Cifrado (%s)".formatted(
                        actor.getNombre(),
                        esPrivado ? "clasificó" : "desclasificó",
                        caso.getId(),
                        caso.getRadicadoId() != null ? caso.getRadicadoId() : "Sin radicado",
                        boveda),
                true);

        return CasoAdminResponse.desde(caso, false, null, null, null);
    }

    // Pedido explícito del usuario (2026-11-05): "un boton para cada caso el cual nos permita
    // finalizarlo, y el caso se remite a un nuevo apartado de casos finalizados... una vez
    // finalizado el sistema ya no le hara vigilancia". Nunca borra nada -- todas las
    // actuaciones/boletines ya cacheados quedan intactos, esta bandera solo saca al caso de
    // las consultas que alimentan el ciclo de vigilancia (ver
    // CasoRepository.findByRadicadoIdIsNotNullAndFinalizadoFalse y
    // ActuacionJudicialRegistradaRepository.ultimaActuacionConFechaPorCaso/
    // conFechaRegistroDesde). Disponible para ADMIN_GENERAL y ABOGADO por igual, mismo nivel
    // de acceso que editar un caso.
    @Transactional
    public CasoAdminResponse finalizar(Long id, UsuarioInterno actor) {
        Caso caso = casoRepository.buscarConClienteYAbogadoPorId(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Caso no encontrado"));
        verificarAccesoPrivado(caso, actor);
        if (!caso.isFinalizado()) {
            caso.setFinalizado(true);
            caso.setFechaFinalizacion(LocalDateTime.now());
            caso.setFinalizadoPor(actor);
            casoRepository.save(caso);

            registroSistemaService.registrar(
                    TipoRegistroSistema.EDICION_CASO,
                    ("'%s' finalizó el caso #%d (%s) -- se mueve a Casos Finalizados y deja de recibir "
                            + "vigilancia judicial automática.").formatted(
                                    actor.getNombre(), caso.getId(),
                                    caso.getRadicadoId() != null ? caso.getRadicadoId() : "Sin radicado"),
                    true);
        }
        return CasoAdminResponse.desde(caso, false, null, null, null);
    }

    // Inverso de finalizar() -- por si se finalizó un caso por error. Retoma la vigilancia
    // judicial automática en el siguiente ciclo (ninguna acción manual adicional necesaria).
    @Transactional
    public CasoAdminResponse reabrir(Long id, UsuarioInterno actor) {
        Caso caso = casoRepository.buscarConClienteYAbogadoPorId(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Caso no encontrado"));
        verificarAccesoPrivado(caso, actor);
        if (caso.isFinalizado()) {
            caso.setFinalizado(false);
            caso.setFechaFinalizacion(null);
            caso.setFinalizadoPor(null);
            casoRepository.save(caso);

            registroSistemaService.registrar(
                    TipoRegistroSistema.EDICION_CASO,
                    ("'%s' reabrió el caso #%d (%s) -- vuelve a Casos activos y retoma la vigilancia judicial "
                            + "automática.").formatted(
                                    actor.getNombre(), caso.getId(),
                                    caso.getRadicadoId() != null ? caso.getRadicadoId() : "Sin radicado"),
                    true);
        }
        return CasoAdminResponse.desde(caso, false, null, null, null);
    }

    // Apartado "Casos Finalizados" -- mismo enriquecimiento de fechas reales que listarTodos(),
    // para que "guarde todo el ultimo seguimiento" se vea igual de completo que en la lista de
    // casos activos, solo que de solo lectura (nunca vuelven a actualizarse).
    @Transactional(readOnly = true)
    public List<CasoAdminResponse> listarFinalizados() {
        Map<Long, LocalDateTime> ultimaDeteccionPorCaso = actuacionJudicialRegistradaRepository
                .ultimaFechaDetectadaPorCaso().stream()
                .collect(Collectors.toMap(
                        ActuacionJudicialRegistradaRepository.UltimaDeteccionPorCaso::getCasoId,
                        ActuacionJudicialRegistradaRepository.UltimaDeteccionPorCaso::getUltimaFechaDetectada));
        Map<Long, LocalDateTime> ultimoMovimientoPorCaso = actuacionJudicialRegistradaRepository
                .ultimaFechaMovimientoPorCaso().stream()
                .collect(Collectors.toMap(
                        ActuacionJudicialRegistradaRepository.UltimoMovimientoPorCaso::getCasoId,
                        ActuacionJudicialRegistradaRepository.UltimoMovimientoPorCaso::getUltimaFechaMovimiento));
        LocalDateTime ahora = LocalDateTime.now();
        Map<Long, Integer> diasParaBorrarPorCaso = boletinJudicialCacheRepository.proximaExpiracionPorCaso().stream()
                .collect(Collectors.toMap(
                        BoletinJudicialCacheRepository.ProximaExpiracionPorCaso::getCasoId,
                        p -> (int) Math.max(0, ChronoUnit.DAYS.between(ahora, p.getProximaExpiracion()))));

        return casoRepository.listarFinalizadosConDetalle().stream()
                .map(caso -> CasoAdminResponse.desde(caso, false,
                        ultimaDeteccionPorCaso.get(caso.getId()),
                        ultimoMovimientoPorCaso.get(caso.getId()),
                        diasParaBorrarPorCaso.get(caso.getId())))
                .toList();
    }

    // Pedido explícito del usuario (2026-11-09): "popup de notificaciones de casos nuevos
    // identificados, donde se pueda aceptar la vinculacion del caso al sistema y se pueda
    // editar". Sin enriquecimiento de fechas de actuación (null/null/null): un caso recién
    // creado por la sincronización todavía no tiene ningún seguimiento judicial corrido.
    @Transactional(readOnly = true)
    public List<CasoAdminResponse> listarPendientesAprobacion() {
        return casoRepository.listarPendientesAprobacion().stream()
                .map(caso -> CasoAdminResponse.desde(caso, false, null, null, null))
                .toList();
    }

    // "aceptar la vinculacion del caso al sistema": el caso ya estaba completamente operativo
    // desde que se creó (vigilancia, correos, etc. nunca esperaron esta aprobación, ver el
    // comentario de Caso.pendienteAprobacion) -- esto solo apaga la notificación del popup y
    // deja constancia de quién la revisó. Si el usuario quiere corregir algún dato antes de
    // aceptar, el frontend llama primero a actualizar() (PATCH /{id}) con los cambios y luego
    // a este método.
    @Transactional
    public CasoAdminResponse aprobarCasoNuevo(Long id, UsuarioInterno actor) {
        Caso caso = casoRepository.buscarConClienteYAbogadoPorId(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Caso no encontrado"));
        caso.setPendienteAprobacion(false);
        caso.setAprobadoPor(actor);
        caso.setFechaAprobado(LocalDateTime.now());
        casoRepository.save(caso);
        return CasoAdminResponse.desde(caso, false, null, null, null);
    }

    // Pedido explícito del usuario (2026-11-09): "un pop-up al actualizarlos que detecte que
    // casos tuvieron cambios y cuales fueron" -- ver CasoCambioDetectado, generado dentro de
    // sincronizarDesdeHoja().
    @Transactional(readOnly = true)
    public List<sie.siejuridicos.caso.dto.CasoCambioDetectadoResponse> listarCambiosPendientes() {
        return casoCambioDetectadoRepository.listarPendientes().stream()
                .map(sie.siejuridicos.caso.dto.CasoCambioDetectadoResponse::desde)
                .toList();
    }

    // El gerente/abogado revisó el popup (lo cerró, o corrigió lo que había que corregir en la
    // hoja): esos cambios puntuales ya no deben volver a aparecer. Ids que no existen o que ya
    // estaban revisados se ignoran sin error -- un doble clic o una pestaña vieja no debe
    // romper nada.
    @Transactional
    public void marcarCambiosRevisados(List<Long> ids, UsuarioInterno actor) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<CasoCambioDetectado> cambios = casoCambioDetectadoRepository.findAllByIdIn(ids);
        LocalDateTime ahora = LocalDateTime.now();
        for (CasoCambioDetectado cambio : cambios) {
            cambio.setRevisado(true);
            cambio.setRevisadoPor(actor);
            cambio.setFechaRevisado(ahora);
        }
        casoCambioDetectadoRepository.saveAll(cambios);
    }

    // Solo la PRIMERA racha de dígitos, no todos los dígitos del texto: "40-2" (un número
    // duplicado en la hoja desambiguado por HojaCalculoService) debe ordenar junto a "40", no
    // como "402" -- concatenar todos los dígitos rompería justo el orden que se está pidiendo.
    private static final Pattern PRIMERA_RACHA_DE_DIGITOS = Pattern.compile("\\d+");

    private static long numeroOrdenable(Caso caso) {
        String numero = caso.getNumeroCaso();
        // "h-" = huella de contenido (ver HojaCalculoService.huellaContenido()), no un número
        // de caso real: extraer el primer dígito de en medio de un hash (ej. "h-a3f9..." -> 3)
        // ordenaría estos casos de forma arbitraria, no por ningún criterio real. Se tratan
        // igual que MANUAL (Long.MAX_VALUE), cayendo al final de su grupo en el orden estable
        // por fecha_creacion DESC que ya trae la consulta.
        if (numero == null || numero.startsWith("h-")) {
            return Long.MAX_VALUE;
        }
        Matcher m = PRIMERA_RACHA_DE_DIGITOS.matcher(numero);
        if (!m.find()) {
            return Long.MAX_VALUE;
        }
        try {
            return Long.parseLong(m.group());
        } catch (NumberFormatException ex) {
            return Long.MAX_VALUE;
        }
    }

    // El reemplazo real de cargar casos a mano: lee TODAS las filas de las tres hojas de la
    // firma (ver HojaCalculoService.listarParaSincronizar()) y, por cada una, decide si ya
    // existe localmente (por fuente + numeroCaso, la llave estable de cada hoja) o hay que
    // crearla. Se puede correr las veces que haga falta -- es idempotente.
    //
    // Reconciliación completa (pedido explícito del usuario): si una fila que antes existía
    // ya no aparece en la lectura fresca de una fuente que sí se pudo leer sin error, el caso
    // se ELIMINA del sistema automáticamente (no se oculta, se borra: nada más referencia un
    // Caso, así que borrarlo no deja huérfanos). Si la fuente falló en esta corrida
    // (fuentesConError), sus casos NO se tocan -- un error de red/permisos no debe borrar
    // nada. Además, si cambió algún dato editable de una fila que ya existía (radicado,
    // nombre/correo/teléfono del cliente), ese cambio se refleja aquí mismo: si el radicado
    // cambió de verdad (no solo pasó de vacío a asignado), se resetean los indicadores de
    // notificación para que el cliente reciba el radicado correcto en el próximo envío.
    // Pedido explícito del usuario (2026-11-09): "en la columna E se tiene apoderado que en
    // este caso es el correspondiente abogado asignado... identifica de ahora en adelante y
    // ajusta el abogado asignado de los casos ya en el sistema". Quita tildes, pasa a
    // mayúsculas, colapsa espacios y recorta títulos comunes ("DR.", "DRA.", "ABOGADO/A") para
    // que "Dr. Carlos Muñoz" y "CARLOS MUÑOZ" (o cualquier combinación de esas dos formas)
    // normalicen igual.
    private static final Pattern TITULOS_APODERADO = Pattern.compile(
            "\\b(DR|DRA|DOCTOR|DOCTORA|AB|ABG|ABOGADO|ABOGADA)\\.?\\b");
    private static final Pattern ESPACIOS_MULTIPLES = Pattern.compile("\\s+");

    private static String normalizarNombrePersona(String texto) {
        if (texto == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String sinTitulos = TITULOS_APODERADO.matcher(sinTildes.toUpperCase(Locale.ROOT)).replaceAll(" ");
        return ESPACIOS_MULTIPLES.matcher(sinTitulos).replaceAll(" ").strip();
    }

    // Construye el índice UNA sola vez por corrida (no por fila) -- consultar a cada una de las
    // ~260 filas sería un N+1 innecesario. Incluye cualquier usuario interno activo
    // (ABOGADO o ADMIN_GENERAL): la columna "APODERADO(A)" de la hoja representa a quien sea
    // que esté llevando el caso, sin importar su rol en el sistema.
    private Map<String, UsuarioInterno> construirIndiceAbogadosPorNombre() {
        Map<String, UsuarioInterno> indice = new HashMap<>();
        for (UsuarioInterno usuario : usuarioInternoRepository.findAll()) {
            if (usuario.isActivo()) {
                String clave = normalizarNombrePersona(usuario.getNombre());
                if (!clave.isBlank()) {
                    indice.put(clave, usuario);
                }
            }
        }
        return indice;
    }

    // Nunca asigna si no hay certeza: coincidencia exacta primero (ya normalizada); si no hay
    // ninguna, solo se acepta una coincidencia parcial (todas las palabras del nombre más
    // corto contenidas en el más largo, ej. "CARLOS MUÑOZ" dentro de "CARLOS ANDRES MUÑOZ
    // PEREZ") cuando resulta en EXACTAMENTE un único candidato -- si hay más de uno (dos
    // abogados con nombres parecidos) o ninguno, se deja sin tocar en vez de adivinar.
    private UsuarioInterno resolverAbogadoPorApoderado(String apoderadoTexto, Map<String, UsuarioInterno> indice) {
        String clave = normalizarNombrePersona(apoderadoTexto);
        if (clave.isBlank()) {
            return null;
        }
        UsuarioInterno exacto = indice.get(clave);
        if (exacto != null) {
            return exacto;
        }
        List<String> tokensApoderado = List.of(clave.split(" "));
        UsuarioInterno unicoCandidato = null;
        for (Map.Entry<String, UsuarioInterno> entrada : indice.entrySet()) {
            List<String> tokensUsuario = List.of(entrada.getKey().split(" "));
            List<String> cortos = tokensApoderado.size() <= tokensUsuario.size() ? tokensApoderado : tokensUsuario;
            List<String> largos = tokensApoderado.size() <= tokensUsuario.size() ? tokensUsuario : tokensApoderado;
            if (cortos.size() < 2) {
                // Un solo nombre/apellido suelto es demasiado ambiguo para cruzarlo por
                // contención de tokens (ej. "LUIS" calzaría con cualquier abogado que se
                // llame Luis) -- solo se acepta coincidencia exacta para esos casos.
                continue;
            }
            if (largos.containsAll(cortos)) {
                if (unicoCandidato != null && unicoCandidato != entrada.getValue()) {
                    return null;
                }
                unicoCandidato = entrada.getValue();
            }
        }
        return unicoCandidato;
    }

    @Transactional
    public ResumenSincronizacion sincronizarDesdeHoja() {
        ResultadoSincronizacionHoja resultadoHoja = hojaCalculoService.listarParaSincronizar();
        List<FilaSincronizacionHoja> filas = resultadoHoja.filas();
        Map<String, UsuarioInterno> indiceAbogados = construirIndiceAbogadosPorNombre();
        int abogadosAsignados = 0;
        int nuevos = 0;
        int actualizados = 0;
        // Ya NO significa "se descartó" -- el caso se sincroniza igual (ver más abajo), esto
        // cuenta cuántos quedaron sin un correo válido capturado en la hoja, así que no se les
        // puede enviar notificación todavía.
        int omitidos = 0;
        int radicadosDuplicados = 0;
        int radicadosInvalidos = 0;
        // Reserva, dentro de esta misma corrida, cada radicado ya asignado (a un caso
        // existente o nuevo) para detectar dos filas de la hoja con el mismo radicado
        // (duplicado real de la firma) sin depender de una consulta a la base de datos que
        // solo vería el resultado tras guardar.
        Set<String> radicadosAsignadosEnEstaCorrida = new HashSet<>();
        Map<FuenteCaso, Set<String>> numerosVistosPorFuente = new EnumMap<>(FuenteCaso.class);
        // Pedido explícito del usuario (2026-11-09): "un pop-up al actualizarlos que detecte
        // que casos tuvieron cambios y cuales fueron" -- se acumulan aquí y se guardan todos
        // juntos al final de la corrida (ver CasoCambioDetectado).
        List<CasoCambioDetectado> cambiosDetectados = new ArrayList<>();

        for (FilaSincronizacionHoja fila : filas) {
            String numeroCaso = fila.numeroCaso().strip();
            numerosVistosPorFuente.computeIfAbsent(fila.fuente(), f -> new HashSet<>()).add(numeroCaso);

            // Un correo con forma inválida (o vacío) YA NO hace que el caso se omita -- pedido
            // explícito del usuario: "no se me puede perder ni faltar ninguno". Se sincroniza
            // igual (permite verlo, buscarlo, y que el estado se consulte en vivo apenas tenga
            // radicado), solo que sin poder enviarle correo hasta que la hoja se actualice con
            // un contacto real (ver crearCliente/actualizarDatosCliente más abajo).
            // Pedido explícito del usuario: tolerar múltiples correos (separados por ';', ',', '/', '·', espacios),
            // desinfectar puntos medios (ej. 'pagos@rumbos.com.co·') y asegurar que siempre se identifiquen todos los contactos.
            String correoValido = sie.siejuridicos.common.ContactoSanitizer.normalizarCadenaCorreos(fila.correoCliente());
            if (correoValido == null) {
                omitidos++;
            }
            String correo = correoValido;

            String radicado = fila.radicadoId();
            // Bug real encontrado en esta sesión: una fila con un radicado de más de 50
            // caracteres (dato corrupto de la hoja, no un radicado real de 23 dígitos)
            // tumbaba TODA la sincronización automática al arrancar (SincronizacionCasosScheduler)
            // con un DataException de Postgres sin que nada lo atajara -- el backend entero
            // no lograba levantar. Se ignora el radicado de esa fila puntual (el caso se
            // sigue sincronizando igual, solo sin radicado hasta que se corrija en la hoja)
            // en vez de dejar que una sola fila mala tumbe todo el proceso.
            if (radicado != null && radicado.length() > LONGITUD_MAXIMA_RADICADO) {
                log.warn("Radicado de {} Nº{} excede los {} caracteres permitidos ('{}...') -- se ignora "
                        + "como dato corrupto de la hoja, revisa esa fila manualmente.",
                        fila.fuente(), numeroCaso, LONGITUD_MAXIMA_RADICADO, radicado.substring(0, LONGITUD_MAXIMA_RADICADO));
                radicado = null;
                radicadosInvalidos++;
            }
            // Bug real encontrado en producción (2026-11-09, reportado por el usuario: el
            // popup de "cambios detectados" repetía el MISMO caso una y otra vez con un
            // "cambio" de radicado que en realidad nunca cambiaba): Caso.setRadicadoId() ya
            // normaliza con RadicadoUtil.normalizarParaAlmacenar() (quita notas como
            // "(2026-01059)" o ceros residuales de exportación) antes de guardar -- pero aquí
            // se comparaba el texto CRUDO de la celda contra el valor YA NORMALIZADO que
            // había quedado guardado. Si la celda trae cualquier nota/ruido, nunca eran
            // iguales en ningún ciclo, así que cada sincronización volvía a "detectar" el
            // mismo cambio inexistente (y de paso reseteaba correoEnviado/whatsappEnviado sin
            // necesidad). Normalizar aquí, antes de cualquier comparación, dos
            // normalizaciones del mismo radicado real ahora sí son iguales.
            radicado = RadicadoUtil.normalizarParaAlmacenar(radicado);
            Optional<Caso> existente = casoRepository.findByFuenteAndNumeroCaso(fila.fuente(), numeroCaso);

            if (existente.isPresent()) {
                Caso caso = existente.get();
                boolean cambio = false;
                if (radicado != null && !radicado.equals(caso.getRadicadoId())) {
                    // radicadoYaUsado excluye al propio caso a propósito: si esta fila ya
                    // tenía este mismo radicado guardado de una sincronización anterior,
                    // NO es un duplicado -- es la confirmación de que sigue siendo el
                    // mismo dato (esto es justo lo que causaba el bug reportado: antes se
                    // trataba como "duplicado" al propio caso consigo mismo y el radicado
                    // se perdía silenciosamente).
                    boolean radicadoYaUsado = casoRepository.findByRadicadoId(radicado)
                            .filter(otro -> !otro.getId().equals(caso.getId()))
                            .isPresent();
                    if (radicadoYaUsado || !radicadosAsignadosEnEstaCorrida.add(radicado)) {
                        radicadosDuplicados++;
                        log.warn("Radicado '{}' de {} Nº{} ya está asignado a otro caso -- hay un "
                                + "duplicado real en la hoja que hay que corregir ahí.",
                                radicado, fila.fuente(), numeroCaso);
                    } else {
                        boolean eraReasignacion = caso.getRadicadoId() != null;
                        // Pedido explícito del usuario (2026-11-09): "un pop-up al
                        // actualizarlos que detecte que casos tuvieron cambios y cuales
                        // fueron". Solo se registra cuando YA había un radicado real distinto
                        // (eraReasignacion) -- la asignación de radicado por PRIMERA vez a un
                        // caso que no tenía ninguno es progreso normal, no una corrección que
                        // alguien necesite revisar.
                        if (eraReasignacion) {
                            cambiosDetectados.add(CasoCambioDetectado.de(
                                    caso, "radicado", caso.getRadicadoId(), radicado));
                        }
                        caso.setRadicadoId(radicado);
                        registrarSiRadicadoInvalido(caso);
                        if (eraReasignacion) {
                            // El radicado que ya se le había enviado al cliente cambió en
                            // la hoja (corrección del despacho): hay que volver a
                            // notificarle el radicado correcto, no quedarse con el viejo
                            // marcado como "enviado".
                            caso.setCorreoEnviado(false);
                            caso.setWhatsappEnviado(false);
                        }
                        cambio = true;
                    }
                } else if (radicado != null) {
                    // Ya coincide con lo que teníamos: se reserva igual en el set de esta
                    // corrida para que otra fila con el mismo radicado (duplicado real) se
                    // detecte correctamente contra este.
                    radicadosAsignadosEnEstaCorrida.add(radicado);
                }
                if (actualizarDatosCliente(caso, fila, correo)) {
                    cambio = true;
                }
                // SIEMPRE se actualiza (a diferencia del nombre compartido del Cliente, ver
                // comentario de Caso.nombreEnHoja): vive en el propio Caso, así que no hay
                // riesgo de pisar el nombre de OTRO caso del mismo cliente.
                String nombreDeLaFila = fila.nombreCliente();
                if (nombreDeLaFila != null && !nombreDeLaFila.equals(caso.getNombreEnHoja())) {
                    if (caso.getNombreEnHoja() != null && !caso.getNombreEnHoja().isBlank()) {
                        cambiosDetectados.add(CasoCambioDetectado.de(
                                caso, "nombre_en_hoja", caso.getNombreEnHoja(), nombreDeLaFila));
                    }
                    caso.setNombreEnHoja(nombreDeLaFila);
                    cambio = true;
                }
                UsuarioInterno abogadoDetectado = resolverAbogadoPorApoderado(fila.apoderadoNombre(), indiceAbogados);
                if (abogadoDetectado != null && !abogadoDetectado.equals(caso.getAbogadoAsignado())) {
                    // Igual criterio que el radicado arriba: solo se alerta una REASIGNACIÓN
                    // (ya había otro abogado asignado), nunca la primera asignación -- incluida
                    // la del backfill inicial de esta misma funcionalidad sobre los casos que
                    // ya estaban en el sistema sin abogado.
                    if (caso.getAbogadoAsignado() != null) {
                        cambiosDetectados.add(CasoCambioDetectado.de(caso, "abogado_asignado",
                                caso.getAbogadoAsignado().getNombre(), abogadoDetectado.getNombre()));
                    }
                    caso.setAbogadoAsignado(abogadoDetectado);
                    abogadosAsignados++;
                    cambio = true;
                }
                if (cambio) {
                    actualizados++;
                }
                continue;
            }

            String radicadoAUsar = null;
            if (radicado != null) {
                boolean disponible = !casoRepository.existsByRadicadoId(radicado)
                        && radicadosAsignadosEnEstaCorrida.add(radicado);
                if (disponible) {
                    radicadoAUsar = radicado;
                } else {
                    radicadosDuplicados++;
                    log.warn("Radicado '{}' de {} Nº{} (caso nuevo) ya está asignado a otro caso -- "
                            + "hay un duplicado real en la hoja que hay que corregir ahí.",
                            radicado, fila.fuente(), numeroCaso);
                }
            }

            // Sin correo no hay nada determinista contra qué reidentificar/deduplicar un
            // cliente entre sincronizaciones (ver Cliente): se crea uno nuevo directo, sin
            // correo ni correoHash, y se completa solo cuando la hoja traiga un correo real
            // en el futuro (ver actualizarDatosCliente()).
            Cliente cliente;
            if (correo == null) {
                cliente = crearCliente(fila, null, null);
            } else {
                String correoHash = cifradoService.indiceCiego(correo);
                cliente = clienteRepository.findByCorreoHash(correoHash)
                        .orElseGet(() -> crearCliente(fila, correo, correoHash));
            }

            Caso nuevoCaso = new Caso();
            nuevoCaso.setCliente(cliente);
            nuevoCaso.setFuente(fila.fuente());
            nuevoCaso.setNumeroCaso(numeroCaso);
            nuevoCaso.setRadicadoId(radicadoAUsar);
            nuevoCaso.setNombreEnHoja(fila.nombreCliente());
            // Pedido explícito del usuario (2026-11-09): "cuando el sistema detecte la
            // creacion de un nuevo caso del google sheet salga un popup de notificaciones de
            // casos nuevos identificados". Solo casos GENUINAMENTE nuevos pasan por esta rama
            // (la reconciliación de arriba ya hizo match por fuente+numeroCaso) -- nunca
            // retroactivo sobre los cientos de casos que ya existían antes de esta
            // funcionalidad (ver migración V88, DEFAULT FALSE).
            nuevoCaso.setPendienteAprobacion(true);
            UsuarioInterno abogadoDelNuevoCaso = resolverAbogadoPorApoderado(fila.apoderadoNombre(), indiceAbogados);
            if (abogadoDelNuevoCaso != null) {
                nuevoCaso.setAbogadoAsignado(abogadoDelNuevoCaso);
                abogadosAsignados++;
            }
            casoRepository.save(nuevoCaso);
            registrarSiRadicadoInvalido(nuevoCaso);
            nuevos++;
        }

        if (!cambiosDetectados.isEmpty()) {
            casoCambioDetectadoRepository.saveAll(cambiosDetectados);
        }

        Set<FuenteCaso> fuentesConError = new HashSet<>(resultadoHoja.fuentesConError());
        int eliminados = 0;
        for (FuenteCaso fuente : FuenteCaso.values()) {
            if (fuente == FuenteCaso.MANUAL || fuentesConError.contains(fuente)) {
                continue;
            }
            Set<String> vistos = numerosVistosPorFuente.getOrDefault(fuente, Set.of());
            List<Caso> aEliminar = new ArrayList<>();
            for (Caso caso : casoRepository.findByFuente(fuente)) {
                if (caso.getNumeroCaso() != null && !vistos.contains(caso.getNumeroCaso())) {
                    aEliminar.add(caso);
                }
            }
            if (!aEliminar.isEmpty()) {
                casoRepository.deleteAll(aEliminar);
                eliminados += aEliminar.size();
            }
        }

        List<String> fuentesConErrorVisible = resultadoHoja.fuentesConError().stream()
                .map(FuenteCaso::getNombreVisible)
                .toList();

        registroSistemaService.registrar(
                TipoRegistroSistema.SINCRONIZACION_CASOS,
                "%d fila(s) leída(s), %d nuevo(s), %d actualizado(s), %d eliminado(s), %d sin correo, %d radicado(s) duplicado(s), %d abogado(s) asignado(s) desde la hoja%s"
                        .formatted(filas.size(), nuevos, actualizados, eliminados, omitidos, radicadosDuplicados, abogadosAsignados,
                                radicadosInvalidos > 0
                                        ? ", %d radicado(s) con formato inválido (ignorados)".formatted(radicadosInvalidos)
                                        : ""),
                fuentesConErrorVisible.isEmpty() ? null : "Fuentes con error: " + String.join(", ", fuentesConErrorVisible),
                fuentesConErrorVisible.isEmpty());

        return new ResumenSincronizacion(
                filas.size(), nuevos, actualizados, eliminados, omitidos, radicadosDuplicados, radicadosInvalidos,
                fuentesConErrorVisible);
    }

    // Aplica a Cliente cualquier corrección hecha directamente en la hoja. El correo es una
    // llave de identidad (ver Cliente.correoHash): si cambió, se reutiliza el Cliente que ya
    // tenga ese correo si existe, o se recifra este mismo Cliente con el correo nuevo si no
    // hay otro -- nunca se crea un Cliente duplicado por esto.
    //
    // nombre/teléfono SOLO se corrigen cuando este es el ÚNICO caso del cliente (ver
    // CasoRepository.countByCliente). Bug real encontrado con datos reales: un cliente con
    // varios casos (frecuente: la misma persona demandada/demandante en procesos distintos)
    // tiene, en cada fila de la hoja, una etiqueta corta específica de ESE caso en la columna
    // de nombre ("DDTE:SANTOYO") en vez de su nombre completo real -- sobrescribir el nombre
    // COMPARTIDO del cliente con eso hacía que se reportaran ~117 "actualizados" en cada
    // sincronización sin que nada real hubiera cambiado, solo alternando el nombre según qué
    // fila del cliente se procesó de último.
    private boolean actualizarDatosCliente(Caso caso, FilaSincronizacionHoja fila, String correoDeLaFila) {
        Cliente cliente = caso.getCliente();
        boolean cambio = false;

        if (casoRepository.countByCliente(cliente) <= 1) {
            String nombreNuevo = nombreConRespaldo(fila.nombreCliente());
            if (!nombreNuevo.equals(cliente.getNombre())) {
                cliente.setNombre(nombreNuevo);
                cambio = true;
            }

            String telefonoNuevo = fila.telefonoCliente();
            if (telefonoNuevo != null && !telefonoNuevo.equals(cliente.getTelefono())) {
                cliente.setTelefono(telefonoNuevo);
                cambio = true;
            }
        }

        // correoDeLaFila puede ser null (esta fila de la hoja todavía no tiene correo
        // capturado): sin nada que comparar, se deja el correo del cliente tal cual está --
        // si ya tenía uno, se conserva; si no tenía, sigue sin tenerlo hasta que la hoja se
        // actualice.
        if (correoDeLaFila != null) {
            String correoHashNuevo = cifradoService.indiceCiego(correoDeLaFila);
            if (!correoHashNuevo.equals(cliente.getCorreoHash())) {
                Optional<Cliente> otroConEseCorreo = clienteRepository.findByCorreoHash(correoHashNuevo);
                if (otroConEseCorreo.isPresent()) {
                    caso.setCliente(otroConEseCorreo.get());
                } else {
                    cliente.setCorreo(correoDeLaFila);
                    cliente.setCorreoHash(correoHashNuevo);
                }
                cambio = true;
            }
        }
        return cambio;
    }

    private Cliente crearCliente(FilaSincronizacionHoja fila, String correo, String correoHash) {
        Cliente nuevo = new Cliente();
        nuevo.setNombre(nombreConRespaldo(fila.nombreCliente()));
        nuevo.setCorreo(correo);
        nuevo.setCorreoHash(correoHash);
        nuevo.setTelefono(fila.telefonoCliente());
        return clienteRepository.save(nuevo);
    }

    // Cliente.nombre es NOT NULL; la columna de la hoja de origen a veces puede venir vacía
    // en una fila recién creada (o la hoja de origen no tiene ninguna columna de nombre,
    // como Procesos Comisaría). "Cliente" a secas en vez de inventar un nombre o dejarlo en
    // blanco.
    private static String nombreConRespaldo(String nombre) {
        return (nombre == null || nombre.isBlank()) ? "Cliente" : nombre;
    }

    // El otro botón nuevo del panel: dispara el envío real, por los dos canales (correo y
    // WhatsApp), a todos los casos que ya tienen radicado pero cuyo cliente todavía no lo
    // sabe por alguno de los dos. Separado de sincronizarDesdeHoja() a propósito --
    // sincronizar solo trae/actualiza datos, nunca envía nada por su cuenta; el admin decide
    // cuándo notificar. Cada canal se controla por separado: un caso sin teléfono capturado,
    // o mientras WhatsApp no esté configurado (ver WhatsAppService.isConfigurado()), sigue
    // recibiendo su correo con normalidad, sin que eso bloquee nada.
    //
    // SIN @Transactional a propósito -- incidente real: un envío masivo de ~200 correos
    // disparados todos casi en simultáneo (la versión @Async anterior) hizo que Gmail
// bloqueara la cuenta a mitad del lote (89 de 208 fallaron esa vez), y encima cada uno se
    // marcaba "enviado" apenas se intentaba, sin saber si de verdad había llegado. Ahora el
    // envío es secuencial, con una pausa entre cada uno (ver PAUSA_ENTRE_ENVIOS_MS) y un
    // reintento corto ante un fallo transitorio, y "enviado" solo queda true cuando el envío
    // realmente tuvo éxito -- si falla, el caso sigue "pendiente" para el próximo intento.
    private String obtenerClaveAgrupacionCliente(Caso caso) {
        Cliente cliente = caso.getCliente();
        if (cliente == null) {
            return "caso-" + (caso.getId() != null ? caso.getId() : caso.getRadicadoId());
        }
        if (cliente.getId() != null) {
            return "cliente-id-" + cliente.getId();
        }
        if (cliente.getTelefono() != null && !cliente.getTelefono().isBlank()) {
            String normalizado = WhatsAppService.normalizarCelular(cliente.getTelefono());
            if (normalizado != null) return "tel-" + normalizado;
        }
        if (cliente.getCorreo() != null && !cliente.getCorreo().isBlank()) {
            return "correo-" + cliente.getCorreo().trim().toLowerCase();
        }
        return "nombre-" + (cliente.getNombre() != null ? cliente.getNombre().trim().toLowerCase() : caso.hashCode());
    }

    // Cada caso se guarda apenas se conoce su resultado (transacción propia, implícita en
    // casoRepository.save()) en vez de una única transacción larga abierta los varios minutos
    // que puede tardar un lote grande con la pausa deliberada.
    // REGLA ESTRICTA (pedido explícito del usuario):
    // Jamás se puede enviar más de una notificación al cliente por el mismo radicado. Si en la
    // hoja existen filas duplicadas con el mismo radicado, solo se envía una vez y las demás
    // se marcan como enviadas sin generar spam.
    public ResumenEnvioCorreos enviarCorreosPendientes() {
        List<Caso> pendientes = casoRepository.listarPendientesDeNotificacion();
        int correosEnviados = 0;
        int correosFallidos = 0;
        int whatsappEnviados = 0;
        int whatsappFallidos = 0;
        int pendientesPorLimiteDiario = 0;
        int omitidosSinClienteReal = 0;

        Set<String> radicadosEnviadosCorreo = new HashSet<>();
        Set<String> radicadosEnviadosWhatsapp = new HashSet<>();

        for (Caso caso : pendientes) {
            if (caso.getRadicadoId() == null || caso.getRadicadoId().isBlank()) {
                log.warn("Caso {} sin radicado apareció en la lista de pendientes de notificación "
                        + "-- se omite, nunca se notifica un radicado que no existe.", caso.getId());
                continue;
            }

            String radicado = caso.getRadicadoId().trim();
            Cliente cliente = caso.getCliente();
            String correo = (cliente != null && cliente.getCorreo() != null) ? cliente.getCorreo().trim() : null;
            String telefono = (cliente != null && cliente.getTelefono() != null) ? cliente.getTelefono().trim() : null;
            String telefonoNormalizado = telefono != null ? WhatsAppService.normalizarCelular(telefono) : null;

            // Blindaje contra radicados duplicados en la misma corrida
            boolean yaEnviadoCorreoEsteRadicado = radicadosEnviadosCorreo.contains(radicado);
            boolean yaEnviadoWhatsappEsteRadicado = radicadosEnviadosWhatsapp.contains(radicado);

            boolean huboActualizacionDuplicado = false;
            if (yaEnviadoCorreoEsteRadicado && !caso.isCorreoEnviado()) {
                caso.setCorreoEnviado(true);
                huboActualizacionDuplicado = true;
            }
            if (yaEnviadoWhatsappEsteRadicado && !caso.isWhatsappEnviado()) {
                caso.setWhatsappEnviado(true);
                huboActualizacionDuplicado = true;
            }
            if (huboActualizacionDuplicado && (caso.isCorreoEnviado() || correo == null)
                    && (caso.isWhatsappEnviado() || telefonoNormalizado == null || !whatsAppService.isConfigurado())) {
                casoRepository.save(caso);
                continue;
            }

            boolean debeEnviarCorreo = !caso.isCorreoEnviado() && correo != null && !correo.isBlank() && !yaEnviadoCorreoEsteRadicado;
            boolean debeEnviarWhatsapp = !caso.isWhatsappEnviado() && whatsAppService.isConfigurado()
                    && telefonoNormalizado != null && !yaEnviadoWhatsappEsteRadicado;

            if (!debeEnviarCorreo && !debeEnviarWhatsapp) {
                continue;
            }

            // Cupo diario compartido de envíos masivos (ver LimiteEnvioMasivoService)
            if (!limiteEnvioMasivoService.intentarReservarCupo()) {
                pendientesPorLimiteDiario++;
                continue;
            }

            String nombre = caso.getNombreEnHoja() != null ? caso.getNombreEnHoja() : (cliente != null ? cliente.getNombre() : "Cliente");

            if (contieneNotaAdministrativaNoCliente(nombre)) {
                log.warn("Caso {} (radicado {}) tiene una nota administrativa en el nombre en vez de un "
                                + "cliente real ('{}') -- se omite la notificación automática, requiere revisión manual.",
                        caso.getId(), radicado, nombre);
                omitidosSinClienteReal++;
                continue;
            }

            try {
                boolean cambio = false;

                if (debeEnviarCorreo) {
                    boolean exito = enviarConReintento(
                            () -> emailService.enviarCodigoCasoSincrono(nombre, correo, radicado));
                    if (exito) {
                        caso.setCorreoEnviado(true);
                        correosEnviados++;
                        radicadosEnviadosCorreo.add(radicado);
                    } else {
                        correosFallidos++;
                    }
                    cambio = true;
                    pausar(PAUSA_ENTRE_ENVIOS_MS);
                }

                if (debeEnviarWhatsapp) {
                    boolean exito = enviarConReintento(
                            () -> whatsAppService.enviarCodigoCasoSincrono(nombre, telefono, radicado));
                    if (exito) {
                        caso.setWhatsappEnviado(true);
                        whatsappEnviados++;
                        radicadosEnviadosWhatsapp.add(radicado);
                    } else {
                        whatsappFallidos++;
                    }
                    cambio = true;
                    pausar(PAUSA_ENTRE_ENVIOS_MS);
                }

                if (cambio) {
                    casoRepository.save(caso);
                }
            } catch (Exception ex) {
                log.error("Fallo inesperado al notificar caso {} (radicado {}): {}",
                        caso.getId(), radicado, ex.getMessage(), ex);
                if (debeEnviarCorreo) correosFallidos++;
                if (debeEnviarWhatsapp) whatsappFallidos++;
            }
        }

        registroSistemaService.registrar(
                TipoRegistroSistema.ENVIO_NOTIFICACIONES_CASOS,
                "%d correo(s) enviado(s), %d fallido(s); %d WhatsApp enviado(s), %d fallido(s)%s%s"
                        .formatted(correosEnviados, correosFallidos, whatsappEnviados, whatsappFallidos,
                                pendientesPorLimiteDiario > 0
                                        ? "; %d caso(s) pendiente(s) para mañana por el límite diario de envíos"
                                                .formatted(pendientesPorLimiteDiario)
                                        : "",
                                omitidosSinClienteReal > 0
                                        ? "; %d caso(s) omitido(s) por no tener un cliente real identificable "
                                                .formatted(omitidosSinClienteReal)
                                                + "(revisar manualmente)"
                                        : ""),
                null,
                correosFallidos == 0 && whatsappFallidos == 0);

        return new ResumenEnvioCorreos(correosEnviados, correosFallidos, whatsappEnviados, whatsappFallidos,
                pendientesPorLimiteDiario, omitidosSinClienteReal);
    }

    // Reporte periódico de estado de casos.
    // Desacoplado por canal a pedido explícito del usuario:
    // - Correo: semanal (una vez a la semana, cada lunes) a costo $0 por SMTP.
    // - WhatsApp: quincenal (dos veces al mes: días 1 y 15) para reducir costos de Meta Cloud API.
    // REGLA CRÍTICA DE NO SPAM:
    // Se agrupa estrictamente por cliente. Si un cliente tiene 10 o 28 casos, recibe EXACTAMENTE UN
    // reporte (con su proceso más reciente) y todos sus casos se marcan con la fecha de envío actualizada.
    public ResumenReporteSemanal enviarReporteSemanal() {
        return enviarReporteSemanal(true, true);
    }

    public ResumenReporteSemanal enviarReporteSemanalAutomatico(boolean permitirCorreo, boolean permitirWhatsapp) {
        return enviarReporteSemanal(permitirCorreo, permitirWhatsapp);
    }

    public ResumenReporteSemanal enviarReporteSemanal(boolean permitirCorreo, boolean permitirWhatsapp) {
        if (!permitirCorreo && !permitirWhatsapp) {
            log.info("Reporte periódico de casos: ambos canales desactivados para este ciclo. Se omite.");
            return new ResumenReporteSemanal(0, 0, 0, 0, 0, 0, 0);
        }

        LocalDate hoy = LocalDate.now();
        LocalDateTime inicioSemana = hoy.with(DayOfWeek.MONDAY).atStartOfDay();
        LocalDateTime inicioQuincena = hoy.getDayOfMonth() < 15
                ? hoy.withDayOfMonth(1).atStartOfDay()
                : hoy.withDayOfMonth(15).atStartOfDay();

        List<Caso> casos = casoRepository.listarPendientesReporte(inicioSemana, inicioQuincena);
        int correosEnviados = 0;
        int correosFallidos = 0;
        int whatsappEnviados = 0;
        int whatsappFallidos = 0;
        int pendientesPorLimiteDiario = 0;
        int omitidosSinClienteReal = 0;
        int casosConReporte = 0;

        Set<String> telefonosEnviadosEnEstaCorrida = new HashSet<>();
        Set<String> correosEnviadosEnEstaCorrida = new HashSet<>();

        // Agrupar los casos por cliente para garantizar que ningún cliente reciba múltiples mensajes
        Map<String, List<Caso>> casosPorCliente = new LinkedHashMap<>();
        for (Caso caso : casos) {
            if (caso.getRadicadoId() == null || caso.getRadicadoId().isBlank()) {
                continue;
            }
            casosPorCliente.computeIfAbsent(obtenerClaveAgrupacionCliente(caso), k -> new ArrayList<>()).add(caso);
        }

        for (List<Caso> casosDelCliente : casosPorCliente.values()) {
            if (casosDelCliente.isEmpty()) continue;

            Cliente cliente = casosDelCliente.get(0).getCliente();
            String correo = (cliente != null && cliente.getCorreo() != null) ? cliente.getCorreo().trim() : null;
            String telefono = (cliente != null && cliente.getTelefono() != null) ? cliente.getTelefono().trim() : null;
            String telefonoNormalizado = telefono != null ? WhatsAppService.normalizarCelular(telefono) : null;

            boolean tienePendienteCorreo = casosDelCliente.stream().anyMatch(c ->
                    c.getFechaUltimoReporteSemanal() == null || c.getFechaUltimoReporteSemanal().isBefore(inicioSemana));
            boolean tienePendienteWhatsapp = casosDelCliente.stream().anyMatch(c ->
                    c.getFechaUltimoReporteWhatsapp() == null || c.getFechaUltimoReporteWhatsapp().isBefore(inicioQuincena));

            boolean debeEnviarCorreo = permitirCorreo
                    && correo != null && !correo.isBlank()
                    && tienePendienteCorreo
                    && !correosEnviadosEnEstaCorrida.contains(correo.toLowerCase());

            boolean debeEnviarWhatsapp = permitirWhatsapp
                    && whatsAppService.isConfigurado()
                    && telefonoNormalizado != null
                    && tienePendienteWhatsapp
                    && !telefonosEnviadosEnEstaCorrida.contains(telefonoNormalizado);

            if (!debeEnviarCorreo && !debeEnviarWhatsapp) {
                continue;
            }

            // Validar cupo diario
            if (!limiteEnvioMasivoService.intentarReservarCupo()) {
                pendientesPorLimiteDiario += casosDelCliente.size();
                continue;
            }

            // Ordenar casos para tomar el más reciente
            casosDelCliente.sort((c1, c2) -> {
                if (c1.getId() != null && c2.getId() != null) {
                    return Long.compare(c2.getId(), c1.getId());
                }
                return 0;
            });
            Caso casoPrincipal = casosDelCliente.get(0);
            String nombre = casoPrincipal.getNombreEnHoja() != null
                    ? casoPrincipal.getNombreEnHoja()
                    : (cliente != null ? cliente.getNombre() : "Cliente");
            String radicado = casoPrincipal.getRadicadoId();

            if (contieneNotaAdministrativaNoCliente(nombre)) {
                log.warn("Cliente/Caso tiene una nota administrativa en el nombre ('{}') -- se omite del reporte periódico.", nombre);
                omitidosSinClienteReal += casosDelCliente.size();
                continue;
            }

            try {
                boolean cambio = false;
                LocalDateTime ahora = LocalDateTime.now();

                if (debeEnviarCorreo) {
                    boolean exito = enviarConReintento(
                            () -> emailService.enviarReporteSemanalCasoSincrono(nombre, correo, radicado));
                    if (exito) {
                        correosEnviados++;
                        correosEnviadosEnEstaCorrida.add(correo.toLowerCase());
                        for (Caso c : casosDelCliente) {
                            c.setFechaUltimoReporteSemanal(ahora);
                            c.setCorreoEnviado(true);
                        }
                        cambio = true;
                    } else {
                        correosFallidos++;
                    }
                    pausar(PAUSA_ENTRE_ENVIOS_MS);
                }

                if (debeEnviarWhatsapp) {
                    boolean exito = enviarConReintento(
                            () -> whatsAppService.enviarReporteSemanalCasoSincrono(nombre, telefono, radicado));
                    if (exito) {
                        whatsappEnviados++;
                        telefonosEnviadosEnEstaCorrida.add(telefonoNormalizado);
                        for (Caso c : casosDelCliente) {
                            c.setFechaUltimoReporteWhatsapp(ahora);
                            c.setWhatsappEnviado(true);
                        }
                        cambio = true;
                    } else {
                        whatsappFallidos++;
                    }
                    pausar(PAUSA_ENTRE_ENVIOS_MS);
                }

                if (cambio) {
                    for (Caso c : casosDelCliente) {
                        casoRepository.save(c);
                    }
                    casosConReporte += casosDelCliente.size();
                }
            } catch (Exception ex) {
                log.error("Fallo inesperado al enviar reporte periódico para cliente '{}': {}", nombre, ex.getMessage(), ex);
                if (debeEnviarCorreo) correosFallidos++;
                if (debeEnviarWhatsapp) whatsappFallidos++;
            }
        }

        registroSistemaService.registrar(
                TipoRegistroSistema.REPORTE_SEMANAL_CASOS,
                "%d caso(s) cubierto(s), %d correo(s) enviado(s), %d fallido(s); %d WhatsApp enviado(s), %d fallido(s)%s%s"
                        .formatted(casosConReporte, correosEnviados, correosFallidos, whatsappEnviados, whatsappFallidos,
                                pendientesPorLimiteDiario > 0
                                        ? "; %d caso(s) pendiente(s) para mañana por el límite diario de envíos"
                                                .formatted(pendientesPorLimiteDiario)
                                        : "",
                                omitidosSinClienteReal > 0
                                        ? "; %d caso(s) omitido(s) por no tener un cliente real identificable "
                                                .formatted(omitidosSinClienteReal)
                                                + "(revisar manualmente)"
                                        : ""),
                null,
                correosFallidos == 0 && whatsappFallidos == 0);

        return new ResumenReporteSemanal(casosConReporte, correosEnviados, correosFallidos,
                whatsappEnviados, whatsappFallidos, pendientesPorLimiteDiario, omitidosSinClienteReal);
    }

    // Un reintento después de una pausa corta antes de darse por vencido: la mayoría de
    // fallos de SMTP/Meta son transitorios (un "Temporary System Problem" de Gmail, un
    // timeout de red puntual) y desaparecen solos unos segundos después -- reintentar una vez
    // recupera esos casos sin necesidad de que el admin vuelva a apretar el botón.
    private boolean enviarConReintento(java.util.function.BooleanSupplier envio) {
        if (envio.getAsBoolean()) {
            return true;
        }
        pausar(PAUSA_REINTENTO_MS);
        return envio.getAsBoolean();
    }

    private void pausar(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    // Marcadores de que la columna de nombre de la hoja no contiene un cliente real sino una
    // nota administrativa interna del despacho -- incidente real: una fila de JUDICIALES tenía
    // "DDTE:TATIANA B. NO SOMOS PARTE" en el nombre (con el correo de una integrante del
    // despacho capturado como contacto de referencia, no de un cliente), y el envío automático
    // le mandó un correo de "tu radicado es..." como si fuera su caso. "No somos parte" es la
    // forma en que la firma anota en la hoja que ese proceso NO es un caso propio (solo lo
    // tienen en seguimiento interno) -- nunca se le debe notificar a nadie como si lo fuera.
    // "Sujeto procesal representado" es el encabezado literal de esa columna en la hoja: si
    // aparece como valor, es una fila mal formada/vacía, no un nombre real.
    private static boolean contieneNotaAdministrativaNoCliente(String nombre) {
        if (nombre == null) {
            return false;
        }
        String normalizado = nombre.toLowerCase(Locale.ROOT);
        return normalizado.contains("no somos parte") || normalizado.contains("sujeto procesal representado");
    }

    // Longitud máxima del radicado que se refleja en el Registro del Sistema (ver
    // consultar() más abajo): el parámetro "codigo" lo manda el visitante sin autenticar, así
    // que un valor absurdamente largo (abuso, no un radicado real -- ningún radicado real de
    // la firma se acerca a esto) no debe generar una fila gigante en la bitácora.
    private static final int LONGITUD_MAXIMA_RADICADO_EN_REGISTRO = 100;

    // Flujo de dos capas, clave para la seguridad: primero se valida que el radicado esté
    // registrado en NUESTRA tabla (solo el admin, autenticado, puede poblarla) antes de
    // consultar la hoja. Así el endpoint público nunca se convierte en un buscador de
    // cualquier fila de las hojas internas de la firma, que pueden tener más casos que los
    // que se comparten con clientes. La fuente del propio Caso local (nunca un parámetro que
    // mande el cliente) decide en cuál de las tres pestañas buscar.
    //
    // Cada intento de consulta queda en el Registro del Sistema (pedido explícito del
    // usuario: "llevar data" de qué radicado consultó su estado y cuándo), tanto si el
    // radicado existe como si no -- registrar() usa su propia transacción (REQUIRES_NEW, ver
    // RegistroSistemaService), y un fallo al guardarlo nunca rompe la consulta real (se traga
    // y se loguea ahí mismo).
    //
    // Deliberadamente SIN @Transactional en este método (aunque el resto de CasoService sí
    // lo usa) -- dos bugs reales encontrados y confirmados en esta auditoría, contra
    // Postgres real en producción, no en un mock:
    // 1) Con @Transactional(readOnly = true) (la anotación original de este método, cuando
    //    solo leía), Postgres rechazaba el INSERT del registro con "cannot execute INSERT in
    //    a read-only transaction", incluso dentro de la transacción aparte de REQUIRES_NEW.
    // 2) Quitar SOLO el readOnly (dejando @Transactional a secas) no bastó: cuando el
    //    radicado no existe, este método termina lanzando RecursoNoEncontradoException, lo
    //    que revierte SU PROPIA transacción -- y esa reversión se llevaba de encuentro el
    //    registro YA GUARDADO por la transacción REQUIRES_NEW anidada (confirmado con
    //    logging temporal: el INSERT sí corría y Postgres sí asignaba un id de la secuencia,
    //    pero la fila nunca quedaba en la tabla). En vez de perseguir esa interacción caso
    //    por caso, la solución robusta es no envolver este método en ninguna transacción
    //    propia: findByRadicadoId() ya trae la suya (todo repositorio de Spring Data JPA la
    //    tiene, individual, de solo lectura) y registrar() trae la suya (REQUIRES_NEW) --
    //    ninguna de las dos necesita ni se beneficia de una transacción ambiente alrededor
    //    que además pueda terminar en rollback por una excepción de negocio esperada (un
    //    "no encontrado" no es un error de los que ameritan deshacer nada).
    public CasoConsultaResponse consultar(String radicadoId) {
        String radicadoBuscado = radicadoId.strip();
        String radicadoParaRegistro = radicadoBuscado.length() > LONGITUD_MAXIMA_RADICADO_EN_REGISTRO
                ? radicadoBuscado.substring(0, LONGITUD_MAXIMA_RADICADO_EN_REGISTRO) + "…"
                : radicadoBuscado;

        Optional<Caso> caso = casoRepository.findByRadicadoId(radicadoBuscado);
        if (caso.isEmpty()) {
            // Un solo punto de normalización (ver RadicadoUtil) -- antes esta misma limpieza
            // vivía con su propia regex local aquí, en RamaJudicialCpnuClient y en
            // PublicacionesProcesalesClient, cada uno por su lado.
            String radicadoLimpio = RadicadoUtil.limpiar(radicadoBuscado);
            if (!radicadoLimpio.isBlank() && !radicadoLimpio.equals(radicadoBuscado)) {
                caso = casoRepository.findByRadicadoId(radicadoLimpio);
                if (caso.isPresent()) {
                    radicadoBuscado = radicadoLimpio;
                }
            }
        }
        if (caso.isEmpty()) {
            List<Caso> porPrefijo = casoRepository.buscarPorRadicadoPrefijo(radicadoBuscado);
            if (porPrefijo.size() == 1) {
                caso = Optional.of(porPrefijo.get(0));
            }
        }
        if (caso.isEmpty()) {
            registroSistemaService.registrar(
                    TipoRegistroSistema.CONSULTA_ESTADO_CASO,
                    "Radicado \"%s\" consultó su estado, pero no existe ningún caso registrado con ese radicado"
                            .formatted(radicadoParaRegistro),
                    false);
            throw new RecursoNoEncontradoException("No encontramos ningún caso con ese radicado");
        }

        Caso casoEncontrado = caso.get();

        registroSistemaService.registrar(
                TipoRegistroSistema.CONSULTA_ESTADO_CASO,
                "Radicado \"%s\" consultó su estado (%s)"
                        .formatted(radicadoParaRegistro, casoEncontrado.getFuente().getNombreVisible()),
                true);

        if (casoEncontrado.getFuente() == FuenteCaso.MANUAL) {
            return CasoConsultaResponse.sinEstadoDisponible(casoEncontrado);
        }

        Optional<FilaCasoHoja> fila = hojaCalculoService.buscarPorRadicado(casoEncontrado.getFuente(), casoEncontrado.getRadicadoId());
        return fila.map(f -> CasoConsultaResponse.desde(casoEncontrado, f))
                .orElseGet(() -> CasoConsultaResponse.sinEstadoDisponible(casoEncontrado));
    }
}
