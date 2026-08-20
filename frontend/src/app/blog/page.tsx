import Link from "next/link";
import type { Metadata } from "next";
import { MagnifyingGlass } from "@phosphor-icons/react/dist/ssr";
import { getArticulos, getCategorias, type TipoContenido } from "@/lib/api";

export const metadata: Metadata = {
  title: "Blog y Noticias",
  description:
    "Artículos, guías prácticas y noticias sobre novedades normativas de Colombia y el mundo.",
};

const TIPOS: { valor: TipoContenido | undefined; label: string }[] = [
  { valor: undefined, label: "Todo" },
  { valor: "BLOG", label: "Blog" },
  { valor: "NOTICIA", label: "Noticias" },
];

function formatearFecha(iso: string) {
  return new Date(iso).toLocaleDateString("es-CO", {
    day: "numeric",
    month: "long",
    year: "numeric",
  });
}

export default async function BlogPage({
  searchParams,
}: PageProps<"/blog">) {
  const params = await searchParams;
  const categoriaParam = Array.isArray(params.categoria)
    ? params.categoria[0]
    : params.categoria;
  const tipoParam = Array.isArray(params.tipo) ? params.tipo[0] : params.tipo;
  const qParam = Array.isArray(params.q) ? params.q[0] : params.q;

  // A diferencia de blog-teaser.tsx (que ya degradaba con gracia si el backend fallaba),
  // esta página llamaba directo sin try/catch: una caída del backend tumbaba toda la
  // página al error.tsx genérico en vez de mostrar un estado vacío honesto como el resto
  // del sitio.
  let errorBackend = false;
  const categorias = await getCategorias().catch(() => {
    errorBackend = true;
    return [];
  });
  const categoriaActiva = categorias.find((c) => c.slug === categoriaParam);
  const tipoActivo = tipoParam === "BLOG" || tipoParam === "NOTICIA" ? tipoParam : undefined;

  const articulos = await getArticulos({
    categoria: categoriaActiva?.id,
    tipo: tipoActivo,
    q: qParam,
  }).catch(() => {
    errorBackend = true;
    return [];
  });

  function construirHref(tipo?: TipoContenido, categoria?: string) {
    const search = new URLSearchParams();
    if (tipo) search.set("tipo", tipo);
    if (categoria) search.set("categoria", categoria);
    const query = search.toString();
    return `/blog${query ? `?${query}` : ""}`;
  }

  return (
    <main className="flex-1 pt-32 pb-24 md:pt-36">
      <div className="mx-auto max-w-6xl px-6">
        <h1 className="font-display text-4xl leading-tight tracking-tight md:text-5xl">
          Blog y Noticias
        </h1>
        <p className="mt-4 max-w-lg text-base leading-relaxed text-ink-soft">
          Artículos y guías prácticas escritos por nuestro equipo de abogados, y noticias
          sobre novedades normativas de Colombia y el mundo.
        </p>

        <div className="mt-8 flex flex-wrap gap-2">
          {TIPOS.map((t) => (
            <Link
              key={t.label}
              href={construirHref(t.valor, categoriaActiva?.slug)}
              className={`rounded-full px-4 py-2 text-sm font-medium transition-colors ${
                tipoActivo === t.valor
                  ? "bg-gold text-ink-fixed"
                  : "bg-ink/5 text-ink-soft hover:bg-ink/10"
              }`}
            >
              {t.label}
            </Link>
          ))}
        </div>

        <div className="mt-4 flex flex-col gap-6 md:flex-row md:items-center md:justify-between">
          <div className="flex flex-wrap gap-2">
            <Link
              href={construirHref(tipoActivo)}
              className={`rounded-full px-4 py-2 text-sm transition-colors ${
                !categoriaActiva
                  ? "bg-ink text-paper"
                  : "bg-ink/5 text-ink-soft hover:bg-ink/10"
              }`}
            >
              Todas
            </Link>
            {categorias.map((cat) => (
              <Link
                key={cat.slug}
                href={construirHref(tipoActivo, cat.slug)}
                className={`rounded-full px-4 py-2 text-sm transition-colors ${
                  categoriaActiva?.slug === cat.slug
                    ? "bg-ink text-paper"
                    : "bg-ink/5 text-ink-soft hover:bg-ink/10"
                }`}
              >
                {cat.nombre}
              </Link>
            ))}
          </div>

          <form className="relative w-full max-w-xs" action="/blog" method="GET">
            {categoriaActiva && (
              <input type="hidden" name="categoria" value={categoriaActiva.slug} />
            )}
            {tipoActivo && <input type="hidden" name="tipo" value={tipoActivo} />}
            <MagnifyingGlass
              weight="light"
              className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-soft"
            />
            <input
              type="search"
              name="q"
              defaultValue={qParam ?? ""}
              placeholder="Buscar artículos"
              className="w-full rounded-full border border-line bg-surface py-2.5 pl-10 pr-4 text-sm text-ink placeholder:text-ink-soft/60 focus:border-gold-deep focus:outline-none"
            />
          </form>
        </div>

        {articulos.length === 0 ? (
          <p className="mt-16 text-center text-sm text-ink-soft">
            {errorBackend
              ? "No pudimos cargar los artículos en este momento. Intenta de nuevo en unos minutos."
              : "No encontramos artículos con esos filtros."}
          </p>
        ) : (
          <div className="mt-10 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
            {articulos.map((articulo) => (
              <Link
                key={articulo.slug}
                href={`/blog/${articulo.slug}`}
                className="group flex flex-col overflow-hidden rounded-3xl bg-surface ring-1 ring-line"
              >
                <div className="flex aspect-[16/10] items-center justify-center overflow-hidden bg-night">
                  {articulo.imagenUrl ? (
                    // eslint-disable-next-line @next/next/no-img-element -- enlace externo arbitrario pegado por el admin
                    <img
                      src={articulo.imagenUrl}
                      alt={articulo.titulo}
                      className="h-full w-full object-cover transition-transform duration-500 group-hover:scale-105"
                    />
                  ) : (
                    <span className="font-display text-2xl text-gold/40">
                      {articulo.categoria.nombre}
                    </span>
                  )}
                </div>
                <div className="flex flex-1 flex-col p-6">
                  <p className="flex items-center gap-2 text-xs font-medium uppercase tracking-[0.1em] text-gold-deep">
                    {articulo.categoria.nombre}
                    {articulo.tipoContenido === "NOTICIA" && (
                      <span className="rounded-full bg-gold/10 px-2 py-0.5 text-[10px] text-gold-deep">
                        Noticia
                      </span>
                    )}
                  </p>
                  <h2 className="mt-2 font-display text-lg leading-snug">
                    {articulo.titulo}
                  </h2>
                  <p className="mt-2 flex-1 text-sm leading-relaxed text-ink-soft">
                    {articulo.resumen}
                  </p>
                  <p className="mt-4 text-xs text-ink-soft">
                    {formatearFecha(articulo.fechaPublicacion)}
                    {articulo.tiempoLecturaMin && ` · ${articulo.tiempoLecturaMin} min`}
                  </p>
                </div>
              </Link>
            ))}
          </div>
        )}
      </div>
    </main>
  );
}
