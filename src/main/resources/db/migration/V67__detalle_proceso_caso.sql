-- Pedido explícito del usuario: "tienes que traer absolutamente toda la informacion... no
-- excluyas nada de lo que de el estado, entre mas datos mucho mejor". El CPNU da mucho más
-- de lo que V66 capturó: departamento, fecha de radicación, si el proceso es privado, y un
-- endpoint APARTE (Proceso/Detalle/{idProceso}) con el ponente (juez), tipo/clase/subclase de
-- proceso, el recurso y la ubicación actual del expediente dentro del despacho. Ver
-- RamaJudicialCpnuClient.buscarProceso().
ALTER TABLE casos
    ADD COLUMN departamento_judicial VARCHAR(100),
    ADD COLUMN fecha_radicacion_proceso DATE,
    ADD COLUMN proceso_privado BOOLEAN,
    ADD COLUMN ponente VARCHAR(255),
    ADD COLUMN tipo_proceso VARCHAR(100),
    ADD COLUMN clase_proceso VARCHAR(100),
    ADD COLUMN subclase_proceso VARCHAR(150),
    ADD COLUMN recurso_proceso VARCHAR(100),
    ADD COLUMN ubicacion_proceso VARCHAR(150);
