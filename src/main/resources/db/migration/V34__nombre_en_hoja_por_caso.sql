-- Bug real reportado por el usuario: el panel mostraba nombres de clientes viejos o
-- equivocados. La causa: el nombre se leía de clientes.nombre, compartido entre TODOS los
-- casos de un mismo cliente (mismo correo), y CasoService nunca lo actualiza una vez que el
-- cliente tiene más de un caso (a propósito, para no pisarlo con una etiqueta corta
-- específica de otro caso -- ver el comentario de actualizarDatosCliente()). Esta columna
-- guarda el nombre TAL COMO aparece en la hoja para CADA fila/caso puntual, así que ya no
-- depende de cuántos casos comparta el cliente. Cifrada igual que notas_internas (dato
-- personal, ver CampoCifradoConverter): sin longitud fija, texto libre.
ALTER TABLE casos ADD COLUMN nombre_en_hoja TEXT;
