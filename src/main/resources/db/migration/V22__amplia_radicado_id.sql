-- La columna radicado_id venía de renombrar codigo_unico (V21), que se quedó con su ancho
-- original de VARCHAR(20) -- suficiente para nuestro código generado (SIE-2026-XXXXXX, 15
-- caracteres) pero NO para un radicado judicial real: el "radicado único de proceso" de la
-- Rama Judicial colombiana son 23 dígitos, y algunos despachos agregan guiones o sufijos.
-- Bug real detectado en vivo: crear un caso con un radicado real fallaba con "value too long
-- for type character varying(20)". 50 caracteres da margen de sobra (coincide con el límite
-- ya validado en CrearCasoRequest.radicadoId).
ALTER TABLE casos
    ALTER COLUMN radicado_id TYPE VARCHAR(50);
