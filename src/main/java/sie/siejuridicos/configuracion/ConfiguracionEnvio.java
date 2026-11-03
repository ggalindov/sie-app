package sie.siejuridicos.configuracion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

// Interruptor POR CATEGORÍA Y CANAL para los envíos automáticos (ver migración V62 y
// ClaveConfiguracionEnvio) -- distinto de app.bloqueo-total-clientes (el interruptor
// maestro estático, que sigue existiendo). La clave ES el enum en sí (string), no un id
// generado: una sola fila por combinación, nunca duplicados.
@Entity
@Table(name = "configuracion_envios")
public class ConfiguracionEnvio {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "clave", length = 50)
    private ClaveConfiguracionEnvio clave;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    @UpdateTimestamp
    @Column(name = "fecha_modificacion")
    private LocalDateTime fechaModificacion;

    public ClaveConfiguracionEnvio getClave() {
        return clave;
    }

    public void setClave(ClaveConfiguracionEnvio clave) {
        this.clave = clave;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public LocalDateTime getFechaModificacion() {
        return fechaModificacion;
    }
}
