-- Pedido explícito del usuario: "implementa todas las mejoras identificadas" (comparando
-- contra Lupa Jurídica) -- la más valiosa: detectar procesos NUEVOS buscando por el nombre
-- del cliente en el CPNU (ver RamaJudicialCpnuClient.buscarPorNombre), no solo vigilar
-- radicados que ya conocíamos. Nunca se crea un caso automáticamente desde esto -- la
-- búsqueda por nombre es ambigua (tocayos reales), así que cada hallazgo queda como
-- CANDIDATO para que un humano lo revise y decida (ver DeteccionProcesosNuevosService).
CREATE TABLE procesos_candidatos_detectados (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_cliente             BIGINT NOT NULL REFERENCES clientes (id) ON DELETE CASCADE,
    llave_proceso          VARCHAR(50) NOT NULL,
    despacho               VARCHAR(255),
    departamento           VARCHAR(100),
    sujetos_procesales     TEXT,
    fecha_proceso          DATE,
    fecha_ultima_actuacion DATE,
    estado                 VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    fecha_deteccion        TIMESTAMP NOT NULL DEFAULT now(),
    fecha_revision         TIMESTAMP,
    CONSTRAINT uq_procesos_candidatos_cliente_llave UNIQUE (id_cliente, llave_proceso),
    CONSTRAINT ck_procesos_candidatos_estado CHECK (estado IN ('PENDIENTE', 'DESCARTADO', 'YA_ES_CASO'))
);

CREATE INDEX ix_procesos_candidatos_estado ON procesos_candidatos_detectados (estado);
