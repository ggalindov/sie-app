"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import Image from "next/image";
import { AnimatePresence, motion } from "motion/react";
import {
  X,
  PaperPlaneTilt,
  ArrowClockwise,
  WhatsappLogo,
  Sparkle,
  CalendarCheck,
} from "@phosphor-icons/react";
import { enviarMensajeChatbot, type TurnoChat } from "@/lib/api";

const EASE = [0.16, 1, 0.3, 1] as const;

const MENSAJE_BIENVENIDA: TurnoChat = {
  rol: "ASISTENTE",
  contenido:
    "Hola, soy **Siebot**, asesor virtual de **SIE Jurídicos**.\n\nEn nuestra firma contamos con más de 20 años de trayectoria y más de 800 casos resueltos con éxito en derecho laboral, de familia, civil, comercial y marcas en Colombia.\n\n¿En qué situación jurídica te encuentras hoy? Cuéntame brevemente tu caso para orientarte y coordinar tu asesoría personalizada con uno de nuestros abogados especialistas.",
};

const SUGERENCIAS = [
  {
    icono: "⚖️",
    etiqueta: "Despido o liquidación",
    mensaje: "Fui despedido de mi trabajo. ¿Cómo me pueden asesorar y cómo agendamos cita?",
  },
  {
    icono: "👨‍👩‍👧",
    etiqueta: "Alimentos o divorcio",
    mensaje: "Necesito asesoría jurídica para fijar una cuota de alimentos o tramitar un divorcio.",
  },
  {
    icono: "🏢",
    etiqueta: "Marcas o crear SAS",
    mensaje: "Deseo registrar una marca ante la SIC o constituir una empresa SAS con asesoría de la firma.",
  },
  {
    icono: "📅",
    etiqueta: "Agendar cita con abogado",
    mensaje: "Deseo agendar una consulta jurídica personalizada con un abogado especialista de SIE Jurídicos.",
  },
];

function FormatoTexto({
  texto,
  rol,
}: {
  texto: string;
  rol: "USUARIO" | "ASISTENTE";
}) {
  function renderInline(line: string) {
    const parts: (string | React.ReactNode)[] = [];
    const regex = /(\*\*.*?\*\*|\[.*?\]\(https?:\/\/[^\s)]+\)|https?:\/\/[^\s]+)/g;
    let lastIndex = 0;
    let match: RegExpExecArray | null;

    while ((match = regex.exec(line)) !== null) {
      if (match.index > lastIndex) {
        parts.push(line.substring(lastIndex, match.index));
      }
      const token = match[0];
      if (token.startsWith("**") && token.endsWith("**") && token.length >= 4) {
        const content = token.slice(2, -2);
        parts.push(
          <strong
            key={match.index}
            className={
              rol === "USUARIO"
                ? "font-bold text-ink-fixed"
                : "font-semibold text-ink"
            }
          >
            {content}
          </strong>
        );
      } else if (token.startsWith("[") && token.includes("](")) {
        const linkMatch = token.match(/\[(.*?)\]\((https?:\/\/[^\s)]+)\)/);
        if (linkMatch) {
          parts.push(
            <a
              key={match.index}
              href={linkMatch[2]}
              target="_blank"
              rel="noopener noreferrer"
              className={
                rol === "USUARIO"
                  ? "underline font-medium text-ink-fixed"
                  : "underline font-medium text-gold-deep hover:text-ink transition-colors"
              }
            >
              {linkMatch[1]}
            </a>
          );
        } else {
          parts.push(token);
        }
      } else if (token.startsWith("http")) {
        parts.push(
          <a
            key={match.index}
            href={token}
            target="_blank"
            rel="noopener noreferrer"
            className={
              rol === "USUARIO"
                ? "underline font-medium text-ink-fixed"
                : "underline font-medium text-gold-deep hover:text-ink transition-colors"
            }
          >
            {token}
          </a>
        );
      } else {
        parts.push(token);
      }
      lastIndex = match.index + token.length;
    }
    if (lastIndex < line.length) {
      parts.push(line.substring(lastIndex));
    }
    return parts.length > 0 ? parts : line;
  }

  // Paso 1: Normalización para unir viñetas huérfanas (ej: "•\nTexto del requisito")
  const raw = texto.split("\n");
  const lineasNormalizadas: string[] = [];
  for (let i = 0; i < raw.length; i++) {
    const t = raw[i].trim();
    if (!t) {
      lineasNormalizadas.push("");
      continue;
    }
    // Si la línea es únicamente una viñeta ("•", "-", "*") y la siguiente tiene texto, unirlas
    if (/^[•\-\*]$/.test(t) && i + 1 < raw.length && raw[i + 1].trim().length > 0) {
      lineasNormalizadas.push(`• ${raw[i + 1].trim()}`);
      i++;
    } else {
      lineasNormalizadas.push(t);
    }
  }

  // Paso 2: Clasificación en bloques estructurales
  type Elemento =
    | { tipo: "encabezado"; texto: string }
    | { tipo: "lista"; items: string[] }
    | { tipo: "parrafo"; lineas: string[] };

  const elementos: Elemento[] = [];
  let bufferParrafo: string[] = [];
  let bufferLista: string[] = [];

  function flushParrafo() {
    if (bufferParrafo.length > 0) {
      elementos.push({ tipo: "parrafo", lineas: [...bufferParrafo] });
      bufferParrafo = [];
    }
  }

  function flushLista() {
    if (bufferLista.length > 0) {
      elementos.push({ tipo: "lista", items: [...bufferLista] });
      bufferLista = [];
    }
  }

  for (const line of lineasNormalizadas) {
    const trimmed = line.trim();
    if (!trimmed) {
      flushLista();
      flushParrafo();
      continue;
    }

    // Encabezado de markdown (ej: "## 📄 Cuota de alimentos")
    const matchEncabezado = trimmed.match(/^#{1,4}\s+(.*)$/);
    if (matchEncabezado) {
      flushLista();
      flushParrafo();
      elementos.push({ tipo: "encabezado", texto: matchEncabezado[1] });
      continue;
    }

    // Viñetas o listas numeradas
    const esBullet = /^([•\-\*]|\d+[\.\)])\s*(.*)$/.test(trimmed);
    if (esBullet) {
      flushParrafo();
      const limpio = trimmed.replace(/^([•\-\*]|\d+[\.\)])\s*/, "").trim();
      if (limpio.length > 0) {
        bufferLista.push(limpio);
      }
    } else {
      flushLista();
      bufferParrafo.push(trimmed);
    }
  }
  flushLista();
  flushParrafo();

  return (
    <div className="space-y-2 text-[13.5px] sm:text-sm leading-relaxed">
      {elementos.map((elem, idx) => {
        if (elem.tipo === "encabezado") {
          return (
            <p
              key={idx}
              className="font-semibold text-ink text-[13.5px] sm:text-sm mt-2 mb-1 flex items-center gap-1.5"
            >
              {renderInline(elem.texto)}
            </p>
          );
        }

        if (elem.tipo === "lista") {
          return (
            <ul key={idx} className="space-y-1.5 my-1.5 pl-0.5">
              {elem.items.map((item, itemIdx) => (
                <li key={itemIdx} className="flex items-start gap-2">
                  <span
                    className={
                      rol === "USUARIO"
                        ? "text-ink-fixed font-bold mt-0.5 text-xs select-none shrink-0"
                        : "text-gold-deep font-bold mt-0.5 text-xs select-none shrink-0"
                    }
                  >
                    •
                  </span>
                  <span className="flex-1 min-w-0 leading-relaxed">
                    {renderInline(item)}
                  </span>
                </li>
              ))}
            </ul>
          );
        }

        return (
          <p key={idx} className="whitespace-normal">
            {elem.lineas.map((linea, lIdx) => (
              <span key={lIdx}>
                {renderInline(linea)}
                {lIdx < elem.lineas.length - 1 && <br />}
              </span>
            ))}
          </p>
        );
      })}
    </div>
  );
}

export function ChatbotWidget() {
  const [open, setOpen] = useState(false);
  const [mensajes, setMensajes] = useState<TurnoChat[]>([MENSAJE_BIENVENIDA]);
  const [conversacionId, setConversacionId] = useState<number | null>(null);
  const [texto, setTexto] = useState("");
  const [cargando, setCargando] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    scrollRef.current?.scrollTo({
      top: scrollRef.current.scrollHeight,
      behavior: "smooth",
    });
  }, [mensajes, cargando]);

  useEffect(() => {
    function abrir() {
      setOpen(true);
    }
    window.addEventListener("abrir-chatbot", abrir);
    return () => window.removeEventListener("abrir-chatbot", abrir);
  }, []);

  useEffect(() => {
    if (open) {
      setTimeout(() => {
        inputRef.current?.focus();
      }, 300);
    }
  }, [open]);

  async function ejecutarEnvio(mensajeAEnviar: string) {
    const mensaje = mensajeAEnviar.trim();
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
            "En este momento no pude procesar tu mensaje. Puedes escribirnos directamente por WhatsApp al [+57 324 3668845](https://wa.me/573243668845) para coordinar tu cita prioritaria con nuestros abogados.",
        },
      ]);
    } finally {
      setCargando(false);
    }
  }

  async function enviar(e: FormEvent) {
    e.preventDefault();
    await ejecutarEnvio(texto);
  }

  function reiniciarChat() {
    setConversacionId(null);
    setMensajes([MENSAJE_BIENVENIDA]);
    setTexto("");
  }

  return (
    <>
      {/* Botón flotante para abrir/cerrar (Modo Claro) */}
      <motion.button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-label={open ? "Cerrar chat con Siebot" : "Abrir chat con Siebot"}
        initial={{ opacity: 0, scale: 0.8 }}
        animate={{ opacity: 1, scale: 1 }}
        transition={{ duration: 0.5, delay: 0.9, ease: EASE }}
        whileHover={{ scale: 1.05 }}
        whileTap={{ scale: 0.95 }}
        className="fixed bottom-5 left-3 sm:left-6 z-40 flex h-14 w-14 sm:h-16 sm:w-16 items-center justify-center rounded-full bg-surface text-ink shadow-[0_12px_30px_-8px_rgba(20,19,15,0.25)] ring-2 ring-gold/60 border border-gold/30 hover:border-gold cursor-pointer"
      >
        <AnimatePresence initial={false} mode="wait">
          {open ? (
            <motion.span
              key="x"
              initial={{ rotate: -90, opacity: 0 }}
              animate={{ rotate: 0, opacity: 1 }}
              exit={{ rotate: 90, opacity: 0 }}
              transition={{ duration: 0.2 }}
            >
              <X className="h-6 w-6 text-ink" weight="bold" />
            </motion.span>
          ) : (
            <motion.span
              key="chat"
              initial={{ rotate: 90, opacity: 0, scale: 0.6 }}
              animate={{ rotate: 0, opacity: 1, scale: 1 }}
              exit={{ rotate: -90, opacity: 0, scale: 0.6 }}
              transition={{ duration: 0.25 }}
              className="relative"
            >
              <div className="relative h-11 w-11 sm:h-13 sm:w-13 rounded-full overflow-hidden ring-1.5 ring-gold/60">
                <Image
                  src="/chatbot/siebot-vendedor.png"
                  alt="Siebot Asesor Virtual"
                  fill
                  sizes="56px"
                  className="object-cover object-top scale-110"
                />
              </div>
              <span className="absolute -top-1 -right-1 flex h-3.5 w-3.5">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75" />
                <span className="relative inline-flex rounded-full h-3.5 w-3.5 bg-emerald-500 ring-2 ring-surface" />
              </span>
            </motion.span>
          )}
        </AnimatePresence>
      </motion.button>

      {/* Ventana de chat responsiva (Completamente en Modo Claro y Optimizada a Venta) */}
      <AnimatePresence>
        {open && (
          <motion.div
            initial={{ opacity: 0, y: 20, scale: 0.96 }}
            animate={{ opacity: 1, y: 0, scale: 1 }}
            exit={{ opacity: 0, y: 20, scale: 0.96 }}
            transition={{ duration: 0.3, ease: EASE }}
            className="fixed inset-x-2 bottom-3 sm:bottom-24 sm:inset-x-auto sm:left-6 z-50 flex max-h-[88vh] sm:max-h-[640px] w-auto sm:w-[420px] flex-col overflow-hidden rounded-2xl sm:rounded-3xl bg-surface shadow-[0_25px_60px_-15px_rgba(20,18,15,0.25)] ring-1 ring-line border border-gold/30"
          >
            {/* Header del Chatbot (Modo Claro) */}
            <div className="flex items-center gap-3 border-b border-line bg-surface px-4 py-3.5 sm:px-5 sm:py-4 text-ink">
              <div className="relative h-10 w-10 shrink-0 rounded-full overflow-hidden ring-2 ring-gold/60 shadow-xs">
                <Image
                  src="/chatbot/siebot-vendedor.png"
                  alt="Siebot"
                  fill
                  sizes="40px"
                  className="object-cover object-top scale-110"
                />
                <span className="absolute bottom-0 right-0 h-2.5 w-2.5 rounded-full bg-emerald-500 ring-1.5 ring-surface" />
              </div>

              <div className="flex-1 min-w-0">
                <p className="text-sm font-semibold tracking-tight text-ink">Siebot</p>
                <p className="text-xs text-ink-soft truncate flex items-center gap-1.5">
                  <span className="h-2 w-2 rounded-full bg-emerald-500 inline-block shadow-2xs" />
                  En línea • Asesoría y Agendamiento SIE
                </p>
              </div>

              {/* Botones de acción rápida en header */}
              <div className="flex items-center gap-1">
                <a
                  href="https://wa.me/573243668845?text=Hola%2C%20quisiera%20agendar%20una%20asesor%C3%ADa%20jur%C3%ADdica%20con%20un%20abogado%20de%20SIE%20Jur%C3%ADdicos"
                  target="_blank"
                  rel="noopener noreferrer"
                  title="Agendar por WhatsApp con un abogado"
                  aria-label="Agendar por WhatsApp con un abogado"
                  className="flex h-8 w-8 items-center justify-center rounded-full text-emerald-600 hover:bg-emerald-50 hover:text-emerald-700 transition-colors"
                >
                  <WhatsappLogo className="h-4.5 w-4.5" weight="fill" />
                </a>

                <button
                  type="button"
                  onClick={reiniciarChat}
                  title="Reiniciar conversación"
                  aria-label="Reiniciar conversación"
                  className="flex h-8 w-8 items-center justify-center rounded-full text-ink-soft hover:bg-ink/5 hover:text-ink transition-colors"
                >
                  <ArrowClockwise className="h-4 w-4" />
                </button>

                <button
                  type="button"
                  onClick={() => setOpen(false)}
                  title="Cerrar chat"
                  aria-label="Cerrar chat"
                  className="flex h-8 w-8 items-center justify-center rounded-full text-ink-soft hover:bg-ink/5 hover:text-ink transition-colors"
                >
                  <X className="h-4 w-4" weight="bold" />
                </button>
              </div>
            </div>

            {/* Área de mensajes con scroll suave */}
            <div
              ref={scrollRef}
              className="flex-1 space-y-3.5 overflow-y-auto px-3.5 py-4 sm:px-4 sm:py-4 bg-paper/50"
            >
              {mensajes.map((m, i) => (
                <div
                  key={i}
                  className={`flex ${
                    m.rol === "USUARIO" ? "justify-end" : "justify-start"
                  }`}
                >
                  {m.rol === "ASISTENTE" ? (
                    <div className="flex items-start gap-2.5 max-w-[92%] sm:max-w-[88%]">
                      <div className="relative h-7 w-7 shrink-0 rounded-full overflow-hidden ring-1 ring-gold/40 mt-0.5 shadow-2xs">
                        <Image
                          src="/chatbot/siebot-vendedor.png"
                          alt="Siebot"
                          fill
                          sizes="28px"
                          className="object-cover object-top scale-110"
                        />
                      </div>
                      <div className="rounded-2xl rounded-tl-xs bg-surface border border-line px-3.5 py-3 sm:px-4 text-ink shadow-[0_2px_8px_-2px_rgba(0,0,0,0.05)]">
                        <FormatoTexto texto={m.contenido} rol="ASISTENTE" />
                      </div>
                    </div>
                  ) : (
                    <div className="max-w-[85%] rounded-2xl rounded-tr-xs bg-gold text-ink-fixed px-4 py-2.5 shadow-xs font-medium">
                      <FormatoTexto texto={m.contenido} rol="USUARIO" />
                    </div>
                  )}
                </div>
              ))}

              {/* Sugerencias Rápidas al inicio de la conversación orientadas a venta */}
              {mensajes.length <= 2 && (
                <div className="pt-2 pb-1">
                  <p className="text-[11.5px] font-medium text-ink-soft mb-2 px-1 flex items-center gap-1">
                    <Sparkle weight="fill" className="h-3 w-3 text-gold-deep" />
                    Consultas frecuentes que atendemos:
                  </p>
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-1.5">
                    {SUGERENCIAS.map((sug, idx) => (
                      <button
                        key={idx}
                        type="button"
                        onClick={() => ejecutarEnvio(sug.mensaje)}
                        disabled={cargando}
                        className="flex items-center gap-2 rounded-xl border border-line bg-surface px-3 py-2 text-left text-xs text-ink hover:border-gold hover:bg-gold/5 active:scale-[0.98] transition-all disabled:opacity-50 shadow-2xs"
                      >
                        <span className="text-sm shrink-0">{sug.icono}</span>
                        <span className="truncate font-medium">{sug.etiqueta}</span>
                      </button>
                    ))}
                  </div>
                </div>
              )}

              {/* Indicador de carga interactivo */}
              {cargando && (
                <div className="flex items-start gap-2.5">
                  <div className="relative h-7 w-7 shrink-0 rounded-full overflow-hidden ring-1 ring-gold/40 mt-0.5">
                    <Image
                      src="/chatbot/siebot-vendedor.png"
                      alt="Siebot pensando"
                      fill
                      sizes="28px"
                      className="object-cover object-top scale-110"
                    />
                  </div>
                  <div className="flex items-center gap-2 rounded-2xl rounded-tl-xs bg-surface border border-line px-4 py-3 shadow-xs">
                    <span className="text-xs text-ink-soft">Siebot está preparando la orientación</span>
                    <div className="flex gap-1 items-center">
                      {[0, 1, 2].map((i) => (
                        <motion.span
                          key={i}
                          className="h-1.5 w-1.5 rounded-full bg-gold-deep"
                          animate={{ opacity: [0.3, 1, 0.3], y: [0, -2, 0] }}
                          transition={{
                            duration: 1,
                            repeat: Infinity,
                            delay: i * 0.18,
                          }}
                        />
                      ))}
                    </div>
                  </div>
                </div>
              )}
            </div>

            {/* Input y formulario de envío */}
            <form
              onSubmit={enviar}
              className="border-t border-line bg-surface p-2.5 sm:p-3"
            >
              <div className="flex items-center gap-2">
                <input
                  ref={inputRef}
                  value={texto}
                  onChange={(e) => setTexto(e.target.value)}
                  aria-label="Escribe tu consulta legal"
                  placeholder="Cuéntame tu caso para agendar asesoría..."
                  className="flex-1 rounded-full border border-line bg-paper px-4 py-2.5 text-base sm:text-sm text-ink placeholder:text-ink-soft/60 focus:border-gold-deep focus:bg-surface focus:outline-none focus:ring-1 focus:ring-gold-deep transition-all shadow-inner"
                />
                <button
                  type="submit"
                  disabled={cargando || !texto.trim()}
                  aria-label="Enviar consulta"
                  className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-gold text-ink-fixed shadow-sm transition-all hover:bg-gold-deep hover:scale-105 active:scale-95 disabled:opacity-40 disabled:hover:scale-100 disabled:cursor-not-allowed cursor-pointer"
                >
                  <PaperPlaneTilt weight="fill" className="h-4 w-4" />
                </button>
              </div>
            </form>
          </motion.div>
        )}
      </AnimatePresence>
    </>
  );
}
