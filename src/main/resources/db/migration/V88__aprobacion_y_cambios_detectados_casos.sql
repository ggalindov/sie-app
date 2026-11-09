-- Pedido explícito del usuario (2026-11-09): "implementa una aprovacion que lo pueda hacer
-- gerente o abogado, que cuando el sistema detecte la creacion de un nuevo caso del google
-- sheet salga un popup de notificaciones de casos nuevos identificados, donde se pueda
-- aceptar la vinculacion del caso al sistema y se pueda editar, asi mismo como un pop-up al
-- actualizarlos que detecte que casos tuvieron cambios y cuales fueron".
--
-- DEFAULT FALSE a propósito: los 262+ casos YA existentes en producción nunca deben quedar
-- marcados pendientes de aprobación de golpe (serían cientos de popups) -- solo un caso
-- CREADO a partir de aquí (ver CasoService.sincronizarDesdeHoja()) nace con este campo en
-- TRUE.
ALTER TABLE casos ADD COLUMN pendiente_aprobacion BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE casos ADD COLUMN aprobado_por_id BIGINT REFERENCES usuarios_internos(id);
ALTER TABLE casos ADD COLUMN fecha_aprobado TIMESTAMP;

-- Un cambio detectado por fila de la hoja durante sincronizarDesdeHoja() (radicado, nombre
-- del cliente en la hoja, o abogado asignado) sobre un caso que YA existía -- ver
-- CasoCambioDetectado. ON DELETE CASCADE: si el caso se elimina (ver la reconciliación de
-- sincronizarDesdeHoja(), una fila que deja de existir en la hoja borra su Caso), su
-- historial de cambios detectados ya no tiene sentido.
CREATE TABLE caso_cambios_detectados (
    id BIGSERIAL PRIMARY KEY,
    id_caso BIGINT NOT NULL REFERENCES casos(id) ON DELETE CASCADE,
    campo VARCHAR(50) NOT NULL,
    valor_anterior TEXT,
    valor_nuevo TEXT,
    fecha_deteccion TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revisado BOOLEAN NOT NULL DEFAULT FALSE,
    revisado_por_id BIGINT REFERENCES usuarios_internos(id),
    fecha_revisado TIMESTAMP
);

-- El popup de cambios pendientes solo lee revisado = false -- esta consulta corre cada vez
-- que un gerente o abogado abre el panel.
CREATE INDEX idx_caso_cambios_revisado ON caso_cambios_detectados (revisado);
