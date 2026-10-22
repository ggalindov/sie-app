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
import sie.siejuridicos.crm.ClienteCrm;
import sie.siejuridicos.usuario.UsuarioInterno;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

// Un firmante de un DocumentoFirma: staff (gerente, o uno de los abogados que gerencia
// asignó a ESTE documento puntual -- firman automáticamente al emitirse, ver
// DocumentoAutoFirmanteStaff) o una parte elegida de la biblioteca de contactos
// (crm_clientes), que firma en línea vía un link único identificado por tokenHash.
// Acumula toda la evidencia técnica exigida por la Ley 527/1999, el Decreto 2364/2012 y la
// Ley 2213/2022 para sostener el valor probatorio de la firma: canal e identidad
// verificados por OTP, IP, geolocalización, huella de dispositivo, foto de verificación
// humana, foto del documento de identidad y la imagen (+ trazo vectorial cifrado) de la
// firma manuscrita capturada en pantalla.
//
// tipoContactoSnapshot copia ClienteCrm.tipo en el momento en que se crea el firmante y
// nunca se relee después -- un cambio posterior en el CRM jamás altera retroactivamente
// cómo se interpretó un documento ya firmado (misma filosofía de inmutabilidad evidencial
// que el resto de estos campos).
@Entity
@Table(name = "documento_firmantes")
public class DocumentoFirmante {

    public static final String TIPO_STAFF_GERENTE = "STAFF_GERENTE";
    public static final String TIPO_STAFF_ABOGADO = "STAFF_ABOGADO";
    public static final String TIPO_PARTE = "PARTE";

    public static final String ESTADO_PENDIENTE = "PENDIENTE";
    public static final String ESTADO_NOTIFICADO = "NOTIFICADO";
    public static final String ESTADO_OTP_VERIFICADO = "OTP_VERIFICADO";
    public static final String ESTADO_FIRMADO = "FIRMADO";
    public static final String ESTADO_RECHAZADO = "RECHAZADO";
    public static final String ESTADO_EXPIRADO = "EXPIRADO";
    public static final String ESTADO_REVOCADO = "REVOCADO";

    public static final String CANAL_WHATSAPP = "WHATSAPP";
    public static final String CANAL_EMAIL = "EMAIL";

    public static final String TIPO_CONTACTO_NATURAL = "PERSONA_NATURAL";
    public static final String TIPO_CONTACTO_EMPRESA = "EMPRESA";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Bloqueo optimista: sin esto, dos solicitudes /firmar casi simultáneas para el mismo
    // firmante (doble toque en el botón desde el celular, o un reintento del cliente que
    // cruza con la respuesta original) pueden pasar ambas la verificación de estado y
    // registrar la firma dos veces -- ver DocumentoFirmaService.registrarFirmaDeParte.
    @Version
    @Column(nullable = false)
    private long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "documento_id", nullable = false)
    private DocumentoFirma documento;

    @Column(name = "tipo_firmante", nullable = false)
    private String tipoFirmante;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id")
    private UsuarioInterno staff;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_crm_id")
    private ClienteCrm clienteCrm;

    @Column(name = "tipo_contacto_snapshot")
    private String tipoContactoSnapshot;

    @Column(name = "nombre_capturado", nullable = false)
    private String nombreCapturado;

    @Column(name = "documento_identidad_capturado")
    private String documentoIdentidadCapturado;

    @Column(nullable = false)
    private int orden;

    @Column(nullable = false)
    private String estado = ESTADO_PENDIENTE;

    @Column(name = "token_hash", unique = true)
    private String tokenHash;

    @Column(name = "token_expira_en")
    private LocalDateTime tokenExpiraEn;

    @Column(name = "canal_notificacion")
    private String canalNotificacion;

    @Column(name = "destino_notificacion")
    private String destinoNotificacion;

    @Column(name = "ip_captura")
    private String ipCaptura;

    @Column(name = "user_agent_captura")
    private String userAgentCaptura;

    @Column(name = "geolocalizacion_lat")
    private BigDecimal geolocalizacionLat;

    @Column(name = "geolocalizacion_lon")
    private BigDecimal geolocalizacionLon;

    @Column(name = "geolocalizacion_precision_m")
    private BigDecimal geolocalizacionPrecisionM;

    @Column(name = "geolocalizacion_capturada_en")
    private LocalDateTime geolocalizacionCapturadaEn;

    @Column(name = "geolocalizacion_rechazada", nullable = false)
    private boolean geolocalizacionRechazada;

    @Column(name = "documento_identidad_imagen")
    private byte[] documentoIdentidadImagen;

    @Column(name = "documento_identidad_content_type")
    private String documentoIdentidadContentType;

    @Column(name = "firma_imagen_png")
    private byte[] firmaImagenPng;

    @Column(name = "consentimiento_texto")
    private String consentimientoTexto;

    @Column(name = "consentimiento_aceptado_en")
    private LocalDateTime consentimientoAceptadoEn;

    @Column(name = "hash_evidencia")
    private String hashEvidencia;

    @Column(name = "firmado_en")
    private LocalDateTime firmadoEn;

    @Column(name = "rechazado_en")
    private LocalDateTime rechazadoEn;

    @Column(name = "motivo_rechazo", length = 2000)
    private String motivoRechazo;

    @Column(name = "identidad_verificada_en")
    private LocalDateTime identidadVerificadaEn;

    @Column(name = "intentos_identidad", nullable = false)
    private int intentosIdentidad;

    // Sellado de tiempo RFC 3161 (ver Rfc3161TimestampClient) del hash de evidencia de esta firma.
    @Column(name = "rfc3161_token")
    private byte[] rfc3161Token;

    @Column(name = "rfc3161_url_tsa")
    private String rfc3161UrlTsa;

    // Huella de dispositivo NO biométrica: hash de señales de configuración del navegador
    // (resolución, zona horaria, idioma, plataforma), nunca reconocimiento facial/de voz ni
    // nada que identifique al individuo por sí mismo.
    @Column(name = "huella_dispositivo")
    private String huellaDispositivo;

    @Column(name = "contexto_dispositivo_json")
    private String contextoDispositivoJson;

    // Heurística informativa (ver ProxyHeuristicsAnalyzer), nunca un veredicto ni un bloqueo.
    @Column(name = "senales_proxy_detectadas")
    private String senalesProxyDetectadas;

    @Column(name = "version_formula_evidencia", nullable = false)
    private int versionFormulaEvidencia = 1;

    // Foto tomada con la cámara del dispositivo justo antes de firmar: NO es reconocimiento
    // facial ni biometría de matching -- no se compara ni se procesa con ningún algoritmo,
    // solo se conserva como evidencia de seguridad adicional, igual que la foto del
    // documento de identidad.
    @Column(name = "foto_verificacion_humana")
    private byte[] fotoVerificacionHumana;

    @Column(name = "foto_verificacion_humana_content_type")
    private String fotoVerificacionHumanaContentType;

    @Column(name = "foto_verificacion_humana_capturada_en")
    private LocalDateTime fotoVerificacionHumanaCapturadaEn;

    // Biometría dinámica del trazo de la firma (puntos x/y/tiempo/presión capturados durante
    // el dibujo en el <canvas>): a diferencia de firmaImagenPng (el mapa de bits final), este
    // vector permite a un perito caligráfico analizar velocidad/aceleración/presión del
    // trazo. Cifrado en reposo con el mismo esquema envelope encryption AES-256-GCM que
    // DocumentoFirma.contenidoPdf -- nunca se expone en claro por ningún endpoint.
    @Column(name = "trazo_vectorial_cifrado")
    private byte[] trazoVectorialCifrado;

    @Column(name = "trazo_vectorial_iv")
    private byte[] trazoVectorialIv;

    @Column(name = "trazo_vectorial_dek_cifrado")
    private byte[] trazoVectorialDekCifrado;

    @Column(name = "trazo_vectorial_dek_iv")
    private byte[] trazoVectorialDekIv;

    @Column(name = "trazo_vectorial_kek_id")
    private String trazoVectorialKekId;

    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    // Última vez que se emitió/entregó un enlace de firma (base para programar recordatorios).
    @Column(name = "enlace_emitido_en")
    private LocalDateTime enlaceEmitidoEn;

    @Column(name = "recordatorios_enviados", nullable = false)
    private int recordatoriosEnviados;

    @Column(name = "ultimo_recordatorio_en")
    private LocalDateTime ultimoRecordatorioEn;

    protected DocumentoFirmante() {
    }

    public DocumentoFirmante(DocumentoFirma documento, String tipoFirmante, UsuarioInterno staff,
                              ClienteCrm clienteCrm, String tipoContactoSnapshot, String nombreCapturado,
                              String documentoIdentidadCapturado, int orden) {
        this.documento = documento;
        this.tipoFirmante = tipoFirmante;
        this.staff = staff;
        this.clienteCrm = clienteCrm;
        this.tipoContactoSnapshot = tipoContactoSnapshot;
        this.nombreCapturado = nombreCapturado;
        this.documentoIdentidadCapturado = documentoIdentidadCapturado;
        this.orden = orden;
        this.creadoEn = LocalDateTime.now();
    }

    public LocalDateTime getEnlaceEmitidoEn() {
        return enlaceEmitidoEn;
    }

    public void setEnlaceEmitidoEn(LocalDateTime enlaceEmitidoEn) {
        this.enlaceEmitidoEn = enlaceEmitidoEn;
    }

    public int getRecordatoriosEnviados() {
        return recordatoriosEnviados;
    }

    public LocalDateTime getUltimoRecordatorioEn() {
        return ultimoRecordatorioEn;
    }

    // Un enlace nuevo reinicia el ciclo de recordatorios.
    public void reiniciarRecordatorios(LocalDateTime emitidoEn) {
        this.enlaceEmitidoEn = emitidoEn;
        this.recordatoriosEnviados = 0;
        this.ultimoRecordatorioEn = null;
    }

    public void registrarRecordatorio(LocalDateTime enviadoEn) {
        this.recordatoriosEnviados++;
        this.ultimoRecordatorioEn = enviadoEn;
    }

    // true si ya firmó o rechazó: no bloquea a los firmantes posteriores ni el sellado.
    public boolean estaResuelto() {
        return ESTADO_FIRMADO.equals(estado) || ESTADO_RECHAZADO.equals(estado);
    }

    public boolean esStaff() {
        return TIPO_STAFF_GERENTE.equals(tipoFirmante) || TIPO_STAFF_ABOGADO.equals(tipoFirmante);
    }

    public boolean esParteExterna() {
        return TIPO_PARTE.equals(tipoFirmante);
    }

    public boolean esContactoEmpresa() {
        return TIPO_CONTACTO_EMPRESA.equals(tipoContactoSnapshot);
    }

    public UUID getId() {
        return id;
    }

    public long getVersion() {
        return version;
    }

    public DocumentoFirma getDocumento() {
        return documento;
    }

    public String getTipoFirmante() {
        return tipoFirmante;
    }

    public UsuarioInterno getStaff() {
        return staff;
    }

    public ClienteCrm getClienteCrm() {
        return clienteCrm;
    }

    public String getTipoContactoSnapshot() {
        return tipoContactoSnapshot;
    }

    public String getNombreCapturado() {
        return nombreCapturado;
    }

    public String getDocumentoIdentidadCapturado() {
        return documentoIdentidadCapturado;
    }

    public int getOrden() {
        return orden;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public LocalDateTime getTokenExpiraEn() {
        return tokenExpiraEn;
    }

    public void setTokenExpiraEn(LocalDateTime tokenExpiraEn) {
        this.tokenExpiraEn = tokenExpiraEn;
    }

    public String getCanalNotificacion() {
        return canalNotificacion;
    }

    public void setCanalNotificacion(String canalNotificacion) {
        this.canalNotificacion = canalNotificacion;
    }

    public String getDestinoNotificacion() {
        return destinoNotificacion;
    }

    public void setDestinoNotificacion(String destinoNotificacion) {
        this.destinoNotificacion = destinoNotificacion;
    }

    public String getIpCaptura() {
        return ipCaptura;
    }

    public void setIpCaptura(String ipCaptura) {
        this.ipCaptura = ipCaptura;
    }

    public String getUserAgentCaptura() {
        return userAgentCaptura;
    }

    public void setUserAgentCaptura(String userAgentCaptura) {
        this.userAgentCaptura = userAgentCaptura;
    }

    // IP/user-agent quedan asociados al firmante en el primer paso de su flujo (verificación
    // de OTP) y ya no se sobreescriben después, para que reflejen el dispositivo que
    // efectivamente demostró control del canal, no el último request cualquiera.
    public void registrarCapturaTecnica(String ip, String userAgent) {
        if (this.ipCaptura == null) {
            this.ipCaptura = ip;
            this.userAgentCaptura = userAgent;
        }
    }

    public BigDecimal getGeolocalizacionLat() {
        return geolocalizacionLat;
    }

    public BigDecimal getGeolocalizacionLon() {
        return geolocalizacionLon;
    }

    public BigDecimal getGeolocalizacionPrecisionM() {
        return geolocalizacionPrecisionM;
    }

    public LocalDateTime getGeolocalizacionCapturadaEn() {
        return geolocalizacionCapturadaEn;
    }

    public boolean isGeolocalizacionRechazada() {
        return geolocalizacionRechazada;
    }

    public void registrarGeolocalizacion(BigDecimal lat, BigDecimal lon, BigDecimal precisionM) {
        this.geolocalizacionLat = lat;
        this.geolocalizacionLon = lon;
        this.geolocalizacionPrecisionM = precisionM;
        this.geolocalizacionCapturadaEn = LocalDateTime.now();
        this.geolocalizacionRechazada = false;
    }

    public void registrarGeolocalizacionRechazada() {
        this.geolocalizacionRechazada = true;
        this.geolocalizacionCapturadaEn = LocalDateTime.now();
    }

    public byte[] getDocumentoIdentidadImagen() {
        return documentoIdentidadImagen;
    }

    public String getDocumentoIdentidadContentType() {
        return documentoIdentidadContentType;
    }

    public void registrarDocumentoIdentidad(byte[] imagen, String contentType) {
        this.documentoIdentidadImagen = imagen;
        this.documentoIdentidadContentType = contentType;
    }

    public byte[] getFirmaImagenPng() {
        return firmaImagenPng;
    }

    public String getConsentimientoTexto() {
        return consentimientoTexto;
    }

    public LocalDateTime getConsentimientoAceptadoEn() {
        return consentimientoAceptadoEn;
    }

    public void registrarConsentimiento(String textoAceptado) {
        this.consentimientoTexto = textoAceptado;
        this.consentimientoAceptadoEn = LocalDateTime.now();
    }

    public String getHashEvidencia() {
        return hashEvidencia;
    }

    public LocalDateTime getFirmadoEn() {
        return firmadoEn;
    }

    public void registrarFirma(byte[] firmaPng, String hashEvidencia) {
        this.firmaImagenPng = firmaPng;
        this.hashEvidencia = hashEvidencia;
        this.firmadoEn = LocalDateTime.now();
        this.estado = ESTADO_FIRMADO;
    }

    public LocalDateTime getRechazadoEn() {
        return rechazadoEn;
    }

    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    // Una parte declina firmar: a diferencia de registrarFirma(), esto nunca deja avanzar
    // el documento -- ver DocumentoFirmaService#registrarRechazoDeParte, que a partir de
    // aquí anula todo el proceso (un contrato no queda válidamente ejecutado si una de las
    // partes requeridas se negó a firmarlo).
    public void registrarRechazo(String motivo) {
        this.motivoRechazo = motivo;
        this.rechazadoEn = LocalDateTime.now();
        this.estado = ESTADO_RECHAZADO;
    }

    public LocalDateTime getCreadoEn() {
        return creadoEn;
    }

    public byte[] getRfc3161Token() {
        return rfc3161Token;
    }

    public String getRfc3161UrlTsa() {
        return rfc3161UrlTsa;
    }

    public void registrarSelloTiempo(byte[] token, String urlTsa) {
        this.rfc3161Token = token;
        this.rfc3161UrlTsa = urlTsa;
    }

    public String getHuellaDispositivo() {
        return huellaDispositivo;
    }

    public String getContextoDispositivoJson() {
        return contextoDispositivoJson;
    }

    public String getSenalesProxyDetectadas() {
        return senalesProxyDetectadas;
    }

    public int getVersionFormulaEvidencia() {
        return versionFormulaEvidencia;
    }

    // version_formula_evidencia sube a 2 solo si llega al menos la huella de dispositivo.
    public void registrarContextoDispositivo(String huellaDispositivo, String contextoDispositivoJson, String senalesProxyDetectadas) {
        this.huellaDispositivo = huellaDispositivo;
        this.contextoDispositivoJson = contextoDispositivoJson;
        this.senalesProxyDetectadas = senalesProxyDetectadas;
        if (huellaDispositivo != null && !huellaDispositivo.isBlank()) {
            this.versionFormulaEvidencia = Math.max(this.versionFormulaEvidencia, 2);
        }
    }

    public byte[] getFotoVerificacionHumana() {
        return fotoVerificacionHumana;
    }

    public String getFotoVerificacionHumanaContentType() {
        return fotoVerificacionHumanaContentType;
    }

    public LocalDateTime getFotoVerificacionHumanaCapturadaEn() {
        return fotoVerificacionHumanaCapturadaEn;
    }

    // version_formula_evidencia sube a 3: incluye esta foto en el hash de evidencia.
    public void registrarVerificacionHumana(byte[] imagen, String contentType) {
        this.fotoVerificacionHumana = imagen;
        this.fotoVerificacionHumanaContentType = contentType;
        this.fotoVerificacionHumanaCapturadaEn = LocalDateTime.now();
        this.versionFormulaEvidencia = Math.max(this.versionFormulaEvidencia, 3);
    }

    public byte[] getTrazoVectorialCifrado() {
        return trazoVectorialCifrado;
    }

    public byte[] getTrazoVectorialIv() {
        return trazoVectorialIv;
    }

    public byte[] getTrazoVectorialDekCifrado() {
        return trazoVectorialDekCifrado;
    }

    public byte[] getTrazoVectorialDekIv() {
        return trazoVectorialDekIv;
    }

    public String getTrazoVectorialKekId() {
        return trazoVectorialKekId;
    }

    public void registrarTrazoVectorial(byte[] contenidoCifrado, byte[] contenidoIv, byte[] dekCifrado, byte[] dekIv, String kekId) {
        this.trazoVectorialCifrado = contenidoCifrado;
        this.trazoVectorialIv = contenidoIv;
        this.trazoVectorialDekCifrado = dekCifrado;
        this.trazoVectorialDekIv = dekIv;
        this.trazoVectorialKekId = kekId;
    }

    public LocalDateTime getIdentidadVerificadaEn() {
        return identidadVerificadaEn;
    }

    public int getIntentosIdentidad() {
        return intentosIdentidad;
    }

    public boolean agotoIntentosIdentidad(int maximos) {
        return intentosIdentidad >= maximos;
    }

    public void registrarIntentoIdentidadFallido() {
        this.intentosIdentidad++;
    }

    public void registrarIdentidadVerificada() {
        this.identidadVerificadaEn = LocalDateTime.now();
    }
}
