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
import sie.siejuridicos.usuario.UsuarioInterno;

import java.time.LocalDate;
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

    // Pedido explícito del usuario (2026-11-01): "el sistema debe reflejar que fallos hay" --
    // true cuando radicadoId, aunque tenga un valor, no se pudo normalizar a un radicado CPNU
    // real de 23 dígitos (ver RadicadoUtil). NUNCA bloquea el guardado del caso (hay
    // jurisdicciones reales sin radicado CPNU, p. ej. comisaría de familia o inspección de
    // policía -- ver RadicadoUtil.esFormatoCpnuValido), solo deja visible en el panel que ESTE
    // caso en particular no tendrá seguimiento automático 24/7 hasta que se corrija o se
    // confirme que es de otro tipo.
    @Column(name = "radicado_formato_invalido", nullable = false)
    private boolean radicadoFormatoInvalido;

    // Nombre TAL COMO aparece en la hoja para ESTA fila puntual (columna de "sujeto procesal
    // representado"/"demandante", ver HojaCalculoService) -- bug real reportado por el
    // usuario: el panel mostraba nombres viejos/equivocados porque antes se mostraba
    // cliente.getNombre(), compartido entre TODOS los casos del mismo Cliente (mismo
    // correo), y CasoService.actualizarDatosCliente() deliberadamente NUNCA actualiza ese
    // nombre compartido cuando el cliente tiene más de un caso (para no pisarlo con una
    // etiqueta corta específica de otro caso, ver el comentario de ese método). Este campo
    // es AL REVÉS: vive en el Caso, no en el Cliente, así que cada caso siempre refleja el
    // nombre real y actual de SU PROPIA fila, sin importar cuántos otros casos comparta ese
    // cliente. Null en los casos MANUAL (no vienen de ninguna hoja) -- CasoAdminResponse cae
    // de vuelta a cliente.getNombre() en ese caso.
    @Convert(converter = CampoCifradoConverter.class)
    @Column(name = "nombre_en_hoja")
    private String nombreEnHoja;

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

    // Última vez que este caso recibió el reporte semanal (ver CasoService.enviarReporteSemanal
    // y la migración V37) -- a diferencia de correoEnviado/whatsappEnviado (el aviso ÚNICO de
    // "aquí está tu código"), este reporte se repite cada semana, así que necesita su propia
    // marca de tiempo en vez de un booleano: sirve tanto para no reenviarle el reporte de esta
    // misma semana dos veces, como para saber quién quedó pendiente por el cupo diario
    // compartido de envíos masivos y recogerlo automáticamente al día siguiente.
    @Column(name = "fecha_ultimo_reporte_semanal")
    private LocalDateTime fechaUltimoReporteSemanal;

    // Última vez que este caso recibió el reporte por WhatsApp (ver CasoService.enviarReporteSemanal):
    // mientras el correo se envía semanalmente a costo $0 por SMTP, WhatsApp se envía quincenalmente
    // (dos veces al mes: días 1 y 15) para reducir costos de la API de Meta.
    @Column(name = "fecha_ultimo_reporte_whatsapp")
    private LocalDateTime fechaUltimoReporteWhatsapp;

    // Cacheado la primera vez que sie.siejuridicos.vigilancia.RamaJudicialCpnuClient lo
    // resuelve a partir del radicadoId, para no tener que volver a resolverlo en cada ciclo
    // del seguimiento judicial (ver SeguimientoJudicialService) -- una llamada menos por
    // caso en cada corrida. Null: todavía no se ha podido resolver (radicadoId vacío, no es
    // un radicado CPNU válido de 23 dígitos, o el seguimiento nunca se ha corrido para este
    // caso).
    @Column(name = "id_proceso_cpnu")
    private Long idProcesoCpnu;

    // Mismo rol que idProcesoCpnu, arriba, pero para la jurisdicción contencioso-
    // administrativa (Consejo de Estado y Tribunales Administrativos, ver
    // sie.siejuridicos.vigilancia.SamaiClient) -- pedido explícito del usuario: "implementa
    // esto... SAMAI". Es un GUID en texto (no un entero como el del CPNU), así que su columna
    // es VARCHAR, no BIGINT. Mutuamente excluyente con idProcesoCpnu en la práctica: un
    // radicado real pertenece a una sola jurisdicción, nunca a las dos a la vez.
    // SeguimientoJudicialService decide qué cliente usar con un simple null-check sobre
    // estos dos campos, sin necesitar un enum aparte de "a qué sistema pertenece este caso".
    @Column(name = "id_proceso_samai")
    private String idProcesoSamai;

    // Gap real detectado comparando contra el correo diario de Lupa Jurídica (pedido
    // explícito del usuario: "lupa nos da un seguimiento mas robusto") -- el propio CPNU ya
    // da esto al resolver idProcesoCpnu (ver RamaJudicialCpnuClient.buscarProceso()), se
    // guarda la primera vez que se resuelve el proceso para no tener que pedirlo de nuevo en
    // cada ciclo. sujetosProcesales llega tal cual el CPNU lo formatea ("Demandante: NOMBRE |
    // Demandado: NOMBRE "), listo para mostrar sin reformatear.
    @Column(name = "sujetos_procesales", columnDefinition = "TEXT")
    private String sujetosProcesales;

    @Column(name = "despacho_judicial")
    private String despachoJudicial;

    // Pedido explícito del usuario: "tienes que traer absolutamente toda la informacion...
    // entre mas datos mucho mejor" -- el resto de lo que da el CPNU al resolver el proceso
    // (ver RamaJudicialCpnuClient.buscarProceso()/ProcesoResuelto), capturado en el mismo
    // momento que sujetosProcesales/despachoJudicial, arriba.
    @Column(name = "departamento_judicial")
    private String departamentoJudicial;

    // Cache del correo oficial del despacho judicial de este caso, resuelto vía Croma
    // Legal-Pro (API de pago por crédito, ver CromaClient.consultarCorreosDespachoPorRadicado)
    // -- se reutiliza para TODAS las actuaciones futuras de este mismo caso sin volver a
    // gastar un crédito nuevo cada vez (ver CorreoDespachoJudicialService).
    @Column(name = "correo_oficial_despacho")
    private String correoOficialDespacho;

    @Column(name = "correo_oficial_despacho_fecha")
    private LocalDateTime correoOficialDespachoFecha;

    @Column(name = "fecha_radicacion_proceso")
    private LocalDate fechaRadicacionProceso;

    @Column(name = "proceso_privado")
    private Boolean procesoPrivado;

    // Ponente = el juez/magistrado asignado al proceso, tal cual lo informa el CPNU
    // (Proceso/Detalle/{idProceso}, un endpoint aparte de NumeroRadicacion).
    @Column(name = "ponente")
    private String ponente;

    @Column(name = "tipo_proceso")
    private String tipoProceso;

    @Column(name = "clase_proceso")
    private String claseProceso;

    @Column(name = "subclase_proceso")
    private String subclaseProceso;

    @Column(name = "recurso_proceso")
    private String recursoProceso;

    // Dónde estaba físicamente el expediente dentro del despacho la última vez que se
    // resolvió el proceso (ej. "Secretaria - Oficios") -- a diferencia de los demás campos de
    // este bloque (fijos por naturaleza), este sí puede cambiar con el tiempo, pero
    // SeguimientoJudicialService solo vuelve a resolver el proceso una vez por caso (igual
    // que ya hacía con idProcesoCpnu), no en cada ciclo -- así que puede quedar desactualizado
    // entre una resolución y la siguiente actuación nueva real.
    @Column(name = "ubicacion_proceso")
    private String ubicacionProceso;

    // Pedido explícito del usuario: "la vigilancia tiene que mostrar toda, absolutamente
    // toda la informacion... todo lo que se detecte" -- Proceso/Detalle del CPNU trae esto
    // (cuándo la Rama Judicial misma actualizó el proceso en SU sistema) y se descartaba.
    // Distinto de fechaUltimoSeguimiento (abajo, cuándo NOSOTROS revisamos) -- este es un
    // dato propio de la Rama Judicial. Mismo límite que ubicacionProceso arriba: solo se
    // refresca cuando se vuelve a resolver el proceso completo, no en cada ciclo.
    @Column(name = "ultima_actualizacion_rama")
    private java.time.LocalDateTime ultimaActualizacionRama;

    // Trazabilidad INDEPENDIENTE por caso del seguimiento judicial (ver
    // SeguimientoJudicialService) -- distinto del Registro del Sistema, que solo guarda un
    // resumen agregado de toda la corrida. Se actualizan en CADA intento, exitoso o no, para
    // que un caso puntual siempre se pueda responder "¿cuándo se revisó por última vez?" sin
    // depender de que la corrida completa haya ido bien.
    @Column(name = "fecha_ultimo_seguimiento")
    private LocalDateTime fechaUltimoSeguimiento;

    @Column(name = "ultimo_seguimiento_exitoso")
    private Boolean ultimoSeguimientoExitoso;

    // Pedido explícito del usuario: "la idea es desligarnos del excel... asignacion de caso
    // por abogado" (ver migración V61) -- quién de la firma es responsable de este caso.
    // Nunca lo pone el Google Sheets (no tiene esa columna); lo asigna el ADMIN_GENERAL
    // desde el panel (ver CasoService.actualizar()). Determina, entre otras cosas, a quién
    // se le crea la tarea automática cuando se detecta un término procesal (ver
    // TerminoProcesalService).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_abogado_asignado")
    private UsuarioInterno abogadoAsignado;

    // Casos Privados y Cifrados: casos ultra sensibles con acceso exclusivo para Gerencia (ADMIN_GENERAL).
    // Bloqueados al 100% para abogados y otros roles del sistema.
    @Column(name = "es_privado_cifrado", nullable = false)
    private boolean esPrivadoCifrado = false;

    // Motivo o notas de alta confidencialidad (cifrado con AES-256-GCM via CampoCifradoConverter).
    @Convert(converter = CampoCifradoConverter.class)
    @Column(name = "motivo_confidencialidad")
    private String motivoConfidencialidad;

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

    // Normaliza SIEMPRE antes de guardar (ver RadicadoUtil) -- único punto de entrada real,
    // así cada llamador (sincronización desde la hoja, creación manual, edición desde el
    // panel) queda cubierto automáticamente sin tener que repetir la lógica en cada uno.
    public void setRadicadoId(String radicadoId) {
        this.radicadoId = RadicadoUtil.normalizarParaAlmacenar(radicadoId);
        this.radicadoFormatoInvalido = this.radicadoId != null && !RadicadoUtil.esFormatoCpnuValido(this.radicadoId);
    }

    public boolean isRadicadoFormatoInvalido() {
        return radicadoFormatoInvalido;
    }

    public String getNombreEnHoja() {
        return nombreEnHoja;
    }

    public void setNombreEnHoja(String nombreEnHoja) {
        this.nombreEnHoja = nombreEnHoja;
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

    public LocalDateTime getFechaUltimoReporteSemanal() {
        return fechaUltimoReporteSemanal;
    }

    public void setFechaUltimoReporteSemanal(LocalDateTime fechaUltimoReporteSemanal) {
        this.fechaUltimoReporteSemanal = fechaUltimoReporteSemanal;
    }

    public LocalDateTime getFechaUltimoReporteWhatsapp() {
        return fechaUltimoReporteWhatsapp;
    }

    public void setFechaUltimoReporteWhatsapp(LocalDateTime fechaUltimoReporteWhatsapp) {
        this.fechaUltimoReporteWhatsapp = fechaUltimoReporteWhatsapp;
    }

    public Long getIdProcesoCpnu() {
        return idProcesoCpnu;
    }

    public void setIdProcesoCpnu(Long idProcesoCpnu) {
        this.idProcesoCpnu = idProcesoCpnu;
    }

    public String getIdProcesoSamai() {
        return idProcesoSamai;
    }

    public void setIdProcesoSamai(String idProcesoSamai) {
        this.idProcesoSamai = idProcesoSamai;
    }

    public String getSujetosProcesales() {
        return sujetosProcesales;
    }

    public void setSujetosProcesales(String sujetosProcesales) {
        this.sujetosProcesales = sujetosProcesales;
    }

    public String getDespachoJudicial() {
        return despachoJudicial;
    }

    public void setDespachoJudicial(String despachoJudicial) {
        this.despachoJudicial = despachoJudicial;
    }

    public String getDepartamentoJudicial() {
        return departamentoJudicial;
    }

    public void setDepartamentoJudicial(String departamentoJudicial) {
        this.departamentoJudicial = departamentoJudicial;
    }

    public String getCorreoOficialDespacho() {
        return correoOficialDespacho;
    }

    public void setCorreoOficialDespacho(String correoOficialDespacho) {
        this.correoOficialDespacho = correoOficialDespacho;
    }

    public LocalDateTime getCorreoOficialDespachoFecha() {
        return correoOficialDespachoFecha;
    }

    public void setCorreoOficialDespachoFecha(LocalDateTime correoOficialDespachoFecha) {
        this.correoOficialDespachoFecha = correoOficialDespachoFecha;
    }

    public LocalDate getFechaRadicacionProceso() {
        return fechaRadicacionProceso;
    }

    public void setFechaRadicacionProceso(LocalDate fechaRadicacionProceso) {
        this.fechaRadicacionProceso = fechaRadicacionProceso;
    }

    public Boolean getProcesoPrivado() {
        return procesoPrivado;
    }

    public void setProcesoPrivado(Boolean procesoPrivado) {
        this.procesoPrivado = procesoPrivado;
    }

    public String getPonente() {
        return ponente;
    }

    public void setPonente(String ponente) {
        this.ponente = ponente;
    }

    public String getTipoProceso() {
        return tipoProceso;
    }

    public void setTipoProceso(String tipoProceso) {
        this.tipoProceso = tipoProceso;
    }

    public String getClaseProceso() {
        return claseProceso;
    }

    public void setClaseProceso(String claseProceso) {
        this.claseProceso = claseProceso;
    }

    public String getSubclaseProceso() {
        return subclaseProceso;
    }

    public void setSubclaseProceso(String subclaseProceso) {
        this.subclaseProceso = subclaseProceso;
    }

    public String getRecursoProceso() {
        return recursoProceso;
    }

    public void setRecursoProceso(String recursoProceso) {
        this.recursoProceso = recursoProceso;
    }

    public String getUbicacionProceso() {
        return ubicacionProceso;
    }

    public void setUbicacionProceso(String ubicacionProceso) {
        this.ubicacionProceso = ubicacionProceso;
    }

    public java.time.LocalDateTime getUltimaActualizacionRama() {
        return ultimaActualizacionRama;
    }

    public void setUltimaActualizacionRama(java.time.LocalDateTime ultimaActualizacionRama) {
        this.ultimaActualizacionRama = ultimaActualizacionRama;
    }

    public LocalDateTime getFechaUltimoSeguimiento() {
        return fechaUltimoSeguimiento;
    }

    public void setFechaUltimoSeguimiento(LocalDateTime fechaUltimoSeguimiento) {
        this.fechaUltimoSeguimiento = fechaUltimoSeguimiento;
    }

    public Boolean getUltimoSeguimientoExitoso() {
        return ultimoSeguimientoExitoso;
    }

    public void setUltimoSeguimientoExitoso(Boolean ultimoSeguimientoExitoso) {
        this.ultimoSeguimientoExitoso = ultimoSeguimientoExitoso;
    }

    public UsuarioInterno getAbogadoAsignado() {
        return abogadoAsignado;
    }

    public void setAbogadoAsignado(UsuarioInterno abogadoAsignado) {
        this.abogadoAsignado = abogadoAsignado;
    }

    public boolean isEsPrivadoCifrado() {
        return esPrivadoCifrado;
    }

    public void setEsPrivadoCifrado(boolean esPrivadoCifrado) {
        this.esPrivadoCifrado = esPrivadoCifrado;
    }

    public String getMotivoConfidencialidad() {
        return motivoConfidencialidad;
    }

    public void setMotivoConfidencialidad(String motivoConfidencialidad) {
        this.motivoConfidencialidad = motivoConfidencialidad;
    }
}
