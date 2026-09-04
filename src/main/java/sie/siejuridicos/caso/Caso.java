package sie.siejuridicos.caso;

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

// El estado del caso NO vive aquí: esta tabla solo registra qué radicado le pertenece a qué
// cliente, para enviarle el código por correo. El estado real (despacho, información, tipo,
// estado, fecha de actualización) se lee en vivo del Google Sheets de la firma con
// HojaCalculoService, buscando la fila cuyo Radicado ID coincide con radicadoId. Ver
// migración V21 (antes esta tabla sí tenía etapa/categoría propias) y V24 (sincronización
// automática desde la hoja, ver CasoService.sincronizarDesdeHoja()).
@Entity
@Table(name = "casos")
public class Caso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_cliente", nullable = false)
    private Cliente cliente;

    // De qué pestaña del Google Sheets de la firma viene este caso (o MANUAL si se creó a
    // mano desde el panel). Determina qué rango de la hoja consultar en vivo (ver
    // HojaCalculoService) y separa los casos por origen en el panel administrativo.
    @Enumerated(EnumType.STRING)
    @Column(name = "fuente", nullable = false, length = 30)
    private FuenteCaso fuente;

    // El número de caso interno de la firma (columna "NO." en su Google Sheets -- o, en la
    // hoja de Procesos Comisaría, que no tiene esa columna, un número de fila sintético, ver
    // HojaCalculoService), no el radicado judicial. Es la llave que usa la sincronización
    // automática para saber si una fila de la hoja ya existe en nuestro sistema o es nueva
    // -- a diferencia del radicado, este número existe desde el primer día, antes de que el
    // despacho judicial le asigne un radicado real. Único POR FUENTE, no global (ver
    // migración V25): dos hojas distintas pueden coincidir en número sin ser el mismo caso.
    // Null en los casos creados a mano desde el panel (ver CrearCasoRequest).
    @Column(name = "numero_caso")
    private String numeroCaso;

    // El radicado judicial REAL de la firma (columna I de su Google Sheets), no un código
    // generado por nosotros. Puede quedar en null temporalmente: un caso sincronizado desde
    // la hoja puede no tener radicado todavía (el despacho aún no lo asigna), y se completa
    // en una sincronización posterior cuando sí aparezca en la hoja.
    @Column(name = "radicado_id", unique = true)
    private String radicadoId;

    // Si ya se le envió al cliente el correo con su número de radicado. Empieza en false al
    // sincronizar o crear un caso (incluso si ya tiene radicado): el envío real lo dispara el
    // botón "Enviar correos pendientes" del panel (ver CasoService.enviarCorreosPendientes()),
    // no ocurre automáticamente, para que el admin decida cuándo notificar en bloque.
    @Column(name = "correo_enviado", nullable = false)
    private boolean correoEnviado;

    // Igual que correoEnviado, pero para la notificación por WhatsApp (ver WhatsAppService)
    // -- independiente: un caso puede tener el correo enviado y el WhatsApp pendiente (o
    // viceversa, si el cliente solo tiene uno de los dos datos de contacto en la hoja).
    @Column(name = "whatsapp_enviado", nullable = false)
    private boolean whatsappEnviado;

    // Texto libre escrito por el admin/abogado, potencialmente sensible (puede describir la
    // situación del cliente), nunca visible para el cliente -- cifrado igual que los datos
    // del Cliente.
    @Convert(converter = CampoCifradoConverter.class)
    @Column(name = "notas_internas")
    private String notasInternas;

    @CreationTimestamp
    @Column(name = "fecha_creacion", updatable = false)
    private LocalDateTime fechaCreacion;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Cliente getCliente() {
        return cliente;
    }

    public void setCliente(Cliente cliente) {
        this.cliente = cliente;
    }

    public FuenteCaso getFuente() {
        return fuente;
    }

    public void setFuente(FuenteCaso fuente) {
        this.fuente = fuente;
    }

    public String getNumeroCaso() {
        return numeroCaso;
    }

    public void setNumeroCaso(String numeroCaso) {
        this.numeroCaso = numeroCaso;
    }

    public String getRadicadoId() {
        return radicadoId;
    }

    public void setRadicadoId(String radicadoId) {
        this.radicadoId = radicadoId;
    }

    public boolean isCorreoEnviado() {
        return correoEnviado;
    }

    public void setCorreoEnviado(boolean correoEnviado) {
        this.correoEnviado = correoEnviado;
    }

    public boolean isWhatsappEnviado() {
        return whatsappEnviado;
    }

    public void setWhatsappEnviado(boolean whatsappEnviado) {
        this.whatsappEnviado = whatsappEnviado;
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
}
