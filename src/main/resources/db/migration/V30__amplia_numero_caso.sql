-- Amplía numero_caso de VARCHAR(20) a VARCHAR(64): necesario para el nuevo esquema de
-- llave de sincronización de SUPERINTENDENCIA y PROCESOS_COMISARIA (ver
-- HojaCalculoService.huellaContenido()), que usa una huella de contenido (prefijo "h-" +
-- 16 caracteres hex) en vez del número de fila física. Con el sufijo de desambiguación
-- ("-2", "-3"...) que ahora se aplica a TODAS las fuentes, 20 caracteres se quedaba corto
-- para casos con varias colisiones de huella. ALTER COLUMN TYPE a un varchar más ancho es
-- solo un cambio de metadato en Postgres (no reescribe la tabla), operación segura sobre
-- una tabla ya en producción.
ALTER TABLE casos
    ALTER COLUMN numero_caso TYPE VARCHAR(64);
