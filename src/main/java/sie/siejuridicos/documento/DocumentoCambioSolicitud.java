package sie.siejuridicos.documento;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import sie.siejuridicos.usuario.UsuarioInterno;

import java.time.LocalDateTime;
import java.util.UUID;

// Doble confirmación (ADMIN_GENERAL + ABOGADO) exigida para cualquier movimiento sensible
// sobre un documento ya emitido/sellado por el motor de firma: el sistema no permite
// modificar un documento sellado directamente, solo a través de una solicitud como esta,
// aprobada por dos roles distintos. Quien la crea queda auto-confirmado en su propio rol;
// alguien con el rol contrario debe confirmar el otro lado antes de poder ejecutarla (ver
// DocumentChangeRequestService).
@Entity
@Table(name = "documento_cambio_solicitudes")
public class DocumentoCambioSolicitud {

    public static final String ACCION_INVALIDAR = "INVALIDAR";
    public static final String ACCION_RE_EMITIR = "RE_EMITIR";
    public static final String ACCION_CORREGIR_METADATO = "CORREGIR_METADATO";
    public static final String ACCION_ANULAR_FIRMA_PARTE = "ANULAR_FIRMA_PARTE";
    public static final String ACCION_REVERTIR_ALTERADO = "REVERTIR_ALTERADO";

    public static final String ESTADO_PENDIENTE = "PENDIENTE";
    public static final String ESTADO_CONFIRMADA_PARCIAL = "CONFIRMADA_PARCIAL";
    public static final String ESTADO_APROBADA = "APROBADA";
    public static final String ESTADO_EJECUTADA = "EJECUTADA";
    public static final String ESTADO_RECHAZADA = "RECHAZADA";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_id", nullable = false)
    private DocumentoFirma documento;

    @Column(name = "tipo_accion", nullable = false)
    private String tipoAccion;

    @Column(nullable = false)
    private String justificacion;

    @Column(name = "payload_json")
    private String payloadJson;

    @Column(nullable = false)
    private String estado = ESTADO_PENDIENTE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "solicitado_por_staff_id", nullable = false)
    private UsuarioInterno solicitadoPor;

    @Column(name = "solicitado_en", nullable = false)
    private LocalDateTime solicitadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmacion_gerente_staff_id")
    private UsuarioInterno confirmacionGerente;

    @Column(name = "confirmacion_gerente_en")
    private LocalDateTime confirmacionGerenteEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmacion_abogado_staff_id")
    private UsuarioInterno confirmacionAbogado;

    @Column(name = "confirmacion_abogado_en")
    private LocalDateTime confirmacionAbogadoEn;

    @Column(name = "ejecutado_en")
    private LocalDateTime ejecutadoEn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rechazado_por_staff_id")
    private UsuarioInterno rechazadoPor;

    @Column(name = "rechazado_en")
    private LocalDateTime rechazadoEn;

    @Column(name = "motivo_rechazo")
    private String motivoRechazo;

    protected DocumentoCambioSolicitud() {
    }

    public DocumentoCambioSolicitud(DocumentoFirma documento, String tipoAccion, String justificacion, String payloadJson,
                                     UsuarioInterno solicitadoPor) {
        this.documento = documento;
        this.tipoAccion = tipoAccion;
        this.justificacion = justificacion;
        this.payloadJson = payloadJson;
        this.solicitadoPor = solicitadoPor;
        this.solicitadoEn = LocalDateTime.now();
    }

    public boolean confirmadaPorAmbosRoles() {
        return confirmacionGerente != null && confirmacionAbogado != null;
    }

    public UUID getId() {
        return id;
    }

    public DocumentoFirma getDocumento() {
        return documento;
    }

    public String getTipoAccion() {
        return tipoAccion;
    }

    public String getJustificacion() {
        return justificacion;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public UsuarioInterno getSolicitadoPor() {
        return solicitadoPor;
    }

    public LocalDateTime getSolicitadoEn() {
        return solicitadoEn;
    }

    public UsuarioInterno getConfirmacionGerente() {
        return confirmacionGerente;
    }

    public LocalDateTime getConfirmacionGerenteEn() {
        return confirmacionGerenteEn;
    }

    public void confirmarComoGerente(UsuarioInterno gerente) {
        this.confirmacionGerente = gerente;
        this.confirmacionGerenteEn = LocalDateTime.now();
    }

    public UsuarioInterno getConfirmacionAbogado() {
        return confirmacionAbogado;
    }

    public LocalDateTime getConfirmacionAbogadoEn() {
        return confirmacionAbogadoEn;
    }

    public void confirmarComoAbogado(UsuarioInterno abogado) {
        this.confirmacionAbogado = abogado;
        this.confirmacionAbogadoEn = LocalDateTime.now();
    }

    public LocalDateTime getEjecutadoEn() {
        return ejecutadoEn;
    }

    public void marcarEjecutada() {
        this.estado = ESTADO_EJECUTADA;
        this.ejecutadoEn = LocalDateTime.now();
    }

    public UsuarioInterno getRechazadoPor() {
        return rechazadoPor;
    }

    public LocalDateTime getRechazadoEn() {
        return rechazadoEn;
    }

    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    public void rechazar(UsuarioInterno staff, String motivo) {
        this.estado = ESTADO_RECHAZADA;
        this.rechazadoPor = staff;
        this.rechazadoEn = LocalDateTime.now();
        this.motivoRechazo = motivo;
    }
}
