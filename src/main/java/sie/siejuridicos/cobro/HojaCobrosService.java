package sie.siejuridicos.cobro;

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
import sie.siejuridicos.cobro.dto.FilaCobroHoja;
import sie.siejuridicos.common.exception.HojaCalculoNoDisponibleException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Lee y ESCRIBE (a diferencia de HojaCalculoService, que es de solo lectura) el Google
// Sheets de cobros pendientes de la firma -- una hoja de cálculo DISTINTA a la de casos, con
// su propio ID (app.hoja-cobros.spreadsheet-id). Reutiliza el mismo archivo de credenciales
// de la cuenta de servicio (GOOGLE_SHEETS_CREDENTIALS_PATH) que HojaCalculoService, pero con
// un cliente HTTP y un scope de OAuth COMPLETAMENTE APARTE:
// SheetsScopes.SPREADSHEETS (lectura y escritura), nunca el de solo lectura. Cliente
// deliberadamente separado del de HojaCalculoService (no un flag "modoEscritura"): así,
// aunque hubiera un error en la lógica de escritura de esta clase, el cliente que consulta
// las hojas de Casos sigue siendo físicamente incapaz de escribir nada -- su token de OAuth
// ni siquiera tiene ese permiso.
//
// Única escritura que hace: marcar la columna "RESPONDIO MENSAJE" (I) cuando el cliente
// contesta el botón de sí/no del recordatorio de WhatsApp (ver
// CobroService.registrarRespuesta()). Nunca toca ninguna otra columna.
@Service
public class HojaCobrosService {

    private static final Logger log = LoggerFactory.getLogger(HojaCobrosService.class);

    // Confirmado leyendo la fila de encabezados real de las dos pestañas: EMPRESAS tiene el
    // encabezado en la fila 5 (datos desde la 6), PERSONAS NATURALES en la fila 2 (datos
    // desde la 3) -- misma estructura de columnas A..I en ambas, distinta solo la fila de
    // arranque. A "NO.", B "NOMBRE / RAZÓN SOCIAL", C "DIRECCIÓN / DOMICILIO" (excluida a
    // propósito, nunca se lee), D "CORREO ELECTRÓNICO", E "TELÉFONO", F "CÉDULA / NIT",
    // G "HONORARIOS", H "PAGO ESTE MES", I "RESPONDIO MENSAJE".
    private record ConfiguracionPestana(TipoClienteCobro tipo, String pestana, String rangoDatos, int primeraFilaDatos) {
    }

    private static final List<ConfiguracionPestana> PESTANAS = List.of(
            new ConfiguracionPestana(TipoClienteCobro.EMPRESA, "EMPRESAS", "EMPRESAS!A6:I", 6),
            new ConfiguracionPestana(TipoClienteCobro.PERSONA_NATURAL, "PERSONAS NATURALES",
                    "'PERSONAS NATURALES'!A3:I", 3)
    );

    private static final int IDX_NUMERO_FILA = 0;      // A
    private static final int IDX_NOMBRE = 1;            // B
    // C (DIRECCIÓN / DOMICILIO) nunca se lee, ni siquiera se pide a la API.
    private static final int IDX_CORREO = 3;             // D
    private static final int IDX_TELEFONO = 4;           // E
    private static final int IDX_CEDULA_NIT = 5;         // F
    private static final int IDX_HONORARIOS = 6;         // G
    private static final int IDX_PAGO_ESTE_MES = 7;      // H
    private static final int IDX_RESPONDIO_MENSAJE = 8;  // I

    private final String spreadsheetId;
    private final Sheets sheets;

    // @Autowired explícito, necesario: en cuanto una segunda constructora aparece en la clase
    // (ver la de abajo, solo para pruebas), Spring deja de poder inferir sola cuál usar para
    // inyección de dependencias -- sin esto, el arranque completo de la aplicación falla con
    // "No default constructor found" (bug real encontrado en esta auditoría, ver el mismo
    // fix en HojaCalculoService).
    @Autowired
    public HojaCobrosService(
            @Value("${app.hoja-cobros.spreadsheet-id}") String spreadsheetId,
            @Value("${app.hoja-calculo.credenciales-path}") String credencialesPath) {
        this.spreadsheetId = spreadsheetId;
        this.sheets = construirCliente(spreadsheetId, credencialesPath);
    }

    // Constructor de prueba (paquete): inyecta un cliente Sheets ya construido (mock) en vez
    // de crearlo a partir de credenciales reales, para que HojaCobrosServiceTest pueda probar
    // la lógica de re-ubicación de fila de marcarRespuesta() sin depender de la API real de
    // Google ni de un archivo de credenciales.
    HojaCobrosService(String spreadsheetId, Sheets sheets) {
        this.spreadsheetId = spreadsheetId;
        this.sheets = sheets;
    }

    private static Sheets construirCliente(String spreadsheetId, String credencialesPath) {
        if (spreadsheetId.isBlank() || credencialesPath.isBlank()) {
            log.warn("Google Sheets de cobros no configurado (faltan app.hoja-cobros.spreadsheet-id / "
                    + "credenciales-path): la sección de Cobros Pendientes responderá 'servicio no "
                    + "disponible' hasta que se configure GOOGLE_SHEETS_COBROS_ID.");
            return null;
        }
        try (InputStream credencialesJson = new FileInputStream(credencialesPath)) {
            // SPREADSHEETS, no SPREADSHEETS_READONLY: única diferencia real de scope frente a
            // HojaCalculoService, y la razón por la que esta clase existe aparte.
            GoogleCredentials credenciales = GoogleCredentials.fromStream(credencialesJson)
                    .createScoped(List.of(SheetsScopes.SPREADSHEETS));
            NetHttpTransport transporte = GoogleNetHttpTransport.newTrustedTransport();
            HttpCredentialsAdapter adaptadorCredenciales = new HttpCredentialsAdapter(credenciales);
            HttpRequestInitializer inicializador = solicitud -> {
                adaptadorCredenciales.initialize(solicitud);
                solicitud.setConnectTimeout(5_000);
                solicitud.setReadTimeout(15_000);
            };
            return new Sheets.Builder(transporte, GsonFactory.getDefaultInstance(), inicializador)
                    .setApplicationName("sie-juridicos")
                    .build();
        } catch (IOException | GeneralSecurityException ex) {
            log.error("No se pudo inicializar el cliente de Google Sheets de cobros: {}", ex.getMessage(), ex);
            return null;
        }
    }

    public boolean isConfigurado() {
        return sheets != null;
    }

    // Trae TODAS las filas con datos de las DOS pestañas, para que
    // CobroService.sincronizarDesdeHoja() decida cuáles son nuevas, cuáles cambiaron, y
    // cuáles desaparecieron (reconciliación completa, a diferencia de la sincronización de
    // Casos que solo agrega/actualiza: aquí si una fila se borra de la hoja hay que
    // reflejarlo, ver ClienteCobro.activo).
    public List<FilaCobroHoja> listarTodos() {
        if (sheets == null) {
            throw new HojaCalculoNoDisponibleException(
                    "La sección de cobros pendientes no está disponible en este momento.");
        }
        List<FilaCobroHoja> resultado = new ArrayList<>();
        for (ConfiguracionPestana pestana : PESTANAS) {
            resultado.addAll(leerPestana(pestana));
        }
        return resultado;
    }

    private List<FilaCobroHoja> leerPestana(ConfiguracionPestana pestana) {
        List<List<Object>> filas;
        try {
            ValueRange respuesta = sheets.spreadsheets().values()
                    .get(spreadsheetId, pestana.rangoDatos())
                    .setValueRenderOption("FORMATTED_VALUE")
                    .execute();
            filas = respuesta.getValues();
        } catch (IOException | RuntimeException ex) {
            log.error("Falló la lectura de la pestaña '{}' del Google Sheets de cobros: {}",
                    pestana.pestana(), ex.getMessage(), ex);
            throw new HojaCalculoNoDisponibleException(
                    "No pudimos leer la hoja de cobros en este momento. Intenta de nuevo en unos minutos.");
        }
        if (filas == null) {
            return List.of();
        }
        List<FilaCobroHoja> resultado = new ArrayList<>();
        for (List<Object> fila : filas) {
            String numeroFila = valorEn(fila, IDX_NUMERO_FILA);
            if (numeroFila.isBlank()) {
                continue;
            }
            resultado.add(new FilaCobroHoja(
                    pestana.tipo(),
                    numeroFila,
                    valorEn(fila, IDX_NOMBRE),
                    valorNuloSiVacio(fila, IDX_CORREO),
                    valorNuloSiVacio(fila, IDX_TELEFONO),
                    valorNuloSiVacio(fila, IDX_CEDULA_NIT),
                    valorNuloSiVacio(fila, IDX_HONORARIOS),
                    valorBooleanoONulo(fila, IDX_PAGO_ESTE_MES),
                    valorNuloSiVacio(fila, IDX_RESPONDIO_MENSAJE)
            ));
        }
        return resultado;
    }

    // Única operación de escritura de todo el sistema: marca en la columna I de la fila que
    // tenga este numeroFila la respuesta del cliente ("Sí"/"No"). Vuelve a ubicar la fila por
    // su número justo antes de escribir (no reutiliza un índice de fila guardado de una
    // sincronización anterior): si alguien insertó o borró filas arriba en la hoja desde
    // entonces, un índice viejo escribiría en la celda equivocada.
    public void marcarRespuesta(TipoClienteCobro tipo, String numeroFila, String respuesta) {
        if (sheets == null) {
            log.warn("No se pudo registrar la respuesta de cobro: Google Sheets de cobros no configurado.");
            return;
        }
        ConfiguracionPestana pestana = PESTANAS.stream()
                .filter(p -> p.tipo() == tipo)
                .findFirst()
                .orElseThrow();
        try {
            // FORMATTED_VALUE, no UNFORMATTED_VALUE -- bug real encontrado en esta auditoría: si
            // la columna "NO." está tipada como número en la hoja (no texto), UNFORMATTED_VALUE
            // la devuelve como un Double vía Gson (Object -> "12.0"), mientras que numeroFila se
            // capturó en leerPestana() con FORMATTED_VALUE (siempre texto, "12"). La comparación
            // de abajo (valor.equals(numeroFila)) nunca habría coincidido, y CADA respuesta de un
            // cliente por WhatsApp habría fallado en silencio a escribirse en la hoja (quedaba
            // solo en nuestra base de datos, nunca reflejada donde el equipo la revisa). Usar el
            // mismo modo de renderizado en ambos lados garantiza el mismo formato de texto sin
            // importar cómo esté tipada la columna.
            ValueRange columnaA = sheets.spreadsheets().values()
                    .get(spreadsheetId, pestana.pestana() + "!A" + pestana.primeraFilaDatos() + ":A")
                    .setValueRenderOption("FORMATTED_VALUE")
                    .execute();
            List<List<Object>> filas = columnaA.getValues();
            if (filas == null) {
                log.warn("No se encontró la fila para registrar la respuesta de cobro (hoja vacía).");
                return;
            }
            int filaFisica = -1;
            for (int i = 0; i < filas.size(); i++) {
                String valor = valorEn(filas.get(i), 0);
                if (valor.equals(numeroFila)) {
                    filaFisica = pestana.primeraFilaDatos() + i;
                    break;
                }
            }
            if (filaFisica == -1) {
                log.warn("No se encontró en la hoja la fila del cobro para registrar la respuesta "
                        + "(puede haber sido eliminada).");
                return;
            }
            ValueRange cuerpo = new ValueRange().setValues(List.of(Collections.singletonList(respuesta)));
            sheets.spreadsheets().values()
                    .update(spreadsheetId, pestana.pestana() + "!I" + filaFisica, cuerpo)
                    .setValueInputOption("RAW")
                    .execute();
        } catch (IOException | RuntimeException ex) {
            log.error("Falló al escribir la respuesta de cobro en la hoja: {}", ex.getMessage(), ex);
        }
    }

    private static String valorEn(List<Object> fila, int indice) {
        if (fila == null || indice >= fila.size() || fila.get(indice) == null) {
            return "";
        }
        return fila.get(indice).toString().strip();
    }

    private static String valorNuloSiVacio(List<Object> fila, int indice) {
        String valor = valorEn(fila, indice);
        return valor.isBlank() ? null : valor;
    }

    private static Boolean valorBooleanoONulo(List<Object> fila, int indice) {
        String valor = valorEn(fila, indice);
        if (valor.isBlank()) {
            return null;
        }
        return "TRUE".equalsIgnoreCase(valor) || "VERDADERO".equalsIgnoreCase(valor) || "SI".equalsIgnoreCase(valor);
    }
}
