-- Nueva área del panel: Cobros Pendientes, sincronizada desde las dos pestañas (EMPRESAS,
-- PERSONAS NATURALES) de un Google Sheets DISTINTO al de Casos -- ver ClienteCobro,
-- HojaCobrosService.
--
-- TEXT (no VARCHAR con límite fijo) en los campos cifrados: el texto cifrado en Base64 (IV +
-- ciphertext + tag, ver CampoCifradoConverter) siempre es más largo que el original y su
-- longitud varía -- mismo criterio que clientes.correo/nombre/telefono en la migración V23.
CREATE TABLE clientes_cobro (
    id                        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    tipo                      VARCHAR(20) NOT NULL,
    numero_fila               VARCHAR(20) NOT NULL,
    nombre                    TEXT NOT NULL,
    correo                    TEXT,
    telefono                  TEXT,
    telefono_hash             VARCHAR(64),
    cedula_nit                TEXT,
    honorarios                TEXT,
    pago_este_mes             BOOLEAN,
    respondio_mensaje         VARCHAR(20),
    fecha_ultimo_recordatorio TIMESTAMP,
    activo                    BOOLEAN NOT NULL DEFAULT true,
    fecha_creacion            TIMESTAMP NOT NULL DEFAULT now(),
    fecha_actualizacion       TIMESTAMP NOT NULL DEFAULT now(),

    CONSTRAINT uq_clientes_cobro_tipo_numero_fila UNIQUE (tipo, numero_fila)
);

-- El panel filtra/lista casi siempre "activos de un tipo" -- un índice parcial (solo sobre
-- las filas activas) es más chico y rápido que uno sobre la tabla completa para esa consulta,
-- que es la que de verdad se repite.
CREATE INDEX ix_clientes_cobro_activos ON clientes_cobro (tipo, activo) WHERE activo = true;

-- Búsqueda del webhook de respuestas de WhatsApp (ver ClienteCobroRepository.findByTelefonoHash()).
CREATE INDEX ix_clientes_cobro_telefono_hash ON clientes_cobro (telefono_hash) WHERE telefono_hash IS NOT NULL;
