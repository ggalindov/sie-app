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

// Un hito de la línea de tiempo técnica de un documento (ver DocumentoTimelineService, única
// puerta de entrada para insertar aquí): cadena de custodia para sostener el valor
// probatorio de la firma ante un eventual litigio (Ley 527/1999, CGP art. 244) -- cada
// evento estampa el hash del documento en ese instante y encadena su HMAC con el del evento
// anterior del mismo documento (ver HashChainCalculator).
@Entity
@Table(name = "documento_eventos")
public class DocumentoEvento {

    public static final String TIPO_DILIGENCIADO = "DILIGENCIADO";
    public static final String TIPO_EMITIDO = "EMITIDO";
    public static final String TIPO_FIRMADO_STAFF = "FIRMADO_STAFF";
    public static final String TIPO_NOTIFICADO = "NOTIFICADO";
    public static final String TIPO_EN_FIRMA_PARTES_INICIADO = "EN_FIRMA_PARTES_INICIADO";
    public static final String TIPO_FIRMADO_POR_PARTE = "FIRMADO_POR_PARTE";
    public static final String TIPO_FIRMA_RECHAZADA = "FIRMA_RECHAZADA";
    public static final String TIPO_SELLADO = "SELLADO";
    public static final String TIPO_REMITIDO = "REMITIDO";
    public static final String TIPO_ENCRIPTADO = "ENCRIPTADO";
    public static final String TIPO_ALTERADO_DETECTADO = "ALTERADO_DETECTADO";
    public static final String TIPO_VERIFICACION_CONSULTADA = "VERIFICACION_CONSULTADA";
    public static final String TIPO_SOLICITUD_CAMBIO_CREADA = "SOLICITUD_CAMBIO_CREADA";
    public static final String TIPO_SOLICITUD_CAMBIO_EJECUTADA = "SOLICITUD_CAMBIO_EJECUTADA";
    public static final String TIPO_LLAVE_APLICACION_ROTADA = "LLAVE_APLICACION_ROTADA";
    public static final String TIPO_SELLO_TIEMPO_APLICADO = "SELLO_TIEMPO_APLICADO";
    public static final String TIPO_SELLO_TIEMPO_FALLIDO = "SELLO_TIEMPO_FALLIDO";
    public static final String TIPO_CERTIFICADO_EVIDENCIA_DESCARGADO = "CERTIFICADO_EVIDENCIA_DESCARGADO";

    public static final String TIPO_RECORDATORIO_ENVIADO = "RECORDATORIO_ENVIADO";
    public static final String TIPO_ENLACE_REVOCADO = "ENLACE_REVOCADO";
    public static final String TIPO_PROCESO_EXPIRADO = "PROCESO_EXPIRADO";
    public static final String TIPO_PROCESO_ANULADO = "PROCESO_ANULADO";

    public static final String ACTOR_STAFF = "STAFF";
    public static final String ACTOR_PARTE = "PARTE";
    public static final String ACTOR_SISTEMA = "SISTEMA";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_id", nullable = false)
    private DocumentoFirma documento;

    @Column(name = "tipo_evento", nullable = false)
    private String tipoEvento;

    @Column(name = "actor_tipo", nullable = false)
    private String actorTipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private UsuarioInterno staff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_firmante_id")
    private DocumentoFirmante firmante;

    private String descripcion;

    private String ip;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "hash_documento_en_evento")
    private String hashDocumentoEnEvento;

    @Column(name = "ocurrido_en", nullable = false)
    private LocalDateTime ocurridoEn;

    // Correlativo por documento (1, 2, 3...); ver HashChainCalculator/DocumentoTimelineService.
    @Column(nullable = false)
    private Long secuencia;

    @Column(name = "hash_evento", nullable = false)
    private String hashEvento;

    @Column(name = "hash_evento_anterior", nullable = false)
    private String hashEventoAnterior;

    protected DocumentoEvento() {
    }

    // El llamador (siempre DocumentoTimelineService) es responsable de resolver secuencia/
    // hashEventoAnterior/hashEvento ANTES de construir, y de pasar el mismo 'ocurridoEn' que
    // usó para calcular hashEvento -- si esta clase generara su propio timestamp con now(),
    // no coincidiría con el ya usado en el HMAC.
    public DocumentoEvento(DocumentoFirma documento, String tipoEvento, String actorTipo, UsuarioInterno staff,
                            DocumentoFirmante firmante, String descripcion, String ip, String userAgent,
                            String hashDocumentoEnEvento, long secuencia, String hashEventoAnterior,
                            String hashEvento, LocalDateTime ocurridoEn) {
        this.documento = documento;
        this.tipoEvento = tipoEvento;
        this.actorTipo = actorTipo;
        this.staff = staff;
        this.firmante = firmante;
        this.descripcion = descripcion;
        this.ip = ip;
        this.userAgent = userAgent;
        this.hashDocumentoEnEvento = hashDocumentoEnEvento;
        this.secuencia = secuencia;
        this.hashEventoAnterior = hashEventoAnterior;
        this.hashEvento = hashEvento;
        this.ocurridoEn = ocurridoEn;
    }

    public UUID getId() {
        return id;
    }

    public DocumentoFirma getDocumento() {
        return documento;
    }

    public String getTipoEvento() {
        return tipoEvento;
    }

    public String getActorTipo() {
        return actorTipo;
    }

    public UsuarioInterno getStaff() {
        return staff;
    }

    public DocumentoFirmante getFirmante() {
        return firmante;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getIp() {
        return ip;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getHashDocumentoEnEvento() {
        return hashDocumentoEnEvento;
    }

    public LocalDateTime getOcurridoEn() {
        return ocurridoEn;
    }

    public Long getSecuencia() {
        return secuencia;
    }

    public String getHashEvento() {
        return hashEvento;
    }

    public String getHashEventoAnterior() {
        return hashEventoAnterior;
    }
}
