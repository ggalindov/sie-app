-- Pedido explícito del usuario (2026-11-05): "implementa la misma funcionalidad que tenia
-- gerencia de casos privados y cifrados, implementala para todos los abogados, que cada uno
-- tenga un espacio privado para casos personales o sensibles... limitandolo a un maximo de
-- 20 casos para abogados, estos casos no los podra ver nadie, ni gerencia ni nadie, ni
-- gerencia puede ver los de abogados, ni abogados los de gerencia".
--
-- NULL = bóveda de Gerencia (comportamiento exacto de siempre, sin cambios). No-NULL = bóveda
-- personal de ESE abogado, visible solo para él (ver CasoService.verificarAccesoPrivado()).
ALTER TABLE casos ADD COLUMN propietario_privado_id BIGINT REFERENCES usuarios_internos(id);

CREATE INDEX idx_casos_propietario_privado ON casos(propietario_privado_id)
    WHERE propietario_privado_id IS NOT NULL;
