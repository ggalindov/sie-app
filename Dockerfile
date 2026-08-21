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
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -q -DskipTests package && \
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
    CMD wget -q -O /dev/null http://localhost:8080/api/salud || exit 1

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
# CORREGIDO tras auditoría: con el límite por defecto del contenedor (512m, ver
# BACKEND_MEM_LIMIT en docker-compose.prod.yml), la combinación anterior de
# MaxRAMPercentage=75% (384m de heap) + MaxMetaspaceSize=160m ya sumaba 544m, por encima
# del límite duro de cgroup, ANTES de contar stacks de hilos, code cache del JIT o
# buffers directos de Tomcat. Un mem_limit excedido es un OOM-kill duro del contenedor
# (el kernel lo mata, no hay degradación suave). Bajado a 60%/128m: 512*0.6=307m de heap
# + 128m de metaspace = 435m, dejando ~77m de colchón real para el resto de la JVM.
ENV JAVA_OPTS="-XX:+UseSerialGC -XX:MaxRAMPercentage=60.0 -XX:InitialRAMPercentage=40.0 -XX:MaxMetaspaceSize=128m -Xss512k -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
