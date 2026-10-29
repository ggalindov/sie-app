package sie.siejuridicos.documento.pdf;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Punto 14 de las mejoras pedidas sobre el motor "hermano" (UCN): el certificado que
// descarga el FIRMANTE (detalleTecnicoCompleto=false) no debe revelar la cédula completa,
// la fecha de vencimiento del enlace, el identificador de la llave de firma de aplicación,
// ni la geolocalización exacta -- todo eso SÍ debe seguir presente en la versión completa
// para staff (detalleTecnicoCompleto=true), que es evidencia forense para una disputa real.
class EvidenceCertificatePdfGeneratorPrivacidadTest {

    private final EvidenceCertificatePdfGenerator generador = new EvidenceCertificatePdfGenerator();

    private EvidenceCertificateData datos(boolean detalleTecnicoCompleto) {
        LocalDateTime ahora = LocalDateTime.now();
        EvidenceCertificateData.FirmanteData firmante = new EvidenceCertificateData.FirmanteData(
                "PARTE",
                "Juan Pérez",
                "1098765432",
                "FIRMADO",
                ahora,
                ahora.plusDays(7),
                "190.144.33.21",
                "Mozilla/5.0 (navegador de prueba)",
                "huella-abc123",
                null,
                false,
                "4.71, -74.07 (precisión +/-15 m)",
                ahora,
                "Acepto el documento.",
                ahora,
                "hash-evidencia-xyz",
                ahora,
                true,
                "https://freetsa.org/tsr",
                true,
                null,
                null,
                true,
                "hash-foto-verificacion",
                null
        );
        return new EvidenceCertificateData(
                "doc-1",
                "Contrato de prueba",
                1L,
                ahora,
                "hash-contenido",
                "SELLADO",
                true,
                "kid-llave-activa-2026",
                true,
                "https://freetsa.org/tsr",
                ahora,
                true,
                null,
                "ABC123",
                "https://siejuridicos.com/verificar-documento?codigo=ABC123",
                ahora,
                List.of(firmante),
                detalleTecnicoCompleto
        );
    }

    private String textoDePdf(byte[] pdf) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(doc);
        }
    }

    // Nota de alcance: el redondeo de la geolocalización (4.71,-74.07 exacto -> aproximado a
    // 2 decimales) NO ocurre en este generador -- ocurre antes, en
    // EvidenceCertificateService.geolocalizacionTexto(), que arma el string que le llega ya
    // resuelto aquí (ver esa clase; ya quedó revisada y correcta por lectura de código). Este
    // test solo cubre lo que de verdad decide EvidenceCertificatePdfGenerator: cédula, llave
    // de firma, vigencia del enlace e IP.
    @Test
    void laVersionDelFirmanteOcultaDatosSensiblesDeInfraestructura() throws Exception {
        String texto = textoDePdf(generador.generar(datos(false)));

        assertFalse(texto.contains("1098765432"), "No debe mostrar la cédula completa.");
        assertTrue(texto.contains("1098765432".substring(6)), "Debe mostrar al menos los últimos 4 dígitos enmascarados.");
        assertFalse(texto.contains("kid-llave-activa-2026"), "No debe mostrar el identificador de la llave de firma.");
        assertFalse(texto.contains("Enlace vigente hasta"), "No debe mostrar la fecha de vencimiento del enlace.");
        assertFalse(texto.contains("190.144.33.21"), "No debe mostrar la IP de captura.");
    }

    @Test
    void laVersionCompletaParaStaffMantieneTodoElDetalleForense() throws Exception {
        String texto = textoDePdf(generador.generar(datos(true)));

        assertTrue(texto.contains("1098765432"), "La versión de staff debe mostrar la cédula completa.");
        assertTrue(texto.contains("kid-llave-activa-2026"), "La versión de staff debe mostrar la llave de firma.");
        assertTrue(texto.contains("Enlace vigente hasta"), "La versión de staff debe mostrar la vigencia del enlace.");
        assertTrue(texto.contains("190.144.33.21"), "La versión de staff debe mostrar la IP de captura.");
    }
}
