"use client";

import { useEffect, useState } from "react";
import { toast } from "sonner";
import { listarSuscriptoresMarketing, type SuscriptorMarketing } from "@/lib/admin-api";
import { AdminPageHeader, AdminCard, EmptyState } from "@/components/admin/ui";

function formatearFecha(iso: string) {
  return new Date(iso).toLocaleDateString("es-CO", { day: "numeric", month: "short", year: "numeric" });
}

export default function MarketingPage() {
  const [suscriptores, setSuscriptores] = useState<SuscriptorMarketing[] | null>(null);

  useEffect(() => {
    listarSuscriptoresMarketing()
      .then(setSuscriptores)
      .catch(() => toast.error("No se pudieron cargar los suscriptores."));
  }, []);

  return (
    <div>
      <AdminPageHeader
        title="Marketing"
        description="Correos que aceptaron recibir novedades desde el formulario de contacto."
      />

      {suscriptores === null ? (
        <p className="text-sm text-ink-soft">Cargando...</p>
      ) : suscriptores.length === 0 ? (
        <EmptyState title="Aún no hay suscriptores" />
      ) : (
        <AdminCard className="p-0">
          <ul className="divide-y divide-line">
            {suscriptores.map((s) => (
              <li key={s.id} className="flex items-center justify-between gap-4 p-5">
                <div>
                  <p className="text-sm font-medium text-ink">{s.nombre}</p>
                  <p className="text-sm text-ink-soft">{s.correo}</p>
                </div>
                <p className="text-xs text-ink-soft">Desde {formatearFecha(s.fechaSuscripcion)}</p>
              </li>
            ))}
          </ul>
        </AdminCard>
      )}
    </div>
  );
}
