package sie.siejuridicos.documento;

import org.springframework.stereotype.Service;

// Envelope encryption: cada documento cifra su contenido con una DEK (clave de datos)
// aleatoria y propia, y esa DEK se envuelve con la KEK activa (ver
// DocumentoClaveMaestraService) -- en vez de que una única clave simétrica plana cifre
// directamente todos los documentos. Esto limita el radio de exposición de una KEK
// filtrada: con ella sola solo se pueden desenvolver DEKs una por una, nunca se obtiene el
// texto plano de todos los documentos de un solo golpe.
@Service
public class EnvelopeEncryptionService {

    public record ResultadoCifrado(byte[] contenidoCifrado, byte[] contenidoIv, byte[] dekCifrado, byte[] dekIv, String kekId) {
    }

    private final AesGcmCipher aesGcmCipher;
    private final DocumentoClaveMaestraService claveMaestraService;

    public EnvelopeEncryptionService(AesGcmCipher aesGcmCipher, DocumentoClaveMaestraService claveMaestraService) {
        this.aesGcmCipher = aesGcmCipher;
        this.claveMaestraService = claveMaestraService;
    }

    public ResultadoCifrado cifrar(byte[] datos) {
        byte[] dek = aesGcmCipher.generarClaveAleatoria();
        AesGcmCipher.Resultado contenido = aesGcmCipher.cifrar(datos, dek);
        String kekId = claveMaestraService.kekIdActual();
        AesGcmCipher.Resultado dekEnvuelta = aesGcmCipher.cifrar(dek, claveMaestraService.obtenerClaveMaestra(kekId));
        return new ResultadoCifrado(contenido.datosCifrados(), contenido.iv(), dekEnvuelta.datosCifrados(), dekEnvuelta.iv(), kekId);
    }

    // Inverso de cifrar(byte[]) para datos que no son un DocumentoFirma completo (ej. el
    // trazo vectorial de una firma).
    public byte[] descifrar(ResultadoCifrado cifrado) {
        byte[] dek = aesGcmCipher.descifrar(cifrado.dekCifrado(), cifrado.dekIv(),
                claveMaestraService.obtenerClaveMaestra(cifrado.kekId()));
        return aesGcmCipher.descifrar(cifrado.contenidoCifrado(), cifrado.contenidoIv(), dek);
    }

    public byte[] leerContenido(DocumentoFirma documento) {
        if (!documento.isContenidoCifrado()) {
            return documento.getContenidoPdf();
        }
        byte[] dek = aesGcmCipher.descifrar(documento.getDekCifrado(), documento.getDekIv(),
                claveMaestraService.obtenerClaveMaestra(documento.getKekId()));
        return aesGcmCipher.descifrar(documento.getContenidoPdf(), documento.getContenidoIv(), dek);
    }
}
