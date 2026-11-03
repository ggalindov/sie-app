-- Pedido explícito del usuario: "la vigilancia tiene que mostrar toda, absolutamente toda
-- la informacion... todo lo que se detecte por caso, todo lo debe traer al sistema" --
-- Proceso/Detalle del CPNU ya trae "ultimaActualizacion" (cuándo la Rama Judicial misma
-- actualizó este proceso en SU sistema) y este proyecto lo estaba descartando, igual que
-- antes pasó con la hora exacta de las actuaciones (ver migración V65). Distinto de
-- fecha_ultimo_seguimiento (cuándo NOSOTROS revisamos el caso) -- este es un dato propio de
-- la Rama Judicial, no nuestro.
ALTER TABLE casos ADD COLUMN ultima_actualizacion_rama TIMESTAMP;
