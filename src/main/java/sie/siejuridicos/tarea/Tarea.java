package sie.siejuridicos.tarea;

import jakarta.persistence.Column;
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
import sie.siejuridicos.caso.Caso;
import sie.siejuridicos.crm.ClienteCrm;
import sie.siejuridicos.usuario.UsuarioInterno;

import java.time.LocalDateTime;

// Tarea con responsable asignado, ligada a un caso puntual O a una persona del directorio del
// CRM (ver migración V46 -- antes, V45, exigía siempre un Caso, pero la mayoría de los clientes
// del CRM todavía no tiene ningún caso judicial vinculado, así que el botón de asignar tareas
// desde su ficha nunca tenía dónde engancharse). Al menos uno de los dos debe estar presente
// (ver el CHECK de la migración), nunca los dos null -- TareaService se encarga de esa regla.
// A diferencia de crm.TareaCrm (ligada a un ClienteCrm en general, con el nombre del responsable
// en texto libre sin validar), aquí el responsable es una relación real a UsuarioInterno --
// permite "¿qué tengo pendiente?" por usuario real (ver TareaRepository).
@Entity
@Table(name = "tareas")
public class Tarea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caso_id")
    private Caso caso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_crm_id")
    private ClienteCrm clienteCrm;

    @Column(name = "titulo", nullable = false)
    private String titulo;

    @Column(name = "descripcion")
    private String descripcion;

    @Column(name = "fecha_vencimiento")
    private LocalDateTime fechaVencimiento;

    @Enumerated(EnumType.STRING)
    @Column(name = "prioridad", nullable = false, length = 20)
    private PrioridadTarea prioridad = PrioridadTarea.MEDIA;

    @Column(name = "completada", nullable = false)
    private boolean completada = false;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_asignado_id", nullable = false)
    private UsuarioInterno usuarioAsignado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_creador_id", nullable = false)
    private UsuarioInterno usuarioCreador;

    @CreationTimestamp
    @Column(name = "fecha_creacion", updatable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_completada")
    private LocalDateTime fechaCompletada;

    public Long getId() {
        return id;
    }

    public Caso getCaso() {
        return caso;
    }

    public void setCaso(Caso caso) {
        this.caso = caso;
    }

    public ClienteCrm getClienteCrm() {
        return clienteCrm;
    }

    public void setClienteCrm(ClienteCrm clienteCrm) {
        this.clienteCrm = clienteCrm;
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

    public LocalDateTime getFechaVencimiento() {
        return fechaVencimiento;
    }

    public void setFechaVencimiento(LocalDateTime fechaVencimiento) {
        this.fechaVencimiento = fechaVencimiento;
    }

    public PrioridadTarea getPrioridad() {
        return prioridad;
    }

    public void setPrioridad(PrioridadTarea prioridad) {
        this.prioridad = prioridad;
    }

    public boolean isCompletada() {
        return completada;
    }

    public void setCompletada(boolean completada) {
        this.completada = completada;
    }

    public UsuarioInterno getUsuarioAsignado() {
        return usuarioAsignado;
    }

    public void setUsuarioAsignado(UsuarioInterno usuarioAsignado) {
        this.usuarioAsignado = usuarioAsignado;
    }

    public UsuarioInterno getUsuarioCreador() {
        return usuarioCreador;
    }

    public void setUsuarioCreador(UsuarioInterno usuarioCreador) {
        this.usuarioCreador = usuarioCreador;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public LocalDateTime getFechaCompletada() {
        return fechaCompletada;
    }

    public void setFechaCompletada(LocalDateTime fechaCompletada) {
        this.fechaCompletada = fechaCompletada;
    }
}
