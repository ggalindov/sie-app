"use client";

import { useEffect, useState } from "react";
import { toast } from "sonner";
import { DownloadSimple, EnvelopeSimple } from "@phosphor-icons/react";
import {
  listarSuscriptoresMarketing,
  exportarSuscriptoresMarketing,
  listarBoletines,
  ApiError,
  type SuscriptorMarketing,
  type BoletinEnviado,
} from "@/lib/admin-api";
import { AdminPageHeader, AdminCard, AdminButton, EmptyState, AdminLoader } from "@/components/admin/ui";
import { useAuth } from "@/lib/auth-context";

function formatearFecha(iso: string) {
  return new Date(iso).toLocaleDateString("es-CO", { day: "numeric", month: "short", year: "numeric" });
}

function formatearFechaHora(iso: string) {
  return new Date(iso).toLocaleString("es-CO", {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export default function MarketingPage() {
  const { sesion } = useAuth();
  const [suscriptores, setSuscriptores] = useState<SuscriptorMarketing[] | null>(null);
  const [boletines, setBoletines] = useState<BoletinEnviado[] | null>(null);
  const [descargando, setDescargando] = useState(false);

  async function onDescargar() {
    setDescargando(true);
    try {
      await exportarSuscriptoresMarketing();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo descargar el Excel.");
    } finally {
      setDescargando(false);
    }
  }

  useEffect(() => {
    if (sesion?.rol === "ADMIN_GENERAL") {
      listarSuscriptoresMarketing()
        .then(setSuscriptores)
        .catch(() => toast.error("No se pudieron cargar los suscriptores."));
      listarBoletines()
        .then(setBoletines)
        .catch(() => toast.error("No se pudo cargar el historial de boletines."));
    }
  }, [sesion]);

  if (sesion && sesion.rol !== "ADMIN_GENERAL") {
    return (
      <EmptyState
        title="No tienes acceso a esta sección"
        description="Solo el administrador general ve la lista de suscriptores de marketing."
      />
    );
  }

  return (
    <div className="space-y-10">
      <div>
        <AdminPageHeader
          title="Marketing"
          description="Correos que aceptaron recibir novedades desde el formulario de contacto."
          action={
            <AdminButton
              variant="secondary"
              onClick={onDescargar}
              disabled={descargando || !suscriptores?.length}
            >
              <DownloadSimple className="h-4 w-4" weight="light" />
              {descargando ? "Generando..." : "Descargar Excel"}
            </AdminButton>
          }
        />

        {suscriptores === null ? (
          <AdminLoader />
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

      <div>
        <h2 className="font-display text-xl text-ink">Boletín automático</h2>
        <p className="mt-1 text-sm text-ink-soft">
          No hay nada que escribir aquí: apenas se publica un artículo o una noticia en Blog
          y Noticias, el sistema le avisa de inmediato a los suscriptores activos. Este es
          el historial de esos envíos.
        </p>

        {boletines === null ? (
          <AdminLoader />
        ) : boletines.length === 0 ? (
          <div className="mt-4">
            <EmptyState
              title="Aún no se ha enviado ningún boletín"
              description="Se enviará el primero apenas publiques un artículo o una noticia."
            />
          </div>
        ) : (
          <AdminCard className="mt-4 p-0">
            <ul className="divide-y divide-line">
              {boletines.map((b) => (
                <li key={b.id} className="flex items-center justify-between gap-4 p-5">
                  <div className="flex items-center gap-3">
                    <span className="flex h-9 w-9 items-center justify-center rounded-full bg-gold/10 text-gold-deep">
                      <EnvelopeSimple weight="light" className="h-4 w-4" />
                    </span>
                    <p className="text-sm text-ink">
                      {b.cantidadPublicaciones} publicación{b.cantidadPublicaciones === 1 ? "" : "es"} · enviado
                      a {b.cantidadDestinatarios} suscriptores
                    </p>
                  </div>
                  <p className="shrink-0 text-xs text-ink-soft">{formatearFechaHora(b.fechaEnvio)}</p>
                </li>
              ))}
            </ul>
          </AdminCard>
        )}
      </div>
    </div>
  );
}
