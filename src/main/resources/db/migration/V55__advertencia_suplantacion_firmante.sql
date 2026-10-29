-- Punto 2 de las mejoras pedidas sobre el motor "hermano" (UCN): aceptación EXPLÍCITA
-- (no solo texto leído) de la advertencia legal de suplantación de identidad, una vez
-- antes de la foto del documento de identidad y otra antes de la selfie de verificación
-- humana -- son dos pasos distintos del wizard, cada uno con su propia aceptación.
ALTER TABLE documento_firmantes
    ADD COLUMN advertencia_suplantacion_identidad_aceptada_en TIMESTAMP,
    ADD COLUMN advertencia_suplantacion_verificacion_aceptada_en TIMESTAMP;
