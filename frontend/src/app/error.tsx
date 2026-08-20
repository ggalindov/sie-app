"use client";

import { useEffect } from "react";
import Link from "next/link";
import { ArrowClockwise, WarningCircle } from "@phosphor-icons/react";

// Error boundary del sitio público: si un componente de una página revienta
// en tiempo de ejecución, esto se muestra en vez de la pantalla en blanco
// por defecto de React/Next. No captura errores del layout raíz en sí (para
// eso existe global-error.tsx); solo de las páginas/segmentos por debajo.
export default function Error({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    // Queda solo en la consola del navegador: nunca se le muestra al
    // visitante el detalle técnico del error (mensaje/stack), por diseño.
    console.error("Error de página en el sitio público:", error);
  }, [error]);

  return (
    <main className="flex flex-1 items-center justify-center px-6 py-32">
      <div className="mx-auto max-w-lg text-center">
        <span className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-gold-pale/60 text-gold-deep">
          <WarningCircle weight="light" className="h-8 w-8" />
        </span>

        <h1 className="mt-8 text-balance font-display text-3xl leading-snug tracking-tight text-ink">
          Algo no salió bien.
        </h1>
        <p className="mt-4 text-balance text-base leading-relaxed text-ink-soft">
          Tuvimos un problema mostrando esta página. Puedes intentar de nuevo,
          o volver al inicio si el problema sigue.
        </p>

        <div className="mt-10 flex flex-wrap items-center justify-center gap-x-8 gap-y-4">
          <button
            type="button"
            onClick={() => reset()}
            className="cta-boton group inline-flex items-center gap-3 rounded-lg bg-gold px-7 py-4 text-base font-medium text-ink-fixed active:scale-[0.98]"
          >
            Intentar de nuevo
            <ArrowClockwise
              weight="bold"
              className="h-4 w-4 shrink-0 transition-transform duration-500 group-hover:rotate-180"
            />
          </button>

          <Link
            href="/"
            className="text-base font-medium text-ink underline decoration-line decoration-2 underline-offset-4 transition-colors duration-300 hover:decoration-gold"
          >
            Volver al inicio
          </Link>
        </div>
      </div>
    </main>
  );
}
