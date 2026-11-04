"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Dialog } from "@base-ui/react/dialog";
import { toast } from "sonner";
import {
  ArrowsClockwise,
  CheckSquare,
  Clock,
  ClockCounterClockwise,
  EnvelopeSimple,
  FileText,
  LockKey,
  MagnifyingGlass,
  PencilSimple,
  Plus,
  ShieldCheck,
  ShieldSlash,
  SortAscending,
  Spinner,
  UserCircle,
  WhatsappLogo,
  X,
} from "@phosphor-icons/react";
import {
  listarCasosPrivados,
  crearCaso,
  marcarCasoPrivado,
  ApiError,
  type CasoAdmin,
} from "@/lib/admin-api";
import { useAuth } from "@/lib/auth-context";
import { AdminPageHeader, AdminCard, AdminButton, Badge, NotificationBadge, EmptyState, AdminLoader } from "@/components/admin/ui";
import { TareasDeCasoModal, type ObjetivoTareas } from "@/components/admin/tareas-de-caso-modal";
import { DocumentosDeCasoModal, type CasoParaDocumentos } from "@/components/admin/documentos-de-caso-modal";
import { SeguimientoDeCasoModal, type CasoParaSeguimiento } from "@/components/admin/seguimiento-de-caso-modal";
import { EditarCasoModal } from "@/components/admin/editar-caso-modal";
import { formatearFechaBogota, formatearFechaHoraBogota } from "@/lib/fecha";

const formatearFecha = formatearFechaBogota;
const formatearFechaHora = formatearFechaHoraBogota;
const formatearFechaSolo = formatearFechaBogota;

export default function CasosPrivadosPage() {
  const { sesion, cargando: cargandoSesion } = useAuth();
  const esAdmin = sesion?.rol === "ADMIN_GENERAL";

  const [casos, setCasos] = useState<CasoAdmin[] | null>(null);
  const [cargando, setCargando] = useState(false);
  const [modalCrearAbierto, setModalCrearAbierto] = useState(false);
  const [busqueda, setBusqueda] = useState("");
  const [vista, setVista] = useState<"tarjetas" | "tabla">("tarjetas");

  const [casoConTareasAbierto, setCasoConTareasAbierto] = useState<ObjetivoTareas | null>(null);
  const [casoConDocumentosAbierto, setCasoConDocumentosAbierto] = useState<CasoParaDocumentos | null>(null);
  const [casoConSeguimientoAbierto, setCasoConSeguimientoAbierto] = useState<CasoParaSeguimiento | null>(null);
  const [casoEditando, setCasoEditando] = useState<CasoAdmin | null>(null);
  const [desclasificandoId, setDesclasificandoId] = useState<number | null>(null);

  const cargar = useCallback(() => {
    if (!esAdmin) return;
    setCargando(true);
    listarCasosPrivados()
      .then(setCasos)
      .catch((err) => {
        toast.error(err instanceof ApiError ? err.message : "No se pudieron cargar los casos privados.");
      })
      .finally(() => setCargando(false));
  }, [esAdmin]);

  useEffect(() => {
    if (esAdmin) {
      cargar();
    }
  }, [esAdmin, cargar]);

  async function onDesclasificar(caso: CasoAdmin) {
    if (
      !window.confirm(
        `¿Deseas desclasificar el caso de "${caso.nombreCliente}"? Al quitar la reserva, el caso volverá a ser visible para los abogados en el módulo general de Casos.`,
      )
    ) {
      return;
    }
    setDesclasificandoId(caso.id);
    try {
      await marcarCasoPrivado(caso.id, false);
      toast.success(`Caso de ${caso.nombreCliente} desclasificado y movido a Casos Generales.`);
      setCasos((prev) => (prev ? prev.filter((c) => c.id !== caso.id) : prev));
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo desclasificar el caso.");
    } finally {
      setDesclasificandoId(null);
    }
  }

  if (cargandoSesion) {
    return (
      <div className="flex h-64 items-center justify-center">
        <AdminLoader />
      </div>
    );
  }

  if (!esAdmin) {
    return (
      <div className="mx-auto max-w-2xl mt-12 rounded-2xl border border-red-200 bg-red-50/50 p-8 text-center shadow-sm">
        <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-red-100 text-red-600 ring-1 ring-red-200">
          <ShieldCheck className="h-8 w-8" weight="fill" />
        </div>
        <h2 className="mt-4 font-display text-xl font-bold text-red-950">Acceso Exclusivo de Gerencia General</h2>
        <p className="mt-2 text-sm text-red-800 leading-relaxed">
          Este apartado almacena expedientes ultra sensibles, confidenciales y cifrados con AES-256. Ningún abogado ni
          usuario sin rol <strong className="font-semibold">ADMIN_GENERAL</strong> tiene autorización para visualizar ni
          consultar esta bóveda.
        </p>
      </div>
    );
  }

  const q = busqueda.trim().toLowerCase();
  const casosFiltrados = (casos ?? []).filter((c) => {
    if (!q) return true;
    return (
      c.nombreCliente.toLowerCase().includes(q) ||
      (c.radicadoId && c.radicadoId.toLowerCase().includes(q)) ||
      (c.correoCliente && c.correoCliente.toLowerCase().includes(q)) ||
      (c.telefonoCliente && c.telefonoCliente.toLowerCase().includes(q)) ||
      (c.motivoConfidencialidad && c.motivoConfidencialidad.toLowerCase().includes(q)) ||
      (c.notasInternas && c.notasInternas.toLowerCase().includes(q))
    );
  });

  return (
    <div>
      {/* Insignia de alta seguridad */}
      <div className="mb-3 inline-flex items-center gap-2 rounded-full border border-gold-deep/30 bg-ink px-3.5 py-1 text-xs font-semibold text-gold-pale shadow-sm">
        <ShieldCheck className="h-4 w-4 text-gold" weight="fill" />
        <span>BÓVEDA DE CASOS PRIVADOS · CIFRADO AES-256-GCM</span>
      </div>

      <AdminPageHeader
        title="Casos Privados y Cifrados"
        description="Bóveda de máxima reserva y secreto profesional. Estos expedientes están 100% aislados de la vista de abogados y del listado público de casos. Solo la Gerencia General puede acceder a su trazabilidad, actuaciones y providencias."
        action={
          <AdminButton onClick={() => setModalCrearAbierto(true)}>
            <Plus className="h-4 w-4" weight="bold" />
            Nuevo caso privado
          </AdminButton>
        }
      />

      {/* Barra de búsqueda y alternador de vista */}
      <div className="mt-6 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-line bg-surface p-4">
        <div className="relative min-w-[280px] flex-1 max-w-md">
          <MagnifyingGlass className="absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-soft" />
          <input
            type="text"
            value={busqueda}
            onChange={(e) => setBusqueda(e.target.value)}
            placeholder="Buscar por cliente, radicado, motivo o notas..."
            className="w-full rounded-xl border border-line bg-paper py-2 pl-9 pr-4 text-sm text-ink placeholder:text-ink-soft/70 focus:border-gold-deep focus:outline-none"
          />
          {busqueda && (
            <button
              type="button"
              onClick={() => setBusqueda("")}
              className="absolute right-3 top-1/2 -translate-y-1/2 text-ink-soft hover:text-ink"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          )}
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={cargar}
            disabled={cargando}
            className="inline-flex items-center gap-1.5 rounded-xl border border-line bg-paper px-3 py-2 text-xs font-medium text-ink-soft hover:bg-ink/5 disabled:opacity-50"
            title="Recargar casos privados"
          >
            <ArrowsClockwise className={`h-3.5 w-3.5 ${cargando ? "animate-spin" : ""}`} />
            Actualizar
          </button>
          <div className="inline-flex rounded-xl border border-line bg-paper p-0.5 text-xs">
            <button
              type="button"
              onClick={() => setVista("tarjetas")}
              className={`rounded-lg px-3 py-1.5 font-medium transition-colors ${
                vista === "tarjetas" ? "bg-ink text-paper shadow-sm" : "text-ink-soft hover:text-ink"
              }`}
            >
              Tarjetas
            </button>
            <button
              type="button"
              onClick={() => setVista("tabla")}
              className={`rounded-lg px-3 py-1.5 font-medium transition-colors ${
                vista === "tabla" ? "bg-ink text-paper shadow-sm" : "text-ink-soft hover:text-ink"
              }`}
            >
              Tabla
            </button>
          </div>
        </div>
      </div>

      {/* Contenido principal */}
      {casos === null ? (
        <div className="mt-8 flex justify-center">
          <AdminLoader />
        </div>
      ) : casosFiltrados.length === 0 ? (
        <div className="mt-8">
          <EmptyState
            title={busqueda ? "Sin coincidencias" : "Bóveda vacía"}
            description={
              busqueda
                ? "No se encontraron casos privados con el filtro ingresado."
                : "Aún no has registrado casos privados o sensibles. Puedes crear uno con el botón superior o mover uno existente desde su modal de edición."
            }
          />
        </div>
      ) : vista === "tabla" ? (
        <div className="mt-6 overflow-x-auto rounded-2xl border border-line bg-surface">
          <table className="w-full text-left text-xs">
            <thead className="border-b border-line bg-ink/[0.02] text-[11px] font-semibold uppercase tracking-wider text-ink-soft">
              <tr>
                <th className="px-4 py-3">Cliente / Caso</th>
                <th className="px-4 py-3">Radicado</th>
                <th className="px-4 py-3">Motivo Confidencialidad</th>
                <th className="px-4 py-3">Último Movimiento</th>
                <th className="px-4 py-3 text-right">Acciones</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-line/60">
              {casosFiltrados.map((c) => (
                <tr key={c.id} className="hover:bg-ink/[0.02]">
                  <td className="px-4 py-3.5 font-medium text-ink">
                    <div className="flex items-center gap-2">
                      <LockKey className="h-4 w-4 text-gold-deep" weight="bold" />
                      <span>{c.nombreCliente}</span>
                    </div>
                  </td>
                  <td className="px-4 py-3.5 font-mono text-gold-deep">{c.radicadoId ?? "Sin radicado"}</td>
                  <td className="px-4 py-3.5 text-ink-soft">{c.motivoConfidencialidad ?? "Reserva gerencial estricta"}</td>
                  <td className="px-4 py-3.5 text-ink-soft">
                    {c.fechaUltimoMovimientoJudicial
                      ? formatearFecha(c.fechaUltimoMovimientoJudicial)
                      : "Sin movimiento registrado"}
                  </td>
                  <td className="px-4 py-3.5 text-right">
                    <div className="inline-flex items-center gap-1.5">
                      <button
                        type="button"
                        onClick={() =>
                          setCasoConSeguimientoAbierto({
                            id: c.id,
                            nombreCliente: c.nombreCliente,
                            radicadoId: c.radicadoId,
                          })
                        }
                        className="rounded-lg bg-gold-pale/50 px-2.5 py-1 text-xs font-semibold text-gold-deep hover:bg-gold-pale"
                      >
                        Seguimiento
                      </button>
                      <button
                        type="button"
                        onClick={() => setCasoEditando(c)}
                        className="rounded-lg p-1.5 text-ink-soft hover:bg-ink/5 hover:text-ink"
                        title="Editar caso"
                      >
                        <PencilSimple className="h-4 w-4" />
                      </button>
                      <button
                        type="button"
                        onClick={() => onDesclasificar(c)}
                        disabled={desclasificandoId === c.id}
                        className="rounded-lg p-1.5 text-ink-soft hover:bg-red-50 hover:text-red-600 disabled:opacity-50"
                        title="Desclasificar a casos generales"
                      >
                        <ShieldSlash className="h-4 w-4" />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="mt-6 space-y-4">
          {casosFiltrados.map((c) => (
            <AdminCard key={c.id} className="!p-5 ring-1 ring-gold-deep/20 hover:ring-gold-deep/50 transition-all">
              <div className="grid gap-5 lg:grid-cols-[1fr_1fr_260px]">
                {/* Columna 1: Información del titular y radicado */}
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="inline-flex items-center gap-1 rounded-full bg-ink px-2.5 py-0.5 text-[11px] font-bold text-gold-pale ring-1 ring-gold/40">
                      <LockKey className="h-3 w-3 text-gold" weight="bold" />
                      Caso Privado
                    </span>
                    <span className="font-mono text-xs font-semibold text-gold-deep">
                      {c.radicadoId ?? "Sin radicado asignado"}
                    </span>
                  </div>

                  <h3 className="mt-2 font-display text-base font-bold text-ink">{c.nombreCliente}</h3>

                  {c.motivoConfidencialidad && (
                    <div className="mt-2.5 rounded-xl border border-gold-deep/25 bg-gold-pale/35 p-2.5 text-xs text-gold-deep">
                      <span className="font-bold">Motivo de reserva: </span>
                      <span>{c.motivoConfidencialidad}</span>
                    </div>
                  )}

                  {c.notasInternas && (
                    <p className="mt-2 text-xs italic text-ink-soft bg-ink/[0.02] rounded-lg p-2 border border-line/60">
                      &quot;{c.notasInternas}&quot;
                    </p>
                  )}

                  <div className="mt-3 flex flex-wrap items-center gap-3 text-xs text-ink-soft">
                    {c.correoCliente && (
                      <span className="inline-flex items-center gap-1">
                        <EnvelopeSimple className="h-3.5 w-3.5" />
                        {c.correoCliente}
                      </span>
                    )}
                    {c.telefonoCliente && (
                      <span className="inline-flex items-center gap-1">
                        <WhatsappLogo className="h-3.5 w-3.5" />
                        {c.telefonoCliente}
                      </span>
                    )}
                  </div>
                </div>

                {/* Columna 2: Ficha procesal y estado judicial */}
                <div className="min-w-0 border-t border-line/70 pt-4 lg:border-l lg:border-t-0 lg:pl-6 lg:pt-0">
                  <p className="text-[11px] font-semibold uppercase tracking-wider text-ink-soft">
                    Ficha y Trazabilidad Judicial
                  </p>
                  <p className="mt-1 text-sm font-semibold text-ink">
                    {c.despachoJudicial ?? "Despacho no asignado aún"}
                  </p>

                  <div className="mt-2 flex flex-wrap gap-1.5">
                    {c.tipoProceso && (
                      <span className="rounded-full bg-ink/5 px-2.5 py-0.5 text-[11px] text-ink-soft">
                        {[c.tipoProceso, c.claseProceso].filter(Boolean).join(" · ")}
                      </span>
                    )}
                    {c.ponente && (
                      <span className="rounded-full bg-ink/5 px-2.5 py-0.5 text-[11px] text-ink-soft">
                        Ponente: {c.ponente}
                      </span>
                    )}
                    {c.ubicacionProceso && (
                      <span className="rounded-full bg-gold-pale/50 px-2.5 py-0.5 text-[11px] text-gold-deep">
                        Ubicación: {c.ubicacionProceso}
                      </span>
                    )}
                  </div>

                  <div className="mt-3 space-y-1 text-xs text-ink-soft">
                    {c.fechaUltimoMovimientoJudicial && (
                      <p>
                        Último movimiento judicial:{" "}
                        <strong className="text-ink font-semibold">
                          {formatearFechaHora(c.fechaUltimoMovimientoJudicial)}
                        </strong>
                      </p>
                    )}
                    <p className="text-[11px] text-ink-soft/70">
                      Registrado en bóveda: {formatearFecha(c.fechaCreacion)}
                    </p>
                  </div>
                </div>

                {/* Columna 3: Acciones exclusivas de Gerencia */}
                <div className="min-w-0 border-t border-line/70 pt-4 lg:border-l lg:border-t-0 lg:pl-6 lg:pt-0">
                  <p className="text-[11px] font-semibold uppercase tracking-wider text-ink-soft">
                    Control Gerencial
                  </p>
                  <div className="mt-2.5 space-y-2">
                    <button
                      type="button"
                      onClick={() =>
                        setCasoConSeguimientoAbierto({
                          id: c.id,
                          nombreCliente: c.nombreCliente,
                          radicadoId: c.radicadoId,
                        })
                      }
                      className="relative flex w-full items-center gap-2 rounded-xl bg-gold-pale/50 px-3 py-2 text-xs font-semibold text-gold-deep ring-1 ring-gold-deep/20 hover:bg-gold-pale transition-colors"
                    >
                      <ClockCounterClockwise className="h-4 w-4" weight="bold" />
                      <span>Seguimiento Dual (Rama + Croma)</span>
                      {c.tieneActualizacionSinRevisar && (
                        <span className="absolute right-2 top-2 h-2 w-2 rounded-full bg-red-500" />
                      )}
                    </button>

                    <button
                      type="button"
                      onClick={() =>
                        setCasoConTareasAbierto({
                          tipo: "caso",
                          id: c.id,
                          nombreCliente: c.nombreCliente,
                          radicadoId: c.radicadoId,
                        })
                      }
                      className="flex w-full items-center gap-2 rounded-xl bg-ink/5 px-3 py-2 text-xs font-medium text-ink hover:bg-ink/10 transition-colors"
                    >
                      <CheckSquare className="h-4 w-4 text-ink-soft" weight="bold" />
                      <span>Tareas privadas</span>
                    </button>

                    <button
                      type="button"
                      onClick={() =>
                        setCasoConDocumentosAbierto({
                          id: c.id,
                          nombreCliente: c.nombreCliente,
                          radicadoId: c.radicadoId,
                        })
                      }
                      className="flex w-full items-center gap-2 rounded-xl bg-ink/5 px-3 py-2 text-xs font-medium text-ink hover:bg-ink/10 transition-colors"
                    >
                      <FileText className="h-4 w-4 text-ink-soft" weight="bold" />
                      <span>Documentos confidenciales</span>
                    </button>

                    <div className="flex items-center gap-2 pt-1">
                      <button
                        type="button"
                        onClick={() => setCasoEditando(c)}
                        className="flex-1 inline-flex items-center justify-center gap-1.5 rounded-xl border border-line bg-paper px-3 py-1.5 text-xs font-medium text-ink hover:bg-ink/5 transition-colors"
                      >
                        <PencilSimple className="h-3.5 w-3.5" />
                        <span>Editar</span>
                      </button>

                      <button
                        type="button"
                        onClick={() => onDesclasificar(c)}
                        disabled={desclasificandoId === c.id}
                        className="inline-flex items-center justify-center gap-1 rounded-xl border border-line bg-paper px-2.5 py-1.5 text-xs font-medium text-ink-soft hover:border-red-200 hover:bg-red-50 hover:text-red-600 transition-colors disabled:opacity-50"
                        title="Desclasificar a casos generales"
                      >
                        <ShieldSlash className="h-3.5 w-3.5" />
                        <span>Desclasificar</span>
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </AdminCard>
          ))}
        </div>
      )}

      {/* Modal para Crear Caso Privado */}
      <CrearCasoPrivadoModal
        abierto={modalCrearAbierto}
        onClose={() => setModalCrearAbierto(false)}
        onCreado={(nuevo) => {
          setCasos((prev) => (prev ? [nuevo, ...prev] : [nuevo]));
          setModalCrearAbierto(false);
        }}
      />

      {/* Modales de detalle integrados */}
      <TareasDeCasoModal
        objetivo={casoConTareasAbierto}
        onClose={() => setCasoConTareasAbierto(null)}
      />

      <DocumentosDeCasoModal
        caso={casoConDocumentosAbierto}
        onClose={() => setCasoConDocumentosAbierto(null)}
      />

      <SeguimientoDeCasoModal
        caso={casoConSeguimientoAbierto}
        onClose={() => setCasoConSeguimientoAbierto(null)}
      />

      <EditarCasoModal
        caso={casoEditando}
        onClose={() => setCasoEditando(null)}
        onActualizado={(actualizado) => {
          if (actualizado.esPrivadoCifrado === false) {
            // Si el admin lo desmarcó desde el modal de edición, quitarlo de la bóveda
            setCasos((prev) => (prev ? prev.filter((c) => c.id !== actualizado.id) : prev));
          } else {
            setCasos((prev) => (prev ? prev.map((c) => (c.id === actualizado.id ? actualizado : c)) : prev));
          }
          setCasoEditando(null);
        }}
      />
    </div>
  );
}

function CrearCasoPrivadoModal({
  abierto,
  onClose,
  onCreado,
}: {
  abierto: boolean;
  onClose: () => void;
  onCreado: (nuevo: CasoAdmin) => void;
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
        nombreCliente: String(data.get("nombreCliente") ?? "").trim(),
        correoCliente: String(data.get("correoCliente") ?? "").trim(),
        telefonoCliente: String(data.get("telefonoCliente") ?? "").trim() || undefined,
        radicadoId,
        notasInternas: String(data.get("notasInternas") ?? "").trim() || undefined,
        esPrivadoCifrado: true,
        motivoConfidencialidad: String(data.get("motivoConfidencialidad") ?? "").trim() || "Reserva gerencial estricta",
      });
      toast.success(`Caso privado creado bajo secreto gerencial (Radicado: ${nuevo.radicadoId}).`);
      form.reset();
      onCreado(nuevo);
    } catch (err) {
      toast.error(err instanceof ApiError ? err.message : "No se pudo registrar el caso privado.");
    } finally {
      setEnviando(false);
    }
  }

  return (
    <Dialog.Root open={abierto} onOpenChange={(open) => !open && onClose()}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-ink-fixed/60 backdrop-blur-sm" />
        <Dialog.Popup className="fixed left-1/2 top-1/2 z-50 w-full max-w-lg -translate-x-1/2 -translate-y-1/2 overflow-y-auto max-h-[90vh] rounded-2xl bg-surface p-6 shadow-2xl ring-1 ring-gold-deep/30">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2.5">
              <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-ink text-gold">
                <ShieldCheck className="h-5 w-5" weight="fill" />
              </span>
              <div>
                <Dialog.Title className="font-display text-lg font-bold text-ink">
                  Nuevo Caso Privado y Cifrado
                </Dialog.Title>
                <p className="text-xs text-gold-deep font-semibold">Bóveda Exclusiva de Gerencia</p>
              </div>
            </div>
            <Dialog.Close className="flex h-8 w-8 items-center justify-center rounded-full text-ink-soft hover:bg-ink/5">
              <X className="h-4 w-4" />
            </Dialog.Close>
          </div>

          <p className="mt-3 rounded-xl border border-gold-deep/20 bg-gold-pale/30 p-3 text-xs leading-relaxed text-gold-deep">
            Este expediente se guardará con <strong>cifrado AES-256</strong> y quedará completamente oculto para los
            abogados de la firma. Solo la Gerencia podrá acceder a su seguimiento y providencias.
          </p>

          <form onSubmit={onSubmit} className="mt-4 space-y-3">
            <div>
              <label className="mb-1 block text-xs font-semibold text-ink">Nombre del cliente / Titular *</label>
              <input
                name="nombreCliente"
                required
                placeholder="Nombre completo o código confidencial"
                className="w-full rounded-xl border border-line bg-paper px-4 py-2.5 text-sm text-ink focus:border-gold-deep focus:outline-none"
              />
            </div>

            <div>
              <label className="mb-1 block text-xs font-semibold text-ink">Número de radicado (23 dígitos) *</label>
              <input
                name="radicadoId"
                required
                placeholder="Ej: 11001400302020250115600"
                className="w-full rounded-xl border border-line bg-paper px-4 py-2.5 font-mono text-sm text-ink focus:border-gold-deep focus:outline-none"
              />
            </div>

            <div className="grid gap-3 sm:grid-cols-2">
              <div>
                <label className="mb-1 block text-xs font-semibold text-ink">Correo de contacto *</label>
                <input
                  name="correoCliente"
                  type="email"
                  required
                  placeholder="correo@ejemplo.com"
                  className="w-full rounded-xl border border-line bg-paper px-4 py-2.5 text-sm text-ink focus:border-gold-deep focus:outline-none"
                />
              </div>
              <div>
                <label className="mb-1 block text-xs font-medium text-ink-soft">Teléfono / WhatsApp</label>
                <input
                  name="telefonoCliente"
                  placeholder="+57 300 123 4567"
                  className="w-full rounded-xl border border-line bg-paper px-4 py-2.5 text-sm text-ink focus:border-gold-deep focus:outline-none"
                />
              </div>
            </div>

            <div>
              <label className="mb-1 block text-xs font-semibold text-ink">
                Motivo de confidencialidad / Justificación *
              </label>
              <input
                name="motivoConfidencialidad"
                required
                defaultValue="Caso ultra sensible bajo estricta reserva de gerencia"
                placeholder="Ej: Caso sensible penal, cliente reservado gerencial..."
                className="w-full rounded-xl border border-line bg-paper px-4 py-2.5 text-sm text-ink focus:border-gold-deep focus:outline-none"
              />
            </div>

            <div>
              <label className="mb-1 block text-xs font-medium text-ink-soft">
                Notas internas cifradas (Solo lectura de gerencia)
              </label>
              <textarea
                name="notasInternas"
                rows={3}
                placeholder="Escribe detalles sensibles, instrucciones reservadas o antecedentes..."
                className="w-full rounded-xl border border-line bg-paper px-4 py-2.5 text-sm text-ink focus:border-gold-deep focus:outline-none"
              />
            </div>

            <AdminButton type="submit" disabled={enviando} className="w-full mt-4">
              {enviando ? "Cifrando y registrando..." : "Guardar en Bóveda Privada"}
            </AdminButton>
          </form>
        </Dialog.Popup>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
