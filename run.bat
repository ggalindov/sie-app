@echo off
setlocal

rem Ancla el directorio de trabajo a la carpeta donde esta este script, sin importar
rem desde donde se invoque (evita fallos de resolucion de "call mvnw.cmd" por nombre pelado).
cd /d "%~dp0"

rem Tu JAVA_HOME del sistema apunta a una carpeta que ya no existe
rem (C:\Users\pc\Downloads\openjdk-21_windows-x64_bin\jdk-21). Se sobreescribe aqui con un
rem JDK que si existe en tu maquina (instalado por IntelliJ). Ajusta esta ruta si la borras
rem o si instalas tu propio JDK 21.
set JAVA_HOME=C:\Users\pc\.jdks\openjdk-26.0.2

rem === Credenciales/config de la aplicacion (deben coincidir con .env para Postgres) ===
set DB_NAME=sie_juridicos
set DB_USER=sie_user
set DB_PASSWORD=secret

rem Puerto local de Postgres: 5432 puede estar ocupado por el contenedor de otro
rem proyecto tuyo en esta maquina (visto en vivo: "nativatrips-postgres"). POSTGRES_PORT
rem controla el mapeo de compose.yaml y DB_PORT el que usa el propio backend para
rem conectarse; deben ser el mismo numero. Cambia esto de vuelta a 5432 si liberas el
rem puerto por tu cuenta.
set POSTGRES_PORT=5433
set DB_PORT=5433

rem Alta del primer ADMIN_GENERAL (no hay registro publico, ver AdminBootstrapRunner)
set ADMIN_BOOTSTRAP_EMAIL=admin@siejuridicos.com
set ADMIN_BOOTSTRAP_PASSWORD=ClaveAdmin2026!

rem Firma de los JWT (cambia esto por una clave propia larga y aleatoria en produccion)
set JWT_SECRET=cambia-esto-por-una-clave-larga-y-aleatoria-de-al-menos-32-caracteres

rem Necesaria solo para probar el chatbot (POST /api/chatbot/mensaje). Deja vacio si no la tienes aun.
set ANTHROPIC_API_KEY=

rem Correo saliente (confirmaciones, citas, recordatorios, notificacion al admin).
rem Sin esto los envios fallan en segundo plano (con log WARN) pero la app funciona igual.
rem Para Gmail: activa verificacion en 2 pasos en tu cuenta y genera una "contrasena de
rem aplicacion" en https://myaccount.google.com/apppasswords (no uses tu contrasena normal).
set MAIL_USERNAME=
set MAIL_PASSWORD=

rem Google Sheets de casos (consulta de estado en /consulta-caso, ver HojaCalculoService).
rem Sin esto, la consulta de estado responde "servicio no disponible" pero la app funciona
rem igual (crear casos desde el panel sigue andando). GOOGLE_SHEETS_ID es el ID de la hoja
rem (el segmento largo en su URL, entre /d/ y /edit). GOOGLE_SHEETS_CREDENTIALS_PATH es la
rem ruta local a la llave JSON de una cuenta de servicio de Google Cloud con acceso de SOLO
rem LECTURA a esa hoja (compartida como "Lector", nunca "Editor" ni "cualquiera con el
rem enlace"). Nunca pongas la llave JSON dentro del repo sin que su carpeta esté en
rem .gitignore.
set GOOGLE_SHEETS_ID=
set GOOGLE_SHEETS_CREDENTIALS_PATH=

rem Llave de cifrado de datos sensibles de clientes/casos (ver CifradoService). Ya trae un
rem valor por defecto de desarrollo (ver application.properties) para que el proyecto
rem funcione sin configurar nada; en produccion DEBE cambiarse por una real: genera una con
rem "openssl rand -base64 32" (debe decodificar a exactamente 32 bytes).
set DATA_ENCRYPTION_KEY=

rem Notificacion del radicado por WhatsApp (linea de atencion de la firma, ver
rem WhatsAppService), ademas del correo. Sin esto, la notificacion se sigue enviando solo
rem por correo con normalidad. WHATSAPP_ACCESS_TOKEN y WHATSAPP_PHONE_NUMBER_ID vienen de
rem Meta Business Manager (WhatsApp Cloud API, ver DEPLOY.md para la guia completa).
rem WHATSAPP_TEMPLATE_NAME debe coincidir EXACTO con el nombre de una plantilla ya aprobada
rem por Meta -- no admite texto libre.
set WHATSAPP_ACCESS_TOKEN=
set WHATSAPP_PHONE_NUMBER_ID=
set WHATSAPP_TEMPLATE_NAME=notificacion_radicado
set WHATSAPP_TEMPLATE_LANG=es

rem Cobros Pendientes: recordatorio mensual de pago (correo + WhatsApp), ver modulo cobro/.
rem Hoja de Google DISTINTA a la de casos (GOOGLE_SHEETS_COBROS_ID), con permiso de EDITOR
rem otorgado sobre esa hoja especifica (unica escritura real que hace el sistema: marcar la
rem columna "RESPONDIO MENSAJE"). WHATSAPP_TEMPLATE_COBRO_NAME es una plantilla distinta a la
rem de radicado, con botones de respuesta rapida Si/No, aprobada aparte en Meta. Las dos
rem variables WEBHOOK verifican el webhook publico que recibe la respuesta del cliente por
rem WhatsApp (ver WhatsAppWebhookController): WEBHOOK_VERIFY_TOKEN lo inventas tu mismo (una
rem cadena aleatoria) y la registras igual en Meta al configurar el webhook; APP_SECRET sale
rem de Meta Business Manager (Configuracion basica de la app).
set GOOGLE_SHEETS_COBROS_ID=
set WHATSAPP_TEMPLATE_COBRO_NAME=recordatorio_cobro
set WHATSAPP_WEBHOOK_VERIFY_TOKEN=
set WHATSAPP_APP_SECRET=

rem Aviso interno (no al cliente): apenas llega una solicitud nueva del formulario publico, se
rem manda un WhatsApp con el resumen completo a la linea interna de la firma. Plantilla
rem DISTINTA a las de radicado/cobro, debe aprobarse aparte en Meta. WHATSAPP_ADMIN_NUMERO ya
rem trae el numero fijo pedido (+57 312 4781583); solo cambialo si la firma pide otra linea.
set WHATSAPP_TEMPLATE_SOLICITUD_NAME=nueva_solicitud
set WHATSAPP_ADMIN_NUMERO=+573124781583

rem Credenciales reales (Gmail u otras): NUNCA las pongas aqui arriba, este archivo esta
rem versionado en git y se sube al repositorio. Crea "run.local.bat" (esta en .gitignore,
rem nunca se sube) junto a este script con las mismas variables set MAIL_USERNAME=... /
rem set MAIL_PASSWORD=... y se cargan automaticamente abajo si el archivo existe.
if exist "%~dp0run.local.bat" call "%~dp0run.local.bat"

echo ============================================================
echo  SIE Juridicos backend
echo  - Docker Desktop debe estar corriendo (spring-boot-docker-compose
echo    levanta y apaga el contenedor de Postgres automaticamente).
echo  - Admin de arranque: %ADMIN_BOOTSTRAP_EMAIL%
echo ============================================================

call "%~dp0mvnw.cmd" spring-boot:run

endlocal
