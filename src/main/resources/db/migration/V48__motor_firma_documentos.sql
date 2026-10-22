-- Motor de firma electrónica (adaptado del motor de UCN, ver
-- C:\Users\pc\Documents\motor-firma-para-SIE\LEEME.md): ciclo de vida completo de un
-- documento que gerencia sube, firma el gerente, firman los abogados asignados y luego
-- firman las N partes elegidas de la biblioteca de contactos (crm_clientes). Estado
-- monótono: DILIGENCIADO -> EMITIDO -> FIRMADO_STAFF -> [EN_FIRMA_PARTES] -> SELLADO ->
-- REMITIDO -> ENCRIPTADO, con ANULADO como terminal alcanzable desde EN_FIRMA_PARTES
-- (expiración o anulación manual). estado_integridad es un eje independiente que cualquier
-- verificación (interna o pública) puede voltear a ALTERADO de forma persistente.
--
-- A diferencia de registro_sistema/registro_envios (que nunca o sí guardan datos del
-- cliente según el caso, ver V29/V47), aquí el contenido del documento y los datos de cada
-- firmante SIEMPRE van cifrados o se cifran al sellar -- es la pieza más sensible del
-- sistema: documentos legales reales con datos de identidad y firmas manuscritas.
CREATE TABLE documentos_firma (
    id                     UUID PRIMARY KEY,
    caso_id                BIGINT NULL REFERENCES casos (id),
    creado_por_staff_id    BIGINT NOT NULL REFERENCES usuarios_internos (id),
    nombre                 VARCHAR(255) NOT NULL,
    content_type           VARCHAR(100) NOT NULL,
    contenido_pdf          BYTEA NOT NULL,
    contenido_cifrado      BOOLEAN NOT NULL DEFAULT FALSE,
    contenido_iv           BYTEA NULL,
    dek_cifrado            BYTEA NULL,
    dek_iv                 BYTEA NULL,
    kek_id                 VARCHAR(60) NULL,
    numero_secuencia       BIGINT NULL,
    codigo_verificacion    VARCHAR(64) NULL,
    hash_contenido         VARCHAR(64) NULL,
    orden_creacion         BIGINT GENERATED ALWAYS AS IDENTITY,
    estado_documento       VARCHAR(20) NOT NULL DEFAULT 'DILIGENCIADO',
    estado_integridad      VARCHAR(10) NOT NULL DEFAULT 'VIGENTE',
    firma_app_valor        BYTEA NULL,
    firma_app_kid          VARCHAR(60) NULL,
    firma_app_generada_en  TIMESTAMP NULL,
    rfc3161_token          BYTEA NULL,
    rfc3161_url_tsa        VARCHAR(255) NULL,
    rfc3161_sellado_en     TIMESTAMP NULL,
    fecha_creacion         TIMESTAMP NOT NULL DEFAULT now(),
    version                BIGINT NOT NULL DEFAULT 0
);

ALTER TABLE documentos_firma ADD CONSTRAINT documentos_firma_estado_check
    CHECK (estado_documento IN ('DILIGENCIADO', 'EMITIDO', 'FIRMADO_STAFF', 'NOTIFICADO',
                                 'EN_FIRMA_PARTES', 'SELLADO', 'REMITIDO', 'ENCRIPTADO', 'ANULADO'));
ALTER TABLE documentos_firma ADD CONSTRAINT documentos_firma_integridad_check
    CHECK (estado_integridad IN ('VIGENTE', 'ALTERADO'));

CREATE INDEX idx_documentos_firma_caso ON documentos_firma (caso_id);
CREATE INDEX idx_documentos_firma_estado_documento ON documentos_firma (estado_documento);
CREATE INDEX idx_documentos_firma_estado_integridad ON documentos_firma (estado_integridad);
CREATE UNIQUE INDEX uq_documentos_firma_codigo_verificacion ON documentos_firma (codigo_verificacion);

-- Secuencia propia para numero_secuencia (impreso en el documento sellado como referencia
-- humana corta) -- asignado una sola vez, en la transición DILIGENCIADO -> EMITIDO.
CREATE SEQUENCE documento_firma_numero_secuencia_seq START WITH 1;

-- Snapshots binarios del PDF anteriores al sellado: permite regenerar un borrador (ej. tras
-- corregir un dato antes de que las partes firmen) sin perder el historial exacto de lo que
-- existió en cada momento, sin violar la inmutabilidad una vez el documento queda SELLADO.
CREATE TABLE documento_versiones (
    id                   UUID PRIMARY KEY,
    documento_id         UUID NOT NULL REFERENCES documentos_firma (id),
    contenido_pdf        BYTEA NOT NULL,
    hash_contenido       VARCHAR(64) NOT NULL,
    motivo               VARCHAR(255) NOT NULL,
    creado_por_staff_id  BIGINT NULL REFERENCES usuarios_internos (id),
    creado_en            TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_documento_versiones_documento ON documento_versiones (documento_id);

-- Firmantes de un documento: staff que firma automáticamente (gerente + los abogados que
-- gerencia asignó a ESTE documento puntual, no todos los abogados activos de la firma) y
-- partes externas (elegidas de la biblioteca de contactos crm_clientes) que firman en línea
-- vía un link único por token. tipo_contacto_snapshot copia ClienteCrm.tipo en el momento en
-- que se crea el firmante -- nunca se relee después, para que un cambio posterior en el CRM
-- jamás altere retroactivamente cómo se interpretó un documento ya firmado.
CREATE TABLE documento_firmantes (
    id                                UUID PRIMARY KEY,
    documento_id                      UUID NOT NULL REFERENCES documentos_firma (id),
    tipo_firmante                     VARCHAR(20) NOT NULL,
    staff_id                          BIGINT NULL REFERENCES usuarios_internos (id),
    cliente_crm_id                    BIGINT NULL REFERENCES crm_clientes (id),
    tipo_contacto_snapshot            VARCHAR(20) NULL,
    nombre_capturado                  VARCHAR(255) NOT NULL,
    documento_identidad_capturado     VARCHAR(50) NULL,
    orden                             INT NOT NULL DEFAULT 0,
    estado                            VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    token_hash                        VARCHAR(64) NULL,
    token_expira_en                   TIMESTAMP NULL,
    canal_notificacion                VARCHAR(20) NULL,
    destino_notificacion              VARCHAR(160) NULL,
    ip_captura                        VARCHAR(64) NULL,
    user_agent_captura                VARCHAR(255) NULL,
    geolocalizacion_lat               NUMERIC(9, 6) NULL,
    geolocalizacion_lon               NUMERIC(9, 6) NULL,
    geolocalizacion_precision_m       NUMERIC(8, 2) NULL,
    geolocalizacion_capturada_en      TIMESTAMP NULL,
    geolocalizacion_rechazada         BOOLEAN NOT NULL DEFAULT FALSE,
    documento_identidad_imagen        BYTEA NULL,
    documento_identidad_content_type  VARCHAR(50) NULL,
    firma_imagen_png                  BYTEA NULL,
    consentimiento_texto              VARCHAR(4000) NULL,
    consentimiento_aceptado_en        TIMESTAMP NULL,
    hash_evidencia                    VARCHAR(64) NULL,
    firmado_en                        TIMESTAMP NULL,
    rechazado_en                      TIMESTAMP NULL,
    motivo_rechazo                    VARCHAR(2000) NULL,
    identidad_verificada_en           TIMESTAMP NULL,
    intentos_identidad                INT NOT NULL DEFAULT 0,
    huella_dispositivo                VARCHAR(64) NULL,
    contexto_dispositivo_json         TEXT NULL,
    senales_proxy_detectadas          VARCHAR(500) NULL,
    version_formula_evidencia         INTEGER NOT NULL DEFAULT 1,
    foto_verificacion_humana                 BYTEA NULL,
    foto_verificacion_humana_content_type    VARCHAR(100) NULL,
    foto_verificacion_humana_capturada_en    TIMESTAMP NULL,
    trazo_vectorial_cifrado           BYTEA NULL,
    trazo_vectorial_iv                BYTEA NULL,
    trazo_vectorial_dek_cifrado       BYTEA NULL,
    trazo_vectorial_dek_iv            BYTEA NULL,
    trazo_vectorial_kek_id            VARCHAR(255) NULL,
    rfc3161_token                     BYTEA NULL,
    rfc3161_url_tsa                   VARCHAR(255) NULL,
    enlace_emitido_en                 TIMESTAMP NULL,
    recordatorios_enviados            INTEGER NOT NULL DEFAULT 0,
    ultimo_recordatorio_en            TIMESTAMP NULL,
    version                           BIGINT NOT NULL DEFAULT 0,
    creado_en                         TIMESTAMP NOT NULL DEFAULT now()
);

ALTER TABLE documento_firmantes ADD CONSTRAINT documento_firmantes_tipo_check
    CHECK (tipo_firmante IN ('STAFF_GERENTE', 'STAFF_ABOGADO', 'PARTE'));
ALTER TABLE documento_firmantes ADD CONSTRAINT documento_firmantes_estado_check
    CHECK (estado IN ('PENDIENTE', 'NOTIFICADO', 'OTP_VERIFICADO', 'FIRMADO', 'RECHAZADO', 'EXPIRADO', 'REVOCADO'));
ALTER TABLE documento_firmantes ADD CONSTRAINT documento_firmantes_canal_check
    CHECK (canal_notificacion IN ('EMAIL', 'WHATSAPP'));
ALTER TABLE documento_firmantes ADD CONSTRAINT documento_firmantes_tipo_contacto_check
    CHECK (tipo_contacto_snapshot IN ('PERSONA_NATURAL', 'EMPRESA'));
-- Un firmante de staff referencia a usuarios_internos y no tiene cliente_crm_id/tipo_contacto;
-- uno de parte referencia a crm_clientes (con su snapshot de tipo) y no tiene staff_id.
ALTER TABLE documento_firmantes ADD CONSTRAINT documento_firmantes_origen_check
    CHECK (
        (tipo_firmante IN ('STAFF_GERENTE', 'STAFF_ABOGADO') AND staff_id IS NOT NULL
            AND cliente_crm_id IS NULL AND tipo_contacto_snapshot IS NULL)
        OR (tipo_firmante = 'PARTE' AND cliente_crm_id IS NOT NULL AND staff_id IS NULL
            AND tipo_contacto_snapshot IS NOT NULL)
    );

CREATE INDEX idx_documento_firmantes_documento ON documento_firmantes (documento_id);
CREATE INDEX idx_documento_firmantes_staff ON documento_firmantes (staff_id);
CREATE INDEX idx_documento_firmantes_cliente_crm ON documento_firmantes (cliente_crm_id);
CREATE UNIQUE INDEX uq_documento_firmantes_token_hash ON documento_firmantes (token_hash);

CREATE TABLE documento_otp (
    id                     UUID PRIMARY KEY,
    documento_firmante_id  UUID NOT NULL REFERENCES documento_firmantes (id),
    canal                  VARCHAR(20) NOT NULL,
    codigo_hash            VARCHAR(64) NOT NULL,
    expira_en              TIMESTAMP NOT NULL,
    intentos               INT NOT NULL DEFAULT 0,
    intentos_maximos       INT NOT NULL DEFAULT 5,
    verificado_en          TIMESTAMP NULL,
    ip_solicitud           VARCHAR(64) NULL,
    creado_en              TIMESTAMP NOT NULL DEFAULT now()
);
ALTER TABLE documento_otp ADD CONSTRAINT documento_otp_canal_check
    CHECK (canal IN ('EMAIL', 'WHATSAPP'));
CREATE INDEX idx_documento_otp_firmante ON documento_otp (documento_firmante_id);

-- Línea de tiempo técnica por documento, con cadena de hashes encadenada tipo append-only
-- (ver HashChainCalculator): cada evento liga su HMAC con el del evento anterior del mismo
-- documento, así que alterar o borrar una fila intermedia rompe el encadenamiento a partir
-- de ese punto, detectable por DocumentoEventoChainVerifier. Nace con las 3 columnas de la
-- cadena NOT NULL desde el inicio (a diferencia de UCN, que las nació NULLABLE para poder
-- hacer backfill de filas ya existentes -- SIE arranca sin filas previas, no hace falta).
CREATE TABLE documento_eventos (
    id                        UUID PRIMARY KEY,
    documento_id              UUID NOT NULL REFERENCES documentos_firma (id),
    tipo_evento               VARCHAR(40) NOT NULL,
    actor_tipo                VARCHAR(20) NOT NULL,
    staff_id                  BIGINT NULL REFERENCES usuarios_internos (id),
    documento_firmante_id     UUID NULL REFERENCES documento_firmantes (id),
    descripcion               VARCHAR(500) NULL,
    ip                        VARCHAR(64) NULL,
    user_agent                VARCHAR(255) NULL,
    hash_documento_en_evento  VARCHAR(64) NULL,
    secuencia                 BIGINT NOT NULL,
    hash_evento               VARCHAR(64) NOT NULL,
    hash_evento_anterior      VARCHAR(64) NOT NULL,
    ocurrido_en               TIMESTAMP NOT NULL DEFAULT now()
);

ALTER TABLE documento_eventos ADD CONSTRAINT documento_eventos_tipo_check
    CHECK (tipo_evento IN ('DILIGENCIADO', 'EMITIDO', 'FIRMADO_STAFF', 'NOTIFICADO',
                            'EN_FIRMA_PARTES_INICIADO', 'FIRMADO_POR_PARTE', 'FIRMA_RECHAZADA', 'SELLADO', 'REMITIDO', 'ENCRIPTADO',
                            'ALTERADO_DETECTADO', 'VERIFICACION_CONSULTADA',
                            'SOLICITUD_CAMBIO_CREADA', 'SOLICITUD_CAMBIO_EJECUTADA',
                            'LLAVE_APLICACION_ROTADA', 'SELLO_TIEMPO_APLICADO', 'SELLO_TIEMPO_FALLIDO',
                            'CERTIFICADO_EVIDENCIA_DESCARGADO',
                            'RECORDATORIO_ENVIADO', 'ENLACE_REVOCADO', 'PROCESO_EXPIRADO', 'PROCESO_ANULADO'));
ALTER TABLE documento_eventos ADD CONSTRAINT documento_eventos_actor_check
    CHECK (actor_tipo IN ('STAFF', 'PARTE', 'SISTEMA'));

CREATE INDEX idx_documento_eventos_documento_fecha ON documento_eventos (documento_id, ocurrido_en);
CREATE UNIQUE INDEX uq_documento_eventos_doc_secuencia ON documento_eventos (documento_id, secuencia);

-- Doble confirmación (gerente ADMIN_GENERAL + ABOGADO) para cualquier movimiento sensible
-- sobre un documento ya emitido o sellado. Al crearla, el rol del solicitante queda
-- auto-confirmado; un staff distinto con el rol contrario debe completar la otra
-- confirmación antes de poder ejecutarla.
CREATE TABLE documento_cambio_solicitudes (
    id                              UUID PRIMARY KEY,
    documento_id                    UUID NOT NULL REFERENCES documentos_firma (id),
    tipo_accion                     VARCHAR(30) NOT NULL,
    justificacion                   VARCHAR(2000) NOT NULL,
    payload_json                    VARCHAR(4000) NULL,
    estado                          VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    solicitado_por_staff_id         BIGINT NOT NULL REFERENCES usuarios_internos (id),
    solicitado_en                   TIMESTAMP NOT NULL DEFAULT now(),
    confirmacion_gerente_staff_id   BIGINT NULL REFERENCES usuarios_internos (id),
    confirmacion_gerente_en         TIMESTAMP NULL,
    confirmacion_abogado_staff_id   BIGINT NULL REFERENCES usuarios_internos (id),
    confirmacion_abogado_en         TIMESTAMP NULL,
    ejecutado_en                    TIMESTAMP NULL,
    rechazado_por_staff_id          BIGINT NULL REFERENCES usuarios_internos (id),
    rechazado_en                    TIMESTAMP NULL,
    motivo_rechazo                  VARCHAR(2000) NULL
);

ALTER TABLE documento_cambio_solicitudes ADD CONSTRAINT documento_cambio_tipo_check
    CHECK (tipo_accion IN ('INVALIDAR', 'RE_EMITIR', 'CORREGIR_METADATO', 'ANULAR_FIRMA_PARTE', 'REVERTIR_ALTERADO'));
ALTER TABLE documento_cambio_solicitudes ADD CONSTRAINT documento_cambio_estado_check
    CHECK (estado IN ('PENDIENTE', 'CONFIRMADA_PARCIAL', 'APROBADA', 'EJECUTADA', 'RECHAZADA'));

CREATE INDEX idx_documento_cambio_documento ON documento_cambio_solicitudes (documento_id);
CREATE INDEX idx_documento_cambio_estado ON documento_cambio_solicitudes (estado);

-- Firma criptográfica ASIMÉTRICA propia (Ed25519) del hash de cada documento sellado,
-- adicional al HMAC simétrico (codigo_verificacion). A diferencia de un HMAC, cualquier
-- tercero con la llave PÚBLICA puede verificarla, sin exponer nunca la privada. No es un
-- certificado X.509 de una entidad de certificación acreditada (eso sería firma digital
-- plena, no firma electrónica simple/avanzada).
CREATE TABLE firma_aplicacion_claves (
    id                     UUID PRIMARY KEY,
    kid                    VARCHAR(60) NOT NULL UNIQUE,
    algoritmo              VARCHAR(20) NOT NULL DEFAULT 'Ed25519',
    clave_publica          BYTEA NOT NULL,
    clave_privada_cifrada  BYTEA NOT NULL,
    clave_privada_iv       BYTEA NOT NULL,
    kek_id                 VARCHAR(60) NOT NULL,
    activa                 BOOLEAN NOT NULL DEFAULT TRUE,
    creada_en              TIMESTAMP NOT NULL DEFAULT now(),
    rotada_en              TIMESTAMP NULL
);
CREATE INDEX idx_firma_aplicacion_clave_activa ON firma_aplicacion_claves (activa);

-- Llave RSA + certificado X.509 (autofirmado) para aplicar firma digital PAdES/PKCS#7
-- embebida en el PDF final de cada documento sellado, reconocida nativamente por lectores
-- como Adobe Acrobat Reader -- adicional a la firma de aplicación de arriba (que solo firma
-- el hash en base de datos, no queda incrustada en el propio archivo).
CREATE TABLE firma_pades_claves (
    id                     UUID PRIMARY KEY,
    kid                    VARCHAR(255) NOT NULL UNIQUE,
    algoritmo              VARCHAR(50) NOT NULL DEFAULT 'RSA-3072',
    certificado            BYTEA NOT NULL,
    clave_privada_cifrada  BYTEA NOT NULL,
    clave_privada_iv       BYTEA NOT NULL,
    kek_id                 VARCHAR(255) NOT NULL,
    activa                 BOOLEAN NOT NULL DEFAULT TRUE,
    creada_en              TIMESTAMP NOT NULL DEFAULT now(),
    vigente_hasta          TIMESTAMP NOT NULL,
    rotada_en              TIMESTAMP NULL
);
CREATE INDEX idx_firma_pades_clave_activa ON firma_pades_claves (activa);

-- Firma manuscrita propia de cada usuario interno (gerente o abogado), subida una sola vez
-- desde su perfil ("Mi firma") y reutilizada por el motor para auto-firmar cada documento
-- donde ese usuario quede como firmante de staff -- pedido explícito del usuario: "el
-- sistema debe insertar firma del gerente... el sistema ingresa la firma de los abogados".
-- Relación 1:1 opcional, por eso son columnas nuevas sobre usuarios_internos y no una tabla
-- aparte (mismo criterio ya usado para fecha_cita sobre solicitudes).
ALTER TABLE usuarios_internos ADD COLUMN firma_png BYTEA NULL;
ALTER TABLE usuarios_internos ADD COLUMN firma_content_type VARCHAR(50) NULL;
ALTER TABLE usuarios_internos ADD COLUMN firma_actualizada_en TIMESTAMP NULL;
