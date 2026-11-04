"use client";

import { Dialog } from "@base-ui/react/dialog";
import {
  Scales,
  FilePdf,
  ArrowSquareOut,
  ClockCounterClockwise,
  X,
  Buildings,
  CalendarCheck,
  User,
  ShieldCheck,
  CheckCircle,
} from "@phosphor-icons/react";
import type { TerminoCalendario } from "@/lib/admin-api";
import { AdminButton, Badge } from "@/components/admin/ui";
import { colorEstadoTermino } from "@/components/admin/calendario-mensual";
import { formatearFechaBogota } from "@/lib/fecha";

interface TerminoDetalleModalProps {
  termino: TerminoCalendario | null;
  onClose: () => void;
  onAbrirSeguimiento?: (casoId: number, radicado: string | null, nombre: string) => void;
}

export function TerminoDetalleModal({
  termino,
  onClose,
  onAbrirSeguimiento,
}: TerminoDetalleModalProps) {
  if (!termino) return null;

  const color = colorEstadoTermino(termino.estado);
  const estadoLabel =
    termino.estado === "VENCIDO"
      ? "Término Vencido"
      : termino.estado === "POR_VENCER"
      ? "Por Vencer Urgente"
      : "Corriendo Activo";

  return (
    <Dialog.Root open={!!termino} onOpenChange={(open) => !open && onClose()}>
      <Dialog.Portal>
        <Dialog.Backdrop className="fixed inset-0 z-50 bg-ink-fixed/60 backdrop-blur-sm transition-opacity" />
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
          <Dialog.Popup className="relative max-h-[90vh] w-full max-w-xl overflow-y-auto rounded-2xl border border-line bg-surface p-6 shadow-2xl outline-none">
            {/* Botón cerrar */}
            <button
              type="button"
              onClick={onClose}
              className="absolute right-4 top-4 rounded-full p-1.5 text-ink-soft hover:bg-ink/5 hover:text-ink"
              aria-label="Cerrar modal"
            >
              <X className="h-5 w-5" />
            </button>

            {/* Cabecera */}
            <div className="flex items-start gap-3.5 pr-8">
              <div
                className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-xl ${color.bg} ${color.text} ring-1 ring-current/20`}
              >
                <Scales className="h-6 w-6" weight="fill" />
              </div>
              <div className="min-w-0">
                <div className="flex flex-wrap items-center gap-2">
                  <span
                    className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-semibold ${color.bg} ${color.text}`}
                  >
                    <span className={`h-1.5 w-1.5 rounded-full ${color.dot}`} />
                    {estadoLabel}
                  </span>
                  {termino.esOficial && (
                    <span className="rounded-full bg-gold-pale/60 px-2 py-0.5 text-[11px] font-bold text-gold-deep ring-1 ring-gold-deep/20">
                      Término Judicial Oficial
                    </span>
                  )}
                  {termino.rama && (
                    <span className="rounded-full bg-ink/[0.04] px-2 py-0.5 text-[11px] font-medium text-ink-soft">
                      {termino.rama}
                    </span>
                  )}
                </div>
                <h3 className="mt-1.5 font-display text-lg font-bold text-ink">
                  {termino.nombreCliente}
                </h3>
                {termino.radicadoId && (
                  <p className="font-mono text-xs font-semibold text-gold-deep">
                    Radicado: {termino.radicadoId}
                  </p>
                )}
              </div>
            </div>

            {/* Tarjeta de Plazo y Vencimiento */}
            <div className="mt-5 rounded-xl border border-line bg-paper/60 p-4">
              <div className="flex items-center justify-between border-b border-line/60 pb-3">
                <span className="text-xs font-medium text-ink-soft">
                  Fecha límite de vencimiento
                </span>
                <span className="font-display text-base font-bold text-ink">
                  {formatearFechaBogota(termino.fechaVencimiento)}
                </span>
              </div>
              <div className="mt-2.5 flex items-center justify-between text-xs">
                <span className="text-ink-soft">Plazo legal fijado</span>
                <span className="font-semibold text-ink">
                  {termino.diasHabiles} días hábiles ({termino.descripcion})
                </span>
              </div>
              {termino.abogadoAsignadoNombre && (
                <div className="mt-2 flex items-center justify-between text-xs">
                  <span className="text-ink-soft">Abogado responsable</span>
                  <span className="font-semibold text-ink">
                    {termino.abogadoAsignadoNombre}
                  </span>
                </div>
              )}
            </div>

            {/* Actuación de origen */}
            <div className="mt-4 rounded-xl border border-line bg-paper/30 p-4">
              <h4 className="text-xs font-bold uppercase tracking-wider text-ink-soft/80">
                Notificación / Actuación Judicial Detectada
              </h4>
              <p className="mt-1 text-sm font-semibold text-ink">
                {termino.nombreActuacion || termino.descripcion}
              </p>
              {termino.anotacionActuacion && (
                <p className="mt-1 text-xs text-ink-soft">
                  {termino.anotacionActuacion}
                </p>
              )}
              {termino.fechaActuacion && (
                <p className="mt-1.5 text-[11px] text-ink-soft/70">
                  Fecha de publicación judicial:{" "}
                  <span className="font-medium text-ink">
                    {formatearFechaBogota(termino.fechaActuacion)}
                  </span>
                </p>
              )}
            </div>

            {/* ACCIONES DIRECTAS A DOCUMENTO O NOTIFICACIÓN DE LA RAMA */}
            <div className="mt-5 space-y-2.5 border-t border-line pt-4">
              <p className="text-xs font-semibold text-ink">
                Acceso directo a la providencia y notificación oficial:
              </p>

              {/* Botón 1: Providencia Oficial PDF */}
              {termino.urlBoletinPdf ? (
                <a
                  href={termino.urlBoletinPdf}
                  target="_blank"
                  rel="noreferrer"
                  className="flex w-full items-center justify-between rounded-xl bg-gold px-4 py-3 text-sm font-bold text-ink-fixed shadow-sm transition-all hover:bg-gold-deep"
                >
                  <div className="flex items-center gap-2.5">
                    <FilePdf className="h-5 w-5 shrink-0" weight="bold" />
                    <span>Ver / Descargar Providencia Oficial (PDF)</span>
                  </div>
                  <ArrowSquareOut className="h-4 w-4 shrink-0" weight="bold" />
                </a>
              ) : (
                <div className="flex items-center justify-between rounded-xl border border-line bg-paper px-4 py-2.5 text-xs text-ink-soft">
                  <div className="flex items-center gap-2">
                    <FilePdf className="h-4 w-4 text-ink-soft" />
                    <span>
                      {termino.nombreBoletin
                        ? `Providencia: ${termino.nombreBoletin}`
                        : "Providencia descargable en proceso de indexación"}
                    </span>
                  </div>
                  <span className="text-[11px] font-semibold text-gold-deep">
                    Disponible en Seguimiento
                  </span>
                </div>
              )}

              {/* Botón 2: Notificación en Rama Judicial (CPNU / SAMAI) */}
              {termino.enlaceRamaJudicial && (
                <a
                  href={termino.enlaceRamaJudicial}
                  target="_blank"
                  rel="noreferrer"
                  className="flex w-full items-center justify-between rounded-xl border border-line bg-paper px-4 py-3 text-sm font-semibold text-ink transition-all hover:border-gold hover:bg-gold-pale/20"
                >
                  <div className="flex items-center gap-2.5">
                    <Buildings className="h-5 w-5 shrink-0 text-gold-deep" weight="fill" />
                    <span>
                      Abrir Notificación en la Rama Judicial (
                      {termino.rama?.includes("SAMAI")
                        ? "SAMAI Consejo de Estado"
                        : "CPNU Rama Judicial"}
                      )
                    </span>
                  </div>
                  <ArrowSquareOut className="h-4 w-4 shrink-0 text-ink-soft" />
                </a>
              )}

              {/* Botón 3: Ver Seguimiento Dual en SIE */}
              {onAbrirSeguimiento && (
                <button
                  type="button"
                  onClick={() => {
                    onClose();
                    onAbrirSeguimiento(
                      termino.casoId,
                      termino.radicadoId,
                      termino.nombreCliente,
                    );
                  }}
                  className="flex w-full items-center justify-between rounded-xl border border-line bg-paper px-4 py-2.5 text-xs font-semibold text-ink-soft transition-all hover:border-line hover:bg-ink/[0.03] hover:text-ink"
                >
                  <div className="flex items-center gap-2">
                    <ClockCounterClockwise className="h-4 w-4 text-ink-soft" />
                    <span>Ver Expediente y Línea de Tiempo Completa en SIE</span>
                  </div>
                  <span className="font-semibold text-gold-deep">Abrir →</span>
                </button>
              )}
            </div>

            <div className="mt-5 flex justify-end">
              <AdminButton variant="secondary" onClick={onClose}>
                Cerrar
              </AdminButton>
            </div>
          </Dialog.Popup>
        </div>
      </Dialog.Portal>
    </Dialog.Root>
  );
}
