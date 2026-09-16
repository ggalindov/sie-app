package sie.siejuridicos.crm;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import sie.siejuridicos.common.cifrado.CampoCifradoConverter;

import java.time.LocalDateTime;

@Entity
@Table(name = "crm_actividades")
public class ActividadCrm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_crm_id", nullable = false)
    private ClienteCrm clienteCrm;

    @Column(name = "solicitud_id")
    private Long solicitudId;

    @Column(name = "caso_id")
    private Long casoId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private TipoActividadCrm tipo;

    @Column(name = "titulo", nullable = false)
    private String titulo;

    @Convert(converter = CampoCifradoConverter.class)
    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "usuario_nombre", length = 150)
    private String usuarioNombre;

    @Column(name = "fecha_actividad", nullable = false)
    private LocalDateTime fechaActividad = LocalDateTime.now();

    @CreationTimestamp
    @Column(name = "fecha_creacion", updatable = false)
    private LocalDateTime fechaCreacion;

    public Long getId() {
        return id;
    }

    public ClienteCrm getClienteCrm() {
        return clienteCrm;
    }

    public void setClienteCrm(ClienteCrm clienteCrm) {
        this.clienteCrm = clienteCrm;
    }

    public Long getSolicitudId() {
        return solicitudId;
    }

    public void setSolicitudId(Long solicitudId) {
        this.solicitudId = solicitudId;
    }

    public Long getCasoId() {
        return casoId;
    }

    public void setCasoId(Long casoId) {
        this.casoId = casoId;
    }

    public TipoActividadCrm getTipo() {
        return tipo;
    }

    public void setTipo(TipoActividadCrm tipo) {
        this.tipo = tipo;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getUsuarioNombre() {
        return usuarioNombre;
    }

    public void setUsuarioNombre(String usuarioNombre) {
        this.usuarioNombre = usuarioNombre;
    }

    public LocalDateTime getFechaActividad() {
        return fechaActividad;
    }

    public void setFechaActividad(LocalDateTime fechaActividad) {
        this.fechaActividad = fechaActividad;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }
}
