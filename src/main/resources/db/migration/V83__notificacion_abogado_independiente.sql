-- Bug real encontrado (2026-11-05): BoletinJudicialDigestService.enviarDigestAbogados()
-- filtraba y marcaba la MISMA bandera (notificada_staff) que enviarDigestGerente(). Como el
-- digest de gerencia se envia primero y marca notificada_staff=true en TODAS las actuaciones
-- pendientes del sistema (no solo las suyas), para cuando corria el digest de abogados la
-- lista de pendientes ya estaba vacia -- ningun abogado recibia nunca movimientos reales en
-- su reporte diario. Ver ActuacionJudicialRegistrada, ActuacionJudicialRegistradaRepository.
-- listarPendientesNotificarPorAbogado y BoletinJudicialDigestService.enviarDigestAbogados.

ALTER TABLE actuaciones_judiciales ADD COLUMN notificada_abogado BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE actuaciones_judiciales ADD COLUMN fecha_notificada_abogado TIMESTAMP;

-- El backfill de arriba (DEFAULT TRUE) es a proposito: trata todo el historial ya existente
-- como "ya notificado a abogados", para que el primer digest despues de este fix no inunde a
-- cada abogado con meses de atraso -- pedido explicito del usuario: "a partir de hoy el
-- sistema ya debe de quedar perfecto... manana solo enviara los movimientos nuevos". A partir
-- de aqui, el DEFAULT cambia a FALSE para que cualquier actuacion nueva (insertada por Java,
-- que de todas formas ya pone notificadaAbogado=false explicitamente) empiece pendiente.
ALTER TABLE actuaciones_judiciales ALTER COLUMN notificada_abogado SET DEFAULT FALSE;
