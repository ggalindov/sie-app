package sie.siejuridicos.caso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import sie.siejuridicos.whatsapp.WhatsAppService;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    private final CasoRepository casoRepository;
    private final ClienteRepository clienteRepository;
    private final EmailService emailService;
    private final WhatsAppService whatsAppService;
    private final HojaCalculoService hojaCalculoService;
    private final CifradoService cifradoService;
    private final RegistroSistemaService registroSistemaService;
    private final LimiteEnvioMasivoService limiteEnvioMasivoService;

    public CasoService(CasoRepository casoRepository,
                        ClienteRepository clienteRepository,
                        EmailService emailService,
                        WhatsAppService whatsAppService,
                        HojaCalculoService hojaCalculoService,
                        CifradoService cifradoService,
                        RegistroSistemaService registroSistemaService,
                        LimiteEnvioMasivoService limiteEnvioMasivoService) {
        this.casoRepository = casoRepository;
        this.clienteRepository = clienteRepository;
        this.emailService = emailService;
        this.whatsAppService = whatsAppService;
        this.hojaCalculoService = hojaCalculoService;
        this.cifradoService = cifradoService;
        this.registroSistemaService = registroSistemaService;
        this.limiteEnvioMasivoService = limiteEnvioMasivoService;
    }

    // Respaldo manual: ya no es el flujo principal (ver sincronizarDesdeHoja()), pero se
    // mantiene para un caso puntual que por lo que sea no está aún en ninguna de las hojas
    // de la firma. Fuente MANUAL: nunca tiene una pestaña que consultar en vivo (ver
    // consultar()), se queda siempre en "aún no hay actualizaciones".
    @Transactional
    public CasoAdminResponse crear(CrearCasoRequest request) {
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

        Caso guardado = casoRepository.save(caso);

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

        return CasoAdminResponse.desde(guardado);
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
    @Transactional(readOnly = true)
    public List<CasoAdminResponse> listarTodos() {
        return casoRepository.listarTodosConDetalle().stream()
                .sorted(Comparator.comparing(Caso::getFuente).thenComparingLong(CasoService::numeroOrdenable))
                .map(CasoAdminResponse::desde)
                .toList();
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
    @Transactional
    public ResumenSincronizacion sincronizarDesdeHoja() {
        ResultadoSincronizacionHoja resultadoHoja = hojaCalculoService.listarParaSincronizar();
        List<FilaSincronizacionHoja> filas = resultadoHoja.filas();
        int nuevos = 0;
        int actualizados = 0;
        // Ya NO significa "se descartó" -- el caso se sincroniza igual (ver más abajo), esto
        // cuenta cuántos quedaron sin un correo válido capturado en la hoja, así que no se les
        // puede enviar notificación todavía.
        int omitidos = 0;
        int radicadosDuplicados = 0;
        // Reserva, dentro de esta misma corrida, cada radicado ya asignado (a un caso
        // existente o nuevo) para detectar dos filas de la hoja con el mismo radicado
        // (duplicado real de la firma) sin depender de una consulta a la base de datos que
        // solo vería el resultado tras guardar.
        Set<String> radicadosAsignadosEnEstaCorrida = new HashSet<>();
        Map<FuenteCaso, Set<String>> numerosVistosPorFuente = new EnumMap<>(FuenteCaso.class);

        for (FilaSincronizacionHoja fila : filas) {
            String numeroCaso = fila.numeroCaso().strip();
            numerosVistosPorFuente.computeIfAbsent(fila.fuente(), f -> new HashSet<>()).add(numeroCaso);

            // Un correo con forma inválida (o vacío) YA NO hace que el caso se omita -- pedido
            // explícito del usuario: "no se me puede perder ni faltar ninguno". Se sincroniza
            // igual (permite verlo, buscarlo, y que el estado se consulte en vivo apenas tenga
            // radicado), solo que sin poder enviarle correo hasta que la hoja se actualice con
            // un contacto real (ver crearCliente/actualizarDatosCliente más abajo).
            String correoValido = fila.correoCliente();
            if (correoValido != null && (correoValido.isBlank() || !correoValido.contains("@"))) {
                correoValido = null;
            }
            if (correoValido == null) {
                omitidos++;
            }
            String correo = correoValido;

            String radicado = fila.radicadoId();
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
                        caso.setRadicadoId(radicado);
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
                    caso.setNombreEnHoja(nombreDeLaFila);
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
            casoRepository.save(nuevoCaso);
            nuevos++;
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
                "%d fila(s) leída(s), %d nuevo(s), %d actualizado(s), %d eliminado(s), %d sin correo, %d radicado(s) duplicado(s)"
                        .formatted(filas.size(), nuevos, actualizados, eliminados, omitidos, radicadosDuplicados),
                fuentesConErrorVisible.isEmpty() ? null : "Fuentes con error: " + String.join(", ", fuentesConErrorVisible),
                fuentesConErrorVisible.isEmpty());

        return new ResumenSincronizacion(
                filas.size(), nuevos, actualizados, eliminados, omitidos, radicadosDuplicados, fuentesConErrorVisible);
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
            String radicadoLimpio = radicadoBuscado.replaceAll("[\\s\\-\\.]+", "");
            if (!radicadoLimpio.isBlank() && !radicadoLimpio.equals(radicadoBuscado)) {
                caso = casoRepository.findByRadicadoId(radicadoLimpio);
                if (caso.isPresent()) {
                    radicadoBuscado = radicadoLimpio;
                }
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

        registroSistemaService.registrar(
                TipoRegistroSistema.CONSULTA_ESTADO_CASO,
                "Radicado \"%s\" consultó su estado (%s)"
                        .formatted(radicadoParaRegistro, caso.get().getFuente().getNombreVisible()),
                true);

        if (caso.get().getFuente() == FuenteCaso.MANUAL) {
            return CasoConsultaResponse.sinEstadoDisponible(caso.get());
        }

        Optional<FilaCasoHoja> fila = hojaCalculoService.buscarPorRadicado(caso.get().getFuente(), caso.get().getRadicadoId());
        return fila.map(f -> CasoConsultaResponse.desde(caso.get(), f))
                .orElseGet(() -> CasoConsultaResponse.sinEstadoDisponible(caso.get()));
    }
}
