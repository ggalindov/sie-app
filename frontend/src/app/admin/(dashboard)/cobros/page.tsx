"use client";

import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import {
  ArrowsClockwise,
  ChatCircleText,
  Check,
  CheckCircle,
  DeviceMobile,
  EnvelopeSimple,
  HourglassMedium,
  PhoneSlash,
  Prohibit,
  Spinner,
  WhatsappLogo,
  X,
} from "@phosphor-icons/react";
import {
  listarCobros,
  sincronizarCobros,
  enviarRecordatoriosCobros,
  cambiarRespuestaCobro,
  ApiError,
  type ClienteCobro,
  type TipoClienteCobro,
} from "@/lib/admin-api";
import { AdminPageHeader, AdminCard, AdminButton, Badge, NotificationBadge, EmptyState, AdminLoader } from "@/components/admin/ui";
import { EnvioLoteProgreso } from "@/components/admin/envio-lote-progreso";
import { useAuth } from "@/lib/auth-context";

function formatearFecha(iso: string) {
  return new Date(iso).toLocaleDateString("es-CO", { day: "numeric", month: "short", year: "numeric" });
}

const TIPOS_FILTRO: { valor: TipoClienteCobro | "TODOS"; label: string }[] = [
  { valor: "TODOS", label: "Todos" },
  { valor: "EMPRESA", label: "Empresas" },
  { valor: "PERSONA_NATURAL", label: "Personas naturales" },
];

type EstadoCobroFiltro = "TODOS" | "PENDIENTE" | "RESPONDIO" | "APROBADO" | "SIN_COSTO";

const ESTADOS_FILTRO: { valor: EstadoCobroFiltro; label: string; siempreVisible?: boolean }[] = [
  { valor: "TODOS", label: "Todos", siempreVisible: true },
  { valor: "PENDIENTE", label: "Respuesta de pago pendiente", siempreVisible: true },
  { valor: "RESPONDIO", label: "Respondieron notificación", siempreVisible: true },
  { valor: "APROBADO", label: "Respuesta de pago aprobada" },
  { valor: "SIN_COSTO", label: "Sin costo" },
];

function tieneCosto(honorarios: string | null) {
  if (!honorarios) return false;
  return /[1-9]/.test(honorarios);
}

function cumpleFiltroEstado(c: ClienteCobro, filtro: EstadoCobroFiltro): boolean {
  if (filtro === "TODOS") return true;
  if (filtro === "PENDIENTE") return tieneCosto(c.honorarios) && !c.pagoEsteMes;
  if (filtro === "RESPONDIO") return Boolean(c.respondioMensaje && c.respondioMensaje.trim() !== "");
  if (filtro === "APROBADO") return tieneCosto(c.honorarios) && Boolean(c.pagoEsteMes);
  if (filtro === "SIN_COSTO") return !tieneCosto(c.honorarios);
  return true;
}

export default function CobrosAdminPage() {
  const { sesion } = useAuth();
  const [clientes, setClientes] = useState<ClienteCobro[] | null>(null);
  const [sincronizando, setSincronizando] = useState(false);
  const [enviandoRecordatorios, setEnviandoRecordatorios] = useState(false);
  const [actualizandoId, setActualizandoId] = useState<number | null>(null);
  const [filtroTipo, setFiltroTipo] = useState<TipoClienteCobro | "TODOS">("TODOS");
  const [filtroEstado, setFiltroEstado] = useState<EstadoCobroFiltro>("TODOS");

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

  async function onCambiarRespuesta(id: number, respondio: string | null, pago: boolean) {
    setActualizandoId(id);
    try {
      const act = await cambiarRespuestaCobro(id, respondio, pago);
      setClientes((prev) => (prev ? prev.map((c) => (c.id === id ? act : c)) : null));
      toast.success(
        pago
          ? "Respuesta actualizada: Pago Aprobado (SÍ)"
          : respondio
            ? "Respuesta actualizada: Rechazado (NO)"
            : "Estado restablecido a pendiente",
      );
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "Error al actualizar respuesta");
    } finally {
      setActualizandoId(null);
    }
  }


  const clientesFiltrados =
    clientes?.filter(
      (c) =>
        (filtroTipo === "TODOS" || c.tipo === filtroTipo) &&
        cumpleFiltroEstado(c, filtroEstado),
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
        if (resumen.pendientesPorLimiteDiario > 0) {
          toast.info(
            `${resumen.pendientesPorLimiteDiario} cliente(s) más quedaron pendientes por el límite diario de envíos (250/día) -- se enviarán automáticamente mañana.`,
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
        description="Clientes activos sincronizados automáticamente desde el Google Sheets de cobros de la firma (Empresas y Personas Naturales). Cada día 3 del mes se les recuerda el pago pendiente por correo y WhatsApp, salvo quienes ya pagaron ese mes o tienen honorarios en $0. Si se alcanza el límite diario de 250 mensajes, los restantes se envían automáticamente al día siguiente."
        action={
          <div className="flex flex-wrap items-center gap-2">
            <AdminButton variant="secondary" onClick={onEnviarRecordatorios} disabled={enviandoRecordatorios}>
              {enviandoRecordatorios ? (
                <>
                  <Spinner className="h-4 w-4 animate-spin" weight="bold" />
                  Enviando...
                </>
              ) : (
                <>
                  <ChatCircleText className="h-4 w-4" weight="bold" />
                  Enviar recordatorios
                </>
              )}
            </AdminButton>
            <AdminButton onClick={onSincronizar} disabled={sincronizando}>
              <ArrowsClockwise className={`h-4 w-4 ${sincronizando ? "admin-loader-anillo" : ""}`} weight="bold" />
              {sincronizando ? "Actualizando..." : "Actualizar desde la hoja"}
            </AdminButton>
          </div>
        }
      />

      <EnvioLoteProgreso
        activo={enviandoRecordatorios}
        titulo="Enviando recordatorios de cobro"
        descripcion="Notificando clientes activos pendientes de pago a través de WhatsApp Cloud API y Gmail SMTP con pausas de seguridad..."
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

          {/* Filtro unificado por estado de cobro y respuesta a la notificación */}
          <div className="mt-2 flex flex-wrap gap-2">
            {ESTADOS_FILTRO.map((e) => {
              const cantidad =
                e.valor === "TODOS"
                  ? clientes.length
                  : clientes.filter((c) => cumpleFiltroEstado(c, e.valor)).length;
              if (!e.siempreVisible && cantidad === 0) return null;
              return (
                <button
                  key={e.valor}
                  type="button"
                  onClick={() => setFiltroEstado(e.valor)}
                  className={`rounded-full px-4 py-2 text-sm transition-colors ${
                    filtroEstado === e.valor
                      ? "bg-gold text-ink-fixed"
                      : "bg-gold-pale/40 text-gold-deep hover:bg-gold-pale/60"
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

                {/* Separador vertical + estado de cobro y notificaciones al costado derecho */}
                <div className="hidden self-stretch border-l border-line lg:block" aria-hidden="true" />
                <div className="flex shrink-0 flex-col items-start gap-2.5 lg:w-80 lg:items-end">
                  {!conCosto ? (
                    <NotificationBadge size="sm" tone="neutral" icon={<Prohibit weight="bold" className="h-3.5 w-3.5" />}>
                      Sin costo
                    </NotificationBadge>
                  ) : (
                    <>
                      <div className="flex flex-wrap items-center gap-1.5 lg:justify-end">
                        {c.pagoEsteMes ? (
                          <NotificationBadge size="sm" tone="success" icon={<CheckCircle weight="bold" className="h-3.5 w-3.5" />}>
                            Pago aprobado
                          </NotificationBadge>
                        ) : (
                          <NotificationBadge size="sm" tone="warning" icon={<HourglassMedium weight="bold" className="h-3.5 w-3.5" />}>
                            Pendiente
                          </NotificationBadge>
                        )}

                        {c.correo ? (
                          <NotificationBadge
                            size="sm"
                            tone={c.correoEnviado ? "success" : "warning"}
                            icon={<EnvelopeSimple weight="bold" className="h-3.5 w-3.5" />}
                          >
                            {c.correoEnviado ? "Correo enviado" : "Correo pend."}
                          </NotificationBadge>
                        ) : (
                          <NotificationBadge size="sm" tone="neutral" icon={<EnvelopeSimple weight="bold" className="h-3.5 w-3.5" />}>
                            Sin correo
                          </NotificationBadge>
                        )}

                        {c.telefono ? (
                          <NotificationBadge
                            size="sm"
                            tone={c.whatsappEnviado ? "success" : "warning"}
                            icon={<WhatsappLogo weight="bold" className="h-3.5 w-3.5" />}
                          >
                            {c.whatsappEnviado ? "WA enviado" : "WA pend."}
                          </NotificationBadge>
                        ) : (
                          <NotificationBadge size="sm" tone="neutral" icon={<PhoneSlash weight="bold" className="h-3.5 w-3.5" />}>
                            Sin tel
                          </NotificationBadge>
                        )}

                        {c.respondioMensaje && (
                          <NotificationBadge
                            size="sm"
                            tone={c.respondioMensaje.toLowerCase().startsWith("s") ? "success" : "danger"}
                            icon={<ChatCircleText weight="bold" className="h-3.5 w-3.5" />}
                          >
                            Resp: {c.respondioMensaje}
                          </NotificationBadge>
                        )}
                      </div>

                      {/* Botones de acción manual rápida */}
                      <div className="mt-1 flex flex-wrap items-center gap-1 text-xs">
                        <span className="mr-1 text-[11px] font-medium uppercase tracking-wider text-ink-soft">
                          Ajuste:
                        </span>
                        <button
                          type="button"
                          disabled={actualizandoId === c.id}
                          onClick={() => onCambiarRespuesta(c.id, "SI", true)}
                          className={`inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium transition-colors ${
                            c.pagoEsteMes
                              ? "bg-emerald-600 text-white shadow-xs"
                              : "border border-emerald-200 bg-emerald-50 text-emerald-700 hover:bg-emerald-100"
                          }`}
                          title="Marcar como pago confirmado (SÍ)"
                        >
                          <Check className="h-3 w-3" weight="bold" />
                          Aceptó
                        </button>
                        <button
                          type="button"
                          disabled={actualizandoId === c.id}
                          onClick={() => onCambiarRespuesta(c.id, "NO", false)}
                          className={`inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium transition-colors ${
                            c.respondioMensaje?.toUpperCase() === "NO"
                              ? "bg-rose-600 text-white shadow-xs"
                              : "border border-rose-200 bg-rose-50 text-rose-700 hover:bg-rose-100"
                          }`}
                          title="Marcar como no pago (NO)"
                        >
                          <X className="h-3 w-3" weight="bold" />
                          No pagó
                        </button>
                        {(c.respondioMensaje || c.pagoEsteMes) && (
                          <button
                            type="button"
                            disabled={actualizandoId === c.id}
                            onClick={() => onCambiarRespuesta(c.id, null, false)}
                            className="inline-flex items-center rounded-md px-1.5 py-1 text-xs text-ink-soft hover:bg-ink/5 hover:text-ink"
                            title="Restablecer a pendiente"
                          >
                            Limpiar
                          </button>
                        )}
                      </div>
                    </>
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
