"use client";

import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import {
  ArrowsClockwise,
  ChatCircleText,
  CheckCircle,
  DeviceMobile,
  EnvelopeSimple,
  HourglassMedium,
  MagnifyingGlass,
  PhoneSlash,
  Prohibit,
  Spinner,
  TestTube,
  WarningCircle,
  WhatsappLogo,
  X,
} from "@phosphor-icons/react";
import {
  listarCobros,
  sincronizarCobros,
  enviarRecordatoriosCobros,
  enviarRecordatorioPrueba,
  actualizarEstadoPagoCobro,
  obtenerSaludWebhookCobros,
  ApiError,
  type ClienteCobro,
} from "@/lib/admin-api";
import { AdminPageHeader, AdminCard, AdminButton, Badge, NotificationBadge, EmptyState, AdminLoader } from "@/components/admin/ui";
import { EnvioLoteProgreso } from "@/components/admin/envio-lote-progreso";
import { useAuth } from "@/lib/auth-context";
import { cn } from "@/lib/utils";
import { estadoDeCobro, tieneCostoCobro, type EstadoCobro } from "@/lib/cobro-estado";

function formatearFecha(iso: string) {
  return new Date(iso).toLocaleDateString("es-CO", { day: "numeric", month: "short", year: "numeric" });
}

// Pedido explícito del usuario: separar con claridad a los clientes "primordiales" (los que
// sí tienen una tarifa monetaria) de los que no, y entre esos, distinguir sin ambigüedad entre
// "todavía no respondió", "dijo que no" y "pagó". Antes PENDIENTE era simplemente "!pagoEsteMes",
// así que un cliente que había respondido explícitamente que NO pagaba se mostraba igual que uno
// que ni siquiera había recibido el recordatorio todavía -- mismo filtro, dos situaciones muy
// distintas. Ahora son 4 estados mutuamente excluyentes (SIN_COSTO no compite con los otros 3,
// que a su vez nunca se solapan entre sí) -- ver lib/cobro-estado.ts, clasificación compartida
// con la ficha 360 del CRM.
type EstadoCobroFiltro = "TODOS" | EstadoCobro;

const ESTADOS_FILTRO: { valor: EstadoCobroFiltro; label: string; siempreVisible?: boolean }[] = [
  { valor: "TODOS", label: "Todos", siempreVisible: true },
  { valor: "PENDIENTE", label: "Sin respuesta aún", siempreVisible: true },
  { valor: "NO_PAGO", label: "Dijeron que no" },
  { valor: "APROBADO", label: "Pagaron este mes" },
  { valor: "SIN_COSTO", label: "Sin costo" },
];

// "$ 1.750.905" -> 1750905, para sumar y mostrar totales en el resumen -- nunca se usa para
// decidir nada de negocio (esa regla sigue siendo tieneCostoCobro()/el backend), solo para el
// total en pantalla.
function honorariosNumero(honorarios: string | null): number {
  if (!honorarios) return 0;
  const soloDigitos = honorarios.replace(/[^0-9]/g, "");
  return soloDigitos ? parseInt(soloDigitos, 10) : 0;
}

function formatearPesos(valor: number): string {
  return `$ ${valor.toLocaleString("es-CO")}`;
}

function cumpleFiltroEstado(c: ClienteCobro, filtro: EstadoCobroFiltro): boolean {
  return filtro === "TODOS" || estadoDeCobro(c) === filtro;
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
  const [filtroEstado, setFiltroEstado] = useState<EstadoCobroFiltro>("TODOS");
  const [busqueda, setBusqueda] = useState("");
  const [actualizandoId, setActualizandoId] = useState<number | null>(null);
  // Pedido explícito del usuario (2026-11-06): "revisa porque el aceptar o rechazar pagos en
  // whatsapp no se esta reflejando" -- null significa "nunca, ni una vez, se ha recibido un
  // evento real de Meta" (ver RESPUESTA_WEBHOOK_WHATSAPP_COBRO en el backend). undefined
  // mientras carga, para no mostrar la alerta de golpe antes de tener la respuesta real.
  const [ultimaRespuestaWebhook, setUltimaRespuestaWebhook] = useState<string | null | undefined>(undefined);

  async function onCambiarEstadoPago(id: number, pagoEsteMes: boolean | null, respuesta: string | null) {
    setActualizandoId(id);
    const anterior = clientes;
    setClientes((prev) =>
      prev
        ? prev.map((c) =>
            c.id === id
              ? { ...c, pagoEsteMes, respondioMensaje: respuesta }
              : c,
          )
        : prev,
    );
    try {
      await actualizarEstadoPagoCobro(id, pagoEsteMes, respuesta);
      toast.success(
        pagoEsteMes === true
          ? "Pago confirmado y reflejado exitosamente en el sistema."
          : pagoEsteMes === false
            ? "Cliente registrado como 'No pagó'."
            : "Estado restablecido a pendiente.",
      );
    } catch (err) {
      setClientes(anterior);
      toast.error(err instanceof ApiError ? err.message : "No se pudo actualizar el estado de pago.");
    } finally {
      setActualizandoId(null);
    }
  }

  const cargar = useCallback(() => {
    listarCobros()
      .then(setClientes)
      .catch(() => toast.error("No se pudieron cargar los clientes con cobro pendiente."));
  }, []);

  useEffect(() => {
    if (sesion?.rol === "ADMIN_GENERAL") cargar();
  }, [cargar, sesion]);

  useEffect(() => {
    if (sesion?.rol !== "ADMIN_GENERAL") return;
    obtenerSaludWebhookCobros()
      .then((salud) => setUltimaRespuestaWebhook(salud.ultimaRespuestaReal))
      .catch(() => {
        // Silencioso a propósito: esta alerta es un dato secundario, un fallo acá no debe
        // bloquear ni interrumpir la carga principal de la lista de cobros.
      });
  }, [sesion]);

  if (sesion && sesion.rol !== "ADMIN_GENERAL") {
    return (
      <EmptyState
        title="No tienes acceso a esta sección"
        description="Solo el administrador general puede ver Cobros Pendientes."
      />
    );
  }

  // Buscador 100% local (pedido explícito del usuario: "super rapido") -- filtra el array ya
  // cargado en memoria, sin ningún viaje al servidor, así que responde en cada tecla sin
  // ningún retraso perceptible.
  const busquedaNormalizada = busqueda.trim().toLowerCase();
  const clientesFiltrados =
    clientes
      ?.filter((c) => cumpleFiltroEstado(c, filtroEstado))
      .filter((c) => {
        if (!busquedaNormalizada) return true;
        return (
          c.nombre.toLowerCase().includes(busquedaNormalizada) ||
          (c.correo?.toLowerCase().includes(busquedaNormalizada) ?? false) ||
          (c.telefono?.toLowerCase().includes(busquedaNormalizada) ?? false) ||
          (c.cedulaNit?.toLowerCase().includes(busquedaNormalizada) ?? false) ||
          c.numeroFila.toLowerCase().includes(busquedaNormalizada)
        );
      }) ?? null;

  // Resumen de cobro (pedido explícito del usuario: "cuando una persona acepta el pago se
  // indique y esto lo veamos reflejado de mejor manera") -- un vistazo con cuánto ya se
  // confirmó este mes y cuánto sigue en juego, sin tener que contar tarjeta por tarjeta.
  const resumen = clientes?.reduce(
    (acc, c) => {
      const monto = honorariosNumero(c.honorarios);
      const estado = estadoDeCobro(c);
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

      {/* Pedido explícito del usuario (2026-11-06): "revisa porque el aceptar o rechazar
          pagos en whatsapp no se esta reflejando" -- auditoría en producción confirmó que
          Meta nunca ha entregado un evento real a nuestro webhook (problema de suscripción
          en el Meta Business Manager, no de nuestro código). Esta alerta deja ese hecho
          visible de inmediato en vez de que se note semanas después solo porque "muchos
          clientes respondieron y no se refleja". ultimaRespuestaWebhook === undefined
          mientras carga -- nunca se muestra la alerta antes de tener la respuesta real. */}
      {ultimaRespuestaWebhook === null && (
        <div className="mt-6 flex items-start gap-3 rounded-xl border border-red-300 bg-red-50 px-4 py-3.5">
          <WarningCircle className="mt-0.5 h-5 w-5 shrink-0 text-red-600" weight="bold" />
          <div className="text-sm text-red-900">
            <p className="font-semibold">El sistema nunca ha recibido una respuesta automática real de WhatsApp.</p>
            <p className="mt-1 text-red-800/90">
              Los clientes pueden estar respondiendo Sí/No por WhatsApp sin que esto llegue a reflejarse aquí. Esto
              casi siempre significa que el webhook no está suscrito correctamente en el Meta Business Manager
              (Configuración → Webhooks → campo &quot;messages&quot;), no un error de este sistema. Verifica esa
              configuración con quien administre la cuenta de WhatsApp Business de la firma.
            </p>
          </div>
        </div>
      )}

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
          <div className="relative mt-6">
            <MagnifyingGlass weight="bold" className="pointer-events-none absolute left-4 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-soft" />
            <input
              type="text"
              value={busqueda}
              onChange={(e) => setBusqueda(e.target.value)}
              placeholder="Buscar por nombre, correo, teléfono, cédula/NIT o número..."
              className="w-full rounded-full border border-line bg-surface py-2.5 pl-11 pr-4 text-sm text-ink placeholder:text-ink-soft focus:border-gold-deep focus:outline-none"
            />
          </div>

          {/* Filtro unificado por estado de cobro y respuesta a la notificación -- pedido
              explícito del usuario: quitar el filtro por tipo (Empresas/Personas naturales,
              poco relevante para el día a día) y dejar los filtros organizados alrededor de
              lo que de verdad importa: a quién le falta cobrar. */}
          <div className="mt-3 flex flex-wrap gap-2">
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
      ) : clientesFiltrados?.length === 0 ? (
        <EmptyState title="Sin resultados" description="Ningún cliente coincide con la búsqueda o el filtro actual." />
      ) : (
        <div className="mt-6 space-y-3">
          {clientesFiltrados?.map((c) => {
            const estado = estadoDeCobro(c);
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
                    <div className="flex flex-col items-start gap-1 lg:items-end">
                      <NotificationBadge tone="success" icon={<CheckCircle weight="bold" className="h-4 w-4" />}>
                        Pago confirmado
                      </NotificationBadge>
                      <p className="text-[11px] font-medium text-emerald-700 dark:text-emerald-400">
                        {c.respondioMensaje
                          ? `✓ Cliente confirmó "${c.respondioMensaje}" por WhatsApp`
                          : "✓ Confirmado en el sistema"}
                      </p>
                    </div>
                  )}
                  {estado === "NO_PAGO" && (
                    <div className="flex flex-col items-start gap-1 lg:items-end">
                      <NotificationBadge tone="danger" icon={<X weight="bold" className="h-4 w-4" />}>
                        Dijo que no pagaba
                      </NotificationBadge>
                      <p className="text-[11px] font-medium text-rose-700 dark:text-rose-400">
                        {c.respondioMensaje
                          ? `✕ Cliente respondió "${c.respondioMensaje}" por WhatsApp`
                          : "✕ Marcado como no pagó"}
                      </p>
                    </div>
                  )}
                  {estado === "PENDIENTE" && (
                    <NotificationBadge tone="warning" icon={<HourglassMedium weight="bold" className="h-4 w-4" />}>
                      Sin respuesta aún
                    </NotificationBadge>
                  )}

                  {/* Acciones directas para reflejar y ajustar la decisión de pago */}
                  {conCosto && (
                    <div className="flex flex-wrap items-center gap-1.5 pt-1 lg:justify-end">
                      {c.pagoEsteMes !== true && (
                        <button
                          type="button"
                          disabled={actualizandoId === c.id}
                          onClick={() => onCambiarEstadoPago(c.id, true, "Sí (admin)")}
                          className="inline-flex items-center gap-1 rounded-full bg-emerald-600/15 px-2.5 py-1 text-xs font-semibold text-emerald-800 transition-colors hover:bg-emerald-600/25 disabled:opacity-50 dark:bg-emerald-500/20 dark:text-emerald-300"
                        >
                          <CheckCircle weight="bold" className="h-3 w-3" />
                          Confirmar pago
                        </button>
                      )}
                      {c.pagoEsteMes !== false && (
                        <button
                          type="button"
                          disabled={actualizandoId === c.id}
                          onClick={() => onCambiarEstadoPago(c.id, false, "No (admin)")}
                          className="inline-flex items-center gap-1 rounded-full bg-rose-600/15 px-2.5 py-1 text-xs font-semibold text-rose-800 transition-colors hover:bg-rose-600/25 disabled:opacity-50 dark:bg-rose-500/20 dark:text-rose-300"
                        >
                          <X weight="bold" className="h-3 w-3" />
                          Marcar no pagó
                        </button>
                      )}
                      {c.pagoEsteMes !== null && (
                        <button
                          type="button"
                          disabled={actualizandoId === c.id}
                          onClick={() => onCambiarEstadoPago(c.id, null, null)}
                          className="inline-flex items-center gap-1 rounded-full bg-ink/5 px-2.5 py-1 text-xs text-ink-soft transition-colors hover:bg-ink/10 disabled:opacity-50"
                        >
                          Restablecer
                        </button>
                      )}
                    </div>
                  )}

                  {conCosto && (
                    // Secundario: qué canales ya se notificaron -- deliberadamente más pequeño/
                    // discreto que el estado principal de arriba. Pedido explícito del usuario:
                    // "el sistema debe ser netamente automático en un 100%" -- el estado de pago
                    // ya NO tiene ningún control manual, se fija únicamente por la respuesta real
                    // del cliente al botón Sí/No de WhatsApp (ver WhatsAppWebhookController ->
                    // CobroService.registrarRespuesta()).
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
