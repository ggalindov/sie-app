package sie.siejuridicos.vigilancia;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sie.siejuridicos.caso.Caso;
import sie.siejuridicos.correo.EmailService;
import sie.siejuridicos.usuario.UsuarioInterno;
import sie.siejuridicos.usuario.UsuarioInternoRepository;
import sie.siejuridicos.vigilancia.dto.ActuacionRegistradaResponse;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// Gap real detectado en la auditoría del plan de desarrollo (inspirado en el correo diario
// de Lupa Jurídica, ver "Movimientos YYYY-MM-DD - SIE ISO 2016 SAS.eml"): digest automático
// de los boletines nuevos cacheados por BoletinJudicialCacheService.
//
// Tres destinatarios, cada uno con su propia bandera de "ya enviado" en
// BoletinJudicialCache (pedido explícito del usuario):
//  - Gerencia: TODOS los casos, diario a las 7am (ver BoletinJudicialScheduler).
//  - Cada abogado: SOLO sus casos asignados, diario a las 7am.
//  - Cada cliente: SOLO su(s) caso(s), semanal (viernes), y SOLO boletines que coinciden
//    por nombre con la actuación esperada (nunca un documento ambiguo sin confirmar).
//
// REDISEÑADO el 2026-10-31 -- pedido explícito del usuario, revisando otra vez el formato de
// Lupa Jurídica como referencia: "mas que enviar radicado... es que muestre cual es la
// ultima actualizacion y todos los ultimos documentos de la semana con descripcion". El
// digest anterior solo listaba el nombre crudo del PDF por caso, sin decir qué actuación lo
// originó ni mostrar el movimiento más reciente del proceso, y el correo al cliente ni
// siquiera incluía el link directo a la consulta de caso (sí lo tenía WhatsApp, por
// WhatsAppService.enlaceConsultaDirecto, pero el correo no). Los tres destinatarios
// (gerente/abogado/cliente) ahora comparten la MISMA construcción de cuerpo
// (construirCuerpoDigest), que por cada caso muestra: radicado + link directo, la última
// actualización real del proceso (la actuación más reciente, tenga o no boletín asociado), y
// la lista de documentos de la semana con su descripción (el nombre de la actuación que los
// originó, cuando se conoce).
@Service
public class BoletinJudicialDigestService {

    private static final Logger log = LoggerFactory.getLogger(BoletinJudicialDigestService.class);
    private static final DateTimeFormatter FECHA_LEGIBLE =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", new Locale("es", "CO"));

    private final BoletinJudicialCacheRepository boletinRepository;
    private final ActuacionJudicialRegistradaRepository actuacionRepository;
    private final UsuarioInternoRepository usuarioInternoRepository;
    private final EmailService emailService;
    private final String correoGerencia;
    private final boolean envioClientesActivo;

    public BoletinJudicialDigestService(BoletinJudicialCacheRepository boletinRepository,
                                         ActuacionJudicialRegistradaRepository actuacionRepository,
                                         UsuarioInternoRepository usuarioInternoRepository,
                                         EmailService emailService,
                                         @Value("${app.correo.gerencia}") String correoGerencia,
                                         @Value("${app.vigilancia.envio-clientes-activo:false}") boolean envioClientesActivo) {
        this.boletinRepository = boletinRepository;
        this.actuacionRepository = actuacionRepository;
        this.usuarioInternoRepository = usuarioInternoRepository;
        this.emailService = emailService;
        this.correoGerencia = correoGerencia;
        this.envioClientesActivo = envioClientesActivo;
    }

    // Diario 7am (ver BoletinJudicialScheduler). TODOS los casos, sin importar si tienen
    // abogado asignado o no -- la gerencia necesita verlo todo.
    @Transactional
    public void enviarDigestGerente() {
        List<BoletinJudicialCache> pendientes = boletinRepository.listarPendientesGerente();
        if (pendientes.isEmpty()) {
            log.info("Digest de boletín judicial a gerencia: nada pendiente hoy.");
            return;
        }
        Map<Caso, List<BoletinJudicialCache>> porCaso = agruparPorCaso(pendientes);
        String cuerpo = construirCuerpoDigestInterno(porCaso, "Resumen de boletines judiciales nuevos detectados en los "
                + "últimos días, para todos los casos de la firma.");
        List<EmailService.Adjunto> adjuntos = adjuntosDe(pendientes);
        emailService.enviarDigestBoletinGerente(correoGerencia, cuerpo, adjuntos);
        pendientes.forEach(b -> b.setEnviadoGerente(true));
        boletinRepository.saveAll(pendientes);
        log.info("Digest de boletín judicial a gerencia: {} boletín(es) de {} caso(s) enviado(s).",
                pendientes.size(), porCaso.size());
    }

    // Diario 7am, uno por cada abogado con al menos un caso asignado que tenga boletines
    // pendientes (pedido explícito del usuario: "a los abogados y sus casos asignados").
    @Transactional
    public void enviarDigestAbogados() {
        List<Long> idsAbogados = boletinRepository.listarIdsAbogadosConPendientes();
        for (Long abogadoId : idsAbogados) {
            UsuarioInterno abogado = usuarioInternoRepository.findById(abogadoId).orElse(null);
            if (abogado == null || !abogado.isActivo()) {
                continue;
            }
            List<BoletinJudicialCache> pendientes = boletinRepository.listarPendientesPorAbogado(abogadoId);
            if (pendientes.isEmpty()) {
                continue;
            }
            Map<Caso, List<BoletinJudicialCache>> porCaso = agruparPorCaso(pendientes);
            String cuerpo = construirCuerpoDigestInterno(porCaso, "Resumen de boletines judiciales nuevos detectados en "
                    + "tus casos asignados.");
            List<EmailService.Adjunto> adjuntos = adjuntosDe(pendientes);
            emailService.enviarDigestBoletinAbogado(abogado.getNombre(), abogado.getCorreo(), cuerpo, adjuntos);
            pendientes.forEach(b -> b.setEnviadoAbogado(true));
            boletinRepository.saveAll(pendientes);
            log.info("Digest de boletín judicial a '{}': {} boletín(es) de {} caso(s) enviado(s).",
                    abogado.getNombre(), pendientes.size(), porCaso.size());
        }
    }

    // Semanal, viernes (ver BoletinJudicialScheduler) -- un correo POR CASO (no agrupado por
    // cliente como el reporte periódico genérico: aquí sí importa mostrar el radicado y los
    // documentos exactos de ESE proceso). Solo boletines sin ambigüedad (ver
    // BoletinJudicialCacheRepository.findByCaso_IdAndEnviadoClienteFalseAndCoincideNombreActuacionTrue).
    @Transactional
    public void enviarDigestClientes() {
        if (!envioClientesActivo) {
            log.info("[SEGURIDAD ACTIVA] Digest de boletines a clientes reales APAGADO por configuración (app.vigilancia.envio-clientes-activo=false). Cero correos a clientes reales.");
            return;
        }
        List<Long> idsCasos = boletinRepository.listarIdsCasosConPendientesCliente();
        for (Long casoId : idsCasos) {
            List<BoletinJudicialCache> pendientes =
                    boletinRepository.findByCaso_IdAndEnviadoClienteFalseAndCoincideNombreActuacionTrue(casoId);
            if (pendientes.isEmpty()) {
                continue;
            }
            Caso caso = pendientes.get(0).getCaso();
            if (caso.getCliente() == null || caso.getCliente().getCorreo() == null || caso.getRadicadoId() == null) {
                continue;
            }
            String nombreMostrado = caso.getNombreEnHoja() != null ? caso.getNombreEnHoja() : caso.getCliente().getNombre();
            String cuerpo = construirCuerpoDigestCliente(caso, nombreMostrado, pendientes,
                    "Esta semana hubo novedades en tu proceso. Te resumimos abajo la última actualización y los "
                            + "documentos oficiales publicados por el despacho.");
            List<EmailService.Adjunto> adjuntos = adjuntosDe(pendientes);
            boolean exito = emailService.enviarDigestBoletinCliente(
                    nombreMostrado, caso.getCliente().getCorreo(), caso.getRadicadoId(), cuerpo, adjuntos);
            if (exito) {
                pendientes.forEach(b -> b.setEnviadoCliente(true));
                boletinRepository.saveAll(pendientes);
            }
        }
        log.info("Digest semanal de boletines judiciales a clientes: {} caso(s) procesado(s).", idsCasos.size());
    }

    // Pedido explícito del usuario: "testea... reporte con boletin [...] envio mensual de
    // todos los boletines" -- prueba el FORMATO del digest tipo gerencia/abogados (la tabla
    // con lista de documentos) contra un caso puntual, mandado a un correo de prueba en vez
    // de a la gerencia real. No toca enviadoGerente/enviadoAbogado (ver findByCaso_Id, sin
    // filtrar por esos flags): una prueba repetible no debe depender de, ni alterar, el
    // estado real de envío del caso.
    public boolean probarDigestInterno(Long casoId, String correoDestino) {
        List<BoletinJudicialCache> boletines = boletinRepository.findByCaso_Id(casoId);
        if (boletines.isEmpty()) {
            return false;
        }
        Map<Caso, List<BoletinJudicialCache>> porCaso = agruparPorCaso(boletines);
        String cuerpo = construirCuerpoDigestInterno(porCaso,
                "PRUEBA -- formato de digest de gerencia/abogados (boletines de un solo caso de prueba).");
        emailService.enviarDigestBoletinGerente(correoDestino, cuerpo, adjuntosDe(boletines));
        return true;
    }

    // Misma idea que arriba, pero con el formato de "envío mensual al cliente" (el mismo que
    // usa enviarDigestClientes(), con TODOS los boletines del caso en un solo correo) --
    // "todos los boletines" a propósito: la prueba es del formato completo, no de la regla de
    // ambigüedad en sí (esa ya se prueba aparte con casos reales, ver
    // coincideNombreActuacion).
    public boolean probarDigestCliente(Long casoId, String correoDestino) {
        List<BoletinJudicialCache> boletines = boletinRepository.findByCaso_Id(casoId);
        if (boletines.isEmpty()) {
            return false;
        }
        Caso caso = boletines.get(0).getCaso();
        if (caso.getRadicadoId() == null) {
            return false;
        }
        String nombreMostrado = caso.getNombreEnHoja() != null ? caso.getNombreEnHoja() : caso.getCliente().getNombre();
        String cuerpo = construirCuerpoDigestCliente(caso, nombreMostrado, boletines,
                "PRUEBA -- formato de digest al cliente (todos los boletines de un solo caso de prueba).");
        return emailService.enviarDigestBoletinCliente(
                nombreMostrado, correoDestino, caso.getRadicadoId(), cuerpo, adjuntosDe(boletines));
    }

    private Map<Caso, List<BoletinJudicialCache>> agruparPorCaso(List<BoletinJudicialCache> boletines) {
        Map<Caso, List<BoletinJudicialCache>> porCaso = new LinkedHashMap<>();
        for (BoletinJudicialCache boletin : boletines) {
            porCaso.computeIfAbsent(boletin.getCaso(), c -> new ArrayList<>()).add(boletin);
        }
        return porCaso;
    }

    private List<EmailService.Adjunto> adjuntosDe(List<BoletinJudicialCache> boletines) {
        return boletines.stream()
                .map(b -> new EmailService.Adjunto(b.getNombreArchivo(), b.getContenidoBytes()))
                .toList();
    }

    // Para "cuál es la última actualización" (pedido explícito del usuario) -- la actuación
    // real más reciente de CADA caso, tenga o no boletín asociado (a diferencia de los
    // documentos de la semana de abajo, que solo son los que sí se lograron descargar). Trae
    // la cartera completa de un solo golpe (ultimaActuacionConFechaPorCaso() ya hace esa
    // consulta agregada) e indexa solo por los casos que de verdad aparecen en este digest --
    // nunca se llama por caso individual, esto evitaría un N+1 real con decenas de casos.
    private Map<Long, ActuacionJudicialRegistrada> ultimaActuacionPorCasoId() {
        Map<Long, ActuacionJudicialRegistrada> resultado = new HashMap<>();
        for (ActuacionJudicialRegistrada actuacion : actuacionRepository.ultimaActuacionConFechaPorCaso()) {
            resultado.put(actuacion.getCaso().getId(), actuacion);
        }
        return resultado;
    }

    // REDISEÑADO el 2026-10-31 -- pedido explícito del usuario: "mandalo como lupa, que manda
    // un excel con todos los reportes del dia, no un excel en si sino una tabla y haz mas
    // ancho el correo". El correo real de Lupa (ver "Movimientos ... - SIE ISO 2016 SAS.eml"
    // en Descargas) consolida TODOS los movimientos del día en una sola tabla ancha, una FILA
    // POR ACTUACIÓN/DOCUMENTO (nunca agrupado por caso con listas anidadas) -- columnas
    // Ciudad/Despacho/Radicado, Partes, Actuación, Información Adicional (sus alertas en
    // rojo: Acreedor Subrogatorio/Liquidación de costas). Esta es la misma idea con nuestros
    // datos reales: Caso/Radicado (+ link directo, que Lupa no tiene porque NO apunta a un
    // sistema propio), Última actualización, Documento, Descripción, Alertas. Usa
    // ANCHO_DIGEST_INTERNO (960px, ver EmailService) en vez de los 600px del resto de
    // correos del sistema -- una tabla de 5 columnas no cabe legible en 600px.
    // Tamaños subidos el 2026-10-31 -- pedido explícito del usuario: "se ve muy pequeno,
    // hazlo mas grande... necesito que sea grande, organiza". De 10-11px (ilegible, se veía
    // "aplastado") a 15-18px, con bastante más padding entre celdas para que la tabla
    // respire en vez de verse apretada.
    private static final String FUENTE_TEXTO_PRINCIPAL = "font-size:15px;";
    private static final String FUENTE_TEXTO_SECUNDARIO = "font-size:13px;color:#8A8477;";
    private static final String PADDING_CELDA = "padding:14px 16px;";

    private String construirCuerpoDigestInterno(Map<Caso, List<BoletinJudicialCache>> porCaso, String introduccion) {
        Map<Long, ActuacionJudicialRegistrada> ultimaActuacion = ultimaActuacionPorCasoId();
        StringBuilder filas = new StringBuilder();
        boolean fondoAlterno = false;
        for (Map.Entry<Caso, List<BoletinJudicialCache>> entrada : porCaso.entrySet()) {
            Caso caso = entrada.getKey();
            String nombreMostrado = caso.getNombreEnHoja() != null ? caso.getNombreEnHoja()
                    : (caso.getCliente() != null ? caso.getCliente().getNombre() : "Cliente");
            String enlace = emailService.enlaceConsultaDirecto(caso.getRadicadoId());
            String celdaCaso = """
                    <span style="font-size:16px;font-weight:bold;">%s</span><br>
                    <span style="font-family:monospace;%s">%s</span><br>
                    <a href="%s" style="%sfont-weight:bold;color:#3258A1;text-decoration:none;">Consultar estado &rarr;</a>
                    """.formatted(escaparHtml(nombreMostrado), FUENTE_TEXTO_SECUNDARIO, escaparHtml(caso.getRadicadoId()),
                    enlace, FUENTE_TEXTO_SECUNDARIO);

            ActuacionJudicialRegistrada ultima = ultimaActuacion.get(caso.getId());
            String celdaUltimaActualizacion;
            if (ultima != null) {
                LocalDateTime fecha = ultima.getFechaRegistro() != null ? ultima.getFechaRegistro() : ultima.getFechaActuacion();
                celdaUltimaActualizacion = "<span style=\"" + FUENTE_TEXTO_PRINCIPAL + "\">" + escaparHtml(ultima.getNombreActuacion()) + "</span>"
                        + (fecha != null ? "<br><span style=\"" + FUENTE_TEXTO_SECUNDARIO + "\">"
                        + escaparHtml(FECHA_LEGIBLE.format(fecha)) + "</span>" : "");
            } else {
                celdaUltimaActualizacion = "<span style=\"" + FUENTE_TEXTO_SECUNDARIO + "\">Sin actuaciones registradas</span>";
            }

            List<BoletinJudicialCache> boletinesDelCaso = new ArrayList<>(entrada.getValue());
            boletinesDelCaso.sort(Comparator.comparing(BoletinJudicialCache::getFechaDescarga).reversed());
            // Una fila de TABLA por documento (estilo Lupa) -- la primera fila de este caso
            // lleva las celdas de Caso/Última actualización, las siguientes (si el caso trae
            // más de un documento) las dejan en blanco para no repetir el mismo dato varias
            // veces seguidas en la misma columna (igual que una tabla dinámica real).
            String fondo = fondoAlterno ? "#F7F7FA" : "#ececef";
            for (int i = 0; i < boletinesDelCaso.size(); i++) {
                boolean primeraDelCaso = i == 0;
                boolean ultimaDelCaso = i == boletinesDelCaso.size() - 1;
                String borde = ultimaDelCaso ? "border-bottom:2px solid #D9A925;" : "";
                filas.append("""
                        <tr style="background-color:%s;vertical-align:top;%s">
                        <td style="%s%s">%s</td>
                        <td style="%s%s">%s</td>
                        %s
                        </tr>
                        """.formatted(fondo, borde,
                        PADDING_CELDA, primeraDelCaso ? "" : "border-top:none;", primeraDelCaso ? celdaCaso : "",
                        PADDING_CELDA, primeraDelCaso ? "" : "border-top:none;", primeraDelCaso ? celdaUltimaActualizacion : "",
                        celdaDocumento(boletinesDelCaso.get(i))));
            }
            fondoAlterno = !fondoAlterno;
        }
        return """
                <p style="margin:0 0 20px;font-size:15px;">%s</p>
                <table style="width:100%%;border-collapse:collapse;">
                <tr style="font-size:16px;background-color:#DDECFD;color:#3258A1;">
                <th style="%stext-align:left;">Caso / Radicado</th>
                <th style="%stext-align:left;">Última actualización</th>
                <th style="%stext-align:left;">Documento de la semana</th>
                <th style="%stext-align:left;">Descripción</th>
                <th style="%stext-align:left;">Alertas</th>
                </tr>
                %s
                </table>
                """.formatted(introduccion, PADDING_CELDA, PADDING_CELDA, PADDING_CELDA, PADDING_CELDA, PADDING_CELDA, filas);
    }

    // Las dos últimas columnas de una fila de la tabla interna (documento + descripción +
    // alertas) -- separado de construirCuerpoDigestInterno() solo para no anidar más los
    // .formatted() de arriba. El nombre del documento es un LINK DIRECTO a la URL real del
    // PDF en Publicaciones Procesales (BoletinJudicialCache.urlOrigen) -- pedido explícito
    // del usuario: "ponle el link directo a cada documento para que lo descargue". Es la URL
    // pública original (no un endpoint propio que exige sesión), así que funciona abierta
    // directo desde el correo, sin necesitar estar logueado en el panel.
    private String celdaDocumento(BoletinJudicialCache boletin) {
        ActuacionJudicialRegistrada actuacion = boletin.getActuacion();
        String descripcion;
        String alertas = "<span style=\"" + FUENTE_TEXTO_SECUNDARIO + "\">Ninguna</span>";
        String fechaTexto = "";
        if (actuacion != null) {
            ActuacionRegistradaResponse resp = ActuacionRegistradaResponse.desde(actuacion);
            descripcion = resp.nombreActuacion()
                    + (resp.anotacion() != null && !resp.anotacion().isBlank() ? " -- " + resp.anotacion() : "");
            LocalDateTime fecha = resp.fechaRegistro() != null ? resp.fechaRegistro() : resp.fechaActuacion();
            if (fecha != null) {
                fechaTexto = "<br><span style=\"" + FUENTE_TEXTO_SECUNDARIO + "\">" + escaparHtml(FECHA_LEGIBLE.format(fecha)) + "</span>";
            }
            // Mismo criterio visual que el correo real de Lupa Jurídica (texto en rojo para
            // "Acreedor Subrogatorio"/"Liquidación de costas") -- pedido explícito del
            // usuario de replicar su formato.
            StringBuilder flags = new StringBuilder();
            if (resp.posibleAcreedorSubrogatorio()) {
                flags.append("<span style=\"color:#C0392B;font-weight:bold;font-size:13px;\">Acreedor subrogatorio</span><br>");
            }
            if (resp.posibleLiquidacionCostas()) {
                flags.append("<span style=\"color:#C0392B;font-weight:bold;font-size:13px;\">Liquidación de costas</span><br>");
            }
            if (resp.posibleMedidaCautelar()) {
                flags.append("<span style=\"color:#C0392B;font-weight:bold;font-size:13px;\">Medida cautelar</span><br>");
            }
            if (!flags.isEmpty()) {
                alertas = flags.toString();
            }
        } else {
            descripcion = "Documento del boletín del Estado del despacho";
        }
        return """
                <td style="%s"><a href="%s" style="font-size:16px;font-weight:bold;color:#3258A1;text-decoration:underline;">%s</a>%s%s</td>
                <td style="%s%s">%s</td>
                <td style="%s">%s</td>
                """.formatted(
                PADDING_CELDA, escaparHtml(boletin.getUrlOrigen()), escaparHtml(boletin.getNombreArchivo()),
                boletin.isCoincideNombreActuacion() ? "" : " <em style=\"font-weight:normal;font-size:13px;color:#8A8477;\">(revisar manualmente)</em>",
                fechaTexto,
                PADDING_CELDA, FUENTE_TEXTO_PRINCIPAL, escaparHtml(descripcion),
                PADDING_CELDA, alertas);
    }

    // Correo al CLIENTE final -- mantiene el formato tipo "tarjeta" (no la tabla densa de
    // arriba, que no tendría sentido para una sola persona con uno o pocos casos propios):
    // radicado + link directo, última actualización, y los documentos de la semana con su
    // descripción.
    private String construirCuerpoDigestCliente(Caso caso, String nombreMostrado, List<BoletinJudicialCache> boletines, String introduccion) {
        String enlace = emailService.enlaceConsultaDirecto(caso.getRadicadoId());
        ActuacionJudicialRegistrada ultima = ultimaActuacionPorCasoId().get(caso.getId());
        String ultimaActualizacionHtml;
        if (ultima != null) {
            LocalDateTime fecha = ultima.getFechaRegistro() != null ? ultima.getFechaRegistro() : ultima.getFechaActuacion();
            ultimaActualizacionHtml = """
                    <p style="margin:0 0 14px;font-size:13px;"><strong>Última actualización:</strong> %s%s</p>
                    """.formatted(escaparHtml(ultima.getNombreActuacion()),
                    fecha != null ? " · " + escaparHtml(FECHA_LEGIBLE.format(fecha)) : "");
        } else {
            ultimaActualizacionHtml = "";
        }
        List<BoletinJudicialCache> ordenados = new ArrayList<>(boletines);
        ordenados.sort(Comparator.comparing(BoletinJudicialCache::getFechaDescarga).reversed());
        StringBuilder docs = new StringBuilder();
        for (BoletinJudicialCache boletin : ordenados) {
            ActuacionJudicialRegistrada actuacion = boletin.getActuacion();
            String descripcion = actuacion != null
                    ? ActuacionRegistradaResponse.desde(actuacion).nombreActuacion()
                    : "Documento del boletín del Estado";
            docs.append("""
                    <li style="margin-bottom:4px;"><strong>%s</strong><br>
                    <span style="font-size:11px;color:#8A8477;">%s</span></li>
                    """.formatted(escaparHtml(boletin.getNombreArchivo()), escaparHtml(descripcion)));
        }
        return """
                <p style="margin:0 0 16px;">%s</p>
                <p style="margin:0 0 4px;font-weight:bold;">%s</p>
                <p style="margin:0 0 14px;font-family:monospace;font-size:12px;color:#D9A925;">%s</p>
                %s
                <p style="margin:0 0 8px;font-weight:bold;font-size:13px;">Documentos de la semana</p>
                <ul style="margin:0 0 16px;padding-left:18px;">%s</ul>
                <p style="margin:0;"><a href="%s" style="color:#3258A1;">Consultar el estado completo de tu proceso &rarr;</a></p>
                """.formatted(introduccion, escaparHtml(nombreMostrado),
                escaparHtml(caso.getRadicadoId()), ultimaActualizacionHtml, docs, enlace);
    }

    private static String escaparHtml(String texto) {
        return texto == null ? "" : texto
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
