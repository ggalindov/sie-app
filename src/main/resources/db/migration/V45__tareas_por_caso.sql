-- Registro de tareas por caso, con responsable asignado (pedido explícito del usuario: "deja un
-- registro de tareas por caso y con responsabilidad, que el administrador pueda dejar una tarea
-- y asignar a que persona debe tener la responsabilidad [...] esta habilidad la podra tener tanto
-- admin como cualquiera de los abogados"). A diferencia de crm_tareas (V40, ligada a un
-- ClienteCrm en general, con un nombre de usuario en texto libre sin validar), esta tabla
-- queda ligada a un Caso puntual y el responsable es un usuario interno real -- permite
-- consultar "mis tareas pendientes" de verdad (ver TareaRepository).
CREATE TABLE tareas (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    caso_id              BIGINT NOT NULL REFERENCES casos(id) ON DELETE CASCADE,
    titulo               VARCHAR(255) NOT NULL,
    descripcion          TEXT,
    fecha_vencimiento    TIMESTAMP,
    prioridad            VARCHAR(20) NOT NULL DEFAULT 'MEDIA',
    completada           BOOLEAN NOT NULL DEFAULT false,
    usuario_asignado_id  BIGINT NOT NULL REFERENCES usuarios_internos(id),
    usuario_creador_id   BIGINT NOT NULL REFERENCES usuarios_internos(id),
    fecha_creacion       TIMESTAMP NOT NULL DEFAULT now(),
    fecha_completada     TIMESTAMP
);

-- El panel filtra casi siempre "tareas de este caso" o "tareas pendientes de este usuario" --
-- un índice parcial sobre pendientes es más chico y rápido para la segunda consulta, que es la
-- que alimenta la página "Tareas" de cada abogado/admin.
CREATE INDEX ix_tareas_caso ON tareas (caso_id);
CREATE INDEX ix_tareas_asignado_pendientes ON tareas (usuario_asignado_id) WHERE completada = false;
