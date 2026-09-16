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

const NOMBRES_MESES: Record<string, { corto: string; largo: string }> = {
  "01": { corto: "Ene", largo: "Enero" },
  "02": { corto: "Feb", largo: "Febrero" },
  "03": { corto: "Mar", largo: "Marzo" },
  "04": { corto: "Abr", largo: "Abril" },
  "05": { corto: "May", largo: "Mayo" },
  "06": { corto: "Jun", largo: "Junio" },
  "07": { corto: "Jul", largo: "Julio" },
  "08": { corto: "Ago", largo: "Agosto" },
  "09": { corto: "Sep", largo: "Septiembre" },
  "10": { corto: "Oct", largo: "Octubre" },
  "11": { corto: "Nov", largo: "Noviembre" },
  "12": { corto: "Dic", largo: "Diciembre" },
};

export type PuntoHistoricoVisitante = {
  mesClave: string; // '2026-04'
  etiquetaCorta: string; // 'Abr 26'
  nombreCompleto: string; // 'Abril 2026'
  conteo: number;
  crecimientoPct: number | null;
  esActual: boolean;
};

/**
 * Gráfica interactiva X-Y con curva de crecimiento exponencial, ejes graduados,
 * gradiente de área, puntos interactivos y persistencia visual mes a mes.
 */
export function GraficaExponencialXY({
  historico,
  visitantesMesActual,
}: {
  historico?: Record<string, number>;
  visitantesMesActual?: number;
}) {
  const listo = useEntrada();
  const [activo, setActivo] = useState<number | null>(null);

  // Procesar y ordenar los puntos cronológicamente con datos 100% reales
  const ahoraAnioMes = new Date().toISOString().slice(0, 7);
  let entradas = Object.entries(historico ?? {}).sort((a, b) => a[0].localeCompare(b[0]));

  if (entradas.length === 0 && visitantesMesActual !== undefined) {
    entradas = [[ahoraAnioMes, visitantesMesActual]];
  }

  const puntos: PuntoHistoricoVisitante[] = entradas.map(([clave, valor], idx) => {
    const partes = clave.split("-");
    const mesNum = partes[1] || "01";
    const anio = partes[0] || "2026";
    const infoMes = NOMBRES_MESES[mesNum] ?? { corto: mesNum, largo: `Mes ${mesNum}` };
    const anterior = idx > 0 ? entradas[idx - 1][1] : null;
    const crecimientoPct =
      anterior && anterior > 0 ? Math.round(((valor - anterior) / anterior) * 100) : null;
    const esActual = idx === entradas.length - 1 || clave === ahoraAnioMes;

    return {
      mesClave: clave,
      etiquetaCorta: `${infoMes.corto} '${anio.slice(2)}`,
      nombreCompleto: `${infoMes.largo} ${anio}`,
      conteo: valor,
      crecimientoPct,
      esActual,
    };
  });

  // Geometría y escala SVG balanceada
  const PADDING_LEFT = 52;
  const PADDING_RIGHT = 28;
  const PADDING_TOP = 20;
  const PADDING_BOTTOM = 30;
  const TOTAL_WIDTH = 680;
  const TOTAL_HEIGHT = 180;
  const GRAPH_WIDTH = TOTAL_WIDTH - PADDING_LEFT - PADDING_RIGHT;
  const GRAPH_HEIGHT = TOTAL_HEIGHT - PADDING_TOP - PADDING_BOTTOM;

  const maxVal = Math.max(...puntos.map((p) => p.conteo), 10);
  const niceMax = maxVal <= 50 ? 50 : maxVal <= 100 ? 100 : Math.ceil(maxVal / 100) * 100;
  const yTicks = [
    0,
    Math.round(niceMax * 0.5),
    niceMax,
  ];

  const coords = puntos.map((p, i) => {
    const x =
      puntos.length > 1
        ? PADDING_LEFT + (i / (puntos.length - 1)) * GRAPH_WIDTH
        : PADDING_LEFT + GRAPH_WIDTH / 2;
    const y = PADDING_TOP + GRAPH_HEIGHT - (p.conteo / niceMax) * GRAPH_HEIGHT;
    return { x, y, p };
  });

  // Construcción de la curva para emular la suavidad del crecimiento real
  let pathD = "";
  if (coords.length === 1) {
    const c = coords[0];
    pathD = `M ${PADDING_LEFT} ${c.y.toFixed(1)} L ${TOTAL_WIDTH - PADDING_RIGHT} ${c.y.toFixed(1)}`;
  } else if (coords.length > 1) {
    pathD = `M ${coords[0].x.toFixed(1)} ${coords[0].y.toFixed(1)}`;
    for (let i = 0; i < coords.length - 1; i++) {
      const c = coords[i];
      const n = coords[i + 1];
      const dx = n.x - c.x;
      const cp1x = c.x + dx * 0.45;
      const cp1y = c.y;
      const cp2x = n.x - dx * 0.32;
      const cp2y = n.y;
      pathD += ` C ${cp1x.toFixed(1)} ${cp1y.toFixed(1)}, ${cp2x.toFixed(1)} ${cp2y.toFixed(1)}, ${n.x.toFixed(1)} ${n.y.toFixed(1)}`;
    }
  }

  const areaD =
    coords.length > 1
      ? `${pathD} L ${coords[coords.length - 1].x.toFixed(1)} ${(PADDING_TOP + GRAPH_HEIGHT).toFixed(1)} L ${coords[0].x.toFixed(1)} ${(PADDING_TOP + GRAPH_HEIGHT).toFixed(1)} Z`
      : coords.length === 1
        ? `${pathD} L ${TOTAL_WIDTH - PADDING_RIGHT} ${(PADDING_TOP + GRAPH_HEIGHT).toFixed(1)} L ${PADDING_LEFT} ${(PADDING_TOP + GRAPH_HEIGHT).toFixed(1)} Z`
        : "";

  const puntoActivo = activo !== null ? coords[activo] : null;

  return (
    <div className="relative w-full">
      {/* Vista SVG de la gráfica con altura balanceada y nítida */}
      <div className="relative overflow-visible">
        <svg
          viewBox={`0 0 ${TOTAL_WIDTH} ${TOTAL_HEIGHT}`}
          className="w-full h-44 sm:h-52 select-none overflow-visible"
        >
          <defs>
            {/* Gradiente de relleno exponencial dorado */}
            <linearGradient id="exp-gradiente-gold" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#D9A925" stopOpacity="0.38" />
              <stop offset="50%" stopColor="#D9A925" stopOpacity="0.14" />
              <stop offset="90%" stopColor="#D9A925" stopOpacity="0.02" />
              <stop offset="100%" stopColor="#D9A925" stopOpacity="0.0" />
            </linearGradient>

            {/* Filtro de brillo sutil en la curva */}
            <filter id="exp-glow" x="-20%" y="-20%" width="140%" height="140%">
              <feGaussianBlur stdDeviation="2.5" result="blur" />
              <feMerge>
                <feMergeNode in="blur" />
                <feMergeNode in="SourceGraphic" />
              </feMerge>
            </filter>
          </defs>

          {/* Líneas horizontales de guía (Eje Y) */}
          {yTicks.map((tickVal) => {
            const yPos = PADDING_TOP + GRAPH_HEIGHT - (tickVal / niceMax) * GRAPH_HEIGHT;
            return (
              <g key={tickVal}>
                <line
                  x1={PADDING_LEFT}
                  y1={yPos}
                  x2={TOTAL_WIDTH - PADDING_RIGHT}
                  y2={yPos}
                  stroke="var(--color-line)"
                  strokeDasharray="4 4"
                  strokeOpacity="0.6"
                />
                <text
                  x={PADDING_LEFT - 10}
                  y={yPos + 4}
                  textAnchor="end"
                  className="fill-ink-soft text-[11px] font-mono select-none"
                >
                  {tickVal >= 1000 ? `${(tickVal / 1000).toFixed(1)}k` : tickVal}
                </text>
              </g>
            );
          })}

          {/* Línea base del Eje X */}
          <line
            x1={PADDING_LEFT}
            y1={PADDING_TOP + GRAPH_HEIGHT}
            x2={TOTAL_WIDTH - PADDING_RIGHT}
            y2={PADDING_TOP + GRAPH_HEIGHT}
            stroke="var(--color-line)"
            strokeWidth="1.2"
          />

          {/* Área sombreada bajo la curva con transición de entrada */}
          {areaD && (
            <path
              d={areaD}
              fill="url(#exp-gradiente-gold)"
              className="transition-opacity duration-1000 ease-out"
              style={{ opacity: listo ? 1 : 0 }}
            />
          )}

          {/* Trazo de la curva exponencial suave */}
          {pathD && (
            <path
              d={pathD}
              fill="none"
              stroke="#D9A925"
              strokeWidth="3.2"
              strokeLinecap="round"
              strokeLinejoin="round"
              filter="url(#exp-glow)"
              className="transition-all duration-1000 ease-out"
              style={{
                strokeDasharray: listo ? "2000" : "0 2000",
                transitionProperty: "stroke-dasharray, opacity",
              }}
            />
          )}

          {/* Etiquetas del Eje X (Meses) y columnas interactivas */}
          {coords.map((c, i) => {
            const esSeleccionado = activo === i;
            return (
              <g key={c.p.mesClave}>
                {/* Línea vertical de guía al pasar el cursor */}
                {esSeleccionado && (
                  <line
                    x1={c.x}
                    y1={PADDING_TOP}
                    x2={c.x}
                    y2={PADDING_TOP + GRAPH_HEIGHT}
                    stroke="#D9A925"
                    strokeWidth="1.2"
                    strokeDasharray="3 3"
                    strokeOpacity="0.8"
                  />
                )}

                {/* Texto del mes en Eje X */}
                <text
                  x={c.x}
                  y={PADDING_TOP + GRAPH_HEIGHT + 19}
                  textAnchor="middle"
                  className={`text-[11px] sm:text-xs transition-colors select-none ${
                    esSeleccionado
                      ? "fill-gold-deep font-semibold"
                      : c.p.esActual
                        ? "fill-ink font-semibold"
                        : "fill-ink-soft"
                  }`}
                >
                  {c.p.etiquetaCorta}
                </text>

                {/* Anillo de pulso animado en el mes actual */}
                {c.p.esActual && (
                  <circle
                    cx={c.x}
                    cy={c.y}
                    r="9"
                    fill="none"
                    stroke="#D9A925"
                    strokeWidth="1.5"
                    className="animate-ping opacity-60 pointer-events-none"
                  />
                )}

                {/* Punto / Nodo en la curva */}
                <circle
                  cx={c.x}
                  cy={c.y}
                  r={esSeleccionado ? 5.5 : c.p.esActual ? 4.5 : 3.5}
                  fill="var(--color-paper, #ffffff)"
                  stroke={c.p.esActual ? "#D9A925" : "#c29722"}
                  strokeWidth={esSeleccionado ? 2.5 : 2}
                  className="cursor-pointer transition-all duration-200"
                />

                {/* Área invisible amplia para facilitar hover con mouse o toque */}
                <rect
                  x={c.x - (GRAPH_WIDTH / (coords.length * 2))}
                  y={PADDING_TOP}
                  width={GRAPH_WIDTH / coords.length}
                  height={GRAPH_HEIGHT + PADDING_BOTTOM}
                  fill="transparent"
                  className="cursor-pointer"
                  onMouseEnter={() => setActivo(i)}
                  onMouseLeave={() => setActivo(null)}
                />
              </g>
            );
          })}
        </svg>

        {/* Tooltip interactivo flotante legible */}
        {puntoActivo && (
          <div
            className="pointer-events-none absolute z-20 flex -translate-x-1/2 flex-col items-center transition-all duration-150"
            style={{
              left: `${(puntoActivo.x / TOTAL_WIDTH) * 100}%`,
              top: `${Math.max(2, (puntoActivo.y / TOTAL_HEIGHT) * 100 - 32)}%`,
            }}
          >
            <div className="flex flex-col items-center rounded-xl border border-gold/45 bg-paper/95 px-3 py-1.5 text-center shadow-lg backdrop-blur-md">
              <span className="text-[11px] font-medium text-ink-soft">
                {puntoActivo.p.nombreCompleto}
                {puntoActivo.p.esActual ? " • En curso" : ""}
              </span>
              <div className="flex items-baseline gap-1 mt-0.5">
                <span className="font-display text-base font-bold text-ink">
                  {puntoActivo.p.conteo.toLocaleString("es-CO")}
                </span>
                <span className="text-[10px] text-ink-soft">visitas</span>
              </div>
              {puntoActivo.p.crecimientoPct !== null && (
                <span
                  className={`text-[10.5px] font-semibold mt-0.5 ${
                    puntoActivo.p.crecimientoPct >= 0
                      ? "text-emerald-600"
                      : "text-rose-600"
                  }`}
                >
                  {puntoActivo.p.crecimientoPct >= 0 ? "▲" : "▼"} {Math.abs(puntoActivo.p.crecimientoPct)}% vs anterior
                </span>
              )}
            </div>
            {/* Flecha inferior del tooltip */}
            <div className="h-1.5 w-3 overflow-hidden">
              <div className="h-2 w-2 rotate-45 transform bg-paper border-r border-b border-gold/45" />
            </div>
          </div>
        )}
      </div>

      {/* Historial guardado mes a mes */}
      <div className="mt-4 border-t border-line/60 pt-3">
        <div className="mb-2 flex items-center justify-between">
          <p className="text-xs font-medium uppercase tracking-wider text-ink-soft">
            Histórico Mensual Guardado
          </p>
          <span className="text-xs font-mono text-ink-soft">
            {puntos.length} {puntos.length === 1 ? "mes registrado" : "meses registrados"}
          </span>
        </div>
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 md:grid-cols-6">
          {puntos.map((p) => (
            <div
              key={p.mesClave}
              className={`rounded-xl border px-3 py-2 text-center transition-colors ${
                p.esActual
                  ? "border-gold/50 bg-gold-pale/30 shadow-2xs"
                  : "border-line bg-paper/70 hover:border-gold/30 hover:bg-paper"
              }`}
            >
              <div className="flex items-center justify-center gap-1.5 text-xs text-ink-soft">
                <span className="font-medium">{p.etiquetaCorta}</span>
                {p.esActual && (
                  <span className="inline-flex h-1.5 w-1.5 rounded-full bg-emerald-500 animate-pulse" />
                )}
              </div>
              <p className="font-display text-sm font-semibold text-ink mt-0.5">
                {p.conteo.toLocaleString("es-CO")}
              </p>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

