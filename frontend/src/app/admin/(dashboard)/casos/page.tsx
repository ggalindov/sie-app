"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Dialog } from "@base-ui/react/dialog";
import { toast } from "sonner";
import { Plus, X } from "@phosphor-icons/react";
import {
  listarCasos,
  crearCaso,
  actualizarEtapaCaso,
  listarCategorias,
  ApiError,
  type CasoAdmin,
  type EtapaCaso,
} from "@/lib/admin-api";
import type { Categoria } from "@/lib/api";
import { AdminPageHeader, AdminCard, AdminButton, Badge, EmptyState } from "@/components/admin/ui";

const ETAPAS: EtapaCaso[] = ["RADICADO", "EN_ESTUDIO", "EN_TRAMITE", "AUDIENCIA_DILIGENCIA", "RESUELTO"];

const etapaLabel: Record<EtapaCaso, string> = {
  RADICADO: "Radicado",
  EN_ESTUDIO: "En estudio",
  EN_TRAMITE: "En trámite",
  AUDIENCIA_DILIGENCIA: "Audiencia / diligencia",
  RESUELTO: "Resuelto",
};

function tonoEtapa(etapa: EtapaCaso) {
  if (etapa === "RESUELTO") return "success" as const;
  if (etapa === "RADICADO") return "gold" as const;
  return "warning" as const;
}

function formatearFecha(iso: string) {
  return new Date(iso).toLocaleDateString("es-CO", { day: "numeric", month: "short", year: "numeric" });
}

export default function CasosAdminPage() {
  const [casos, setCasos] = useState<CasoAdmin[] | null>(null);
  const [categorias, setCategorias] = useState<Categoria[]>([]);
  const [modalAbierto, setModalAbierto] = useState(false);

  const cargar = useCallback(() => {
    listarCasos()
      .then(setCasos)
      .catch(() => toast.error("No se pudieron cargar los casos."));
  }, []);

  useEffect(() => {
    cargar();
    listarCategorias().then(setCategorias).catch(() => {});
  }, [cargar]);

  async function onCambiarEtapa(id: number, nuevaEtapa: EtapaCaso) {
    try {
      const actualizado = await actualizarEtapaCaso(id, nuevaEtapa);
      setCasos((prev) => prev?.map((c) => (c.id === id ? actualizado : c)) ?? null);
      toast.success("Etapa actualizada.");
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo actualizar la etapa.");
    }
  }

  return (
    <div>
      <AdminPageHeader
        title="Casos"
        description="Clientes y casos con consulta pública de estado por código, sin necesidad de cuenta."
        action={
          <AdminButton onClick={() => setModalAbierto(true)}>
            <Plus className="h-4 w-4" weight="bold" />
            Nuevo caso
          </AdminButton>
        }
      />

      {casos === null ? (
        <p className="text-sm text-ink-soft">Cargando...</p>
      ) : casos.length === 0 ? (
        <EmptyState title="Aún no hay casos registrados" description="Crea el primero con el botón de arriba." />
      ) : (
        <div className="space-y-3">
          {casos.map((c) => (
            <AdminCard key={c.id} className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <p className="font-medium text-ink">{c.nombreCliente}</p>
                  <Badge tone={tonoEtapa(c.etapa)}>{etapaLabel[c.etapa]}</Badge>
                  <Badge>{c.categoriaNombre}</Badge>
                </div>
                <p className="mt-1 text-sm text-ink-soft">
                  {c.correoCliente}
                  {c.telefonoCliente && ` · ${c.telefonoCliente}`}
                </p>
                <p className="mt-2 font-mono text-xs tracking-wider text-gold-deep">{c.codigoUnico}</p>
                <p className="mt-1 text-xs text-ink-soft">Creado {formatearFecha(c.fechaCreacion)}</p>
              </div>

              <select
                value={c.etapa}
                onChange={(e) => onCambiarEtapa(c.id, e.target.value as EtapaCaso)}
                className="shrink-0 rounded-full border border-line bg-paper px-3 py-2 text-xs text-ink focus:border-gold-deep focus:outline-none"
              >
                {ETAPAS.map((e) => (
                  <option key={e} value={e}>
                    {etapaLabel[e]}
                  </option>
                ))}
              </select>
            </AdminCard>
          ))}
        </div>
      )}

      <ModalNuevoCaso
        abierto={modalAbierto}
        categorias={categorias}
        onClose={() => setModalAbierto(false)}
        onCreado={(nuevo) => {
          setCasos((prev) => (prev ? [nuevo, ...prev] : [nuevo]));
          setModalAbierto(false);
        }}
      />
    </div>
  );
}

function ModalNuevoCaso({
  abierto,
  categorias,
  onClose,
  onCreado,
}: {
  abierto: boolean;
  categorias: Categoria[];
  onClose: () => void;
  onCreado: (caso: CasoAdmin) => void;
}) {
  const [enviando, setEnviando] = useState(false);

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const data = new FormData(form);
    const idCategoria = Number(data.get("idCategoria"));
    if (!idCategoria) {
      toast.error("Selecciona un tipo de caso.");
      return;
    }

    setEnviando(true);
    try {
      const nuevo = await crearCaso({
        nombreCliente: String(data.get("nombreCliente") ?? ""),
        correoCliente: String(data.get("correoCliente") ?? ""),
        telefonoCliente: String(data.get("telefonoCliente") ?? "") || undefined,
        idCategoria,
        notasInternas: String(data.get("notasInternas") ?? "") || undefined,
      });
      toast.success(`Caso creado. Código ${nuevo.codigoUnico} enviado al correo del cliente.`);
      form.reset();
      onCreado(nuevo);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo crear el caso.");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <Dialog.Root open={abierto} onOpenChange={(open) => !open && onClose()}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-ink/40 backdrop-blur-sm" />
        <Dialog.Popup className="fixed left-1/2 top-1/2 z-50 w-full max-w-md -translate-x-1/2 -translate-y-1/2 rounded-2xl bg-surface p-6 shadow-2xl ring-1 ring-line">
          <div className="flex items-center justify-between">
            <Dialog.Title className="font-display text-lg text-ink">Nuevo caso</Dialog.Title>
            <Dialog.Close className="flex h-8 w-8 items-center justify-center rounded-full text-ink-soft hover:bg-ink/5">
              <X className="h-4 w-4" />
            </Dialog.Close>
          </div>
          <Dialog.Description className="mt-1 text-sm text-ink-soft">
            Se genera un código único que se envía por correo al cliente.
          </Dialog.Description>

          <form onSubmit={onSubmit} className="mt-5 space-y-3">
            <input
              name="nombreCliente"
              required
              placeholder="Nombre del cliente"
              className="w-full rounded-xl border border-line bg-paper px-4 py-3 text-sm text-ink focus:border-gold-deep focus:outline-none"
            />
            <input
              name="correoCliente"
              type="email"
              required
              placeholder="Correo del cliente"
              className="w-full rounded-xl border border-line bg-paper px-4 py-3 text-sm text-ink focus:border-gold-deep focus:outline-none"
            />
            <input
              name="telefonoCliente"
              placeholder="Teléfono (opcional)"
              className="w-full rounded-xl border border-line bg-paper px-4 py-3 text-sm text-ink focus:border-gold-deep focus:outline-none"
            />
            <select
              name="idCategoria"
              required
              defaultValue=""
              className="w-full rounded-xl border border-line bg-paper px-4 py-3 text-sm text-ink focus:border-gold-deep focus:outline-none"
            >
              <option value="" disabled>
                Tipo de caso
              </option>
              {categorias.map((cat) => (
                <option key={cat.id} value={cat.id}>
                  {cat.nombre}
                </option>
              ))}
            </select>
            <textarea
              name="notasInternas"
              rows={3}
              placeholder="Notas internas (opcional, no las ve el cliente)"
              className="w-full rounded-xl border border-line bg-paper px-4 py-3 text-sm text-ink focus:border-gold-deep focus:outline-none"
            />

            <AdminButton type="submit" disabled={enviando} className="w-full">
              {enviando ? "Creando..." : "Crear caso"}
            </AdminButton>
          </form>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
