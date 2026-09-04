"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Dialog } from "@base-ui/react/dialog";
import { toast } from "sonner";
import { ArrowsClockwise, EnvelopeSimple, HourglassMedium, PhoneSlash, Plus, WhatsappLogo, X } from "@phosphor-icons/react";
import {
  listarCasos,
  crearCaso,
  sincronizarCasos,
  enviarCorreosPendientesCasos,
  ApiError,
  type CasoAdmin,
  type FuenteCaso,
} from "@/lib/admin-api";
import { AdminPageHeader, AdminCard, AdminButton, Badge, NotificationBadge, EmptyState, AdminLoader } from "@/components/admin/ui";

function formatearFecha(iso: string) {
  return new Date(iso).toLocaleDateString("es-CO", { day: "numeric", month: "short", year: "numeric" });
}

// "Todos" + las 4 fuentes reales: separa el seguimiento por hoja de origen (pedido
// explícito), sin perder la vista conjunta para cuando de verdad hace falta ver todo junto.
const FUENTES_FILTRO: { valor: FuenteCaso | "TODOS"; label: string }[] = [
  { valor: "TODOS", label: "Todos" },
  { valor: "JUDICIALES", label: "Judiciales" },
  { valor: "SUPERINTENDENCIA", label: "Superintendencia" },
  { valor: "PROCESOS_COMISARIA", label: "Procesos Comisaría" },
  { valor: "MANUAL", label: "Manuales" },
];

export default function CasosAdminPage() {
  const [casos, setCasos] = useState<CasoAdmin[] | null>(null);
  const [modalAbierto, setModalAbierto] = useState(false);
  const [sincronizando, setSincronizando] = useState(false);
  const [enviandoPendientes, setEnviandoPendientes] = useState(false);
  const [filtroFuente, setFiltroFuente] = useState<FuenteCaso | "TODOS">("TODOS");

  const cargar = useCallback(() => {
    listarCasos()
      .then(setCasos)
      .catch(() => toast.error("No se pudieron cargar los casos."));
  }, []);

  useEffect(() => {
    cargar();
  }, [cargar]);

  const casosFiltrados = casos?.filter((c) => filtroFuente === "TODOS" || c.fuente === filtroFuente) ?? null;

  async function onSincronizar() {
    setSincronizando(true);
    try {
      const resumen = await sincronizarCasos();
      toast.success(
        `${resumen.casosNuevos} caso(s) nuevo(s), ${resumen.casosActualizados} actualizado(s)` +
          (resumen.casosEliminados > 0 ? `, ${resumen.casosEliminados} eliminado(s) (ya no están en la hoja)` : "") +
          (resumen.filasSinCorreo > 0 ? `, ${resumen.filasSinCorreo} sin correo capturado aún (no se les puede notificar todavía).` : "."),
      );
      if (resumen.fuentesConError.length > 0) {
        toast.error(
          `No se pudo leer: ${resumen.fuentesConError.join(", ")}. Las demás hojas sí se sincronizaron; intenta de nuevo en unos minutos para esa(s).`,
        );
      }
      if (resumen.radicadosDuplicados > 0) {
        toast.warning(
          `${resumen.radicadosDuplicados} radicado(s) no se pudieron asignar porque ya están duplicados en la hoja (el mismo radicado en dos filas distintas). Corrige el duplicado directamente en el Google Sheets y vuelve a actualizar.`,
        );
      }
      cargar();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo sincronizar con la hoja.");
    } finally {
      setSincronizando(false);
    }
  }

  async function onEnviarPendientes() {
    setEnviandoPendientes(true);
    toast.info(
      "Enviando notificaciones pendientes -- va uno por uno con una pausa entre cada uno para no saturar el correo. Un lote grande puede tardar varios minutos.",
    );
    try {
      const resumen = await enviarCorreosPendientesCasos();
      const totalIntentado = resumen.correosEnviados + resumen.correosFallidos + resumen.whatsappEnviados + resumen.whatsappFallidos;
      if (totalIntentado === 0) {
        toast.info("No hay notificaciones pendientes por enviar.");
      } else {
        toast.success(
          `${resumen.correosEnviados} correo(s) y ${resumen.whatsappEnviados} WhatsApp confirmados como enviados.`,
        );
        if (resumen.correosFallidos > 0 || resumen.whatsappFallidos > 0) {
          toast.error(
            `${resumen.correosFallidos} correo(s) y ${resumen.whatsappFallidos} WhatsApp fallaron (incluso tras reintentar) -- quedaron pendientes para el próximo envío, revisa el Registro del sistema para el detalle.`,
          );
        }
      }
      cargar();
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudieron enviar las notificaciones pendientes.");
    } finally {
      setEnviandoPendientes(false);
    }
  }

  return (
    <div>
      <AdminPageHeader
        title="Casos"
        description="Se sincronizan automáticamente desde el Google Sheets de seguimiento de la firma: número de caso, radicado y datos de contacto del cliente. La notificación del radicado se envía por correo y por WhatsApp (línea de atención de la firma). El estado real se consulta en vivo desde la misma hoja."
        action={
          <div className="flex flex-wrap items-center gap-2">
            <AdminButton variant="ghost" onClick={() => setModalAbierto(true)}>
              <Plus className="h-4 w-4" weight="bold" />
              Nuevo caso manual
            </AdminButton>
            <AdminButton variant="secondary" onClick={onEnviarPendientes} disabled={enviandoPendientes}>
              <EnvelopeSimple className="h-4 w-4" weight="bold" />
              {enviandoPendientes ? "Enviando..." : "Enviar notificaciones pendientes"}
            </AdminButton>
            <AdminButton onClick={onSincronizar} disabled={sincronizando}>
              <ArrowsClockwise className={`h-4 w-4 ${sincronizando ? "admin-loader-anillo" : ""}`} weight="bold" />
              {sincronizando ? "Actualizando..." : "Actualizar desde la hoja"}
            </AdminButton>
          </div>
        }
      />

      {casos !== null && casos.length > 0 && (
        <div className="mt-6 flex flex-wrap gap-2">
          {FUENTES_FILTRO.map((f) => {
            const cantidad = f.valor === "TODOS" ? casos.length : casos.filter((c) => c.fuente === f.valor).length;
            if (f.valor !== "TODOS" && cantidad === 0) return null;
            return (
              <button
                key={f.valor}
                type="button"
                onClick={() => setFiltroFuente(f.valor)}
                className={`rounded-full px-4 py-2 text-sm transition-colors ${
                  filtroFuente === f.valor ? "bg-ink text-paper" : "bg-ink/5 text-ink-soft hover:bg-ink/10"
                }`}
              >
                {f.label} <span className="opacity-70">({cantidad})</span>
              </button>
            );
          })}
        </div>
      )}

      {casos === null ? (
        <AdminLoader />
      ) : casos.length === 0 ? (
        <EmptyState
          title="Aún no hay casos registrados"
          description='Usa "Actualizar desde la hoja" para traer todos los casos existentes de las hojas de la firma.'
        />
      ) : (
        <div className="mt-6 space-y-3">
          {casosFiltrados?.map((c) => (
            <AdminCard key={c.id} className="flex flex-col gap-5 lg:flex-row lg:items-center">
              <div className="min-w-0 flex-1">
                <div className="flex flex-wrap items-center gap-2">
                  <p className="font-medium text-ink">{c.nombreCliente}</p>
                  <Badge tone="gold">{c.fuenteVisible}</Badge>
                  {c.numeroCaso && <Badge tone="neutral">Caso Nº {c.numeroCaso}</Badge>}
                </div>
                <p className="mt-3 text-sm text-ink-soft">
                  {c.correoCliente ?? "Sin correo capturado en la hoja"}
                  {c.telefonoCliente && ` · ${c.telefonoCliente}`}
                </p>
                {c.radicadoId && (
                  <p className="mt-2 font-mono text-xs tracking-wider text-gold-deep">{c.radicadoId}</p>
                )}
                <p className="mt-1 text-xs text-ink-soft">Creado {formatearFecha(c.fechaCreacion)}</p>
                {c.notasInternas && (
                  <p className="mt-2 text-xs text-ink-soft">Nota: {c.notasInternas}</p>
                )}
              </div>

              {/* Separador vertical + estado de notificación al costado derecho (pedido
                  explícito): antes todo quedaba apilado a la izquierda dejando un vacío
                  enorme en pantallas anchas. Se oculta en móvil, donde el bloque de la
                  derecha simplemente cae debajo por el flex-col del contenedor. */}
              <div className="hidden self-stretch border-l border-line lg:block" aria-hidden="true" />
              <div className="flex shrink-0 flex-col items-start gap-2.5 lg:w-56 lg:items-end">
                {c.radicadoId ? (
                  <>
                    {c.correoCliente ? (
                      <NotificationBadge
                        tone={c.correoEnviado ? "success" : "warning"}
                        icon={<EnvelopeSimple weight="bold" className="h-3.5 w-3.5" />}
                      >
                        {c.correoEnviado ? "Correo enviado" : "Correo pendiente"}
                      </NotificationBadge>
                    ) : (
                      <NotificationBadge tone="neutral" icon={<EnvelopeSimple weight="bold" className="h-3.5 w-3.5" />}>
                        Sin correo capturado
                      </NotificationBadge>
                    )}
                    {c.telefonoCliente ? (
                      <NotificationBadge
                        tone={c.whatsappEnviado ? "success" : "warning"}
                        icon={<WhatsappLogo weight="bold" className="h-3.5 w-3.5" />}
                      >
                        {c.whatsappEnviado ? "WhatsApp enviado" : "WhatsApp pendiente"}
                      </NotificationBadge>
                    ) : (
                      <NotificationBadge tone="neutral" icon={<PhoneSlash weight="bold" className="h-3.5 w-3.5" />}>
                        Sin teléfono
                      </NotificationBadge>
                    )}
                  </>
                ) : (
                  <NotificationBadge tone="neutral" icon={<HourglassMedium weight="bold" className="h-3.5 w-3.5" />}>
                    Sin radicado aún
                  </NotificationBadge>
                )}
              </div>
            </AdminCard>
          ))}
        </div>
      )}

      <ModalNuevoCaso
        abierto={modalAbierto}
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
  onClose,
  onCreado,
}: {
  abierto: boolean;
  onClose: () => void;
  onCreado: (caso: CasoAdmin) => void;
}) {
  const [enviando, setEnviando] = useState(false);

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = e.currentTarget;
    const data = new FormData(form);
    const radicadoId = String(data.get("radicadoId") ?? "").trim();
    if (!radicadoId) {
      toast.error("Ingresa el número de radicado.");
      return;
    }

    setEnviando(true);
    try {
      const nuevo = await crearCaso({
        nombreCliente: String(data.get("nombreCliente") ?? ""),
        correoCliente: String(data.get("correoCliente") ?? ""),
        telefonoCliente: String(data.get("telefonoCliente") ?? "") || undefined,
        radicadoId,
        notasInternas: String(data.get("notasInternas") ?? "") || undefined,
      });
      toast.success(`Caso creado. Radicado ${nuevo.radicadoId} enviado al correo del cliente.`);
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
            <Dialog.Title className="font-display text-lg text-ink">Nuevo caso manual</Dialog.Title>
            <Dialog.Close className="flex h-8 w-8 items-center justify-center rounded-full text-ink-soft hover:bg-ink/5">
              <X className="h-4 w-4" />
            </Dialog.Close>
          </div>
          <Dialog.Description className="mt-1 text-sm text-ink-soft">
            Solo para un caso puntual que todavía no está en el Google Sheets de la firma —
            lo normal es que "Actualizar desde la hoja" ya lo traiga automáticamente. El
            radicado se envía por correo al cliente de inmediato.
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
            <input
              name="radicadoId"
              required
              placeholder="Número de radicado"
              className="w-full rounded-xl border border-line bg-paper px-4 py-3 font-mono text-sm text-ink focus:border-gold-deep focus:outline-none"
            />
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
