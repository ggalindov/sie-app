"use client";

import { useState, useMemo, useId } from "react";
import Link from "next/link";
import { motion, AnimatePresence } from "motion/react";
import {
  MagnifyingGlass,
  X,
  CaretDown,
  WhatsappLogo,
  Robot,
  FileText,
  Scales,
  CheckCircle,
  ArrowRight,
} from "@phosphor-icons/react";
import { siteConfig } from "@/lib/site-config";
import type { PreguntaFrecuente as PreguntaFrecuenteApi } from "@/lib/api";

interface ItemFaq {
  id: string;
  pregunta: string;
  respuesta: string[];
  puntosClave?: string[];
  consejo?: string;
  tags: string[];
  esDelBackend?: boolean;
}

const FAQS_DEFAULT: ItemFaq[] = [
  {
    id: "tarifas-honorarios",
    pregunta: "¿Cómo se calculan los honorarios y qué formas de pago manejan?",
    respuesta: [
      "En SIE Jurídicos creemos en la total transparencia desde el primer contacto. No manejamos costos ocultos ni cobros imprevistos.",
      "Dependiendo de la naturaleza del asunto, estructuramos los honorarios bajo tres modalidades claras:",
    ],
    puntosClave: [
      "Honorarios fijos por etapas: Se pacta un valor global dividido por hitos procesales (ej. presentación de demanda, audiencia inicial, fallo).",
      "Cuota litis (porcentaje sobre resultado): Aplicable a ciertas reclamaciones laborales e indemnizaciones civiles, donde nuestros honorarios están atados al éxito de tu caso.",
      "Asesoría y conceptos por tarifa horaria o mensual: Diseñado para empresas y clientes recurrentes.",
    ],
    consejo: "En la primera sesión diagnóstica te entregamos una propuesta de servicios detallada y por escrito para tu completa tranquilidad.",
    tags: ["honorarios", "costo", "precio", "cuanto vale", "pagos", "formas de pago", "cuota litis", "tarifas", "cotizacion"],
  },
  {
    id: "tarifas-primera-cita",
    pregunta: "¿La primera consulta o evaluación de mi caso tiene algún costo?",
    respuesta: [
      "Realizamos una valoración diagnóstica preliminar sin costo a través de nuestros canales digitales (chat asistido o WhatsApp) para determinar si tu asunto es viable jurídicamente y qué área de práctica le corresponde.",
      "Si el caso requiere el estudio exhaustivo de expedientes voluminosos, análisis jurisprudencial especializado o la emisión de un concepto legal formal por escrito, acordamos previamente el valor de la consulta antes de que asumas cualquier compromiso económico.",
    ],
    tags: ["primera consulta", "gratis", "costo", "asesoria", "evaluacion", "diagnostico"],
  },
  {
    id: "laboral-despido",
    pregunta: "¿Qué debo hacer si fui despedido sin justa causa de mi trabajo?",
    respuesta: [
      "El despido sin justa causa en Colombia da lugar al pago obligatorio de una indemnización legal calculada según el tipo de contrato (término fijo o indefinido) y el tiempo laborado, conforme al artículo 64 del Código Sustantivo del Trabajo.",
      "Te sugerimos seguir estos pasos de inmediato:",
    ],
    puntosClave: [
      "No firmes cartas de renuncia voluntaria ni acuerdos transaccionales de mutuo acuerdo si no estás completamente de acuerdo con los montos.",
      "Exige la carta de despido oficial con la firma del empleador y la fecha exacta de terminación.",
      "Verifica que tus cesantías, intereses, primas y vacaciones pendientes queden liquidadas al 100%.",
      "Consulta con un abogado antes de 3 años: ese es el plazo máximo de prescripción para reclamar tus derechos ante la jurisdicción laboral.",
    ],
    consejo: "Si gozas de estabilidad laboral reforzada (salud, maternidad o pre-pensión), el despido puede ser ineficaz y dar lugar a reintegro judicial.",
    tags: ["despido", "sin justa causa", "indemnizacion", "liquidacion", "laboral", "trabajo", "renuncia", "empleador"],
  },
  {
    id: "laboral-fueros",
    pregunta: "¿Qué es la estabilidad laboral reforzada y cuándo me protege de un despido?",
    respuesta: [
      "La estabilidad laboral reforzada es una garantía constitucional que impide que un empleador despida a un trabajador cuando se encuentra en condiciones de vulnerabilidad, salvo que cuente con permiso previo del Ministerio del Trabajo.",
      "Aplica principalmente en trabajadores con incapacidades continuas o tratamientos activos (fuero de salud), mujeres en estado de embarazo o lactancia (fuero de maternidad), y personas a las que les falten 3 años o menos para pensionarse (fuero de pre-pensionado).",
      "Si fuiste despedido existiendo fuero sin aval del Ministerio, podemos radicar una Acción de Tutela para ordenar tu reintegro inmediato y el pago de todos los salarios dejados de percibir.",
    ],
    tags: ["fuero", "salud", "maternidad", "pre pensionado", "estabilidad reforzada", "tutela", "reintegro", "despido"],
  },
  {
    id: "laboral-liquidacion",
    pregunta: "¿Cuánto tiempo tiene la empresa para pagarme la liquidación de prestaciones?",
    respuesta: [
      "En Colombia, la ley no establece un periodo de gracia de 15 o 30 días como comúnmente se cree. La liquidación debe pagarse al momento exacto de la terminación del contrato.",
      "Si el empleador no cancela los salarios y prestaciones debidas sin una razón justificada, incurre en la sanción moratoria del artículo 65 del CST (un día de salario por cada día de retraso).",
    ],
    tags: ["liquidacion", "prestaciones", "sancion moratoria", "retraso", "salarios caidos", "pago"],
  },
  {
    id: "familia-cuota",
    pregunta: "¿Cómo se calcula y se fija legalmente la cuota de alimentos para los hijos?",
    respuesta: [
      "En Colombia, la cuota alimentaria no cubre únicamente la comida: incluye educación, salud, vestuario, vivienda, recreación y transporte del menor.",
      "Para determinar su valor se evalúan la capacidad económica del progenitor (la ley autoriza embargar hasta el 50% de ingresos y prestaciones) y las necesidades reales demostrables del menor.",
      "Se puede fijar amistosamente mediante Acta de Conciliación en Centro de Conciliación o Defensoría de Familia (con mérito ejecutivo) o, a falta de acuerdo, ante un Juez de Familia mediante proceso judicial.",
    ],
    tags: ["cuota alimentaria", "alimentos", "hijos", "pension alimenticia", "icbf", "embargo", "familia"],
  },
  {
    id: "familia-divorcio",
    pregunta: "¿Cuánto tiempo tarda un divorcio y qué diferencia hay entre notarial y judicial?",
    respuesta: [
      "El divorcio de común acuerdo (vía Notaría) es ágil y pacífico: con apoderado judicial se legaliza en un plazo promedio de 8 a 15 días hábiles (o 20 a 30 días si hay hijos menores e interviene el Defensor de Familia).",
      "El divorcio contencioso (vía Judicial) procede cuando una de las partes se niega a divorciarse o no hay acuerdo en la partición de bienes o custodia. Debe invocarse alguna de las causales de ley y suele tardar entre 6 meses y 1 año.",
    ],
    tags: ["divorcio", "separacion", "notaria", "juzgado", "bienes", "sociedad conyugal", "mutuo acuerdo"],
  },
  {
    id: "familia-sucesion",
    pregunta: "¿Cómo se reparte una herencia o sucesión de bienes?",
    respuesta: [
      "La sucesión por causa de muerte permite adjudicar legalmente los bienes, cuentas y derechos de una persona fallecida a sus herederos legítimos.",
      "Si todos los herederos están de acuerdo con la partición y avalúo, el proceso se eleva a escritura pública ante Notaría en un término de 1 a 3 meses.",
      "Si existe controversia sobre los bienes, ocultamiento o desacuerdo entre herederos, debe adelantarse un proceso de sucesión contencioso ante un Juez de Familia.",
    ],
    tags: ["sucesion", "herencia", "herederos", "testamento", "notaria", "bienes", "particion"],
  },
  {
    id: "civil-arriendos",
    pregunta: "¿Qué hacer si un arrendatario no paga el canon de arrendamiento o no desocupa?",
    respuesta: [
      "Bajo la Ley 820 de 2003, el no pago de un solo periodo de renta o servicios públicos es causal suficiente para terminar unilateralmente el contrato de arrendamiento y solicitar la restitución judicial.",
      "Nuestro equipo adelanta el requerimiento formal, la demanda de restitución de inmueble arrendado y solicita la medida cautelar de entrega provisional para recuperar la tenencia del predio sin tener que esperar hasta la sentencia final.",
    ],
    tags: ["arriendo", "arrendatario", "restitucion de inmueble", "desalojo", "no paga", "contrato de arrendamiento", "fiador"],
  },
  {
    id: "civil-tutela",
    pregunta: "¿Cuándo procede una Acción de Tutela y cuánto tiempo tarda en fallarse?",
    respuesta: [
      "La Acción de Tutela (Art. 86 de la Constitución) procede de manera preferente y sumaria cuando se vulneran o amenazan derechos fundamentales (salud, vida, debido proceso, petición, trabajo, mínimo vital) y no existe otro mecanismo judicial ordinario idóneo.",
      "El juez de tutela tiene un plazo estricto e improrrogable de 10 días hábiles para emitir el fallo de primera instancia.",
    ],
    tags: ["tutela", "accion de tutela", "derecho de peticion", "salud", "eps", "desacato", "juez constitucional"],
  },
  {
    id: "civil-incumplimiento-contratos",
    pregunta: "¿Cómo actuar ante el incumplimiento de una promesa de compraventa?",
    respuesta: [
      "Al celebrarse una promesa de compraventa de inmuebles o vehículos, ambas partes quedan legalmente obligadas a comparecer en la notaría, fecha y hora acordadas.",
      "Si la otra parte no asiste o no cancela el precio pactado, es vital levantar el Acta de No Comparecencia en la notaría. Con esa prueba, puedes demandar judicialmente la resolución del contrato con cobro de cláusula penal o exigir el otorgamiento forzado de la escritura pública más perjuicios.",
    ],
    tags: ["compraventa", "promesa", "incumplimiento", "clausula penal", "arras", "notaria", "inmueble"],
  },
  {
    id: "comercial-registro-marca",
    pregunta: "¿Por qué tener matrícula en Cámara de Comercio no protege el nombre de mi marca?",
    respuesta: [
      "La matrícula mercantil ante la Cámara de Comercio registra la razón social de tu empresa, pero NO te otorga ningún derecho de propiedad intelectual sobre tu marca ni sobre tus productos o servicios.",
      "La única entidad facultada para otorgarte el uso exclusivo de un nombre comercial o logotipo en Colombia es la Superintendencia de Industria y Comercio (SIC), protegiendo tu marca por 10 años renovables en todo el país.",
    ],
    tags: ["marca", "sic", "registro de marca", "camara de comercio", "propiedad intelectual", "nombre comercial"],
  },
  {
    id: "procesos-consulta-radicado",
    pregunta: "¿Cómo puedo hacer seguimiento en tiempo real al avance de mi proceso judicial?",
    respuesta: [
      "Para garantizar transparencia total, cada cliente de SIE Jurídicos recibe un código de radicado confidencial una vez formalizado su expediente.",
      "En nuestra plataforma web, en el apartado 'Consulta el estado de tu caso', puedes ingresar tu código en cualquier momento sin contraseñas para visualizar el juzgado asignado, la etapa procesal actual, el resumen de la última decisión y la fecha de última actualización.",
    ],
    tags: ["radicado", "seguimiento", "estado de caso", "juzgado", "consulta caso", "avance"],
  },
  {
    id: "procesos-cobertura-nacional",
    pregunta: "¿Atienden casos únicamente en Bogotá o en otras ciudades de Colombia?",
    respuesta: [
      "Nuestra sede principal se encuentra en Bogotá D.C., pero gestionamos y litigamos procesos en todo el territorio colombiano.",
      "Con la vigencia de la Ley 2213 de 2022 (justicia digital), las audiencias judiciales, radicación de memoriales y notificaciones se tramitan electrónicamente ante despachos de Medellín, Cali, Barranquilla, Bucaramanga, Pereira, Ibagué y demás ciudades.",
      "Para asesorías y consultas atendemos de forma presencial en Bogotá o virtualmente por videollamada para clientes en toda Colombia y en el exterior.",
    ],
    tags: ["bogota", "cobertura", "ciudades", "virtual", "presencial", "colombia", "videollamada"],
  },
  {
    id: "procesos-confidencialidad",
    pregunta: "¿Cómo se protege la confidencialidad de la información y documentos compartidos?",
    respuesta: [
      "Toda comunicación, archivo, testimonio o documento que compartas con los abogados de SIE Jurídicos está amparado por el secreto profesional constitucional (Artículo 74 de la Constitución Política y Ley 1123 de 2007).",
      "Esto significa que la información suministrada jamás será divulgada a terceros ni a contrapartes, garantizando absoluta reserva y cumplimiento de la Ley 1581 de Protección de Datos.",
    ],
    tags: ["confidencialidad", "secreto profesional", "datos", "privacidad", "seguridad"],
  },
];

interface Props {
  preguntasBackend?: PreguntaFrecuenteApi[];
}

export function PreguntasFrecuentesCliente({ preguntasBackend = [] }: Props) {
  const [busqueda, setBusqueda] = useState("");
  const [itemsAbiertos, setItemsAbiertos] = useState<Set<string>>(new Set(["tarifas-honorarios"]));
  const searchInputId = useId();

  // Fusionamos las preguntas del backend con el banco curado de preguntas
  const catalogoCompleto = useMemo<ItemFaq[]>(() => {
    if (!preguntasBackend || preguntasBackend.length === 0) {
      return FAQS_DEFAULT;
    }

    const desdeBackend: ItemFaq[] = preguntasBackend.map((b) => {
      const parrafos = b.respuesta.split("\n\n").map((s) => s.trim()).filter(Boolean);
      return {
        id: `backend-${b.id}`,
        pregunta: b.pregunta,
        respuesta: parrafos.length > 0 ? parrafos : [b.respuesta],
        tags: [b.pregunta.toLowerCase(), ...b.respuesta.toLowerCase().split(/\s+/).slice(0, 15)],
        esDelBackend: true,
      };
    });

    const preguntasBackendTitulos = new Set(desdeBackend.map((b) => b.pregunta.trim().toLowerCase()));
    const defaultFiltradas = FAQS_DEFAULT.filter(
      (d) => !preguntasBackendTitulos.has(d.pregunta.trim().toLowerCase()),
    );

    return [...desdeBackend, ...defaultFiltradas];
  }, [preguntasBackend]);

  // Filtrado reactivo en tiempo real con el buscador
  const preguntasFiltradas = useMemo(() => {
    const q = busqueda.trim().toLowerCase();
    if (!q) return catalogoCompleto;

    return catalogoCompleto.filter((item) => {
      const coincidePregunta = item.pregunta.toLowerCase().includes(q);
      const coincideRespuesta = item.respuesta.some((p) => p.toLowerCase().includes(q));
      const coincideTags = item.tags.some((t) => t.toLowerCase().includes(q));
      const coincidePuntos = item.puntosClave?.some((p) => p.toLowerCase().includes(q)) ?? false;

      return coincidePregunta || coincideRespuesta || coincideTags || coincidePuntos;
    });
  }, [catalogoCompleto, busqueda]);

  function alternarItem(id: string) {
    setItemsAbiertos((prev) => {
      const next = new Set(prev);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }

  function abrirChatbot() {
    if (typeof window !== "undefined") {
      window.dispatchEvent(new CustomEvent("abrir-chatbot"));
    }
  }

  return (
    <main className="flex-1 pt-32 pb-24 md:pt-36">
      <div className="mx-auto max-w-3xl px-6">
        {/* Cabecera Clásica y Limpia como antes */}
        <h1 className="font-display text-4xl leading-tight tracking-tight text-ink md:text-5xl">
          Preguntas frecuentes
        </h1>
        <p className="mt-4 max-w-xl text-base leading-relaxed text-ink-soft">
          Estas son las preguntas que con más frecuencia nos hacen a través de nuestro asistente
          virtual y en consulta directa. Si no encuentras lo que buscas, escríbenos directamente.
        </p>

        {/* Buscador Implementado */}
        <div className="mt-8">
          <div className="relative rounded-2xl border border-line bg-surface p-1.5 shadow-xs transition-all focus-within:border-gold-deep focus-within:ring-2 focus-within:ring-gold/20">
            <label htmlFor={searchInputId} className="sr-only">
              Buscar preguntas frecuentes
            </label>
            <div className="relative flex items-center">
              <MagnifyingGlass
                weight="bold"
                className="pointer-events-none absolute left-4 h-4 w-4 text-gold-deep"
              />
              <input
                id={searchInputId}
                type="text"
                value={busqueda}
                onChange={(e) => setBusqueda(e.target.value)}
                placeholder="Buscar por palabra clave (ej. despido, arriendo, divorcio, honorarios, radicado)..."
                className="w-full bg-transparent py-2.5 pl-11 pr-9 text-sm text-ink placeholder:text-ink-soft/60 focus:outline-none sm:text-base"
              />
              {busqueda && (
                <button
                  type="button"
                  onClick={() => setBusqueda("")}
                  aria-label="Limpiar búsqueda"
                  className="absolute right-3 flex h-6 w-6 items-center justify-center rounded-full bg-ink/5 text-ink-soft transition-colors hover:bg-ink/10 hover:text-ink"
                >
                  <X weight="bold" className="h-3.5 w-3.5" />
                </button>
              )}
            </div>
          </div>

          {busqueda && (
            <p className="mt-2 text-xs text-ink-soft">
              Mostrando {preguntasFiltradas.length} resultado{preguntasFiltradas.length === 1 ? "" : "s"} para &ldquo;{busqueda}&rdquo;
            </p>
          )}
        </div>

        {/* Contenedor Unificado de Acordeones como teníamos antes, mejorado visualmente */}
        {preguntasFiltradas.length === 0 ? (
          <div className="mt-10 rounded-3xl border border-line bg-surface p-8 text-center ring-1 ring-line">
            <p className="text-base font-medium text-ink">
              No encontramos preguntas para &ldquo;{busqueda}&rdquo;
            </p>
            <p className="mt-2 text-sm text-ink-soft">
              Puedes consultarle a nuestro asistente inteligente o escribirnos directamente a WhatsApp.
            </p>
            <div className="mt-5 flex justify-center gap-3">
              <button
                type="button"
                onClick={() => setBusqueda("")}
                className="rounded-full border border-line bg-paper px-4 py-2 text-xs font-semibold text-ink hover:border-ink"
              >
                Ver todas las preguntas
              </button>
              <button
                type="button"
                onClick={abrirChatbot}
                className="rounded-full bg-gold px-4 py-2 text-xs font-semibold text-ink-fixed hover:bg-gold-deep hover:text-white"
              >
                Preguntar a SIEBOT
              </button>
            </div>
          </div>
        ) : (
          <div className="mt-10 divide-y divide-line rounded-3xl bg-surface px-6 sm:px-8 ring-1 ring-line shadow-xs">
            {preguntasFiltradas.map((p) => {
              const estaAbierto = itemsAbiertos.has(p.id);

              return (
                <div key={p.id} className="py-2">
                  <button
                    type="button"
                    onClick={() => alternarItem(p.id)}
                    aria-expanded={estaAbierto}
                    className="group flex w-full items-center justify-between gap-4 py-4 text-left transition-colors"
                  >
                    <span className="font-display text-lg sm:text-[1.18rem] leading-snug text-ink group-hover:text-gold-deep transition-colors">
                      {p.pregunta}
                    </span>
                    <CaretDown
                      weight="bold"
                      className={`h-4 w-4 shrink-0 text-ink-soft transition-transform duration-300 ${
                        estaAbierto ? "rotate-180 text-gold-deep" : "group-hover:text-gold-deep"
                      }`}
                    />
                  </button>

                  <AnimatePresence initial={false}>
                    {estaAbierto && (
                      <motion.div
                        key="panel"
                        initial={{ height: 0, opacity: 0 }}
                        animate={{ height: "auto", opacity: 1 }}
                        exit={{ height: 0, opacity: 0 }}
                        transition={{ duration: 0.25, ease: [0.16, 1, 0.3, 1] }}
                        className="overflow-hidden text-sm sm:text-base leading-relaxed text-ink-soft"
                      >
                        <div className="space-y-3 pb-5 pt-1 text-ink/90">
                          {p.respuesta.map((parrafo, idx) => (
                            <p key={idx}>{parrafo}</p>
                          ))}

                          {p.puntosClave && p.puntosClave.length > 0 && (
                            <ul className="mt-3 space-y-2 rounded-2xl bg-paper/60 p-4 ring-1 ring-line/70">
                              {p.puntosClave.map((punto, pIdx) => (
                                <li key={pIdx} className="flex items-start gap-2.5 text-xs sm:text-sm text-ink-soft">
                                  <CheckCircle
                                    weight="fill"
                                    className="mt-0.5 h-4 w-4 shrink-0 text-gold-deep"
                                  />
                                  <span>{punto}</span>
                                </li>
                              ))}
                            </ul>
                          )}

                          <div className="pt-2 flex justify-end">
                            <a
                              href={`https://wa.me/573243668845?text=${encodeURIComponent(
                                `Hola equipo SIE Jurídicos, leí sobre "${p.pregunta}" y quisiera consultar mi caso.`
                              )}`}
                              target="_blank"
                              rel="noreferrer"
                              className="inline-flex items-center gap-1.5 text-xs font-medium text-gold-deep hover:underline"
                            >
                              <WhatsappLogo weight="fill" className="h-4 w-4 text-emerald-600 dark:text-emerald-400" />
                              <span>Consultar caso particular por WhatsApp</span>
                              <ArrowRight weight="bold" className="h-3 w-3" />
                            </a>
                          </div>
                        </div>
                      </motion.div>
                    )}
                  </AnimatePresence>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Sección Inferior de Asistencia (Orientación Personalizada, como solicitada en imagen) */}
      <div className="mx-auto max-w-5xl px-6 mt-20">
        <section className="rounded-3xl border border-line bg-surface p-6 sm:p-10 shadow-sm">
          <div className="max-w-2xl text-left">
            <div className="inline-flex items-center gap-2 rounded-full bg-gold/10 px-3 py-1 text-xs font-semibold text-gold-deep uppercase tracking-wider">
              <Scales weight="fill" className="h-3.5 w-3.5" />
              <span>Orientación personalizada</span>
            </div>
            <h2 className="mt-3 font-display text-2xl sm:text-3xl font-medium text-ink">
              ¿No encontraste la respuesta a tu caso específico?
            </h2>
            <p className="mt-2 text-sm sm:text-base leading-relaxed text-ink-soft">
              El derecho colombiano evalúa cada situación de manera particular. Dispones de tres
              canales inmediatos para resolver tu duda con respaldo profesional:
            </p>
          </div>

          <div className="mt-8 grid grid-cols-1 gap-4 md:grid-cols-3">
            {/* Tarjeta 1: Siebot */}
            <div className="flex flex-col justify-between rounded-2xl border border-line bg-paper/50 p-5 transition-all hover:border-gold/40 hover:shadow-xs">
              <div>
                <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-gold/15 text-gold-deep">
                  <Robot weight="duotone" className="h-6 w-6" />
                </div>
                <h3 className="mt-4 font-display text-lg font-medium text-ink">
                  Pregúntale a SIEBOT
                </h3>
                <p className="mt-1.5 text-xs sm:text-sm leading-relaxed text-ink-soft">
                  Asistente virtual con inteligencia jurídica disponible las 24 horas para resolver inquietudes preliminares y tomar tus datos.
                </p>
              </div>
              <button
                type="button"
                onClick={abrirChatbot}
                className="mt-5 inline-flex w-full items-center justify-center gap-2 rounded-xl bg-gold px-4 py-2.5 text-xs sm:text-sm font-semibold text-ink-fixed transition-colors hover:bg-gold-deep hover:text-white"
              >
                <Robot weight="fill" className="h-4 w-4" />
                <span>Hablar con SIEBOT ahora</span>
              </button>
            </div>

            {/* Tarjeta 2: WhatsApp */}
            <div className="flex flex-col justify-between rounded-2xl border border-line bg-paper/50 p-5 transition-all hover:border-gold/40 hover:shadow-xs">
              <div>
                <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-emerald-500/15 text-emerald-600 dark:text-emerald-400">
                  <WhatsappLogo weight="duotone" className="h-6 w-6" />
                </div>
                <h3 className="mt-4 font-display text-lg font-medium text-ink">
                  Escríbenos por WhatsApp
                </h3>
                <p className="mt-1.5 text-xs sm:text-sm leading-relaxed text-ink-soft">
                  Chatea directamente con nuestro equipo en Bogotá para agendar una sesión diagnóstica virtual o presencial.
                </p>
              </div>
              <a
                href={siteConfig.whatsapp}
                target="_blank"
                rel="noreferrer"
                className="mt-5 inline-flex w-full items-center justify-center gap-2 rounded-xl border border-emerald-500/30 bg-emerald-500/10 px-4 py-2.5 text-xs sm:text-sm font-semibold text-emerald-700 dark:text-emerald-400 transition-colors hover:bg-emerald-500/20"
              >
                <WhatsappLogo weight="fill" className="h-4 w-4" />
                <span>Chatear por WhatsApp</span>
              </a>
            </div>

            {/* Tarjeta 3: Consulta tu Radicado */}
            <div className="flex flex-col justify-between rounded-2xl border border-line bg-paper/50 p-5 transition-all hover:border-gold/40 hover:shadow-xs">
              <div>
                <div className="flex h-11 w-11 items-center justify-center rounded-xl bg-gold/15 text-gold-deep">
                  <FileText weight="duotone" className="h-6 w-6" />
                </div>
                <h3 className="mt-4 font-display text-lg font-medium text-ink">
                  Consulta tu Radicado
                </h3>
                <p className="mt-1.5 text-xs sm:text-sm leading-relaxed text-ink-soft">
                  ¿Ya tienes un proceso con nosotros? Ingresa tu código de radicado para ver actuaciones judiciales y avances actualizados.
                </p>
              </div>
              <Link
                href="/consulta-caso"
                className="mt-5 inline-flex w-full items-center justify-center gap-2 rounded-xl border border-line bg-surface px-4 py-2.5 text-xs sm:text-sm font-semibold text-ink transition-colors hover:border-gold-deep hover:text-gold-deep"
              >
                <FileText weight="bold" className="h-4 w-4" />
                <span>Rastrear mi expediente</span>
              </Link>
            </div>
          </div>
        </section>
      </div>
    </main>
  );
}
