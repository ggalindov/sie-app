"use client";

import { useId, useState, type InputHTMLAttributes } from "react";
import { Eye, EyeSlash } from "@phosphor-icons/react";

// Mismo estilo de input que ya usan login/perfil/usuarios del panel admin (paleta
// clara fija, ver CLAUDE.md), con un botón para revelar la contraseña en texto plano
// -- pedido explícito del usuario tras tener problemas para confirmar qué estaba
// escribiendo al iniciar sesión.
export function PasswordInput({
  id,
  className,
  ...props
}: InputHTMLAttributes<HTMLInputElement>) {
  const [visible, setVisible] = useState(false);
  const idGenerado = useId();
  const idFinal = id ?? idGenerado;

  return (
    <div className="relative">
      <input
        {...props}
        id={idFinal}
        type={visible ? "text" : "password"}
        className={`${className ?? ""} pr-11`}
      />
      <button
        type="button"
        onClick={() => setVisible((v) => !v)}
        aria-label={visible ? "Ocultar contraseña" : "Mostrar contraseña"}
        tabIndex={-1}
        className="absolute right-3 top-1/2 -translate-y-1/2 text-ink-soft/60 transition-colors hover:text-ink"
      >
        {visible ? <EyeSlash className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
      </button>
    </div>
  );
}
