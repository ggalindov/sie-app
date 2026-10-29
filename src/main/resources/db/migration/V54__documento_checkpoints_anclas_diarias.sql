-- Detección de un ataque de truncamiento: DocumentoEventoChainVerifier ya detecta si algún
-- eslabón INTERMEDIO de la cadena de eventos de un documento fue alterado, pero una cadena
-- de hashes simple no puede detectar que alguien borró los ÚLTIMOS N eventos -- lo que
-- queda sigue viéndose perfectamente consecutivo y bien encadenado hasta el último evento
-- que sobrevivió. documento_eventos_checkpoints guarda, cada noche, cuántos eventos tenía
-- cada documento en ese momento; como la tabla es append-only (solo puede crecer), si algún
-- checkpoint pasado quedó con MÁS eventos de los que existen hoy, eso es evidencia
-- concluyente de que algo se borró (ver DocumentoEventoChainVerifier).
CREATE TABLE documento_eventos_checkpoints (
    id                  UUID PRIMARY KEY,
    documento_id        UUID NOT NULL REFERENCES documentos_firma (id),
    cantidad_eventos    BIGINT NOT NULL,
    hash_ultimo_evento  VARCHAR(64) NOT NULL,
    fecha               DATE NOT NULL,
    creado_en           TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_documento_eventos_checkpoints_documento ON documento_eventos_checkpoints (documento_id);

-- Mismo trigger append-only que ya protege documento_eventos (ver V53): los checkpoints en
-- sí mismos no tienen ninguna vía legítima de edición o borrado, ni siquiera la excepción de
-- "documento eliminado" (un checkpoint histórico sigue siendo prueba válida aunque el
-- documento que describía ya no exista).
CREATE TRIGGER trg_documento_eventos_checkpoints_append_only
    BEFORE UPDATE OR DELETE ON documento_eventos_checkpoints
    FOR EACH ROW
    EXECUTE FUNCTION fn_rechazar_modificacion_tabla_append_only();

-- Sello de tiempo RFC 3161 agregado, una vez al día, sobre el hash de TODOS los checkpoints
-- de ese día -- prueba externa adicional (no depende solo del reloj/BD propios del
-- servidor) de que el estado de todo el historial de auditoría en esa fecha era ese y no
-- otro. 'token_rfc3161' nace NULL (la TSA externa puede no responder a tiempo) y se
-- completa en un reintento posterior -- a diferencia de documento_eventos_checkpoints, esta
-- tabla SÍ necesita permitir un UPDATE legítimo (rellenar el token), por eso no lleva el
-- trigger append-only.
CREATE TABLE anclas_diarias_integridad (
    id                  BIGSERIAL PRIMARY KEY,
    fecha               DATE NOT NULL,
    hash_agregado       VARCHAR(64) NOT NULL,
    token_rfc3161       BYTEA NULL,
    url_tsa             VARCHAR(255) NULL,
    cantidad_documentos INT NOT NULL,
    creado_en           TIMESTAMP NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_anclas_diarias_integridad_fecha ON anclas_diarias_integridad (fecha);
