package sie.siejuridicos.cobro;

import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.ValueRange;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Auditoría de seguridad crítica: marcarRespuesta() es la ÚNICA escritura que hace todo el
// sistema sobre una hoja de cálculo real de la firma. Si se equivocara de fila o de pestaña,
// escribiría la respuesta de un cliente sobre los datos financieros de OTRO cliente distinto
// -- exactamente el tipo de cruce de información que estas pruebas existen para descartar.
// Usa el constructor de paquete de HojaCobrosService (ver su javadoc) para inyectar un
// cliente Sheets mockeado en vez de hablar con la API real de Google.
class HojaCobrosServiceTest {

    private static final String SPREADSHEET_ID = "hoja-de-prueba";

    @Test
    void marcaLaRespuestaSoloEnLaFilaCuyoNumeroCoincideExactamente() throws IOException {
        Sheets sheets = mock(Sheets.class, RETURNS_DEEP_STUBS);
        // Tres clientes reales en la pestaña EMPRESAS (datos desde la fila física 6), con
        // números de fila (columna "NO.") deliberadamente NO consecutivos con la fila física
        // (5, 12, 20): si el código confundiera "número de fila" con "posición en la hoja",
        // esta prueba fallaría de inmediato.
        ValueRange columnaA = new ValueRange().setValues(List.of(
                List.of("5"), List.of("12"), List.of("20")
        ));
        when(sheets.spreadsheets().values()
                .get(eq(SPREADSHEET_ID), eq("EMPRESAS!A6:A"))
                .setValueRenderOption("FORMATTED_VALUE")
                .execute())
                .thenReturn(columnaA);

        HojaCobrosService servicio = new HojaCobrosService(SPREADSHEET_ID, sheets);
        servicio.marcarRespuesta(TipoClienteCobro.EMPRESA, "12", "Sí");

        // "12" es la SEGUNDA fila de datos (índice 1) -> fila física 6+1 = 7. Debe escribirse
        // ahí, en la columna I, y en ninguna otra fila de esa misma pestaña.
        verify(sheets.spreadsheets().values())
                .update(eq(SPREADSHEET_ID), eq("EMPRESAS!I7"), any(ValueRange.class));
        verify(sheets.spreadsheets().values(), never())
                .update(eq(SPREADSHEET_ID), eq("EMPRESAS!I6"), any(ValueRange.class));
        verify(sheets.spreadsheets().values(), never())
                .update(eq(SPREADSHEET_ID), eq("EMPRESAS!I8"), any(ValueRange.class));
    }

    // El escenario más peligroso de todos: dos clientes reales que comparten el MISMO número
    // de fila ("NO. 1") pero viven en pestañas distintas (Empresas vs Personas Naturales) --
    // esto ocurre siempre, porque cada pestaña numera sus filas desde 1 de forma
    // independiente. Confirma que responder al recordatorio de una empresa jamás toca la hoja
    // de personas naturales, ni viceversa.
    @Test
    void nuncaEscribeEnLaPestanaEquivocadaAunqueElNumeroDeFilaCoincida() throws IOException {
        Sheets sheets = mock(Sheets.class, RETURNS_DEEP_STUBS);
        ValueRange columnaEmpresas = new ValueRange().setValues(List.of(List.of("1")));
        ValueRange columnaPersonas = new ValueRange().setValues(List.of(List.of("1")));
        when(sheets.spreadsheets().values()
                .get(eq(SPREADSHEET_ID), eq("EMPRESAS!A6:A"))
                .setValueRenderOption("FORMATTED_VALUE")
                .execute())
                .thenReturn(columnaEmpresas);
        when(sheets.spreadsheets().values()
                .get(eq(SPREADSHEET_ID), eq("'PERSONAS NATURALES'!A3:A"))
                .setValueRenderOption("FORMATTED_VALUE")
                .execute())
                .thenReturn(columnaPersonas);

        HojaCobrosService servicio = new HojaCobrosService(SPREADSHEET_ID, sheets);
        servicio.marcarRespuesta(TipoClienteCobro.EMPRESA, "1", "Sí");

        verify(sheets.spreadsheets().values())
                .update(eq(SPREADSHEET_ID), eq("EMPRESAS!I6"), any(ValueRange.class));
        verify(sheets.spreadsheets().values(), never())
                .update(eq(SPREADSHEET_ID), eq("'PERSONAS NATURALES'!I3"), any(ValueRange.class));
    }

    // Si la fila ya no existe en la hoja (se borró, o el número no coincide con nada), no debe
    // escribirse absolutamente nada -- nunca "a ciegas" en una fila cualquiera.
    @Test
    void siNoEncuentraLaFilaNoEscribeNadaEnNingunLado() throws IOException {
        Sheets sheets = mock(Sheets.class, RETURNS_DEEP_STUBS);
        ValueRange columnaA = new ValueRange().setValues(List.of(List.of("5"), List.of("12")));
        when(sheets.spreadsheets().values()
                .get(eq(SPREADSHEET_ID), eq("EMPRESAS!A6:A"))
                .setValueRenderOption("FORMATTED_VALUE")
                .execute())
                .thenReturn(columnaA);

        HojaCobrosService servicio = new HojaCobrosService(SPREADSHEET_ID, sheets);
        servicio.marcarRespuesta(TipoClienteCobro.EMPRESA, "999-no-existe", "Sí");

        verify(sheets.spreadsheets().values(), never())
                .update(any(), any(), any(ValueRange.class));
    }
}
