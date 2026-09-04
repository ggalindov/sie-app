-- Reestructuración: el admin ya no tiene que cargar cada caso a mano. El sistema ahora
-- puede sincronizar automáticamente TODOS los casos existentes desde el Google Sheets de
-- la firma (columna B = "NO.", el número de caso interno de la firma -- ver
-- HojaCalculoService.listarParaSincronizar()). Ese número es la llave que usa la
-- sincronización para saber "¿esta fila ya existe en nuestro sistema o es nueva?", por eso
-- es distinto e independiente del radicado judicial (columna I).
--
-- numero_caso es NULLABLE a propósito: los casos creados a mano desde el panel (que se
-- mantiene como respaldo, ver CrearCasoRequest) no tienen un número de la hoja. UNIQUE
-- permite múltiples NULL en Postgres sin conflicto (no es lo mismo NULL que "").
ALTER TABLE casos
    ADD COLUMN numero_caso VARCHAR(20),
    ADD CONSTRAINT uq_casos_numero_caso UNIQUE (numero_caso);

-- radicado_id deja de ser obligatorio: con la sincronización, un caso puede existir (ya
-- tiene número de caso y cliente) antes de que el despacho judicial le asigne un radicado
-- real -- ese dato puede llegar días o semanas después, en una sincronización posterior.
ALTER TABLE casos
    ALTER COLUMN radicado_id DROP NOT NULL;

-- Reemplaza el criterio anterior de "¿ya se envió el correo?" (antes: se enviaba una sola
-- vez, en el momento de crear el caso a mano). Ahora un caso puede quedar sincronizado SIN
-- radicado todavía (correo_enviado se queda en false hasta que sí lo tenga), y el botón
-- "Enviar correos pendientes" del panel es el que de verdad dispara el envío cuando
-- corresponde -- ya no ocurre automáticamente al crear/sincronizar.
ALTER TABLE casos
    ADD COLUMN correo_enviado BOOLEAN NOT NULL DEFAULT false;

-- Los casos que ya existían (creados a mano antes de esta migración) ya recibieron su
-- correo en el momento de crearse (el flujo viejo lo enviaba de inmediato) -- se marcan
-- como enviados para que el nuevo botón de "pendientes" no se los vuelva a mandar.
UPDATE casos SET correo_enviado = true WHERE radicado_id IS NOT NULL;
