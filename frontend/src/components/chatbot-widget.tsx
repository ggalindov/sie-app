"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import Image from "next/image";
import { AnimatePresence, motion } from "motion/react";
import { X, PaperPlaneTilt } from "@phosphor-icons/react";
import { enviarMensajeChatbot, type TurnoChat } from "@/lib/api";
import { SiebotMascot } from "@/components/siebot-mascot";

const EASE = [0.16, 1, 0.3, 1] as const;

const MENSAJE_BIENVENIDA: TurnoChat = {
  rol: "ASISTENTE",
  contenido:
    "Hola, soy Siebot, el asistente virtual de SIE Jurídicos. Puedo contarte sobre nuestros horarios, ubicación y áreas de práctica, o tomar tus datos para que un abogado te contacte. ¿En qué puedo ayudarte?",
};

// Mensajes de venta que Siebot va rotando solo mientras el chat está cerrado (ver el
// efecto de ciclo más abajo). A propósito NO llaman al chatbot real (cero costo de
// Anthropic): son frases fijas, siempre basadas en cifras y hechos ya publicados en el
// sitio (ver trusted-by.tsx: 20+ años, 800+ casos, 6 áreas de práctica) en vez de
// superlativos sin sustento ("somos los mejores") -- una firma de abogados tiene límites
// éticos reales sobre publicidad comparativa y garantías de resultado (Ley 1123 de 2007),
// así que la "mejor empresa" se transmite con hechos verificables, no con la frase literal.
const MENSAJES_PROMOCIONALES: string[] = [
  "¿Tienes una situación legal pendiente? Agenda tu asesoría con nosotros.",
  "Más de 20 años acompañando a nuestros clientes en sus procesos legales.",
  "Más de 800 casos atendidos, con el mismo compromiso desde el primero.",
  "Laboral, familia, civil, mercantil, administrativo o constitucional: te asesoramos.",
  "Cada caso es distinto. Cuéntame el tuyo y te digo cómo podemos ayudarte.",
  "¿Prefieres WhatsApp? Con gusto te respondemos por ahí también.",
  "Un abogado real revisa cada solicitud que llega, no solo un asistente virtual.",
  "¿Tienes dudas sobre tu proceso? Empecemos por una conversación, sin compromiso.",
];

export function ChatbotWidget() {
  const [open, setOpen] = useState(false);
  const [mensajes, setMensajes] = useState<TurnoChat[]>([MENSAJE_BIENVENIDA]);
  const [conversacionId, setConversacionId] = useState<number | null>(null);
  const [texto, setTexto] = useState("");
  const [cargando, setCargando] = useState(false);
  const [indiceMensaje, setIndiceMensaje] = useState<number | null>(null);
  const [descartado, setDescartado] = useState(false);
  const [avatarRoto, setAvatarRoto] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [mensajes, cargando]);

  // Si el visitante ya cerró la burbuja con la (X), no vuelve a insistir en esta misma
  // pestaña -- sessionStorage (no localStorage): en una visita nueva, o en otra pestaña,
  // Siebot vuelve a presentarse con normalidad.
  useEffect(() => {
    try {
      if (sessionStorage.getItem("siebot-burbuja-descartada") === "1") setDescartado(true);
    } catch {
      // Safari en modo privado, o storage bloqueado por el navegador: simplemente no
      // recuerda el descarte entre recargas, no es un caso que valga la pena romper nada.
    }
  }, []);

  // Siebot va rotando sola por la biblioteca de mensajes de venta (ver
  // MENSAJES_PROMOCIONALES) mientras el chat está cerrado -- nunca llama al chatbot real,
  // cero costo de Anthropic. Encadenamiento de setTimeout (no setInterval) a propósito:
  // cada paso agenda el siguiente solo después de que el anterior termina, así que un
  // cierre/reapertura rápida del chat nunca dispara dos ciclos superpuestos ni acumula
  // temporizadores fantasma -- con setInterval eso hay que prevenirlo a mano.
  useEffect(() => {
    if (open || descartado) return;
    let cancelado = false;
    let temporizador: ReturnType<typeof setTimeout>;
    let indice = 0;

    function mostrarSiguiente() {
      if (cancelado) return;
      setIndiceMensaje(indice);
      temporizador = setTimeout(() => {
        if (cancelado) return;
        setIndiceMensaje(null); // se retira un instante antes de traer el siguiente
        temporizador = setTimeout(() => {
          indice = (indice + 1) % MENSAJES_PROMOCIONALES.length;
          mostrarSiguiente();
        }, 500);
      }, 6500);
    }

    temporizador = setTimeout(mostrarSiguiente, 3200); // primera aparición
    return () => {
      cancelado = true;
      clearTimeout(temporizador);
    };
  }, [open, descartado]);

  function descartarBurbuja() {
    setIndiceMensaje(null);
    setDescartado(true);
    try {
      sessionStorage.setItem("siebot-burbuja-descartada", "1");
    } catch {
      // ver comentario del efecto de arriba
    }
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
          <motion.button
            type="button"
            onClick={() => {
              setOpen(true);
              setIndiceMensaje(null);
            }}
            key={indiceMensaje}
            initial={{ opacity: 0, y: 14, scale: 0.85, rotate: -2 }}
            animate={{ opacity: 1, y: 0, scale: 1, rotate: 0 }}
            exit={{ opacity: 0, y: 8, scale: 0.9, rotate: 1 }}
            transition={{ type: "spring", stiffness: 340, damping: 22 }}
            className="fixed bottom-24 left-6 z-40 flex w-64 items-start gap-2.5 rounded-2xl rounded-bl-sm bg-surface p-3 text-left shadow-[0_20px_45px_-15px_rgba(28,26,22,0.4)] ring-1 ring-line"
          >
            {!avatarRoto && (
              <Image
                src="/chatbot/siebot-vendedor.png"
                alt=""
                width={40}
                height={40}
                onError={() => setAvatarRoto(true)}
                className="h-10 w-10 shrink-0 rounded-full object-cover ring-2 ring-gold-pale"
              />
            )}
            <p className="flex-1 pt-1 text-sm leading-snug text-ink">
              {MENSAJES_PROMOCIONALES[indiceMensaje]}
            </p>
            <span
              role="button"
              tabIndex={0}
              aria-label="Cerrar mensaje"
              onClick={(e) => {
                e.stopPropagation();
                descartarBurbuja();
              }}
              onKeyDown={(e) => {
                if (e.key === "Enter" || e.key === " ") {
                  e.stopPropagation();
                  descartarBurbuja();
                }
              }}
              className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full text-ink-soft/60 hover:bg-ink/5 hover:text-ink-soft"
            >
              <X className="h-3 w-3" weight="bold" />
            </span>
          </motion.button>
        )}
      </AnimatePresence>

      <motion.button
        type="button"
        onClick={() => {
          setOpen((v) => !v);
          setIndiceMensaje(null);
        }}
        aria-label={open ? "Cerrar chat" : "Abrir chat"}
        initial={{ opacity: 0, scale: 0.8 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ duration: 0.5, delay: 0.9, ease: EASE }}
        whileHover={{ scale: 1.06 }}
        whileTap={{ scale: 0.96 }}
        className="fixed bottom-6 left-6 z-40 flex h-16 w-16 items-center justify-center rounded-full bg-ink text-paper shadow-[0_10px_30px_-8px_rgba(0,0,0,0.4)]"
      >
        {/* anillo de atención: solo pulsa mientras hay un mensaje de venta activo, para que
            el botón mismo (no solo la burbuja) llame la atención de reojo -- el visitante
            puede tener la burbuja fuera de su campo de visión inmediato. */}
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
              <SiebotMascot size={42} />
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
            className="fixed inset-x-4 bottom-24 z-40 flex max-h-[70vh] flex-col overflow-hidden rounded-3xl bg-surface shadow-[0_30px_60px_-15px_rgba(28,26,22,0.4)] ring-1 ring-line sm:inset-x-auto sm:bottom-24 sm:left-6 sm:w-96"
          >
            <div className="flex items-center gap-3 border-b border-line bg-ink px-5 py-4 text-paper">
              <SiebotMascot size={36} pensando={cargando} />
              <div>
                <p className="text-sm font-medium">Siebot</p>
                <p className="text-xs text-paper/60">Asistente virtual de SIE Jurídicos</p>
              </div>
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
