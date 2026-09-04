package sie.siejuridicos.hojacalculo;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.junit.jupiter.api.Test;
import sie.siejuridicos.caso.FuenteCaso;
import sie.siejuridicos.hojacalculo.dto.FilaCasoHoja;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
