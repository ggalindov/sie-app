"use client";

import { useEffect, useState } from "react";

// Gráficas propias en SVG (sin librería externa): donut con arcos redondeados y gauge
// radial, con tooltip al pasar el mouse/tocar y leyenda con el valor siempre visible
// (nunca solo color). Reemplazan las barras planas anteriores del panel, que eran
// idénticas en cualquier dashboard genérico.

export type Segmento = { etiqueta: string; valor: number; color: string };

function useEntrada() {
  const [listo, setListo] = useState(false);
  useEffect(() => {
    const id = requestAnimationFrame(() => setListo(true));
    return () => cancelAnimationFrame(id);
  }, []);
  return listo;
}

export function DonutChart({
  segmentos,
  centroLabel,
}: {
  segmentos: Segmento[];
  centroLabel?: string;
}) {
  const listo = useEntrada();
  const [activo, setActivo] = useState<number | null>(null);
  const total = segmentos.reduce((a, s) => a + s.valor, 0);

  const RADIO = 70;
  const GROSOR = 24;
  const CIRC = 2 * Math.PI * RADIO;
  const HUECO = total > 0 ? 1.4 : 0; // separación entre segmentos (grados de arco, vía offset en px)

  let acumulado = 0;

  return (
    <div className="flex flex-col items-center gap-5 sm:flex-row sm:items-center sm:gap-8">
      <div className="relative h-44 w-44 shrink-0">
        <svg viewBox="0 0 200 200" className="h-full w-full -rotate-90">
          <circle cx="100" cy="100" r={RADIO} fill="none" stroke="var(--color-line)" strokeWidth={GROSOR} />
          {total > 0 &&
            segmentos.map((s, i) => {
              if (s.valor <= 0) return null;
              const frac = s.valor / total;
              const largo = Math.max(frac * CIRC - HUECO, 0);
              const offset = CIRC - acumulado;
              acumulado += frac * CIRC;
              const esActivo = activo === i;
              return (
                <circle
                  key={s.etiqueta}
                  cx="100"
                  cy="100"
                  r={RADIO}
                  fill="none"
                  stroke={s.color}
                  strokeWidth={esActivo ? GROSOR + 4 : GROSOR}
                  strokeLinecap="round"
                  strokeDasharray={`${listo ? largo : 0} ${CIRC}`}
                  strokeDashoffset={offset}
                  className="cursor-pointer transition-all duration-700 ease-out"
                  style={{ transitionProperty: "stroke-dasharray, stroke-width", opacity: activo === null || esActivo ? 1 : 0.45 }}
                  onMouseEnter={() => setActivo(i)}
                  onMouseLeave={() => setActivo(null)}
                >
                  <title>{`${s.etiqueta}: ${s.valor}`}</title>
                </circle>
              );
            })}
        </svg>
        <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
          <span className="font-display text-3xl text-ink">
            {activo !== null ? segmentos[activo].valor : total}
          </span>
          <span className="max-w-[6.5rem] text-center text-[11px] leading-tight text-ink-soft">
            {activo !== null ? segmentos[activo].etiqueta : centroLabel ?? "Total"}
          </span>
        </div>
      </div>

      <ul className="flex w-full flex-col gap-2.5">
        {segmentos.map((s, i) => (
          <li
            key={s.etiqueta}
            onMouseEnter={() => setActivo(i)}
            onMouseLeave={() => setActivo(null)}
            className={`flex cursor-default items-center justify-between gap-3 rounded-lg px-2 py-1.5 text-sm transition-colors ${
              activo === i ? "bg-ink/5" : ""
            }`}
          >
            <span className="flex items-center gap-2.5 text-ink-soft">
              <span className="h-2.5 w-2.5 shrink-0 rounded-full" style={{ backgroundColor: s.color }} />
              {s.etiqueta}
            </span>
            <span className="font-medium text-ink">
              {s.valor}
              <span className="ml-1.5 text-xs font-normal text-ink-soft">
                {total > 0 ? `${Math.round((s.valor / total) * 100)}%` : "0%"}
              </span>
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

export function GaugeRadial({
  porcentaje,
  valorLabel,
  subLabel,
  tono = "gold",
}: {
  porcentaje: number;
  valorLabel: string;
  subLabel: string;
  tono?: "gold" | "warning" | "danger";
}) {
  const listo = useEntrada();
  const pct = Math.min(100, Math.max(0, porcentaje));
  const RADIO = 80;
  const GROSOR = 16;
  const CIRC = 2 * Math.PI * RADIO;
  const colorTrazo = tono === "danger" ? "#ef4444" : tono === "warning" ? "#f59e0b" : "var(--color-gold)";

  return (
    <div className="relative mx-auto h-52 w-52">
      <svg viewBox="0 0 200 200" className="h-full w-full -rotate-90">
        <circle cx="100" cy="100" r={RADIO} fill="none" stroke="var(--color-line)" strokeWidth={GROSOR} />
        <circle
          cx="100"
          cy="100"
          r={RADIO}
          fill="none"
          stroke={colorTrazo}
          strokeWidth={GROSOR}
          strokeLinecap="round"
          strokeDasharray={CIRC}
          strokeDashoffset={listo ? CIRC - (pct / 100) * CIRC : CIRC}
          className="transition-[stroke-dashoffset] duration-[1100ms] ease-out"
        />
      </svg>
      <div className="pointer-events-none absolute inset-0 flex flex-col items-center justify-center">
        <span className="font-display text-4xl text-ink">{valorLabel}</span>
        <span className="mt-1 max-w-[8rem] text-center text-xs leading-snug text-ink-soft">{subLabel}</span>
      </div>
    </div>
  );
}

/** Barra horizontal con extremo redondeado y crecimiento animado, para listas cortas. */
export function BarraAnimada({
  etiqueta,
  valor,
  total,
  color,
}: {
  etiqueta: string;
  valor: number;
  total: number;
  color: string;
}) {
  const listo = useEntrada();
  const pct = total > 0 ? Math.round((valor / total) * 100) : 0;
  return (
    <div>
      <div className="flex items-center justify-between text-sm">
        <span className="text-ink-soft">{etiqueta}</span>
        <span className="font-medium text-ink">{valor}</span>
      </div>
      <div className="mt-1.5 h-2.5 overflow-hidden rounded-full bg-ink/8">
        <div
          className="h-full rounded-full transition-[width] duration-700 ease-out"
          style={{ width: listo ? `${pct}%` : "0%", backgroundColor: color }}
        />
      </div>
    </div>
  );
}
