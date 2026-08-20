import type { MetadataRoute } from "next";

// El panel administrativo está protegido por autenticación real (JWT +
// roles en el backend), así que esto no es la defensa en sí — es solo
// evitar que buscadores lo indexen y lo dejen listado en resultados de
// búsqueda como una URL "descubrible" de más.
export default function robots(): MetadataRoute.Robots {
  return {
    rules: {
      userAgent: "*",
      allow: "/",
      disallow: ["/admin"],
    },
  };
}
