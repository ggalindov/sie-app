-- Pedido explícito del usuario: digest diario SEPARADO para el gerente (todos los casos) y
-- para cada abogado (solo sus casos asignados) -- son destinatarios independientes, un
-- mismo boletín se marca "ya enviado" a cada uno por separado, nunca comparten la bandera.
ALTER TABLE boletines_judiciales_cache
    ADD COLUMN enviado_abogado BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_boletines_cache_pendiente_abogado ON boletines_judiciales_cache(enviado_abogado) WHERE NOT enviado_abogado;

-- El portal de Publicaciones Procesales nunca da el radicado exacto en el nombre del
-- archivo de un boletín (agrupa TODOS los procesos de un despacho en un mismo día, ver
-- PublicacionesProcesalesClient) -- solo se empareja por fecha + similitud de nombre contra
-- la actuación (ver DocumentoProvidenciaCandidato.coincideConNombreActuacion). Para el
-- gerente/abogado (revisores internos con criterio) se envía SIEMPRE, con o sin coincidir;
-- para el CLIENTE (que no puede juzgar si el documento es el correcto) solo se envía
-- automáticamente cuando sí coincide -- envíos ambiguos a un cliente real son un riesgo
-- real, no solo una formalidad.
ALTER TABLE boletines_judiciales_cache
    ADD COLUMN coincide_nombre_actuacion BOOLEAN NOT NULL DEFAULT FALSE;
