import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getArticuloPorSlug } from "@/lib/api";
import { ArticuloCabecera } from "@/components/articulo-cabecera";

// timeZone explícito: ver el mismo comentario en blog-grid.tsx -- sin esto, la fecha se
// muestra en la zona de quien renderiza (navegador o servidor), no en la de Colombia.
function formatearFecha(iso: string) {
  return new Date(iso).toLocaleDateString("es-CO", {
    day: "numeric",
    month: "long",
    year: "numeric",
    timeZone: "America/Bogota",
  });
}

export async function generateMetadata({
  params,
}: PageProps<"/blog/[slug]">): Promise<Metadata> {
  const { slug } = await params;
  const articulo = await getArticuloPorSlug(slug).catch(() => null);
  if (!articulo) return {};

  // Sin esto, la tarjeta de vista previa que arma WhatsApp/Facebook/iMessage al pegar el
  // enlace del artículo heredaba el título, descripción e imagen GENÉRICOS de todo el
  // sitio (definidos en layout.tsx) -- nunca los del artículo en sí. openGraph/twitter no
  // se combinan automáticamente con el título/descripción de arriba: hay que declararlos
  // aparte, o si no, la tarjeta de vista previa se ve igual sin importar qué artículo se
  // comparta.
  const descripcion = articulo.resumen ?? undefined;
  const imagenes = articulo.imagenUrl ? [{ url: articulo.imagenUrl }] : undefined;

  return {
    title: articulo.titulo,
    description: descripcion,
    alternates: { canonical: `/blog/${slug}` },
    openGraph: {
      type: "article",
      title: articulo.titulo,
      description: descripcion,
      url: `/blog/${slug}`,
      images: imagenes,
    },
    twitter: {
      card: "summary_large_image",
      title: articulo.titulo,
      description: descripcion,
      images: imagenes,
    },
  };
}

export default async function ArticuloPage({
  params,
}: PageProps<"/blog/[slug]">) {
  const { slug } = await params;
  // Igual que en blog/page.tsx: si el backend está caído, se trata como "no
  // encontrado" (página 404 propia) en vez de tumbar la página al error.tsx genérico.
  const articulo = await getArticuloPorSlug(slug).catch(() => null);

  if (!articulo) notFound();

  return (
    <main className="flex-1 pt-32 pb-24 md:pt-36">
      <article className="mx-auto max-w-3xl px-6">
        <ArticuloCabecera
          categoria={articulo.categoria.nombre}
          titulo={articulo.titulo}
          meta={`${articulo.autorNombre} · ${formatearFecha(articulo.fechaPublicacion)}${
            articulo.tiempoLecturaMin ? ` · ${articulo.tiempoLecturaMin} min de lectura` : ""
          }`}
          // La misma imagenUrl que ya se ve en la miniatura del listado (ver blog/page.tsx),
          // ahora también dentro del propio artículo: antes solo se guardaba/mostraba en la
          // tarjeta, nunca en el detalle. aspect-[16/9] (no [16/10] como la miniatura, a
          // propósito: más grande/cinematográfico aquí, para marcar que este es el momento
          // "hero" del artículo, distinto de la miniatura chica de la lista).
          imagen={
            articulo.imagenUrl && (
              <div className="relative mt-8 aspect-[16/9] w-full overflow-hidden rounded-3xl bg-night md:mt-10">
                {/* eslint-disable-next-line @next/next/no-img-element -- enlace externo arbitrario pegado por el admin */}
                <img src={articulo.imagenUrl} alt={articulo.titulo} className="h-full w-full object-cover" />
              </div>
            )
          }
        />

        <div
          className="contenido-articulo mt-10 text-ink md:mt-12"
          dangerouslySetInnerHTML={{ __html: articulo.contenido }}
        />
      </article>
    </main>
  );
}
