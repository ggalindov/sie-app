package sie.siejuridicos.caso;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.caso.dto.CasoAdminResponse;
import sie.siejuridicos.caso.dto.CasoConsultaResponse;
import sie.siejuridicos.caso.dto.CrearCasoRequest;
import sie.siejuridicos.caso.dto.ResumenEnvioCorreos;
import sie.siejuridicos.caso.dto.ResumenSincronizacion;
import sie.siejuridicos.common.cifrado.CifradoService;
import sie.siejuridicos.common.exception.ConflictoNegocioException;
import sie.siejuridicos.common.exception.RecursoNoEncontradoException;
import sie.siejuridicos.correo.EmailService;
import sie.siejuridicos.hojacalculo.HojaCalculoService;
import sie.siejuridicos.hojacalculo.dto.FilaCasoHoja;
import sie.siejuridicos.hojacalculo.dto.FilaSincronizacionHoja;
import sie.siejuridicos.hojacalculo.dto.ResultadoSincronizacionHoja;
import sie.siejuridicos.registro.RegistroSistemaService;
import sie.siejuridicos.registro.TipoRegistroSistema;
import sie.siejuridicos.whatsapp.WhatsAppService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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

    public CasoService(CasoRepository casoRepository,
                        ClienteRepository clienteRepository,
                        EmailService emailService,
                        WhatsAppService whatsAppService,
                        HojaCalculoService hojaCalculoService,
                        CifradoService cifradoService,
                        RegistroSistemaService registroSistemaService) {
        this.casoRepository = casoRepository;
        this.clienteRepository = clienteRepository;
        this.emailService = emailService;
        this.whatsAppService = whatsAppService;
        this.hojaCalculoService = hojaCalculoService;
        this.cifradoService = cifradoService;
        this.registroSistemaService = registroSistemaService;
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

        emailService.enviarCodigoCaso(cliente.getNombre(), cliente.getCorreo(), guardado.getRadicadoId());
        guardado.setCorreoEnviado(true);
        if (whatsAppService.isConfigurado() && cliente.getTelefono() != null) {
            whatsAppService.enviarCodigoCaso(cliente.getNombre(), cliente.getTelefono(), guardado.getRadicadoId());
            guardado.setWhatsappEnviado(true);
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
    // Cada caso se guarda apenas se conoce su resultado (transacción propia, implícita en
    // casoRepository.save()) en vez de una única transacción larga abierta los varios minutos
    // que puede tardar un lote grande con la pausa deliberada.
    public ResumenEnvioCorreos enviarCorreosPendientes() {
        List<Caso> pendientes = casoRepository.listarPendientesDeNotificacion();
        int correosEnviados = 0;
        int correosFallidos = 0;
        int whatsappEnviados = 0;
        int whatsappFallidos = 0;

        for (Caso caso : pendientes) {
            // Segunda barrera, redundante a propósito: listarPendientesDeNotificacion() ya
            // filtra por radicadoId IS NOT NULL en la consulta, pero jamás se le debe enviar a
            // un cliente un correo/WhatsApp de "tu radicado es..." sin un radicado real que
            // mandarle -- si por cualquier cambio futuro en la consulta esa garantía se
            // rompiera, esta línea evita el envío en vez de mandar un mensaje sin sentido.
            if (caso.getRadicadoId() == null) {
                log.warn("Caso {} sin radicado apareció en la lista de pendientes de notificación "
                        + "-- se omite, nunca se notifica un radicado que no existe.", caso.getId());
                continue;
            }
            Cliente cliente = caso.getCliente();
            boolean cambio = false;

            // cliente.getCorreo() puede ser null (sujeto procesal sin contacto capturado
            // todavía en la hoja, ver sincronizarDesdeHoja()): se deja correoEnviado=false a
            // propósito, para que en cuanto la hoja traiga un correo real este mismo botón lo
            // recoja y notifique, en vez de darlo por "ya enviado" sin haberlo enviado nunca.
            if (!caso.isCorreoEnviado() && cliente.getCorreo() != null) {
                String nombre = cliente.getNombre();
                String correo = cliente.getCorreo();
                String radicado = caso.getRadicadoId();
                boolean exito = enviarConReintento(
                        () -> emailService.enviarCodigoCasoSincrono(nombre, correo, radicado));
                if (exito) {
                    caso.setCorreoEnviado(true);
                    correosEnviados++;
                } else {
                    correosFallidos++;
                }
                cambio = true;
                pausar(PAUSA_ENTRE_ENVIOS_MS);
            }
            if (!caso.isWhatsappEnviado() && whatsAppService.isConfigurado() && cliente.getTelefono() != null) {
                String nombre = cliente.getNombre();
                String telefono = cliente.getTelefono();
                String radicado = caso.getRadicadoId();
                boolean exito = enviarConReintento(
                        () -> whatsAppService.enviarCodigoCasoSincrono(nombre, telefono, radicado));
                if (exito) {
                    caso.setWhatsappEnviado(true);
                    whatsappEnviados++;
                } else {
                    whatsappFallidos++;
                }
                cambio = true;
                pausar(PAUSA_ENTRE_ENVIOS_MS);
            }

            if (cambio) {
                casoRepository.save(caso);
            }
        }

        registroSistemaService.registrar(
                TipoRegistroSistema.ENVIO_NOTIFICACIONES_CASOS,
                "%d correo(s) enviado(s), %d fallido(s); %d WhatsApp enviado(s), %d fallido(s)"
                        .formatted(correosEnviados, correosFallidos, whatsappEnviados, whatsappFallidos),
                null,
                correosFallidos == 0 && whatsappFallidos == 0);

        return new ResumenEnvioCorreos(correosEnviados, correosFallidos, whatsappEnviados, whatsappFallidos);
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
    // SIN readOnly=true a propósito, aunque este método no escribe nada directamente: bug
    // real encontrado corriendo la suite completa contra Postgres real (no en un mock, ahí no
    // se habría visto) -- Spring marca la conexión JDBC como de solo lectura para TODA la
    // transacción, y Postgres rechaza el INSERT del registro incluso dentro de la transacción
    // aparte de REQUIRES_NEW ("cannot execute INSERT in a read-only transaction"), porque esa
    // transacción anidada corre sobre la misma conexión física que Spring ya marcó readOnly.
    // El registro se tragaba el error y quedaba en silencio -- consultar() respondía bien,
    // pero NUNCA se guardaba nada en la bitácora, justo la funcionalidad que se pidió.
    @Transactional
    public CasoConsultaResponse consultar(String radicadoId) {
        String radicadoBuscado = radicadoId.strip();
        String radicadoParaRegistro = radicadoBuscado.length() > LONGITUD_MAXIMA_RADICADO_EN_REGISTRO
                ? radicadoBuscado.substring(0, LONGITUD_MAXIMA_RADICADO_EN_REGISTRO) + "…"
                : radicadoBuscado;

        Optional<Caso> caso = casoRepository.findByRadicadoId(radicadoBuscado);
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
