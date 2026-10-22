package sie.siejuridicos.documento;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;

// Raíz criptográfica propia del motor de firma, separada a propósito de app.cifrado.clave
// (ver CifradoService, usado para el resto de datos sensibles de SIE): si uno de los dos
// secretos se filtra, el otro dominio queda a salvo -- el motor de firma es la pieza más
// sensible del sistema (documentos legales reales con datos de identidad y firmas
// manuscritas), merece aislamiento de radio de explosión propio.
//
// A diferencia del motor original (UCN, que usa 3 secretos raíz independientes:
// DOCUMENTOS_CIFRADO_KEY, un mapa de "claves maestras" rotables, y CADENA_EVENTOS_SECRET),
// aquí se deriva TODO (la KEK de envelope encryption, la llave HMAC de la cadena de
// eventos, la llave HMAC del código de verificación) de un único secreto raíz
// (DOCUMENTO_FIRMA_CLAVE_MAESTRA) con HMAC-SHA256 sobre una etiqueta fija distinta por uso
// -- mismo patrón ya usado en CifradoService.derivar(), solo que con un secreto raíz propio.
// Un solo secreto nuevo que generar y resguardar en vez de tres, con el mismo nivel de
// aislamiento entre usos (nunca se reutilizan los mismos bytes crudos para AES y HMAC).
//
// Sin verdadera rotación de KEK (kekId siempre "v1"): mismo nivel de madurez que
// CifradoService hoy (tampoco rota su llave maestra) -- si en el futuro hace falta rotar,
// la columna kek_id ya existe en el esquema para soportar una segunda etiqueta de
// derivación sin migración de datos.
@Service
public class DocumentoClaveMaestraService {

    public static final String KEK_ID_ACTUAL = "v1";

    private final byte[] kek;
    private final byte[] claveCadenaEventos;
    private final byte[] claveCodigoVerificacion;

    public DocumentoClaveMaestraService(@Value("${app.documento.clave-maestra}") String claveMaestraBase64) {
        byte[] claveMaestra;
        try {
            claveMaestra = Base64.getDecoder().decode(claveMaestraBase64);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "app.documento.clave-maestra (DOCUMENTO_FIRMA_CLAVE_MAESTRA) no es Base64 válido", ex);
        }
        if (claveMaestra.length != 32) {
            throw new IllegalStateException(
                    "app.documento.clave-maestra (DOCUMENTO_FIRMA_CLAVE_MAESTRA) debe decodificar a exactamente "
                            + "32 bytes (AES-256). Genera una nueva con: openssl rand -base64 32");
        }
        this.kek = derivar(claveMaestra, "sie-documento-kek-" + KEK_ID_ACTUAL);
        this.claveCadenaEventos = derivar(claveMaestra, "sie-documento-cadena-eventos");
        this.claveCodigoVerificacion = derivar(claveMaestra, "sie-documento-codigo-verificacion");
    }

    // Clave maestra (32 bytes, lista para AES-256) identificada por kekId -- hoy solo existe
    // KEK_ID_ACTUAL, cualquier otro id es un documento corrupto o de otro sistema.
    public byte[] obtenerClaveMaestra(String kekId) {
        if (!KEK_ID_ACTUAL.equals(kekId)) {
            throw new IllegalStateException("No hay una clave maestra configurada con id '" + kekId + "'.");
        }
        return kek;
    }

    public String kekIdActual() {
        return KEK_ID_ACTUAL;
    }

    public byte[] claveCadenaEventos() {
        return claveCadenaEventos;
    }

    public byte[] claveCodigoVerificacion() {
        return claveCodigoVerificacion;
    }

    private static byte[] derivar(byte[] claveMaestra, String etiqueta) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(claveMaestra, "HmacSHA256"));
            return mac.doFinal(etiqueta.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo derivar la llave del motor de firma", ex);
        }
    }
}
