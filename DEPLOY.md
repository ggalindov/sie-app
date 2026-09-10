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

### 2.1 Google Sheets de casos (consulta de estado en `/consulta-caso`)

Este módulo lee en vivo, **solo lectura**, el Google Sheets donde la firma lleva el
seguimiento real de sus casos (ver `HojaCalculoService`). Antes del primer arranque:

1. En [Google Cloud Console](https://console.cloud.google.com/), crea/selecciona un
   proyecto y habilita "Google Sheets API".
2. Crea una **Service Account** (IAM y administración → Cuentas de servicio), sin roles
   especiales de proyecto.
3. Genera una llave JSON para esa cuenta y descárgala.
4. Colócala en el VPS en `./secrets/google-sheets-service-account.json` (misma carpeta que
   `docker-compose.prod.yml`) y restringe sus permisos:
   ```bash
   mkdir -p secrets
   chmod 600 secrets/google-sheets-service-account.json
   ```
   Esta ruta ya está en `.gitignore` — nunca debe subirse a git. `docker-compose.prod.yml`
   la monta como **secreto de Docker Compose** (no como variable de entorno) dentro del
   contenedor del backend, de solo lectura.
5. Abre la hoja real, "Compartir" → agrega el `client_email` de esa llave JSON como
   **Lector** (nunca Editor, nunca "cualquiera con el enlace" — la hoja sigue siendo
   privada, solo esa cuenta de servicio puede leerla).
6. Pon el ID de la hoja (el segmento largo de su URL, entre `/d/` y `/edit`) en
   `GOOGLE_SHEETS_ID` dentro de `.env.prod` (el script de la sección 2 ya lo pide).

Sin este setup, el sitio funciona igual (crear casos desde el panel sigue andando), pero
`/consulta-caso` responde "servicio no disponible" en vez de mostrar el estado real.

### 2.2 WhatsApp de la línea de atención (notificación del radicado)

Este módulo (`WhatsAppService`) manda, además del correo, el mismo aviso del número de
radicado por WhatsApp, usando la **API oficial de Meta (WhatsApp Cloud API)** — no la app
normal de WhatsApp Business, que no tiene forma de conectarse por código. Sin este setup, el
sistema sigue notificando con normalidad, solo que únicamente por correo.

**Diferencia clave con el correo**: WhatsApp NO permite mandar texto libre cuando el negocio
inicia la conversación primero (como avisarle a un cliente su radicado sin que él haya
escrito antes) — es una regla dura de la plataforma, no una limitación de este proyecto.
Hay que redactar una **plantilla de mensaje** y mandarla a aprobación de Meta una sola vez;
después de aprobada, el sistema solo rellena sus variables (nombre, radicado, enlace) en
cada envío.

1. Crea (o usa) una cuenta en [Meta Business Manager](https://business.facebook.com/) para
   la firma.
2. Dentro de ella, activa **WhatsApp** → "Empezar a usar la API de WhatsApp Business"
   (WhatsApp Cloud API). Sigue el asistente para verificar el número de la línea de
   atención de SIE como número de negocio (requiere un código de verificación por SMS o
   llamada al número real).
   - Si ese número ya está en uso activo en la app normal de WhatsApp Business, hay que
     migrarlo formalmente a la Cloud API desde el mismo asistente (Meta lo guía paso a
     paso); una vez migrado, deja de poder usarse desde la app de celular para lo que
     administre la API.
3. En **WhatsApp → Configuración de la API**, copia:
   - El **Phone Number ID** (identificador interno del número, no el número en sí) →
     `WHATSAPP_PHONE_NUMBER_ID`.
   - Un **token de acceso permanente**: genera un "System User" en Business Settings →
     Usuarios del sistema, asígnale el activo de WhatsApp con permiso `whatsapp_business_messaging`,
     y genera su token sin fecha de expiración desde ahí (el token temporal de 24h que
     muestra el asistente inicial NO sirve para producción) → `WHATSAPP_ACCESS_TOKEN`.
4. En **Administrador de WhatsApp → Plantillas de mensajes**, crea una nueva plantilla:
   - Categoría: **Utilidad** (Utility) — es una notificación transaccional de un trámite
     que el cliente ya inició, no publicidad.
   - Nombre: `notificacion_radicado` (o el que prefieras, pero debe coincidir EXACTO con
     `WHATSAPP_TEMPLATE_NAME`).
   - Idioma: Español.
   - Cuerpo sugerido (con las 3 variables que el sistema ya rellena, en este orden: nombre,
     radicado, enlace):
     ```
     Hola {{1}}, en SIE Jurídicos registramos tu caso. Tu número de radicado es: {{2}}.
     Consulta el estado de tu proceso en cualquier momento aquí: {{3}}
     ```
   - Envíala a aprobación. Meta suele responder en minutos a un par de días para plantillas
     de categoría Utilidad.
5. Con la plantilla ya **aprobada**, completa en `.env.prod`: `WHATSAPP_ACCESS_TOKEN`,
   `WHATSAPP_PHONE_NUMBER_ID`, y si usaste un nombre distinto de plantilla,
   `WHATSAPP_TEMPLATE_NAME`.

Mientras la plantilla esté en revisión (o si `WHATSAPP_ACCESS_TOKEN`/`WHATSAPP_PHONE_NUMBER_ID`
quedan vacíos), el sistema sigue notificando solo por correo sin ningún error visible para
el cliente ni para el admin — el botón "Enviar notificaciones pendientes" del panel deja
esos casos con WhatsApp pendiente hasta que se configure.

### 2.3 Cobros Pendientes (recordatorio mensual de pago)

Este módulo (`cobro/`) sincroniza los clientes activos desde un Google Sheets **distinto**
al de casos (dos pestañas: Empresas y Personas Naturales) y les recuerda, el día 1 de cada
mes, el pago pendiente por correo y por WhatsApp — con dos botones de respuesta rápida
(Sí/No) para que confirmen el pago directamente desde WhatsApp.

1. **La hoja**: usa la MISMA cuenta de servicio de la sección 2.1
   (`secrets/google-sheets-service-account.json`), pero esta vez la hoja hay que compartirla
   con permiso de **Editor** (no Lector) — es la única escritura real que hace todo el
   sistema: marcar la columna "RESPONDIO MENSAJE" cuando el cliente contesta. Pon el ID de
   esta hoja (distinto al de casos) en `GOOGLE_SHEETS_COBROS_ID`.
2. **La plantilla de WhatsApp**: en el mismo Administrador de plantillas de Meta (ver 2.2),
   crea una plantilla NUEVA y aparte de `notificacion_radicado`:
   - Categoría: **Utilidad**.
   - Nombre: `recordatorio_cobro` (o el que prefieras, debe coincidir EXACTO con
     `WHATSAPP_TEMPLATE_COBRO_NAME`).
   - Idioma: Español.
   - Cuerpo sugerido (2 variables: nombre, monto):
     ```
     Hola {{1}}, te recordamos el valor pendiente de tus honorarios con SIE Jurídicos este
     mes: {{2}}. Seguimos trabajando activamente en tu proceso. ¿Confirmas el pago?

     Si ya realizaste el pago, por favor haz caso omiso a este mensaje.
     ```
   - Pie de página (opcional en Meta): `Si ya realizaste el pago, haz caso omiso.`
   - **Botones**: agrega dos botones de tipo "Respuesta rápida" (Quick Reply): uno con texto
     `Sí` y otro con texto `No`. El sistema identifica la respuesta por el texto del botón
     que el cliente presiona, no hace falta configurar nada más de tu lado para eso.
   - Envíala a aprobación.
3. **El webhook** (recibe la respuesta del cliente al botón): en tu app de Meta (la misma
   usada para WhatsApp Cloud API), ve a **Configuración de la app → Básica** y copia el
   **App Secret** → `WHATSAPP_APP_SECRET`. Luego en **WhatsApp → Configuración → Webhook**:
   - URL de retorno de llamada: `https://<tu-dominio>/api/whatsapp/webhook`.
   - Verify token: el mismo valor que `WHATSAPP_WEBHOOK_VERIFY_TOKEN` en tu `.env.prod`
     (si usaste `scripts/generar-env-prod.sh`, ya se generó uno real por ti — cópialo del
     archivo con `grep WHATSAPP_WEBHOOK_VERIFY_TOKEN .env.prod`).
   - Suscríbete al campo **messages**.
4. Con la plantilla ya **aprobada** y el webhook verificado, completa en `.env.prod`:
   `GOOGLE_SHEETS_COBROS_ID`, `WHATSAPP_TEMPLATE_COBRO_NAME`, `WHATSAPP_WEBHOOK_VERIFY_TOKEN`,
   `WHATSAPP_APP_SECRET`.

Sin este setup, la sección "Cobros Pendientes" del panel responde "servicio no disponible" en
vez de listar clientes, y el recordatorio mensual simplemente no encuentra a quién notificar
— nada de esto afecta al resto del sistema (Casos, Solicitudes, Blog, etc. siguen igual).

### 2.4 Aviso interno de nueva solicitud por WhatsApp

Apenas alguien llena el formulario público de contacto, el sistema le manda un WhatsApp con
el resumen completo (nombre, correo, teléfono, mensaje) a la línea interna de la firma
(`WHATSAPP_ADMIN_NUMERO`, por defecto `+57 312 4781583`) — además del correo de notificación
al admin que ya existía. Es una plantilla NUEVA y aparte de `notificacion_radicado` y
`recordatorio_cobro`.

1. En el mismo Administrador de plantillas de Meta (ver 2.2), crea una plantilla:
   - Categoría: **Utilidad** — es una notificación interna transaccional, no publicidad.
   - Nombre: `nueva_solicitud` (o el que prefieras, debe coincidir EXACTO con
     `WHATSAPP_TEMPLATE_SOLICITUD_NAME`).
   - Idioma: Español.
   - Cuerpo sugerido (4 variables, en este orden: nombre, correo, teléfono, mensaje):
     ```
     Nueva solicitud en el sitio web:
     Nombre: {{1}}
     Correo: {{2}}
     Teléfono: {{3}}
     Mensaje: {{4}}
     ```
   - Envíala a aprobación.
2. Con la plantilla ya **aprobada**, completa en `.env.prod`: `WHATSAPP_TEMPLATE_SOLICITUD_NAME`
   y, si la línea de atención cambia, `WHATSAPP_ADMIN_NUMERO` (formato internacional, con o
   sin `+`).

Mientras la plantilla esté en revisión, el aviso simplemente no se envía por WhatsApp (sin
error visible para el visitante ni para el admin) — el correo de notificación al admin sigue
llegando con normalidad.

### 2.5 Confirmación de reunión agendada (calendario)

Cuando un abogado o el admin agenda una reunión desde el Calendario del panel
(`/admin/calendario` o el botón "Agendar reunión" de Solicitudes), el sistema le confirma al
cliente la fecha, hora y cómo acceder (link de Meet/Zoom si es virtual, dirección si es
presencial) por correo y, si el teléfono es un celular colombiano válido, también por
WhatsApp. Es una plantilla NUEVA y aparte de las tres anteriores.

1. En el mismo Administrador de plantillas de Meta (ver 2.2), crea una plantilla:
   - Categoría: **Utilidad** — confirma un trámite que el cliente ya solicitó, no publicidad.
   - Nombre: `confirmacion_cita` (o el que prefieras, debe coincidir EXACTO con
     `WHATSAPP_TEMPLATE_CITA_NAME`).
   - Idioma: Español.
   - Cuerpo sugerido (3 variables, en este orden: nombre, fecha y hora, detalle de acceso —
     el mismo texto sirve para reuniones virtuales, donde la variable trae
     "Será virtual, únete aquí: <link>", y presenciales, donde trae "Será presencial, en:
     <dirección>" — siempre dice de entrada si es virtual o presencial, para que quede claro
     sin tener que interpretar si lo que sigue es un link o una dirección):
     ```
     Hola {{1}}, confirmamos tu reunión con SIE Jurídicos para el {{2}}
     {{3}}
     ```
   - Envíala a aprobación.
2. Con la plantilla ya **aprobada**, completa en `.env.prod`: `WHATSAPP_TEMPLATE_CITA_NAME`
   (solo si usaste un nombre distinto de plantilla).

Mientras la plantilla esté en revisión, la confirmación simplemente no se envía por WhatsApp
(sin error visible para el cliente ni para quien agenda) — el correo de confirmación, que no
depende de Meta, sigue llegando con normalidad con el mismo link de acceso.

### 2.6 Aviso de blog/noticia publicado

Cada vez que un artículo pasa a PUBLICADO (ver `ArticuloService.notificarPublicacion`), además
del correo interno a redes sociales y el boletín a suscriptores, se manda un WhatsApp a un
**único número fijo** (pedido explícito del usuario, `WHATSAPP_NUMERO_AVISO_BLOG`) avisando
que se publicó contenido nuevo, con el título y el link directo al artículo.

**A diferencia de las cuatro notificaciones anteriores, esta es de texto libre, no de
plantilla** — pedido explícito del usuario: como siempre va al mismo único destinatario
interno (nunca a un cliente distinto cada vez), no necesita pasar por aprobación de Meta.
No hay nada que crear en el Administrador de plantillas para este aviso.

Ojo con la limitación real de la propia API de WhatsApp (no de este código, no se puede
evitar con nada de configuración): un mensaje de texto libre iniciado por el negocio **solo
se entrega mientras exista una "ventana de servicio al cliente" abierta** con ese número —
las últimas 24 horas desde que esa persona le escribió algo al número de WhatsApp de la
firma. Si `WHATSAPP_NUMERO_AVISO_BLOG` no le ha escrito nada al número de la firma en ese
lapso, Meta rechaza el envío silenciosamente (mismo comportamiento con gracia del resto de
esta clase: sin error visible para nadie, el correo interno a redes sociales sigue llegando
con normalidad). Si se necesita que este aviso llegue siempre, sin depender de que alguien
mantenga la conversación activa, la solución sería aprobar una plantilla para este caso
también (mismo procedimiento de las secciones anteriores).

Completa en `.env.prod`: `WHATSAPP_NUMERO_AVISO_BLOG` (ya trae el número fijo pedido,
`3126029742`; cámbialo solo si piden otro).

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

## 6. Imágenes de portada de artículos

Desde `/admin/articulos`, además de pegar una URL externa, ahora se puede subir una imagen
directamente desde el computador (ver `ImagenArticuloService`). El backend la redimensiona
(máximo 1600px de lado), corrige la orientación y la recomprime siempre a JPEG calidad 82% —
una foto de celular sin editar queda casi siempre por debajo de 300 KB.

**Por qué necesita un volumen aparte**: el filesystem del propio contenedor del backend es
efímero (se recrea en cada `docker compose up -d --build`). Sin un volumen con nombre, cada
imagen subida se perdería en el siguiente despliegue. `docker-compose.prod.yml` ya declara
`sie_juridicos_uploads`, montado en `/data/uploads` dentro del contenedor del backend
(`IMAGENES_ARTICULOS_PATH=/data/uploads/articulos`) — no requiere ninguna variable nueva en
`.env.prod`, funciona solo con levantar el stack normalmente.

**Organización por mes**: las imágenes quedan en subcarpetas `yyyy-MM` (ej.
`/data/uploads/articulos/2026-09/`). Esto es a propósito, para poder limpiar meses viejos más
adelante sin tener que identificar imagen por imagen:

```bash
# Ver cuánto pesa cada mes
docker compose -f docker-compose.prod.yml exec backend du -sh /data/uploads/articulos/*

# Borrar un mes completo (irreversible: cualquier artículo, incluso publicado, que
# todavía referencie una imagen de ese mes se queda sin portada)
docker compose -f docker-compose.prod.yml exec backend rm -rf /data/uploads/articulos/2025-01
```

**Este volumen no está incluido en `scripts/respaldo-db.sh`** (ese script solo respalda
Postgres). Si quieres respaldar también las imágenes, un `tar` puntual del volumen basta —
no corre solo, hay que programarlo aparte si se necesita:

```bash
docker run --rm -v sie-juridicos-prod_sie_juridicos_uploads:/data -v $(pwd):/respaldo alpine \
  tar czf /respaldo/uploads-$(date +%F).tar.gz -C /data .
```

## 7. Respaldo de la base de datos

Respaldo manual puntual:

```bash
docker compose -f docker-compose.prod.yml exec postgres \
  pg_dump -U <DB_USER> <DB_NAME> > respaldo-$(date +%F).sql
```

**Respaldo automático diario** (recomendado, ya incluido): `scripts/respaldo-db.sh`
genera un respaldo comprimido cada noche y conserva los últimos 7 días automáticamente,
borrando el resto. Instalación (una sola vez, en el VPS):

```bash
chmod +x scripts/respaldo-db.sh
crontab -e
# agrega esta línea (respaldo diario a las 3:00 am hora del servidor):
0 3 * * * /root/sie-juridicos/scripts/respaldo-db.sh >> /root/respaldos-db/respaldo.log 2>&1
```

**Limitación real a tener presente**: estos respaldos viven únicamente en el disco del
mismo VPS. Si el VPS se pierde por completo (falla de disco, cuenta suspendida), los
respaldos se pierden con él. Para protección real ante ese escenario, hay que subirlos
también a almacenamiento externo (S3, Backblaze B2, un bucket de otro proveedor) — eso
requiere credenciales de ese servicio externo que hay que gestionar aparte.

## 8. Administrar la base de datos manualmente (opcional)

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
| `secrets/google-sheets-service-account.json` | Llave de solo lectura al Google Sheets de casos, montada como secreto de Compose (nunca en git, ver sección 2.1) |
| Volumen `sie_juridicos_uploads` | Imágenes de portada de artículos subidas desde el panel, organizadas por mes (ver sección 6) |
