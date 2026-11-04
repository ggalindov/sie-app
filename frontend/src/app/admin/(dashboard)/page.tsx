"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { ArrowRight, ArrowUpRight, Envelope, Article, Megaphone } from "@phosphor-icons/react";
import { toast } from "sonner";
import {
  listarSolicitudes,
  listarArticulosAdmin,
  listarSuscriptoresMarketing,
  listarConfiguracionEnvios,
  actualizarConfiguracionEnvio,
  ApiError,
  type Solicitud,
  type ConfiguracionEnvio,
} from "@/lib/admin-api";
import { useAuth } from "@/lib/auth-context";
import { AdminPageHeader, AdminCard, Badge, AdminLoader } from "@/components/admin/ui";

function formatearFecha(iso: string) {
  return new Date(iso).toLocaleDateString("es-CO", { day: "numeric", month: "short", year: "numeric" });
}

export default function ResumenPage() {
  const { sesion } = useAuth();
  const [solicitudes, setSolicitudes] = useState<Solicitud[] | null>(null);
  const [totalArticulos, setTotalArticulos] = useState<number | null>(null);
  const [totalSuscriptores, setTotalSuscriptores] = useState<number | null>(null);
  // Pedido explícito del usuario: "implementa en el dashboard principal del administrador
  // un boton para apagar los envios de correo, apagar los envios de whatsapp (boton
  // independiente notificacones, boletin, marketing entre otros)".
  const [configuracionEnvios, setConfiguracionEnvios] = useState<ConfiguracionEnvio[] | null>(null);

  useEffect(() => {
    listarSolicitudes().then(setSolicitudes).catch(() => setSolicitudes([]));
    listarArticulosAdmin().then((a) => setTotalArticulos(a.length)).catch(() => setTotalArticulos(0));
    // Solo ADMIN_GENERAL tiene acceso a este endpoint (ver marketing/page.tsx): para un
    // ABOGADO la llamada devolvía 403 y el catch mostraba "0 suscriptores", indistinguible
    // de que de verdad no hubiera ninguno.
    if (sesion?.rol === "ADMIN_GENERAL") {
      listarSuscriptoresMarketing().then((s) => setTotalSuscriptores(s.length)).catch(() => setTotalSuscriptores(0));
      listarConfiguracionEnvios().then(setConfiguracionEnvios).catch(() => {});
    }
  }, [sesion]);

  async function onAlternarEnvio(clave: string, activoActual: boolean) {
    const nuevo = !activoActual;
    setConfiguracionEnvios((prev) =>
      prev ? prev.map((c) => (c.clave === clave ? { ...c, activo: nuevo } : c)) : prev,
    );
    try {
      await actualizarConfiguracionEnvio(clave, nuevo);
      toast.success(nuevo ? "Envío activado." : "Envío desactivado.");
    } catch (err) {
      // Revierte el cambio optimista si el backend lo rechazó.
      setConfiguracionEnvios((prev) =>
        prev ? prev.map((c) => (c.clave === clave ? { ...c, activo: activoActual } : c)) : prev,
      );
      toast.error(err instanceof ApiError ? err.message : "No se pudo actualizar la configuración de envíos.");
    }
  }

  const nuevas = solicitudes?.filter((s) => s.estado === "NUEVO").length ?? null;
  const contactadas = solicitudes?.filter((s) => s.estado === "CONTACTADO").length ?? null;
  const recientes = solicitudes?.slice(0, 5) ?? [];

  return (
    <div>
      <AdminPageHeader
        title={`Hola, ${(sesion?.nombre || "Administrador").split(" ")[0]}`}
        description="Resumen de la actividad reciente."
      />

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard icono={Envelope} etiqueta="Solicitudes nuevas" valor={nuevas} href="/admin/solicitudes?estado=NUEVO" />
        <StatCard icono={Envelope} etiqueta="En contacto" valor={contactadas} href="/admin/solicitudes?estado=CONTACTADO" />
        <StatCard icono={Article} etiqueta="Artículos" valor={totalArticulos} href="/admin/articulos" />
        {sesion?.rol === "ADMIN_GENERAL" && (
          <StatCard icono={Megaphone} etiqueta="Suscriptores marketing" valor={totalSuscriptores} href="/admin/marketing" />
        )}
      </div>

      {sesion?.rol === "ADMIN_GENERAL" && configuracionEnvios && (
        <div className="mt-10">
          <div className="mb-4">
            <h2 className="font-display text-lg text-ink">Interruptores de envío</h2>
            <p className="mt-1 text-sm text-ink-soft">
              Apaga en caliente cualquier categoría de envío automático, sin reiniciar el sistema.
            </p>
          </div>
          <AdminCard>
            <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
              {(
                [
                  { titulo: "Notificaciones", correo: "NOTIFICACIONES_CORREO", whatsapp: "NOTIFICACIONES_WHATSAPP" },
                  { titulo: "Reporte periódico", correo: "REPORTE_PERIODICO_CORREO", whatsapp: "REPORTE_PERIODICO_WHATSAPP" },
                  { titulo: "Boletín judicial", correo: "BOLETIN_JUDICIAL_CORREO", whatsapp: "BOLETIN_JUDICIAL_WHATSAPP" },
                  { titulo: "Marketing", correo: "MARKETING_CORREO", whatsapp: "MARKETING_WHATSAPP" },
                  // Pedido explícito del usuario (2026-11-04): "deja en el panel principal un
                  // boton para apagar o prender" -- correo automático al despacho judicial
                  // solicitando el documento de una actuación que ningún canal pudo resolver
                  // (ver CorreoDespachoJudicialService). Sin variante de WhatsApp: un despacho
                  // judicial nunca recibe WhatsApp.
                  { titulo: "Correo a despachos judiciales", correo: "CORREO_DESPACHO_JUDICIAL" },
                ] as { titulo: string; correo: string; whatsapp?: string }[]
              ).map((grupo) => (
                <div key={grupo.titulo} className="rounded-xl border border-line p-4">
                  <p className="text-sm font-medium text-ink">{grupo.titulo}</p>
                  <div className="mt-3 space-y-2">
                    {[grupo.correo, grupo.whatsapp].filter((clave): clave is string => Boolean(clave)).map((clave) => {
                      const item = configuracionEnvios.find((c) => c.clave === clave);
                      if (!item) return null;
                      return (
                        <button
                          key={clave}
                          type="button"
                          onClick={() => onAlternarEnvio(item.clave, item.activo)}
                          className="flex w-full items-center justify-between gap-2 rounded-lg bg-ink/[0.03] px-3 py-2 text-left text-xs transition-colors hover:bg-ink/[0.06]"
                        >
                          <span className="text-ink-soft">{clave.endsWith("WHATSAPP") ? "WhatsApp" : "Correo"}</span>
                          <span
                            className={`relative inline-flex h-5 w-9 shrink-0 items-center rounded-full transition-colors ${
                              item.activo ? "bg-emerald-500" : "bg-ink/20"
                            }`}
                          >
                            <span
                              className={`inline-block h-4 w-4 transform rounded-full bg-white shadow transition-transform ${
                                item.activo ? "translate-x-4" : "translate-x-0.5"
                              }`}
                            />
                          </span>
                        </button>
                      );
                    })}
                  </div>
                </div>
              ))}
            </div>
          </AdminCard>
        </div>
      )}

      <div className="mt-10">
        <div className="mb-4 flex items-center justify-between">
          <h2 className="font-display text-lg text-ink">Solicitudes recientes</h2>
          <Link href="/admin/solicitudes" className="flex items-center gap-1 text-sm font-medium text-gold-deep">
            Ver todas <ArrowRight className="h-3.5 w-3.5" weight="bold" />
          </Link>
        </div>

        <AdminCard className="p-0">
          {solicitudes === null ? (
            <AdminLoader className="py-10" />
          ) : recientes.length === 0 ? (
            <p className="p-6 text-sm text-ink-soft">Aún no hay solicitudes.</p>
          ) : (
            <ul className="divide-y divide-line">
              {recientes.map((s) => (
                <li key={s.id} className="flex items-center justify-between gap-4 p-5">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium text-ink">{s.nombre}</p>
                    <p className="truncate text-xs text-ink-soft">{s.mensaje}</p>
                  </div>
                  <div className="flex shrink-0 items-center gap-3">
                    <Badge tone={s.estado === "NUEVO" ? "gold" : s.estado === "CONTACTADO" ? "warning" : "neutral"}>
                      {s.estado}
                    </Badge>
                    <span className="text-xs text-ink-soft">{formatearFecha(s.fechaCreacion)}</span>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </AdminCard>
      </div>
    </div>
  );
}

function StatCard({
  icono: Icono,
  etiqueta,
  valor,
  href,
}: {
  icono: typeof Envelope;
  etiqueta: string;
  valor: number | null;
  href: string;
}) {
  return (
    <Link
      href={href}
      className="group relative overflow-hidden rounded-2xl bg-surface p-6 ring-1 ring-line transition-all duration-300 hover:-translate-y-0.5 hover:ring-gold/30 hover:shadow-[0_16px_32px_-20px_rgba(20,19,15,0.35)]"
    >
      <div className="flex items-start justify-between">
        <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-gold-pale/50 text-gold-deep transition-colors group-hover:bg-gold group-hover:text-ink-fixed">
          <Icono weight="light" className="h-5 w-5" />
        </span>
        <ArrowUpRight
          weight="bold"
          className="h-4 w-4 text-ink-soft/40 opacity-0 transition-all duration-300 group-hover:translate-x-0.5 group-hover:-translate-y-0.5 group-hover:text-gold-deep group-hover:opacity-100"
        />
      </div>
      <p className="mt-4 font-display text-3xl text-ink">{valor ?? "..."}</p>
      <p className="mt-1 text-sm text-ink-soft">{etiqueta}</p>
    </Link>
  );
}
