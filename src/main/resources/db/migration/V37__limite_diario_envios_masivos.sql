-- Cupo diario COMPARTIDO entre casos, cobros y cualquier otro envío masivo (reporte semanal,
-- notificaciones de radicado, recordatorios de cobro) -- pedido explícito del usuario: la API
-- de WhatsApp Business tiene un límite real de la plataforma (Meta) de 250 mensajes salientes
-- por día. Se comparte entre TODOS los orígenes (no un cupo separado por módulo) porque el
-- límite es de la cuenta de WhatsApp completa, no de una funcionalidad puntual.
--
-- fn_reservar_cupo_envio_masivo(limite) intenta reservar UNA unidad de cupo para HOY de forma
-- atómica (una sola sentencia UPDATE con WHERE cantidad < limite, sin condición de carrera
-- posible): devuelve true si quedaba cupo (y ya lo reservó), false si el límite del día ya se
-- alcanzó. Cada llamador (CasoService, CobroService) reserva un cupo por CADA destinatario que
-- va a procesar, sin importar cuántos canales (correo y/o WhatsApp) le toquen -- así un cliente
-- nunca queda con un canal enviado hoy y el otro tres días después.
CREATE TABLE envios_masivos_contador_diario (
    fecha DATE PRIMARY KEY,
    cantidad INTEGER NOT NULL DEFAULT 0
);

CREATE OR REPLACE FUNCTION fn_reservar_cupo_envio_masivo(p_limite INTEGER)
    RETURNS BOOLEAN
    LANGUAGE plpgsql
AS
$$
DECLARE
    v_filas_actualizadas INTEGER;
BEGIN
    INSERT INTO envios_masivos_contador_diario (fecha, cantidad)
    VALUES (CURRENT_DATE, 0)
    ON CONFLICT (fecha) DO NOTHING;

    UPDATE envios_masivos_contador_diario
    SET cantidad = cantidad + 1
    WHERE fecha = CURRENT_DATE
      AND cantidad < p_limite;

    GET DIAGNOSTICS v_filas_actualizadas = ROW_COUNT;
    RETURN v_filas_actualizadas > 0;
END;
$$;

-- Reporte semanal de casos (ver CasoService.enviarReporteSemanal): antes reenviaba a TODOS los
-- casos con radicado en cada corrida, sin memoria de a quién ya se le mandó el reporte de ESTA
-- semana. Con el cupo diario, un lote grande puede tardar más de un día en completarse, así que
-- ahora se necesita saber quién ya recibió el reporte de la semana actual para no repetírselo
-- al día siguiente ni perder de vista a quien quedó pendiente por el límite.
ALTER TABLE casos ADD COLUMN fecha_ultimo_reporte_semanal TIMESTAMP;
