package sie.siejuridicos.documento;

import com.lowagie.text.pdf.AcroFields;
import com.lowagie.text.pdf.PdfPKCS7;
import com.lowagie.text.pdf.PdfReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import sie.siejuridicos.documento.dto.VerificacionPublicaResponse;

import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

// Inspecciona las firmas digitales PAdES (PKCS#7 detached) de un PDF subido: si la firma
// criptográfica verifica contra el hash del contenido firmado, si el certificado
// corresponde a una llave PAdES de SIE (las mismas que emite PadesSigningService) y si la
// firma cubre el documento completo. Una firma que NO cubre todo el documento delata
// revisiones incrementales añadidas después de firmar. Nunca lanza: un archivo ilegible es
// simplemente "sin firmas".
@Component
public class PadesInspector {

    private static final Logger log = LoggerFactory.getLogger(PadesInspector.class);

    private final FirmaPadesClaveRepository firmaPadesClaveRepository;

    public PadesInspector(FirmaPadesClaveRepository firmaPadesClaveRepository) {
        this.firmaPadesClaveRepository = firmaPadesClaveRepository;
    }

    public VerificacionPublicaResponse.FirmaPades inspeccionar(byte[] pdf) {
        try {
            PdfReader lector = new PdfReader(pdf);
            try {
                AcroFields campos = lector.getAcroFields();
                @SuppressWarnings("unchecked")
                List<String> nombres = campos.getSignatureNames();
                if (nombres.isEmpty()) {
                    return new VerificacionPublicaResponse.FirmaPades(0, false, false, false);
                }
                List<byte[]> certificadosDeSie = firmaPadesClaveRepository.findAll().stream()
                        .map(FirmaPadesClave::getCertificado).toList();
                boolean todasValidas = true;
                boolean todasCubren = true;
                boolean algunaDeSie = false;
                for (String nombre : nombres) {
                    todasCubren &= campos.signatureCoversWholeDocument(nombre);
                    try {
                        PdfPKCS7 pkcs7 = campos.verifySignature(nombre);
                        todasValidas &= pkcs7.verify();
                        X509Certificate certificado = pkcs7.getSigningCertificate();
                        if (certificado != null) {
                            byte[] der = certificado.getEncoded();
                            algunaDeSie |= certificadosDeSie.stream().anyMatch(c -> Arrays.equals(c, der));
                        }
                    } catch (Exception ex) {
                        todasValidas = false;
                    }
                }
                return new VerificacionPublicaResponse.FirmaPades(nombres.size(), todasValidas, algunaDeSie, todasCubren);
            } finally {
                lector.close();
            }
        } catch (Exception ex) {
            log.debug("El archivo subido no pudo inspeccionarse como PDF firmado: {}", ex.getMessage());
            return new VerificacionPublicaResponse.FirmaPades(0, false, false, false);
        }
    }
}
