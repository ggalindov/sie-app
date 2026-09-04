-- Cifra a nivel de aplicación (AES-256-GCM, ver CifradoService/CampoCifradoConverter) los
-- datos de identificación de clientes y las notas internas de casos: protege esos datos
-- aunque alguien obtenga acceso directo a la base de datos (volcado, copia de seguridad
-- robada, consulta SQL que se salte la aplicación), sin depender de si el disco del
-- servidor está o no cifrado.
--
-- IMPORTANTE — limpia los datos de prueba existentes: los valores en texto plano que ya
-- están guardados (de las pruebas locales de esta sesión) ya NO son compatibles con las
-- columnas cifradas -- la aplicación fallaría al intentar descifrarlos como si fueran
-- ciphertext real. Este proyecto todavía no está en producción (confirmado: solo hay datos
-- de prueba locales), así que no hace falta un script de re-cifrado en caliente; basta con
-- limpiar y volver a crear los casos de prueba que se necesiten.
TRUNCATE TABLE casos, clientes RESTART IDENTITY CASCADE;

-- TEXT (no VARCHAR con un límite fijo): el texto cifrado en Base64 (IV + ciphertext + tag)
-- siempre es más largo que el texto plano original, y su longitud exacta varía.
ALTER TABLE clientes
    ALTER COLUMN nombre TYPE TEXT,
    ALTER COLUMN correo TYPE TEXT,
    ALTER COLUMN telefono TYPE TEXT;

-- correo ya no puede ser el UNIQUE (queda cifrado con IV aleatorio: dos cifrados del mismo
-- correo dan bytes distintos, "correo = ?" ya no encuentra nada). La unicidad pasa a
-- correo_hash: un índice ciego (HMAC-SHA256, determinista y no reversible) que sí se puede
-- comparar por igualdad en SQL.
ALTER TABLE clientes
    DROP CONSTRAINT uq_clientes_correo;

ALTER TABLE clientes
    ADD COLUMN correo_hash VARCHAR(64) NOT NULL,
    ADD CONSTRAINT uq_clientes_correo_hash UNIQUE (correo_hash);

ALTER TABLE casos
    ALTER COLUMN notas_internas TYPE TEXT;
