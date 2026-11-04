"use client";

import { Scales } from "@phosphor-icons/react";
import type { Solicitud, TerminoCalendario } from "@/lib/admin-api";
import { cn } from "@/lib/utils";

const DIAS_SEMANA = ["Dom", "Lun", "Mar", "Mié", "Jue", "Vie", "Sáb"];

// Paleta fija (no generada dinámicamente: Tailwind necesita ver las clases literales en el
// archivo para incluirlas en el build) que diferencia visualmente a cada abogado en la vista
// de ADMIN_GENERAL -- un abogado individual solo ve sus propias reuniones (ya filtradas por
// el backend), así que para él todos los chips comparten el mismo color sin que eso
// signifique nada especial.
const PALETA_RESPONSABLE = [
  { bg: "bg-gold-pale/70", text: "text-gold-deep", dot: "bg-gold-deep" },
  { bg: "bg-emerald-100", text: "text-emerald-800", dot: "bg-emerald-600" },
  { bg: "bg-sky-100", text: "text-sky-800", dot: "bg-sky-600" },
  { bg: "bg-violet-100", text: "text-violet-800", dot: "bg-violet-600" },
  { bg: "bg-rose-100", text: "text-rose-800", dot: "bg-rose-600" },
  { bg: "bg-amber-100", text: "text-amber-800", dot: "bg-amber-600" },
] as const;

// Gap real detectado en la auditoría del plan de desarrollo: antes el término procesal solo
// se veía como una insignia dentro del modal de Seguimiento -- esto es el semáforo real en
// /admin/calendario que pidió el usuario. Deliberadamente DISTINTO de PALETA_RESPONSABLE:
// el color de un término significa "qué tan urgente es" (un hecho objetivo), no "de quién
// es" (una identidad arbitraria) -- nunca deben competir ni confundirse.
export function colorEstadoTermino(estado: TerminoCalendario["estado"]) {
  if (estado === "VENCIDO") return { bg: "bg-red-100", text: "text-red-800", dot: "bg-red-600" };
  if (estado === "POR_VENCER") return { bg: "bg-amber-100", text: "text-amber-800", dot: "bg-amber-600" };
  return { bg: "bg-emerald-100", text: "text-emerald-800", dot: "bg-emerald-600" };
}

export function colorResponsable(nombre: string | null) {
  if (!nombre) return PALETA_RESPONSABLE[0];
  let hash = 0;
  for (let i = 0; i < nombre.length; i++) hash = (hash * 31 + nombre.charCodeAt(i)) >>> 0;
  return PALETA_RESPONSABLE[hash % PALETA_RESPONSABLE.length];
}

function mismodia(a: Date, b: Date) {
  return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
}

function claveDia(d: Date) {
  return `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}`;
}

/** Cuadrícula de 6 semanas (siempre 42 días) que cubre el mes de `mes`, empezando en domingo. */
export function diasDeLaCuadricula(mes: Date): Date[] {
  const primerDiaMes = new Date(mes.getFullYear(), mes.getMonth(), 1);
  const inicio = new Date(mes.getFullYear(), mes.getMonth(), 1 - primerDiaMes.getDay());
  return Array.from({ length: 42 }, (_, i) => new Date(inicio.getFullYear(), inicio.getMonth(), inicio.getDate() + i));
}

export function CalendarioMensual({
  mes,
  eventos,
  terminos = [],
  diaSeleccionado,
  onSeleccionarDia,
  onSeleccionarTermino,
  mostrarResponsable,
}: {
  mes: Date;
  eventos: Solicitud[];
  // Gap real detectado en la auditoría del plan de desarrollo: "se vea con semáforo en
  // /admin/calendario" -- opcional para no romper ningún otro lugar que ya use este
  // componente sin pasar términos.
  terminos?: TerminoCalendario[];
  diaSeleccionado: Date | null;
  onSeleccionarDia: (dia: Date) => void;
  onSeleccionarTermino?: (termino: TerminoCalendario) => void;
  /** true para ADMIN_GENERAL (necesita distinguir de quién es cada reunión). */
  mostrarResponsable: boolean;
}) {
  const dias = diasDeLaCuadricula(mes);
  const hoy = new Date();

  const eventosPorDia = new Map<string, Solicitud[]>();
  for (const evento of eventos) {
    if (!evento.fechaCita) continue;
    const fecha = new Date(evento.fechaCita);
    const clave = claveDia(fecha);
    const lista = eventosPorDia.get(clave) ?? [];
    lista.push(evento);
    eventosPorDia.set(clave, lista);
  }
  for (const lista of eventosPorDia.values()) {
    lista.sort((a, b) => new Date(a.fechaCita!).getTime() - new Date(b.fechaCita!).getTime());
  }

  const terminosPorDia = new Map<string, TerminoCalendario[]>();
  for (const termino of terminos) {
    // fechaVencimiento es "YYYY-MM-DD" (LocalDate del backend, sin hora) -- parsear con new
    // Date() a secas lo interpretaría en UTC y podría correrse un día según el huso horario
    // del navegador, por eso se arma la fecha local a mano con los 3 números.
    const [anio, mesNum, dia] = termino.fechaVencimiento.split("-").map(Number);
    const clave = claveDia(new Date(anio, mesNum - 1, dia));
    const lista = terminosPorDia.get(clave) ?? [];
    lista.push(termino);
    terminosPorDia.set(clave, lista);
  }

  return (
    <div className="overflow-hidden rounded-2xl bg-surface ring-1 ring-line">
      <div className="grid grid-cols-7 border-b border-line bg-ink/[0.02]">
        {DIAS_SEMANA.map((dia) => (
          <div key={dia} className="px-2 py-2.5 text-center text-[11px] font-semibold uppercase tracking-[0.08em] text-ink-soft/70">
            {dia}
          </div>
        ))}
      </div>
      <div className="grid grid-cols-7">
        {dias.map((dia, i) => {
          const enMes = dia.getMonth() === mes.getMonth();
          const esHoy = mismodia(dia, hoy);
          const seleccionado = diaSeleccionado ? mismodia(dia, diaSeleccionado) : false;
          const eventosDelDia = eventosPorDia.get(claveDia(dia)) ?? [];
          const visibles = eventosDelDia.slice(0, 3);
          const restantes = eventosDelDia.length - visibles.length;
          const terminosDelDia = terminosPorDia.get(claveDia(dia)) ?? [];
          const terminosVisibles = terminosDelDia.slice(0, 2);
          const terminosRestantes = terminosDelDia.length - terminosVisibles.length;

          return (
            <button
              key={i}
              type="button"
              onClick={() => onSeleccionarDia(dia)}
              className={cn(
                "flex min-h-[92px] flex-col items-stretch gap-1 border-b border-r border-line/70 p-1.5 text-left transition-colors sm:min-h-[108px] sm:p-2",
                i % 7 === 6 && "border-r-0",
                !enMes && "bg-ink/[0.015]",
                seleccionado ? "bg-gold-pale/30" : "hover:bg-ink/[0.03]",
              )}
            >
              <span
                className={cn(
                  "inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-xs font-medium",
                  !enMes && "text-ink-soft/40",
                  enMes && !esHoy && "text-ink",
                  esHoy && "bg-gold text-ink-fixed font-semibold",
                )}
              >
                {dia.getDate()}
              </span>
              <div className="flex flex-col gap-1">
                {visibles.map((evento) => {
                  const nombresResponsables = evento.responsables.map((r) => r.nombre).join(", ");
                  const color = mostrarResponsable ? colorResponsable(evento.responsables[0]?.nombre ?? null) : colorResponsable(null);
                  const hora = new Date(evento.fechaCita!).toLocaleTimeString("es-CO", { hour: "numeric", minute: "2-digit" });
                  return (
                    <span
                      key={evento.id}
                      className={cn("truncate rounded-md px-1.5 py-0.5 text-[10.5px] font-medium leading-tight sm:text-[11px]", color.bg, color.text)}
                      title={`${hora} · ${evento.nombre} · ${evento.tipoReunion === "PRESENCIAL" ? "Presencial" : "Virtual"}${mostrarResponsable && nombresResponsables ? ` · ${nombresResponsables}` : ""}`}
                    >
                      {hora} {evento.nombre}
                    </span>
                  );
                })}
                {restantes > 0 && (
                  <span className="px-1.5 text-[10.5px] font-medium text-ink-soft">+{restantes} más</span>
                )}
                {/* Semáforo de términos procesales (gap real detectado en la auditoría del
                    plan de desarrollo) -- color por URGENCIA (rojo/ámbar/verde), nunca por
                    responsable, para no confundirse con los chips de reuniones de arriba. */}
                {terminosVisibles.map((termino) => {
                  const color = colorEstadoTermino(termino.estado);
                  return (
                    <button
                      key={termino.id}
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        onSeleccionarTermino?.(termino);
                      }}
                      className={cn(
                        "flex w-full items-center gap-1 truncate rounded-md px-1.5 py-0.5 text-left text-[10.5px] font-medium leading-tight transition-transform hover:scale-[1.02] active:scale-[0.98] sm:text-[11px]",
                        color.bg,
                        color.text,
                      )}
                      title={`${termino.nombreCliente} · ${termino.descripcion}${termino.abogadoAsignadoNombre ? ` · ${termino.abogadoAsignadoNombre}` : ""} (Clic para ver providencia o notificación oficial)`}
                    >
                      <Scales className="h-3 w-3 shrink-0" weight="bold" />
                      <span className="truncate">{termino.nombreCliente}</span>
                    </button>
                  );
                })}
                {terminosRestantes > 0 && (
                  <span className="px-1.5 text-[10.5px] font-medium text-ink-soft">+{terminosRestantes} término(s)</span>
                )}
              </div>
            </button>
          );
        })}
      </div>
    </div>
  );
}
