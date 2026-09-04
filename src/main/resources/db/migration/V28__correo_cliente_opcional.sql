-- Bug real encontrado con datos reales: en la hoja de Superintendencia (y en menor medida en
-- las otras dos) hay filas de casos reales -- con despacho, radicado, a veces hasta ya con el
-- correo del cliente -- que NUNCA llegaban a sincronizarse porque a esa fila puntual le
-- faltaba el correo capturado en la hoja (la firma aún no lo tiene). El sistema las trataba
-- como "fila incompleta, se omite" en vez de "caso real sin contacto capturado todavía" --
-- pedido explícito del usuario: "no se me puede perder ni faltar ninguno".
--
-- clientes.correo/correo_hash pasan a ser opcionales: un Cliente ahora puede representar un
-- sujeto procesal del que todavía no se tiene correo (se sincroniza igual, solo que sin poder
-- enviarle notificación hasta que la hoja se actualice con su contacto). La restricción UNIQUE
-- sobre correo_hash sigue intacta y sigue funcionando igual: en Postgres NULL nunca es igual a
-- otro NULL, así que múltiples clientes sin correo no chocan entre sí.
ALTER TABLE clientes ALTER COLUMN correo DROP NOT NULL;
ALTER TABLE clientes ALTER COLUMN correo_hash DROP NOT NULL;
