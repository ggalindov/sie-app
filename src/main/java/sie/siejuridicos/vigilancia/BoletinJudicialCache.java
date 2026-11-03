package sie.siejuridicos.vigilancia;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import sie.siejuridicos.caso.Caso;

import java.time.LocalDateTime;

// Cache TEMPORAL (30 días, ver BoletinJudicialCacheService.DIAS_VENCIMIENTO_CACHE -- subido
// desde los 7 días originales de la migración V63 a pedido explícito del usuario: "guardarlos
// en el sistema por un total de 30 dias"). Sigue en pie la decisión de NO custodiar PDFs para
// siempre (nunca se vuelve un DMS) -- BoletinJudicialScheduler.purgar() borra todo lo que ya
// pasó fechaExpiracion, sin excepción.
//
// Esta tabla es también la fuente real de VigilanciaAdminController.boletinesCache(): el
// modal de Seguimiento ya NO consulta Publicaciones Procesales en vivo por cada actuación al
// abrirse (pedido explícito del usuario, 2026-10-31: "las cargas se estan demorando
// demasiado... implementa algo mas rapido, que no se demore tanto sin que nos lleguen a
// bloquear") -- lee de aquí, que el ciclo nocturno de VigilanciaExternaOrquestador ya dejó
// actualizado.
//
// contenidoBytes NUNCA usa @Lob (mismo criterio ya probado en DocumentoFirma.contenidoPdf
// de este mismo proyecto): @Lob en Postgres mapea a un Large Object por OID, no a BYTEA --
// @Column simple sobre byte[] sí mapea a BYTEA, que es el tipo real de la columna (ver
// migración V63).
@Entity
@Table(name = "boletines_judiciales_cache")
public class BoletinJudicialCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_caso", nullable = false)
    private Caso caso;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_actuacion")
    private ActuacionJudicialRegistrada actuacion;

    @Column(name = "nombre_archivo", nullable = false)
    private String nombreArchivo;

    @Column(name = "url_origen", nullable = false)
    private String urlOrigen;

    @Column(name = "contenido_bytes", nullable = false)
    private byte[] contenidoBytes;

    @Column(name = "hash_sha256", nullable = false)
    private String hashSha256;

    // Ver migración V64: solo true cuando el nombre del boletín coincide por palabras clave
    // con la actuación esperada -- el único caso en el que se le puede enviar
    // AUTOMÁTICAMENTE al cliente sin que un humano lo confirme primero.
    @Column(name = "coincide_nombre_actuacion", nullable = false)
    private boolean coincideNombreActuacion;

    @Column(name = "enviado_gerente", nullable = false)
    private boolean enviadoGerente;

    @Column(name = "enviado_abogado", nullable = false)
    private boolean enviadoAbogado;

    @Column(name = "enviado_cliente", nullable = false)
    private boolean enviadoCliente;

    @Column(name = "fecha_descarga", nullable = false)
    private LocalDateTime fechaDescarga;

    @Column(name = "fecha_expiracion", nullable = false)
    private LocalDateTime fechaExpiracion;

    public Long getId() {
        return id;
    }

    public Caso getCaso() {
        return caso;
    }

    public void setCaso(Caso caso) {
        this.caso = caso;
    }

    public ActuacionJudicialRegistrada getActuacion() {
        return actuacion;
    }

    public void setActuacion(ActuacionJudicialRegistrada actuacion) {
        this.actuacion = actuacion;
    }

    public String getNombreArchivo() {
        return nombreArchivo;
    }

    public void setNombreArchivo(String nombreArchivo) {
        this.nombreArchivo = nombreArchivo;
    }

    public String getUrlOrigen() {
        return urlOrigen;
    }

    public void setUrlOrigen(String urlOrigen) {
        this.urlOrigen = urlOrigen;
    }

    public byte[] getContenidoBytes() {
        return contenidoBytes;
    }

    public void setContenidoBytes(byte[] contenidoBytes) {
        this.contenidoBytes = contenidoBytes;
    }

    public String getHashSha256() {
        return hashSha256;
    }

    public void setHashSha256(String hashSha256) {
        this.hashSha256 = hashSha256;
    }

    public boolean isEnviadoGerente() {
        return enviadoGerente;
    }

    public void setEnviadoGerente(boolean enviadoGerente) {
        this.enviadoGerente = enviadoGerente;
    }

    public boolean isCoincideNombreActuacion() {
        return coincideNombreActuacion;
    }

    public void setCoincideNombreActuacion(boolean coincideNombreActuacion) {
        this.coincideNombreActuacion = coincideNombreActuacion;
    }

    public boolean isEnviadoAbogado() {
        return enviadoAbogado;
    }

    public void setEnviadoAbogado(boolean enviadoAbogado) {
        this.enviadoAbogado = enviadoAbogado;
    }

    public boolean isEnviadoCliente() {
        return enviadoCliente;
    }

    public void setEnviadoCliente(boolean enviadoCliente) {
        this.enviadoCliente = enviadoCliente;
    }

    public LocalDateTime getFechaDescarga() {
        return fechaDescarga;
    }

    public void setFechaDescarga(LocalDateTime fechaDescarga) {
        this.fechaDescarga = fechaDescarga;
    }

    public LocalDateTime getFechaExpiracion() {
        return fechaExpiracion;
    }

    public void setFechaExpiracion(LocalDateTime fechaExpiracion) {
        this.fechaExpiracion = fechaExpiracion;
    }
}
