import Link from "next/link";
import type { Metadata } from "next";
import { notFound } from "next/navigation";
import { ArrowLeft } from "@phosphor-icons/react/dist/ssr";
import { getArticuloPorSlug } from "@/lib/api";

function formatearFecha(iso: string) {
  return new Date(iso).toLocaleDateString("es-CO", {
    day: "numeric",
    month: "long",
    year: "numeric",
  });
}

export async function generateMetadata({
  params,
}: PageProps<"/blog/[slug]">): Promise<Metadata> {
  const { slug } = await params;
  const articulo = await getArticuloPorSlug(slug).catch(() => null);
  if (!articulo) return {};
  return {
    title: articulo.titulo,
    description: articulo.resumen ?? undefined,
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
        <Link
          href="/blog"
          className="inline-flex items-center gap-2 text-sm font-medium text-ink-soft transition-colors hover:text-ink"
        >
          <ArrowLeft weight="bold" className="h-4 w-4" />
          Volver al blog
        </Link>

        <p className="mt-8 text-xs font-medium uppercase tracking-[0.1em] text-gold-deep">
          {articulo.categoria.nombre}
        </p>
        <h1 className="mt-3 text-balance font-display text-3xl leading-tight tracking-tight md:text-5xl">
          {articulo.titulo}
        </h1>
        <p className="mt-5 text-sm text-ink-soft">
          {articulo.autorNombre} · {formatearFecha(articulo.fechaPublicacion)}
          {articulo.tiempoLecturaMin && ` · ${articulo.tiempoLecturaMin} min de lectura`}
        </p>

        <div
          className="prose prose-neutral mt-10 max-w-none text-ink [&_a]:text-gold-deep [&_h2]:font-display [&_h2]:text-2xl [&_p]:leading-relaxed [&_p]:text-ink-soft"
          dangerouslySetInnerHTML={{ __html: articulo.contenido }}
        />
      </article>
    </main>
  );
}
