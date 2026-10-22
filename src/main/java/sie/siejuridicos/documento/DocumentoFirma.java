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
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import sie.siejuridicos.caso.Caso;
import sie.siejuridicos.usuario.UsuarioInterno;

import java.time.LocalDateTime;
import java.util.UUID;

// Un documento que gerencia sube al motor de firma: se le inserta la firma del gerente, la
// de cada abogado que gerencia asignó a este documento puntual, y luego -- si gerencia
// asignó alguna parte -- la de cada parte elegida de la biblioteca de contactos (ver
// DocumentoFirmante). Ciclo de vida monótono (cada documento avanza en este orden, sin
// retroceder): DILIGENCIADO -> EMITIDO -> FIRMADO_STAFF -> [EN_FIRMA_PARTES, solo si hay
// partes asignadas] -> SELLADO -> REMITIDO -> ENCRIPTADO, con ANULADO como terminal
// alcanzable desde EN_FIRMA_PARTES (expiración del proceso o anulación manual). A
// diferencia del motor original (UCN), aquí no hay un "tipo" de documento que determine la
// ruta -- la rama a EN_FIRMA_PARTES depende solo de si se asignaron partes o no, ver
// DocumentoFirmaService.
//
// casoId es opcional: un documento puede o no estar ligado a un caso real de la firma
// (mismo patrón ya usado en Tarea.casoId/clienteCrmId, ambos nullable).
@Entity
@Table(name = "documentos_firma")
public class DocumentoFirma {

    // Eje de integridad, independiente del ciclo de vida anterior: cualquier verificación
    // (pública o interna) que detecte que el contenido no coincide con su hash/código
    // original lo voltea a ALTERADO de forma persistente, sin importar en qué estado del
    // ciclo de vida se encuentre.
    public static final String ESTADO_DOC_DILIGENCIADO = "DILIGENCIADO";
    public static final String ESTADO_DOC_EMITIDO = "EMITIDO";
    public static final String ESTADO_DOC_FIRMADO_STAFF = "FIRMADO_STAFF";
    public static final String ESTADO_DOC_NOTIFICADO = "NOTIFICADO";
    public static final String ESTADO_DOC_EN_FIRMA_PARTES = "EN_FIRMA_PARTES";
    public static final String ESTADO_DOC_SELLADO = "SELLADO";
    public static final String ESTADO_DOC_REMITIDO = "REMITIDO";
    public static final String ESTADO_DOC_ENCRIPTADO = "ENCRIPTADO";
    public static final String ESTADO_DOC_ANULADO = "ANULADO";

    public static final String INTEGRIDAD_VIGENTE = "VIGENTE";
    public static final String INTEGRIDAD_ALTERADO = "ALTERADO";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Bloqueo optimista: DocumentoSellaInfoService.intentarSellarYRemitir() lee, sella,
    // remite y cifra este documento en una sola operación que puede dispararse dos veces
    // casi al mismo tiempo (dos firmantes distintos completando su firma casi
    // simultáneamente). Sin esto, la segunda transacción sobrescribiría en silencio el
    // sellado/cifrado de la primera.
    @Version
    @Column(nullable = false)
    private long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caso_id")
    private Caso caso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por_staff_id", nullable = false)
    private UsuarioInterno creadoPorStaff;

    @Column(nullable = false)
    private String nombre;

    // Content-Type real del archivo subido (siempre "application/pdf" hoy -- el panel solo
    // acepta PDF, ver DocumentoAdminController -- pero se guarda explícito en vez de
    // asumirlo, por si en el futuro se acepta otro formato de soporte).
    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "contenido_pdf", nullable = false)
    private byte[] contenidoPdf;

    @Column(name = "numero_secuencia")
    private Long numeroSecuencia;

    // HMAC-SHA256 calculado con una clave secreta del servidor a partir del id y el número
    // de secuencia del documento. Se guarda para poder confirmarlo después sin exponer el
    // documento completo: nadie puede fabricar un código válido sin la clave.
    @Column(name = "codigo_verificacion")
    private String codigoVerificacion;

    // SHA-256 del PDF completo (los bytes exactos guardados en 'contenidoPdf'), recalculado
    // en cada paso que modifica el contenido (estampado de firmas, PAdES). A diferencia de
    // codigoVerificacion (que solo cubre id+secuencia), este hash cambia si se altera
    // CUALQUIER byte del archivo, por lo que permite detectar si un PDF fue modificado
    // después de su emisión aunque el código impreso siga siendo el correcto.
    @Column(name = "hash_contenido")
    private String hashContenido;

    // Orden de inserción real en la base (BIGSERIAL). 'fechaCreacion' no sirve para ordenar
    // la lista de documentos: varios documentos pueden compartir el mismo timestamp exacto.
    @Column(name = "orden_creacion", insertable = false, updatable = false)
    private Long ordenCreacion;

    @Column(name = "estado_documento", nullable = false)
    private String estadoDocumento = ESTADO_DOC_DILIGENCIADO;

    @Column(name = "estado_integridad", nullable = false)
    private String estadoIntegridad = INTEGRIDAD_VIGENTE;

    @Column(name = "contenido_cifrado", nullable = false)
    private boolean contenidoCifrado;

    @Column(name = "contenido_iv")
    private byte[] contenidoIv;

    // Envelope encryption: 'dekCifrado' es la clave de datos (DEK) aleatoria de este
    // documento, ya envuelta (cifrada) con la clave maestra activa ('kekId'); 'dekIv' es el
    // IV usado para esa envoltura (distinto del IV de 'contenidoIv', que cifra el PDF con la
    // DEK ya desenvuelta).
    @Column(name = "dek_cifrado")
    private byte[] dekCifrado;

    @Column(name = "dek_iv")
    private byte[] dekIv;

    @Column(name = "kek_id")
    private String kekId;

    // Firma criptográfica de aplicación (Ed25519, ver ApplicationSigningKeyService) del hash.
    @Column(name = "firma_app_valor")
    private byte[] firmaAppValor;

    @Column(name = "firma_app_kid")
    private String firmaAppKid;

    @Column(name = "firma_app_generada_en")
    private LocalDateTime firmaAppGeneradaEn;

    // Sellado de tiempo RFC 3161 (ver Rfc3161TimestampClient) del hash del PDF sellado.
    @Column(name = "rfc3161_token")
    private byte[] rfc3161Token;

    @Column(name = "rfc3161_url_tsa")
    private String rfc3161UrlTsa;

    @Column(name = "rfc3161_sellado_en")
    private LocalDateTime rfc3161SelladoEn;

    @CreationTimestamp
    @Column(name = "fecha_creacion", updatable = false)
    private LocalDateTime fechaCreacion;

    protected DocumentoFirma() {
    }

    public DocumentoFirma(Caso caso, UsuarioInterno creadoPorStaff, String nombre, String contentType, byte[] contenidoPdf) {
        this.caso = caso;
        this.creadoPorStaff = creadoPorStaff;
        this.nombre = nombre;
        this.contentType = contentType;
        this.contenidoPdf = contenidoPdf;
    }

    public UUID getId() {
        return id;
    }

    public long getVersion() {
        return version;
    }

    public Caso getCaso() {
        return caso;
    }

    public UsuarioInterno getCreadoPorStaff() {
        return creadoPorStaff;
    }

    public String getNombre() {
        return nombre;
    }

    // Solo para correcciones de metadato vía el motor de firma, y únicamente antes de que el
    // documento llegue a SELLADO (ver DocumentChangeRequestService, que la usa dentro de una
    // DocumentoCambioSolicitud con doble confirmación -- nunca se llama directo).
    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getContentType() {
        return contentType;
    }

    public byte[] getContenidoPdf() {
        return contenidoPdf;
    }

    public void setContenidoPdf(byte[] contenidoPdf) {
        this.contenidoPdf = contenidoPdf;
    }

    public Long getNumeroSecuencia() {
        return numeroSecuencia;
    }

    // Se asigna explícitamente antes de persistir, a partir de
    // DocumentoFirmaRepository#siguienteNumeroSecuencia(), en vez de con @GeneratedValue.
    public void setNumeroSecuencia(Long numeroSecuencia) {
        this.numeroSecuencia = numeroSecuencia;
    }

    public String getCodigoVerificacion() {
        return codigoVerificacion;
    }

    public void setCodigoVerificacion(String codigoVerificacion) {
        this.codigoVerificacion = codigoVerificacion;
    }

    public String getHashContenido() {
        return hashContenido;
    }

    public void setHashContenido(String hashContenido) {
        this.hashContenido = hashContenido;
    }

    public Long getOrdenCreacion() {
        return ordenCreacion;
    }

    public String getEstadoDocumento() {
        return estadoDocumento;
    }

    public void setEstadoDocumento(String estadoDocumento) {
        this.estadoDocumento = estadoDocumento;
    }

    public String getEstadoIntegridad() {
        return estadoIntegridad;
    }

    public void setEstadoIntegridad(String estadoIntegridad) {
        this.estadoIntegridad = estadoIntegridad;
    }

    public boolean isContenidoCifrado() {
        return contenidoCifrado;
    }

    public void setContenidoCifrado(boolean contenidoCifrado) {
        this.contenidoCifrado = contenidoCifrado;
    }

    public byte[] getContenidoIv() {
        return contenidoIv;
    }

    public void setContenidoIv(byte[] contenidoIv) {
        this.contenidoIv = contenidoIv;
    }

    public byte[] getDekCifrado() {
        return dekCifrado;
    }

    public void setDekCifrado(byte[] dekCifrado) {
        this.dekCifrado = dekCifrado;
    }

    public byte[] getDekIv() {
        return dekIv;
    }

    public void setDekIv(byte[] dekIv) {
        this.dekIv = dekIv;
    }

    public String getKekId() {
        return kekId;
    }

    public void setKekId(String kekId) {
        this.kekId = kekId;
    }

    public byte[] getFirmaAppValor() {
        return firmaAppValor;
    }

    public void setFirmaAppValor(byte[] firmaAppValor) {
        this.firmaAppValor = firmaAppValor;
    }

    public String getFirmaAppKid() {
        return firmaAppKid;
    }

    public void setFirmaAppKid(String firmaAppKid) {
        this.firmaAppKid = firmaAppKid;
    }

    public LocalDateTime getFirmaAppGeneradaEn() {
        return firmaAppGeneradaEn;
    }

    public void setFirmaAppGeneradaEn(LocalDateTime firmaAppGeneradaEn) {
        this.firmaAppGeneradaEn = firmaAppGeneradaEn;
    }

    public byte[] getRfc3161Token() {
        return rfc3161Token;
    }

    public void setRfc3161Token(byte[] rfc3161Token) {
        this.rfc3161Token = rfc3161Token;
    }

    public String getRfc3161UrlTsa() {
        return rfc3161UrlTsa;
    }

    public void setRfc3161UrlTsa(String rfc3161UrlTsa) {
        this.rfc3161UrlTsa = rfc3161UrlTsa;
    }

    public LocalDateTime getRfc3161SelladoEn() {
        return rfc3161SelladoEn;
    }

    public void setRfc3161SelladoEn(LocalDateTime rfc3161SelladoEn) {
        this.rfc3161SelladoEn = rfc3161SelladoEn;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }
}
