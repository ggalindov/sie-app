-- Pedido explícito del usuario (2026-11-01): "setea todos los radicados a radicados
-- funcionales" + "el sistema debe reflejar que fallos hay". Desde ahora, Caso.setRadicadoId()
-- (ver RadicadoUtil) normaliza cada guardado nuevo -- este backfill cubre lo que ya existía
-- antes de ese cambio, sin tocar el Google Sheets de origen (solo nuestra propia copia).

ALTER TABLE casos ADD COLUMN radicado_formato_invalido boolean NOT NULL DEFAULT false;

-- Limpia espacios/guiones/puntos internos SOLO cuando el resultado es un radicado CPNU real
-- de 23 dígitos (bug real confirmado en vivo: 13 casos con radicados como
-- "11001 31 03 048 2026 00324 00", que son procesos reales con ruido de formato, no datos
-- corruptos). Nunca toca un identificador que no reduce a 23 dígitos -- esos son de otra
-- jurisdicción (comisaría de familia, inspección de policía) y mangarlos rompería lo que se
-- le muestra/envía al cliente.
UPDATE casos
SET radicado_id = regexp_replace(radicado_id, '[\s\-.]+', '', 'g')
WHERE radicado_id IS NOT NULL
  AND regexp_replace(radicado_id, '[\s\-.]+', '', 'g') ~ '^[0-9]{23}$';

-- Marca como "formato inválido" todo lo que, tras limpiar, no sea un radicado CPNU real de
-- 23 dígitos -- visible en el panel para que un abogado sepa que ese caso puntual no tendrá
-- seguimiento automático 24/7 hasta que se corrija o se confirme que es de otro tipo.
UPDATE casos
SET radicado_formato_invalido = true
WHERE radicado_id IS NOT NULL
  AND NOT (regexp_replace(radicado_id, '[\s\-.]+', '', 'g') ~ '^[0-9]{23}$');
