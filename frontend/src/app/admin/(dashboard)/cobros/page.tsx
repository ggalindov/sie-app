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
  TestTube,
  WhatsappLogo,
  X,
} from "@phosphor-icons/react";
import {
  listarCobros,
  sincronizarCobros,
  enviarRecordatoriosCobros,
  enviarRecordatorioPrueba,
  cambiarRespuestaCobro,
  ApiError,
  type ClienteCobro,
  type TipoClienteCobro,
} from "@/lib/admin-api";
import { AdminPageHeader, AdminCard, AdminButton, Badge, NotificationBadge, EmptyState, AdminLoader } from "@/components/admin/ui";
import { EnvioLoteProgreso } from "@/components/admin/envio-lote-progreso";
import { useAuth } from "@/lib/auth-context";
import { cn } from "@/lib/utils";

function formatearFecha(iso: string) {
  return new Date(iso).toLocaleDateString("es-CO", { day: "numeric", month: "short", year: "numeric" });
}

const TIPOS_FILTRO: { valor: TipoClienteCobro | "TODOS"; label: string }[] = [
  { valor: "TODOS", label: "Todos" },
  { valor: "EMPRESA", label: "Empresas" },
  { valor: "PERSONA_NATURAL", label: "Personas naturales" },
];

// Pedido explícito del usuario: separar con claridad a los clientes "primordiales" (los que
// sí tienen una tarifa monetaria) de los que no, y entre esos, distinguir sin ambigüedad entre
// "todavía no respondió", "dijo que no" y "pagó". Antes PENDIENTE era simplemente "!pagoEsteMes",
// así que un cliente que había respondido explícitamente que NO pagaba se mostraba igual que uno
// que ni siquiera había recibido el recordatorio todavía -- mismo filtro, dos situaciones muy
// distintas. Ahora son 4 estados mutuamente excluyentes (SIN_COSTO no compite con los otros 3,
// que a su vez nunca se solapan entre sí).
type EstadoCobroFiltro = "TODOS" | "PENDIENTE" | "NO_PAGO" | "APROBADO" | "SIN_COSTO";

const ESTADOS_FILTRO: { valor: EstadoCobroFiltro; label: string; siempreVisible?: boolean }[] = [
  { valor: "TODOS", label: "Todos", siempreVisible: true },
  { valor: "PENDIENTE", label: "Sin respuesta aún", siempreVisible: true },
  { valor: "NO_PAGO", label: "Dijeron que no" },
  { valor: "APROBADO", label: "Pagaron este mes" },
  { valor: "SIN_COSTO", label: "Sin costo" },
];

function tieneCosto(honorarios: string | null) {
  if (!honorarios) return false;
  return /[1-9]/.test(honorarios);
}

// "$ 1.750.905" -> 1750905, para sumar y mostrar totales en el resumen -- nunca se usa para
// decidir nada de negocio (esa regla sigue siendo tieneCosto()/el backend), solo para el total
// en pantalla.
function honorariosNumero(honorarios: string | null): number {
  if (!honorarios) return 0;
  const soloDigitos = honorarios.replace(/[^0-9]/g, "");
  return soloDigitos ? parseInt(soloDigitos, 10) : 0;
}

function formatearPesos(valor: number): string {
  return `$ ${valor.toLocaleString("es-CO")}`;
}

// Estado real y único de cada cliente -- la base de los 4 filtros y del acento de color de
// cada tarjeta, para que "qué le falta a este cliente" se lea de un vistazo sin tener que
// combinar varias insignias sueltas en la cabeza.
function estadoDe(c: ClienteCobro): EstadoCobroFiltro {
  if (!tieneCosto(c.honorarios)) return "SIN_COSTO";
  if (c.pagoEsteMes) return "APROBADO";
  if (c.respondioMensaje?.trim().toUpperCase() === "NO") return "NO_PAGO";
  return "PENDIENTE";
}

function cumpleFiltroEstado(c: ClienteCobro, filtro: EstadoCobroFiltro): boolean {
  return filtro === "TODOS" || estadoDe(c) === filtro;
}

const ACENTO_ESTADO: Record<EstadoCobroFiltro, string> = {
  TODOS: "",
  PENDIENTE: "border-l-4 border-l-amber-400",
  NO_PAGO: "border-l-4 border-l-rose-400",
  APROBADO: "border-l-4 border-l-emerald-400",
  SIN_COSTO: "border-l-4 border-l-ink/10",
};

export default function CobrosAdminPage() {
  const { sesion } = useAuth();
  const [clientes, setClientes] = useState<ClienteCobro[] | null>(null);
  const [sincronizando, setSincronizando] = useState(false);
  const [enviandoRecordatorios, setEnviandoRecordatorios] = useState(false);
  const [enviandoPrueba, setEnviandoPrueba] = useState(false);
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

  // Resumen de cobro (pedido explícito del usuario: "cuando una persona acepta el pago se
  // indique y esto lo veamos reflejado de mejor manera") -- un vistazo con cuánto ya se
  // confirmó este mes y cuánto sigue en juego, sin tener que contar tarjeta por tarjeta.
  const resumen = clientes?.reduce(
    (acc, c) => {
      const monto = honorariosNumero(c.honorarios);
      const estado = estadoDe(c);
      if (estado === "APROBADO") {
        acc.aprobado.cantidad += 1;
        acc.aprobado.total += monto;
      } else if (estado === "NO_PAGO") {
        acc.noPago.cantidad += 1;
        acc.noPago.total += monto;
      } else if (estado === "PENDIENTE") {
        acc.pendiente.cantidad += 1;
        acc.pendiente.total += monto;
      } else {
        acc.sinCosto += 1;
      }
      return acc;
    },
    {
      aprobado: { cantidad: 0, total: 0 },
      noPago: { cantidad: 0, total: 0 },
      pendiente: { cantidad: 0, total: 0 },
      sinCosto: 0,
    },
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

  // Botón de prueba: dispara un único recordatorio aislado al número de prueba fijo de la
  // firma (3126029742) -- nunca toca al lote real de clientes, a diferencia del botón de abajo.
  async function onEnviarRecordatorioPrueba() {
    setEnviandoPrueba(true);
    try {
      const resumen = await enviarRecordatorioPrueba();
      if (!resumen.encontrado) {
        toast.error("No se encontró un cliente activo con el número de prueba (3126029742).");
      } else {
        toast.success(
          `Recordatorio de prueba enviado a ${resumen.nombre}: ` +
            `${resumen.correoEnviado ? "correo OK" : "correo no enviado"}, ` +
            `${resumen.whatsappEnviado ? "WhatsApp OK" : "WhatsApp no enviado"}.`,
        );
      }
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo enviar el recordatorio de prueba.");
    } finally {
      setEnviandoPrueba(false);
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
            <AdminButton
              variant="ghost"
              onClick={onEnviarRecordatorioPrueba}
              disabled={enviandoPrueba}
              title="Envía un único recordatorio aislado al número de prueba (3126029742), sin tocar a ningún cliente real"
            >
              {enviandoPrueba ? (
                <>
                  <Spinner className="h-4 w-4 animate-spin" weight="bold" />
                  Enviando prueba...
                </>
              ) : (
                <>
                  <TestTube className="h-4 w-4" weight="bold" />
                  Enviar recordatorio de prueba
                </>
              )}
            </AdminButton>
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

      {resumen && clientes && clientes.length > 0 && (
        <div className="mt-6 grid grid-cols-2 gap-3 lg:grid-cols-4">
          <button
            type="button"
            onClick={() => setFiltroEstado("PENDIENTE")}
            className={cn(
              "rounded-2xl bg-amber-50 p-4 text-left ring-1 ring-amber-200 transition-opacity hover:opacity-90",
              filtroEstado === "PENDIENTE" && "ring-2 ring-amber-400",
            )}
          >
            <div className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-amber-700">
              <HourglassMedium weight="bold" className="h-3.5 w-3.5" />
              Por cobrar ({resumen.pendiente.cantidad})
            </div>
            <p className="mt-1.5 font-mono text-lg font-semibold text-amber-900">{formatearPesos(resumen.pendiente.total)}</p>
          </button>
          <button
            type="button"
            onClick={() => setFiltroEstado("NO_PAGO")}
            className={cn(
              "rounded-2xl bg-rose-50 p-4 text-left ring-1 ring-rose-200 transition-opacity hover:opacity-90",
              filtroEstado === "NO_PAGO" && "ring-2 ring-rose-400",
            )}
          >
            <div className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-rose-700">
              <X weight="bold" className="h-3.5 w-3.5" />
              Dijeron que no ({resumen.noPago.cantidad})
            </div>
            <p className="mt-1.5 font-mono text-lg font-semibold text-rose-900">{formatearPesos(resumen.noPago.total)}</p>
          </button>
          <button
            type="button"
            onClick={() => setFiltroEstado("APROBADO")}
            className={cn(
              "rounded-2xl bg-emerald-50 p-4 text-left ring-1 ring-emerald-200 transition-opacity hover:opacity-90",
              filtroEstado === "APROBADO" && "ring-2 ring-emerald-400",
            )}
          >
            <div className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-emerald-700">
              <CheckCircle weight="bold" className="h-3.5 w-3.5" />
              Pagado este mes ({resumen.aprobado.cantidad})
            </div>
            <p className="mt-1.5 font-mono text-lg font-semibold text-emerald-900">{formatearPesos(resumen.aprobado.total)}</p>
          </button>
          <button
            type="button"
            onClick={() => setFiltroEstado("SIN_COSTO")}
            className={cn(
              "rounded-2xl bg-ink/5 p-4 text-left ring-1 ring-line transition-opacity hover:opacity-90",
              filtroEstado === "SIN_COSTO" && "ring-2 ring-ink/30",
            )}
          >
            <div className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wide text-ink-soft">
              <Prohibit weight="bold" className="h-3.5 w-3.5" />
              Sin costo ({resumen.sinCosto})
            </div>
            <p className="mt-1.5 font-mono text-lg font-semibold text-ink-soft">No aplica</p>
          </button>
        </div>
      )}

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
            const estado = estadoDe(c);
            const conCosto = estado !== "SIN_COSTO";
            return (
              <AdminCard
                key={c.id}
                className={cn("flex flex-col gap-5 lg:flex-row lg:items-center", ACENTO_ESTADO[estado])}
              >
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <p className="font-medium text-ink">{c.nombre}</p>
                    <Badge tone="gold">{c.tipoVisible}</Badge>
                    <Badge tone="neutral">Nº {c.numeroFila}</Badge>
                  </div>
                  <p className="mt-3 text-sm text-ink-soft">
                    {c.correo ?? "Sin correo"}
                    {c.telefono && (
                      <span className="inline-flex items-center gap-1">
                        {" · "}
                        <DeviceMobile weight="bold" className="h-3.5 w-3.5" />
                        {c.telefono}
                      </span>
                    )}
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
                <div className="flex shrink-0 flex-col items-start gap-3 lg:w-80 lg:items-end">
                  {/* Estado principal: un solo dato grande y sin ambigüedad, en vez de competir
                      por atención con el resto de insignias (pedido explícito del usuario: "se
                      indique y esto lo veamos reflejado de mejor manera"). */}
                  {estado === "SIN_COSTO" && (
                    <NotificationBadge tone="neutral" icon={<Prohibit weight="bold" className="h-4 w-4" />}>
                      Sin costo
                    </NotificationBadge>
                  )}
                  {estado === "APROBADO" && (
                    <NotificationBadge tone="success" icon={<CheckCircle weight="bold" className="h-4 w-4" />}>
                      Pago confirmado
                    </NotificationBadge>
                  )}
                  {estado === "NO_PAGO" && (
                    <NotificationBadge tone="danger" icon={<X weight="bold" className="h-4 w-4" />}>
                      Dijo que no pagaba
                    </NotificationBadge>
                  )}
                  {estado === "PENDIENTE" && (
                    <NotificationBadge tone="warning" icon={<HourglassMedium weight="bold" className="h-4 w-4" />}>
                      Sin respuesta aún
                    </NotificationBadge>
                  )}

                  {conCosto && (
                    <>
                      {/* Secundario: qué canales ya se notificaron -- deliberadamente más
                          pequeño/discreto que el estado principal de arriba. */}
                      <div className="flex flex-wrap items-center gap-1.5 lg:justify-end">
                        {c.correo ? (
                          <NotificationBadge
                            size="sm"
                            tone={c.correoEnviado ? "success" : "neutral"}
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
                            tone={c.whatsappEnviado ? "success" : "neutral"}
                            icon={<WhatsappLogo weight="bold" className="h-3.5 w-3.5" />}
                          >
                            {c.whatsappEnviado ? "WA enviado" : "WA pend."}
                          </NotificationBadge>
                        ) : (
                          <NotificationBadge size="sm" tone="neutral" icon={<PhoneSlash weight="bold" className="h-3.5 w-3.5" />}>
                            Sin tel
                          </NotificationBadge>
                        )}
                      </div>

                      {/* Ajuste manual: un solo control de 3 posiciones en vez de 3 botones
                          sueltos con estilos distintos + un link de "Limpiar" aparte -- pedido
                          explícito del usuario ("arregla como se ve porque está muy confuso"). */}
                      <div className="flex flex-col items-start gap-1 lg:items-end">
                        <span className="text-[10px] font-medium uppercase tracking-wider text-ink-soft/70">
                          Ajuste manual
                        </span>
                        <div className="inline-flex items-center gap-0.5 rounded-full bg-ink/5 p-1">
                          <button
                            type="button"
                            disabled={actualizandoId === c.id}
                            onClick={() => onCambiarRespuesta(c.id, null, false)}
                            title="Restablecer a sin respuesta"
                            className={cn(
                              "inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium transition-colors disabled:opacity-50",
                              estado === "PENDIENTE" ? "bg-amber-500 text-white shadow-xs" : "text-ink-soft hover:bg-ink/10",
                            )}
                          >
                            <HourglassMedium className="h-3 w-3" weight="bold" />
                            Pendiente
                          </button>
                          <button
                            type="button"
                            disabled={actualizandoId === c.id}
                            onClick={() => onCambiarRespuesta(c.id, "SI", true)}
                            title="Marcar como pago confirmado (SÍ)"
                            className={cn(
                              "inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium transition-colors disabled:opacity-50",
                              estado === "APROBADO" ? "bg-emerald-600 text-white shadow-xs" : "text-ink-soft hover:bg-ink/10",
                            )}
                          >
                            <Check className="h-3 w-3" weight="bold" />
                            Pagó
                          </button>
                          <button
                            type="button"
                            disabled={actualizandoId === c.id}
                            onClick={() => onCambiarRespuesta(c.id, "NO", false)}
                            title="Marcar como no pago (NO)"
                            className={cn(
                              "inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium transition-colors disabled:opacity-50",
                              estado === "NO_PAGO" ? "bg-rose-600 text-white shadow-xs" : "text-ink-soft hover:bg-ink/10",
                            )}
                          >
                            <X className="h-3 w-3" weight="bold" />
                            No pagó
                          </button>
                        </div>
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
