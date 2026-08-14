import Link from "next/link";
import { ArrowUpRight } from "@phosphor-icons/react/dist/ssr";
import { getArticulos, type ArticuloResumen } from "@/lib/api";

function formatearFecha(iso: string) {
  return new Date(iso).toLocaleDateString("es-CO", {
    day: "numeric",
    month: "long",
    year: "numeric",
  });
}

export async function BlogTeaser() {
  let articulos: ArticuloResumen[];
  try {
    articulos = (await getArticulos()).slice(0, 3);
  } catch {
    articulos = [];
  }

  if (articulos.length === 0) {
    return (
      <section id="blog" className="snap-slide section-seam py-24 md:py-32">
        <div className="mx-auto max-w-7xl px-6">
          <h2 className="font-display text-3xl leading-tight tracking-tight md:text-4xl">
            Blog jurídico
          </h2>
          <div
            className="mt-10 flex min-h-[220px] flex-col items-center justify-center rounded-3xl bg-surface p-10 text-center ring-1 ring-line"
            style={{
              backgroundImage:
                "radial-gradient(60% 80% at 50% 0%, rgba(217,169,37,0.1), transparent 70%)",
            }}
          >
            <p className="font-display text-xl">Estamos preparando nuestros primeros artículos.</p>
            <p className="mt-2 max-w-sm text-sm leading-relaxed text-ink-soft">
              Muy pronto encontrarás aquí contenido jurídico útil sobre las áreas en las
              que te podemos ayudar.
            </p>
          </div>
        </div>
      </section>
    );
  }

  const [destacado, ...resto] = articulos;

  return (
    <section id="blog" className="snap-slide section-seam py-24 md:py-32">
      <div className="mx-auto max-w-7xl px-6">
        <div className="flex items-end justify-between gap-4">
          <h2 className="font-display text-3xl leading-tight tracking-tight md:text-4xl">
            Blog jurídico
          </h2>
          <Link
            href="/blog"
            className="hidden shrink-0 items-center gap-1 text-sm font-medium text-ink underline decoration-line decoration-2 underline-offset-4 hover:decoration-gold sm:flex"
          >
            Ver todos los artículos
          </Link>
        </div>

        <div className="mt-10 grid gap-6 md:grid-cols-2">
          <Link
            href={`/blog/${destacado.slug}`}
            className="group flex flex-col overflow-hidden rounded-3xl bg-surface ring-1 ring-line transition-colors md:row-span-2"
          >
            <div className="flex aspect-[16/9] items-center justify-center bg-night md:aspect-auto md:flex-1">
              <span className="font-display text-4xl text-gold/40">
                {destacado.categoria.nombre}
              </span>
            </div>
            <div className="p-7">
              <p className="text-xs font-medium uppercase tracking-[0.1em] text-gold-deep">
                {destacado.categoria.nombre}
              </p>
              <h3 className="mt-2 font-display text-2xl leading-snug">
                {destacado.titulo}
              </h3>
              <p className="mt-2 text-sm leading-relaxed text-ink-soft">
                {destacado.resumen}
              </p>
              <p className="mt-4 text-xs text-ink-soft">
                {formatearFecha(destacado.fechaPublicacion)}
                {destacado.tiempoLecturaMin && ` · ${destacado.tiempoLecturaMin} min de lectura`}
              </p>
            </div>
          </Link>

          <div className="flex flex-col gap-6">
            {resto.map((articulo) => (
              <Link
                key={articulo.slug}
                href={`/blog/${articulo.slug}`}
                className="group flex items-start justify-between gap-4 rounded-3xl bg-surface p-6 ring-1 ring-line"
              >
                <div>
                  <p className="text-xs font-medium uppercase tracking-[0.1em] text-gold-deep">
                    {articulo.categoria.nombre}
                  </p>
                  <h3 className="mt-2 font-display text-lg leading-snug">
                    {articulo.titulo}
                  </h3>
                  <p className="mt-3 text-xs text-ink-soft">
                    {formatearFecha(articulo.fechaPublicacion)}
                  </p>
                </div>
                <ArrowUpRight
                  weight="bold"
                  className="mt-1 h-5 w-5 shrink-0 text-ink-soft transition-transform duration-300 group-hover:translate-x-0.5 group-hover:-translate-y-0.5"
                />
              </Link>
            ))}
          </div>
        </div>

        <Link
          href="/blog"
          className="mt-8 flex items-center gap-1 text-sm font-medium text-ink underline decoration-line decoration-2 underline-offset-4 hover:decoration-gold sm:hidden"
        >
          Ver todos los artículos
        </Link>
      </div>
    </section>
  );
}
