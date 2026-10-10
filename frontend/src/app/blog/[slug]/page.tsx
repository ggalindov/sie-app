import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { getArticuloPorSlug } from "@/lib/api";
import { ArticuloCabecera } from "@/components/articulo-cabecera";
import { ArticuloSidebar } from "@/components/articulo-sidebar";
import { extraerYouTubeId } from "@/lib/utils";

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

  const videoId = extraerYouTubeId(articulo.videoYoutubeUrl);

  const fechaFormateada = formatearFecha(articulo.fechaPublicacion);

  return (
    <main className="flex-1 pt-32 pb-24 md:pt-36">
      {/* Dos columnas (pedido explícito del usuario: "mas nivel articulo o periodico con dos
          columnas"), SOLO en escritorio -- en móvil es exactamente el mismo mx-auto max-w-3xl
          de antes, sin ningún cambio. lg:items-start evita que el riel lateral se estire para
          igualar el alto del artículo (se queda "sticky" a su propio contenido, ver
          ArticuloSidebar). */}
      <div className="mx-auto max-w-3xl px-6 lg:max-w-6xl lg:px-8">
        <div className="lg:grid lg:grid-cols-[1fr_300px] lg:gap-16 lg:items-start">
          <article className="min-w-0 lg:max-w-[42rem]">
            <ArticuloCabecera
              categoria={articulo.categoria.nombre}
              titulo={articulo.titulo}
              meta={`${articulo.autorNombre} · ${fechaFormateada}${
                articulo.tiempoLecturaMin ? ` · ${articulo.tiempoLecturaMin} min de lectura` : ""
              }`}
              // La misma imagenUrl que ya se ve en la miniatura del listado (ver blog/page.tsx),
              // ahora también dentro del propio artículo. aspect-[16/9] (no [16/10] como la
              // miniatura, a propósito: más grande/cinematográfico aquí, como momento "hero" del
              // artículo. Mismo tratamiento de sombra + filo dorado que el video de abajo, para
              // que los dos bloques de medios del artículo luzcan de la misma familia visual
              // (pedido explícito: "las imagenes se vean mucho mejor").
              imagen={
                articulo.imagenUrl && (
                  <div className="relative mt-8 aspect-[16/9] w-full overflow-hidden rounded-3xl bg-night shadow-[0_20px_50px_-15px_rgba(0,0,0,0.5)] ring-1 ring-gold/25 md:mt-10">
                    {/* eslint-disable-next-line @next/next/no-img-element -- enlace externo arbitrario pegado por el admin */}
                    <img src={articulo.imagenUrl} alt={articulo.titulo} className="h-full w-full object-cover" />
                  </div>
                )
              }
            />

            {videoId && (
              <div className="relative mt-8 aspect-video w-full overflow-hidden rounded-2xl md:rounded-3xl bg-night shadow-[0_20px_50px_-15px_rgba(0,0,0,0.5)] ring-1 ring-gold/25 md:mt-10">
                <iframe
                  src={`https://www.youtube.com/embed/${videoId}?rel=0`}
                  title={`Video: ${articulo.titulo}`}
                  allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
                  allowFullScreen
                  className="absolute inset-0 h-full w-full border-0"
                />
              </div>
            )}

            <div
              className="contenido-articulo mt-10 text-ink md:mt-12"
              dangerouslySetInnerHTML={{ __html: articulo.contenido }}
            />
          </article>

          <ArticuloSidebar
            categoria={articulo.categoria.nombre}
            autorNombre={articulo.autorNombre}
            fecha={fechaFormateada}
            tiempoLecturaMin={articulo.tiempoLecturaMin}
          />
        </div>
      </div>
    </main>
  );
}
