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

echo ============================================================
echo  SIE Juridicos backend
echo  - Docker Desktop debe estar corriendo (spring-boot-docker-compose
echo    levanta y apaga el contenedor de Postgres automaticamente).
echo  - Admin de arranque: %ADMIN_BOOTSTRAP_EMAIL%
echo ============================================================

call "%~dp0mvnw.cmd" spring-boot:run

endlocal
