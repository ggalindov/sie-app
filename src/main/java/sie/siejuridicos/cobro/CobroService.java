package sie.siejuridicos.cobro;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.cobro.dto.ClienteCobroResponse;
import sie.siejuridicos.cobro.dto.FilaCobroHoja;
import sie.siejuridicos.cobro.dto.ResumenEnvioRecordatoriosCobros;
import sie.siejuridicos.cobro.dto.ResumenSincronizacionCobros;
import sie.siejuridicos.common.cifrado.CifradoService;
import sie.siejuridicos.correo.EmailService;
import sie.siejuridicos.registro.RegistroSistemaService;
import sie.siejuridicos.registro.TipoRegistroSistema;
import sie.siejuridicos.whatsapp.WhatsAppService;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Cobros Pendientes: sincroniza clientes activos desde el Google Sheets de cobros (ver
// HojaCobrosService) y dispara el recordatorio mensual de pago por correo y WhatsApp.
//
// Reconciliación completa igual que caso.CasoService.sincronizarDesdeHoja() (pedido explícito
// del usuario, "lo mismo en el sheet de listado de clientes activos"): una fila que
// desaparece de la hoja hace que el cliente se ELIMINE del sistema automáticamente, no solo
// se oculte. A diferencia de Casos, aquí SIEMPRE se leen las dos pestañas completas en cada
// sincronización (HojaCobrosService.listarTodos() no tiene el concepto de "fuente con error
// parcial" que sí tiene HojaCalculoService), así que si la lectura falla la transacción
// completa se revierte y no se borra nada -- nunca se interpreta un error de red como "se
// vaciaron las hojas".
@Service
public class CobroService {

    // Mismo motivo y mismo valor que CasoService.PAUSA_ENTRE_ENVIOS_MS: un lote de recordatorios
    // sin pausa entre cada envío es justo lo que hizo que Gmail bloqueara la cuenta en el
    // incidente real del envío masivo de Casos.
    private static final long PAUSA_ENTRE_ENVIOS_MS = 1500;
    private static final long PAUSA_REINTENTO_MS = 4000;

    private final ClienteCobroRepository clienteCobroRepository;
    private final HojaCobrosService hojaCobrosService;
    private final EmailService emailService;
    private final WhatsAppService whatsAppService;
    private final CifradoService cifradoService;
    private final RegistroSistemaService registroSistemaService;

    public CobroService(ClienteCobroRepository clienteCobroRepository,
                         HojaCobrosService hojaCobrosService,
                         EmailService emailService,
                         WhatsAppService whatsAppService,
                         CifradoService cifradoService,
                         RegistroSistemaService registroSistemaService) {
        this.clienteCobroRepository = clienteCobroRepository;
        this.hojaCobrosService = hojaCobrosService;
        this.emailService = emailService;
        this.whatsAppService = whatsAppService;
        this.cifradoService = cifradoService;
        this.registroSistemaService = registroSistemaService;
    }

    // Ordenado por tipo y, dentro de cada tipo, por su número de fila (pedido explícito del
    // usuario: "que todos en los paneles queden organizados por su número") -- no
    // alfabéticamente por nombre como antes. Se ordena en memoria, no con ORDER BY en la
    // consulta: numeroFila es VARCHAR ("2", "68"...) y un ORDER BY de texto pondría "10"
    // antes que "9" (mismo motivo que CasoService.listarTodos()).
    @Transactional(readOnly = true)
    public List<ClienteCobroResponse> listarActivos() {
        return clienteCobroRepository.findByActivoTrueOrderByNombreAsc().stream()
                .sorted(Comparator.comparing(ClienteCobro::getTipo).thenComparingLong(CobroService::numeroOrdenable))
                .map(ClienteCobroResponse::desde)
                .toList();
    }

    @Transactional
    public ResumenSincronizacionCobros sincronizarDesdeHoja() {
        List<FilaCobroHoja> filas = hojaCobrosService.listarTodos();
        int nuevos = 0;
        int actualizados = 0;
        Map<TipoClienteCobro, Set<String>> vistosPorTipo = new EnumMap<>(TipoClienteCobro.class);

        for (FilaCobroHoja fila : filas) {
            vistosPorTipo.computeIfAbsent(fila.tipo(), t -> new HashSet<>()).add(fila.numeroFila());

            Optional<ClienteCobro> existente = clienteCobroRepository.findByTipoAndNumeroFila(fila.tipo(), fila.numeroFila());
            if (existente.isPresent()) {
                if (aplicarFila(existente.get(), fila)) {
                    actualizados++;
                }
            } else {
                ClienteCobro nuevo = new ClienteCobro();
                nuevo.setTipo(fila.tipo());
                nuevo.setNumeroFila(fila.numeroFila());
                aplicarFila(nuevo, fila);
                clienteCobroRepository.save(nuevo);
                nuevos++;
            }
        }

        // Reconciliación: cualquier cliente que ya teníamos registrado de un tipo, cuyo
        // numeroFila ya no aparece en la lectura fresca de ese mismo tipo, se elimina del
        // sistema -- la fila se borró de la hoja.
        List<ClienteCobro> aEliminar = new ArrayList<>();
        for (ClienteCobro cliente : clienteCobroRepository.findAll()) {
            Set<String> vistos = vistosPorTipo.getOrDefault(cliente.getTipo(), Set.of());
            if (!vistos.contains(cliente.getNumeroFila())) {
                aEliminar.add(cliente);
            }
        }
        if (!aEliminar.isEmpty()) {
            clienteCobroRepository.deleteAll(aEliminar);
        }

        registroSistemaService.registrar(
                TipoRegistroSistema.SINCRONIZACION_COBROS,
                "%d fila(s) leída(s), %d nuevo(s), %d actualizado(s), %d eliminado(s)"
                        .formatted(filas.size(), nuevos, actualizados, aEliminar.size()),
                true);

        return new ResumenSincronizacionCobros(filas.size(), nuevos, actualizados, aEliminar.size());
    }

    // Aplica a `cliente` los valores actuales de `fila`, devolviendo true si algo cambió (para
    // que sincronizarDesdeHoja() pueda contar cuántos registros se actualizaron de verdad, no
    // solo se volvieron a escribir con el mismo valor).
    private boolean aplicarFila(ClienteCobro cliente, FilaCobroHoja fila) {
        boolean cambio = false;
        if (!fila.nombre().equals(cliente.getNombre())) {
            cliente.setNombre(fila.nombre());
            cambio = true;
        }
        if (!Objects.equals(fila.correo(), cliente.getCorreo())) {
            cliente.setCorreo(fila.correo());
            cambio = true;
        }
        if (!Objects.equals(fila.telefono(), cliente.getTelefono())) {
            cliente.setTelefono(fila.telefono());
            cliente.setTelefonoHash(calcularTelefonoHash(fila.telefono()));
            cambio = true;
        }
        if (!Objects.equals(fila.cedulaNit(), cliente.getCedulaNit())) {
            cliente.setCedulaNit(fila.cedulaNit());
            cambio = true;
        }
        if (!Objects.equals(fila.honorarios(), cliente.getHonorarios())) {
            cliente.setHonorarios(fila.honorarios());
            cambio = true;
        }
        if (!Objects.equals(fila.pagoEsteMes(), cliente.getPagoEsteMes())) {
            cliente.setPagoEsteMes(fila.pagoEsteMes());
            cambio = true;
        }
        if (!Objects.equals(fila.respondioMensaje(), cliente.getRespondioMensaje())) {
            cliente.setRespondioMensaje(fila.respondioMensaje());
            cambio = true;
        }
        cliente.setActivo(true);
        return cambio;
    }

    private String calcularTelefonoHash(String telefono) {
        String normalizado = WhatsAppService.normalizarCelular(telefono);
        return normalizado == null ? null : cifradoService.indiceCiego(normalizado);
    }

    // Botón "Enviar recordatorios" del panel, y el mismo método que corre solo el día 1 (ver
    // RecordatorioCobroScheduler). Se salta: clientes con honorarios en $0 (pedido explícito:
    // "todo cliente que tenga 0 en casilla de honorario saltarlo"), clientes con
    // pagoEsteMes=true (ya pagaron), y clientes que YA recibieron el recordatorio este mismo
    // mes calendario (evita duplicar si el botón manual se usa además del envío automático).
    // SIN @Transactional a propósito -- mismo motivo que
    // CasoService.enviarCorreosPendientes(): el envío es secuencial con pausa entre cada uno
    // (puede tardar varios minutos en un lote grande), "enviado" solo cuenta cuando el envío
    // realmente tuvo éxito (con un reintento corto ante un fallo transitorio), y cada cliente
    // se guarda en su propia transacción corta apenas se conoce su resultado.
    public ResumenEnvioRecordatoriosCobros enviarRecordatorios() {
        List<ClienteCobro> activos = clienteCobroRepository.findByActivoTrueOrderByNombreAsc();
        YearMonth mesActual = YearMonth.now();
        int correosEnviados = 0;
        int correosFallidos = 0;
        int whatsappEnviados = 0;
        int whatsappFallidos = 0;
        int sinCosto = 0;

        for (ClienteCobro cliente : activos) {
            if (honorariosComoEntero(cliente.getHonorarios()) <= 0) {
                sinCosto++;
                continue;
            }
            if (Boolean.TRUE.equals(cliente.getPagoEsteMes())) {
                continue;
            }
            LocalDateTime ultimoRecordatorio = cliente.getFechaUltimoRecordatorio();
            if (ultimoRecordatorio != null && YearMonth.from(ultimoRecordatorio).equals(mesActual)) {
                continue;
            }

            boolean seEnvioAlgo = false;
            if (cliente.getCorreo() != null) {
                String nombre = cliente.getNombre();
                String correo = cliente.getCorreo();
                String honorarios = cliente.getHonorarios();
                boolean exito = enviarConReintento(
                        () -> emailService.enviarTirillaCobroSincrono(nombre, correo, honorarios));
                if (exito) {
                    correosEnviados++;
                    seEnvioAlgo = true;
                } else {
                    correosFallidos++;
                }
                pausar(PAUSA_ENTRE_ENVIOS_MS);
            }
            if (cliente.getTelefono() != null && whatsAppService.isConfigurado()) {
                String nombre = cliente.getNombre();
                String telefono = cliente.getTelefono();
                String honorarios = cliente.getHonorarios();
                boolean exito = enviarConReintento(
                        () -> whatsAppService.enviarRecordatorioCobroSincrono(nombre, telefono, honorarios));
                if (exito) {
                    whatsappEnviados++;
                    seEnvioAlgo = true;
                } else {
                    whatsappFallidos++;
                }
                pausar(PAUSA_ENTRE_ENVIOS_MS);
            }
            if (seEnvioAlgo) {
                cliente.setFechaUltimoRecordatorio(LocalDateTime.now());
                clienteCobroRepository.save(cliente);
            }
        }

        registroSistemaService.registrar(
                TipoRegistroSistema.ENVIO_RECORDATORIOS_COBROS,
                "%d correo(s) enviado(s), %d fallido(s); %d WhatsApp enviado(s), %d fallido(s); %d sin costo"
                        .formatted(correosEnviados, correosFallidos, whatsappEnviados, whatsappFallidos, sinCosto),
                null,
                correosFallidos == 0 && whatsappFallidos == 0);

        return new ResumenEnvioRecordatoriosCobros(correosEnviados, correosFallidos, whatsappEnviados, whatsappFallidos, sinCosto);
    }

    private boolean enviarConReintento(BooleanSupplier envio) {
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

    // Misma corrección que CasoService.numeroOrdenable: solo la PRIMERA racha de dígitos, no
    // todos los dígitos del texto.
    private static final Pattern PRIMERA_RACHA_DE_DIGITOS = Pattern.compile("\\d+");

    private static long numeroOrdenable(ClienteCobro cliente) {
        Matcher m = PRIMERA_RACHA_DE_DIGITOS.matcher(cliente.getNumeroFila());
        if (!m.find()) {
            return Long.MAX_VALUE;
        }
        try {
            return Long.parseLong(m.group());
        } catch (NumberFormatException ex) {
            return Long.MAX_VALUE;
        }
    }

    // "$ 0", "$0", null o vacío -> 0. Cualquier otro valor formateado a la colombiana
    // ("$ 1.750.905") se limpia a solo dígitos y se interpreta como pesos enteros -- nunca se
    // hacen operaciones aritméticas con esto más allá de decidir cero vs no-cero (ver
    // ClienteCobro.honorarios).
    static long honorariosComoEntero(String honorariosTexto) {
        if (honorariosTexto == null) {
            return 0L;
        }
        String soloDigitos = honorariosTexto.replaceAll("[^0-9]", "");
        if (soloDigitos.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(soloDigitos);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    // Llamado por el webhook de WhatsApp (ver WhatsAppWebhookController) cuando el cliente
    // responde al botón de sí/no del recordatorio. telefonoNormalizado ya viene en el mismo
    // formato E.164-sin-"+" que se usó para calcular telefonoHash al sincronizar (ver
    // calcularTelefonoHash), así que se puede buscar directo por igualdad del índice ciego,
    // sin descifrar el teléfono de cada cliente activo uno por uno.
    @Transactional
    public void registrarRespuesta(String telefonoNormalizado, String respuesta) {
        String hash = cifradoService.indiceCiego(telefonoNormalizado);
        List<ClienteCobro> encontrados = clienteCobroRepository.findByTelefonoHashAndActivoTrue(hash);
        for (ClienteCobro cliente : encontrados) {
            cliente.setRespondioMensaje(respuesta);
            hojaCobrosService.marcarRespuesta(cliente.getTipo(), cliente.getNumeroFila(), respuesta);
        }
    }
}
