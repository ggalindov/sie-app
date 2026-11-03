package sie.siejuridicos.croma;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "croma_legal_pro_cache")
public class CromaLegalProCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "caso_id")
    private Long casoId;

    @Column(name = "radicado", nullable = false, unique = true, length = 50)
    private String radicado;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Column(name = "fecha_extraccion", nullable = false)
    private OffsetDateTime fechaExtraccion;

    @Column(name = "fecha_expiracion", nullable = false)
    private OffsetDateTime fechaExpiracion;

    public CromaLegalProCache() {
    }

    public CromaLegalProCache(Long casoId, String radicado, String payloadJson, OffsetDateTime fechaExtraccion, OffsetDateTime fechaExpiracion) {
        this.casoId = casoId;
        this.radicado = radicado;
        this.payloadJson = payloadJson;
        this.fechaExtraccion = fechaExtraccion;
        this.fechaExpiracion = fechaExpiracion;
    }

    public Long getId() {
        return id;
    }

    public Long getCasoId() {
        return casoId;
    }

    public void setCasoId(Long casoId) {
        this.casoId = casoId;
    }

    public String getRadicado() {
        return radicado;
    }

    public void setRadicado(String radicado) {
        this.radicado = radicado;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public OffsetDateTime getFechaExtraccion() {
        return fechaExtraccion;
    }

    public void setFechaExtraccion(OffsetDateTime fechaExtraccion) {
        this.fechaExtraccion = fechaExtraccion;
    }

    public OffsetDateTime getFechaExpiracion() {
        return fechaExpiracion;
    }

    public void setFechaExpiracion(OffsetDateTime fechaExpiracion) {
        this.fechaExpiracion = fechaExpiracion;
    }
}
