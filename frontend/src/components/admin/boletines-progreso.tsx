"use client";

import { useEffect, useRef, useState } from "react";
import { CheckCircle, Hourglass } from "@phosphor-icons/react";
import { obtenerProgresoBoletines, type ProgresoBoletines } from "@/lib/admin-api";
import { cn } from "@/lib/utils";

// Barra permanente del ciclo de boletines (pedido explícito del usuario): siempre visible,
// muestra SOLO el avance del paso de boletines, nunca el del ciclo completo. Consulta el
// backend cada 5s; mientras el ciclo corre muestra el porcentaje real (actuaciones
// revisadas / total), fuera del ciclo muestra el resultado de la última corrida.
export function BoletinesProgreso({ onAvance }: { onAvance?: () => void }) {
  const [p, setP] = useState<ProgresoBoletines | null>(null);
  const [error, setError] = useState(false);
  const ultimasNuevos = useRef(0);

  useEffect(() => {
    let vivo = true;
    async function cargar() {
      try {
        const datos = await obtenerProgresoBoletines();
        if (!vivo) return;
        setP(datos);
        setError(false);
        if (datos.nuevos !== ultimasNuevos.current) {
          ultimasNuevos.current = datos.nuevos;
          onAvance?.();
        }
      } catch {
        if (vivo) setError(true);
      }
    }
    cargar();
    const id = setInterval(cargar, 5000);
    return () => {
      vivo = false;
      clearInterval(id);
    };
  }, [onAvance]);

  const enCurso = p?.enCurso ?? false;
  const reintentando = enCurso && p?.fase === "REINTENTOS";
  const esperandoPortal = enCurso && p?.fase === "ESPERANDO_PORTAL";
  const terminado = !enCurso && !!p?.fin && p.total > 0;
  const porcentaje = reintentando
    ? p && p.reintentosTotal > 0
      ? Math.min(100, Math.round((p.reintentosHechos * 100) / p.reintentosTotal))
      : 0
    : enCurso
      ? p?.porcentaje ?? 0
      : terminado
        ? 100
        : 0;
  const pendientes = p?.pendientes ?? 0;

  return (
    <div
      role="status"
      aria-live="polite"
      className={cn(
        "mt-6 rounded-2xl border bg-surface p-5",
        enCurso ? "border-gold/40 ring-1 ring-gold/20" : "border-line",
      )}
    >
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-2.5">
          {enCurso ? (
            <span className="relative flex h-3 w-3">
              <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-gold opacity-75" />
              <span className="relative inline-flex h-3 w-3 rounded-full bg-gold-deep" />
            </span>
          ) : terminado ? (
            <CheckCircle className="h-4 w-4 text-emerald-600" weight="fill" />
          ) : (
            <Hourglass className="h-4 w-4 text-ink-soft" weight="bold" />
          )}
          <p className="text-sm font-semibold text-ink">
            {esperandoPortal
              ? "El portal no responde: esperando para reanudar"
              : reintentando
              ? "Reintentando los que fallaron"
              : enCurso
              ? "Ciclo de boletines en curso"
              : terminado
                ? "Último ciclo de boletines completado"
                : "Ciclo de boletines en espera"}
          </p>
        </div>
        <p className="font-display text-2xl tabular-nums text-ink">{porcentaje}%</p>
      </div>

      <div
        className="mt-3 h-2.5 w-full overflow-hidden rounded-full bg-ink/10"
        role="progressbar"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={porcentaje}
        aria-label="Progreso del ciclo de boletines"
      >
        <div
          className={cn(
            "h-full rounded-full transition-all duration-700",
            enCurso ? "bg-gradient-to-r from-gold-deep to-gold" : terminado ? "bg-emerald-500" : "bg-ink/20",
          )}
          style={{ width: `${porcentaje}%` }}
        />
      </div>

      <p className="mt-2.5 text-xs text-ink-soft">
        {error
          ? "No se pudo consultar el progreso (reintentando)."
          : esperandoPortal
            ? "Publicaciones Procesales dejó de responder. El ciclo espera y vuelve a probar solo; retomará donde quedó."
            : reintentando && p
            ? `Segunda etapa: ${p.reintentosHechos} de ${p.reintentosTotal} reintentos · ${p.recuperados} recuperado${p.recuperados === 1 ? "" : "s"}`
            : p && p.total > 0
              ? `${p.revisadas} de ${p.total} actuaciones revisadas · ${p.nuevos} boletín${p.nuevos === 1 ? "" : "es"} nuevo${p.nuevos === 1 ? "" : "s"} encontrado${p.nuevos === 1 ? "" : "s"}`
              : "Corre de manera automatizada a las 5:15 a. m. (hora Colombia); aquí verás el avance en vivo cuando arranque."}
      </p>
      {pendientes > 0 && !error && (
        <p className="mt-1 text-xs font-medium text-amber-700">
          {pendientes} pendiente{pendientes === 1 ? "" : "s"} en cola: se reintentan solos hasta descargarse, no se pierden.
        </p>
      )}
    </div>
  );
}
