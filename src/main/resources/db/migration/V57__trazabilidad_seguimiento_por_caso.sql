-- Trazabilidad INDEPENDIENTE por caso del seguimiento judicial (ver
-- sie.siejuridicos.vigilancia.SeguimientoJudicialService): hasta ahora solo quedaba un
-- resumen agregado de toda la corrida en el Registro del Sistema (ver
-- TipoRegistroSistema.SEGUIMIENTO_JUDICIAL) -- no había forma de saber, para UN caso
-- puntual, cuándo fue la última vez que se revisó y si esa revisión salió bien. Pedido
-- explícito del usuario: "tiene que haber una trazabilidad independiente por caso".
ALTER TABLE casos
    ADD COLUMN fecha_ultimo_seguimiento TIMESTAMP,
    ADD COLUMN ultimo_seguimiento_exitoso BOOLEAN;
