-- Migración V80: Soporte para Casos Privados y Cifrados de Gerencia (Bóveda Ultra Sensible)
-- Permite aislar completamente casos de alta confidencialidad para acceso exclusivo de ADMIN_GENERAL.

ALTER TABLE casos ADD COLUMN IF NOT EXISTS es_privado_cifrado BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE casos ADD COLUMN IF NOT EXISTS motivo_confidencialidad TEXT;

CREATE INDEX IF NOT EXISTS idx_casos_es_privado_cifrado ON casos(es_privado_cifrado);
