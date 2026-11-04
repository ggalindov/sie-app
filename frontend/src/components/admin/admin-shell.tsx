"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import {
  ChartBar,
  ChartLineUp,
  Envelope,
  Article,
  Users,
  SignOut,
  List,
  X,
  UserCircle,
  Megaphone,
  Quotes,
  Question,
  Briefcase,
  CheckSquare,
  CurrencyCircleDollar,
  ClockCounterClockwise,
  CalendarBlank,
  AddressBook,
  FileText,
  Flag,
  Binoculars,
  FilePdf,
  ChartPieSlice,
  ShieldCheck,
} from "@phosphor-icons/react";
import { useAuth } from "@/lib/auth-context";
import { AdminLoader } from "@/components/admin/ui";
import { NotificacionesPanel } from "@/components/admin/notificaciones-panel";
import { BuscadorGlobal } from "@/components/admin/buscador-global";
import { listarSolicitudes, listarPqrs, listarCasos, listarCasosPrivados, listarCobros, listarTareasPendientes, listarProcesosDetectados } from "@/lib/admin-api";

type Rol = "ADMIN_GENERAL" | "ABOGADO";

type NavItem = {
  href: string;
  label: string;
  // Una línea explicando qué hay en esa pantalla -- pedido explícito del usuario: "mejor
  // organizado, descripciones y demas". Nunca un texto genérico tipo "Gestiona tus X", sino
  // lo que de verdad hay ahí (mismo criterio de honestidad que el resto del proyecto).
  descripcion: string;
  icon: typeof ChartBar;
  roles: Rol[];
  // Clave para buscar el contador dinámico en el mapa `contadores` de AdminShell (ver más
  // abajo) -- no todos los ítems tienen uno: solo donde existe un número real de "pendientes
  // de revisar" que vale la pena mostrar de un vistazo sin entrar. Nunca se inventa un
  // contador para un ítem que no tiene un estado "pendiente" real (ej. Blog, Usuarios).
  contadorId?: string;
};

type NavGroup = {
  titulo: string;
  items: NavItem[];
};

// Reorganizado en grupos más pequeños y temáticos (pedido explícito del usuario: "mejor
// organizado") -- antes "Gestión" mezclaba 9 ítems sin relación clara entre sí (captación de
// leads, seguimiento judicial y trabajo interno, todo junto). Ahora cada grupo responde una
// sola pregunta: "¿de dónde salen los clientes?", "¿qué pasa con sus casos?", "¿qué trabajo
// interno hay pendiente?". Un mismo grupo puede mezclar ítems visibles para ambos roles con
// ítems solo-admin -- el filtrado por rol pasa primero y un grupo que quede vacío después de
// filtrar simplemente no se renderiza (ver AdminShell.grupos).
const navGroups: NavGroup[] = [
  {
    titulo: "General",
    items: [
      { href: "/admin", label: "Resumen", descripcion: "Panorama general de la firma, de un vistazo.", icon: ChartBar, roles: ["ADMIN_GENERAL", "ABOGADO"] },
      { href: "/admin/estadisticas", label: "Estadísticas", descripcion: "Leads, conversión y casos activos en el tiempo.", icon: ChartLineUp, roles: ["ADMIN_GENERAL", "ABOGADO"] },
    ],
  },
  {
    titulo: "Clientes y captación",
    items: [
      { href: "/admin/crm", label: "CRM / Clientes", descripcion: "Ficha completa de cada cliente y su historial.", icon: AddressBook, roles: ["ADMIN_GENERAL", "ABOGADO"] },
      { href: "/admin/solicitudes", label: "Solicitudes", descripcion: "Leads nuevos del formulario y del chatbot.", icon: Envelope, roles: ["ADMIN_GENERAL", "ABOGADO"], contadorId: "solicitudes" },
      { href: "/admin/calendario", label: "Calendario", descripcion: "Citas agendadas con cada cliente.", icon: CalendarBlank, roles: ["ADMIN_GENERAL", "ABOGADO"] },
    ],
  },
  {
    titulo: "Casos y seguimiento judicial",
    items: [
      { href: "/admin/casos", label: "Casos", descripcion: "Expedientes activos con seguimiento judicial 24/7.", icon: Briefcase, roles: ["ADMIN_GENERAL", "ABOGADO"], contadorId: "casos" },
      { href: "/admin/casos-privados", label: "Casos Privados y Cifrados", descripcion: "Bóveda ultra sensible de Gerencia con cifrado AES-256.", icon: ShieldCheck, roles: ["ADMIN_GENERAL"], contadorId: "casosPrivados" },
      // Pedido explícito del usuario: "implementa todas las mejoras identificadas"
      // (comparando contra Lupa Jurídica) -- procesos que el CPNU encontró buscando por el
      // nombre de un cliente, que todavía no están en nuestra tabla de casos. Ver
      // DeteccionProcesosNuevosService en el backend.
      { href: "/admin/procesos-detectados", label: "Procesos nuevos", descripcion: "Procesos que el sistema detectó por nombre, sin crear.", icon: Binoculars, roles: ["ADMIN_GENERAL", "ABOGADO"], contadorId: "procesosDetectados" },
      // Pedido explícito del usuario: "el sistema debe de traer y darle la opcion de todos
      // los casos de ver/descargar todos los boletines que esten disponibles" -- todos los
      // documentos oficiales ya cacheados, de todos los casos, en una sola pantalla.
      { href: "/admin/boletines", label: "Boletines", descripcion: "Documentos oficiales descargados de todos los casos.", icon: FilePdf, roles: ["ADMIN_GENERAL", "ABOGADO"] },
      { href: "/admin/documentos", label: "Documentos para firma", descripcion: "Documentos en el motor de firma electrónica.", icon: FileText, roles: ["ADMIN_GENERAL", "ABOGADO"] },
      // Pedido explícito del usuario (2026-11-04, auditoría en vivo de LUPA/IUS): réplica de
      // su "Control diario procesal" -- conteo de admisiones, inadmisiones, audiencias,
      // sentencias y terminaciones por rango de fecha, con alerta de posible inasistencia a
      // audiencia.
      { href: "/admin/control-diario", label: "Control diario procesal", descripcion: "Admisiones, audiencias y terminaciones por día.", icon: ChartPieSlice, roles: ["ADMIN_GENERAL", "ABOGADO"] },
    ],
  },
  {
    titulo: "Trabajo interno",
    items: [
      { href: "/admin/pqrs", label: "PQRS", descripcion: "Peticiones, quejas, reclamos y sugerencias.", icon: Flag, roles: ["ADMIN_GENERAL", "ABOGADO"], contadorId: "pqrs" },
      // Pedido explícito del usuario: "El Admin si tendra la seccion de la nav de tareas para
      // que vea todas las tareas del sistema" -- un ABOGADO ya no tiene esta entrada en el
      // menú, ve sus propias tareas pendientes desde la campanita de notificaciones (ver
      // NotificacionesPanel), disponible en todos los paneles para ambos roles.
      { href: "/admin/tareas", label: "Tareas", descripcion: "Pendientes de todos los abogados de la firma.", icon: CheckSquare, roles: ["ADMIN_GENERAL"], contadorId: "tareas" },
      { href: "/admin/cobros", label: "Cobros Pendientes", descripcion: "Clientes con honorarios por cobrar este mes.", icon: CurrencyCircleDollar, roles: ["ADMIN_GENERAL"], contadorId: "cobros" },
    ],
  },
  {
    titulo: "Contenido del sitio",
    items: [
      { href: "/admin/articulos", label: "Blog y Noticias", descripcion: "Artículos publicados en el sitio público.", icon: Article, roles: ["ADMIN_GENERAL", "ABOGADO"] },
      { href: "/admin/faq", label: "Preguntas frecuentes", descripcion: "FAQ que se muestra en el sitio público.", icon: Question, roles: ["ADMIN_GENERAL", "ABOGADO"] },
      { href: "/admin/testimonios", label: "Testimonios", descripcion: "Moderación de testimonios enviados por clientes.", icon: Quotes, roles: ["ADMIN_GENERAL"] },
    ],
  },
  {
    titulo: "Administración",
    items: [
      { href: "/admin/marketing", label: "Marketing", descripcion: "Suscriptores y boletines automáticos enviados.", icon: Megaphone, roles: ["ADMIN_GENERAL"] },
      { href: "/admin/usuarios", label: "Usuarios", descripcion: "Cuentas internas de abogados y administradores.", icon: Users, roles: ["ADMIN_GENERAL"] },
      { href: "/admin/registro", label: "Registro del sistema", descripcion: "Bitácora de todo lo que el sistema hace solo.", icon: ClockCounterClockwise, roles: ["ADMIN_GENERAL"] },
    ],
  },
];

export function AdminShell({ children }: { children: ReactNode }) {
  const { sesion, cargando, logout } = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  const [menuAbierto, setMenuAbierto] = useState(false);
  // Pedido explícito del usuario: "mas funcional" -- contadores reales de "pendiente de
  // revisar" junto a cada ítem que de verdad tiene uno (ver contadorId en navGroups). Se
  // cargan una sola vez al abrir sesión (AdminShell envuelve todo /admin/**, nunca se
  // desmonta entre rutas), nunca en cada clic de navegación. Si una llamada falla, ese
  // contador simplemente no aparece (silencioso a propósito, mismo criterio que
  // NotificacionesPanel: un contador es un dato secundario, no debe bloquear ni interrumpir
  // con un toast de error la navegación del panel).
  const [contadores, setContadores] = useState<Record<string, number>>({});

  useEffect(() => {
    if (!sesion) return;
    listarSolicitudes({ estado: "NUEVO" })
      .then((rows) => setContadores((c) => ({ ...c, solicitudes: rows.length })))
      .catch(() => {});
    listarPqrs()
      .then((rows) => setContadores((c) => ({ ...c, pqrs: rows.filter((r) => r.estado === "RECIBIDO").length })))
      .catch(() => {});
    listarProcesosDetectados()
      .then((rows) => setContadores((c) => ({ ...c, procesosDetectados: rows.length })))
      .catch(() => {});
    listarCasos()
      .then((rows) => setContadores((c) => ({ ...c, casos: rows.filter((r) => r.tieneActualizacionSinRevisar).length })))
      .catch(() => {});
    if (sesion.rol === "ADMIN_GENERAL") {
      listarTareasPendientes()
        .then((rows) => setContadores((c) => ({ ...c, tareas: rows.length })))
        .catch(() => {});
      // pagoEsteMes === true es el único estado que de verdad significa "ya no hay nada
      // pendiente este mes" -- null (nunca se le ha cobrado) cuenta como pendiente, igual
      // que false.
      listarCobros()
        .then((rows) => setContadores((c) => ({ ...c, cobros: rows.filter((r) => r.pagoEsteMes !== true).length })))
        .catch(() => {});
      listarCasosPrivados()
        .then((rows) => setContadores((c) => ({ ...c, casosPrivados: rows.length })))
        .catch(() => {});
    }
  }, [sesion]);

  useEffect(() => {
    if (!cargando && !sesion) {
      router.replace("/admin/login");
    }
  }, [cargando, sesion, router]);

  useEffect(() => {
    setMenuAbierto(false);
  }, [pathname]);

  if (cargando || !sesion) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-paper">
        <AdminLoader />
      </div>
    );
  }

  const grupos = navGroups
    .map((grupo) => ({
      titulo: grupo.titulo,
      items: grupo.items.filter((item) => (item.roles as readonly string[]).includes(sesion.rol)),
    }))
    // un grupo entero (ej. "Administración") puede quedar sin ítems para el rol ABOGADO:
    // se descarta en vez de renderizar un título de sección vacío
    .filter((grupo) => grupo.items.length > 0);

  return (
    // h-dvh + overflow-hidden en el contenedor raíz (en vez del min-h-screen anterior, que
    // dejaba crecer la fila completa con el contenido y arrastraba el sidebar en el scroll
    // de la página) -- pedido explícito del usuario: en una lista larga (solicitudes, casos,
    // cobros) el panel lateral debe quedar fijo y que solo se desplace el contenido. Cada
    // columna maneja su propio scroll interno (nav del sidebar y <main>), con min-h-0 para
    // que los hijos flex puedan encogerse por debajo de su alto de contenido -- sin eso el
    // min-height:auto por defecto de un flex item anula el overflow-y-auto interno.
    <div className="flex h-dvh overflow-hidden bg-paper">
      {/* Sidebar desktop: semi-oscuro con degradado sutil (de un carbón cálido a un negro
          casi puro) en vez de un bg-night plano -- pedido explícito del usuario tras ver el
          primer intento ("horrible, algo más elegante"): un color sólido liso se veía plano
          y barato, el degradado le da profundidad real. El filo dorado tenue a la derecha
          (shadow inset) remata el borde en vez de dejarlo cortado en seco contra el panel
          claro. El resto del panel admin se queda en la paleta clara original a propósito
          (ver CLAUDE.md 5.1/5.8). */}
      <aside className="hidden w-72 min-h-0 shrink-0 flex-col bg-gradient-to-b from-[#1e1a12] via-[#15120d] to-[#0a0907] shadow-[inset_-1px_0_0_rgba(217,169,37,0.1)] lg:flex">
        <SidebarContent grupos={grupos} pathname={pathname} sesion={sesion} onLogout={logout} contadores={contadores} />
      </aside>

      {/* Sidebar móvil */}
      {menuAbierto && (
        <div className="fixed inset-0 z-50 flex lg:hidden">
          <div className="flex w-80 min-h-0 flex-col bg-gradient-to-b from-[#1e1a12] via-[#15120d] to-[#0a0907] shadow-[inset_-1px_0_0_rgba(217,169,37,0.1)]">
            <SidebarContent grupos={grupos} pathname={pathname} sesion={sesion} onLogout={logout} contadores={contadores} />
          </div>
          <button
            type="button"
            aria-label="Cerrar menú"
            onClick={() => setMenuAbierto(false)}
            className="flex-1 bg-ink/40"
          />
        </div>
      )}

      <div className="flex min-h-0 min-w-0 flex-1 flex-col">
        <header className="flex h-16 shrink-0 items-center justify-between border-b border-line bg-surface px-4 lg:hidden">
          <div className="flex items-center gap-2">
            <Image src="/marca/logo.png" alt="" width={28} height={28} className="h-7 w-7 object-contain" />
            <span className="font-display text-sm font-semibold">Panel</span>
          </div>
          <div className="flex items-center gap-1">
            <NotificacionesPanel variante="clara" />
            <button
              type="button"
              aria-label="Abrir menú"
              onClick={() => setMenuAbierto((v) => !v)}
              className="flex h-10 w-10 items-center justify-center rounded-full text-ink"
            >
              {menuAbierto ? <X className="h-5 w-5" /> : <List className="h-5 w-5" />}
            </button>
          </div>
        </header>

        {/* Buscador total del panel: una sola fila compartida entre mobile y desktop (no
            existía un topbar común antes de esto -- en desktop solo había el header interno
            del sidebar, sin espacio de sobra) para no duplicar el input en dos lugares. */}
        <div className="flex shrink-0 items-center border-b border-line bg-surface px-4 py-3 sm:px-8">
          <BuscadorGlobal />
        </div>

        <main className="min-h-0 min-w-0 flex-1 overflow-x-hidden overflow-y-auto px-4 py-8 sm:px-8 lg:py-10">
          {children}
        </main>
      </div>
    </div>
  );
}

function SidebarContent({
  grupos,
  pathname,
  sesion,
  onLogout,
  contadores,
}: {
  grupos: NavGroup[];
  pathname: string | null;
  sesion: { nombre: string; rol: string };
  onLogout: () => void;
  contadores: Record<string, number>;
}) {
  return (
    <>
      <div className="relative flex h-16 shrink-0 items-center gap-2.5 overflow-hidden border-b border-white/10 px-5">
        <div
          aria-hidden="true"
          className="pointer-events-none absolute inset-0"
          style={{ backgroundImage: "radial-gradient(220px circle at 0% 0%, rgba(217,169,37,0.22), transparent 65%)" }}
        />
        <span className="relative flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-gold/15 ring-1 ring-gold/40">
          <Image src="/marca/logo.png" alt="" width={22} height={22} className="h-[22px] w-[22px] object-contain" />
        </span>
        <span className="relative font-display text-base font-semibold text-night-ink">Panel</span>
        {/* Campanita de notificaciones (pedido explícito del usuario, mismo header de la
            captura que mostró): tareas pendientes y reporte general, disponible para ambos
            roles en todos los paneles -- ver NotificacionesPanel. */}
        <span className="relative ml-auto">
          <NotificacionesPanel />
        </span>
      </div>

      <nav className="sidebar-scroll min-h-0 flex-1 overflow-y-auto px-3 py-5">
        {grupos.map((grupo, i) => (
          <div key={grupo.titulo} className={i > 0 ? "mt-5 border-t border-white/10 pt-4" : ""}>
            <p className="px-3 pb-2 text-[11px] font-semibold uppercase tracking-[0.12em] text-night-ink/40">
              {grupo.titulo}
            </p>
            <div className="space-y-1">
              {grupo.items.map((item) => {
                const activo = pathname === item.href || (item.href !== "/admin" && pathname?.startsWith(item.href));
                const Icon = item.icon;
                const contador = item.contadorId ? contadores[item.contadorId] : undefined;
                return (
                  <Link
                    key={item.href}
                    href={item.href}
                    className={`group relative flex items-start gap-3 rounded-xl py-2.5 pl-3 pr-3 transition-colors ${
                      activo ? "bg-gold/12 text-night-ink" : "text-night-ink/65 hover:bg-white/5 hover:text-night-ink"
                    }`}
                  >
                    {/* barra de acento a la izquierda: más claro que solo un fondo tenue,
                        y consistente con el filo dorado que ya usa el sitio público */}
                    <span
                      className={`absolute left-0 top-1/2 h-6 w-[3px] -translate-y-1/2 rounded-r-full bg-gold transition-transform duration-300 ${
                        activo ? "scale-y-100" : "scale-y-0"
                      }`}
                    />
                    <span
                      className={`mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg transition-colors ${
                        activo ? "bg-gold text-ink-fixed" : "bg-white/5 text-night-ink/70 group-hover:bg-white/10"
                      }`}
                    >
                      <Icon weight={activo ? "fill" : "light"} className="h-4 w-4" />
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="flex items-center justify-between gap-2">
                        <span className={`truncate text-sm ${activo ? "font-semibold" : "font-medium"}`}>{item.label}</span>
                        {/* Contador real de "pendiente de revisar" (pedido explícito del
                            usuario: "mas funcional") -- solo aparece cuando hay algo que
                            contar, nunca un 0 vacío ocupando espacio. */}
                        {!!contador && (
                          <span className="flex h-[18px] min-w-[18px] shrink-0 items-center justify-center rounded-full bg-gold px-1 text-[10px] font-bold text-ink-fixed">
                            {contador > 99 ? "99+" : contador}
                          </span>
                        )}
                      </span>
                      {/* Descripción de una línea -- pedido explícito del usuario:
                          "descripciones y demas", para entender qué hay en cada pantalla
                          sin tener que entrar primero. */}
                      <span className="mt-0.5 block truncate text-[11.5px] leading-snug text-night-ink/45">
                        {item.descripcion}
                      </span>
                    </span>
                  </Link>
                );
              })}
            </div>
          </div>
        ))}
      </nav>

      <div className="border-t border-white/10 p-3">
        <Link
          href="/admin/perfil"
          className={`flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm transition-colors ${
            pathname === "/admin/perfil" ? "bg-gold/12 font-medium text-night-ink" : "text-night-ink/65 hover:bg-white/5 hover:text-night-ink"
          }`}
        >
          <UserCircle weight="light" className="h-5 w-5 shrink-0" />
          <span className="min-w-0 flex-1 truncate">{sesion.nombre}</span>
        </Link>
        <p className="mt-1 truncate px-3 text-xs text-night-ink/45">
          {sesion.rol === "ADMIN_GENERAL" ? "Administrador General" : "Abogado"}
        </p>
        <button
          type="button"
          onClick={onLogout}
          className="mt-2 flex w-full items-center gap-3 rounded-xl px-3 py-2.5 text-sm text-night-ink/65 transition-colors hover:bg-white/5 hover:text-night-ink"
        >
          <SignOut weight="light" className="h-5 w-5 shrink-0" />
          Cerrar sesión
        </button>
      </div>
    </>
  );
}
