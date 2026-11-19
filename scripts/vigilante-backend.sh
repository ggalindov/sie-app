#!/bin/bash
# Vigilante del backend (caida de produccion 2026-11-19: la JVM quedo viva pero sin atender
# HTTP durante horas y Docker no la reinicio porque el proceso nunca termino).
# Corre cada minuto desde cron. Si /api/salud falla 3 veces seguidas, reinicia el backend.
#
# Instalacion (una sola vez, en el VPS):
#   chmod +x /root/sie-juridicos/scripts/vigilante-backend.sh
#   (crontab -l 2>/dev/null; echo '* * * * * /root/sie-juridicos/scripts/vigilante-backend.sh >> /var/log/vigilante-backend.log 2>&1') | crontab -

CONTENEDOR="sie-juridicos-prod-backend-1"
ESTADO="/tmp/vigilante-backend.fallos"
MAX_FALLOS=3

# No intervenir si el contenedor esta arrancando (la primera comprobacion tarda ~40 s).
INICIO=$(docker inspect -f '{{.State.StartedAt}}' "$CONTENEDOR" 2>/dev/null)
if [ -n "$INICIO" ]; then
  SEGUNDOS=$(( $(date +%s) - $(date -d "$INICIO" +%s) ))
  if [ "$SEGUNDOS" -lt 120 ]; then exit 0; fi
fi

if docker exec "$CONTENEDOR" wget -q -T 5 -O /dev/null http://127.0.0.1:8080/api/salud; then
  rm -f "$ESTADO"
  exit 0
fi

FALLOS=$(( $(cat "$ESTADO" 2>/dev/null || echo 0) + 1 ))
echo "$FALLOS" > "$ESTADO"
echo "$(date -Is) /api/salud no responde ($FALLOS/$MAX_FALLOS)"

if [ "$FALLOS" -ge "$MAX_FALLOS" ]; then
  echo "$(date -Is) REINICIANDO $CONTENEDOR"
  docker restart "$CONTENEDOR"
  rm -f "$ESTADO"
fi
