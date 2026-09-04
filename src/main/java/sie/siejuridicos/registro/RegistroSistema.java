package sie.siejuridicos.registro;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// Una fila = un proceso real que el sistema ejecutó (sincronización, envío masivo,
// recordatorio programado, boletín, inicio de sesión), visible en /admin/registro. No
// reemplaza los logs del servidor (esos son para depurar código); esto es la bitácora legible
// para el admin de "qué hizo el sistema y cuándo" -- pedido explícito del usuario tras el
// incidente del envío masivo de correos, donde no había ninguna forma de ver en el panel qué
// se había disparado y con qué resultado.
//
// Nunca se guarda información sensible del cliente (nombres, correos, teléfonos) en
// descripcion/detalle -- solo conteos y nombres de fuentes/hojas, igual que el resto de los
// logs del sistema.
@Entity
@Table(name = "registro_sistema")
public class RegistroSistema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 40)
    private TipoRegistroSistema tipo;

    @Column(name = "descripcion", nullable = false)
    private String descripcion;

    // Detalle libre opcional (ej. "grupo por grupo: JUDICIALES ok, SUPERINTENDENCIA falló"),
    // nunca información del cliente.
    @Column(name = "detalle")
    private String detalle;

    @Column(name = "exitoso", nullable = false)
    private boolean exitoso;

    @CreationTimestamp
    @Column(name = "fecha_hora", updatable = false)
    private LocalDateTime fechaHora;

    public Long getId() {
        return id;
    }

    public TipoRegistroSistema getTipo() {
        return tipo;
    }

    public void setTipo(TipoRegistroSistema tipo) {
        this.tipo = tipo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDetalle() {
        return detalle;
    }

    public void setDetalle(String detalle) {
        this.detalle = detalle;
    }

    public boolean isExitoso() {
        return exitoso;
    }

    public void setExitoso(boolean exitoso) {
        this.exitoso = exitoso;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }
}
