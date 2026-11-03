-- Sonda permanente de salud de los portales del Estado (2026-11-02). Hallazgo: Publicaciones
-- Procesales se degrada de noche (timeouts en rachas de ~15 min y una hora entera de descargas
-- abortadas), mientras el CPNU e internet responden normal en los mismos minutos. Para elegir
-- con datos la mejor hora del ciclo automatico (y dejar registro de cuando se caen los
-- portales), una medicion liviana cada 10 minutos de cada servicio mas un control de internet.
CREATE TABLE portal_salud (
    id           BIGSERIAL PRIMARY KEY,
    fecha        TIMESTAMPTZ NOT NULL DEFAULT now(),
    servicio     VARCHAR(20) NOT NULL,
    ok           BOOLEAN NOT NULL,
    http         INT,
    latencia_ms  INT,
    error        TEXT
);

CREATE INDEX idx_portal_salud_fecha ON portal_salud (fecha);
CREATE INDEX idx_portal_salud_servicio_fecha ON portal_salud (servicio, fecha);
