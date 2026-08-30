import type { MetadataRoute } from "next";

// El panel administrativo está protegido por autenticación real (JWT +
// roles en el backend), así que esto no es la defensa en sí — es solo
// evitar que buscadores lo indexen y lo dejen listado en resultados de
// búsqueda como una URL "descubrible" de más.
const SITE_URL = (process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:3000").replace(/\/api\/?$/, "");

export default function robots(): MetadataRoute.Robots {
  return {
    rules: {
      userAgent: "*",
      allow: "/",
      disallow: ["/admin"],
    },
    sitemap: `${SITE_URL}/sitemap.xml`,
  };
}
