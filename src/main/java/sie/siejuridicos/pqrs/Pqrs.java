package sie.siejuridicos.pqrs;

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

// descripcion/respuesta son @Column normal (no @Lob): la columna es TEXT en Postgres, y
// @Lob sobre un String mapea a oid, no a TEXT (ver CLAUDE.md 4.5 gotcha #3, mismo bug que
// ya afectó a Articulo.contenido y Solicitud.mensaje).
@Entity
@Table(name = "pqrs")
public class Pqrs {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String radicado;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TipoPqrs tipo;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String correo;

    private String telefono;

    @Column(nullable = false, length = 200)
    private String asunto;

    @Column(nullable = false)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoPqrs estado = EstadoPqrs.RECIBIDO;

    private String respuesta;

    @Column(name = "respondido_por")
    private String respondidoPor;

    @CreationTimestamp
    @Column(name = "fecha_creacion", updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_limite_respuesta", nullable = false)
    private LocalDateTime fechaLimiteRespuesta;

    @Column(name = "fecha_respuesta")
    private LocalDateTime fechaRespuesta;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRadicado() {
        return radicado;
    }

    public void setRadicado(String radicado) {
        this.radicado = radicado;
    }

    public TipoPqrs getTipo() {
        return tipo;
    }

    public void setTipo(TipoPqrs tipo) {
        this.tipo = tipo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getAsunto() {
        return asunto;
    }

    public void setAsunto(String asunto) {
        this.asunto = asunto;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public EstadoPqrs getEstado() {
        return estado;
    }

    public void setEstado(EstadoPqrs estado) {
        this.estado = estado;
    }

    public String getRespuesta() {
        return respuesta;
    }

    public void setRespuesta(String respuesta) {
        this.respuesta = respuesta;
    }

    public String getRespondidoPor() {
        return respondidoPor;
    }

    public void setRespondidoPor(String respondidoPor) {
        this.respondidoPor = respondidoPor;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public LocalDateTime getFechaLimiteRespuesta() {
        return fechaLimiteRespuesta;
    }

    public void setFechaLimiteRespuesta(LocalDateTime fechaLimiteRespuesta) {
        this.fechaLimiteRespuesta = fechaLimiteRespuesta;
    }

    public LocalDateTime getFechaRespuesta() {
        return fechaRespuesta;
    }

    public void setFechaRespuesta(LocalDateTime fechaRespuesta) {
        this.fechaRespuesta = fechaRespuesta;
    }
}
