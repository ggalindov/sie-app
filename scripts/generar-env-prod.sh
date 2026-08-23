#!/usr/bin/env bash
# Genera .env.prod con secretos fuertes generados de verdad (JWT_SECRET, DB_PASSWORD,
# ADMIN_BOOTSTRAP_PASSWORD) en vez de dejarlos como valores de ejemplo que alguien
# tiene que acordarse de reemplazar a mano. Los datos que SÍ requieren una cuenta real
# (dominio, SMTP, Anthropic) se piden de forma interactiva. Pensado para correr UNA vez
# en el VPS, antes del primer "docker compose up" (ver DEPLOY.md).
#
# Uso: bash scripts/generar-env-prod.sh

set -euo pipefail
cd "$(dirname "$0")/.."

if [ -f .env.prod ]; then
    echo "Ya existe un .env.prod en este directorio. Bórralo o muévelo primero si quieres" >&2
    echo "generar uno nuevo — este script nunca sobreescribe uno existente, para no" >&2
    echo "perder credenciales de producción ya en uso por accidente." >&2
    exit 1
fi

if ! command -v openssl >/dev/null 2>&1; then
    echo "Este script necesita openssl (ya viene instalado en casi cualquier VPS Linux)." >&2
    exit 1
fi

pedir() {
    local etiqueta="$1" var_defecto="${2:-}"
    local valor
    read -r -p "$etiqueta${var_defecto:+ [$var_defecto]}: " valor
    echo "${valor:-$var_defecto}"
}

echo "=== Datos que necesitas tener a mano ==="
DOMAIN=$(pedir "Dominio (ya apuntando por DNS a este VPS, ej. siejuridicos.com)")
ADMIN_BOOTSTRAP_EMAIL=$(pedir "Correo del primer administrador" "admin@${DOMAIN}")
MAIL_USERNAME=$(pedir "Correo de Gmail para envíos (deja vacío para configurarlo después)")
MAIL_PASSWORD=$(pedir "Contraseña de aplicación de Gmail (myaccount.google.com/apppasswords)")
MAIL_FROM=$(pedir "Correo remitente" "${MAIL_USERNAME:-siejuridicos@gmail.com}")
MAIL_ADMIN=$(pedir "Correo que recibe las notificaciones internas" "${MAIL_USERNAME:-siejuridicos@gmail.com}")
ANTHROPIC_API_KEY=$(pedir "API key de Anthropic para el chatbot (deja vacío para configurarla después)")
FIRMA_WHATSAPP_URL=$(pedir "Enlace de WhatsApp (wa.me/... o wa.link/...)")

# Contraseñas/secretos generados de verdad, nunca placeholders que alguien tenga que
# acordarse de cambiar. 48 bytes en base64 da de sobra los 256 bits mínimos que exige
# JwtService para HS256 (y el chequeo de arranque en producción que rechaza el valor
# de ejemplo del repositorio si alguien llegara a copiarlo igual).
DB_PASSWORD=$(openssl rand -base64 24)
JWT_SECRET=$(openssl rand -base64 48)
ADMIN_BOOTSTRAP_PASSWORD=$(openssl rand -base64 18)

cat > .env.prod << EOF
DOMAIN=${DOMAIN}

DB_NAME=sie_juridicos
DB_USER=sie_user
DB_PASSWORD=${DB_PASSWORD}

JWT_SECRET=${JWT_SECRET}

ADMIN_BOOTSTRAP_EMAIL=${ADMIN_BOOTSTRAP_EMAIL}
ADMIN_BOOTSTRAP_PASSWORD=${ADMIN_BOOTSTRAP_PASSWORD}

MAIL_USERNAME=${MAIL_USERNAME}
MAIL_PASSWORD=${MAIL_PASSWORD}
MAIL_FROM=${MAIL_FROM}
MAIL_ADMIN=${MAIL_ADMIN}

ANTHROPIC_API_KEY=${ANTHROPIC_API_KEY}

FIRMA_WHATSAPP_URL=${FIRMA_WHATSAPP_URL}
EOF

chmod 600 .env.prod

echo
echo "=== .env.prod generado y protegido (chmod 600) ==="
echo "Contraseña del primer administrador (${ADMIN_BOOTSTRAP_EMAIL}): ${ADMIN_BOOTSTRAP_PASSWORD}"
echo "Guárdala en un gestor de contraseñas ahora mismo — no vuelve a mostrarse."
echo
echo "Siguiente paso:"
echo "  docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build"
