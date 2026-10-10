-- Bug real corregido en esta auditoría: la tarea (V45) solo podía ligarse a un Caso, pero el
-- 96% de los clientes del directorio del CRM todavía no tiene ningún caso judicial vinculado
-- (son prospectos o clientes en gestión temprana) -- para ellos el botón "Tareas" de la ficha
-- 360 simplemente no tenía dónde engancharse y nunca aparecía. Pedido explícito del usuario:
-- "arregla desde el directorio de clientes en el CRM ahi es donde se asginara las tareas por
-- cada persona".
--
-- caso_id pasa a ser opcional, y se agrega cliente_crm_id (también opcional) para que una tarea
-- pueda ligarse directamente a la persona en el directorio del CRM sin necesitar un caso
-- judicial todavía. El CHECK garantiza que toda tarea quede ligada a algo real (nunca huérfana
-- por completo).
ALTER TABLE tareas ALTER COLUMN caso_id DROP NOT NULL;
ALTER TABLE tareas ADD COLUMN cliente_crm_id BIGINT REFERENCES crm_clientes(id) ON DELETE CASCADE;
ALTER TABLE tareas ADD CONSTRAINT chk_tareas_caso_o_cliente CHECK (caso_id IS NOT NULL OR cliente_crm_id IS NOT NULL);

CREATE INDEX ix_tareas_cliente_crm ON tareas (cliente_crm_id);
