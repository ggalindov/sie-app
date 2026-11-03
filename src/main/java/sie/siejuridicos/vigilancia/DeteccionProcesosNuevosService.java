package sie.siejuridicos.vigilancia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import sie.siejuridicos.caso.CasoRepository;
import sie.siejuridicos.caso.Cliente;
import sie.siejuridicos.caso.ClienteRepository;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

// Pedido explícito del usuario: "implementa todas las mejoras identificadas" (comparando
// contra Lupa Jurídica, ver memoria del proyecto) -- la mejora de mayor valor detectada:
// Lupa no solo vigila los radicados que la firma ya conoce, también busca por el NOMBRE de
// cada cliente en el CPNU para encontrar procesos NUEVOS (demandas en contra o a favor que
// la firma todavía no sabe que existen). Confirmado en vivo el 2026-10-31: buscar por nombre
// a un cliente real de la firma devolvió un proceso (una tutela) que no estaba en nuestra
// tabla de casos.
//
// Ciclo PROPIO, separado del de SeguimientoJudicialService a propósito (mismo criterio que
// BoletinJudicialCacheService): esto es una búsqueda completamente distinta (por nombre, no
// por radicado) contra el mismo CPNU, nunca se mezcla con ese ciclo para no duplicar tráfico.
// Nunca crea un Caso automáticamente -- la búsqueda por nombre es ambigua (un tocayo real es
// perfectamente posible), así que cada hallazgo nuevo queda como CANDIDATO PENDIENTE para que
// un humano lo revise en /admin/procesos-detectados.
//
// Deliberadamente SIN @Transactional en el ciclo exterior (misma razón que
// BoletinJudicialCacheService/SeguimientoJudicialService: una pausa real de varios segundos
// entre cada cliente no puede dejar una transacción/conexión abierta todo ese tiempo). Por la
// misma razón, revisarCliente() NUNCA recibe una entidad Cliente ya cargada por otro método de
// esta clase: un @Transactional en un método invocado desde dentro de la misma clase (self-
// invocation) no pasa por el proxy de Spring y sencillamente no abre ninguna transacción -- ya
// costó tiempo real descubrir este mismo bug en TerminoProcesalService esta sesión. En vez de
// eso, revisarCliente() recibe solo el ID y vuelve a buscar el Cliente fresco con
// clienteRepository.findById(), que SÍ abre su propia transacción (es un método de Spring Data,
// no un self-invocation) y devuelve una entidad ya completamente hidratada -- nombre
// descifrado incluido (ver Cliente/CampoCifradoConverter) -- sin necesitar ninguna sesión
// abierta después.
@Service
public class DeteccionProcesosNuevosService {

    private static final Logger log = LoggerFactory.getLogger(DeteccionProcesosNuevosService.class);

    // Heurística tosca para decidir tipoPersona=jur vs nat -- el CPNU lo exige como
    // parámetro de búsqueda y no hay forma de saberlo con certeza solo del nombre guardado
    // (nunca capturamos NIT/cédula por separado). Casi todas las razones sociales reales
    // colombianas incluyen alguno de estos sufijos; si no hay ninguno, se asume persona
    // natural -- un error aquí en el peor caso devuelve cero resultados, nunca uno
    // incorrecto (el CPNU filtra por tipoPersona del lado de ellos).
    private static final Pattern SUFIJO_PERSONA_JURIDICA = Pattern.compile(
            "\\b(S\\.?A\\.?S\\.?|LTDA\\.?|S\\.?A\\.?|E\\.?U\\.?|CIA\\.?|LLC)\\b", Pattern.CASE_INSENSITIVE);

    private final CasoRepository casoRepository;
    private final ClienteRepository clienteRepository;
    private final RamaJudicialCpnuClient cpnuClient;
    private final ProcesoCandidatoDetectadoRepository candidatoRepository;
    private final VigilanciaCursorDeteccionRepository cursorRepository;
    private final long pausaEntreClientesMs;
    private final AtomicBoolean enCurso = new AtomicBoolean(false);

    public DeteccionProcesosNuevosService(CasoRepository casoRepository,
                                           ClienteRepository clienteRepository,
                                           RamaJudicialCpnuClient cpnuClient,
                                           ProcesoCandidatoDetectadoRepository candidatoRepository,
                                           VigilanciaCursorDeteccionRepository cursorRepository,
                                           @Value("${app.vigilancia.pausa-entre-casos-ms}") long pausaEntreClientesMs) {
        this.casoRepository = casoRepository;
        this.clienteRepository = clienteRepository;
        this.cpnuClient = cpnuClient;
        this.candidatoRepository = candidatoRepository;
        this.cursorRepository = cursorRepository;
        this.pausaEntreClientesMs = pausaEntreClientesMs;
    }

    private VigilanciaCursorDeteccion cargarCursor() {
        return cursorRepository.findById((short) 1).orElseGet(() -> {
            VigilanciaCursorDeteccion nuevo = new VigilanciaCursorDeteccion();
            nuevo.setPosicion(0);
            return nuevo;
        });
    }

    // Disparo manual desde el panel -- @Async por la misma razón que
    // BoletinJudicialCacheService.sincronizarNuevosBoletinesAsync(): recorrer un cliente por
    // cada caso con radicado, con una pausa real entre cada uno, tarda del orden de una hora
    // con la cartera real de la firma.
    @Async
    public void buscarNuevosProcesosAsync() {
        buscarNuevosProcesos();
    }

    public void buscarNuevosProcesos() {
        if (!enCurso.compareAndSet(false, true)) {
            log.info("Detección de procesos nuevos: ya hay una corrida en curso, se ignora esta solicitud.");
            return;
        }
        try {
            buscarNuevosProcesosInterno();
        } finally {
            enCurso.set(false);
        }
    }

    private void buscarNuevosProcesosInterno() {
        // Proyección plana (clienteId + radicadoId, nunca la entidad Caso/Cliente completa) --
        // ver el comentario de listarRadicadosPorCliente() en CasoRepository sobre por qué.
        List<CasoRepository.RadicadoPorCliente> filas = casoRepository.listarRadicadosPorCliente();

        // Un cliente puede tener varios casos (varios radicados) -- se agrupa una sola vez por
        // cliente para no repetir la misma búsqueda por nombre varias veces, y para que "ya lo
        // conocíamos" compare contra TODOS los radicados de ese cliente, no solo el del caso
        // que disparó la vuelta.
        Map<Long, Set<String>> radicadosConocidosPorCliente = new HashMap<>();
        for (CasoRepository.RadicadoPorCliente fila : filas) {
            radicadosConocidosPorCliente
                    .computeIfAbsent(fila.getClienteId(), k -> new HashSet<>())
                    .add(fila.getRadicadoId());
        }

        List<Long> clienteIds = new java.util.ArrayList<>(radicadosConocidosPorCliente.keySet());
        int clientesRevisados = 0;
        int candidatosNuevos = 0;

        if (!clienteIds.isEmpty()) {
            // Mismo bug real que ya se arregló en boletines y en seguimiento judicial (ver
            // sus comentarios): sin esta rotación, si el CPNU corta el freno de seguridad, los
            // mismos primeros clientes de la lista se revisan una y otra vez en cada ciclo, y
            // los últimos nunca reciben su turno.
            VigilanciaCursorDeteccion cursor = cargarCursor();
            int inicio = Math.floorMod(cursor.getPosicion(), clienteIds.size());
            if (inicio > 0) {
                List<Long> rotada = new java.util.ArrayList<>(clienteIds.subList(inicio, clienteIds.size()));
                rotada.addAll(clienteIds.subList(0, inicio));
                clienteIds = rotada;
            }

            for (int i = 0; i < clienteIds.size(); i++) {
                if (cpnuClient.pareceCaido()) {
                    log.warn("Detección de procesos nuevos: la Rama Judicial (CPNU) no está respondiendo "
                                    + "(varios fallos seguidos) -- se corta esta corrida en {}/{}, se retoma en el próximo ciclo.",
                            i, clienteIds.size());
                    break;
                }
                Long clienteId = clienteIds.get(i);
                try {
                    candidatosNuevos += revisarCliente(clienteId, radicadosConocidosPorCliente.get(clienteId));
                } catch (Exception ex) {
                    log.warn("Fallo inesperado buscando procesos nuevos para un cliente: {}", ex.getMessage());
                }
                clientesRevisados++;
                if (i < clienteIds.size() - 1) {
                    pausar(pausaEntreClientesMs);
                }
            }
            cursor.setPosicion(cursor.getPosicion() + clientesRevisados);
            cursorRepository.save(cursor);
        }
        log.info("Detección de procesos nuevos: {} cliente(s) revisado(s), {} candidato(s) nuevo(s) encontrado(s).",
                clientesRevisados, candidatosNuevos);
    }

    // Bug real confirmado en vivo (2026-11-01, ciclo nocturno): 570 de 1346 candidatos
    // guardados venían de clientes con nombre literal "Cliente" -- el placeholder que
    // CasoService.nombreConRespaldo() persiste en Cliente.nombre cuando la hoja de origen no
    // trae el nombre real (43 de 364 casos, dato legítimamente faltante en la fuente, no un
    // error). Ese placeholder nunca fue pensado para buscarse como si fuera un nombre de
    // persona real: buscarPorNombre("Cliente", ...) contra el CPNU trae procesos reales que
    // contienen esa palabra en el texto, sin relación alguna con el cliente real -- y como
    // CADA cliente con ese mismo placeholder recibe los MISMOS resultados, inunda
    // /admin/procesos-detectados con docenas de candidatos idénticos sin ningún valor.
    private static final String NOMBRE_RESPALDO_SIN_DATO = "Cliente";

    int revisarCliente(Long clienteId, Set<String> radicadosConocidos) {
        Cliente cliente = clienteRepository.findById(clienteId).orElse(null);
        if (cliente == null) {
            return 0;
        }
        String nombre = cliente.getNombre();
        if (nombre == null || nombre.isBlank() || nombre.strip().equalsIgnoreCase(NOMBRE_RESPALDO_SIN_DATO)) {
            return 0;
        }
        boolean esPersonaJuridica = SUFIJO_PERSONA_JURIDICA.matcher(nombre).find();
        List<RamaJudicialCpnuClient.ProcesoCandidato> encontrados = cpnuClient.buscarPorNombre(nombre, esPersonaJuridica);
        int nuevos = 0;
        for (RamaJudicialCpnuClient.ProcesoCandidato candidato : encontrados) {
            if (radicadosConocidos.contains(candidato.llaveProceso())) {
                continue;
            }
            if (candidatoRepository.existsByCliente_IdAndLlaveProceso(cliente.getId(), candidato.llaveProceso())) {
                continue;
            }
            candidatoRepository.save(ProcesoCandidatoDetectado.desde(cliente, candidato));
            nuevos++;
        }
        return nuevos;
    }

    private void pausar(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
