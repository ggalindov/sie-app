package sie.siejuridicos.solicitud;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import sie.siejuridicos.crm.EtapaPipeline;
import sie.siejuridicos.usuario.UsuarioInterno;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

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

    // Responsables (abogados/admin) vinculados a la reunion (ver SolicitudService.agendarCita):
    // vacio hasta que se agenda la primera cita. Reemplaza al antiguo campo unico
    // abogadoAsignado (@ManyToOne) -- pedido explicito del usuario: "vincular a 1 o mas
    // responsables a la llamada, quiere decir mas abogados". Es lo que separa el calendario por
    // rol (SolicitudService.listarCalendario): ADMIN_GENERAL ve todas las reuniones, un ABOGADO
    // solo aquellas donde el es uno de los responsables.
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "solicitud_responsables",
            joinColumns = @JoinColumn(name = "solicitud_id"),
            inverseJoinColumns = @JoinColumn(name = "usuario_interno_id")
    )
    private Set<UsuarioInterno> responsables = new LinkedHashSet<>();

    @Column(name = "link_reunion")
    private String linkReunion;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_reunion", nullable = false)
    private TipoReunion tipoReunion = TipoReunion.VIRTUAL;

    @Column(name = "lugar_reunion")
    private String lugarReunion;

    @Enumerated(EnumType.STRING)
    @Column(name = "etapa_pipeline", length = 30)
    private EtapaPipeline etapaPipeline = EtapaPipeline.NUEVO;

    @Column(name = "valor_estimado")
    private BigDecimal valorEstimado;

    @Column(name = "area_practica", length = 100)
    private String areaPractica;

    @Column(name = "cliente_crm_id")
    private Long clienteCrmId;

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

    public void setFechaActualizacionEstado(LocalDateTime fechaActualizacionEstado) {
        this.fechaActualizacionEstado = fechaActualizacionEstado;
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

    public Set<UsuarioInterno> getResponsables() {
        return responsables;
    }

    public void setResponsables(Set<UsuarioInterno> responsables) {
        this.responsables = responsables;
    }

    public String getLinkReunion() {
        return linkReunion;
    }

    public void setLinkReunion(String linkReunion) {
        this.linkReunion = linkReunion;
    }

    public TipoReunion getTipoReunion() {
        return tipoReunion;
    }

    public void setTipoReunion(TipoReunion tipoReunion) {
        this.tipoReunion = tipoReunion;
    }

    public String getLugarReunion() {
        return lugarReunion;
    }

    public void setLugarReunion(String lugarReunion) {
        this.lugarReunion = lugarReunion;
    }

    public EtapaPipeline getEtapaPipeline() {
        return etapaPipeline;
    }

    public void setEtapaPipeline(EtapaPipeline etapaPipeline) {
        this.etapaPipeline = etapaPipeline;
    }

    public BigDecimal getValorEstimado() {
        return valorEstimado;
    }

    public void setValorEstimado(BigDecimal valorEstimado) {
        this.valorEstimado = valorEstimado;
    }

    public String getAreaPractica() {
        return areaPractica;
    }

    public void setAreaPractica(String areaPractica) {
        this.areaPractica = areaPractica;
    }

    public Long getClienteCrmId() {
        return clienteCrmId;
    }

    public void setClienteCrmId(Long clienteCrmId) {
        this.clienteCrmId = clienteCrmId;
    }
}
