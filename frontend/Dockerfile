# Build multi-stage: "deps" y "build" cargan devDependencies y el node_modules completo
# (500MB+), pero ninguna de las dos etapas llega a la imagen final. Gracias a
# output: "standalone" (ver next.config.ts), la etapa "runtime" solo copia lo que Next.js
# determinó que de verdad hace falta para correr, no todo el árbol de dependencias.

FROM node:22-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

# ---

FROM node:22-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .

# NEXT_PUBLIC_* se incrusta en el JavaScript del navegador durante el build, no se puede
# cambiar después en tiempo de ejecución (a diferencia de las variables del backend): por
# eso entra como ARG aquí y no como variable de entorno del contenedor en producción.
ARG NEXT_PUBLIC_API_URL
ENV NEXT_PUBLIC_API_URL=${NEXT_PUBLIC_API_URL}
ENV NODE_ENV=production

RUN npm run build

# ---

FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
ENV PORT=3000
ENV HOSTNAME=0.0.0.0

RUN addgroup -S sie && adduser -S sie -G sie

# server.js standalone (ver next.config.ts) no copia public/ ni .next/static por defecto
# (pensado para dejarlos en un CDN aparte): en este despliegue no hay CDN, así que se
# copian a mano para que el propio servidor de Node los sirva.
COPY --from=build /app/.next/standalone ./
COPY --from=build /app/.next/static ./.next/static
COPY --from=build /app/public ./public

USER sie
EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD wget -q -O /dev/null http://localhost:3000/ || exit 1

CMD ["node", "server.js"]
