package sie.siejuridicos.documento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

// Par de llaves Ed25519 con el que la aplicación firma criptográficamente el hash de cada
// documento sellado (ver ApplicationSigningKeyService) -- no es un certificado X.509 de una
// entidad de certificación acreditada, sigue siendo firma electrónica simple con evidencia
// técnica reforzada, nunca firma digital plena. La privada nunca se guarda en claro: viaja
// cifrada con la KEK activa. Las llaves rotadas ('activa=false') NUNCA se borran: documentos
// ya sellados siguen referenciando su 'kid' para poder re-verificarse.
@Entity
@Table(name = "firma_aplicacion_claves")
public class FirmaAplicacionClave {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String kid;

    @Column(nullable = false)
    private String algoritmo = "Ed25519";

    @Column(name = "clave_publica", nullable = false)
    private byte[] clavePublica;

    @Column(name = "clave_privada_cifrada", nullable = false)
    private byte[] clavePrivadaCifrada;

    @Column(name = "clave_privada_iv", nullable = false)
    private byte[] clavePrivadaIv;

    @Column(name = "kek_id", nullable = false)
    private String kekId;

    @Column(nullable = false)
    private boolean activa = true;

    @Column(name = "creada_en", nullable = false)
    private LocalDateTime creadaEn;

    @Column(name = "rotada_en")
    private LocalDateTime rotadaEn;

    protected FirmaAplicacionClave() {
    }

    public FirmaAplicacionClave(String kid, byte[] clavePublica, byte[] clavePrivadaCifrada, byte[] clavePrivadaIv, String kekId) {
        this.kid = kid;
        this.clavePublica = clavePublica;
        this.clavePrivadaCifrada = clavePrivadaCifrada;
        this.clavePrivadaIv = clavePrivadaIv;
        this.kekId = kekId;
        this.activa = true;
        this.creadaEn = LocalDateTime.now();
    }

    public void desactivar() {
        this.activa = false;
        this.rotadaEn = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public String getKid() {
        return kid;
    }

    public String getAlgoritmo() {
        return algoritmo;
    }

    public byte[] getClavePublica() {
        return clavePublica;
    }

    public byte[] getClavePrivadaCifrada() {
        return clavePrivadaCifrada;
    }

    public byte[] getClavePrivadaIv() {
        return clavePrivadaIv;
    }

    public String getKekId() {
        return kekId;
    }

    public boolean isActiva() {
        return activa;
    }

    public LocalDateTime getCreadaEn() {
        return creadaEn;
    }

    public LocalDateTime getRotadaEn() {
        return rotadaEn;
    }
}
