"use client";

import { useEffect, useState } from "react";
import { toast } from "sonner";
import { PaperPlaneTilt } from "@phosphor-icons/react";
import {
  listarPqrs,
  responderPqrs,
  cambiarEstadoPqrs,
  ApiError,
  type PqrsAdmin,
  type EstadoPqrs,
  type TipoPqrs,
} from "@/lib/admin-api";
import { AdminPageHeader, AdminButton, AdminCard, Badge, EmptyState, AdminLoader } from "@/components/admin/ui";

function formatearFecha(iso: string) {
  return new Date(iso).toLocaleDateString("es-CO", { day: "numeric", month: "short", year: "numeric" });
}

const TIPO_VISIBLE: Record<TipoPqrs, string> = {
  PETICION: "Petición",
  QUEJA: "Queja",
  RECLAMO: "Reclamo",
  SUGERENCIA: "Sugerencia",
};

const ESTADO_TONO: Record<EstadoPqrs, "neutral" | "gold" | "success"> = {
  RECIBIDO: "neutral",
  EN_TRAMITE: "gold",
  RESUELTO: "success",
  CERRADO: "neutral",
};

const filtros: { label: string; valor: EstadoPqrs | "TODOS" }[] = [
  { label: "Recibidas", valor: "RECIBIDO" },
  { label: "En trámite", valor: "EN_TRAMITE" },
  { label: "Resueltas", valor: "RESUELTO" },
  { label: "Cerradas", valor: "CERRADO" },
  { label: "Todas", valor: "TODOS" },
];

export default function PqrsAdminPage() {
  const [items, setItems] = useState<PqrsAdmin[] | null>(null);
  const [filtro, setFiltro] = useState<EstadoPqrs | "TODOS">("RECIBIDO");
  const [respondiendoId, setRespondiendoId] = useState<number | null>(null);
  const [borrador, setBorrador] = useState("");
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    cargar();
  }, []);

  function cargar() {
    listarPqrs()
      .then(setItems)
      .catch(() => toast.error("No se pudieron cargar las PQRS."));
  }

  async function onCambiarEstado(id: number, nuevoEstado: EstadoPqrs) {
    try {
      const actualizado = await cambiarEstadoPqrs(id, nuevoEstado);
      setItems((prev) => prev?.map((p) => (p.id === id ? actualizado : p)) ?? null);
      toast.success("Estado actualizado.");
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo actualizar el estado.");
    }
  }

  function abrirRespuesta(item: PqrsAdmin) {
    setRespondiendoId(item.id);
    setBorrador(item.respuesta ?? "");
  }

  async function onResponder(id: number) {
    if (!borrador.trim()) return;
    setEnviando(true);
    try {
      const actualizado = await responderPqrs(id, borrador.trim());
      setItems((prev) => prev?.map((p) => (p.id === id ? actualizado : p)) ?? null);
      toast.success("Respuesta enviada al correo del remitente.");
      setRespondiendoId(null);
      setBorrador("");
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo enviar la respuesta.");
    } finally {
      setEnviando(false);
    }
  }

  const filtrados = items?.filter((p) => filtro === "TODOS" || p.estado === filtro) ?? null;

  return (
    <div>
      <AdminPageHeader
        title="PQRS"
        description="Peticiones, quejas, reclamos y sugerencias radicadas desde el sitio público."
      />

      <div className="flex flex-wrap gap-2">
        {filtros.map((f) => (
          <button
            key={f.valor}
            onClick={() => setFiltro(f.valor)}
            className={`rounded-full px-4 py-2 text-sm transition-colors ${
              filtro === f.valor ? "bg-ink text-paper" : "bg-ink/5 text-ink-soft hover:bg-ink/10"
            }`}
          >
            {f.label}
          </button>
        ))}
      </div>

      {filtrados === null ? (
        <AdminLoader />
      ) : filtrados.length === 0 ? (
        <EmptyState title="No hay PQRS en esta categoría" />
      ) : (
        <div className="mt-6 space-y-3">
          {filtrados.map((p) => (
            <AdminCard key={p.id}>
              <div className="flex flex-wrap items-start justify-between gap-4">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="font-mono text-xs font-bold tracking-wider text-ink">{p.radicado}</span>
                    <Badge tone="gold">{TIPO_VISIBLE[p.tipo]}</Badge>
                    <Badge tone={ESTADO_TONO[p.estado]}>{p.estado.replace("_", " ")}</Badge>
                  </div>
                  <p className="mt-2 font-medium text-ink">{p.asunto}</p>
                  <p className="mt-1 text-xs text-ink-soft">
                    {p.nombre} · {p.correo}
                    {p.telefono ? ` · ${p.telefono}` : ""} · {formatearFecha(p.fechaCreacion)}
                  </p>
                  <p className="mt-3 max-w-2xl whitespace-pre-line text-sm leading-relaxed text-ink">{p.descripcion}</p>

                  {p.respuesta && (
                    <div className="mt-4 rounded-xl border-l-4 border-gold bg-gold-pale/20 p-4">
                      <p className="text-xs font-semibold uppercase tracking-wide text-gold-deep">
                        Respondido por {p.respondidoPor ?? "el equipo"}
                        {p.fechaRespuesta ? ` · ${formatearFecha(p.fechaRespuesta)}` : ""}
                      </p>
                      <p className="mt-1.5 whitespace-pre-line text-sm text-ink">{p.respuesta}</p>
                    </div>
                  )}

                  {respondiendoId === p.id && (
                    <div className="mt-4 space-y-2">
                      <textarea
                        value={borrador}
                        onChange={(e) => setBorrador(e.target.value)}
                        rows={4}
                        maxLength={3000}
                        placeholder="Escribe la respuesta que recibirá el remitente por correo"
                        className="w-full resize-none rounded-xl border border-line bg-surface px-4 py-2.5 text-sm text-ink focus:border-gold-deep focus:outline-none"
                      />
                      <div className="flex gap-2">
                        <AdminButton onClick={() => onResponder(p.id)} disabled={enviando || !borrador.trim()}>
                          <PaperPlaneTilt weight="fill" className="h-4 w-4" />
                          {enviando ? "Enviando" : "Enviar respuesta"}
                        </AdminButton>
                        <AdminButton variant="ghost" onClick={() => setRespondiendoId(null)}>
                          Cancelar
                        </AdminButton>
                      </div>
                    </div>
                  )}
                </div>

                {respondiendoId !== p.id && (
                  <div className="flex shrink-0 flex-wrap gap-2">
                    {p.estado === "RECIBIDO" && (
                      <AdminButton variant="secondary" onClick={() => onCambiarEstado(p.id, "EN_TRAMITE")}>
                        Marcar en trámite
                      </AdminButton>
                    )}
                    {p.estado !== "RESUELTO" && (
                      <AdminButton onClick={() => abrirRespuesta(p)}>Responder</AdminButton>
                    )}
                    {p.estado !== "CERRADO" && (
                      <AdminButton variant="ghost" onClick={() => onCambiarEstado(p.id, "CERRADO")}>
                        Cerrar
                      </AdminButton>
                    )}
                  </div>
                )}
              </div>
            </AdminCard>
          ))}
        </div>
      )}
    </div>
  );
}
