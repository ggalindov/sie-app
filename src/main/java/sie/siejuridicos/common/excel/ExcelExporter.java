package sie.siejuridicos.common.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

// Generador de .xlsx reutilizable para cualquier exportación del panel admin (hoy:
// solicitudes y suscriptores de marketing). Una sola hoja, encabezado en negrita,
// columnas auto-ajustadas al contenido. Deliberadamente simple (sin estilos por fila,
// sin fórmulas): esto es un reporte para abrir en Excel y filtrar/ordenar ahí mismo,
// no un documento con diseño propio.
public final class ExcelExporter {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private ExcelExporter() {
    }

    public static byte[] generar(String nombreHoja, List<String> encabezados, List<List<Object>> filas) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(nombreHoja);

            CellStyle estiloEncabezado = workbook.createCellStyle();
            Font fuenteEncabezado = workbook.createFont();
            fuenteEncabezado.setBold(true);
            estiloEncabezado.setFont(fuenteEncabezado);

            Row filaEncabezado = sheet.createRow(0);
            for (int col = 0; col < encabezados.size(); col++) {
                Cell celda = filaEncabezado.createCell(col);
                celda.setCellValue(encabezados.get(col));
                celda.setCellStyle(estiloEncabezado);
            }

            for (int fila = 0; fila < filas.size(); fila++) {
                Row row = sheet.createRow(fila + 1);
                List<Object> valores = filas.get(fila);
                for (int col = 0; col < valores.size(); col++) {
                    escribirCelda(row.createCell(col), valores.get(col));
                }
            }

            for (int col = 0; col < encabezados.size(); col++) {
                sheet.autoSizeColumn(col);
                // autoSizeColumn puede dejar columnas angostas de más con fuentes
                // proporcionales; un margen fijo evita encabezados cortados a la mitad.
                sheet.setColumnWidth(col, sheet.getColumnWidth(col) + 1024);
            }

            ByteArrayOutputStream salida = new ByteArrayOutputStream();
            workbook.write(salida);
            return salida.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo generar el archivo Excel", ex);
        }
    }

    private static void escribirCelda(Cell celda, Object valor) {
        switch (valor) {
            case null -> celda.setBlank();
            case LocalDateTime fecha -> celda.setCellValue(fecha.format(FORMATO_FECHA));
            case Number numero -> celda.setCellValue(numero.doubleValue());
            case Boolean booleano -> celda.setCellValue(booleano ? "Sí" : "No");
            default -> celda.setCellValue(sanearContraFormulas(valor.toString()));
        }
    }

    // Varias columnas exportadas (nombre, mensaje, notas) vienen de formularios públicos
    // sin autenticar. Excel/LibreOffice interpretan una celda que empieza por =, +, -, @,
    // tab o retorno de carro como una fórmula al abrirla: sin este escape, un remitente
    // podría meter un payload tipo "=HYPERLINK(...)" o de inyección DDE que se ejecute en
    // el equipo de quien exporta (CWE-1236, "CSV/Formula Injection"). Anteponer un
    // apóstrofe fuerza a Excel a tratar el valor como texto literal, nunca como fórmula.
    private static String sanearContraFormulas(String valor) {
        if (!valor.isEmpty() && "=+-@\t\r".indexOf(valor.charAt(0)) >= 0) {
            return "'" + valor;
        }
        return valor;
    }
}
