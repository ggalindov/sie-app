"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";
import {
  Archive,
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
  CaretDown,
  CaretLeft,
  CaretRight,
} from "@phosphor-icons/react";
import { Tooltip } from "@base-ui/react/tooltip";
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
  // Pedido explícito del usuario (2026-11-06): "en el menu para todos, casos hazlo como un
  // desplegable que saque los otros" -- "Casos Privados y Cifrados" y "Casos Finalizados"
  // dejan de ser ítems hermanos sueltos y pasan a vivir anidados aquí, ocultos hasta que se
  // despliega el padre (ver SidebarContent). Nunca más de un nivel de anidamiento.
  children?: NavItem[];
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
      // Pedido explícito del usuario (2026-11-04): "para el rol de abogado elimina
      // estadisticas" -- antes al alcance de ambos roles.
      { href: "/admin/estadisticas", label: "Estadísticas", descripcion: "Leads, conversión y casos activos en el tiempo.", icon: ChartLineUp, roles: ["ADMIN_GENERAL"] },
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
      {
        href: "/admin/casos",
        label: "Casos",
        descripcion: "Expedientes activos con seguimiento judicial 24/7.",
        icon: Briefcase,
        roles: ["ADMIN_GENERAL", "ABOGADO"],
        contadorId: "casos",
        children: [
          {
            href: "/admin/casos-privados",
            label: "Casos Privados y Cifrados",
            // Pedido explícito del usuario (2026-11-05): "implementa la misma funcionalidad
            // que tenia gerencia de casos privados y cifrados, implementala para todos los
            // abogados, que cada uno tenga un espacio privado para casos personales o
            // sensibles... maximo de 20 casos". Misma pantalla para ambos roles: cada uno ve
            // SOLO su propia bóveda (Gerencia la suya, cada abogado la suya), nunca la del otro.
            descripcion: "Tu bóveda ultra sensible con cifrado AES-256 -- nadie más puede verla.",
            icon: ShieldCheck,
            roles: ["ADMIN_GENERAL", "ABOGADO"],
            contadorId: "casosPrivados",
          },
          // Pedido explícito del usuario (2026-11-05): "un boton para cada caso el cual nos
          // permita finalizarlo, y el caso se remite a un nuevo apartado de que diga casos
          // finalizados" -- de solo lectura, un caso finalizado ya no recibe vigilancia judicial.
          { href: "/admin/casos-finalizados", label: "Casos Finalizados", descripcion: "Expedientes cerrados, con el último seguimiento guardado.", icon: Archive, roles: ["ADMIN_GENERAL", "ABOGADO"] },
        ],
      },
      // Pedido explícito del usuario: "implementa todas las mejoras identificadas"
      // (comparando contra Lupa Jurídica) -- procesos que el CPNU encontró buscando por el
      // nombre de un cliente, que todavía no están en nuestra tabla de casos. Ver
      // DeteccionProcesosNuevosService en el backend.
      { href: "/admin/procesos-detectados", label: "Procesos nuevos", descripcion: "Procesos que el sistema detectó por nombre, sin crear.", icon: Binoculars, roles: ["ADMIN_GENERAL", "ABOGADO"], contadorId: "procesosDetectados" },
      // Pedido explícito del usuario: "el sistema debe de traer y darle la opcion de todos
      // los casos de ver/descargar todos los boletines que esten disponibles" -- todos los
      // documentos oficiales ya cacheados, de todos los casos, en una sola pantalla.
      // Renombrado (2026-11-06, pedido explícito del usuario, "cambia el nombre de
      // boletines"): "Boletines" se confundía con el envío masivo de correos de marketing
      // (ver /admin/marketing, que también habla de "boletines automáticos"); el nombre
      // nuevo describe lo que de verdad hay ahí.
      { href: "/admin/boletines", label: "Documentos Judiciales", descripcion: "Autos, sentencias y boletines oficiales descargados de todos los casos.", icon: FilePdf, roles: ["ADMIN_GENERAL", "ABOGADO"] },
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
      // Pedido explícito del usuario (2026-11-04): "para el rol de abogado elimina ... PQRS"
      // -- revierte la decisión anterior que la dejaba al alcance de ambos roles.
      { href: "/admin/pqrs", label: "PQRS", descripcion: "Peticiones, quejas, reclamos y sugerencias.", icon: Flag, roles: ["ADMIN_GENERAL"], contadorId: "pqrs" },
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
      // Pedido explícito del usuario (2026-11-04): "para el rol de abogado elimina ...
      // blog ... Preguntas frecuentes" -- revierte la decisión anterior que dejaba ambas al
      // alcance de los dos roles (incluida la de FAQ, "el abogado la aprueba con un clic").
      { href: "/admin/articulos", label: "Blog y Noticias", descripcion: "Artículos publicados en el sitio público.", icon: Article, roles: ["ADMIN_GENERAL"] },
      { href: "/admin/faq", label: "Preguntas frecuentes", descripcion: "FAQ que se muestra en el sitio público.", icon: Question, roles: ["ADMIN_GENERAL"] },
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
  const [contraido, setContraido] = useState(false);

  useEffect(() => {
    try {
      const guardado = localStorage.getItem("sie_admin_sidebar_collapsed");
      if (guardado !== null) {
        setContraido(guardado === "true");
      }
    } catch {}
  }, []);

  const toggleContraer = () => {
    setContraido((prev) => {
      const next = !prev;
      try {
        localStorage.setItem("sie_admin_sidebar_collapsed", String(next));
      } catch {}
      return next;
    });
  };

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      const tag = (e.target as HTMLElement)?.tagName;
      if (tag === "INPUT" || tag === "TEXTAREA" || (e.target as HTMLElement)?.isContentEditable) {
        return;
      }
      if ((e.ctrlKey || e.metaKey) && (e.key === "b" || e.key === "B")) {
        e.preventDefault();
        toggleContraer();
      }
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, []);

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
    }
    // Pedido explícito del usuario (2026-11-05): ya no es exclusivo de ADMIN_GENERAL -- cada
    // ABOGADO también tiene su propia bóveda personal, así que el contador corre para
    // cualquier rol (el backend ya devuelve SOLO la bóveda de quien pregunta).
    listarCasosPrivados()
      .then((rows) => setContadores((c) => ({ ...c, casosPrivados: rows.length })))
      .catch(() => {});
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
      items: grupo.items
        .filter((item) => (item.roles as readonly string[]).includes(sesion.rol))
        .map((item) =>
          item.children
            ? { ...item, children: item.children.filter((hijo) => (hijo.roles as readonly string[]).includes(sesion.rol)) }
            : item,
        ),
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
      <aside
        data-sidebar="true"
        className={`group/sidebar relative hidden min-h-0 shrink-0 flex-col bg-gradient-to-b from-[#1e1a12] via-[#15120d] to-[#0a0907] shadow-[inset_-1px_0_0_rgba(217,169,37,0.1)] lg:flex transition-[width] duration-300 ease-[cubic-bezier(0.23,1,0.32,1)] ${
          contraido ? "w-[76px]" : "w-72"
        }`}
      >
        {/* Botón principal de contraer/expandir en la mitad del borde lateral, más grande y visible (pedido explícito del usuario) */}
        <button
          type="button"
          onClick={toggleContraer}
          title={contraido ? "Expandir menú lateral (Ctrl+B)" : "Contraer menú lateral (Ctrl+B)"}
          aria-label={contraido ? "Expandir menú lateral" : "Contraer menú lateral"}
          className="absolute -right-5 top-1/2 -translate-y-1/2 z-40 flex h-10 w-10 items-center justify-center rounded-full bg-[#181510] border-2 border-gold/70 text-gold shadow-2xl shadow-black/80 hover:bg-gold hover:text-ink-fixed hover:border-gold hover:scale-110 active:scale-95 transition-all duration-200 cursor-pointer focus:outline-none focus:ring-2 focus:ring-gold/50"
        >
          {contraido ? (
            <CaretRight weight="bold" className="h-5 w-5" />
          ) : (
            <CaretLeft weight="bold" className="h-5 w-5" />
          )}
        </button>

        <SidebarContent
          grupos={grupos}
          pathname={pathname}
          sesion={sesion}
          onLogout={logout}
          contadores={contadores}
          contraido={contraido}
          onToggleContraer={toggleContraer}
        />
      </aside>

      {/* Sidebar móvil */}
      {menuAbierto && (
        <div className="fixed inset-0 z-50 flex lg:hidden">
          <div data-sidebar="true" className="flex w-80 min-h-0 flex-col bg-gradient-to-b from-[#1e1a12] via-[#15120d] to-[#0a0907] shadow-[inset_-1px_0_0_rgba(217,169,37,0.1)]">
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
            del sidebar, sin espacio de sobra) para no duplicar el input en dos lugares.
            Pedido explícito del usuario (2026-11-05): "el buscador general solo dejalo
            visible para cuando uno este en resumen" -- /admin es la página de Resumen. */}
        {pathname === "/admin" && (
          <div className="flex shrink-0 items-center border-b border-line bg-surface px-4 py-3 sm:px-8">
            <BuscadorGlobal />
          </div>
        )}

        <main className="min-h-0 min-w-0 flex-1 overflow-x-hidden overflow-y-auto px-4 py-8 sm:px-8 lg:py-10">
          {children}
        </main>
      </div>
    </div>
  );
}

// Pedido explícito del usuario (2026-11-06): "en el menu para todos, casos hazlo como un
// desplegable que saque los otros" -- fila de navegación que sabe renderizarse a sí misma
// anidada (para "Casos Privados y Cifrados" / "Casos Finalizados" dentro de "Casos"). El
// link principal sigue navegando normal al hacer clic en cualquier parte de la fila; el
// caret de la derecha solo existe cuando hay hijos y únicamente alterna mostrarlos/
// ocultarlos (stopPropagation para no disparar también la navegación del Link). Por
// defecto el grupo aparece desplegado si la ruta activa es uno de los hijos (para no
// esconder justo la página en la que ya está el usuario), y colapsado en cualquier otro caso.
function NavRow({
  item,
  pathname,
  contadores,
  anidado = false,
  contraido = false,
}: {
  item: NavItem;
  pathname: string | null;
  contadores: Record<string, number>;
  anidado?: boolean;
  contraido?: boolean;
}) {
  const activo = pathname === item.href || (item.href !== "/admin" && pathname?.startsWith(item.href));
  const hijoActivo = !!item.children?.some(
    (hijo) => pathname === hijo.href || pathname?.startsWith(hijo.href),
  );
  const [desplegadoManual, setDesplegadoManual] = useState<boolean | null>(null);
  const desplegado = desplegadoManual ?? hijoActivo;
  const Icon = item.icon;
  const contador = item.contadorId ? contadores[item.contadorId] : undefined;
  const tieneHijos = !!item.children?.length;

  // Modo contraído: solo iconos centrados con tooltips flotantes ultra nítidos
  if (contraido) {
    return (
      <div className="space-y-1.5 py-0.5">
        <Tooltip.Provider delay={100} closeDelay={100}>
          <Tooltip.Root>
            <Tooltip.Trigger
              render={
                <Link
                  href={item.href}
                  className={`group relative flex h-10 w-10 items-center justify-center rounded-xl mx-auto transition-all duration-200 ${
                    activo
                      ? "bg-gold text-ink-fixed shadow-lg shadow-gold/25 ring-1 ring-gold scale-100"
                      : "bg-white/5 text-night-ink/70 hover:bg-white/10 hover:text-gold hover:scale-105 active:scale-95"
                  }`}
                />
              }
            >
              <Icon weight={activo ? "fill" : "light"} className="h-5 w-5" />
              {!!contador && (
                <span className="absolute -top-1 -right-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-gold px-1 text-[9px] font-bold text-ink-fixed shadow-sm">
                  {contador > 99 ? "99+" : contador}
                </span>
              )}
            </Tooltip.Trigger>
            <Tooltip.Portal>
              <Tooltip.Positioner side="right" sideOffset={14}>
                <Tooltip.Popup className="z-50 rounded-xl bg-[#14120e] border border-gold/40 px-3.5 py-2 shadow-2xl shadow-black/80 text-xs backdrop-blur-md animate-in fade-in-0 zoom-in-95 pointer-events-none">
                  <div className="flex items-center gap-2">
                    <span className="font-semibold text-night-ink text-sm">{item.label}</span>
                    {!!contador && (
                      <span className="rounded-full bg-gold px-1.5 py-0.2 text-[10px] font-bold text-ink-fixed">
                        {contador > 99 ? "99+" : contador}
                      </span>
                    )}
                  </div>
                  <p className="mt-0.5 max-w-[210px] text-[11px] leading-tight text-night-ink/60">
                    {item.descripcion}
                  </p>
                </Tooltip.Popup>
              </Tooltip.Positioner>
            </Tooltip.Portal>
          </Tooltip.Root>
        </Tooltip.Provider>

        {/* Sub-items en modo contraído (ej: Casos Privados y Cifrados) */}
        {tieneHijos && (
          <div className="space-y-1.5 border-l border-gold/30 pl-1 mx-auto w-fit">
            {item.children!.map((hijo) => {
              const hijoActivo = pathname === hijo.href || (hijo.href !== "/admin" && pathname?.startsWith(hijo.href));
              const HijoIcon = hijo.icon;
              const hijoContador = hijo.contadorId ? contadores[hijo.contadorId] : undefined;
              return (
                <Tooltip.Provider key={hijo.href} delay={100} closeDelay={100}>
                  <Tooltip.Root>
                    <Tooltip.Trigger
                      render={
                        <Link
                          href={hijo.href}
                          className={`group relative flex h-8 w-8 items-center justify-center rounded-lg transition-all duration-200 ${
                            hijoActivo
                              ? "bg-gold text-ink-fixed shadow-md shadow-gold/25 ring-1 ring-gold"
                              : "bg-white/5 text-night-ink/60 hover:bg-white/10 hover:text-gold hover:scale-105 active:scale-95"
                          }`}
                        />
                      }
                    >
                      <HijoIcon weight={hijoActivo ? "fill" : "light"} className="h-4 w-4" />
                      {!!hijoContador && (
                        <span className="absolute -top-1 -right-1 flex h-3.5 min-w-3.5 items-center justify-center rounded-full bg-gold px-0.5 text-[8.5px] font-bold text-ink-fixed shadow-sm">
                          {hijoContador > 99 ? "99+" : hijoContador}
                        </span>
                      )}
                    </Tooltip.Trigger>
                    <Tooltip.Portal>
                      <Tooltip.Positioner side="right" sideOffset={14}>
                        <Tooltip.Popup className="z-50 rounded-xl bg-[#14120e] border border-gold/40 px-3.5 py-2 shadow-2xl shadow-black/80 text-xs backdrop-blur-md animate-in fade-in-0 zoom-in-95 pointer-events-none">
                          <div className="flex items-center gap-2">
                            <span className="font-semibold text-night-ink text-sm">{hijo.label}</span>
                            {!!hijoContador && (
                              <span className="rounded-full bg-gold px-1.5 py-0.2 text-[10px] font-bold text-ink-fixed">
                                {hijoContador > 99 ? "99+" : hijoContador}
                              </span>
                            )}
                          </div>
                          <p className="mt-0.5 max-w-[210px] text-[11px] leading-tight text-night-ink/60">
                            {hijo.descripcion}
                          </p>
                        </Tooltip.Popup>
                      </Tooltip.Positioner>
                    </Tooltip.Portal>
                  </Tooltip.Root>
                </Tooltip.Provider>
              );
            })}
          </div>
        )}
      </div>
    );
  }

  return (
    <div>
      <div
        className={`group relative flex items-stretch rounded-xl transition-colors ${
          activo && !anidado ? "bg-gold/12 text-night-ink" : "text-night-ink/65 hover:bg-white/5 hover:text-night-ink"
        } ${anidado ? "text-night-ink/55 hover:bg-white/5 hover:text-night-ink" : ""}`}
      >
        <Link
          href={item.href}
          className={`flex min-w-0 flex-1 items-start gap-3 py-2.5 pl-3 ${tieneHijos ? "pr-1" : "pr-3"}`}
        >
          {!anidado && (
            <span
              className={`absolute left-0 top-1/2 h-6 w-[3px] -translate-y-1/2 rounded-r-full bg-gold transition-transform duration-300 ${
                activo ? "scale-y-100" : "scale-y-0"
              }`}
            />
          )}
          <span
            className={`mt-0.5 flex shrink-0 items-center justify-center rounded-lg transition-colors ${
              anidado ? "h-6 w-6" : "h-8 w-8"
            } ${
              activo ? "bg-gold text-ink-fixed" : "bg-white/5 text-night-ink/70 group-hover:bg-white/10"
            }`}
          >
            <Icon weight={activo ? "fill" : "light"} className={anidado ? "h-3.5 w-3.5" : "h-4 w-4"} />
          </span>
          <span className="min-w-0 flex-1">
            <span className="flex items-center justify-between gap-2">
              <span className={`truncate text-sm ${activo ? "font-semibold" : "font-medium"}`}>{item.label}</span>
              {/* Contador real de "pendiente de revisar" (pedido explícito del usuario:
                  "mas funcional") -- solo aparece cuando hay algo que contar, nunca un 0
                  vacío ocupando espacio. */}
              {!!contador && (
                <span className="flex h-[18px] min-w-[18px] shrink-0 items-center justify-center rounded-full bg-gold px-1 text-[10px] font-bold text-ink-fixed">
                  {contador > 99 ? "99+" : contador}
                </span>
              )}
            </span>
            {/* Descripción de una línea -- pedido explícito del usuario: "descripciones y
                demas", para entender qué hay en cada pantalla sin tener que entrar primero. */}
            <span className="mt-0.5 block truncate text-[11.5px] leading-snug text-night-ink/45">
              {item.descripcion}
            </span>
          </span>
        </Link>

        {tieneHijos && (
          <button
            type="button"
            aria-label={desplegado ? `Ocultar ${item.label}` : `Mostrar ${item.label}`}
            onClick={(e) => {
              e.preventDefault();
              setDesplegadoManual(!desplegado);
            }}
            className="flex shrink-0 items-center px-2.5 text-night-ink/50 hover:text-night-ink"
          >
            <CaretDown className={`h-3.5 w-3.5 transition-transform duration-200 ${desplegado ? "rotate-180" : ""}`} weight="bold" />
          </button>
        )}
      </div>

      {tieneHijos && desplegado && (
        <div className="ml-4 mt-1 space-y-1 border-l border-white/10 pl-2">
          {item.children!.map((hijo) => (
            <NavRow key={hijo.href} item={hijo} pathname={pathname} contadores={contadores} anidado />
          ))}
        </div>
      )}
    </div>
  );
}

function SidebarContent({
  grupos,
  pathname,
  sesion,
  onLogout,
  contadores,
  contraido = false,
  onToggleContraer,
}: {
  grupos: NavGroup[];
  pathname: string | null;
  sesion: { nombre: string; rol: string };
  onLogout: () => void;
  contadores: Record<string, number>;
  contraido?: boolean;
  onToggleContraer?: () => void;
}) {
  return (
    <>
      {/* Cabecera del sidebar */}
      {!contraido ? (
        <div className="relative flex h-16 shrink-0 items-center gap-2.5 overflow-hidden border-b border-white/10 px-4">
          <div
            aria-hidden="true"
            className="pointer-events-none absolute inset-0"
            style={{ backgroundImage: "radial-gradient(220px circle at 0% 0%, rgba(217,169,37,0.22), transparent 65%)" }}
          />
          <span className="relative flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-gold/15 ring-1 ring-gold/40">
            <Image src="/marca/logo.png" alt="" width={22} height={22} className="h-[22px] w-[22px] object-contain" />
          </span>
          <span className="relative font-display text-base font-semibold text-night-ink tracking-tight">Panel</span>
          
          <div className="relative ml-auto flex items-center gap-1">
            {/* Campanita de notificaciones */}
            <NotificacionesPanel />
          </div>
        </div>
      ) : (
        <div className="relative flex h-16 shrink-0 flex-col items-center justify-center border-b border-white/10 px-2">
          {/* Logo clickeable para expandir */}
          <button
            type="button"
            onClick={onToggleContraer}
            title="Expandir menú lateral (Ctrl+B)"
            aria-label="Expandir menú lateral"
            className="group relative flex h-10 w-10 items-center justify-center rounded-xl bg-gold/10 hover:bg-gold/20 ring-1 ring-gold/30 transition-all duration-200 hover:scale-105 active:scale-95 cursor-pointer"
          >
            <Image src="/marca/logo.png" alt="" width={22} height={22} className="h-[22px] w-[22px] object-contain transition-transform group-hover:scale-95" />
            <span className="absolute -bottom-1 -right-1 flex h-4 w-4 items-center justify-center rounded-full bg-gold text-ink-fixed shadow-md">
              <CaretRight weight="bold" className="h-2.5 w-2.5" />
            </span>
          </button>
        </div>
      )}

      {/* Campanita en modo contraído centrada debajo de la cabecera */}
      {contraido && (
        <div className="flex justify-center border-b border-white/5 py-2 shrink-0">
          <NotificacionesPanel />
        </div>
      )}

      {/* Lista de navegación */}
      <nav className={`sidebar-scroll min-h-0 flex-1 overflow-y-auto ${contraido ? "px-2 py-3" : "px-3 py-5"}`}>
        {grupos.map((grupo, i) => (
          <div key={grupo.titulo} className={i > 0 ? (contraido ? "mt-3 border-t border-white/10 pt-2.5" : "mt-5 border-t border-white/10 pt-4") : ""}>
            {!contraido ? (
              <p className="px-3 pb-2 text-[11px] font-semibold uppercase tracking-[0.12em] text-night-ink/40">
                {grupo.titulo}
              </p>
            ) : null}
            <div className="space-y-1">
              {grupo.items.map((item) => (
                <NavRow key={item.href} item={item} pathname={pathname} contadores={contadores} contraido={contraido} />
              ))}
            </div>
          </div>
        ))}
      </nav>

      {/* Pie del menú: perfil y cerrar sesión */}
      {!contraido ? (
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
      ) : (
        <div className="border-t border-white/10 p-2 flex flex-col items-center gap-2">
          <Tooltip.Provider delay={100} closeDelay={100}>
            <Tooltip.Root>
              <Tooltip.Trigger
                render={
                  <Link
                    href="/admin/perfil"
                    className={`flex h-10 w-10 items-center justify-center rounded-xl transition-all duration-200 ${
                      pathname === "/admin/perfil" ? "bg-gold text-ink-fixed shadow-md shadow-gold/25 ring-1 ring-gold" : "bg-white/5 text-night-ink/70 hover:bg-white/10 hover:text-gold"
                    }`}
                  />
                }
              >
                <UserCircle weight="light" className="h-5 w-5" />
              </Tooltip.Trigger>
              <Tooltip.Portal>
                <Tooltip.Positioner side="right" sideOffset={14}>
                  <Tooltip.Popup className="z-50 rounded-xl bg-[#14120e] border border-gold/40 px-3.5 py-2 shadow-2xl shadow-black/80 text-xs backdrop-blur-md animate-in fade-in-0 zoom-in-95 pointer-events-none">
                    <span className="font-semibold text-night-ink text-sm block">{sesion.nombre}</span>
                    <span className="text-[11px] text-night-ink/60 block">
                      {sesion.rol === "ADMIN_GENERAL" ? "Administrador General" : "Abogado"}
                    </span>
                  </Tooltip.Popup>
                </Tooltip.Positioner>
              </Tooltip.Portal>
            </Tooltip.Root>
          </Tooltip.Provider>

          <Tooltip.Provider delay={100} closeDelay={100}>
            <Tooltip.Root>
              <Tooltip.Trigger
                render={
                  <button
                    type="button"
                    onClick={onLogout}
                    className="flex h-10 w-10 items-center justify-center rounded-xl bg-white/5 text-night-ink/70 hover:bg-rose-500/20 hover:text-rose-400 active:scale-95 transition-all duration-200"
                  />
                }
              >
                <SignOut weight="light" className="h-5 w-5" />
              </Tooltip.Trigger>
              <Tooltip.Portal>
                <Tooltip.Positioner side="right" sideOffset={14}>
                  <Tooltip.Popup className="z-50 rounded-xl bg-[#14120e] border border-gold/40 px-3 py-1.5 shadow-2xl shadow-black/80 text-xs backdrop-blur-md animate-in fade-in-0 zoom-in-95 pointer-events-none">
                    <span className="font-semibold text-rose-300">Cerrar sesión</span>
                  </Tooltip.Popup>
                </Tooltip.Positioner>
              </Tooltip.Portal>
            </Tooltip.Root>
          </Tooltip.Provider>
        </div>
      )}
    </>
  );
}
