package sie.siejuridicos.registro;

// Cada tipo de proceso real que el sistema ejecuta por su cuenta (programado) o que el admin
// dispara desde el panel -- ver RegistroSistemaService.registrar(). nombreVisible es lo que
// ve el admin en /admin/registro, no el nombre técnico de la clase que lo dispara.
public enum TipoRegistroSistema {
    SINCRONIZACION_CASOS("Sincronización de casos"),
    ENVIO_NOTIFICACIONES_CASOS("Envío de notificaciones de casos"),
    SINCRONIZACION_COBROS("Sincronización de cobros"),
    ENVIO_RECORDATORIOS_COBROS("Envío de recordatorios de cobro"),
    RECORDATORIO_CITA("Recordatorio de cita"),
    BOLETIN_ENVIADO("Boletín enviado"),
    // Pedido explícito del usuario: además de los procesos automáticos/masivos de arriba, el
    // registro también debe cubrir eventos de seguridad y administración útiles para el admin.
    INICIO_SESION("Inicio de sesión"),
    USUARIO_CREADO("Usuario creado"),
    USUARIO_ACTIVO_CAMBIADO("Cambio de estado de usuario");

    private final String nombreVisible;

    TipoRegistroSistema(String nombreVisible) {
        this.nombreVisible = nombreVisible;
    }

    public String getNombreVisible() {
        return nombreVisible;
    }
}
