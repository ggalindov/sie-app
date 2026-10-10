"use client";

import { useCallback, useEffect, useState } from "react";
import { toast } from "sonner";
import {
  ArrowsClockwise,
  CaretLeft,
  CaretRight,
  CheckCircle,
  EnvelopeSimple,
  WhatsappLogo,
  XCircle,
} from "@phosphor-icons/react";
import {
  listarRegistroSistema,
  listarRegistroEnvios,
  ApiError,
  type PaginaRegistroSistema,
  type PaginaRegistroEnvios,
  type TipoRegistroSistema,
  type CategoriaRegistroSistema,
  type CanalEnvio,
  type TipoEnvio,
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

// Cada categoría recibe un tono de píldora propio (ver Badge en ui.tsx) para que un vistazo
// rápido a la lista ya distinga el tipo de evento sin tener que leer el texto -- pedido
// explícito del usuario: "pon categorias para que sea mas intuitivo de entender".
const TONO_CATEGORIA: Record<CategoriaRegistroSistema, "gold" | "neutral" | "danger" | "warning"> = {
  COMUNICACIONES: "gold",
  SINCRONIZACION: "neutral",
  SEGURIDAD: "danger",
  GESTION_INTERNA: "warning",
};

// Filtros de "Procesos del sistema" agrupados por categoría (antes era una sola fila plana
// de 11 chips sin ningún orden). "Todos" se renderiza aparte, siempre primero.
const GRUPOS_FILTRO_PROCESOS: { categoria: string; tipos: { valor: TipoRegistroSistema; label: string }[] }[] = [
  {
    categoria: "Comunicaciones automáticas",
    tipos: [
      { valor: "ENVIO_NOTIFICACIONES_CASOS", label: "Notificaciones de casos" },
      { valor: "REPORTE_SEMANAL_CASOS", label: "Reporte semanal de casos" },
      { valor: "ENVIO_RECORDATORIOS_COBROS", label: "Recordatorios de cobro" },
      { valor: "RECORDATORIO_CITA", label: "Recordatorios de cita" },
      { valor: "BOLETIN_ENVIADO", label: "Boletín" },
    ],
  },
  {
    categoria: "Sincronización de datos",
    tipos: [
      { valor: "SINCRONIZACION_CASOS", label: "Sincronización de casos" },
      { valor: "SINCRONIZACION_COBROS", label: "Sincronización de cobros" },
      { valor: "REINICIO_MENSUAL_COBROS", label: "Reinicio mensual de cobros" },
    ],
  },
  {
    categoria: "Seguridad y usuarios",
    tipos: [
      { valor: "INICIO_SESION", label: "Inicios de sesión" },
      { valor: "USUARIO_CREADO", label: "Usuarios creados" },
      { valor: "USUARIO_ACTIVO_CAMBIADO", label: "Cambios de estado de usuario" },
    ],
  },
  {
    categoria: "Gestión interna",
    tipos: [
      { valor: "CONSULTA_ESTADO_CASO", label: "Consultas de estado de caso" },
      { valor: "GESTION_CRM", label: "Gestión CRM" },
      { valor: "GESTION_TAREAS", label: "Gestión de tareas" },
    ],
  },
];

const TIPOS_FILTRO_ENVIOS: { valor: TipoEnvio; label: string }[] = [
  { valor: "CODIGO_CASO", label: "Código de caso" },
  { valor: "REPORTE_CASO", label: "Reporte de caso" },
  { valor: "RECORDATORIO_COBRO", label: "Recordatorio de cobro" },
  { valor: "CONFIRMACION_CITA", label: "Confirmación de cita" },
  { valor: "RECORDATORIO_CITA", label: "Recordatorio de cita" },
  { valor: "SOLICITUD_CONFIRMACION", label: "Confirmación de solicitud" },
  { valor: "SOLICITUD_RESPUESTA", label: "Respuesta a solicitud" },
  { valor: "NOTIFICACION_INTERNA", label: "Notificación interna" },
  { valor: "PUBLICACION_BLOG", label: "Aviso de publicación" },
  { valor: "AVISO_REDES_SOCIALES", label: "Aviso a redes sociales" },
  { valor: "BOLETIN", label: "Boletín" },
  { valor: "BIENVENIDA_BOLETIN", label: "Bienvenida al boletín" },
];

function Chip({ activo, onClick, children }: { activo: boolean; onClick: () => void; children: React.ReactNode }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`rounded-full px-4 py-2 text-sm transition-colors ${
        activo ? "bg-ink text-paper" : "bg-ink/5 text-ink-soft hover:bg-ink/10"
      }`}
    >
      {children}
    </button>
  );
}

function ResultadoBadge({ exitoso }: { exitoso: boolean }) {
  return exitoso ? (
    <span className="inline-flex items-center gap-1.5 text-xs font-medium text-emerald-700">
      <CheckCircle weight="fill" className="h-4 w-4" />
      Sin errores
    </span>
  ) : (
    <span className="inline-flex items-center gap-1.5 text-xs font-medium text-red-700">
      <XCircle weight="fill" className="h-4 w-4" />
      Con errores
    </span>
  );
}

function Paginador({
  pagina,
  onAnterior,
  onSiguiente,
}: {
  pagina: { number: number; totalPages: number; totalElements: number };
  onAnterior: () => void;
  onSiguiente: () => void;
}) {
  return (
    <div className="mt-6 flex items-center justify-between">
      <p className="text-xs text-ink-soft">
        Página {pagina.number + 1} de {Math.max(pagina.totalPages, 1)} -- {pagina.totalElements} registro(s) en total
      </p>
      <div className="flex gap-2">
        <AdminButton variant="ghost" onClick={onAnterior} disabled={pagina.number === 0}>
          <CaretLeft className="h-4 w-4" weight="bold" />
          Anterior
        </AdminButton>
        <AdminButton variant="ghost" onClick={onSiguiente} disabled={pagina.number + 1 >= pagina.totalPages}>
          Siguiente
          <CaretRight className="h-4 w-4" weight="bold" />
        </AdminButton>
      </div>
    </div>
  );
}

function TabProcesos() {
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
      <div className="flex items-center justify-between">
        <p className="text-sm text-ink-soft">
          Cada sincronización, envío masivo, recordatorio programado y evento de seguridad que ejecuta el sistema,
          agrupado por categoría.
        </p>
        <AdminButton onClick={() => cargar(filtroTipo, numeroPagina)} disabled={cargando}>
          <ArrowsClockwise className={`h-4 w-4 ${cargando ? "admin-loader-anillo" : ""}`} weight="bold" />
          {cargando ? "Actualizando..." : "Actualizar"}
        </AdminButton>
      </div>

      <div className="mt-5 flex flex-wrap gap-2">
        <Chip activo={filtroTipo === "TODOS"} onClick={() => cambiarFiltro("TODOS")}>
          Todos
        </Chip>
      </div>
      <div className="mt-3 space-y-3">
        {GRUPOS_FILTRO_PROCESOS.map((grupo) => (
          <div key={grupo.categoria} className="flex flex-wrap items-center gap-2">
            <span className="w-full text-[11px] font-medium uppercase tracking-[0.08em] text-ink-soft/60 sm:w-auto">
              {grupo.categoria}
            </span>
            {grupo.tipos.map((t) => (
              <Chip key={t.valor} activo={filtroTipo === t.valor} onClick={() => cambiarFiltro(t.valor)}>
                {t.label}
              </Chip>
            ))}
          </div>
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
                    <Badge tone={TONO_CATEGORIA[r.categoria]}>{r.tipoVisible}</Badge>
                    <span className="text-[11px] text-ink-soft/50">{r.categoriaVisible}</span>
                    <ResultadoBadge exitoso={r.exitoso} />
                  </div>
                  <p className="mt-2 text-sm text-ink">{r.descripcion}</p>
                  {r.detalle && <p className="mt-1 text-xs text-ink-soft">{r.detalle}</p>}
                </div>
                <p className="shrink-0 font-mono text-xs text-ink-soft">{formatearFechaHora(r.fechaHora)}</p>
              </AdminCard>
            ))}
          </div>

          <Paginador
            pagina={pagina}
            onAnterior={() => setNumeroPagina((n) => Math.max(n - 1, 0))}
            onSiguiente={() => setNumeroPagina((n) => n + 1)}
          />
        </>
      )}
    </div>
  );
}

function TabEnvios() {
  const [pagina, setPagina] = useState<PaginaRegistroEnvios | null>(null);
  const [numeroPagina, setNumeroPagina] = useState(0);
  const [filtroCanal, setFiltroCanal] = useState<CanalEnvio | "TODOS">("TODOS");
  const [filtroTipo, setFiltroTipo] = useState<TipoEnvio | "TODOS">("TODOS");
  const [cargando, setCargando] = useState(false);

  const cargar = useCallback((canal: CanalEnvio | "TODOS", tipo: TipoEnvio | "TODOS", num: number) => {
    setCargando(true);
    listarRegistroEnvios({
      canal: canal === "TODOS" ? undefined : canal,
      tipo: tipo === "TODOS" ? undefined : tipo,
      pagina: num,
      tamano: 30,
    })
      .then(setPagina)
      .catch((err) => toast.error(err instanceof ApiError ? err.message : "No se pudo cargar el registro de envíos."))
      .finally(() => setCargando(false));
  }, []);

  useEffect(() => {
    cargar(filtroCanal, filtroTipo, numeroPagina);
  }, [cargar, filtroCanal, filtroTipo, numeroPagina]);

  function cambiarCanal(canal: CanalEnvio | "TODOS") {
    setFiltroCanal(canal);
    setNumeroPagina(0);
  }

  function cambiarTipo(tipo: TipoEnvio | "TODOS") {
    setFiltroTipo(tipo);
    setNumeroPagina(0);
  }

  return (
    <div>
      <div className="flex items-center justify-between">
        <p className="text-sm text-ink-soft">
          Cada correo y mensaje de WhatsApp que el sistema envió, con el destinatario real -- solo visible para
          Administrador General.
        </p>
        <AdminButton onClick={() => cargar(filtroCanal, filtroTipo, numeroPagina)} disabled={cargando}>
          <ArrowsClockwise className={`h-4 w-4 ${cargando ? "admin-loader-anillo" : ""}`} weight="bold" />
          {cargando ? "Actualizando..." : "Actualizar"}
        </AdminButton>
      </div>

      <div className="mt-5 flex flex-wrap gap-2">
        <Chip activo={filtroCanal === "TODOS"} onClick={() => cambiarCanal("TODOS")}>
          Todos los canales
        </Chip>
        <Chip activo={filtroCanal === "EMAIL"} onClick={() => cambiarCanal("EMAIL")}>
          Correo
        </Chip>
        <Chip activo={filtroCanal === "WHATSAPP"} onClick={() => cambiarCanal("WHATSAPP")}>
          WhatsApp
        </Chip>
      </div>

      <div className="mt-3 flex flex-wrap gap-2">
        <Chip activo={filtroTipo === "TODOS"} onClick={() => cambiarTipo("TODOS")}>
          Todos los tipos
        </Chip>
        {TIPOS_FILTRO_ENVIOS.map((t) => (
          <Chip key={t.valor} activo={filtroTipo === t.valor} onClick={() => cambiarTipo(t.valor)}>
            {t.label}
          </Chip>
        ))}
      </div>

      {pagina === null ? (
        <AdminLoader />
      ) : pagina.content.length === 0 ? (
        <EmptyState
          title="Sin envíos todavía"
          description="Acá aparecerá cada correo y WhatsApp que el sistema envíe, con el destinatario y si se entregó o no."
        />
      ) : (
        <>
          <div className="mt-6 space-y-3">
            {pagina.content.map((r) => (
              <AdminCard key={r.id} className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                <div className="flex min-w-0 flex-1 items-start gap-3">
                  <span
                    className={`mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-full ${
                      r.canal === "EMAIL" ? "bg-ink/8 text-ink-soft" : "bg-emerald-100 text-emerald-700"
                    }`}
                  >
                    {r.canal === "EMAIL" ? (
                      <EnvelopeSimple weight="bold" className="h-4 w-4" />
                    ) : (
                      <WhatsappLogo weight="fill" className="h-4 w-4" />
                    )}
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <Badge tone="gold">{r.tipoVisible}</Badge>
                      <ResultadoBadge exitoso={r.exitoso} />
                    </div>
                    <p className="mt-2 text-sm font-medium text-ink">
                      {r.destinatarioNombre ?? "(destinatario interno)"}
                      <span className="ml-2 font-normal text-ink-soft">{r.destinatarioContacto}</span>
                    </p>
                    <p className="mt-1 text-xs text-ink-soft">{r.resumen}</p>
                  </div>
                </div>
                <p className="shrink-0 font-mono text-xs text-ink-soft">{formatearFechaHora(r.fechaHora)}</p>
              </AdminCard>
            ))}
          </div>

          <Paginador
            pagina={pagina}
            onAnterior={() => setNumeroPagina((n) => Math.max(n - 1, 0))}
            onSiguiente={() => setNumeroPagina((n) => n + 1)}
          />
        </>
      )}
    </div>
  );
}

export default function RegistroSistemaPage() {
  const [tab, setTab] = useState<"procesos" | "envios">("procesos");

  return (
    <div>
      <AdminPageHeader
        title="Registro del sistema"
        description="Bitácora de todo lo que hace el sistema por su cuenta: procesos automáticos (sincronizaciones, recordatorios, seguridad) y cada correo o WhatsApp que sale, con su destinatario."
      />

      <div className="flex gap-2 border-b border-line">
        <button
          type="button"
          onClick={() => setTab("procesos")}
          className={`border-b-2 px-1 pb-3 text-sm font-medium transition-colors ${
            tab === "procesos" ? "border-gold text-ink" : "border-transparent text-ink-soft hover:text-ink"
          }`}
        >
          Procesos del sistema
        </button>
        <button
          type="button"
          onClick={() => setTab("envios")}
          className={`border-b-2 px-1 pb-3 text-sm font-medium transition-colors ${
            tab === "envios" ? "border-gold text-ink" : "border-transparent text-ink-soft hover:text-ink"
          }`}
        >
          Correos y WhatsApp enviados
        </button>
      </div>

      <div className="mt-6">{tab === "procesos" ? <TabProcesos /> : <TabEnvios />}</div>
    </div>
  );
}
