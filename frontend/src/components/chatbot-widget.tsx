"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import Image from "next/image";
import { AnimatePresence, motion } from "motion/react";
import { X, PaperPlaneTilt } from "@phosphor-icons/react";
import { enviarMensajeChatbot, type TurnoChat } from "@/lib/api";

const EASE = [0.16, 1, 0.3, 1] as const;

const MENSAJE_BIENVENIDA: TurnoChat = {
  rol: "ASISTENTE",
  contenido:
    "Hola, soy Siebot, el asistente virtual de SIE Jurídicos. Puedo contarte sobre nuestros horarios, ubicación y áreas de práctica, o tomar tus datos para que un abogado te contacte. ¿En qué puedo ayudarte?",
};

export function ChatbotWidget() {
  const [open, setOpen] = useState(false);
  const [mensajes, setMensajes] = useState<TurnoChat[]>([MENSAJE_BIENVENIDA]);
  const [conversacionId, setConversacionId] = useState<number | null>(null);
  const [texto, setTexto] = useState("");
  const [cargando, setCargando] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: "smooth" });
  }, [mensajes, cargando]);

  useEffect(() => {
    function abrir() {
      setOpen(true);
    }
    window.addEventListener("abrir-chatbot", abrir);
    return () => window.removeEventListener("abrir-chatbot", abrir);
  }, []);

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
      <motion.button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-label={open ? "Cerrar chat" : "Abrir chat"}
        initial={{ opacity: 0, scale: 0.8 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ duration: 0.5, delay: 0.9, ease: EASE }}
        whileHover={{ scale: 1.06 }}
        whileTap={{ scale: 0.96 }}
        className="fixed bottom-6 left-3 sm:left-6 z-40 flex h-13 w-13 sm:h-16 sm:w-16 items-center justify-center rounded-full bg-ink text-paper shadow-[0_10px_30px_-8px_rgba(0,0,0,0.4)] ring-2 ring-gold/40 cursor-pointer"
      >
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
