package sie.siejuridicos.registro;

import static sie.siejuridicos.registro.CategoriaRegistroSistema.COMUNICACIONES;
import static sie.siejuridicos.registro.CategoriaRegistroSistema.GESTION_INTERNA;
import static sie.siejuridicos.registro.CategoriaRegistroSistema.SEGURIDAD;
import static sie.siejuridicos.registro.CategoriaRegistroSistema.SINCRONIZACION;

// Cada tipo de proceso real que el sistema ejecuta por su cuenta (programado) o que el admin
// dispara desde el panel -- ver RegistroSistemaService.registrar(). nombreVisible es lo que
// ve el admin en /admin/registro, no el nombre técnico de la clase que lo dispara. categoria
// agrupa estos 14 tipos en 4 familias (ver CategoriaRegistroSistema) -- pedido explícito del
// usuario: "pon categorias para que sea mas intuitivo de entender".
public enum TipoRegistroSistema {
    SINCRONIZACION_CASOS("Sincronización de casos", SINCRONIZACION),
    ENVIO_NOTIFICACIONES_CASOS("Envío de notificaciones de casos", COMUNICACIONES),
    REPORTE_SEMANAL_CASOS("Reporte semanal de casos", COMUNICACIONES),
    SINCRONIZACION_COBROS("Sincronización de cobros", SINCRONIZACION),
    ENVIO_RECORDATORIOS_COBROS("Envío de recordatorios de cobro", COMUNICACIONES),
    REINICIO_MENSUAL_COBROS("Reinicio mensual de cobros", SINCRONIZACION),
    RECORDATORIO_CITA("Recordatorio de cita", COMUNICACIONES),
    BOLETIN_ENVIADO("Boletín enviado", COMUNICACIONES),
    // Pedido explícito del usuario: además de los procesos automáticos/masivos de arriba, el
    // registro también debe cubrir eventos de seguridad y administración útiles para el admin.
    INICIO_SESION("Inicio de sesión", SEGURIDAD),
    USUARIO_CREADO("Usuario creado", SEGURIDAD),
    USUARIO_ACTIVO_CAMBIADO("Cambio de estado de usuario", SEGURIDAD),
    // Pedido explícito del usuario: llevar registro de qué radicado consulta su estado y
    // cuándo, tanto si el radicado existe como si no (ver CasoService.consultar()). El
    // radicado en sí no cuenta como dato sensible del cliente -- es el código que nosotros
    // mismos le enviamos por correo para que lo use en esta consulta pública (mismo criterio
    // ya usado en HojaCalculoService al decidir qué es seguro loguear).
    CONSULTA_ESTADO_CASO("Consulta de estado de caso", GESTION_INTERNA),
    GESTION_CRM("Gestión CRM", GESTION_INTERNA),
    GESTION_TAREAS("Gestión de tareas", GESTION_INTERNA);

    private final String nombreVisible;
    private final CategoriaRegistroSistema categoria;

    TipoRegistroSistema(String nombreVisible, CategoriaRegistroSistema categoria) {
        this.nombreVisible = nombreVisible;
        this.categoria = categoria;
    }

    public String getNombreVisible() {
        return nombreVisible;
    }

    public CategoriaRegistroSistema getCategoria() {
        return categoria;
    }
}
