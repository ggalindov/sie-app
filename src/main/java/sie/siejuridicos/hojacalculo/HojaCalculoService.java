package sie.siejuridicos.hojacalculo;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.GoogleCredentials;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import sie.siejuridicos.caso.FuenteCaso;
import sie.siejuridicos.common.exception.HojaCalculoNoDisponibleException;
import sie.siejuridicos.hojacalculo.dto.FilaCasoHoja;
import sie.siejuridicos.hojacalculo.dto.FilaSincronizacionHoja;
import sie.siejuridicos.hojacalculo.dto.ResultadoSincronizacionHoja;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

// Lectura EN VIVO y de SOLO LECTURA (SheetsScopes.SPREADSHEETS_READONLY, fijo en el código,
// sin alternativa) del Google Sheets donde la firma lleva el seguimiento real de sus casos.
// Este servicio nunca escribe nada en la hoja.
//
// No hay caché: el requisito explícito es que un cambio en la hoja se refleje "apenas se
// actualice" en la consulta pública, y el endpoint que consume esto ya tiene rate limit (ver
// RateLimitFilter, /api/casos/consulta) que acota el volumen de llamadas.
//
// Nunca se loguea el contenido de una fila (puede tener información sensible del cliente):
// solo se loguea el radicado buscado (el propio cliente ya lo conoce, se lo enviamos por
// correo) y errores operativos, nunca a nivel INFO.
//
// Tres pestañas soportadas (ver FuenteCaso), cada una con su propia estructura de columnas
// -- confirmadas leyendo la fila de encabezados real de cada una, no adivinadas:
// - JUDICIALES: encabezados en la fila 6, datos desde la 7. B "NO.", D "DESPACHO JUDICIAL",
//   E "PARTES DEL PROCESO", F "TIPO", H "SUJETO PROCESAL REPRESENTADO", I "RADICADO",
//   J "ULTIMA DECISIÓN", K "ESTADO", M "REVISADO" (se usa como fecha de actualización: en
//   la práctica siempre trae una fecha real, pese al nombre de la columna),
//   T "CORREO DEL CLIENTE", U "TELÉFONO DEL CLIENTE".
// - SUPERINTENDENCIA: encabezados en la fila 3, datos desde la 4. A "NO.",
//   B "NOMBRE SUPERINTENDECIA", D "RADICADO", F "DEMANDANTE" (also el nombre del cliente
//   para personalizar el correo), I "ULTIMO ESTADO", J "FECHA DE REVISIÓN",
//   L "CORREO DEL CLIENTE", M "TELÉFONO DEL CLIENTE".
// - PROCESOS COMISARIA-: encabezados en la fila 2, datos desde la 3. NO tiene columna de
//   número de caso (a diferencia de las otras dos) -- se usa una huella de contenido como
//   llave sintética de sincronización (ver listarParaSincronizar/huellaContenido).
//   A "DESPACHO JUDICIAL", B "PARTES DEL PROCESO", C "TIPO", E "SUJETO PROCESAL REPRESENTADO", F "RADICADO",
//   G "ULTIMA DECISIÓN", Q "CORREO DEL CLIENTE", R "TELÉFONO DEL CLIENTE".
@Service
public class HojaCalculoService {

    private static final Logger log = LoggerFactory.getLogger(HojaCalculoService.class);

    // idxNumeroCaso == null significa "esta fuente no tiene columna de número de caso": se usa
    // una huella de contenido (despacho + nombre del cliente) como llave sintética en su lugar
    // (ver listarParaSincronizar/huellaContenido). Cualquier
    // otro índice == null significa "esta fuente no tiene ese dato" (ej. SUPERINTENDENCIA no
    // tiene un "tipo de caso" separado): el campo correspondiente queda en null, tanto en la
    // consulta pública como en la sincronización, en vez de forzar un valor que no existe.
    private record ConfiguracionFuente(
            FuenteCaso fuente,
            String rango,
            Integer idxNumeroCaso,
            int idxDespacho,
            int idxInformacionCaso,
            Integer idxTipoCaso,
            Integer idxUltimaDecision,
            Integer idxEstado,
            Integer idxFechaActualizacion,
            int idxRadicado,
            Integer idxNombreCliente,
            int idxCorreoCliente,
            int idxTelefonoCliente
    ) {
    }

    private static final Map<FuenteCaso, ConfiguracionFuente> CONFIGURACIONES = construirConfiguraciones();

    private static Map<FuenteCaso, ConfiguracionFuente> construirConfiguraciones() {
        Map<FuenteCaso, ConfiguracionFuente> mapa = new EnumMap<>(FuenteCaso.class);
        mapa.put(FuenteCaso.JUDICIALES, new ConfiguracionFuente(
                FuenteCaso.JUDICIALES, "JUDICIALES!B7:U",
                0, 2, 3, 4, 8, 9, 11, 7, 6, 18, 19));
        // idxNumeroCaso=null a propósito (bug real encontrado con datos reales, ver comentario
        // de tieneContenidoReal()): la columna "NO." de esta hoja NO es confiable -- se repite
        // dentro de un mismo bloque, se reinicia varias veces entre bloques de distintas
        // entidades (Industria y Comercio, Economía Solidaria, Financiera, Salud, Servicios
        // Públicos...), y varias filas de casos reales la traen simplemente en blanco. La llave
        // real usada es huellaContenido() (despacho + nombre del cliente/demandante, ver
        // listarParaSincronizar) -- ni la columna "NO." ni la posición física de la fila, así
        // que ningún caso real se pierde, se fusiona con otro, ni se cruza con el de un cliente
        // distinto si la firma inserta una fila nueva en medio de un bloque existente.
        mapa.put(FuenteCaso.SUPERINTENDENCIA, new ConfiguracionFuente(
                FuenteCaso.SUPERINTENDENCIA, "SUPERINTENDENCIA!A4:M",
                null, 1, 5, null, null, 8, 9, 3, 5, 11, 12));
        mapa.put(FuenteCaso.PROCESOS_COMISARIA, new ConfiguracionFuente(
                FuenteCaso.PROCESOS_COMISARIA, "'PROCESOS COMISARIA-'!A3:R",
                null, 0, 1, 2, 6, null, null, 5, 4, 16, 17));
        return mapa;
    }

    private final String spreadsheetId;
    private final Sheets sheets;

    // @Autowired explícito, necesario: en cuanto una segunda constructora aparece en la clase
    // (ver la de abajo, solo para pruebas), Spring deja de poder inferir sola cuál usar para
    // inyección de dependencias -- sin esto, el arranque completo de la aplicación falla con
    // "No default constructor found" (bug real encontrado en esta auditoría, confirmado
    // corriendo la suite de pruebas de integración contra una base de datos real).
    @Autowired
    public HojaCalculoService(
            @Value("${app.hoja-calculo.spreadsheet-id}") String spreadsheetId,
            @Value("${app.hoja-calculo.credenciales-path}") String credencialesPath) {
        this.spreadsheetId = spreadsheetId;
        this.sheets = construirCliente(spreadsheetId, credencialesPath);
    }

    // Constructor de prueba (paquete), mismo patrón que HojaCobrosService: inyecta un cliente
    // Sheets ya construido (mock) para que HojaCalculoServiceTest pueda probar la lógica de
    // búsqueda por radicado (y que nunca cruce fuentes/columnas) sin depender de la API real
    // de Google ni de un archivo de credenciales.
    HojaCalculoService(String spreadsheetId, Sheets sheets) {
        this.spreadsheetId = spreadsheetId;
        this.sheets = sheets;
    }

    private static Sheets construirCliente(String spreadsheetId, String credencialesPath) {
        if (spreadsheetId.isBlank() || credencialesPath.isBlank()) {
            log.warn("Google Sheets no configurado (faltan app.hoja-calculo.spreadsheet-id / "
                    + "credenciales-path): la consulta de estado de casos responderá 'servicio no "
                    + "disponible' hasta que se configuren GOOGLE_SHEETS_ID y "
                    + "GOOGLE_SHEETS_CREDENTIALS_PATH.");
            return null;
        }
        try (InputStream credencialesJson = new FileInputStream(credencialesPath)) {
            GoogleCredentials credenciales = GoogleCredentials.fromStream(credencialesJson)
                    .createScoped(List.of(SheetsScopes.SPREADSHEETS_READONLY));
            NetHttpTransport transporte = GoogleNetHttpTransport.newTrustedTransport();
            HttpCredentialsAdapter adaptadorCredenciales = new HttpCredentialsAdapter(credenciales);
            // Disponibilidad: sin esto, el cliente usa los timeouts por defecto de la
            // librería (20s conectar / 20s leer) -- suficiente para colgar un hilo de Tomcat
            // más tiempo del razonable si Google responde lento. El de lectura queda en 15s
            // (no 8s): la sincronización masiva trae TODAS las filas de TRES hojas, una
            // respuesta más pesada que una consulta de una sola fila.
            HttpRequestInitializer inicializador = solicitud -> {
                adaptadorCredenciales.initialize(solicitud);
                solicitud.setConnectTimeout(5_000);
                solicitud.setReadTimeout(15_000);
            };
            return new Sheets.Builder(transporte, GsonFactory.getDefaultInstance(), inicializador)
                    .setApplicationName("sie-juridicos")
                    .build();
        } catch (IOException | GeneralSecurityException ex) {
            log.error("No se pudo inicializar el cliente de Google Sheets (revisa "
                    + "GOOGLE_SHEETS_CREDENTIALS_PATH y que el archivo sea una llave JSON válida "
                    + "de una cuenta de servicio): {}", ex.getMessage(), ex);
            return null;
        }
    }

    // Optional.empty() = no existe esa fila TODAVÍA en la hoja (no es un error: el caso puede
    // estar recién registrado en nuestro sistema y la firma aún no lo cargó a la hoja). Lanza
    // HojaCalculoNoDisponibleException solo ante una falla operativa real (sin configurar, sin
    // acceso, red, cuota).
    //
    // La fuente la decide el llamador (CasoService.consultar()) a partir del Caso local ya
    // encontrado -- nunca el propio cliente que consulta: así siempre se busca en la pestaña
    // correcta sin exponer de qué hoja interna viene cada radicado.
    public Optional<FilaCasoHoja> buscarPorRadicado(FuenteCaso fuente, String radicadoId) {
        ConfiguracionFuente config = CONFIGURACIONES.get(fuente);
        if (config == null) {
            // Un Caso con fuente MANUAL (o cualquiera sin configuración de hoja) nunca
            // debería llegar aquí -- CasoService.consultar() lo filtra antes.
            return Optional.empty();
        }
        List<List<Object>> filas = leerRango(config.rango(), radicadoId);
        String buscado = radicadoId.strip();
        for (List<Object> fila : filas) {
            if (valorEn(fila, config.idxRadicado()).equalsIgnoreCase(buscado)) {
                return Optional.of(new FilaCasoHoja(
                        valorEn(fila, config.idxDespacho()),
                        valorEn(fila, config.idxInformacionCaso()),
                        config.idxTipoCaso() != null ? valorEn(fila, config.idxTipoCaso()) : null,
                        config.idxUltimaDecision() != null ? valorEn(fila, config.idxUltimaDecision()) : null,
                        config.idxEstado() != null ? valorEn(fila, config.idxEstado()) : null,
                        config.idxFechaActualizacion() != null ? valorEn(fila, config.idxFechaActualizacion()) : null
                ));
            }
        }
        return Optional.empty();
    }

    // Trae TODAS las filas de las TRES hojas, para que CasoService.sincronizarDesdeHoja()
    // decida cuáles son nuevas y cuáles ya existen localmente (por fuente + número de caso).
    // Un fallo leyendo una hoja puntual (red, permisos de esa pestaña) NO aborta las demás:
    // son tres llamadas HTTP independientes, se sincroniza lo que sí se pudo leer y se
    // reportan las fuentes que fallaron para que el admin sepa que esas quedaron pendientes.
    public ResultadoSincronizacionHoja listarParaSincronizar() {
        List<FilaSincronizacionHoja> resultado = new ArrayList<>();
        List<FuenteCaso> fuentesConError = new ArrayList<>();
        for (ConfiguracionFuente config : CONFIGURACIONES.values()) {
            try {
                resultado.addAll(listarParaSincronizar(config));
            } catch (HojaCalculoNoDisponibleException ex) {
                fuentesConError.add(config.fuente());
            }
        }
        return new ResultadoSincronizacionHoja(resultado, fuentesConError);
    }

    private List<FilaSincronizacionHoja> listarParaSincronizar(ConfiguracionFuente config) {
        List<List<Object>> filas = leerRango(config.rango(), null);
        List<FilaSincronizacionHoja> resultado = new ArrayList<>();
        int numeroFila = 1;
        for (List<Object> fila : filas) {
            String numeroCaso;
            if (config.idxNumeroCaso() != null) {
                String valor = valorEn(fila, config.idxNumeroCaso());
                if (valor.isBlank()) {
                    // Sin número de caso en una fuente que sí tiene esa columna: no es un
                    // caso real todavía (fila en blanco intermedia, o de notas/totales al
                    // final de la hoja).
                    numeroFila++;
                    continue;
                }
                numeroCaso = valor;
            } else {
                // Esta fuente (Procesos Comisaría, o Superintendencia -- ver comentario en
                // construirConfiguraciones()) no usa una columna de "NO." como llave. Se filtran
                // aquí mismo las filas realmente vacías (ver tieneContenidoReal) para no crear
                // un caso fantasma a partir de una fila de separación/nota entre bloques.
                if (!tieneContenidoReal(fila, config)) {
                    numeroFila++;
                    continue;
                }
                // Bug real encontrado con datos reales, corregido en esta auditoría: ANTES la
                // llave era "fila-" + número de fila física, estable solo "mientras la hoja
                // crezca agregando filas al final, no reordenando/insertando en medio". Esa
                // condición es falsa en la práctica: la hoja de Superintendencia está organizada
                // por bloques de entidad (Industria y Comercio, Economía Solidaria, Financiera,
                // Salud, Servicios Públicos...) y la firma sí inserta filas nuevas en medio de un
                // bloque existente, no solo al final. Cada inserción corre una posición todas las
                // filas de abajo, así que en la siguiente sincronización "fila-15" dejaba de ser
                // la misma fila real de antes -- el Caso ya guardado con esa llave se actualizaba
                // en silencio con los datos de OTRA fila (en el peor caso, el radicado de un
                // cliente distinto quedaba asignado al Caso equivocado: exactamente el cruce de
                // información entre clientes que el sistema no puede permitir bajo ninguna
                // causa). huellaContenido() usa el CONTENIDO de la fila (despacho + nombre del
                // cliente/demandante), no su posición, así que insertar o borrar filas en
                // cualquier parte de la hoja ya no afecta la llave de las demás.
                numeroCaso = huellaContenido(
                        valorEn(fila, config.idxDespacho()),
                        config.idxNombreCliente() != null ? valorEn(fila, config.idxNombreCliente()) : "");
            }
            numeroFila++;

            resultado.add(new FilaSincronizacionHoja(
                    config.fuente(),
                    numeroCaso,
                    radicadoValidoOVacio(fila, config.idxRadicado()),
                    config.idxNombreCliente() != null ? valorEn(fila, config.idxNombreCliente()) : null,
                    valorNuloSiVacio(fila, config.idxCorreoCliente()),
                    valorNuloSiVacio(fila, config.idxTelefonoCliente())
            ));
        }
        // Se desambigua SIEMPRE, no solo en fuentes con columna "NO." real: la huella de
        // contenido (huellaContenido) también puede colisionar -- dos casos reales distintos que
        // por coincidencia comparten despacho Y nombre de cliente -- y necesita el mismo sufijo
        // "-2", "-3"... para no fusionarse en un solo Caso (ver desambiguarNumerosDuplicados).
        return desambiguarNumerosDuplicados(resultado);
    }

    // Huella determinística de identidad para fuentes sin columna de "NO." confiable (ver
    // arriba). Usa SOLO despacho + nombre del cliente/demandante -- datos estructurales fijos
    // desde que la fila se crea -- nunca radicado ni correo, que a propósito empiezan vacíos y
    // se completan en una sincronización posterior (ver sincronizarDesdeHoja): si se incluyeran
    // en la huella, completarlos más adelante cambiaría la llave y "perdería" el caso existente
    // (se borraría por reconciliación y se crearía uno nuevo) en vez de actualizarlo en el
    // mismo Caso, que es el comportamiento que sincronizarDesdeHoja espera.
    //
    // SHA-256 truncado a 16 caracteres hex (64 bits) hace una colisión accidental entre dos
    // casos reales distintos prácticamente imposible al volumen de casos de una firma de
    // abogados. El raro caso de colisión real (o de una fila sin despacho ni nombre
    // identificable, solo radicado/correo) igual queda cubierto de forma segura por
    // desambiguarNumerosDuplicados(), que ahora se aplica siempre.
    //
    // Nunca se loguea el texto de entrada (puede tener nombres de clientes reales) -- misma
    // regla que el resto de esta clase.
    private static String huellaContenido(String despacho, String identidad) {
        String normalizado = normalizarTexto(despacho) + "␟" + normalizarTexto(identidad);
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] digest = sha256.digest(normalizado.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                hex.append(String.format("%02x", digest[i]));
            }
            return "h-" + hex;
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 es un algoritmo estándar garantizado por el JDK (JEP 176 / especificación
            // de Java Cryptography Architecture): esta rama es inalcanzable en la práctica.
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", ex);
        }
    }

    private static String normalizarTexto(String texto) {
        return texto == null ? "" : texto.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    // Bug real encontrado con datos reales: la columna "NO." de la hoja de la firma NO es un
    // identificador único de verdad. En JUDICIALES se repite puntualmente (casos distintos,
    // con despachos y radicados distintos, que por error de digitación quedaron con el mismo
    // número). En SUPERINTENDENCIA la numeración se REINICIA varias veces dentro de la misma
    // hoja -- confirmado: de 23 filas con datos, solo 11 números eran únicos, es decir, más de
    // la mitad de los casos reales de Superintendencia nunca llegaban a sincronizarse porque
    // se fusionaban silenciosamente con otro caso que por casualidad compartía el mismo "NO.".
    //
    // A la 2ª, 3ª... aparición de un mismo número/huella (en el orden en que aparecen en la
    // hoja) se le agrega un sufijo "-2", "-3"... antes de usarlo como llave de sincronización
    // (ver CasoService.sincronizarDesdeHoja): la primera aparición no cambia (compatible con lo
    // que ya estaba sincronizado), y el sufijo es estable entre sincronizaciones mientras el
    // orden relativo de esas filas duplicadas ENTRE SÍ no cambie -- una hipótesis mucho más
    // débil, y por lo tanto más segura, que asumir que la hoja completa nunca inserta filas en
    // medio (justo la asunción que ya NO hace falta para SUPERINTENDENCIA/PROCESOS_COMISARIA
    // desde que su llave es huellaContenido(), basada en contenido y no en posición).
    private static List<FilaSincronizacionHoja> desambiguarNumerosDuplicados(List<FilaSincronizacionHoja> filas) {
        Map<String, Integer> ocurrencias = new HashMap<>();
        List<FilaSincronizacionHoja> resultado = new ArrayList<>(filas.size());
        for (FilaSincronizacionHoja fila : filas) {
            int ocurrencia = ocurrencias.merge(fila.numeroCaso(), 1, Integer::sum);
            if (ocurrencia == 1) {
                resultado.add(fila);
            } else {
                resultado.add(new FilaSincronizacionHoja(
                        fila.fuente(),
                        fila.numeroCaso() + "-" + ocurrencia,
                        fila.radicadoId(),
                        fila.nombreCliente(),
                        fila.correoCliente(),
                        fila.telefonoCliente()
                ));
            }
        }
        return resultado;
    }

    // radicadoBuscado=null para lecturas que no son una consulta de un radicado puntual (la
    // sincronización masiva): el mensaje de log/error queda genérico en ese caso, en vez de
    // decir "radicado null".
    private List<List<Object>> leerRango(String rango, String radicadoBuscado) {
        if (sheets == null) {
            throw new HojaCalculoNoDisponibleException(
                    "La consulta de estado no está disponible en este momento. Intenta de nuevo más tarde.");
        }
        try {
            ValueRange respuesta = sheets.spreadsheets().values()
                    .get(spreadsheetId, rango)
                    .setValueRenderOption("FORMATTED_VALUE")
                    .execute();
            List<List<Object>> filas = respuesta.getValues();
            return filas == null ? List.of() : filas;
        } catch (IOException | RuntimeException ex) {
            if (radicadoBuscado != null) {
                log.error("Falló la consulta a Google Sheets para el radicado {}: {}",
                        radicadoBuscado.strip(), ex.getMessage(), ex);
            } else {
                log.error("Falló la sincronización con Google Sheets (rango {}): {}", rango, ex.getMessage(), ex);
            }
            throw new HojaCalculoNoDisponibleException(
                    "No pudimos consultar el estado en este momento. Intenta de nuevo en unos minutos.");
        }
    }

    private static String valorEn(List<Object> fila, int indice) {
        if (indice >= fila.size() || fila.get(indice) == null) {
            return "";
        }
        return fila.get(indice).toString().strip();
    }

    // Una fila cuenta como caso real si trae despacho, un radicado con al menos un dígito, un
    // sujeto procesal que no sea un enlace, o correo del cliente -- cualquiera de esos, no
    // hace falta que estén todos. Solo se usa para las fuentes sin columna de "NO." (ver
    // arriba): ahí no hay ninguna otra señal de "esto es una fila de separación/nota, no un
    // caso" más que mirar si tiene algo de contenido real.
    //
    // Bug real encontrado con datos reales: una fila de Superintendencia sin despacho traía,
    // en la columna de radicado, el texto "Radicar demanda " (sin ningún dígito) y en la de
    // sujeto procesal un enlace de referencia a cómo radicar en el sitio de la SIC -- no es un
    // caso, es una nota de la firma para sí misma. Un radicado sin dígitos y un "nombre" que
    // en realidad es una URL ya no cuentan como contenido real por sí solos (mismo criterio de
    // radicadoValidoOVacio() para el radicado).
    private static boolean tieneContenidoReal(List<Object> fila, ConfiguracionFuente config) {
        if (!valorEn(fila, config.idxDespacho()).isBlank()) {
            return true;
        }
        String radicado = valorEn(fila, config.idxRadicado());
        if (radicado.chars().anyMatch(Character::isDigit)) {
            return true;
        }
        if (config.idxNombreCliente() != null) {
            String nombre = valorEn(fila, config.idxNombreCliente());
            if (!nombre.isBlank() && !esEnlace(nombre)) {
                return true;
            }
        }
        return !valorEn(fila, config.idxCorreoCliente()).isBlank();
    }

    private static boolean esEnlace(String texto) {
        String normalizado = texto.strip().toLowerCase(Locale.ROOT);
        return normalizado.startsWith("http://") || normalizado.startsWith("https://") || normalizado.startsWith("www.");
    }

    private static String valorNuloSiVacio(List<Object> fila, int indice) {
        String valor = valorEn(fila, indice);
        return valor.isBlank() ? null : valor;
    }

    // Bug real encontrado con datos reales: la columna de radicado a veces trae texto de
    // marcador de posición en vez de un número real -- "EN PROCESO DE RADICACION" en un caso
    // de Procesos Comisaría -- y tratarlo como si fuera el radicado real terminaría
    // enviándole al cliente un correo diciendo "tu número de radicado es: EN PROCESO DE
    // RADICACION". Cualquier radicado real (de cualquiera de las tres hojas, visto en la
    // práctica) siempre trae al menos un dígito; un valor sin ningún dígito se trata igual
    // que una celda vacía -- el caso se sincroniza igual, solo que sin radicado todavía.
    private static String radicadoValidoOVacio(List<Object> fila, int indice) {
        String valor = valorNuloSiVacio(fila, indice);
        if (valor == null || valor.chars().noneMatch(Character::isDigit)) {
            return null;
        }
        return valor;
    }
}
