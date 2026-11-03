-- "Si es nuevo para cada persona, si no la ha abierto le sale un botoncito rojo" (pedido
-- explícito del usuario): visto/no visto es POR USUARIO interno, no global -- dos abogados
-- distintos pueden tener el mismo caso con un estado de lectura independiente, así que no
-- alcanza con un único booleano/fecha en Caso.
CREATE TABLE seguimiento_visto_usuario (
    id BIGSERIAL PRIMARY KEY,
    id_caso BIGINT NOT NULL REFERENCES casos(id) ON DELETE CASCADE,
    id_usuario BIGINT NOT NULL REFERENCES usuarios_internos(id) ON DELETE CASCADE,
    fecha_vista TIMESTAMP NOT NULL,
    CONSTRAINT uq_seguimiento_visto_caso_usuario UNIQUE (id_caso, id_usuario)
);

CREATE INDEX idx_seguimiento_visto_usuario ON seguimiento_visto_usuario(id_usuario);
