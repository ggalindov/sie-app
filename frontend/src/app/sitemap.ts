import type { MetadataRoute } from "next";
import { getArticulos } from "@/lib/api";
import { areasPractica } from "@/lib/content";

// NEXT_PUBLIC_API_URL apunta al backend (p. ej. "https://tudominio.com/api" en
// producción, ver docker-compose.prod.yml). El sitio público vive en el mismo dominio,
// un nivel arriba: derivarlo de ahí evita depender de una variable de entorno nueva
// solo para esto.
const SITE_URL = (process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:3000").replace(/\/api\/?$/, "");

const PAGINAS_ESTATICAS = ["", "/blog", "/preguntas-frecuentes", "/consulta-caso", "/cuida-tu-marca"];

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  // El negocio depende de posicionar en Google (ver CLAUDE.md): sin sitemap, los
  // buscadores dependen solo de rastrear enlaces internos para descubrir artículos
  // nuevos, lo que retrasa la indexación de contenido reciente.
  const articulos = await getArticulos().catch(() => []);

  const estaticas: MetadataRoute.Sitemap = PAGINAS_ESTATICAS.map((ruta) => ({
    url: `${SITE_URL}${ruta}`,
    changeFrequency: ruta === "" ? "weekly" : "monthly",
    priority: ruta === "" ? 1 : 0.6,
  }));

  const areas: MetadataRoute.Sitemap = areasPractica.map((area) => ({
    url: `${SITE_URL}/areas/${area.slug}`,
    changeFrequency: "monthly",
    priority: 0.7,
  }));

  const posts: MetadataRoute.Sitemap = articulos.map((articulo) => ({
    url: `${SITE_URL}/blog/${articulo.slug}`,
    lastModified: articulo.fechaPublicacion,
    changeFrequency: "yearly",
    priority: 0.5,
  }));

  return [...estaticas, ...areas, ...posts];
}
