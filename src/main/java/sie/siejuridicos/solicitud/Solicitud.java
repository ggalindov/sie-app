package sie.siejuridicos.solicitud;

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
@Table(name = "solicitudes")
public class Solicitud {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String correo;

    private String telefono;

    @Column(nullable = false)
    private String mensaje;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrigenSolicitud origen;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoSolicitud estado = EstadoSolicitud.NUEVO;

    @Column(name = "notas_internas")
    private String notasInternas;

    @CreationTimestamp
    @Column(name = "fecha_creacion", updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion_estado")
    private LocalDateTime fechaActualizacionEstado;

    @Column(name = "fecha_cita")
    private LocalDateTime fechaCita;

    @Column(name = "recordatorio_enviado", nullable = false)
    private boolean recordatorioEnviado = false;

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

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public OrigenSolicitud getOrigen() {
        return origen;
    }

    public void setOrigen(OrigenSolicitud origen) {
        this.origen = origen;
    }

    public EstadoSolicitud getEstado() {
        return estado;
    }

    public void setEstado(EstadoSolicitud estado) {
        this.estado = estado;
    }

    public String getNotasInternas() {
        return notasInternas;
    }

    public void setNotasInternas(String notasInternas) {
        this.notasInternas = notasInternas;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public LocalDateTime getFechaActualizacionEstado() {
        return fechaActualizacionEstado;
    }

    public LocalDateTime getFechaCita() {
        return fechaCita;
    }

    public void setFechaCita(LocalDateTime fechaCita) {
        this.fechaCita = fechaCita;
    }

    public boolean isRecordatorioEnviado() {
        return recordatorioEnviado;
    }

    public void setRecordatorioEnviado(boolean recordatorioEnviado) {
        this.recordatorioEnviado = recordatorioEnviado;
    }
}
