"use client";

import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import {
  ArrowsClockwise,
  ChatCircleText,
  CheckCircle,
  EnvelopeSimple,
  HourglassMedium,
  PhoneSlash,
  Prohibit,
  Spinner,
  WhatsappLogo,
} from "@phosphor-icons/react";
import {
  listarCobros,
  sincronizarCobros,
  enviarRecordatoriosCobros,
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

                {/* Separador vertical + estado de cobro y notificaciones al costado derecho (mismo
                    patrón que Casos para ver si se le envió por WhatsApp y correo). */}
                <div className="hidden self-stretch border-l border-line lg:block" aria-hidden="true" />
                <div className="flex shrink-0 flex-col items-start gap-2.5 lg:w-64 lg:items-end">
                  {!conCosto ? (
                    <NotificationBadge tone="neutral" icon={<Prohibit weight="bold" className="h-3.5 w-3.5" />}>
                      Sin costo
                    </NotificationBadge>
                  ) : (
                    <>
                      {c.pagoEsteMes ? (
                        <NotificationBadge tone="success" icon={<CheckCircle weight="bold" className="h-3.5 w-3.5" />}>
                          Respuesta de pago aprobada
                        </NotificationBadge>
                      ) : (
                        <NotificationBadge tone="warning" icon={<HourglassMedium weight="bold" className="h-3.5 w-3.5" />}>
                          Respuesta de pago pendiente
                        </NotificationBadge>
                      )}

                      {c.correo ? (
                        <NotificationBadge
                          tone={c.correoEnviado ? "success" : "warning"}
                          icon={<EnvelopeSimple weight="bold" className="h-3.5 w-3.5" />}
                        >
                          {c.correoEnviado
                            ? c.fechaUltimoRecordatorioCorreo
                              ? `Correo enviado (${formatearFecha(c.fechaUltimoRecordatorioCorreo)})`
                              : "Correo enviado"
                            : "Correo pendiente"}
                        </NotificationBadge>
                      ) : (
                        <NotificationBadge tone="neutral" icon={<EnvelopeSimple weight="bold" className="h-3.5 w-3.5" />}>
                          Sin correo capturado
                        </NotificationBadge>
                      )}

                      {c.telefono ? (
                        <NotificationBadge
                          tone={c.whatsappEnviado ? "success" : "warning"}
                          icon={<WhatsappLogo weight="bold" className="h-3.5 w-3.5" />}
                        >
                          {c.whatsappEnviado
                            ? c.fechaUltimoRecordatorioWhatsapp
                              ? `WhatsApp enviado (${formatearFecha(c.fechaUltimoRecordatorioWhatsapp)})`
                              : "WhatsApp enviado"
                            : "WhatsApp pendiente"}
                        </NotificationBadge>
                      ) : (
                        <NotificationBadge tone="neutral" icon={<PhoneSlash weight="bold" className="h-3.5 w-3.5" />}>
                          Sin teléfono
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
