"use client";

import { useEffect } from "react";

/**
 * Aplica la clase .panel-admin al <body> y <html> mientras el usuario
 * esté dentro de las rutas administrativas. Asegura que tanto la página entera
 * como cualquier modal (Dialog.Portal) montado directamente en el body
 * hereden el scrollbar blanco con dorado.
 */
export function AdminBodyClass() {
  useEffect(() => {
    document.body.classList.add("panel-admin");
    document.documentElement.classList.add("panel-admin");
    return () => {
      document.body.classList.remove("panel-admin");
      document.documentElement.classList.remove("panel-admin");
    };
  }, []);

  return null;
}
