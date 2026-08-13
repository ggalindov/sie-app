package sie.siejuridicos.testimonio;

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

@Entity
@Table(name = "testimonios_publicos")
public class TestimonioPublico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    private String empresa;

    private String cargo;

    @Column(nullable = false, length = 600)
    private String cita;

    @Column(nullable = false)
    private Integer calificacion;

    @Column(nullable = false)
    private String correo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoTestimonio estado = EstadoTestimonio.PENDIENTE;

    @CreationTimestamp
    @Column(name = "fecha_creacion", updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_moderacion")
    private LocalDateTime fechaModeracion;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getEmpresa() {
        return empresa;
    }

    public void setEmpresa(String empresa) {
        this.empresa = empresa;
    }

    public String getCargo() {
        return cargo;
    }

    public void setCargo(String cargo) {
        this.cargo = cargo;
    }

    public String getCita() {
        return cita;
    }

    public void setCita(String cita) {
        this.cita = cita;
    }

    public Integer getCalificacion() {
        return calificacion;
    }

    public void setCalificacion(Integer calificacion) {
        this.calificacion = calificacion;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public EstadoTestimonio getEstado() {
        return estado;
    }

    public void setEstado(EstadoTestimonio estado) {
        this.estado = estado;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public LocalDateTime getFechaModeracion() {
        return fechaModeracion;
    }

    public void setFechaModeracion(LocalDateTime fechaModeracion) {
        this.fechaModeracion = fechaModeracion;
    }
}
