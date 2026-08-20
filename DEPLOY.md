# Despliegue a producción (VPS)

Guía paso a paso para llevar SIE Jurídicos a un VPS nuevo. Todo el stack (Postgres,
backend, frontend, HTTPS) corre con Docker Compose; no hay que instalar Java, Node ni
Postgres directamente en el VPS.

## 1. Requisitos del VPS

- Un VPS Linux (Ubuntu 22.04/24.04 recomendado). Con 1 vCPU / 1-2 GB de RAM alcanza
  cómodo para el tráfico esperado de este sitio; los límites de memoria de cada
  contenedor ya están ajustados para eso (ver `.env.prod.example`).
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
cp .env.prod.example .env.prod
```

Edita `.env.prod` y completa **todos** los valores (dominio, contraseñas, JWT_SECRET,
credenciales SMTP, API key de Anthropic). Cada variable está documentada en el propio
archivo. No subas `.env.prod` a git — ya está en `.gitignore`.

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
`healthy` o `running`. Si alguno falla:

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
