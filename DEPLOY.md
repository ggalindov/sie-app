# Despliegue a producción (VPS)

Guía paso a paso para llevar SIE Jurídicos a un VPS nuevo. Todo el stack (Postgres,
backend, frontend, HTTPS) corre con Docker Compose; no hay que instalar Java, Node ni
Postgres directamente en el VPS.

**Estado verificado**: `docker compose -f docker-compose.prod.yml build` (backend y
frontend) y `up -d` de `postgres` + `backend` + `frontend` se probaron de verdad en
local (no solo revisados en papel) — Postgres queda `healthy`, el backend arranca con
el perfil `prod`, corre Flyway, crea el `ADMIN_GENERAL` inicial y responde en
`/api/salud` y `/api/categorias` con datos reales; el frontend sirve `200` en `/`. Lo
único que no se puede probar sin un dominio real apuntando por DNS es Caddy emitiendo
el certificado HTTPS de Let's Encrypt (paso 3-4 más abajo) — esa parte queda pendiente
de confirmar en el VPS real, el resto del stack ya está probado end-to-end.

## 1. Requisitos del VPS

- Un VPS Linux (Ubuntu 22.04/24.04 recomendado), **2 vCPU / 2 GB de RAM como mínimo
  real**. Los `mem_limit` de los 4 contenedores (Postgres 384m + backend 640m +
  frontend 256m + Caddy 128m ≈ 1.4 GB) ya son un piso, no cuentan el sistema operativo
  ni el propio daemon de Docker (~250-400m adicionales), y el primer `--build` corre
  `npm run build` (Turbopack) y `mvnw package` **sin** ningún límite de memoria propio —
  cada uno puede necesitar 1 GB+ libre solo para el build. Si el VPS es de 1 GB,
  aprovisiona swap ANTES del primer build (`fallocate -l 2G /swapfile && chmod 600
  /swapfile && mkswap /swapfile && swapon /swapfile`, y agrégalo a `/etc/fstab` para que
  persista tras reiniciar) o el build puede morir por falta de memoria sin un error claro.
- [Docker](https://docs.docker.com/engine/install/) y el plugin `docker compose`
  instalados (`docker compose version` debe funcionar).
- Un dominio (o subdominio) con un registro DNS **A** apuntando a la IP pública del VPS.
  Caddy necesita esto para emitir el certificado HTTPS automáticamente — sin DNS
  apuntando ya al VPS, la emisión del certificado falla.
- Puertos **80** y **443** abiertos en el firewall del VPS (necesarios para HTTPS/Let's
  Encrypt). El resto de puertos (Postgres 5432, backend 8080, frontend 3000) **no** se
  exponen a Internet: solo Caddy es público, todo lo demás vive en la red interna de
  Docker.

## 2. Preparar el servidor

```bash
git clone <url-del-repositorio> sie-juridicos
cd sie-juridicos
```

Genera `.env.prod` de una de estas dos formas:

- **Recomendado**: `bash scripts/generar-env-prod.sh` — genera `DB_PASSWORD`,
  `JWT_SECRET` y `ADMIN_BOOTSTRAP_PASSWORD` como secretos aleatorios reales (nunca un
  valor de ejemplo que alguien tenga que acordarse de reemplazar), pide de forma
  interactiva los datos que sí requieren una cuenta real (dominio, SMTP, Anthropic) y
  deja el archivo con `chmod 600` desde el inicio.
- **Manual**: `cp .env.prod.example .env.prod` y edítalo a mano completando **todos**
  los valores (dominio, contraseñas, JWT_SECRET, credenciales SMTP, API key de
  Anthropic). Cada variable está documentada en el propio archivo.

En cualquiera de los dos casos, no subas `.env.prod` a git — ya está en `.gitignore`.

Restringe los permisos del archivo (queda con contraseñas y llaves en texto plano):

```bash
chmod 600 .env.prod
```

Antes de levantar el stack, confirma que no quedó ningún valor de ejemplo sin
reemplazar (si este comando imprime algo, ese valor real es el que ya está publicado en
el propio repositorio de ejemplo — no lo dejes así):

```bash
grep -n "cambia-esto\|cambiar-este" .env.prod
```

## 3. Levantar el stack

```bash
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

La primera vez tarda varios minutos (construye las imágenes de backend y frontend desde
cero). Al terminar:

- Postgres arranca y espera a estar realmente listo (`healthcheck`) antes de que el
  backend intente conectarse.
- El backend corre las migraciones de Flyway automáticamente al arrancar (igual que en
  desarrollo) y crea el primer `ADMIN_GENERAL` con las credenciales de `.env.prod`.
- Caddy pide el certificado HTTPS a Let's Encrypt para tu dominio y queda sirviendo en
  `https://tudominio.com`.

## 4. Verificar

```bash
docker compose -f docker-compose.prod.yml ps
```

Los cuatro servicios (`postgres`, `backend`, `frontend`, `caddy`) deben aparecer como
`healthy` o `running`. Todos tienen `restart: unless-stopped`: si uno falla al arrancar
(una migración rota, una variable mal puesta), Docker lo va a reiniciar en bucle sin
avisar a nadie — el estado en `ps` para ese contenedor va a leer `Restarting`. Vale la
pena correr este comando manualmente después de cada despliegue, o apuntar un monitor
externo de uptime a `https://tudominio.com/api/salud`. Si alguno falla:

```bash
docker compose -f docker-compose.prod.yml logs -f backend   # o frontend / caddy / postgres
```

Abre `https://tudominio.com` en el navegador: debe cargar el sitio público. Entra a
`https://tudominio.com/admin/login` con el correo/contraseña de `ADMIN_BOOTSTRAP_*` para
confirmar que el backend y la base de datos responden de verdad.

## 5. Actualizar a una versión nueva

```bash
git pull
docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
```

Reconstruye solo lo que cambió y reinicia esos contenedores; Postgres y sus datos no se
tocan (viven en un volumen con nombre, `sie_juridicos_data`, que persiste entre
despliegues).

## 6. Respaldo de la base de datos

```bash
docker compose -f docker-compose.prod.yml exec postgres \
  pg_dump -U <DB_USER> <DB_NAME> > respaldo-$(date +%F).sql
```

Considera automatizar esto con un cron en el VPS que corra ese comando y suba el
resultado a almacenamiento externo (no dejar los respaldos únicamente en el mismo VPS).

## 7. Administrar la base de datos manualmente (opcional)

No hay pgAdmin en producción a propósito (correrlo 24/7 solo gasta RAM). Si necesitas
entrar puntualmente:

```bash
docker compose -f docker-compose.prod.yml exec postgres psql -U <DB_USER> -d <DB_NAME>
```

O, para usar un cliente gráfico desde tu computador, abre un túnel SSH puntual:

```bash
ssh -L 5433:localhost:5432 usuario@tu-vps
# y conecta tu cliente de Postgres a localhost:5433
```

(Esto solo funciona si expones Postgres en el host — por defecto no está expuesto ni
siquiera en localhost del VPS en `docker-compose.prod.yml`; si lo necesitas seguido,
agrega `ports: ["127.0.0.1:5432:5432"]` al servicio `postgres` del compose de
producción.)

## Qué hace cada pieza (por si algo falla)

| Componente | Rol |
|---|---|
| `Dockerfile` (raíz) | Imagen del backend: build con Maven, runtime con solo el JRE (no el JDK completo) |
| `frontend/Dockerfile` | Imagen del frontend: build de Next.js, runtime con `output: standalone` (sin `node_modules` completo) |
| `docker-compose.prod.yml` | Orquesta los 4 servicios, red interna fija, límites de memoria, rotación de logs |
| `Caddyfile` | Proxy reverso + HTTPS automático: `/api/*` → backend, todo lo demás → frontend |
| `application-prod.properties` | Ajustes de producción del backend (logging, proxies de confianza, sin el módulo de docker-compose de desarrollo) |
| `.env.prod` | Todas las credenciales y configuración específica de este despliegue (nunca en git) |
