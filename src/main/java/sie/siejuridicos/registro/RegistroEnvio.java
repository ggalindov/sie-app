package sie.siejuridicos.registro;

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
import sie.siejuridicos.common.cifrado.CampoCifradoConverter;

import java.time.LocalDateTime;

// Una fila = un correo o WhatsApp real que el sistema intentó enviar, con su destinatario --
// pedido explícito del usuario: "un registro de correos y whatsapp que salen del sistema y
// para quien salen". Se guarda desde EmailService/WhatsAppService, justo después de cada
// intento de envío real (éxito o fallo), nunca desde los controladores/servicios que lo
// disparan -- así ningún punto de envío queda sin registrar.
//
// A diferencia de RegistroSistema (que a propósito nunca guarda datos de un cliente),
// destinatarioNombre/destinatarioContacto/resumen SÍ son datos de una persona real, así que
// van cifrados igual que el resto de los datos de contacto del sistema (ver Cliente,
// ActividadCrm.descripcion). Sin índice ciego (a diferencia de Cliente.correoHash): esta
// tabla nunca se consulta por destinatario exacto, solo se lista/filtra por canal, tipo y
// fecha, así que no hace falta poder comparar el valor cifrado por igualdad en SQL.
@Entity
@Table(name = "registro_envios")
public class RegistroEnvio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "canal", nullable = false, length = 20)
    private CanalEnvio canal;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 40)
    private TipoEnvio tipo;

    @Convert(converter = CampoCifradoConverter.class)
    @Column(name = "destinatario_nombre")
    private String destinatarioNombre;

    // Correo o número de celular, según el canal.
    @Convert(converter = CampoCifradoConverter.class)
    @Column(name = "destinatario_contacto", nullable = false)
    private String destinatarioContacto;

    // Descripción corta de lo que se envió (ej. "Radicado SIE-2026-104", "Recordatorio de
    // pago - $350.000"), cifrada aunque hoy casi nunca repita un dato de identificación --
    // más simple y más seguro no tener que decidir caso por caso qué texto sí podría filtrar
    // algo del cliente.
    @Convert(converter = CampoCifradoConverter.class)
    @Column(name = "resumen", nullable = false)
    private String resumen;

    @Column(name = "exitoso", nullable = false)
    private boolean exitoso;

    @CreationTimestamp
    @Column(name = "fecha_hora", updatable = false)
    private LocalDateTime fechaHora;

    public Long getId() {
        return id;
    }

    public CanalEnvio getCanal() {
        return canal;
    }

    public void setCanal(CanalEnvio canal) {
        this.canal = canal;
    }

    public TipoEnvio getTipo() {
        return tipo;
    }

    public void setTipo(TipoEnvio tipo) {
        this.tipo = tipo;
    }

    public String getDestinatarioNombre() {
        return destinatarioNombre;
    }

    public void setDestinatarioNombre(String destinatarioNombre) {
        this.destinatarioNombre = destinatarioNombre;
    }

    public String getDestinatarioContacto() {
        return destinatarioContacto;
    }

    public void setDestinatarioContacto(String destinatarioContacto) {
        this.destinatarioContacto = destinatarioContacto;
    }

    public String getResumen() {
        return resumen;
    }

    public void setResumen(String resumen) {
        this.resumen = resumen;
    }

    public boolean isExitoso() {
        return exitoso;
    }

    public void setExitoso(boolean exitoso) {
        this.exitoso = exitoso;
    }

    public LocalDateTime getFechaHora() {
        return fechaHora;
    }
}
