"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
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
        className="fixed bottom-6 left-6 z-40 flex h-16 w-16 items-center justify-center rounded-full bg-ink text-paper shadow-[0_10px_30px_-8px_rgba(0,0,0,0.4)]"
      >
        <AnimatePresence initial={false} mode="wait">
          {open ? (
            <motion.span key="x" initial={{ rotate: -90, opacity: 0 }} animate={{ rotate: 0, opacity: 1 }} exit={{ rotate: 90, opacity: 0 }} transition={{ duration: 0.2 }}>
              <X className="h-6 w-6" weight="light" />
            </motion.span>
          ) : (
            <motion.span key="chat" initial={{ rotate: 90, opacity: 0, scale: 0.6 }} animate={{ rotate: 0, opacity: 1, scale: 1 }} exit={{ rotate: -90, opacity: 0, scale: 0.6 }} transition={{ duration: 0.25 }}>
              <SiebotMascot size={38} />
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
