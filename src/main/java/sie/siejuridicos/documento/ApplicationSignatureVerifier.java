package sie.siejuridicos.documento;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.format.DateTimeFormatter;

// Recompone el mensaje canónico de un documento (ver ApplicationSigningKeyService) con sus
// datos ACTUALES y confirma que la firma Ed25519 guardada sigue siendo válida contra la
// clave PÚBLICA de la llave con la que se firmó -- sin necesitar desenvolver ninguna clave
// privada. Si alguien con acceso directo a la base de datos cambia 'hash_contenido' (incluso
// recalculando un SHA-256 consistente con un PDF alterado), la firma guardada deja de
// verificar contra el mensaje canónico reconstruido: solo quien tiene la privada (nunca
// expuesta en claro) puede producir una firma válida para un hash nuevo.
@Service
public class ApplicationSignatureVerifier {

    private static final String ALGORITMO = "Ed25519";

    private final ApplicationSigningKeyService applicationSigningKeyService;

    public ApplicationSignatureVerifier(ApplicationSigningKeyService applicationSigningKeyService) {
        this.applicationSigningKeyService = applicationSigningKeyService;
    }

    // El orden de los campos importa: DocumentoSellaInfoService firma esto DESPUÉS de dejar
    // el documento en su estado final (ver el comentario ahí sobre por qué va al final del
    // pipeline de sellado) -- si se reordenara, la firma de aplicación quedaría calculada
    // sobre un estado intermedio y ApplicationSignatureVerifier siempre reportaría ALTERADO.
    public static String mensajeCanonico(DocumentoFirma documento) {
        return "SIE-DOCUMENTO|" + documento.getId() + "|" + documento.getHashContenido() + "|"
                + documento.getEstadoDocumento() + "|"
                + (documento.getFirmaAppGeneradaEn() != null
                        ? documento.getFirmaAppGeneradaEn().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        : "");
    }

    // true si no hay firma de aplicación que verificar todavía (documento aún no sellado) o
    // si la firma guardada es válida; false si la firma no coincide con el mensaje canónico actual.
    public boolean verificar(DocumentoFirma documento) {
        if (documento.getFirmaAppValor() == null || documento.getFirmaAppKid() == null) {
            return true;
        }
        FirmaAplicacionClave clave = applicationSigningKeyService.obtenerPorKid(documento.getFirmaAppKid());
        try {
            KeyFactory factory = KeyFactory.getInstance(ALGORITMO);
            PublicKey publica = factory.generatePublic(new X509EncodedKeySpec(clave.getClavePublica()));
            Signature verificador = Signature.getInstance(ALGORITMO);
            verificador.initVerify(publica);
            verificador.update(mensajeCanonico(documento).getBytes(StandardCharsets.UTF_8));
            return verificador.verify(documento.getFirmaAppValor());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo verificar la firma de aplicación del documento.", ex);
        }
    }
}
