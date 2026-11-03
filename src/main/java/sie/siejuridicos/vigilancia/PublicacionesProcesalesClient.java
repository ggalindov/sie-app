package sie.siejuridicos.vigilancia;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import sie.siejuridicos.caso.RadicadoUtil;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Resuelve el PDF oficial de una actuación contra el portal público "Publicaciones
// Procesales" de la propia Rama Judicial (publicacionesprocesales.ramajudicial.gov.co) --
// NO es un OneDrive/SharePoint de terceros como se temía al leer el texto de la actuación
// ("...consultar en el siguiente link: https://acortar.link/JuzgadoXXX"): ese acortador
// resuelve a este mismo portal oficial. Validado en vivo el 2026-10-30, ver la memoria del
// proyecto (vigilancia-judicial-alcance.md) para el detalle completo de la investigación.
//
// No es una API (no hay contrato, no hay SLA, es una página Liferay normal destinada a
// personas) -- por eso se usa Jsoup en vez de regex contra el HTML, y por eso, igual que
// RamaJudicialCpnuClient, NUNCA lanza: cualquier fallo devuelve una lista vacía.
//
// Una limitación real y conocida, documentada aquí para que quien use este cliente no asuma
// más de lo que da: la vista por despacho (ver buscarUrlDetalleBoletin) solo trae el puñado
// de boletines MÁS RECIENTES, sin paginación ni filtro de fecha que haya podido hacer
// funcionar en la investigación (los parámetros fechaInicio/fechaFin del formulario del
// portal no devolvieron resultados en las pruebas reales) -- sirve para vigilancia hacia
// ADELANTE (una actuación nueva, de los últimos días), NO para buscar el documento de una
// actuación antigua.
//
// Bug real reportado por el usuario (2026-10-31): "el sistema esta trayendo boletines que no
// son para cada caso". Causa -- el boletín de un despacho es el LISTADO DE ESTADO de TODO el
// despacho ese día (el "efecto" que se busca por idDespacho+fecha), así que casi siempre
// trae documentos de MUCHOS procesos distintos a la vez, no solo del caso que se está
// consultando (confirmado en vivo: el "LISTADO ESTADO No 084.pdf" de un caso real trajo 7
// procesos de 7 clientes totalmente distintos, ninguno el que se estaba buscando). La versión
// anterior de este cliente devolvía TODOS esos documentos marcando apenas "coincide" por una
// comparación tosca de palabras clave contra el nombre de la actuación -- author original
// asumía (incorrectamente, documentado más abajo como limitación #2 vieja) que el radicado de
// 23 dígitos no se podía cruzar contra el número de proceso interno del despacho.
//
// Eso SÍ se puede derivar, y con eso se arregla de raíz: el radicado de 23 dígitos trae el
// año (posiciones 13-16) y el consecutivo (posiciones 17-21) del proceso, EXACTAMENTE el
// mismo "AÑO-CONSECUTIVO" (con o sin ceros a la izquierda, con o sin guion) que el despacho
// usa en el nombre de archivo de sus documentos individuales ("2025-528", "202600532") y
// dentro del texto de sus listados agregados ("...2025 00528..."). Confirmado en vivo
// extrayendo el texto real de un LISTADO y viendo la tabla de procesos. Ver
// anioYConsecutivoDesdeRadicado() y documentosDelBoletin() -- ahora solo se devuelve (y por
// lo tanto solo se cachea) un documento cuando el proceso de ESTE radicado de verdad aparece
// en él, nunca "todo lo que había en el boletín de esa fecha".
//
// Verificado también en vivo (2026-10-31, navegando el portal real): no todos los despachos
// publican sus providencias como archivos PDF individuales con nombre visible -- un Juzgado
// de Pequeñas Causas, por ejemplo, mostró su "Notificación por Estado" con la leyenda literal
// "No hay documentos asociados en la presente publicación" (la tabla de providencias viene
// vacía, solo quedan enlaces "Ver" sin nombre de archivo visible, que documentosDelBoletin()
// correctamente ignora). Que buscarDocumentos() devuelva lista vacía para un caso así es el
// resultado CORRECTO (genuinamente no hay nada que descargar), no un fallo del cliente --
// antes de asumir un bug por "este caso no trae boletín", confirmar primero si su despacho de
// verdad adjunta PDFs navegando el portal manualmente.
@Component
public class PublicacionesProcesalesClient {

    private static final Logger log = LoggerFactory.getLogger(PublicacionesProcesalesClient.class);

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";
    private static final String PORTLET_NS =
            "_co_com_avanti_efectosProcesales_PublicacionesEfectosProcesalesPortletV2_INSTANCE_BIyXQFHVaYaq";

    // Bug real encontrado en vivo (2026-10-31): con solo User-Agent + "Connection: close", el
    // mismo link que un navegador real abre sin problema (confirmado visualmente, 548
    // notificaciones reales del despacho 110014003053) se quedaba colgado desde este cliente,
    // SIN IMPORTAR LA IP (probado desde la misma máquina, misma IP pública, navegador vs.
    // Jsoup) -- así que no era ni IPv6 ni un bloqueo por IP, sino que la petición en sí
    // no se parecía lo suficiente a la de un navegador real. Estas son las cabeceras que
    // cualquier Chrome real manda y que antes faltaban aquí -- nada de esto imita huella TLS
    // ni intenta evadir un control de seguridad deliberadamente, son cabeceras HTTP estándar
    // de navegación normal (Accept/Accept-Language/Referer) que ya formaban parte del User-
    // Agent de Chrome que este cliente YA declaraba desde antes.
    private static final String ACCEPT_HTML =
            "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8";
    private static final String ACCEPT_LANGUAGE = "es-CO,es;q=0.9,en;q=0.8";
    private static final Pattern FECHA_EN_TEXTO = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");
    private static final Pattern PATRON_RADICADO_CORTO_AJENO = Pattern.compile("\\b(19|20)\\d{2}\\s*[-.]\\s*\\d{2,6}\\b");
    private static final DateTimeFormatter FECHA_ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    private final String urlBase;
    private final String hostPermitido;
    private final int timeoutMs;
    // Freno de seguridad real, encontrado en vivo (pedido del usuario: "el sistema nunca
    // debe parar de buscar actualizaciones" -- al subir la frecuencia de este ciclo de una
    // vez al día a uno continuo, se encontró que Publicaciones Procesales a veces responde
    // "Read timed out" en TODAS las consultas seguidas, sin importar que el proceso sea
    // nuevo -- la misma clase de bloqueo/degradación ya documentada antes para este mismo
    // portal). Cuenta fallos de RED consecutivos (nunca "no encontré nada", eso es un
    // resultado válido, ver pareceCaido()) para que BoletinJudicialCacheService pueda cortar
    // el resto de la corrida en vez de seguir golpeando un portal que claramente no está
    // respondiendo durante la hora completa que tardaría terminar igual.
    private final AtomicInteger fallosConsecutivos = new AtomicInteger(0);
    // Subido de 5 a 10 el 2026-11-01 -- probado en vivo con curl (misma petición exacta, mismos
    // headers) contra los 7 despachos que cortaron el ciclo nocturno esa noche: los 7
    // respondieron bien después (HTTP 200, contenido real), 2 de 7 solo tardaron más de los
    // 15s que tenía el timeout entonces (ver timeout-ms, ya subido a 30000). Con el timeout más
    // generoso, una racha de 5 fallos de red reales (no solo lentitud) es menos probable, pero
    // seguía siendo un umbral bajo para una muestra de ~200 actuaciones por noche: 10 da más
    // margen ante una racha corta de mala suerte sin dejar de proteger contra un apagón real
    // sostenido del portal.
    private static final int UMBRAL_FALLOS_CONSECUTIVOS = 10;

    public PublicacionesProcesalesClient(
            @Value("${app.vigilancia.publicaciones-procesales.url-base}") String urlBase,
            @Value("${app.vigilancia.publicaciones-procesales.timeout-ms}") int timeoutMs) {
        this.urlBase = urlBase;
        this.hostPermitido = URI.create(urlBase).getHost();
        this.timeoutMs = timeoutMs;
    }

    // true si las últimas UMBRAL_FALLOS_CONSECUTIVOS consultas de RED (no "no encontré
    // nada", un fallo real de conexión/timeout) fallaron seguidas -- señal de que el portal
    // está caído o bloqueándonos ahora mismo, no de que estos radicados puntuales no tengan
    // boletín. Se reinicia solo en la siguiente consulta que sí logre conectarse,
    // encuentre o no algo (ver buscarUrlDetalleBoletin).
    public boolean pareceCaido() {
        return fallosConsecutivos.get() >= UMBRAL_FALLOS_CONSECUTIVOS;
    }

    // Sondeo liviano: UNA peticion a la pagina inicial del portal. Si responde, el contador de
    // fallos seguidos se reinicia y el ciclo puede reanudar; si no, se suma un fallo. Hallazgo
    // real del 2026-11-02: el portal estuvo caido ~15 min (23:59-00:14), el freno de seguridad
    // corto el recorrido en 70/270 y el resto quedo para otro ciclo, cuando bastaba esperar a
    // que volviera. Nunca lanza.
    // Auditoria 2026-11-02: antes solo pedia la pagina de inicio, que responde 200 en <1 s
    // aunque el BUSCADOR por despacho (lo unico que usa el ciclo) este colgado sin dar un
    // byte -- verificado en vivo con curl: inicio 200 en 0.8 s mientras 4 de 4 listados de
    // despacho agotaban 30-40 s. Resultado: el freno de seguridad creia que el portal habia
    // vuelto, reiniciaba el contador y el ciclo seguia golpeando un portal caido. Ahora la
    // sonda hace la MISMA consulta que el ciclo (listado de un despacho fijo, el mismo de
    // PortalSaludSonda) con timeout corto: solo cuenta como "vuelto" si ese listado responde.
    private static final String DESPACHO_SONDA = "110014189033";
    private static final int TIMEOUT_SONDA_MS = 15_000;

    public boolean sondearPortal() {
        try {
            peticionBase(construirUrlListadoDespacho(DESPACHO_SONDA)).timeout(TIMEOUT_SONDA_MS).get();
            fallosConsecutivos.set(0);
            return true;
        } catch (Exception ex) {
            log.debug("Sondeo del portal de Publicaciones Procesales sin respuesta: {}", ex.getMessage());
            return false;
        }
    }

    // idDespacho = los primeros 12 dígitos del radicado de 23 dígitos (depto+municipio+
    // entidad+especialidad+despacho). Verificado en vivo el 2026-10-30 contra el propio
    // desplegable de despachos del portal para 3 radicados reales distintos -- coincidió
    // exacto en los 3, pero no hay ninguna documentación oficial que lo confirme como regla
    // general, así que no hay garantía de que valga para absolutamente todos los despachos
    // del país.
    static String idDespachoDesdeRadicado(String radicado23Digitos) {
        // Un solo punto de normalización (ver RadicadoUtil) -- antes tenía su propia regex
        // local y era más insidioso que el mismo bug en RamaJudicialCpnuClient: nunca lanzaba
        // ni se veía en el log, idDespacho simplemente quedaba con espacios mezclados, un
        // despacho que no existe, sin boletines para siempre, en silencio.
        String limpio = RadicadoUtil.limpiar(radicado23Digitos);
        return limpio.length() >= 12 ? limpio.substring(0, 12) : null;
    }

    // Año (4 dígitos) y consecutivo (5 dígitos, con y sin ceros a la izquierda) del proceso,
    // derivados de las posiciones fijas del radicado de 23 dígitos (depto 2 + municipio 3 +
    // entidad 2 + especialidad 2 + despacho 3 + AÑO 4 + CONSECUTIVO 5 + recurso 2 = 23). Ver
    // el comentario de la clase para la evidencia real que confirma este cruce. null si el
    // radicado no tiene exactamente 23 dígitos (nunca se adivina con un formato distinto).
    record AnioConsecutivo(String anio, String consecutivoConCeros, String consecutivoSinCeros) {
    }

    static AnioConsecutivo anioYConsecutivoDesdeRadicado(String radicado23Digitos) {
        if (!RadicadoUtil.esFormatoCpnuValido(radicado23Digitos)) {
            return null;
        }
        String limpio = RadicadoUtil.limpiar(radicado23Digitos);
        String anio = limpio.substring(12, 16);
        String consecutivoConCeros = limpio.substring(16, 21);
        String consecutivoSinCeros = consecutivoConCeros.replaceFirst("^0+(?=\\d)", "");
        return new AnioConsecutivo(anio, consecutivoConCeros, consecutivoSinCeros);
    }

    // Busca, entre los boletines MÁS RECIENTES publicados por el despacho de ese radicado, el
    // que coincide con fechaPublicacionEsperada (normalmente la fechaRegistro de la
    // actuación, ver ActuacionJudicial), y devuelve SOLO los documentos de ese boletín que de
    // verdad son del proceso de este radicado (ver documentosDelBoletin). Lista vacía: no se
    // pudo determinar el despacho o el año/consecutivo del radicado, no se encontró ningún
    // boletín de esa fecha en la ventana reciente que expone el portal (ver limitación de la
    // clase), el proceso de este radicado no aparece en ese boletín, o falló la consulta.
    // nombreActuacionEsperado ya no se usa para filtrar (ver el comentario de la clase sobre
    // por qué la comparación por palabras clave se reemplazó), se deja el parámetro para no
    // tener que tocar a todos los llamadores -- puede quitarse si algún día molesta.
    public List<DocumentoProvidenciaCandidato> buscarDocumentos(
            String radicado23Digitos, LocalDate fechaPublicacionEsperada, String nombreActuacionEsperado) {
        return buscarDocumentosConEstado(radicado23Digitos, fechaPublicacionEsperada, nombreActuacionEsperado)
                .candidatos();
    }

    // incompleta=true: la busqueda NO pudo terminar (el portal fallo al listar el despacho, al
    // abrir el boletin, o no se pudo bajar un PDF para verificarlo), asi que "candidatos vacio"
    // NO significa "no hay boletin". Hallazgo real del ciclo del 2026-11-01: antes ese caso se
    // devolvia como lista vacia, indistinguible de "no hay nada", y el boletin se perdia sin
    // rastro. El ciclo automatico usa este metodo para saber que debe reintentar esa actuacion.
    public record ResultadoBusqueda(List<DocumentoProvidenciaCandidato> candidatos, boolean incompleta) {
    }

    public ResultadoBusqueda buscarDocumentosConEstado(
            String radicado23Digitos, LocalDate fechaPublicacionEsperada, String nombreActuacionEsperado) {
        String idDespacho = idDespachoDesdeRadicado(radicado23Digitos);
        AnioConsecutivo anioConsecutivo = anioYConsecutivoDesdeRadicado(radicado23Digitos);
        if (idDespacho == null || fechaPublicacionEsperada == null || anioConsecutivo == null) {
            return new ResultadoBusqueda(List.of(), false);
        }
        List<String> urlsDetalle;
        try {
            urlsDetalle = buscarUrlsDetalleBoletinRango(idDespacho, fechaPublicacionEsperada, 3);
        } catch (ConsultaBoletinFallidaException ex) {
            return new ResultadoBusqueda(List.of(), true);
        }
        if (urlsDetalle.isEmpty()) {
            return new ResultadoBusqueda(List.of(), false);
        }
        boolean[] incompleta = {false};
        java.util.LinkedHashMap<String, DocumentoProvidenciaCandidato> encontrados = new java.util.LinkedHashMap<>();
        for (String urlDet : urlsDetalle) {
            try {
                for (DocumentoProvidenciaCandidato doc : documentosDelBoletinConEstado(urlDet, anioConsecutivo, true, incompleta)) {
                    encontrados.putIfAbsent(doc.urlDescarga(), doc);
                }
            } catch (ConsultaBoletinFallidaException ex) {
                incompleta[0] = true;
                continue;
            }
            if (!encontrados.isEmpty()) {
                break;
            }
        }
        return new ResultadoBusqueda(new ArrayList<>(encontrados.values()), incompleta[0]);
    }

    // Escaneo profundo para Legal-Pro: consulta una sola vez el listado del despacho en Publicaciones
    // Procesales con timeout corto (4s) y presupuesto total de tiempo (4.5s) para nunca bloquear la UI
    // interactiva si el portal de la Rama Judicial está lento o colgado.
    public List<DocumentoProvidenciaCandidato> buscarTodosLosDocumentosRecientesDelProceso(
            String radicado23Digitos, java.util.Set<LocalDate> fechasEstadoDelCaso) {
        String idDespacho = idDespachoDesdeRadicado(radicado23Digitos);
        AnioConsecutivo anioConsecutivo = anioYConsecutivoDesdeRadicado(radicado23Digitos);
        if (idDespacho == null || anioConsecutivo == null) {
            return List.of();
        }
        String urlListado = construirUrlListadoDespacho(idDespacho);
        java.util.LinkedHashMap<String, DocumentoProvidenciaCandidato> porUrl = new java.util.LinkedHashMap<>();
        long inicioMs = System.currentTimeMillis();
        long presupuestoMaxMs = 4500L;
        try {
            Document doc = peticionBase(urlListado).timeout(3800).get();
            fallosConsecutivos.set(0);
            java.util.LinkedHashMap<String, Boolean> urlsDetalleConDescargaListado = new java.util.LinkedHashMap<>();
            for (Element tarjeta : doc.select("div.efecto")) {
                LocalDate fechaTarjeta = extraerFecha(tarjeta.select(".publish-date, .fecha-pub").text());
                Element enlaceDetalle = tarjeta.selectFirst("a[href]");
                if (enlaceDetalle == null) {
                    continue;
                }
                String href = enlaceDetalle.attr("abs:href");
                if (href.isBlank()) {
                    continue;
                }
                boolean coincideConFechaEstado = false;
                if (fechaTarjeta != null && fechasEstadoDelCaso != null) {
                    for (LocalDate fRef : fechasEstadoDelCaso) {
                        if (!fechaTarjeta.isBefore(fRef) && !fechaTarjeta.isAfter(fRef.plusDays(4))) {
                            coincideConFechaEstado = true;
                            break;
                        }
                    }
                }
                urlsDetalleConDescargaListado.merge(href, coincideConFechaEstado, (a, b) -> a || b);
            }

            Pattern patronNombreArchivo = Pattern.compile(
                    Pattern.quote(anioConsecutivo.anio()) + "\\s*[-.]?\\s*0*" + Pattern.quote(anioConsecutivo.consecutivoSinCeros()) + "\\b");

            int tarjetasRevisadas = 0;
            for (var entry : urlsDetalleConDescargaListado.entrySet()) {
                if (tarjetasRevisadas >= 4 || (System.currentTimeMillis() - inicioMs) >= presupuestoMaxMs) {
                    break;
                }
                tarjetasRevisadas++;
                try {
                    Document docDet = peticionBase(entry.getKey()).timeout(2800).get();
                    boolean incluirListadosGenerales = Boolean.TRUE.equals(entry.getValue());
                    for (Element enlace : docDet.select("a[href]")) {
                        String nombreArchivo = enlace.text().strip();
                        if (!nombreArchivo.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                            continue;
                        }
                        String urlDescarga = enlace.attr("abs:href");
                        if (urlDescarga.isBlank()) {
                            continue;
                        }
                        boolean esDocumentoIndividual = patronNombreArchivo.matcher(nombreArchivo).find();
                        if (esDocumentoIndividual) {
                            porUrl.putIfAbsent(urlDescarga, new DocumentoProvidenciaCandidato(nombreArchivo, urlDescarga, true));
                        } else if (incluirListadosGenerales && !PATRON_RADICADO_CORTO_AJENO.matcher(nombreArchivo).find()) {
                            porUrl.putIfAbsent(urlDescarga, new DocumentoProvidenciaCandidato(nombreArchivo, urlDescarga, false));
                        }
                    }
                } catch (Exception ignored) {
                    // Si una tarjeta puntual tarda más de 2.8s, pasamos a la siguiente sin bloquear
                }
            }
        } catch (Exception ex) {
            log.warn("Legal-Pro: no se pudo escanear Publicaciones Procesales para despacho {}: {}", idDespacho, ex.getMessage());
        }
        return new ArrayList<>(porUrl.values());
    }

    public String construirUrlListadoDespacho(String idDespachoORadicado) {
        String idDespacho = idDespachoORadicado != null && idDespachoORadicado.length() > 12
                ? idDespachoDesdeRadicado(idDespachoORadicado)
                : idDespachoORadicado;
        if (idDespacho == null || idDespacho.isBlank()) {
            return urlBase + "/web/publicaciones-procesales/inicio";
        }
        return urlBase + "/web/publicaciones-procesales/inicio"
                + "?p_p_id=co_com_avanti_efectosProcesales_PublicacionesEfectosProcesalesPortletV2_INSTANCE_BIyXQFHVaYaq"
                + "&p_p_lifecycle=0&p_p_state=normal&p_p_mode=view"
                + "&" + PORTLET_NS + "_action=busqueda"
                + "&" + PORTLET_NS + "_idDespacho=" + idDespacho
                + "&" + PORTLET_NS + "_verTotales=true";
    }

    // Factorizado de buscarUrlDetalleBoletin()/documentosDelBoletin() -- mismo patrón de
    // petición en los dos. Un solo reintento, encontrado necesario en vivo (2026-11-01,
    // diagnóstico con curl -v): el portal a veces completa la conexión TCP y el handshake
    // TLS por completo (certificado real recibido) pero nunca manda ni un byte de respuesta
    // -- ni en 25s ni en 45s, un cuelgue real del gateway, no solo lentitud (eso ya lo cubre
    // timeoutMs, ver su comentario en application.properties). Un segundo intento, tras una
    // pausa corta, suele conectar a una instancia distinta del gateway y responder normal --
    // absorbe esa clase de fallo puntual sin gastar un fallo consecutivo real del circuit
    // breaker (ver pareceCaido()) por una sola mala suerte aislada, reservando ese contador
    // para un apagón genuino y sostenido del portal.
    private Document obtenerDocumentoConReintento(String url) throws IOException {
        try {
            return peticionBase(url).get();
        } catch (IOException primerIntento) {
            pausarReintento();
            return peticionBase(url).get();
        }
    }

    private Connection peticionBase(String url) {
        // "Connection: close" en cada petición es intencional, no cosmético: se confirmó en
        // vivo (2026-10-31) que el proceso backend, ya con varias horas de uptime, fallaba
        // con "Read timed out" en CASI TODAS las consultas a este portal -- mientras que un
        // proceso Java nuevo, en el mismo instante y contra el mismo host, respondía bien en
        // 1-2s. El patrón (siempre falla en el proceso viejo, nunca en uno nuevo) apunta a
        // una conexión keep-alive reutilizada del pool interno de la JVM que el gateway de
        // Azure del portal ya cerró del lado del servidor sin avisar (idle timeout
        // silencioso) -- la JVM la sigue creyendo viva y se queda esperando una respuesta que
        // nunca llega hasta el timeout de lectura. Forzar una conexión nueva en cada llamada
        // evita esa clase de problema por completo; el costo de un handshake TLS extra es
        // insignificante frente al ritmo ya pausado de este cliente.
        return Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .header("Connection", "close")
                .header("Accept", ACCEPT_HTML)
                .header("Accept-Language", ACCEPT_LANGUAGE)
                .header("Upgrade-Insecure-Requests", "1")
                .referrer(urlBase + "/web/publicaciones-procesales/inicio")
                .timeout(timeoutMs);
    }

    private static void pausarReintento() {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private List<String> buscarUrlsDetalleBoletinRango(String idDespacho, LocalDate fechaEsperada, int diasMargenPosterior) {
        String urlListado = construirUrlListadoDespacho(idDespacho);
        try {
            Document doc = obtenerDocumentoConReintento(urlListado);
            fallosConsecutivos.set(0);
            List<String> exactas = new ArrayList<>();
            List<String> cercanas = new ArrayList<>();
            LocalDate fechaMax = fechaEsperada.plusDays(Math.max(0, diasMargenPosterior));
            for (Element tarjeta : doc.select("div.efecto")) {
                LocalDate fechaTarjeta = extraerFecha(tarjeta.select(".publish-date, .fecha-pub").text());
                if (fechaTarjeta == null) {
                    continue;
                }
                Element enlaceDetalle = tarjeta.selectFirst("a[href]");
                if (enlaceDetalle == null) {
                    continue;
                }
                String href = enlaceDetalle.attr("abs:href");
                if (href.isBlank()) {
                    continue;
                }
                if (fechaTarjeta.equals(fechaEsperada)) {
                    exactas.add(href);
                } else if (!fechaTarjeta.isBefore(fechaEsperada) && !fechaTarjeta.isAfter(fechaMax)) {
                    cercanas.add(href);
                }
            }
            List<String> resultado = new ArrayList<>(exactas);
            for (String c : cercanas) {
                if (!resultado.contains(c)) {
                    resultado.add(c);
                }
            }
            return resultado;
        } catch (Exception ex) {
            log.warn("No se pudo consultar Publicaciones Procesales (despacho {}): {}", idDespacho, ex.getMessage());
            fallosConsecutivos.incrementAndGet();
            throw new ConsultaBoletinFallidaException(
                    "Fallo consultando el listado del despacho " + idDespacho, ex);
        }
    }

    // Comportamiento historico intacto (lo usa tambien el escaneo profundo de Legal-Pro): ante
    // cualquier fallo devuelve lista vacia. El ciclo automatico usa documentosDelBoletinConEstado.
    private List<DocumentoProvidenciaCandidato> documentosDelBoletin(
            String urlDetalle, AnioConsecutivo anioConsecutivo, boolean verificarListadosGenerales) {
        try {
            return documentosDelBoletinConEstado(urlDetalle, anioConsecutivo, verificarListadosGenerales, new boolean[1]);
        } catch (ConsultaBoletinFallidaException ex) {
            return List.of();
        }
    }

    // Igual que documentosDelBoletin, pero sin esconder los fallos: si la pagina del boletin no
    // se puede abrir lanza ConsultaBoletinFallidaException, y si un PDF de verificacion no se
    // pudo bajar (red) marca incompleta[0]=true en vez de darlo por "no coincide" -- bug real
    // del ciclo del 2026-11-01: esos PDFs se descartaban en silencio como si no contuvieran el
    // proceso, y el boletin correcto se perdia.
    private List<DocumentoProvidenciaCandidato> documentosDelBoletinConEstado(
            String urlDetalle, AnioConsecutivo anioConsecutivo, boolean verificarListadosGenerales,
            boolean[] incompleta) {
        try {
            Document doc = obtenerDocumentoConReintento(urlDetalle);
            Pattern patronNombreArchivo = Pattern.compile(
                    Pattern.quote(anioConsecutivo.anio()) + "\\s*[-.]?\\s*0*" + Pattern.quote(anioConsecutivo.consecutivoSinCeros()) + "\\b");
            Pattern patronTextoListado = Pattern.compile(
                    Pattern.quote(anioConsecutivo.anio()) + "[\\s\\-./]+0*" + Pattern.quote(anioConsecutivo.consecutivoSinCeros()) + "\\b");
            List<DocumentoProvidenciaCandidato> resultado = new ArrayList<>();
            for (Element enlace : doc.select("a[href]")) {
                String nombreArchivo = enlace.text().strip();
                if (!nombreArchivo.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
                    continue;
                }
                String urlDescarga = enlace.attr("abs:href");
                boolean esDocumentoIndividual = patronNombreArchivo.matcher(nombreArchivo).find();
                boolean verificado = esDocumentoIndividual;
                if (!verificado && verificarListadosGenerales) {
                    // Si el nombre del archivo ya contiene el año-consecutivo de OTRO proceso distinto
                    // (ej. "2018-00723. Liq patri.pdf"), se descarta al instante sin descargarlo.
                    // Solo descargamos el PDF para inspeccionar su texto interno cuando es un listado
                    // general de estado ("estado 170...pdf", "LISTADO ESTADO...pdf") sin radicado ajeno.
                    if (PATRON_RADICADO_CORTO_AJENO.matcher(nombreArchivo).find()) {
                        continue;
                    }
                    try {
                        verificado = extraerTexto(descargarDocumentoOExcepcion(urlDescarga, INTENTOS_DESCARGA_CICLO))
                                .map(t -> patronTextoListado.matcher(t).find())
                                .orElse(false);
                    } catch (DescargaDocumentoFallidaException ex) {
                        // 404/URL invalida: el PDF no existe, "no coincide" es la respuesta
                        // correcta. Fallo de red: no se sabe -> la busqueda queda incompleta.
                        if (ex.isReintentable()) {
                            incompleta[0] = true;
                        }
                        verificado = false;
                    }
                }
                if (verificado) {
                    resultado.add(new DocumentoProvidenciaCandidato(nombreArchivo, urlDescarga, esDocumentoIndividual));
                }
            }
            return resultado;
        } catch (Exception ex) {
            log.warn("No se pudo leer el boletín de Publicaciones Procesales en {}: {}", urlDetalle, ex.getMessage());
            throw new ConsultaBoletinFallidaException("Fallo leyendo el boletin " + urlDetalle, ex);
        }
    }

    private static LocalDate extraerFecha(String texto) {
        if (texto == null) {
            return null;
        }
        Matcher m = FECHA_EN_TEXTO.matcher(texto);
        if (!m.find()) {
            return null;
        }
        try {
            return LocalDate.parse(m.group(1), FECHA_ISO);
        } catch (Exception ex) {
            return null;
        }
    }

    // Descarga el PDF de un candidato ya devuelto por buscarDocumentos() o por CPNU directo.
    // Valida que el host sea oficial de la Rama Judicial (publicacionesprocesales.ramajudicial.gov.co
    // o consultaprocesos.ramajudicial.gov.co).
    public Optional<byte[]> descargarDocumento(String url) {
        // Llamadores interactivos (panel, Croma, envio manual): 2 intentos, para no dejar al
        // usuario esperando varios minutos; el ciclo automatico usa mas (ver abajo).
        try {
            return Optional.of(descargarDocumentoOExcepcion(url, 2));
        } catch (DescargaDocumentoFallidaException ex) {
            return Optional.empty();
        }
    }

    // Intentos con que el ciclo automatico baja (y verifica) un PDF antes de darlo por fallido
    // y dejarlo en la cola de pendientes. Espera creciente entre intentos: 3s, 6s, 9s.
    static final int INTENTOS_DESCARGA_CICLO = 4;

    // Descarga con reintentos y SIN esconder el fallo: lanza DescargaDocumentoFallidaException
    // (reintentable=true si fue red/timeout/5xx; false si la URL no es valida o el documento no
    // existe). Hallazgo real del ciclo del 2026-11-01: 68 PDFs ya localizados se perdieron
    // porque una racha de conexiones abortadas (66 "getsockopt") duro una hora y la descarga
    // se intentaba una sola vez y devolvia Optional.empty() sin dejar rastro.
    public byte[] descargarDocumentoOExcepcion(String url, int intentosMaximos) {
        if (url == null || url.isBlank()) {
            throw new DescargaDocumentoFallidaException(url, false, "URL vacia", null);
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (Exception ex) {
            throw new DescargaDocumentoFallidaException(url, false, "URL invalida", ex);
        }
        String host = uri.getHost();
        boolean hostValido = host != null && (
                hostPermitido.equalsIgnoreCase(host)
                || "consultaprocesos.ramajudicial.gov.co".equalsIgnoreCase(host)
        );
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !hostValido) {
            log.warn("Se rechazó la descarga de un documento judicial con host no permitido: {}", host);
            throw new DescargaDocumentoFallidaException(url, false, "Host no permitido: " + host, null);
        }
        DescargaDocumentoFallidaException ultimo = null;
        for (int intento = 1; intento <= Math.max(1, intentosMaximos); intento++) {
            try {
                return descargarUnaVez(url, uri);
            } catch (DescargaDocumentoFallidaException ex) {
                if (!ex.isReintentable()) {
                    log.warn("No se pudo descargar el documento judicial {}: {}", url, ex.getMessage());
                    throw ex;
                }
                ultimo = ex;
                if (intento < intentosMaximos) {
                    log.debug("Descarga fallida (intento {}/{}) de {}: {}", intento, intentosMaximos, url, ex.getMessage());
                    try {
                        Thread.sleep(3000L * intento);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        log.warn("No se pudo descargar el documento judicial {} tras {} intento(s): {}", url, intentosMaximos,
                ultimo != null ? ultimo.getMessage() : "interrumpido");
        throw new DescargaDocumentoFallidaException(url, true,
                ultimo != null ? ultimo.getMessage() : "Descarga interrumpida", ultimo);
    }

    private byte[] descargarUnaVez(String url, URI uri) {
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/pdf,*/*;q=0.8")
                    .header("Accept-Language", ACCEPT_LANGUAGE)
                    .header("Referer", urlBase + "/web/publicaciones-procesales/inicio")
                    .GET()
                    .build();
            // "Connection" es un header restringido en java.net.http (no se puede fijar con
            // .header(), lanza IllegalArgumentException) -- por eso aquí se evita reutilizar
            // this.httpClient (un único HttpClient para toda la vida del proceso, con su
            // propio pool interno de conexiones) y se construye uno nuevo por descarga. Mismo
            // motivo que el "Connection: close" de Jsoup arriba: una conexión keep-alive que
            // el gateway del portal ya cerró en silencio deja la JVM esperando una respuesta
            // que nunca llega. Esta llamada ya está limitada por el ritmo pausado del resto
            // del cliente, así que el costo de un HttpClient nuevo por descarga es marginal.
            HttpClient clienteDeUnSoloUso = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(timeoutMs))
                    .build();
            // Bug real confirmado en vivo (2026-11-01, thread dump con jstack): esta llamada
            // se quedó colgada 3h24min en HttpClientImpl.send() -- el .timeout(...) del
            // HttpRequest de arriba NO se cumplió. java.net.http.HttpClient tiene un
            // comportamiento conocido donde ese timeout no siempre corta una conexión que el
            // servidor deja medio abierta (ni manda más datos ni la cierra) -- exactamente el
            // mismo patrón de gateway degradado ya documentado en esta clase. .sendAsync() +
            // orTimeout() SÍ cancela de verdad: no depende de que el propio HttpClient cumpla
            // su temporizador interno, solo de que este CompletableFuture expire.
            HttpResponse<byte[]> response;
            try {
                response = clienteDeUnSoloUso.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray())
                        .orTimeout(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                        .get();
            } catch (java.util.concurrent.ExecutionException ex) {
                throw ex.getCause() instanceof Exception causa ? causa : ex;
            }
            int estado = response.statusCode();
            if (estado != 200) {
                log.warn("Publicaciones Procesales respondió HTTP {} al descargar {}", estado, url);
                // 5xx/408/429: el portal esta sobrecargado o limitando -> vale reintentar.
                // 404/403/otros 4xx: el documento no esta o no se puede bajar -> no insistir.
                boolean transitorio = estado >= 500 || estado == 408 || estado == 429;
                throw new DescargaDocumentoFallidaException(url, transitorio, "HTTP " + estado, null);
            }
            return response.body();
        } catch (DescargaDocumentoFallidaException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new DescargaDocumentoFallidaException(url, false, "Descarga interrumpida", ex);
        } catch (Exception ex) {
            String causa = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            throw new DescargaDocumentoFallidaException(url, true, causa, ex);
        }
    }

    // Pedido explícito del usuario: el texto que trae la Rama Judicial en el campo
    // "anotacion" de una actuación (ver RamaJudicialCpnuClient) suele ser un apunte interno
    // corto del despacho ("DESCORRE", "MEMORIAL PODER"), NO el texto real del Estado
    // Electrónico -- confirmado en vivo con datos reales el 2026-10-31. El texto completo tal
    // como lo publica el despacho solo existe dentro del PDF oficial. Este método descarga
    // (mismo host validado que descargarDocumento()) y extrae el texto plano con PDFBox, para
    // mostrar el contenido verbatim en vez de depender del resumen corto del CPNU.
    //
    // A propósito solo se llama bajo demanda, sobre UN documento puntual que el abogado ya
    // eligió de la lista de candidatos (nunca automático, nunca para toda la línea de tiempo
    // de una vez) -- extraer texto de cada PDF de cada actuación de los 235 casos reales
    // multiplicaría la carga contra Publicaciones Procesales justo después del bloqueo HTTP
    // 403 real de esta misma sesión.
    public Optional<String> descargarYExtraerTexto(String url) {
        return descargarDocumento(url).flatMap(PublicacionesProcesalesClient::extraerTexto);
    }

    // Factorizado de descargarYExtraerTexto() -- también lo usa documentosDelBoletin() para
    // verificar si el proceso de un radicado aparece dentro de un LISTADO (ver ahí).
    private static Optional<String> extraerTexto(byte[] pdf) {
        try (PDDocument documento = Loader.loadPDF(pdf)) {
            String texto = new PDFTextStripper().getText(documento);
            return texto == null || texto.isBlank() ? Optional.empty() : Optional.of(texto.strip());
        } catch (Exception ex) {
            log.warn("No se pudo extraer el texto de un documento judicial: {}", ex.getMessage());
            return Optional.empty();
        }
    }
}
