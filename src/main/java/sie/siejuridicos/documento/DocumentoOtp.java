package sie.siejuridicos.documento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

// Código de un solo uso (OTP) enviado a un DocumentoFirmante para verificar su identidad
// antes de permitirle capturar la firma. Solo se guarda el hash del código (nunca el valor
// plano).
@Entity
@Table(name = "documento_otp")
public class DocumentoOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_firmante_id", nullable = false)
    private DocumentoFirmante firmante;

    @Column(nullable = false)
    private String canal;

    @Column(name = "codigo_hash", nullable = false)
    private String codigoHash;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    @Column(nullable = false)
    private int intentos;

    @Column(name = "intentos_maximos", nullable = false)
    private int intentosMaximos = 5;

    @Column(name = "verificado_en")
    private LocalDateTime verificadoEn;

    @Column(name = "ip_solicitud")
    private String ipSolicitud;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    protected DocumentoOtp() {
    }

    public DocumentoOtp(DocumentoFirmante firmante, String canal, String codigoHash, LocalDateTime expiraEn, String ipSolicitud) {
        this.firmante = firmante;
        this.canal = canal;
        this.codigoHash = codigoHash;
        this.expiraEn = expiraEn;
        this.ipSolicitud = ipSolicitud;
        this.creadoEn = LocalDateTime.now();
    }

    public boolean expirado() {
        return LocalDateTime.now().isAfter(expiraEn);
    }

    public boolean agotoIntentos() {
        return intentos >= intentosMaximos;
    }

    public void registrarIntentoFallido() {
        this.intentos++;
    }

    public void marcarVerificado() {
        this.verificadoEn = LocalDateTime.now();
    }

    public UUID getId() {
        return id;
    }

    public DocumentoFirmante getFirmante() {
        return firmante;
    }

    public String getCanal() {
        return canal;
    }

    public String getCodigoHash() {
        return codigoHash;
    }

    public LocalDateTime getExpiraEn() {
        return expiraEn;
    }

    public int getIntentos() {
        return intentos;
    }

    public int getIntentosMaximos() {
        return intentosMaximos;
    }

    public void setIntentosMaximos(int intentosMaximos) {
        this.intentosMaximos = intentosMaximos;
    }

    public LocalDateTime getVerificadoEn() {
        return verificadoEn;
    }

    public String getIpSolicitud() {
        return ipSolicitud;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }
}
