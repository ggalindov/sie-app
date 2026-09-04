"use client";

import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { ArrowsClockwise, ChatCircleText, CheckCircle, HourglassMedium, Prohibit } from "@phosphor-icons/react";
import {
  listarCobros,
  sincronizarCobros,
  enviarRecordatoriosCobros,
  ApiError,
  type ClienteCobro,
  type TipoClienteCobro,
} from "@/lib/admin-api";
import { AdminPageHeader, AdminCard, AdminButton, Badge, NotificationBadge, EmptyState, AdminLoader } from "@/components/admin/ui";
import { useAuth } from "@/lib/auth-context";

function formatearFecha(iso: string) {
  return new Date(iso).toLocaleDateString("es-CO", { day: "numeric", month: "short", year: "numeric" });
}

const TIPOS_FILTRO: { valor: TipoClienteCobro | "TODOS"; label: string }[] = [
  { valor: "TODOS", label: "Todos" },
  { valor: "EMPRESA", label: "Empresas" },
  { valor: "PERSONA_NATURAL", label: "Personas naturales" },
];

// Separador nuevo pedido explícitamente: agrupar por el mismo estado que ya muestra la
// píldora de cada tarjeta ("Respuesta de pago pendiente" / "Respuesta de pago aprobada"),
// no solo por tipo de cliente. "Sin costo" queda fuera de ambos grupos a propósito (no es un
// estado de respuesta, es que ese cliente nunca genera cobro) -- solo aparece en "Todos".
type EstadoRespuestaPago = "TODOS" | "PENDIENTE" | "APROBADO";

const ESTADOS_FILTRO: { valor: EstadoRespuestaPago; label: string }[] = [
  { valor: "TODOS", label: "Todos" },
  { valor: "PENDIENTE", label: "Respuesta de pago pendiente" },
  { valor: "APROBADO", label: "Respuesta de pago aprobada" },
];

// Un cliente con honorarios en $0 nunca genera cobro (pedido explícito), pero igual se
// muestra en el listado -- el admin sigue queriendo verlo como cliente activo, solo no le
// llegan recordatorios.
function tieneCosto(honorarios: string | null) {
  if (!honorarios) return false;
  return /[1-9]/.test(honorarios);
}

function estadoRespuestaPago(c: ClienteCobro): Exclude<EstadoRespuestaPago, "TODOS"> | "SIN_COSTO" {
  if (!tieneCosto(c.honorarios)) return "SIN_COSTO";
  return c.pagoEsteMes ? "APROBADO" : "PENDIENTE";
}

export default function CobrosAdminPage() {
  const { sesion } = useAuth();
  const [clientes, setClientes] = useState<ClienteCobro[] | null>(null);
  const [sincronizando, setSincronizando] = useState(false);
  const [enviandoRecordatorios, setEnviandoRecordatorios] = useState(false);
  const [filtroTipo, setFiltroTipo] = useState<TipoClienteCobro | "TODOS">("TODOS");
  const [filtroEstado, setFiltroEstado] = useState<EstadoRespuestaPago>("TODOS");

  const cargar = useCallback(() => {
    listarCobros()
      .then(setClientes)
      .catch(() => toast.error("No se pudieron cargar los clientes con cobro pendiente."));
  }, []);

  useEffect(() => {
    if (sesion?.rol === "ADMIN_GENERAL") cargar();
  }, [cargar, sesion]);

  if (sesion && sesion.rol !== "ADMIN_GENERAL") {
    return (
      <EmptyState
        title="No tienes acceso a esta sección"
        description="Solo el administrador general puede ver Cobros Pendientes."
      />
    );
  }

  const clientesFiltrados =
    clientes?.filter(
      (c) =>
        (filtroTipo === "TODOS" || c.tipo === filtroTipo) &&
        (filtroEstado === "TODOS" || estadoRespuestaPago(c) === filtroEstado),
    ) ?? null;

  async function onSincronizar() {
    setSincronizando(true);
    try {
      const resumen = await sincronizarCobros();
      toast.success(
        `${resumen.clientesNuevos} cliente(s) nuevo(s), ${resumen.clientesActualizados} actualizado(s)` +
          (resumen.clientesEliminados > 0
            ? `, ${resumen.clientesEliminados} eliminado(s) (ya no están en la hoja).`
            : "."),
      );
      cargar();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo sincronizar con la hoja.");
    } finally {
      setSincronizando(false);
    }
  }

  async function onEnviarRecordatorios() {
    setEnviandoRecordatorios(true);
    toast.info(
      "Enviando recordatorios -- va uno por uno con una pausa entre cada uno para no saturar el correo. Un lote grande puede tardar varios minutos.",
    );
    try {
      const resumen = await enviarRecordatoriosCobros();
      const totalIntentado = resumen.correosEnviados + resumen.correosFallidos + resumen.whatsappEnviados + resumen.whatsappFallidos;
      if (totalIntentado === 0) {
        toast.info("No hay recordatorios pendientes por enviar este mes.");
      } else {
        toast.success(
          `${resumen.correosEnviados} correo(s) y ${resumen.whatsappEnviados} WhatsApp confirmados como enviados` +
            (resumen.clientesSinCosto > 0 ? ` (${resumen.clientesSinCosto} sin costo, omitido(s)).` : "."),
        );
        if (resumen.correosFallidos > 0 || resumen.whatsappFallidos > 0) {
          toast.error(
            `${resumen.correosFallidos} correo(s) y ${resumen.whatsappFallidos} WhatsApp fallaron (incluso tras reintentar) -- se reintentan el próximo envío.`,
          );
        }
      }
      cargar();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudieron enviar los recordatorios.");
    } finally {
      setEnviandoRecordatorios(false);
    }
  }

  return (
    <div>
      <AdminPageHeader
        title="Cobros Pendientes"
        description="Clientes activos sincronizados automáticamente desde el Google Sheets de cobros de la firma (Empresas y Personas Naturales). Cada día 1 del mes se les recuerda el pago pendiente por correo y WhatsApp, salvo quienes ya pagaron ese mes o tienen honorarios en $0."
        action={
          <div className="flex flex-wrap items-center gap-2">
            <AdminButton variant="secondary" onClick={onEnviarRecordatorios} disabled={enviandoRecordatorios}>
              <ChatCircleText className="h-4 w-4" weight="bold" />
              {enviandoRecordatorios ? "Enviando..." : "Enviar recordatorios"}
            </AdminButton>
            <AdminButton onClick={onSincronizar} disabled={sincronizando}>
              <ArrowsClockwise className={`h-4 w-4 ${sincronizando ? "admin-loader-anillo" : ""}`} weight="bold" />
              {sincronizando ? "Actualizando..." : "Actualizar desde la hoja"}
            </AdminButton>
          </div>
        }
      />

      {clientes !== null && clientes.length > 0 && (
        <>
          <div className="mt-6 flex flex-wrap gap-2">
            {TIPOS_FILTRO.map((t) => {
              const cantidad = t.valor === "TODOS" ? clientes.length : clientes.filter((c) => c.tipo === t.valor).length;
              if (t.valor !== "TODOS" && cantidad === 0) return null;
              return (
                <button
                  key={t.valor}
                  type="button"
                  onClick={() => setFiltroTipo(t.valor)}
                  className={`rounded-full px-4 py-2 text-sm transition-colors ${
                    filtroTipo === t.valor ? "bg-ink text-paper" : "bg-ink/5 text-ink-soft hover:bg-ink/10"
                  }`}
                >
                  {t.label} <span className="opacity-70">({cantidad})</span>
                </button>
              );
            })}
          </div>

          {/* Separador nuevo, pedido explícito: agrupar por estado de respuesta de pago,
              independiente del filtro por tipo de arriba (se combinan). */}
          <div className="mt-2 flex flex-wrap gap-2">
            {ESTADOS_FILTRO.map((e) => {
              const cantidad =
                e.valor === "TODOS" ? clientes.length : clientes.filter((c) => estadoRespuestaPago(c) === e.valor).length;
              if (e.valor !== "TODOS" && cantidad === 0) return null;
              return (
                <button
                  key={e.valor}
                  type="button"
                  onClick={() => setFiltroEstado(e.valor)}
                  className={`rounded-full px-4 py-2 text-sm transition-colors ${
                    filtroEstado === e.valor ? "bg-gold text-ink-fixed" : "bg-gold-pale/40 text-gold-deep hover:bg-gold-pale/60"
                  }`}
                >
                  {e.label} <span className="opacity-70">({cantidad})</span>
                </button>
              );
            })}
          </div>
        </>
      )}

      {clientes === null ? (
        <AdminLoader />
      ) : clientes.length === 0 ? (
        <EmptyState
          title="Aún no hay clientes registrados"
          description='Usa "Actualizar desde la hoja" para traer todos los clientes activos del Google Sheets de cobros.'
        />
      ) : (
        <div className="mt-6 space-y-3">
          {clientesFiltrados?.map((c) => {
            const conCosto = tieneCosto(c.honorarios);
            return (
              <AdminCard key={c.id} className="flex flex-col gap-5 lg:flex-row lg:items-center">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="font-medium text-ink">{c.nombre}</p>
                    <Badge tone="gold">{c.tipoVisible}</Badge>
                    <Badge tone="neutral">Nº {c.numeroFila}</Badge>
                  </div>
                  <p className="mt-3 text-sm text-ink-soft">
                    {c.correo ?? "Sin correo"}
                    {c.telefono && ` · ${c.telefono}`}
                    {c.cedulaNit && ` · ${c.cedulaNit}`}
                  </p>
                  {c.honorarios && (
                    <p className="mt-2 font-mono text-xs tracking-wider text-gold-deep">{c.honorarios}</p>
                  )}
                  <p className="mt-1 text-xs text-ink-soft">
                    Registrado {formatearFecha(c.fechaCreacion)}
                    {c.fechaUltimoRecordatorio && ` · Último recordatorio ${formatearFecha(c.fechaUltimoRecordatorio)}`}
                  </p>
                </div>

                {/* Separador vertical + estado de pago/respuesta al costado derecho (mismo
                    patrón que Casos): antes quedaba todo apilado a la izquierda. */}
                <div className="hidden self-stretch border-l border-line lg:block" aria-hidden="true" />
                <div className="flex shrink-0 flex-col items-start gap-2.5 lg:w-56 lg:items-end">
                  {!conCosto ? (
                    <NotificationBadge tone="neutral" icon={<Prohibit weight="bold" className="h-3.5 w-3.5" />}>
                      Sin costo
                    </NotificationBadge>
                  ) : c.pagoEsteMes ? (
                    <NotificationBadge tone="success" icon={<CheckCircle weight="bold" className="h-3.5 w-3.5" />}>
                      Respuesta de pago aprobada
                    </NotificationBadge>
                  ) : (
                    <NotificationBadge tone="warning" icon={<HourglassMedium weight="bold" className="h-3.5 w-3.5" />}>
                      Respuesta de pago pendiente
                    </NotificationBadge>
                  )}
                  {c.respondioMensaje && (
                    <NotificationBadge
                      tone={c.respondioMensaje.toLowerCase().startsWith("s") ? "success" : "danger"}
                      icon={<ChatCircleText weight="bold" className="h-3.5 w-3.5" />}
                    >
                      Respondió: {c.respondioMensaje}
                    </NotificationBadge>
                  )}
                </div>
              </AdminCard>
            );
          })}
        </div>
      )}
    </div>
  );
}
