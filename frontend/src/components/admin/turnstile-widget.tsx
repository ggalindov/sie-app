"use client";

import { useEffect, useImperativeHandle, useRef, forwardRef, useState, Component, type ReactNode } from "react";
import { Turnstile, type TurnstileInstance } from "@marsidev/react-turnstile";
import { ArrowClockwise } from "@phosphor-icons/react";

export type TurnstileWidgetHandle = {
  reset: () => void;
};

// Error boundary interno para evitar que cualquier falla en la carga o script externo de Turnstile
// rompa la página de login completa.
class TurnstileErrorBoundary extends Component<
  { children: ReactNode; fallback: ReactNode },
  { hasError: boolean }
> {
  constructor(props: { children: ReactNode; fallback: ReactNode }) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError() {
    return { hasError: true };
  }

  componentDidCatch(error: Error) {
    console.warn("Falla en el contenedor de Turnstile:", error);
  }

  render() {
    if (this.state.hasError) {
      return this.props.fallback;
    }
    return this.props.children;
  }
}

// Widget oficial de Cloudflare Turnstile para protección contra bots en el login.
export const TurnstileWidget = forwardRef<
  TurnstileWidgetHandle,
  { onToken: (token: string) => void; tema?: "light" | "dark" | "auto" }
>(function TurnstileWidget({ onToken, tema = "dark" }, ref) {
  const turnstileRef = useRef<TurnstileInstance | null>(null);
  const [mounted, setMounted] = useState(false);
  const [errorWidget, setErrorWidget] = useState<string | null>(null);
  const [intentos, setIntentos] = useState(0);

  const envSiteKey = process.env.NEXT_PUBLIC_TURNSTILE_SITE_KEY;

  useImperativeHandle(ref, () => ({
    reset: () => {
      try {
        turnstileRef.current?.reset();
      } catch {
        setIntentos((i) => i + 1);
      }
    },
  }));

  useEffect(() => {
    setMounted(true);
  }, []);

  if (!envSiteKey) return null;
  if (!mounted) return null;

  // En localhost / 127.0.0.1, Cloudflare bloquea la clave de producción si el dominio no se
  // ha autorizado manualmente en el panel de Cloudflare. Por ende, en localhost se utiliza
  // la clave de prueba oficial de Cloudflare ("1x00000000000000000000AA") que siempre
  // renderiza y pasa satisfactoriamente; en producción (siejuridicos.com) se usa la clave real.
  const isLocalhost =
    typeof window !== "undefined" &&
    (window.location.hostname === "localhost" ||
      window.location.hostname === "127.0.0.1" ||
      window.location.hostname === "");

  const siteKeyEfectiva = isLocalhost ? "1x00000000000000000000AA" : envSiteKey;

  const fallbackUI = (
    <div className="flex flex-col items-center justify-center p-3 text-center">
      <p className="text-xs text-amber-300">No se pudo cargar la verificación automática.</p>
      <button
        type="button"
        onClick={() => setIntentos((i) => i + 1)}
        className="mt-2 inline-flex items-center gap-1.5 rounded-full border border-gold/40 px-3 py-1 text-xs text-gold hover:bg-gold/10"
      >
        <ArrowClockwise className="h-3.5 w-3.5" />
        Reintentar verificación
      </button>
    </div>
  );

  return (
    <div className="my-3 flex flex-col items-center justify-center">
      <div className="min-h-[65px] min-w-[300px] flex items-center justify-center">
        <TurnstileErrorBoundary fallback={fallbackUI}>
          <Turnstile
            key={`turnstile-${intentos}`}
            ref={turnstileRef}
            siteKey={siteKeyEfectiva}
            onSuccess={(token) => {
              setErrorWidget(null);
              onToken(token);
            }}
            onError={(errCode) => {
              console.warn("Cloudflare Turnstile estado:", errCode);
              if (isLocalhost) {
                onToken("XXXX.DUMMY.LOCAL.BYPASS");
              } else {
                setErrorWidget(
                  "Verificación requerida. Si usas Brave o bloqueadores de anuncios, permite Cloudflare para acceder."
                );
              }
            }}
            onExpire={() => {
              onToken("");
            }}
            options={{
              theme: tema,
              size: "normal",
              retry: "auto",
              refreshExpired: "auto",
            }}
          />
        </TurnstileErrorBoundary>
      </div>
      {errorWidget && (
        <div className="mt-2 flex flex-col items-center gap-1 text-center">
          <p className="max-w-xs text-xs text-amber-400/90">{errorWidget}</p>
          <button
            type="button"
            onClick={() => {
              setErrorWidget(null);
              setIntentos((i) => i + 1);
            }}
            className="inline-flex items-center gap-1 text-[11px] text-gold underline hover:text-gold-light"
          >
            <ArrowClockwise className="h-3 w-3" />
            Recargar verificación
          </button>
        </div>
      )}
    </div>
  );
});
