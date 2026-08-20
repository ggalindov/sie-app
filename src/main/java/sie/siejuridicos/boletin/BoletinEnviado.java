package sie.siejuridicos.boletin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

// Log de auditoría de los envíos automáticos del boletín (ver
// ArticuloService.notificarPublicacion): se dispara de inmediato cada vez que se publica
// un artículo o una noticia. No hay composición manual: cada fila es un envío que ya ocurrió.
@Entity
@Table(name = "boletines_enviados")
public class BoletinEnviado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cantidad_publicaciones", nullable = false)
    private Integer cantidadPublicaciones;

    @Column(name = "cantidad_destinatarios", nullable = false)
    private Integer cantidadDestinatarios;

    @CreationTimestamp
    @Column(name = "fecha_envio", updatable = false)
    private LocalDateTime fechaEnvio;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getCantidadPublicaciones() {
        return cantidadPublicaciones;
    }

    public void setCantidadPublicaciones(Integer cantidadPublicaciones) {
        this.cantidadPublicaciones = cantidadPublicaciones;
    }

    public Integer getCantidadDestinatarios() {
        return cantidadDestinatarios;
    }

    public void setCantidadDestinatarios(Integer cantidadDestinatarios) {
        this.cantidadDestinatarios = cantidadDestinatarios;
    }

    public LocalDateTime getFechaEnvio() {
        return fechaEnvio;
    }
}
