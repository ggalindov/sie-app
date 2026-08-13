"use client";

import { useEffect, useState } from "react";
import { AnimatePresence, motion } from "motion/react";
import { Moon, Sun } from "@phosphor-icons/react";

export function ThemeToggle({ className }: { className?: string }) {
  const [theme, setTheme] = useState<"light" | "dark">("dark");

  useEffect(() => {
    const actual = document.documentElement.getAttribute("data-theme");
    if (actual === "light" || actual === "dark") setTheme(actual);
  }, []);

  function toggle() {
    const next = theme === "dark" ? "light" : "dark";
    setTheme(next);
    document.documentElement.setAttribute("data-theme", next);
    try {
      localStorage.setItem("sie-theme", next);
    } catch {
      // localStorage puede fallar en modo privado; el tema simplemente no persiste
    }
  }

  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={theme === "dark" ? "Cambiar a modo claro" : "Cambiar a modo oscuro"}
      className={`relative flex h-9 w-9 shrink-0 items-center justify-center overflow-hidden rounded-full transition-colors ${className ?? "text-ink-soft hover:bg-paper hover:text-ink"}`}
    >
      <AnimatePresence initial={false} mode="wait">
        {theme === "dark" ? (
          <motion.span
            key="sun"
            initial={{ rotate: -90, opacity: 0 }}
            animate={{ rotate: 0, opacity: 1 }}
            exit={{ rotate: 90, opacity: 0 }}
            transition={{ duration: 0.25 }}
          >
            <Sun className="h-[18px] w-[18px]" weight="light" />
          </motion.span>
        ) : (
          <motion.span
            key="moon"
            initial={{ rotate: 90, opacity: 0 }}
            animate={{ rotate: 0, opacity: 1 }}
            exit={{ rotate: -90, opacity: 0 }}
            transition={{ duration: 0.25 }}
          >
            <Moon className="h-[18px] w-[18px]" weight="light" />
          </motion.span>
        )}
      </AnimatePresence>
    </button>
  );
}
