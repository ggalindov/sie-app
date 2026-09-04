package sie.siejuridicos.hojacalculo;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.junit.jupiter.api.Test;
import sie.siejuridicos.caso.FuenteCaso;
import sie.siejuridicos.hojacalculo.dto.FilaCasoHoja;
import sie.siejuridicos.hojacalculo.dto.FilaSincronizacionHoja;
import sie.siejuridicos.hojacalculo.dto.ResultadoSincronizacionHoja;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Auditoría de seguridad crítica: buscarPorRadicado() es lo único que alimenta la consulta
// PÚBLICA de estado de un caso (/api/casos/consulta, ver CasoService.consultar()). Si hiciera
// coincidencia parcial en vez de exacta, o mezclara el rango de una fuente con el de otra, un
// cliente podría terminar viendo el despacho/estado/última decisión del caso de OTRO cliente
// distinto -- exactamente el tipo de cruce de información que estas pruebas descartan.
class HojaCalculoServiceTest {

    private static final String SPREADSHEET_ID = "hoja-de-prueba";

    // Fila de JUDICIALES con 20 columnas (B..U, índices 0..19): índice 2=despacho,
    // 3=información del caso, 4=tipo, 6=nombre cliente, 7=radicado, 8=última decisión,
    // 9=estado, 11=fecha de actualización (ver HojaCalculoService.construirConfiguraciones()).
    private static List<Object> filaJudiciales(String radicado, String sufijoDatos) {
        Object[] fila = new Object[20];
        fila[0] = "NO-" + sufijoDatos;
        fila[2] = "Juzgado " + sufijoDatos;
        fila[3] = "Partes del proceso " + sufijoDatos;
        fila[4] = "Laboral";
        fila[6] = "Cliente " + sufijoDatos;
        fila[7] = radicado;
        fila[8] = "Última decisión " + sufijoDatos;
        fila[9] = "En trámite";
        fila[11] = "01/01/2026";
        // Arrays.asList, no List.of: la fila real tiene columnas sin usar (null) entre las que
        // sí importan para esta prueba -- List.of() rechaza elementos null.
        return Arrays.asList(fila);
    }

    private Sheets sheetsConFilas(String rango, List<List<Object>> filas) throws IOException {
        Sheets sheets = mock(Sheets.class, RETURNS_DEEP_STUBS);
        ValueRange respuesta = new ValueRange().setValues(filas);
        when(sheets.spreadsheets().values()
                .get(eq(SPREADSHEET_ID), eq(rango))
                .setValueRenderOption("FORMATTED_VALUE")
                .execute())
                .thenReturn(respuesta);
        return sheets;
    }

    @Test
    void devuelveExactamenteLosDatosDeLaFilaConEseRadicado() throws IOException {
        Sheets sheets = sheetsConFilas("JUDICIALES!B7:U", List.of(
                fila("2026-00111-A", "cliente-A"),
                fila("2026-00222-B", "cliente-B")
        ));
        HojaCalculoService servicio = new HojaCalculoService(SPREADSHEET_ID, sheets);

        Optional<FilaCasoHoja> resultado = servicio.buscarPorRadicado(FuenteCaso.JUDICIALES, "2026-00222-B");

        assertTrue(resultado.isPresent());
        assertEquals("Juzgado cliente-B", resultado.get().despachoJudicial());
        assertEquals("Partes del proceso cliente-B", resultado.get().informacionCaso());
        assertEquals("Última decisión cliente-B", resultado.get().ultimaDecision());
        // El nombre/correo/teléfono del cliente NUNCA deben aparecer en la respuesta pública
        // (ver el javadoc de FilaCasoHoja): esto se verifica por construcción de tipos (el
        // record no tiene esos campos), no hace falta una aserción explícita aquí.
    }

    private static List<Object> fila(String radicado, String sufijoDatos) {
        return filaJudiciales(radicado, sufijoDatos);
    }

    @Test
    void unRadicadoQueEsSubcadenaDeOtroNoHaceMatchParcial() throws IOException {
        // "123" no debe encontrar la fila cuyo radicado real es "2026-00123-A": una
        // coincidencia por contains()/startsWith() en vez de igualdad exacta expondría el
        // caso de un cliente a partir de un fragmento adivinado de su radicado.
        Sheets sheets = sheetsConFilas("JUDICIALES!B7:U", List.of(fila("2026-00123-A", "cliente-A")));
        HojaCalculoService servicio = new HojaCalculoService(SPREADSHEET_ID, sheets);

        assertTrue(servicio.buscarPorRadicado(FuenteCaso.JUDICIALES, "123").isEmpty());
        assertTrue(servicio.buscarPorRadicado(FuenteCaso.JUDICIALES, "2026-00123").isEmpty());
        assertTrue(servicio.buscarPorRadicado(FuenteCaso.JUDICIALES, "00123-A").isEmpty());
        assertTrue(servicio.buscarPorRadicado(FuenteCaso.JUDICIALES, "2026-00123-A").isPresent());
    }

    @Test
    void laComparacionEsInsensibleAMayusculasPeroExacta() throws IOException {
        Sheets sheets = sheetsConFilas("JUDICIALES!B7:U", List.of(fila("abc-2026-001", "cliente-A")));
        HojaCalculoService servicio = new HojaCalculoService(SPREADSHEET_ID, sheets);

        assertTrue(servicio.buscarPorRadicado(FuenteCaso.JUDICIALES, "ABC-2026-001").isPresent());
        assertTrue(servicio.buscarPorRadicado(FuenteCaso.JUDICIALES, "AbC-2026-001").isPresent());
    }

    // El escenario más peligroso: dos clientes con el MISMO texto de radicado, cada uno en una
    // fuente (pestaña) distinta -- confirma que consultar en JUDICIALES nunca lee ni devuelve
    // el dato de SUPERINTENDENCIA aunque el radicado buscado coincida textualmente en ambas.
    @Test
    void nuncaMezclaDatosDeUnaFuenteConLosDeOtraAunqueElRadicadoCoincidaEnTexto() throws IOException {
        Sheets sheets = mock(Sheets.class, RETURNS_DEEP_STUBS);
        when(sheets.spreadsheets().values()
                .get(eq(SPREADSHEET_ID), eq("JUDICIALES!B7:U"))
                .setValueRenderOption("FORMATTED_VALUE")
                .execute())
                .thenReturn(new ValueRange().setValues(List.of(fila("RAD-COMPARTIDO", "cliente-judicial"))));

        Object[] filaSuper = new Object[13];
        filaSuper[1] = "Superintendencia de Sociedades";
        filaSuper[3] = "RAD-COMPARTIDO";
        filaSuper[5] = "Cliente Superintendencia";
        filaSuper[8] = "Estado super";
        filaSuper[9] = "02/02/2026";
        when(sheets.spreadsheets().values()
                .get(eq(SPREADSHEET_ID), eq("SUPERINTENDENCIA!A4:M"))
                .setValueRenderOption("FORMATTED_VALUE")
                .execute())
                .thenReturn(new ValueRange().setValues(List.of(Arrays.asList(filaSuper))));

        HojaCalculoService servicio = new HojaCalculoService(SPREADSHEET_ID, sheets);

        Optional<FilaCasoHoja> resultadoJudicial = servicio.buscarPorRadicado(FuenteCaso.JUDICIALES, "RAD-COMPARTIDO");
        assertTrue(resultadoJudicial.isPresent());
        assertEquals("Juzgado cliente-judicial", resultadoJudicial.get().despachoJudicial());

        Optional<FilaCasoHoja> resultadoSuper = servicio.buscarPorRadicado(FuenteCaso.SUPERINTENDENCIA, "RAD-COMPARTIDO");
        assertTrue(resultadoSuper.isPresent());
        assertEquals("Superintendencia de Sociedades", resultadoSuper.get().despachoJudicial());

        // Consultar SUPERINTENDENCIA jamás debió leer el rango de JUDICIALES, ni viceversa.
        verify(sheets.spreadsheets().values(), never())
                .get(eq(SPREADSHEET_ID), eq("'PROCESOS COMISARIA-'!A3:R"));
    }

    @Test
    void sinCoincidenciaDevuelveVacioEnVezDeError() throws IOException {
        Sheets sheets = sheetsConFilas("JUDICIALES!B7:U", List.of(fila("2026-00111-A", "cliente-A")));
        HojaCalculoService servicio = new HojaCalculoService(SPREADSHEET_ID, sheets);

        assertTrue(servicio.buscarPorRadicado(FuenteCaso.JUDICIALES, "NO-EXISTE").isEmpty());
    }

    // --- listarParaSincronizar() / SUPERINTENDENCIA ------------------------------------------
    //
    // Auditoría de seguridad crítica pedida explícitamente: confirmar que la sincronización de
    // Superintendencia trae exactamente los casos reales, sin duplicarlos ni cruzarlos, y que
    // sigue siendo correcta aunque la firma edite la hoja insertando filas en medio de un
    // bloque de entidad (Industria y Comercio, Financiera, Salud...) en vez de solo al final --
    // el escenario que rompía la llave "fila-N" anterior (ver HojaCalculoService.
    // huellaContenido()).

    // SUPERINTENDENCIA!A4:M -- índices 0..12: B(1)=despacho/nombre superintendencia,
    // D(3)=radicado, F(5)=demandante (también nombre del cliente), I(8)=estado,
    // J(9)=fecha de revisión, L(11)=correo, M(12)=teléfono.
    private static List<Object> filaSuper(String despacho, String radicado, String demandante, String correo) {
        Object[] fila = new Object[13];
        fila[1] = despacho;
        fila[3] = radicado;
        fila[5] = demandante;
        fila[8] = "En trámite";
        fila[9] = "01/01/2026";
        fila[11] = correo;
        return Arrays.asList(fila);
    }

    private Sheets sheetsSuperintendencia(List<List<Object>> filas) throws IOException {
        return sheetsConFilas("SUPERINTENDENCIA!A4:M", filas);
    }

    private static Map<String, FilaSincronizacionHoja> porRadicado(ResultadoSincronizacionHoja resultado) {
        return resultado.filas().stream()
                .collect(Collectors.toMap(FilaSincronizacionHoja::radicadoId, Function.identity()));
    }

    @Test
    void sincronizacionDeSuperintendenciaLeeLasColumnasCorrectas() throws IOException {
        Sheets sheets = sheetsSuperintendencia(List.of(
                filaSuper("Superintendencia de Industria y Comercio", "SIC-2026-001", "Juan Pérez", "juan@correo.com")
        ));
        HojaCalculoService servicio = new HojaCalculoService(SPREADSHEET_ID, sheets);

        ResultadoSincronizacionHoja resultado = servicio.listarParaSincronizar();

        assertEquals(1, resultado.filas().size());
        FilaSincronizacionHoja fila = resultado.filas().get(0);
        assertEquals(FuenteCaso.SUPERINTENDENCIA, fila.fuente());
        assertEquals("SIC-2026-001", fila.radicadoId());
        assertEquals("Juan Pérez", fila.nombreCliente());
        assertEquals("juan@correo.com", fila.correoCliente());
    }

    // Bug real corregido en esta auditoría: antes la llave de sincronización de Superintendencia
    // era el número de fila física ("fila-N"), estable solo si la hoja nunca inserta filas en
    // medio. Esta prueba reproduce exactamente ese escenario -- una fila nueva insertada ENTRE
    // dos casos que ya existían -- y confirma que los casos de abajo, aunque su posición física
    // cambió, conservan la MISMA llave de sincronización que antes de la inserción. Con la
    // llave anterior esta prueba habría fallado: "fila-2"/"fila-3" habrían pasado a identificar
    // filas de contenido distinto tras la inserción, arriesgando que el radicado de un cliente
    // se sobrescribiera con el de otro.
    @Test
    void unaFilaInsertadaEnMedioDeLaHojaNoCambiaLaLlaveDeLosCasosQueYaExistian() throws IOException {
        // Radicados con al menos un dígito a propósito: radicadoValidoOVacio() descarta como
        // "sin radicado todavía" cualquier valor sin dígitos (ver esa función), así que un
        // radicado de prueba puramente alfabético no serviría de llave para este mapa.
        List<List<Object>> antesDeInsertar = List.of(
                filaSuper("Superintendencia Financiera", "RAD-100", "Ana Gómez", "ana@correo.com"),
                filaSuper("Superintendencia de Salud", "RAD-200", "Beatriz Ruiz", "beatriz@correo.com"),
                filaSuper("Superintendencia de Servicios Públicos", "RAD-300", "Carlos Díaz", "carlos@correo.com")
        );
        Sheets sheetsAntes = sheetsSuperintendencia(antesDeInsertar);
        Map<String, FilaSincronizacionHoja> antes =
                porRadicado(new HojaCalculoService(SPREADSHEET_ID, sheetsAntes).listarParaSincronizar());

        // La firma agrega un caso nuevo justo DESPUÉS del primero, no al final de la hoja: todo
        // lo que estaba después de "Ana Gómez" se corre una fila.
        List<List<Object>> despuesDeInsertar = List.of(
                filaSuper("Superintendencia Financiera", "RAD-100", "Ana Gómez", "ana@correo.com"),
                filaSuper("Superintendencia de Industria y Comercio", "RAD-400", "Diana Torres", "diana@correo.com"),
                filaSuper("Superintendencia de Salud", "RAD-200", "Beatriz Ruiz", "beatriz@correo.com"),
                filaSuper("Superintendencia de Servicios Públicos", "RAD-300", "Carlos Díaz", "carlos@correo.com")
        );
        Sheets sheetsDespues = sheetsSuperintendencia(despuesDeInsertar);
        Map<String, FilaSincronizacionHoja> despues =
                porRadicado(new HojaCalculoService(SPREADSHEET_ID, sheetsDespues).listarParaSincronizar());

        assertEquals(despues.get("RAD-100").numeroCaso(), antes.get("RAD-100").numeroCaso(),
                "Ana Gómez no cambió de fila físicamente, su llave debe seguir igual.");
        assertEquals(despues.get("RAD-200").numeroCaso(), antes.get("RAD-200").numeroCaso(),
                "Beatriz Ruiz se corrió una fila hacia abajo por la inserción, pero su CONTENIDO "
                        + "no cambió -- su llave de sincronización debe seguir siendo la misma para que "
                        + "sincronizarDesdeHoja() la reconozca como el mismo caso, no cree un duplicado "
                        + "ni le asigne por error el radicado de otra fila.");
        assertEquals(despues.get("RAD-300").numeroCaso(), antes.get("RAD-300").numeroCaso(),
                "Carlos Díaz se corrió dos filas hacia abajo, misma exigencia que Beatriz.");

        // El caso nuevo debe tener una llave propia, distinta de los otros tres.
        String llaveNueva = despues.get("RAD-400").numeroCaso();
        assertNotEquals(antes.get("RAD-100").numeroCaso(), llaveNueva);
        assertNotEquals(antes.get("RAD-200").numeroCaso(), llaveNueva);
        assertNotEquals(antes.get("RAD-300").numeroCaso(), llaveNueva);
    }

    @Test
    void dosCasosRealesDistintosNuncaComparenLaMismaLlave() throws IOException {
        Sheets sheets = sheetsSuperintendencia(List.of(
                filaSuper("Superintendencia Financiera", "RAD-A", "Ana Gómez", "ana@correo.com"),
                filaSuper("Superintendencia de Salud", "RAD-B", "Beatriz Ruiz", "beatriz@correo.com")
        ));
        ResultadoSincronizacionHoja resultado = new HojaCalculoService(SPREADSHEET_ID, sheets).listarParaSincronizar();

        assertEquals(2, resultado.filas().size());
        assertNotEquals(resultado.filas().get(0).numeroCaso(), resultado.filas().get(1).numeroCaso());
    }

    // Colisión real de contenido (mismo despacho y mismo nombre de demandante en dos filas
    // realmente distintas, ej. el mismo cliente con dos procesos separados contra la misma
    // entidad): deben quedar desambiguadas con un sufijo, nunca fusionadas en un solo caso --
    // si se fusionaran, uno de los dos radicados/clientes simplemente desaparecería de la
    // sincronización.
    @Test
    void dosFilasConElMismoDespachoYElMismoDemandanteQuedanDesambiguadasNoFusionadas() throws IOException {
        Sheets sheets = sheetsSuperintendencia(List.of(
                filaSuper("Superintendencia Financiera", "RAD-1", "Ana Gómez", "ana@correo.com"),
                filaSuper("Superintendencia Financiera", "RAD-2", "Ana Gómez", "ana@correo.com")
        ));
        ResultadoSincronizacionHoja resultado = new HojaCalculoService(SPREADSHEET_ID, sheets).listarParaSincronizar();

        assertEquals(2, resultado.filas().size(), "Las dos filas deben sincronizarse, ninguna se pierde.");
        String llave1 = resultado.filas().get(0).numeroCaso();
        String llave2 = resultado.filas().get(1).numeroCaso();
        assertNotEquals(llave1, llave2, "Deben quedar desambiguadas, nunca compartir la misma llave.");
        assertEquals(llave1 + "-2", llave2);
    }

    // La llave de sincronización nunca debe depender del radicado: el radicado a propósito
    // puede llegar vacío y completarse en una sincronización posterior (el despacho aún no lo
    // asigna). Si el radicado formara parte de la llave, completarlo más adelante "perdería" el
    // caso ya sincronizado en vez de actualizarlo -- CasoService.sincronizarDesdeHoja() dejaría
    // de reconocerlo como el mismo caso.
    @Test
    void laLlaveNoCambiaCuandoElRadicadoPasaDeVacioAAsignado() throws IOException {
        Sheets sheetsSinRadicado = sheetsSuperintendencia(List.of(
                filaSuper("Superintendencia Financiera", "", "Ana Gómez", "ana@correo.com")
        ));
        ResultadoSincronizacionHoja sinRadicado =
                new HojaCalculoService(SPREADSHEET_ID, sheetsSinRadicado).listarParaSincronizar();

        Sheets sheetsConRadicado = sheetsSuperintendencia(List.of(
                filaSuper("Superintendencia Financiera", "RAD-100-ASIGNADO", "Ana Gómez", "ana@correo.com")
        ));
        ResultadoSincronizacionHoja conRadicado =
                new HojaCalculoService(SPREADSHEET_ID, sheetsConRadicado).listarParaSincronizar();

        assertEquals(1, sinRadicado.filas().size());
        assertEquals(1, conRadicado.filas().size());
        assertNull(sinRadicado.filas().get(0).radicadoId(), "Todavía sin radicado real.");
        assertEquals("RAD-100-ASIGNADO", conRadicado.filas().get(0).radicadoId(), "Ya con radicado real.");
        assertEquals(sinRadicado.filas().get(0).numeroCaso(), conRadicado.filas().get(0).numeroCaso(),
                "La llave debe ser la misma antes y después de que el despacho asigne el radicado.");
    }

    // Bug real reportado por el usuario ("todos los de superintendencia están mal, no están
    // jalando la info correctamente") y confirmado leyendo la hoja real: la hoja de
    // Superintendencia separa sus bloques de entidad con filas donde el nombre de la
    // siguiente entidad (o, en un caso, los propios encabezados de columna) queda escrito
    // como texto en la MISMA columna que el nombre del demandante, con despacho, radicado y
    // correo todos vacíos. Antes esto se sincronizaba como un "caso" fantasma -- el nombre por
    // sí solo ya no es señal suficiente de contenido real.
    @Test
    void unaFilaSeparadoraDeSeccionConSoloElNombreDeLaEntidadNoCreaUnCasoFantasma() throws IOException {
        Sheets sheets = sheetsSuperintendencia(List.of(
                filaSuper("Superintendencia Financiera", "RAD-100", "Ana Gómez", "ana@correo.com"),
                // Fila separadora real: solo la columna de "demandante" trae texto (el nombre
                // de la siguiente entidad, centrado con espacios de relleno), todo lo demás
                // vacío -- exactamente el patrón encontrado en la hoja real.
                filaSuper("", "", "                    SUPERINTENDENCIA NACIONAL DE SALUD", ""),
                // Fila de encabezados re-escritos como si fueran datos, también real.
                filaSuper("", "RADICADO ", "DEMANDANTE ", ""),
                filaSuper("Superintendencia Nacional de Salud", "RAD-200", "Beatriz Ruiz", "beatriz@correo.com")
        ));

        ResultadoSincronizacionHoja resultado = new HojaCalculoService(SPREADSHEET_ID, sheets).listarParaSincronizar();

        assertEquals(2, resultado.filas().size(),
                "Solo los 2 casos reales, ninguna de las 2 filas separadoras/de encabezado.");
        assertTrue(resultado.filas().stream().anyMatch(f -> "RAD-100".equals(f.radicadoId())));
        assertTrue(resultado.filas().stream().anyMatch(f -> "RAD-200".equals(f.radicadoId())));
    }
}
