"use client";

import { useEffect, useState } from "react";
import { toast } from "sonner";
import {
  listarTestimonios,
  moderarTestimonio,
  ApiError,
  type TestimonioAdmin,
  type EstadoTestimonio,
} from "@/lib/admin-api";
import { AdminPageHeader, AdminButton, AdminCard, Badge, EmptyState } from "@/components/admin/ui";

function formatearFecha(iso: string) {
  return new Date(iso).toLocaleDateString("es-CO", { day: "numeric", month: "short", year: "numeric" });
}

const filtros: { label: string; valor: EstadoTestimonio | "TODOS" }[] = [
  { label: "Pendientes", valor: "PENDIENTE" },
  { label: "Aprobados", valor: "APROBADO" },
  { label: "Rechazados", valor: "RECHAZADO" },
  { label: "Todos", valor: "TODOS" },
];

export default function TestimoniosAdminPage() {
  const [testimonios, setTestimonios] = useState<TestimonioAdmin[] | null>(null);
  const [filtro, setFiltro] = useState<EstadoTestimonio | "TODOS">("PENDIENTE");

  useEffect(() => {
    cargar();
  }, []);

  function cargar() {
    listarTestimonios()
      .then(setTestimonios)
      .catch(() => toast.error("No se pudieron cargar los testimonios."));
  }

  async function onModerar(id: number, nuevoEstado: EstadoTestimonio) {
    try {
      const actualizado = await moderarTestimonio(id, nuevoEstado);
      setTestimonios((prev) => prev?.map((t) => (t.id === id ? actualizado : t)) ?? null);
      toast.success(nuevoEstado === "APROBADO" ? "Testimonio aprobado." : "Testimonio rechazado.");
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo actualizar el testimonio.");
    }
  }

  const filtrados = testimonios?.filter((t) => filtro === "TODOS" || t.estado === filtro) ?? null;

  return (
    <div>
      <AdminPageHeader
        title="Testimonios"
        description="Modera los testimonios enviados desde el sitio público antes de que se publiquen."
      />

      <div className="flex flex-wrap gap-2">
        {filtros.map((f) => (
          <button
            key={f.valor}
            onClick={() => setFiltro(f.valor)}
            className={`rounded-full px-4 py-2 text-sm transition-colors ${
              filtro === f.valor
                ? "bg-ink text-paper"
                : "bg-ink/5 text-ink-soft hover:bg-ink/10"
            }`}
          >
            {f.label}
          </button>
        ))}
      </div>

      {filtrados === null ? (
        <p className="mt-6 text-sm text-ink-soft">Cargando...</p>
      ) : filtrados.length === 0 ? (
        <EmptyState title="No hay testimonios en esta categoría" />
      ) : (
        <div className="mt-6 space-y-3">
          {filtrados.map((t) => (
            <AdminCard key={t.id}>
              <div className="flex flex-wrap items-start justify-between gap-4">
                <div>
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="font-medium text-ink">{t.nombre}</p>
                    <Badge
                      tone={
                        t.estado === "APROBADO" ? "success" : t.estado === "RECHAZADO" ? "danger" : "neutral"
                      }
                    >
                      {t.estado}
                    </Badge>
                    <span className="text-xs text-ink-soft">{"★".repeat(t.calificacion)}</span>
                  </div>
                  <p className="mt-1 text-xs text-ink-soft">
                    {[t.cargo, t.empresa].filter(Boolean).join(", ") || "Sin cargo/empresa"} ·{" "}
                    {t.correo} · {formatearFecha(t.fechaCreacion)}
                  </p>
                  <p className="mt-3 max-w-2xl text-sm leading-relaxed text-ink">{t.cita}</p>
                </div>

                {t.estado !== "APROBADO" && (
                  <AdminButton onClick={() => onModerar(t.id, "APROBADO")}>Aprobar</AdminButton>
                )}
                {t.estado !== "RECHAZADO" && (
                  <AdminButton variant="danger" onClick={() => onModerar(t.id, "RECHAZADO")}>
                    Rechazar
                  </AdminButton>
                )}
              </div>
            </AdminCard>
          ))}
        </div>
      )}
    </div>
  );
}
