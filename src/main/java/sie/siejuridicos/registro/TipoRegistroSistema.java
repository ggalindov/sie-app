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
    // Pedido explícito del usuario (2026-11-06): "revisa porque el aceptar o rechazar pagos
    // en whatsapp no se esta reflejando" -- auditoría en vivo de los logs de producción
    // (backend y borde Caddy) confirmó que Meta NUNCA ha llamado a nuestro webhook de verdad
    // (solo 2 llamadas manuales de prueba con curl/Python en todo el historial disponible):
    // el problema es la suscripción del webhook en el Meta Business Manager, no nuestro
    // código. Este tipo se registra SOLO desde WhatsAppWebhookController (nunca desde
    // /simular-respuesta ni desde el ajuste manual del admin) para que /admin/cobros pueda
    // mostrar de forma honesta "la última vez que de verdad llegó algo de Meta" y avisar si
    // nunca ha llegado nada, en vez de que esto vuelva a pasar inadvertido durante semanas.
    RESPUESTA_WEBHOOK_WHATSAPP_COBRO("Respuesta real de WhatsApp recibida (cobros)", COMUNICACIONES),
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
    GESTION_TAREAS("Gestión de tareas", GESTION_INTERNA),
    // Edición manual de un caso desde el panel (pedido explícito del usuario: "desligarnos
    // del excel... un boton para editar caso para administrador unicamente", ver
    // CasoService.actualizar()).
    EDICION_CASO("Edición de caso", GESTION_INTERNA),
    // Interruptores de envíos automáticos por categoría/canal desde el panel (pedido
    // explícito del usuario, ver sie.siejuridicos.configuracion).
    CONFIGURACION_ENVIOS("Configuración de envíos", SEGURIDAD),
    // Seguimiento judicial 24/7 contra la Rama Judicial (ver
    // sie.siejuridicos.vigilancia.SeguimientoJudicialService) -- solo lectura, nunca envía
    // nada a ningún cliente.
    SEGUIMIENTO_JUDICIAL("Seguimiento judicial", SINCRONIZACION),
    // Auditoría de SIE-Vigilancia (pedido explícito del usuario: "necesito dejar todo
    // perfecto... seguimiento para usuarios") -- los otros dos pasos del ciclo nocturno de
    // VigilanciaExternaOrquestador, antes sin ningún rastro visible para el admin más allá
    // de los logs del servidor. exitoso=false cuando el cliente externo correspondiente
    // (Publicaciones Procesales/CPNU) "pareceCaido()" al terminar el paso -- eso es además lo
    // que dispara la alerta por correo, ver VigilanciaCaidaAlertaListener.
    VIGILANCIA_BOLETINES("Recopilación de boletines judiciales", SINCRONIZACION),
    VIGILANCIA_DETECCION_PROCESOS("Detección de procesos nuevos (vigilancia)", SINCRONIZACION),
    // El ÚNICO punto del módulo de vigilancia judicial que sí envía algo a un cliente real
    // -- siempre manual, a pedido expreso de un abogado (ver EnvioDocumentoJudicialService),
    // nunca disparado por el seguimiento automático.
    ENVIO_DOCUMENTO_JUDICIAL("Envío de documento judicial", COMUNICACIONES),
    // Pedido explícito del usuario: el botón "Crear y adjuntar automáticamente en SIE" de
    // /admin/procesos-detectados (ver CasoService.crearDesdeProcesoCandidato) -- a diferencia
    // de EDICION_CASO, deja explícito en el registro que el caso no se tecleó a mano, sino que
    // viene de una búsqueda por nombre que un abogado/admin confirmó como real.
    CASO_CREADO_DESDE_DETECCION("Caso creado desde detección automática", GESTION_INTERNA),
    // Pedido explícito del usuario (2026-11-01): "el sistema debe reflejar que fallos hay" --
    // un radicado que, al guardarse (sincronización desde la hoja, creación manual o edición
    // desde el panel), no se pudo normalizar a un radicado CPNU real de 23 dígitos (ver
    // RadicadoUtil/Caso.setRadicadoId()). NUNCA bloquea el guardado -- hay jurisdicciones
    // reales sin radicado CPNU (comisaría de familia, inspección de policía) -- solo deja
    // trazabilidad de qué caso puntual necesita revisión antes de que alguien note que nunca
    // recibió seguimiento automático.
    RADICADO_FORMATO_INVALIDO("Radicado con formato no reconocido", GESTION_INTERNA);

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
