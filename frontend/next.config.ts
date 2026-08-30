import type { NextConfig } from "next";

// Origen real de la API a la que el navegador necesita conectarse (fetch/XHR):
// en dev es http://localhost:8080, en producción lo que se configure en
// NEXT_PUBLIC_API_URL. Sin esto en connect-src, el CSP bloquearía toda
// llamada al backend.
const apiOrigin = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

// CSP razonablemente estricta, no perfecta: 'unsafe-inline' en script-src es
// una concesión deliberada por el único script inline del sitio (la
// detección de tema oscuro/claro en layout.tsx, que debe ejecutar antes de
// la primera pintura para no parpadear — moverlo a un script con nonce
// requeriría middleware nuevo, fuera de alcance de este pase). Sigue
// bloqueando: scripts de otros orígenes, iframes de terceros, envío de datos
// a destinos no declarados, y objetos/plugins embebidos.
// React/Turbopack en modo desarrollo usan eval() internamente para el hot
// reload y la reconstrucción de stack traces (nunca en producción, lo dice
// el propio mensaje de advertencia de React). Sin 'unsafe-eval' aquí, el
// servidor de desarrollo directamente no carga. En build de producción
// (NODE_ENV=production) esta concesión no se agrega.
const esDesarrollo = process.env.NODE_ENV !== "production";

const csp = [
  "default-src 'self'",
  `script-src 'self' 'unsafe-inline'${esDesarrollo ? " 'unsafe-eval'" : ""}`,
  "style-src 'self' 'unsafe-inline'",
  // https: (sin restringir a un host) porque la imagen de portada de un
  // artículo es un enlace externo que el admin pega a mano (RF: "poner el
  // enlace de la imagen"), no un asset propio del sitio: no hay una lista
  // fija de dominios que permitir de antemano.
  "img-src 'self' data: blob: https:",
  "font-src 'self' data:",
  "media-src 'self'",
  `connect-src 'self' ${apiOrigin}`,
  "frame-ancestors 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  "object-src 'none'",
].join("; ");

const securityHeaders = [
  { key: "Content-Security-Policy", value: csp },
  { key: "X-Content-Type-Options", value: "nosniff" },
  // Redundante con frame-ancestors del CSP (que ya cubren los navegadores
  // modernos), pero se deja para navegadores viejos que solo respetan esta
  // cabecera clásica.
  { key: "X-Frame-Options", value: "DENY" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  // Ningún permiso de navegador que el sitio realmente use (no hay cámara,
  // micrófono, geolocalización ni pago embebido en ninguna página).
  { key: "Permissions-Policy", value: "camera=(), microphone=(), geolocation=(), payment=()" },
  // Solo tiene efecto real bajo HTTPS (el navegador la ignora en HTTP, como
  // en desarrollo local); se deja puesta para cuando el sitio se despliegue
  // con TLS real.
  { key: "Strict-Transport-Security", value: "max-age=63072000; includeSubDomains" },
];

const nextConfig: NextConfig = {
  // Quita la cabecera "X-Powered-By: Next.js": no aporta nada al visitante y
  // sí le regala al atacante una pista gratis de qué framework/versión atacar.
  poweredByHeader: false,
  // Para el Dockerfile de producción: en vez de copiar todo node_modules (cientos de MB,
  // incluidas devDependencies de build), Next.js traza automáticamente qué archivos usa
  // cada página y genera un .next/standalone con solo eso + un server.js mínimo propio
  // (reemplaza "next start"). Reduce drásticamente el tamaño de la imagen y la memoria
  // que carga Node al arrancar, sin cambiar nada del comportamiento en desarrollo
  // ("next dev" ignora esta opción por completo).
  output: "standalone",
  async headers() {
    return [
      {
        source: "/:path*",
        headers: securityHeaders,
      },
      // Los assets estáticos de marca (videos de fondo, logo, fotos del equipo, logos de
      // confianza) nunca cambian de contenido bajo el mismo nombre de archivo -- si algún
      // día se reemplaza uno, se renombra el archivo (cache-busting real) en vez de pisar
      // el mismo nombre. Sin este Cache-Control explícito, Next.js sirve estos archivos de
      // /public sin ninguna cabecera de caché fuerte: cada visita repetida (o cada
      // navegación entre secciones de la misma sesión) los vuelve a pedir enteros al
      // servidor en vez de servirlos directo desde el caché del navegador. "immutable"
      // evita incluso la revalidación condicional (If-None-Match) de un año completo.
      {
        source: "/(videos|marca|equipo|confianza|fondos)/:path*",
        headers: [{ key: "Cache-Control", value: "public, max-age=31536000, immutable" }],
      },
    ];
  },
};

export default nextConfig;
