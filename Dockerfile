# Build multi-stage: la etapa "build" (con Maven + JDK completo, ~500MB+) nunca llega a la
# imagen final. Solo el .jar empaquetado pasa a la etapa "runtime" (JRE, sin compilador ni
# gestor de dependencias): así la imagen que de verdad corre en el VPS pesa una fracción y
# no carga en memoria nada que no necesite para ejecutar.

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

# Copia primero solo lo necesario para resolver dependencias: mientras no cambie el pom.xml,
# Docker reutiliza esta capa en rebuilds futuros y no vuelve a descargar todo Maven Central.
COPY mvnw pom.xml ./
COPY .mvn/ .mvn/

# Maven Central (y a veces el propio DNS del build) tiene baches transitorios reales
# ("Try again" / resolución intermitente), independientes del contenido del pom. Un
# reintento simple con backoff evita que un build de producción entero falle por un
# solo hipo de red de unos segundos. MAVEN_OPTS con retryHandler ataca el mismo
# problema a nivel de conexión HTTP individual (una descarga que se corta a la mitad),
# el bucle de shell ataca el caso de que el intento completo falle.
ENV MAVEN_OPTS="-Dmaven.wagon.http.retryHandler.count=5 -Dmaven.wagon.httpconnectionManager.ttlSeconds=120"
RUN chmod +x mvnw && \
    for intento in 1 2 3; do \
        ./mvnw -B -q dependency:go-offline && break; \
        echo "dependency:go-offline falló (intento $intento/3), reintentando en 10s..."; \
        sleep 10; \
    done

COPY src/ src/
RUN for intento in 1 2 3; do \
        ./mvnw -B -q -DskipTests package && break; \
        echo "mvn package falló (intento $intento/3), reintentando en 10s..."; \
        sleep 10; \
    done && \
    mv target/*.jar app.jar

# ---

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Usuario sin privilegios: si algo dentro del contenedor llega a ejecutar código arbitrario
# (una dependencia comprometida, por ejemplo), que no corra como root.
RUN addgroup -S sie && adduser -S sie -G sie
COPY --from=build /build/app.jar app.jar
USER sie

# El propio backend ya expone un healthcheck minimalista sin autenticación
# (SaludController, GET /api/salud) pensado exactamente para esto: confirma que el
# proceso está vivo y respondiendo, sin tocar la base de datos ni exponer detalles
# internos (se evaluó y se descartó a propósito usar Spring Boot Actuator completo, ver
# CLAUDE.md, por la superficie de ataque que agrega).
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD wget -q -O /dev/null http://127.0.0.1:8080/api/salud || exit 1

EXPOSE 8080

# MaxRAMPercentage (no -Xmx fijo) para que el heap se calcule según el límite de memoria
# real del contenedor (mem_limit en docker-compose.prod.yml), no según la RAM total del
# host VPS: si el límite del contenedor cambia, el JVM se ajusta solo, sin tocar esta
# línea. SerialGC en vez de G1 (el default moderno): G1 está pensado para heaps grandes
# con varios núcleos, en un VPS pequeño (1-2 vCPU, poca RAM) su overhead de contabilidad
# interna cuesta más de lo que ahorra; SerialGC es el recomendado oficialmente por
# OpenJDK para exactamente este perfil de máquina. JAVA_OPTS queda como variable de
# entorno editable por si hay que afinar esto sin reconstruir la imagen.
#
# CORREGIDO dos veces -- primero por auditoría, luego por un incidente real en producción:
#
# 1) Auditoría inicial: con el límite del contenedor de ENTONCES (512m), la combinación
#    anterior de MaxRAMPercentage=75% (384m de heap) + MaxMetaspaceSize=160m ya sumaba 544m,
#    por encima del límite duro de cgroup, ANTES de contar stacks de hilos, code cache del
#    JIT o buffers directos de Tomcat. Un mem_limit excedido es un OOM-kill duro del
#    contenedor (el kernel lo mata, no hay degradación suave). Se bajó a 60%/128m.
#
# 2) Incidente real en producción (después de esa auditoría): el chatbot empezó a fallar con
#    "OutOfMemoryError: Metaspace" en cada mensaje -- confirmado en los logs reales del
#    backend, no una hipótesis. Causa: el proyecto creció mucho desde el ajuste de arriba
#    (cliente de Google Sheets -- trae bastantes clases propias --, el SDK de Anthropic en
#    Kotlin -- que el chatbot carga perezosamente recién en su primer uso real, coincidiendo
#    justo con cuándo aparecía el error --, WhatsApp Cloud API, cifrado de campos, Registro
#    del Sistema), y esas 128m de techo ya se quedaban cortas para el metaspace real que la
#    aplicación necesita hoy. El límite del contenedor también subió (ver BACKEND_MEM_LIMIT
#    en docker-compose.prod.yml, 640m -> 1024m: el VPS real tiene RAM de sobra, confirmado
#    con `free -h` en el incidente), así que el metaspace pudo subir con margen real en vez
#    de quedar otra vez al límite: 1024*0.6=614m de heap + 256m de metaspace = 870m, dejando
#    ~154m de colchón para el resto de la JVM.
ENV JAVA_OPTS="-XX:+UseSerialGC -XX:MaxRAMPercentage=60.0 -XX:InitialRAMPercentage=40.0 -XX:MaxMetaspaceSize=256m -Xss512k -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
