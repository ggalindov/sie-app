"use client";

import { useEffect, useImperativeHandle, useRef, forwardRef, useState } from "react";
import { Turnstile, type TurnstileInstance } from "@marsidev/react-turnstile";

export type TurnstileWidgetHandle = {
  reset: () => void;
};

// Widget oficial de Cloudflare Turnstile para protección contra bots en el login.
// Implementado con @marsidev/react-turnstile para garantizar compatibilidad con React 19,
// Fast Refresh, desmontaje limpio y evitar errores de manipulación directa del DOM (removeChild).
export const TurnstileWidget = forwardRef<
  TurnstileWidgetHandle,
  { onToken: (token: string) => void; tema?: "light" | "dark" | "auto" }
>(function TurnstileWidget({ onToken, tema = "dark" }, ref) {
  const turnstileRef = useRef<TurnstileInstance | null>(null);
  const [mounted, setMounted] = useState(false);
  const [errorWidget, setErrorWidget] = useState<string | null>(null);

  const envSiteKey = process.env.NEXT_PUBLIC_TURNSTILE_SITE_KEY;

  useImperativeHandle(ref, () => ({
    reset: () => {
      turnstileRef.current?.reset();
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

  return (
    <div className="my-3 flex flex-col items-center justify-center">
      <div className="min-h-[65px] min-w-[300px] flex items-center justify-center">
        <Turnstile
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
              setErrorWidget("Verificación de seguridad requerida.");
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
      </div>
      {errorWidget && (
        <p className="mt-1 text-xs text-amber-400/90">{errorWidget}</p>
      )}
    </div>
  );
});
