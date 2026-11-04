"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import {
  Scales,
  XCircle,
  CheckCircle,
  CalendarCheck,
  Gavel,
  FileX,
  FlagCheckered,
  WarningCircle,
  Clock,
} from "@phosphor-icons/react";
import {
  obtenerControlDiario,
  listarResponsables,
  type ControlDiarioProcesal,
  type Responsable,
} from "@/lib/admin-api";
import { useAuth } from "@/lib/auth-context";
import { AdminPageHeader, AdminCard, AdminLoader, EmptyState } from "@/components/admin/ui";

// Réplica del "Control diario procesal" de LUPA (pedido explícito del usuario, 2026-11-04,
// tras auditar su panel en vivo: "Procesos Nuevos(Admisiones)", "Procesos Nuevos(No
// Admisiones)", "Audiencias", "Procesos Terminados", "Vencimientos de Terminos", filtrable
// por rango de fecha) -- ver ControlDiarioProcesalResponse/ClasificadorMovimientoJudicial en
// el backend. Todos los conteos son derivados por palabras clave, nunca un hecho verificado
// (mismo criterio que el resto de "posible..." en el seguimiento judicial).
const TARJETAS: { clave: string; etiqueta: string; icono: typeof Scales; tono: string }[] = [
  { clave: "ADMISION_DEMANDA", etiqueta: "Admisiones", icono: CheckCircle, tono: "text-emerald-600 bg-emerald-50 ring-emerald-200" },
  { clave: "INADMISION_DEMANDA", etiqueta: "Inadmisiones / Rechazos", icono: XCircle, tono: "text-red-600 bg-red-50 ring-red-200" },
  { clave: "AUDIENCIA_PROGRAMADA", etiqueta: "Audiencias programadas", icono: CalendarCheck, tono: "text-blue-600 bg-blue-50 ring-blue-200" },
  { clave: "SENTENCIA_FALLO", etiqueta: "Sentencias / fallos", icono: Gavel, tono: "text-gold-deep bg-gold-pale/40 ring-gold/30" },
  { clave: "MANDAMIENTO_PAGO", etiqueta: "Mandamientos de pago", icono: FileX, tono: "text-amber-600 bg-amber-50 ring-amber-200" },
  { clave: "TERMINACION_PROCESO", etiqueta: "Procesos terminados", icono: FlagCheckered, tono: "text-slate-600 bg-slate-100 ring-slate-200" },
];

function isoFecha(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

function formatearFechaHora(iso: string | null) {
  if (!iso) return "";
  try {
    return new Date(iso).toLocaleString("es-CO", { day: "numeric", month: "short", hour: "numeric", minute: "2-digit" });
  } catch {
    return iso;
  }
}

export default function ControlDiarioPage() {
  const { sesion } = useAuth();
  const esAdmin = sesion?.rol === "ADMIN_GENERAL";

  const [desde, setDesde] = useState(() => isoFecha(new Date(Date.now() - 6 * 24 * 60 * 60 * 1000)));
  const [hasta, setHasta] = useState(() => isoFecha(new Date()));
  const [abogadoId, setAbogadoId] = useState<number | undefined>(undefined);
  const [responsables, setResponsables] = useState<Responsable[]>([]);
  const [datos, setDatos] = useState<ControlDiarioProcesal | null>(null);
  const [cargando, setCargando] = useState(false);

  const cargar = useCallback(() => {
    setCargando(true);
    obtenerControlDiario({ desde, hasta, abogadoId })
      .then(setDatos)
      .catch(() => toast.error("No se pudo cargar el control diario procesal."))
      .finally(() => setCargando(false));
  }, [desde, hasta, abogadoId]);

  useEffect(() => {
    cargar();
  }, [cargar]);

  useEffect(() => {
    if (esAdmin) {
      listarResponsables().then(setResponsables).catch(() => {});
    }
  }, [esAdmin]);

  const totalMovimientos = useMemo(
    () => (datos ? Object.values(datos.conteosPorTipo).reduce((acc, n) => acc + n, 0) : 0),
    [datos],
  );

  return (
    <div>
      <AdminPageHeader
        title="Control diario procesal"
        description="Admisiones, inadmisiones, audiencias, sentencias y terminaciones por rango de fecha -- igual que el control diario de LUPA/IUS. Todos los conteos son por palabras clave sobre el texto de la actuación, nunca un hecho verificado: confirma siempre leyendo el auto real."
      />

      <div className="mb-5 flex flex-wrap items-center gap-3">
        <div className="flex items-center gap-2">
          <input
            type="date"
            value={desde}
            max={hasta}
            onChange={(e) => setDesde(e.target.value)}
            className="rounded-full border border-line bg-surface px-4 py-2 text-sm text-ink focus:border-gold-deep focus:outline-none"
          />
          <span className="text-xs text-ink-soft">a</span>
          <input
            type="date"
            value={hasta}
            min={desde}
            max={isoFecha(new Date())}
            onChange={(e) => setHasta(e.target.value)}
            className="rounded-full border border-line bg-surface px-4 py-2 text-sm text-ink focus:border-gold-deep focus:outline-none"
          />
        </div>

        {esAdmin && (
          <select
            value={abogadoId ?? ""}
            onChange={(e) => setAbogadoId(e.target.value ? Number(e.target.value) : undefined)}
            className="rounded-full border border-line bg-surface px-4 py-2 text-sm text-ink focus:border-gold-deep focus:outline-none"
          >
            <option value="">Todo el equipo</option>
            {responsables.map((r) => (
              <option key={r.id} value={r.id}>
                {r.nombre}
              </option>
            ))}
          </select>
        )}
      </div>

      {cargando && !datos ? (
        <AdminLoader />
      ) : !datos ? (
        <EmptyState title="Sin datos" description="No se pudo cargar el control diario procesal." />
      ) : (
        <>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-7">
            {TARJETAS.map((t) => {
              const Icono = t.icono;
              return (
                <AdminCard key={t.clave} className="!p-4 text-center">
                  <span className={`mx-auto flex h-9 w-9 items-center justify-center rounded-full ring-1 ${t.tono}`}>
                    <Icono className="h-4.5 w-4.5" weight="bold" />
                  </span>
                  <p className="mt-2 text-2xl font-bold text-ink">{datos.conteosPorTipo[t.clave] ?? 0}</p>
                  <p className="text-[11px] leading-tight text-ink-soft">{t.etiqueta}</p>
                </AdminCard>
              );
            })}
            <AdminCard className="!p-4 text-center">
              <span className="mx-auto flex h-9 w-9 items-center justify-center rounded-full bg-red-50 text-red-600 ring-1 ring-red-200">
                <Clock className="h-4.5 w-4.5" weight="bold" />
              </span>
              <p className="mt-2 text-2xl font-bold text-ink">{datos.vencimientosTermino}</p>
              <p className="text-[11px] leading-tight text-ink-soft">Vencimientos de término</p>
            </AdminCard>
          </div>

          <p className="mt-4 text-xs text-ink-soft">
            {totalMovimientos} movimiento{totalMovimientos === 1 ? "" : "s"} clasificado{totalMovimientos === 1 ? "" : "s"} entre{" "}
            {new Date(desde + "T00:00:00").toLocaleDateString("es-CO", { day: "numeric", month: "long" })} y{" "}
            {new Date(hasta + "T00:00:00").toLocaleDateString("es-CO", { day: "numeric", month: "long" })}.
          </p>

          {/* Pedido explícito del usuario: réplica de la alerta de LUPA "Procesos en donde el
              abogado no asistió a la audiencia" (su "Control asistencia audiencia"). */}
          <div className="mt-8">
            <h2 className="mb-3 flex items-center gap-2 font-display text-lg text-ink">
              <WarningCircle className="h-5 w-5 text-red-600" weight="fill" />
              Posibles inasistencias a audiencia
            </h2>
            {datos.audienciasConPosibleInasistencia.length === 0 ? (
              <EmptyState title="Ninguna detectada" description="No se detectó ninguna posible inasistencia a audiencia en este rango." />
            ) : (
              <div className="space-y-3">
                {datos.audienciasConPosibleInasistencia.map((a, i) => (
                  <AdminCard key={i} className="!p-4 !border-red-200">
                    <div className="flex items-start justify-between gap-3">
                      <div className="min-w-0">
                        <p className="text-sm font-medium text-ink">{a.nombreCliente}</p>
                        {a.radicadoId && <p className="font-mono text-xs text-gold-deep">{a.radicadoId}</p>}
                        <p className="mt-1.5 text-xs leading-relaxed text-ink-soft">{a.nombreActuacion}</p>
                        {a.anotacion && <p className="mt-1 text-xs leading-relaxed text-ink-soft/80">{a.anotacion}</p>}
                      </div>
                      <div className="shrink-0 text-right">
                        {a.fechaRegistro && <p className="text-[11px] text-ink-soft">{formatearFechaHora(a.fechaRegistro)}</p>}
                        {a.abogadoAsignadoNombre && (
                          <p className="mt-1 text-[11px] font-medium text-ink">{a.abogadoAsignadoNombre}</p>
                        )}
                      </div>
                    </div>
                  </AdminCard>
                ))}
              </div>
            )}
          </div>
        </>
      )}
    </div>
  );
}
