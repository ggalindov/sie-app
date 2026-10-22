package sie.siejuridicos.documento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

// Par de llaves RSA + certificado X.509 (autofirmado por SIE Jurídicos, ver
// PadesSigningService) con el que se aplica la firma digital PAdES/PKCS#7 EMBEBIDA en el
// PDF final de cada documento sellado -- distinta de FirmaAplicacionClave (Ed25519, firma
// solo el hash en base de datos): esta llave produce una firma que el propio PDF lleva
// incrustada en su estructura (diccionario /Sig, ByteRange), reconocida nativamente por
// lectores como Adobe Acrobat Reader ("Documento firmado, no ha sido modificado desde la
// firma"), no solo verificable a través de esta aplicación. No es un certificado emitido
// por una entidad de certificación digital acreditada (ONAC): el lector mostrará la firma
// como criptográficamente válida pero el certificado como "no confiable" hasta que alguien
// lo agregue a su lista de confianza -- migrar a un certificado de una CA acreditada más
// adelante es cuestión de reemplazar esta llave, sin tocar el resto del motor (mismo
// patrón de rotación que FirmaAplicacionClave). La privada nunca se guarda en claro: viaja
// cifrada con la KEK activa. Las llaves rotadas ('activa=false') NUNCA se borran.
@Entity
@Table(name = "firma_pades_claves")
public class FirmaPadesClave {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String kid;

    @Column(nullable = false)
    private String algoritmo = "RSA-3072";

    // Certificado X.509 completo (DER), en claro: es información PÚBLICA por diseño -- viaja
    // incrustado en cada PDF firmado para que cualquier lector pueda verificar la firma.
    @Column(nullable = false)
    private byte[] certificado;

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

    @Column(name = "vigente_hasta", nullable = false)
    private LocalDateTime vigenteHasta;

    @Column(name = "rotada_en")
    private LocalDateTime rotadaEn;

    protected FirmaPadesClave() {
    }

    public FirmaPadesClave(String kid, byte[] certificado, byte[] clavePrivadaCifrada, byte[] clavePrivadaIv,
                            String kekId, LocalDateTime vigenteHasta) {
        this.kid = kid;
        this.certificado = certificado;
        this.clavePrivadaCifrada = clavePrivadaCifrada;
        this.clavePrivadaIv = clavePrivadaIv;
        this.kekId = kekId;
        this.activa = true;
        this.creadaEn = LocalDateTime.now();
        this.vigenteHasta = vigenteHasta;
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

    public byte[] getCertificado() {
        return certificado;
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

    public LocalDateTime getVigenteHasta() {
        return vigenteHasta;
    }

    public LocalDateTime getRotadaEn() {
        return rotadaEn;
    }
}
