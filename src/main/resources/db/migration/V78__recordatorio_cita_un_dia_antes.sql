-- Recordatorio de cita UN DIA ANTES (2026-11-02). Regla pedida por el usuario: cada cita lleva
-- tres avisos al cliente -- (1) al programarla (confirmacion, ya existia), (2) un dia antes
-- (nuevo, este flag) y (3) 1 hora antes (recordatorio_enviado, ya existia).
ALTER TABLE solicitudes
    ADD COLUMN recordatorio_dia_antes_enviado BOOLEAN NOT NULL DEFAULT FALSE;

-- Citas ya agendadas cuyo "dia antes" ya pasó o esta por pasar (menos de 24 h para empezar) o
-- que ya tuvieron lugar: se marcan como enviadas para que, al desplegar, no salga una rafaga de
-- recordatorios retroactivos. Las citas de mas de 24 h hacia adelante conservan FALSE y
-- recibiran su recordatorio normalmente. fecha_cita se guarda en hora local de Bogota.
UPDATE solicitudes
SET recordatorio_dia_antes_enviado = TRUE
WHERE fecha_cita IS NULL
   OR fecha_cita < (now() AT TIME ZONE 'America/Bogota') + interval '24 hours';
