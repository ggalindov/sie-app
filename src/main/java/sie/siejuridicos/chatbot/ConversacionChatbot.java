package sie.siejuridicos.chatbot;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import sie.siejuridicos.solicitud.Solicitud;

import java.time.LocalDateTime;

@Entity
@Table(name = "conversaciones_chatbot")
public class ConversacionChatbot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_solicitud")
    private Solicitud solicitud;

    @CreationTimestamp
    @Column(name = "fecha", updatable = false)
    private LocalDateTime fecha;

    @Column(name = "cantidad_mensajes", nullable = false)
    private Integer cantidadMensajes = 0;

    private String resumen;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Solicitud getSolicitud() {
        return solicitud;
    }

    public void setSolicitud(Solicitud solicitud) {
        this.solicitud = solicitud;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public Integer getCantidadMensajes() {
        return cantidadMensajes;
    }

    public void setCantidadMensajes(Integer cantidadMensajes) {
        this.cantidadMensajes = cantidadMensajes;
    }

    public String getResumen() {
        return resumen;
    }

    public void setResumen(String resumen) {
        this.resumen = resumen;
    }
}
