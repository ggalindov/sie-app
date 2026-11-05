-- Pedido explícito del usuario (2026-11-05): "implementa un nuevo boton para cada caso el
-- cual nos permita finalizarlo, y el caso se remite a un nuevo apartado de que diga casos
-- finalizados para guardarlo en el sistema y guarde todo el ultimo seguimiento pero ya una
-- vez finalizado el sistema ya no le hara vigilancia". Nunca se borra nada al finalizar: las
-- actuaciones y boletines ya cacheados quedan intactos como historial ("el último
-- seguimiento"), esta bandera solo saca al caso de las consultas que alimentan el ciclo de
-- vigilancia (ver CasoRepository/ActuacionJudicialRegistradaRepository).
ALTER TABLE casos ADD COLUMN finalizado BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE casos ADD COLUMN fecha_finalizacion TIMESTAMP;
ALTER TABLE casos ADD COLUMN finalizado_por_id BIGINT REFERENCES usuarios_internos(id);

CREATE INDEX idx_casos_finalizado ON casos(finalizado);
