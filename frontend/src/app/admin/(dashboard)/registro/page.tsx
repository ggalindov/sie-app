"use client";

import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import { ArrowsClockwise, CaretLeft, CaretRight, CheckCircle, XCircle } from "@phosphor-icons/react";
import {
  listarRegistroSistema,
  ApiError,
  type PaginaRegistroSistema,
  type TipoRegistroSistema,
} from "@/lib/admin-api";
import { AdminPageHeader, AdminCard, AdminButton, Badge, EmptyState, AdminLoader } from "@/components/admin/ui";

function formatearFechaHora(iso: string) {
  return new Date(iso).toLocaleString("es-CO", {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

const TIPOS_FILTRO: { valor: TipoRegistroSistema | "TODOS"; label: string }[] = [
  { valor: "TODOS", label: "Todos" },
  { valor: "SINCRONIZACION_CASOS", label: "Sincronización de casos" },
  { valor: "ENVIO_NOTIFICACIONES_CASOS", label: "Notificaciones de casos" },
  { valor: "SINCRONIZACION_COBROS", label: "Sincronización de cobros" },
  { valor: "ENVIO_RECORDATORIOS_COBROS", label: "Recordatorios de cobro" },
  { valor: "RECORDATORIO_CITA", label: "Recordatorios de cita" },
  { valor: "BOLETIN_ENVIADO", label: "Boletín" },
  // Pedido explícito del usuario: además de los procesos automáticos/masivos de arriba, el
  // registro también cubre eventos de seguridad y administración (inicios de sesión, altas
  // y cambios de estado de usuarios internos).
  { valor: "INICIO_SESION", label: "Inicios de sesión" },
  { valor: "USUARIO_CREADO", label: "Usuarios creados" },
  { valor: "USUARIO_ACTIVO_CAMBIADO", label: "Cambios de estado de usuario" },
];

export default function RegistroSistemaPage() {
  const [pagina, setPagina] = useState<PaginaRegistroSistema | null>(null);
  const [numeroPagina, setNumeroPagina] = useState(0);
  const [filtroTipo, setFiltroTipo] = useState<TipoRegistroSistema | "TODOS">("TODOS");
  const [cargando, setCargando] = useState(false);

  const cargar = useCallback((tipo: TipoRegistroSistema | "TODOS", num: number) => {
    setCargando(true);
    listarRegistroSistema({ tipo: tipo === "TODOS" ? undefined : tipo, pagina: num, tamano: 30 })
      .then(setPagina)
      .catch((err) => toast.error(err instanceof ApiError ? err.message : "No se pudo cargar el registro del sistema."))
      .finally(() => setCargando(false));
  }, []);

  useEffect(() => {
    cargar(filtroTipo, numeroPagina);
  }, [cargar, filtroTipo, numeroPagina]);

  function cambiarFiltro(tipo: TipoRegistroSistema | "TODOS") {
    setFiltroTipo(tipo);
    setNumeroPagina(0);
  }

  return (
    <div>
      <AdminPageHeader
        title="Registro del sistema"
        description="Bitácora de todos los procesos que ejecuta el sistema: sincronizaciones con las hojas de la firma, envíos de notificaciones y recordatorios, boletines, inicios de sesión y cambios sobre usuarios internos -- con fecha, hora y si tuvieron éxito o no."
        action={
          <AdminButton onClick={() => cargar(filtroTipo, numeroPagina)} disabled={cargando}>
            <ArrowsClockwise className={`h-4 w-4 ${cargando ? "admin-loader-anillo" : ""}`} weight="bold" />
            {cargando ? "Actualizando..." : "Actualizar"}
          </AdminButton>
        }
      />

      <div className="mt-6 flex flex-wrap gap-2">
        {TIPOS_FILTRO.map((t) => (
          <button
            key={t.valor}
            type="button"
            onClick={() => cambiarFiltro(t.valor)}
            className={`rounded-full px-4 py-2 text-sm transition-colors ${
              filtroTipo === t.valor ? "bg-ink text-paper" : "bg-ink/5 text-ink-soft hover:bg-ink/10"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>

      {pagina === null ? (
        <AdminLoader />
      ) : pagina.content.length === 0 ? (
        <EmptyState
          title="Sin registros todavía"
          description="Acá aparecerá cada sincronización, envío masivo y recordatorio que el sistema ejecute, con su resultado."
        />
      ) : (
        <>
          <div className="mt-6 space-y-3">
            {pagina.content.map((r) => (
              <AdminCard key={r.id} className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-center gap-2">
                    <Badge tone="gold">{r.tipoVisible}</Badge>
                    {r.exitoso ? (
                      <span className="inline-flex items-center gap-1.5 text-xs font-medium text-emerald-700">
                        <CheckCircle weight="fill" className="h-4 w-4" />
                        Sin errores
                      </span>
                    ) : (
                      <span className="inline-flex items-center gap-1.5 text-xs font-medium text-red-700">
                        <XCircle weight="fill" className="h-4 w-4" />
                        Con errores
                      </span>
                    )}
                  </div>
                  <p className="mt-2 text-sm text-ink">{r.descripcion}</p>
                  {r.detalle && <p className="mt-1 text-xs text-ink-soft">{r.detalle}</p>}
                </div>
                <p className="shrink-0 font-mono text-xs text-ink-soft">{formatearFechaHora(r.fechaHora)}</p>
              </AdminCard>
            ))}
          </div>

          <div className="mt-6 flex items-center justify-between">
            <p className="text-xs text-ink-soft">
              Página {pagina.number + 1} de {Math.max(pagina.totalPages, 1)} -- {pagina.totalElements} registro(s) en total
            </p>
            <div className="flex gap-2">
              <AdminButton
                variant="ghost"
                onClick={() => setNumeroPagina((n) => Math.max(n - 1, 0))}
                disabled={pagina.number === 0}
              >
                <CaretLeft className="h-4 w-4" weight="bold" />
                Anterior
              </AdminButton>
              <AdminButton
                variant="ghost"
                onClick={() => setNumeroPagina((n) => n + 1)}
                disabled={pagina.number + 1 >= pagina.totalPages}
              >
                Siguiente
                <CaretRight className="h-4 w-4" weight="bold" />
              </AdminButton>
            </div>
          </div>
        </>
      )}
    </div>
  );
}
