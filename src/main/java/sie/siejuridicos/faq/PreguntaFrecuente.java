package sie.siejuridicos.faq;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "preguntas_frecuentes")
public class PreguntaFrecuente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pregunta_normalizada", nullable = false, unique = true)
    private String preguntaNormalizada;

    @Column(name = "pregunta_ejemplo", nullable = false)
    private String preguntaEjemplo;

    @Column(name = "respuesta_sugerida")
    private String respuestaSugerida;

    @Column(name = "respuesta_final")
    private String respuestaFinal;

    @Column(nullable = false)
    private Integer conteo = 1;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoPreguntaFrecuente estado = EstadoPreguntaFrecuente.CANDIDATA;

    @Column(name = "fecha_primera_vez")
    private LocalDateTime fechaPrimeraVez;

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    @Column(name = "fecha_moderacion")
    private LocalDateTime fechaModeracion;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPreguntaNormalizada() {
        return preguntaNormalizada;
    }

    public void setPreguntaNormalizada(String preguntaNormalizada) {
        this.preguntaNormalizada = preguntaNormalizada;
    }

    public String getPreguntaEjemplo() {
        return preguntaEjemplo;
    }

    public void setPreguntaEjemplo(String preguntaEjemplo) {
        this.preguntaEjemplo = preguntaEjemplo;
    }

    public String getRespuestaSugerida() {
        return respuestaSugerida;
    }

    public void setRespuestaSugerida(String respuestaSugerida) {
        this.respuestaSugerida = respuestaSugerida;
    }

    public String getRespuestaFinal() {
        return respuestaFinal;
    }

    public void setRespuestaFinal(String respuestaFinal) {
        this.respuestaFinal = respuestaFinal;
    }

    public Integer getConteo() {
        return conteo;
    }

    public void setConteo(Integer conteo) {
        this.conteo = conteo;
    }

    public EstadoPreguntaFrecuente getEstado() {
        return estado;
    }

    public void setEstado(EstadoPreguntaFrecuente estado) {
        this.estado = estado;
    }

    public LocalDateTime getFechaPrimeraVez() {
        return fechaPrimeraVez;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }

    public LocalDateTime getFechaModeracion() {
        return fechaModeracion;
    }

    public void setFechaModeracion(LocalDateTime fechaModeracion) {
        this.fechaModeracion = fechaModeracion;
    }
}
