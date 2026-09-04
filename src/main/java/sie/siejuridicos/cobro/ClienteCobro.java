package sie.siejuridicos.cobro;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import sie.siejuridicos.common.cifrado.CampoCifradoConverter;

import java.time.LocalDateTime;

// Un cliente activo con cobro pendiente mensual, sincronizado desde una de las dos pestañas
// del Google Sheets de cobros de la firma (ver HojaCobrosService). A diferencia de
// caso.Cliente (que registra a quién le pertenece un radicado), esta es información
// financiera recurrente -- se cifran también honorarios y cédula/NIT, no solo nombre/
// correo/teléfono, por el mismo criterio de "dato sensible de un cliente real" (ver
// CifradoService).
//
// numeroFila + tipo es la llave de sincronización (única por tipo, igual que numeroCaso +
// fuente en caso.Caso, ver migración V25): "NO. 12" en Empresas y "NO. 12" en Personas
// Naturales son filas distintas.
//
// activo=false (no se borra la fila): si una fila desaparece de la hoja (el cliente ya no
// debe, o se dio de baja), se marca inactiva en vez de eliminarla -- reversible y auditable,
// y dentro de la misma transacción que la sincronización si algo se revierte.
@Entity
@Table(name = "clientes_cobro")
public class ClienteCobro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoClienteCobro tipo;

    @Column(name = "numero_fila", nullable = false, length = 20)
    private String numeroFila;

    @Convert(converter = CampoCifradoConverter.class)
    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Convert(converter = CampoCifradoConverter.class)
    @Column(name = "correo")
    private String correo;

    @Convert(converter = CampoCifradoConverter.class)
    @Column(name = "telefono")
    private String telefono;

    // Índice ciego (HMAC, no reversible, ver CifradoService.indiceCiego) del teléfono YA
    // NORMALIZADO (ver WhatsAppService.normalizarCelular): permite encontrar por igualdad en
    // SQL a qué cliente le llegó la respuesta de WhatsApp del webhook, sin tener que
    // descifrar el teléfono de cada fila activa una por una para compararlo.
    @Column(name = "telefono_hash", length = 64)
    private String telefonoHash;

    @Convert(converter = CampoCifradoConverter.class)
    @Column(name = "cedula_nit")
    private String cedulaNit;

    // Se guarda tal cual viene formateada de la hoja ("$ 1.750.905"), no como número: solo
    // se necesita mostrarla y saber si es "$ 0" (sin costo, ver
    // CobroService.tieneCosto()) -- no se hacen operaciones aritméticas con ella.
    @Convert(converter = CampoCifradoConverter.class)
    @Column(name = "honorarios")
    private String honorarios;

    // Columna "PAGO ESTE MES" de la hoja: true = ya pagó este mes, no se le recuerda. Puede
    // venir null si la celda está vacía en la hoja (ver Empresas, que todavía no la usa).
    @Column(name = "pago_este_mes")
    private Boolean pagoEsteMes;

    // Columna "RESPONDIO MENSAJE": lo que el cliente respondió por WhatsApp al botón de
    // sí/no del recordatorio (ver whatsapp.WhatsAppWebhookController), y lo que este mismo
    // sistema escribe de vuelta en esa columna de la hoja (ver HojaCobrosService.marcarRespuesta).
    // No cifrado a propósito: es un valor corto (Sí/No), no un dato de identificación en sí
    // mismo, y necesita poder filtrarse/mostrarse simple en el panel.
    @Column(name = "respondio_mensaje", length = 20)
    private String respondioMensaje;

    // Cuándo se le envió por última vez el recordatorio mensual (correo + WhatsApp): evita
    // que el mismo cliente reciba dos recordatorios en el mismo mes si el botón manual del
    // panel se usa además del envío automático del día 1 (ver CobroService.enviarRecordatorios()).
    @Column(name = "fecha_ultimo_recordatorio")
    private LocalDateTime fechaUltimoRecordatorio;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    @CreationTimestamp
    @Column(name = "fecha_creacion", updatable = false)
    private LocalDateTime fechaCreacion;

    @UpdateTimestamp
    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    public Long getId() {
        return id;
    }

    public TipoClienteCobro getTipo() {
        return tipo;
    }

    public void setTipo(TipoClienteCobro tipo) {
        this.tipo = tipo;
    }

    public String getNumeroFila() {
        return numeroFila;
    }

    public void setNumeroFila(String numeroFila) {
        this.numeroFila = numeroFila;
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

    public String getTelefonoHash() {
        return telefonoHash;
    }

    public void setTelefonoHash(String telefonoHash) {
        this.telefonoHash = telefonoHash;
    }

    public String getCedulaNit() {
        return cedulaNit;
    }

    public void setCedulaNit(String cedulaNit) {
        this.cedulaNit = cedulaNit;
    }

    public String getHonorarios() {
        return honorarios;
    }

    public void setHonorarios(String honorarios) {
        this.honorarios = honorarios;
    }

    public Boolean getPagoEsteMes() {
        return pagoEsteMes;
    }

    public void setPagoEsteMes(Boolean pagoEsteMes) {
        this.pagoEsteMes = pagoEsteMes;
    }

    public String getRespondioMensaje() {
        return respondioMensaje;
    }

    public void setRespondioMensaje(String respondioMensaje) {
        this.respondioMensaje = respondioMensaje;
    }

    public LocalDateTime getFechaUltimoRecordatorio() {
        return fechaUltimoRecordatorio;
    }

    public void setFechaUltimoRecordatorio(LocalDateTime fechaUltimoRecordatorio) {
        this.fechaUltimoRecordatorio = fechaUltimoRecordatorio;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public LocalDateTime getFechaCreacion() {
        return fechaCreacion;
    }

    public LocalDateTime getFechaActualizacion() {
        return fechaActualizacion;
    }
}
