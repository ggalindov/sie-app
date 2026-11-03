-- "No podemos reducir nada... tiene que estar todo de manera ultra rigurosa" (pedido
-- explícito del usuario). Se verificó en vivo el 2026-10-31 contra el endpoint real de
-- actuaciones del CPNU (ver RamaJudicialCpnuClient) que la Rama Judicial SÍ devuelve, por
-- cada actuación, fechaInicial/fechaFinal (el término real que abre esa actuación, cuando
-- aplica) y consActuacion (el consecutivo asignado por el propio despacho) -- campos que
-- este sistema venía descartando por completo. fechaInicial/fechaFinal son un HECHO
-- informado directamente por el despacho, más autoritativo que la inferencia por palabras
-- clave que ya existía (ver InferenciaTerminoProcesal), así que se persisten aparte y se
-- prefieren sobre esa inferencia siempre que estén presentes.

ALTER TABLE actuaciones_judiciales
    ADD COLUMN consecutivo INTEGER,
    ADD COLUMN fecha_inicial_termino DATE,
    ADD COLUMN fecha_final_termino DATE;
