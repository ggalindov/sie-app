"use client";

import { useCallback, useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { toast } from "sonner";
import {
  Binoculars,
  MagnifyingGlass,
  UsersThree,
  Lightning,
  CheckCircle,
  XCircle,
  Copy,
  Buildings,
  MapPin,
  CalendarBlank,
  Clock,
  Spinner,
  Sparkle,
} from "@phosphor-icons/react";
import {
  listarProcesosDetectados,
  actualizarProcesoDetectado,
  crearCasoDesdeProcesoDetectado,
  ApiError,
  type ProcesoCandidato,
} from "@/lib/admin-api";
import { AdminPageHeader, AdminCard, AdminButton, EmptyState, AdminLoader } from "@/components/admin/ui";
import { BusquedaCromaModal } from "@/components/admin/busqueda-croma-modal";
import { formatearFechaBogota, formatearFechaHoraBogota } from "@/lib/fecha";

const formatearFechaSolo = formatearFechaBogota;
const formatearFechaHora = formatearFechaHoraBogota;

// El CPNU devuelve las partes como un solo string ("Demandante: X | Demandado: Y", con \r\n
// crudos dentro de cada tramo) -- esto lo separa en pares etiqueta/valor para pintarlo como
// una lista de datos, no como un párrafo plano. Si un tramo no trae ":" (pasa con roles
// atípicos, ej. "Radicación Corte: 26117" sin más contexto), se muestra entero como valor.
function parsearPartes(sujetosProcesales: string): { etiqueta: string; valor: string }[] {
  return sujetosProcesales
    .split("|")
    .map((parte) => parte.replace(/\s*\r?\n\s*/g, " ").replace(/\s+/g, " ").trim())
    .filter(Boolean)
    .map((parte) => {
      const i = parte.indexOf(":");
      if (i === -1) return { etiqueta: "", valor: parte };
      return { etiqueta: parte.slice(0, i).trim(), valor: parte.slice(i + 1).trim() };
    });
}

function copiarRadicado(radicado: string) {
  navigator.clipboard
    .writeText(radicado)
    .then(() => toast.success("Radicado copiado."))
    .catch(() => toast.error("No se pudo copiar."));
}

// Los 3 pasos del flujo completo -- pedido explícito del usuario tras la primera versión:
// "necesito algo completamente distinto y más funcional... que explique cada uno". Esto
// reemplaza el único párrafo de descripción que tenía la pantalla antes.
const PASOS = [
  {
    icono: MagnifyingGlass,
    titulo: "1. Busca por nombre",
    texto:
      "Además de vigilar los radicados que ya tenemos cargados, el sistema busca el NOMBRE de cada cliente directamente en la Rama Judicial, para encontrar procesos que la firma todavía no sabe que existen.",
  },
  {
    icono: UsersThree,
    titulo: "2. Tú confirmas",
    texto:
      "Buscar por nombre puede encontrar un homónimo real, no necesariamente tu cliente. Por eso cada hallazgo queda aquí esperando -- nunca se crea un caso sin que un abogado lo confirme.",
  },
  {
    icono: Lightning,
    titulo: "3. Un clic y queda listo",
    texto:
      "Si confirmas que sí es tu cliente, el caso se crea de inmediato con el radicado, las partes y el despacho que ya encontramos, y entra al seguimiento 24/7 como cualquier otro caso.",
  },
] as const;

export default function ProcesosDetectadosPage() {
  const router = useRouter();
  const [candidatos, setCandidatos] = useState<ProcesoCandidato[] | null>(null);
  const [procesandoId, setProcesandoId] = useState<number | null>(null);
  // Buscador 100% local (pedido explícito del usuario: "implementa buscador interno para
  // procesos nuevos") -- mismo criterio que el buscador de /admin/casos: filtra el array ya
  // cargado en memoria, sin ningún viaje al servidor. Con cientos de candidatos reales
  // (confirmado en producción: más de 600), encontrar uno a mano desplazándose es
  // impracticable sin esto.
  const [busqueda, setBusqueda] = useState("");
  const [cromaModalAbierto, setCromaModalAbierto] = useState(false);

  const cargar = useCallback(() => {
    listarProcesosDetectados()
      .then(setCandidatos)
      .catch(() => toast.error("No se pudieron cargar los procesos detectados."));
  }, []);

  useEffect(() => {
    cargar();
  }, [cargar]);

  async function onCrearCaso(c: ProcesoCandidato) {
    if (
      !window.confirm(
        `Vas a crear un caso nuevo para "${c.clienteNombre}" con el radicado ${c.llaveProceso}. ` +
          "Quedará vinculado a este cliente y el sistema empezará a hacerle seguimiento 24/7 de inmediato. ¿Confirmas?",
      )
    ) {
      return;
    }
    setProcesandoId(c.id);
    try {
      const caso = await crearCasoDesdeProcesoDetectado(c.id);
      setCandidatos((prev) => (prev ? prev.filter((x) => x.id !== c.id) : prev));
      toast.success(`Caso creado (radicado ${caso.radicadoId}) y en seguimiento.`, {
        action: { label: "Ver en Casos", onClick: () => router.push("/admin/casos") },
      });
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo crear el caso.");
    } finally {
      setProcesandoId(null);
    }
  }

  async function onDescartar(c: ProcesoCandidato) {
    setProcesandoId(c.id);
    try {
      await actualizarProcesoDetectado(c.id, "DESCARTADO");
      setCandidatos((prev) => (prev ? prev.filter((x) => x.id !== c.id) : prev));
      toast.success("Descartado: queda registrado que no era el cliente.");
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo descartar.");
    } finally {
      setProcesandoId(null);
    }
  }

  const busquedaNormalizada = busqueda.trim().toLowerCase();
  const candidatosFiltrados = (candidatos ?? []).filter((c) => {
    if (!busquedaNormalizada) return true;
    return (
      c.clienteNombre.toLowerCase().includes(busquedaNormalizada) ||
      c.llaveProceso.toLowerCase().includes(busquedaNormalizada) ||
      (c.despacho?.toLowerCase().includes(busquedaNormalizada) ?? false) ||
      (c.departamento?.toLowerCase().includes(busquedaNormalizada) ?? false) ||
      (c.sujetosProcesales?.toLowerCase().includes(busquedaNormalizada) ?? false)
    );
  });

  return (
    <div>
      {/* Pedido explícito del usuario (2026-11-01): "quita todo boton que haga que dispare
          el ciclo manualmente, el ciclo sera permanentemente automatizado" -- se quitó
          "Buscar procesos nuevos de todos los clientes" (disparaba el ciclo completo de
          VigilanciaExternaOrquestador). Corre solo, una vez al día, por el cron nocturno. */}
      <AdminPageHeader
        title="Procesos nuevos detectados"
        description="Gap real frente a Lupa Jurídica: el sistema no solo vigila los radicados ya conocidos, también busca activamente procesos nuevos de cada cliente, una vez al día a las 5:15 a. m. (hora Colombia)."
        action={
          <AdminButton onClick={() => setCromaModalAbierto(true)}>
            <Sparkle className="h-4 w-4" weight="fill" />
            Búsqueda puntual por Cédula/Nombre (Croma)
          </AdminButton>
        }
      />

      {/* Los 3 pasos del flujo -- reemplaza el párrafo único que tenía la primera versión de
          esta pantalla (pedido explícito del usuario: "que explique cada uno"). */}
      <div className="mb-8 grid gap-4 sm:grid-cols-3">
        {PASOS.map((paso) => (
          <AdminCard key={paso.titulo} className="flex flex-col gap-3">
            <span className="flex h-10 w-10 items-center justify-center rounded-full bg-gold-pale/60 text-gold-deep">
              <paso.icono className="h-5 w-5" weight="duotone" />
            </span>
            <div>
              <p className="font-display text-base text-ink">{paso.titulo}</p>
              <p className="mt-1 text-sm leading-relaxed text-ink-soft">{paso.texto}</p>
            </div>
          </AdminCard>
        ))}
      </div>

      {candidatos !== null && candidatos.length > 0 && (
        <div className="relative mb-6">
          <MagnifyingGlass weight="bold" className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-soft" />
          <input
            type="text"
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            placeholder="Buscar por cliente, radicado, despacho o departamento..."
            className="w-full rounded-full border border-line bg-surface py-2.5 pl-11 pr-4 text-sm text-ink placeholder:text-ink-soft focus:border-gold-deep focus:outline-none"
          />
        </div>
      )}

      {candidatos === null ? (
        <AdminLoader />
      ) : candidatos.length === 0 ? (
        <EmptyState
          title="No hay candidatos pendientes de revisión"
          description="El ciclo automático corre una vez al día, de madrugada -- los candidatos nuevos van a ir apareciendo aquí solos."
        />
      ) : candidatosFiltrados.length === 0 ? (
        <EmptyState title="Sin resultados" description="Ningún candidato coincide con la búsqueda actual." />
      ) : (
        <>
          <p className="mb-3 text-xs font-semibold uppercase tracking-wide text-ink-soft/70">
            {candidatosFiltrados.length} de {candidatos.length} candidato{candidatos.length === 1 ? "" : "s"} esperando revisión
          </p>
          <div className="space-y-4">
            {candidatosFiltrados.map((c) => {
              const partes = c.sujetosProcesales ? parsearPartes(c.sujetosProcesales) : [];
              const procesando = procesandoId === c.id;
              const bloqueado = procesandoId !== null;
              return (
                <AdminCard key={c.id} accent className="p-0">
                  <div className="flex flex-col gap-4 p-5 sm:flex-row sm:items-start sm:justify-between sm:gap-6">
                    <div className="min-w-0 flex-1 space-y-4">
                      {/* Identidad: a quién creemos que pertenece y el radicado exacto que el
                          CPNU devolvió -- el botón de copiar existe porque el abogado suele
                          necesitar pegar el radicado en otro lado (WhatsApp, correo) antes de
                          decidir. */}
                      <div>
                        <p className="inline-flex items-center gap-2 text-sm font-semibold text-ink">
                          <Binoculars className="h-4 w-4 shrink-0 text-gold-deep" weight="bold" />
                          {c.clienteNombre}
                        </p>
                        <button
                          type="button"
                          onClick={() => copiarRadicado(c.llaveProceso)}
                          className="mt-1 inline-flex items-center gap-1.5 rounded-md px-1.5 py-0.5 font-mono text-xs tracking-wider text-gold-deep transition-colors hover:bg-gold-pale/40"
                          title="Copiar radicado"
                        >
                          {c.llaveProceso}
                          <Copy className="h-3 w-3" weight="bold" />
                        </button>
                      </div>

                      {/* Partes del proceso, como datos etiqueta/valor (no como un párrafo
                          plano) -- mismo criterio ya usado en /admin/casos y el modal de
                          Seguimiento. */}
                      {partes.length > 0 && (
                        <div className="grid gap-x-6 gap-y-1.5 sm:grid-cols-2">
                          {partes.map((p, i) => (
                            <div key={i} className="text-xs">
                              {p.etiqueta && (
                                <span className="font-semibold uppercase tracking-wide text-ink-soft/60">{p.etiqueta}: </span>
                              )}
                              <span className="text-ink-soft">{p.valor}</span>
                            </div>
                          ))}
                        </div>
                      )}

                      {/* Metadatos del proceso -- cuatro datos cortos en vez del párrafo de
                          texto corrido que tenía la primera versión. */}
                      <div className="flex flex-wrap gap-x-5 gap-y-1.5 border-t border-line pt-3 text-xs text-ink-soft">
                        {c.despacho && (
                          <span className="inline-flex items-center gap-1.5">
                            <Buildings className="h-3.5 w-3.5 shrink-0" weight="light" />
                            {c.despacho}
                          </span>
                        )}
                        {c.departamento && (
                          <span className="inline-flex items-center gap-1.5">
                            <MapPin className="h-3.5 w-3.5 shrink-0" weight="light" />
                            {c.departamento}
                          </span>
                        )}
                        {c.fechaUltimaActuacion && (
                          <span className="inline-flex items-center gap-1.5">
                            <CalendarBlank className="h-3.5 w-3.5 shrink-0" weight="light" />
                            Último movimiento: {formatearFechaSolo(c.fechaUltimaActuacion)}
                          </span>
                        )}
                        <span className="inline-flex items-center gap-1.5 text-ink-soft/70">
                          <Clock className="h-3.5 w-3.5 shrink-0" weight="light" />
                          Detectado {formatearFechaHora(c.fechaDeteccion)}
                        </span>
                      </div>
                    </div>

                    {/* Las dos únicas decisiones posibles, cada una con su consecuencia
                        explicada debajo del botón -- pedido explícito del usuario: "deja dos
                        botones, CREAR Y ADJUNTAR AUTOMATICAMENTE EN SIE, NO (ES OTRA
                        PERSONA)". */}
                    <div className="flex shrink-0 flex-col gap-3 sm:w-64">
                      <div>
                        <AdminButton
                          onClick={() => onCrearCaso(c)}
                          disabled={bloqueado}
                          className="w-full justify-center"
                        >
                          {procesando ? (
                            <Spinner className="h-4 w-4 animate-spin" weight="bold" />
                          ) : (
                            <CheckCircle className="h-4 w-4" weight="bold" />
                          )}
                          CREAR Y ADJUNTAR AUTOMÁTICAMENTE EN SIE
                        </AdminButton>
                        <p className="mt-1.5 text-center text-[11px] leading-snug text-ink-soft/70">
                          Sí es {c.clienteNombre.split(" ")[0]}: crea el caso ya mismo y empieza a vigilarlo.
                        </p>
                      </div>
                      <div>
                        <AdminButton
                          variant="ghost"
                          onClick={() => onDescartar(c)}
                          disabled={bloqueado}
                          className="w-full justify-center"
                        >
                          <XCircle className="h-4 w-4" weight="bold" />
                          NO (ES OTRA PERSONA)
                        </AdminButton>
                        <p className="mt-1.5 text-center text-[11px] leading-snug text-ink-soft/70">
                          Es un homónimo: descarta este hallazgo sin crear nada.
                        </p>
                      </div>
                    </div>
                  </div>
                </AdminCard>
              );
            })}
          </div>
        </>
      )}

      <BusquedaCromaModal
        abierto={cromaModalAbierto}
        onClose={() => setCromaModalAbierto(false)}
        onSeleccionarRadicado={(radicado) => {
          router.push(`/admin/casos?q=${encodeURIComponent(radicado)}`);
        }}
      />
    </div>
  );
}
