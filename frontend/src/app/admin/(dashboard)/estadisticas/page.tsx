"use client";

import { useEffect, useState } from "react";
import {
  Envelope,
  Quotes,
  ChatCircleDots,
  Article,
  Megaphone,
  CalendarCheck,
  TrendUp,
  UsersThree,
  ChatText,
  Globe,
} from "@phosphor-icons/react";
import { obtenerEstadisticas, type Estadisticas } from "@/lib/admin-api";
import { AdminPageHeader, AdminCard, EmptyState, AdminLoader } from "@/components/admin/ui";
import { DonutChart, GaugeRadial } from "@/components/admin/charts";
import { useAuth } from "@/lib/auth-context";

// Misma asignación de color por categoría en toda la página (nunca cambia según el
// filtro activo): dorado para "lo nuevo/lo propio", ámbar para "en curso", esmeralda
// para "positivo", rojo para "negativo", gris cálido para "neutral/cerrado".
const COLOR = {
  gold: "#D9A925",
  warning: "#f59e0b",
  success: "#10b981",
  danger: "#ef4444",
  neutral: "#b9b3a2",
} as const;

function SectionTitle({
  icono: Icono,
  etiqueta,
  total,
}: {
  icono: typeof Envelope;
  etiqueta: string;
  total?: number;
}) {
  return (
    <div className="flex items-center justify-between">
      <div className="flex items-center gap-2.5">
        <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-gold-pale/50 text-gold-deep">
          <Icono weight="light" className="h-4.5 w-4.5" />
        </span>
        <h2 className="font-display text-base text-ink">{etiqueta}</h2>
      </div>
      {total !== undefined && <span className="text-sm text-ink-soft">{total} total</span>}
    </div>
  );
}

function CifraCard({
  icono: Icono,
  etiqueta,
  valor,
  descripcion,
}: {
  icono: typeof Envelope;
  etiqueta: string;
  valor: number;
  descripcion: string;
}) {
  return (
    <AdminCard>
      <SectionTitle icono={Icono} etiqueta={etiqueta} />
      <p className="mt-4 font-display text-4xl text-ink">{valor}</p>
      <p className="mt-1 text-sm text-ink-soft">{descripcion}</p>
    </AdminCard>
  );
}

export default function EstadisticasPage() {
  const { sesion } = useAuth();
  const [datos, setDatos] = useState<Estadisticas | null>(null);
  const [error, setError] = useState(false);
  const esAdminGeneral = sesion?.rol === "ADMIN_GENERAL";

  useEffect(() => {
    obtenerEstadisticas()
      .then(setDatos)
      .catch(() => setError(true));
  }, []);

  if (error) {
    return (
      <div>
        <AdminPageHeader title="Estadísticas" description="Un vistazo agregado a la actividad de la firma." />
        <EmptyState
          title="No se pudieron cargar las estadísticas"
          description="Verifica que el backend esté disponible e intenta de nuevo."
        />
      </div>
    );
  }

  if (!datos) {
    return (
      <div>
        <AdminPageHeader title="Estadísticas" description="Un vistazo agregado a la actividad de la firma." />
        <AdminLoader />
      </div>
    );
  }

  const totalSolicitudes = Object.values(datos.solicitudesPorEstado).reduce((a, b) => a + b, 0);
  const totalOrigen = Object.values(datos.solicitudesPorOrigen).reduce((a, b) => a + b, 0);
  const totalTestimonios = Object.values(datos.testimoniosPorEstado).reduce((a, b) => a + b, 0);
  const totalArticulos = datos.articulosPublicados + datos.articulosBorrador;

  // Multiplicador puramente visual para la tarjeta "Chatbot este mes" (pedido explícito).
  // El bloqueo real del chatbot sigue enforced en el backend contra el valor REAL de
  // datos.limiteMensualChatbot (app.chatbot.limite-mensual, 250) — esto no lo toca, solo
  // cambia lo que se muestra aquí. Topado en CHATBOT_MAX_VISUAL para que nunca se vea un
  // número mayor que el máximo mostrado (se veía roto sin el tope: a las 250
  // conversaciones reales, ×4 sin topar habría mostrado "1000 / 500").
  const CHATBOT_MULTIPLICADOR_VISUAL = 4;
  const CHATBOT_MAX_VISUAL = 500;
  const conversacionesChatbotVisual = Math.min(
    datos.conversacionesChatbotMesActual * CHATBOT_MULTIPLICADOR_VISUAL,
    CHATBOT_MAX_VISUAL
  );
  const pctChatbot = Math.min(100, Math.round((conversacionesChatbotVisual / CHATBOT_MAX_VISUAL) * 100));

  return (
    <div>
      <AdminPageHeader title="Estadísticas" description="Un vistazo agregado a la actividad de la firma." />

      {/* Contador grande y destacado, fuera de la grilla de tarjetas chicas: es el
          primer número que pidió ver el dueño de la firma. */}
      <AdminCard accent className="mb-6">
        <SectionTitle icono={Globe} etiqueta="Visitantes este mes" />
        <p className="mt-4 font-display text-6xl text-ink">{datos.visitantesMesActual}</p>
        <p className="mt-1 text-sm text-ink-soft">Visitantes únicos aproximados al sitio público este mes.</p>
      </AdminCard>

      {/* Casework: lo que le importa al día a día de cualquier abogado del equipo */}
      <div className="grid gap-6 lg:grid-cols-2">
        <AdminCard>
          <SectionTitle icono={Envelope} etiqueta="Solicitudes" total={totalSolicitudes} />
          <div className="mt-5">
            <DonutChart
              centroLabel="Solicitudes"
              segmentos={[
                { etiqueta: "Nuevas", valor: datos.solicitudesPorEstado.NUEVO, color: COLOR.gold },
                { etiqueta: "En contacto", valor: datos.solicitudesPorEstado.CONTACTADO, color: COLOR.warning },
                { etiqueta: "Cerradas", valor: datos.solicitudesPorEstado.CERRADO, color: COLOR.neutral },
              ]}
            />
          </div>
        </AdminCard>

        <AdminCard>
          <SectionTitle icono={ChatText} etiqueta="De dónde llegan" total={totalOrigen} />
          <div className="mt-5">
            <DonutChart
              centroLabel="Solicitudes"
              segmentos={[
                { etiqueta: "Formulario del sitio", valor: datos.solicitudesPorOrigen.FORMULARIO, color: COLOR.gold },
                { etiqueta: "Chatbot (Siebot)", valor: datos.solicitudesPorOrigen.CHATBOT, color: COLOR.success },
                { etiqueta: "WhatsApp", valor: datos.solicitudesPorOrigen.WHATSAPP, color: COLOR.neutral },
              ]}
            />
          </div>
        </AdminCard>

        <CifraCard
          icono={TrendUp}
          etiqueta="Actividad reciente"
          valor={datos.solicitudesUltimos7Dias}
          descripcion="Solicitudes nuevas en los últimos 7 días."
        />

        <AdminCard>
          <SectionTitle icono={CalendarCheck} etiqueta="Citas" />
          <div className="mt-4 grid grid-cols-2 gap-4">
            <div>
              <p className="font-display text-3xl text-ink">{datos.citasAgendadas}</p>
              <p className="mt-1 text-xs text-ink-soft">Agendadas en total</p>
            </div>
            <div>
              <p className="font-display text-3xl text-gold-deep">{datos.citasProximas}</p>
              <p className="mt-1 text-xs text-ink-soft">Aún por venir</p>
            </div>
          </div>
        </AdminCard>

        <AdminCard className="flex flex-col items-center text-center">
          <div className="w-full">
            <SectionTitle icono={ChatCircleDots} etiqueta="Chatbot este mes" />
          </div>
          <div className="mt-4">
            <GaugeRadial
              porcentaje={pctChatbot}
              valorLabel={`${conversacionesChatbotVisual}`}
              subLabel={`de ${CHATBOT_MAX_VISUAL} conversaciones (${pctChatbot}%)`}
              tono={pctChatbot >= 90 ? "danger" : pctChatbot >= 70 ? "warning" : "gold"}
            />
          </div>
        </AdminCard>

        <AdminCard>
          <SectionTitle icono={Article} etiqueta="Artículos" total={totalArticulos} />
          <div className="mt-5">
            <DonutChart
              centroLabel="Artículos"
              segmentos={[
                { etiqueta: "Publicados", valor: datos.articulosPublicados, color: COLOR.success },
                { etiqueta: "Borradores", valor: datos.articulosBorrador, color: COLOR.neutral },
              ]}
            />
          </div>
        </AdminCard>
      </div>

      {/* Lado administrativo: testimonios, marketing y equipo, solo para ADMIN_GENERAL
          (mismo alcance que ya no ve el rol ABOGADO en el menú) */}
      {esAdminGeneral && (
        <>
          <h2 className="mt-10 mb-4 font-display text-lg text-ink">Administración</h2>
          <div className="grid gap-6 lg:grid-cols-2">
            <AdminCard>
              <SectionTitle icono={Quotes} etiqueta="Testimonios" total={totalTestimonios} />
              <div className="mt-5">
                <DonutChart
                  centroLabel="Testimonios"
                  segmentos={[
                    { etiqueta: "Pendientes", valor: datos.testimoniosPorEstado.PENDIENTE, color: COLOR.warning },
                    { etiqueta: "Aprobados", valor: datos.testimoniosPorEstado.APROBADO, color: COLOR.success },
                    { etiqueta: "Rechazados", valor: datos.testimoniosPorEstado.RECHAZADO, color: COLOR.danger },
                  ]}
                />
              </div>
            </AdminCard>

            <CifraCard
              icono={Megaphone}
              etiqueta="Suscriptores de marketing"
              valor={datos.suscriptoresMarketingActivos}
              descripcion="Personas que aceptaron recibir novedades por correo."
            />

            <AdminCard className="lg:col-span-2">
              <SectionTitle icono={UsersThree} etiqueta="Equipo interno" total={datos.usuariosInternosActivos} />
              <div className="mt-4 grid grid-cols-2 gap-6">
                <div>
                  <p className="font-display text-3xl text-ink">{datos.usuariosPorRol.ADMIN_GENERAL}</p>
                  <p className="mt-1 text-xs text-ink-soft">Administradores generales</p>
                </div>
                <div>
                  <p className="font-display text-3xl text-ink">{datos.usuariosPorRol.ABOGADO}</p>
                  <p className="mt-1 text-xs text-ink-soft">Abogados</p>
                </div>
              </div>
            </AdminCard>
          </div>
        </>
      )}
    </div>
  );
}
