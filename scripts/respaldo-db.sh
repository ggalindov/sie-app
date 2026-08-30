#!/usr/bin/env bash
# Respaldo diario automático de la base de datos de producción. Pensado para correr
# vía cron en el VPS (ver instalación abajo), no a mano.
#
# Guarda en /root/respaldos-db/ con rotación: conserva los últimos N días y borra el
# resto, para que el disco del VPS no crezca sin límite. Los respaldos SOLO viven en
# este mismo VPS -- si el VPS se pierde por completo (disco dañado, cuenta suspendida),
# los respaldos se pierden con él. Para protección real ante ese escenario, hay que
# subirlos también a almacenamiento externo (S3, Backblaze B2, etc.), lo cual requiere
# credenciales de esos servicios que este script no tiene -- ver DEPLOY.md.
#
# Instalación (una sola vez, en el VPS):
#   chmod +x scripts/respaldo-db.sh
#   crontab -e
#   # agregar esta línea (respaldo diario a las 3:00 am hora del servidor):
#   0 3 * * * /root/sie-juridicos/scripts/respaldo-db.sh >> /root/respaldos-db/respaldo.log 2>&1

set -euo pipefail
cd "$(dirname "$0")/.."

DIAS_A_CONSERVAR=7
DIR_RESPALDOS=/root/respaldos-db
FECHA=$(date +%F-%H%M)

mkdir -p "$DIR_RESPALDOS"

if [ ! -f .env.prod ]; then
    echo "$(date -Iseconds) ERROR: no se encontró .env.prod, abortando respaldo" >&2
    exit 1
fi

# shellcheck disable=SC1091
DB_USER=$(grep -E '^DB_USER=' .env.prod | cut -d= -f2-)
DB_NAME=$(grep -E '^DB_NAME=' .env.prod | cut -d= -f2-)

ARCHIVO="$DIR_RESPALDOS/sie-juridicos-${FECHA}.sql.gz"

if docker compose -f docker-compose.prod.yml exec -T postgres \
    pg_dump -U "$DB_USER" "$DB_NAME" | gzip > "$ARCHIVO"; then
    echo "$(date -Iseconds) OK: respaldo creado en $ARCHIVO ($(du -h "$ARCHIVO" | cut -f1))"
else
    echo "$(date -Iseconds) ERROR: pg_dump falló, revisa que el contenedor postgres esté sano" >&2
    rm -f "$ARCHIVO"
    exit 1
fi

# Rotación: borra respaldos con más de DIAS_A_CONSERVAR días.
find "$DIR_RESPALDOS" -name "sie-juridicos-*.sql.gz" -mtime "+${DIAS_A_CONSERVAR}" -delete
echo "$(date -Iseconds) Respaldos actuales: $(find "$DIR_RESPALDOS" -name 'sie-juridicos-*.sql.gz' | wc -l) archivo(s), conservando últimos ${DIAS_A_CONSERVAR} días"
