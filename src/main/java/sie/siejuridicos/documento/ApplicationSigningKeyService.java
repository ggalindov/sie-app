package sie.siejuridicos.documento;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// Genera, guarda cifrada y usa la llave de firma criptográfica propia de la aplicación
// (Ed25519, nativo en la JCE desde Java 15: sin dependencia nueva). No es un certificado
// X.509 de una entidad de certificación acreditada -- sigue siendo firma electrónica simple
// con evidencia técnica reforzada, nunca firma digital -- pero permite que un tercero con
// la clave PÚBLICA verifique la integridad de un documento sin depender de un secreto
// compartido (a diferencia del HMAC de CodigoVerificacionCalculator).
@Service
public class ApplicationSigningKeyService {

    private static final String ALGORITMO = "Ed25519";

    private final FirmaAplicacionClaveRepository repository;
    private final AesGcmCipher aesGcmCipher;
    private final DocumentoClaveMaestraService claveMaestraService;

    // Cierra en memoria la carrera de arranque en frío: si dos firmas concurrentes
    // encuentran "ninguna llave activa" (nunca se firmó nada todavía), cada una generaría y
    // guardaría la suya -- y a partir de ahí repository.findByActivaTrue() (Optional de
    // resultado ÚNICO) rompe con IncorrectResultSizeDataAccessException en TODA firma
    // futura. Solo protege contra múltiples hilos de la MISMA instancia -- suficiente
    // porque el backend corre como réplica única.
    private final Object claveActivaLock = new Object();

    public ApplicationSigningKeyService(FirmaAplicacionClaveRepository repository, AesGcmCipher aesGcmCipher,
                                         DocumentoClaveMaestraService claveMaestraService) {
        this.repository = repository;
        this.aesGcmCipher = aesGcmCipher;
        this.claveMaestraService = claveMaestraService;
    }

    public record FirmaResultado(byte[] firma, String kid) {
    }

    @Transactional
    public FirmaResultado firmar(byte[] mensaje) {
        FirmaAplicacionClave clave = obtenerOCrearClaveActiva();
        byte[] privadaBytes = aesGcmCipher.descifrar(clave.getClavePrivadaCifrada(), clave.getClavePrivadaIv(),
                claveMaestraService.obtenerClaveMaestra(clave.getKekId()));
        try {
            KeyFactory factory = KeyFactory.getInstance(ALGORITMO);
            PrivateKey privada = factory.generatePrivate(new PKCS8EncodedKeySpec(privadaBytes));
            Signature firmador = Signature.getInstance(ALGORITMO);
            firmador.initSign(privada);
            firmador.update(mensaje);
            return new FirmaResultado(firmador.sign(), clave.getKid());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo firmar con la llave de aplicación.", ex);
        } finally {
            Arrays.fill(privadaBytes, (byte) 0);
        }
    }

    // Genera un par de llaves nuevo, desactiva el anterior (si existe) sin borrarlo, y lo guarda.
    @Transactional
    public FirmaAplicacionClave rotar() {
        repository.findByActivaTrue().ifPresent(actual -> {
            actual.desactivar();
            repository.save(actual);
        });
        return generarYGuardarNueva();
    }

    // Historial completo (activas e inactivas) para auditoría -- nunca expone la privada cifrada.
    public List<FirmaAplicacionClave> historial() {
        return repository.findAll();
    }

    public FirmaAplicacionClave obtenerPorKid(String kid) {
        return repository.findByKid(kid)
                .orElseThrow(() -> new IllegalStateException("No se encontró la llave de firma de aplicación '" + kid + "'."));
    }

    private FirmaAplicacionClave obtenerOCrearClaveActiva() {
        Optional<FirmaAplicacionClave> existente = repository.findByActivaTrue();
        if (existente.isPresent()) {
            return existente.get();
        }
        synchronized (claveActivaLock) {
            return repository.findByActivaTrue().orElseGet(this::generarYGuardarNueva);
        }
    }

    private FirmaAplicacionClave generarYGuardarNueva() {
        try {
            KeyPairGenerator generador = KeyPairGenerator.getInstance(ALGORITMO);
            KeyPair par = generador.generateKeyPair();
            byte[] publica = par.getPublic().getEncoded();
            byte[] privada = par.getPrivate().getEncoded();
            String kekId = claveMaestraService.kekIdActual();
            AesGcmCipher.Resultado privadaCifrada = aesGcmCipher.cifrar(privada, claveMaestraService.obtenerClaveMaestra(kekId));
            Arrays.fill(privada, (byte) 0);
            FirmaAplicacionClave clave = new FirmaAplicacionClave(UUID.randomUUID().toString(), publica,
                    privadaCifrada.datosCifrados(), privadaCifrada.iv(), kekId);
            return repository.save(clave);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("No se pudo generar una nueva llave de firma de aplicación (Ed25519).", ex);
        }
    }
}
