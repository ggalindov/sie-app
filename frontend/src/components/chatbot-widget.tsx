"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import Image from "next/image";
import { AnimatePresence, motion } from "motion/react";
import { X, PaperPlaneTilt, ArrowRight } from "@phosphor-icons/react";
import { enviarMensajeChatbot, type TurnoChat } from "@/lib/api";

const EASE = [0.16, 1, 0.3, 1] as const;

const MENSAJE_BIENVENIDA: TurnoChat = {
  rol: "ASISTENTE",
  contenido:
    "Hola, soy Siebot, el asistente virtual de SIE Jurídicos. Puedo contarte sobre nuestros horarios, ubicación y áreas de práctica, o tomar tus datos para que un abogado te contacte. ¿En qué puedo ayudarte?",
};

// Mensajes institucionales y de captación que Siebot muestra de forma pausada y
// respetuosa mientras el chat está cerrado.
// Diseñados con tono cercano, profesional y ético (Ley 1123 de 2007),
// basados en datos reales de la firma (20+ años, 800+ casos, 6 áreas).
const MENSAJES_PROMOCIONALES: { tema: string; texto: string }[] = [
  {
    tema: "Orientación Inmediata",
    texto: "¿Tienes dudas sobre un contrato, despido o liquidación? Puedo orientarte en segundos.",
  },
  {
    tema: "Experiencia Comprobada",
    texto: "Más de 20 años de trayectoria y 800 casos ganados respaldan cada consejo de nuestra firma.",
  },
  {
    tema: "Especialidades Jurídicas",
    texto: "Laboral, comercial, civil, familia o administrativo: te conecto con el especialista indicado.",
  },
  {
    tema: "Atención Confidencial",
    texto: "Cada consulta es confidencial. Cuéntame tu caso y revisamos el camino legal sin compromiso.",
  },
  {
    tema: "Canal Directo",
    texto: "¿Prefieres comunicarte por WhatsApp o agendar una llamada con un abogado? Te guío ahora.",
  },
  {
    tema: "Respaldo Real",
    texto: "Un abogado especialista revisa personalmente cada solicitud que recibimos en el despacho.",
  },
];

export function ChatbotWidget() {
  const [open, setOpen] = useState(false);
  const [mensajes, setMensajes] = useState<TurnoChat[]>([MENSAJE_BIENVENIDA]);
  const [conversacionId, setConversacionId] = useState<number | null>(null);
  const [texto, setTexto] = useState("");
  const [cargando, setCargando] = useState(false);
  const [indiceMensaje, setIndiceMensaje] = useState<number | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);

  // Referencias para control de pausa seguro:
  // pausadoRef guarda si el puntero está físicamente sobre el globo en escritorio
  const pausadoRef = useRef(false);
  const tiempoInicioRef = useRef(Date.now());

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [mensajes, cargando]);

  // Limpiar cualquier residuo de sessionStorage de versiones anteriores
  useEffect(() => {
    try {
      sessionStorage.removeItem("siebot-burbuja-descartada");
    } catch {
      // Manejo seguro para navegadores con storage bloqueado
    }
  }, []);

  useEffect(() => {
    function abrir() {
      setOpen(true);
      setIndiceMensaje(null);
      pausadoRef.current = false;
    }
    window.addEventListener("abrir-chatbot", abrir);
    return () => window.removeEventListener("abrir-chatbot", abrir);
  }, []);

  // Rotación pausada y elegante de sugerencias legales:
  // - Primera aparición a los 8.5s (permite al usuario asentarse y leer el Hero con calma).
  // - Cada consejo legal se expone durante 9s para lectura totalmente descansada.
  // - Pausa amplia de 7.5s entre un comentario y el siguiente (no satura la pantalla).
  // - Hover seguro: solo se activa en dispositivos con mouse real (hover: hover).
  // - Límite de seguridad de 16s para evitar congelamientos accidentales.
  // - Tocar o hacer clic en la nube abre el asistente virtual de inmediato.
  useEffect(() => {
    if (open) {
      setIndiceMensaje(null);
      pausadoRef.current = false;
      return;
    }

    let cancelado = false;
    let temporizador: ReturnType<typeof setTimeout>;
    let indice = 0;

    function rotar() {
      if (cancelado) return;

      // Si el cursor está encima en escritorio y no ha superado el tope de seguridad
      const tiempoHover = Date.now() - tiempoInicioRef.current;
      if (pausadoRef.current && tiempoHover < 16000) {
        temporizador = setTimeout(rotar, 1000);
        return;
      }

      pausadoRef.current = false;
      setIndiceMensaje(indice);
      tiempoInicioRef.current = Date.now();

      temporizador = setTimeout(() => {
        if (cancelado) return;

        function esperarYPasar() {
          if (cancelado) return;
          const tiempoTotal = Date.now() - tiempoInicioRef.current;
          if (pausadoRef.current && tiempoTotal < 16000) {
            temporizador = setTimeout(esperarYPasar, 1000);
          } else {
            setIndiceMensaje(null);
            pausadoRef.current = false;
            // Pausa generosa de 7.5s antes de mostrar el siguiente comentario
            temporizador = setTimeout(() => {
              if (cancelado) return;
              indice = (indice + 1) % MENSAJES_PROMOCIONALES.length;
              rotar();
            }, 7500);
          }
        }

        esperarYPasar();
      }, 9000); // 9s visible para lectura cómoda
    }

    // Primera aparición a los 8.5s tras abrir la web (no invasiva)
    temporizador = setTimeout(rotar, 8500);

    return () => {
      cancelado = true;
      clearTimeout(temporizador);
      pausadoRef.current = false;
    };
  }, [open]);

  function descartarBurbuja() {
    setIndiceMensaje(null);
    pausadoRef.current = false;
    // Si el visitante cierra voluntariamente con (X), pausar 30s
    setTimeout(() => {
      setIndiceMensaje(0);
      tiempoInicioRef.current = Date.now();
    }, 30000);
  }

  async function enviar(e: FormEvent) {
    e.preventDefault();
    const mensaje = texto.trim();
    if (!mensaje || cargando) return;

    const historial = mensajes;
    setMensajes((prev) => [...prev, { rol: "USUARIO", contenido: mensaje }]);
    setTexto("");
    setCargando(true);

    try {
      const respuesta = await enviarMensajeChatbot({
        conversacionId,
        mensaje,
        historial,
      });
      setConversacionId(respuesta.conversacionId);
      setMensajes((prev) => [
        ...prev,
        { rol: "ASISTENTE", contenido: respuesta.respuesta },
      ]);
    } catch {
      setMensajes((prev) => [
        ...prev,
        {
          rol: "ASISTENTE",
          contenido:
            "No pude procesar tu mensaje en este momento. Por favor escríbenos por WhatsApp o completa el formulario de contacto.",
        },
      ]);
    } finally {
      setCargando(false);
    }
  }

  return (
    <>
      <AnimatePresence>
        {indiceMensaje !== null && !open && (
          <motion.div
            key={indiceMensaje}
            initial={{ opacity: 0, scale: 0.65, y: 14 }}
            animate={{
              opacity: 1,
              scale: 1,
              y: [0, -3, 0],
              transition: {
                y: { duration: 3.4, repeat: Infinity, ease: "easeInOut" },
                opacity: { duration: 0.35, ease: EASE },
                scale: { duration: 0.35, ease: EASE },
              },
            }}
            exit={{ opacity: 0, scale: 0.7, y: 10, transition: { duration: 0.22 } }}
            style={{ transformOrigin: "bottom left" }}
            onMouseEnter={() => {
              if (typeof window !== "undefined" && window.matchMedia("(hover: hover)").matches) {
                pausadoRef.current = true;
              }
            }}
            onMouseLeave={() => {
              pausadoRef.current = false;
            }}
            className="fixed bottom-[4.75rem] sm:bottom-[5.5rem] left-3 sm:left-6 z-40 flex flex-col items-start select-none filter drop-shadow-[0_14px_28px_rgba(15,14,10,0.45)]"
          >
            {/* Cuerpo principal de la nube de pensamiento */}
            <div
              role="button"
              tabIndex={0}
              onClick={() => {
                setOpen(true);
                setIndiceMensaje(null);
                pausadoRef.current = false;
              }}
              onKeyDown={(e) => {
                if (e.key === "Enter" || e.key === " ") {
                  setOpen(true);
                  setIndiceMensaje(null);
                  pausadoRef.current = false;
                }
              }}
              className="group relative w-[235px] sm:w-[265px] cursor-pointer overflow-hidden rounded-2xl rounded-bl-xs border border-gold/45 bg-surface/95 p-2.5 sm:p-3 text-left shadow-lg backdrop-blur-xl ring-1 ring-gold/20 transition-all duration-300 hover:border-gold hover:shadow-[0_16px_36px_-8px_rgba(217,169,37,0.35)]"
            >
              {/* Micro resplandor aureo interior */}
              <div className="pointer-events-none absolute -right-4 -top-4 h-16 w-16 rounded-full bg-gold/10 blur-lg" />

              {/* Cabecera compacta con tema y botón cerrar */}
              <div className="flex items-center justify-between border-b border-line/50 pb-1 mb-1.5">
                <div className="flex items-center gap-1.5">
                  <span className="relative flex h-1.5 w-1.5">
                    <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-gold opacity-75" />
                    <span className="relative inline-flex h-1.5 w-1.5 rounded-full bg-gold" />
                  </span>
                  <span className="text-[10px] font-bold uppercase tracking-wider text-gold-deep">
                    {MENSAJES_PROMOCIONALES[indiceMensaje].tema}
                  </span>
                </div>

                <div className="flex items-center gap-1.5">
                  <span className="text-[9px] font-medium text-ink-soft/60">
                    {`${indiceMensaje + 1}/${MENSAJES_PROMOCIONALES.length}`}
                  </span>
                  <button
                    type="button"
                    aria-label="Cerrar sugerencia"
                    onClick={(e) => {
                      e.stopPropagation();
                      descartarBurbuja();
                    }}
                    className="flex h-4 w-4 items-center justify-center rounded-full text-ink-soft/60 transition-colors hover:bg-ink/10 hover:text-ink"
                  >
                    <X className="h-2.5 w-2.5" weight="bold" />
                  </button>
                </div>
              </div>

              {/* Texto de la nube en formato más pequeño */}
              <p className="text-[11px] sm:text-xs leading-snug text-ink transition-colors group-hover:text-gold-deep font-sans">
                {MENSAJES_PROMOCIONALES[indiceMensaje].texto}
              </p>

              {/* Micro CTA inferior */}
              <div className="mt-1.5 flex items-center justify-between pt-0.5 text-[10px] font-semibold text-gold-deep">
                <span className="inline-flex items-center gap-1 group-hover:underline">
                  Preguntar a Siebot
                  <ArrowRight className="h-2.5 w-2.5 transition-transform duration-200 group-hover:translate-x-0.5" />
                </span>
                <span className="text-[9px] font-normal text-ink-soft/50">
                  Asistente
                </span>
              </div>
            </div>

            {/* Colita de burbujas tipo nube saliendo del botón */}
            <div className="relative -mt-0.5 flex flex-col items-start pl-3 pointer-events-none" aria-hidden="true">
              {/* Burbujita intermedia */}
              <span className="h-2.5 w-2.5 rounded-full border border-gold/45 bg-surface/95 shadow-xs -translate-x-0.5" />
              {/* Burbujita pequeña que roza el botón */}
              <span className="h-1.5 w-1.5 rounded-full border border-gold/40 bg-surface/95 shadow-xs translate-x-0.5 mt-0.5" />
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      <motion.button
        type="button"
        onClick={() => {
          setOpen((v) => !v);
          setIndiceMensaje(null);
          pausadoRef.current = false;
        }}
        aria-label={open ? "Cerrar chat" : "Abrir chat"}
        initial={{ opacity: 0, scale: 0.8 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ duration: 0.5, delay: 0.9, ease: EASE }}
        whileHover={{ scale: 1.06 }}
        whileTap={{ scale: 0.96 }}
        className="fixed bottom-6 left-3 sm:left-6 z-40 flex h-13 w-13 sm:h-16 sm:w-16 items-center justify-center rounded-full bg-ink text-paper shadow-[0_10px_30px_-8px_rgba(0,0,0,0.4)] ring-2 ring-gold/40"
      >
        {/* anillo de atención: solo pulsa mientras hay un mensaje activo */}
        {indiceMensaje !== null && !open && (
          <motion.span
            aria-hidden="true"
            initial={{ opacity: 0.5, scale: 1 }}
            animate={{ opacity: 0, scale: 1.5 }}
            transition={{ duration: 1.6, repeat: Infinity, ease: "easeOut" }}
            className="absolute inset-0 rounded-full bg-gold"
          />
        )}
        <AnimatePresence initial={false} mode="wait">
          {open ? (
            <motion.span key="x" initial={{ rotate: -90, opacity: 0 }} animate={{ rotate: 0, opacity: 1 }} exit={{ rotate: 90, opacity: 0 }} transition={{ duration: 0.2 }}>
              <X className="h-6 w-6" weight="light" />
            </motion.span>
          ) : (
            <motion.span key="chat" initial={{ rotate: 90, opacity: 0, scale: 0.6 }} animate={{ rotate: 0, opacity: 1, scale: 1 }} exit={{ rotate: -90, opacity: 0, scale: 0.6 }} transition={{ duration: 0.25 }}>
              <div className="relative h-10 w-10 sm:h-13 sm:w-13 rounded-full overflow-hidden ring-1.5 ring-gold/40">
                <Image
                  src="/chatbot/siebot-vendedor.png"
                  alt="Siebot"
                  fill
                  sizes="52px"
                  className="object-cover object-top scale-110"
                />
              </div>
            </motion.span>
          )}
        </AnimatePresence>
      </motion.button>

      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ opacity: 0, y: 24, scale: 0.96 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 24, scale: 0.96 }}
            transition={{ duration: 0.35, ease: EASE }}
            className="fixed inset-x-2 bottom-20 sm:bottom-24 sm:inset-x-auto sm:left-6 z-50 flex max-h-[82vh] sm:max-h-[75vh] flex-col overflow-hidden rounded-2xl sm:rounded-3xl bg-surface shadow-[0_30px_60px_-15px_rgba(28,26,22,0.4)] ring-1 ring-line sm:w-96"
          >
            <div className="flex items-center gap-3 border-b border-line bg-ink px-5 py-4 text-paper">
              <div className="relative h-10 w-10 shrink-0 rounded-full overflow-hidden ring-2 ring-gold/40">
                <Image
                  src="/chatbot/siebot-vendedor.png"
                  alt="Siebot"
                  fill
                  sizes="40px"
                  className="object-cover object-top scale-110"
                />
                <span className="absolute bottom-0 right-0 h-2.5 w-2.5 rounded-full bg-emerald-500 ring-1 ring-ink" />
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-sm font-medium">Siebot</p>
                <p className="text-xs text-paper/60 truncate">Asistente virtual de SIE Jurídicos</p>
              </div>
              <button
                type="button"
                onClick={() => setOpen(false)}
                aria-label="Cerrar chat"
                className="flex h-8 w-8 items-center justify-center rounded-full text-paper/70 hover:bg-paper/10 hover:text-paper"
              >
                <X className="h-4 w-4" />
              </button>
            </div>

            <div ref={scrollRef} className="flex-1 space-y-3 overflow-y-auto px-4 py-4">
              {mensajes.map((m, i) => (
                <div
                  key={i}
                  className={`flex ${m.rol === "USUARIO" ? "justify-end" : "justify-start"}`}
                >
                  <p
                    className={`max-w-[85%] rounded-2xl px-4 py-2.5 text-sm leading-relaxed ${
                      m.rol === "USUARIO"
                        ? "bg-gold text-ink-fixed"
                        : "bg-ink/5 text-ink"
                    }`}
                  >
                    {m.contenido}
                  </p>
                </div>
              ))}
              {cargando && (
                <div className="flex justify-start">
                  <div className="flex gap-1 rounded-2xl bg-ink/5 px-4 py-3">
                    {[0, 1, 2].map((i) => (
                      <motion.span
                        key={i}
                        className="h-1.5 w-1.5 rounded-full bg-ink-soft"
                        animate={{ opacity: [0.3, 1, 0.3] }}
                        transition={{ duration: 1.2, repeat: Infinity, delay: i * 0.15 }}
                      />
                    ))}
                  </div>
                </div>
              )}
            </div>

            <form onSubmit={enviar} className="flex items-center gap-2 border-t border-line p-3">
              <input
                value={texto}
                onChange={(e) => setTexto(e.target.value)}
                aria-label="Escribe tu mensaje"
                placeholder="Escribe tu mensaje"
                className="flex-1 rounded-full border border-line bg-paper px-4 py-2.5 text-sm text-ink placeholder:text-ink-soft/60 focus:border-gold-deep focus:outline-none"
              />
              <button
                type="submit"
                disabled={cargando || !texto.trim()}
                aria-label="Enviar"
                className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-gold text-ink-fixed transition-opacity disabled:opacity-50"
              >
                <PaperPlaneTilt weight="fill" className="h-4 w-4" />
              </button>
            </form>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  );
}
