"use client";

import Link from "next/link";
import { useRef, type MouseEvent } from "react";
import { gsap } from "gsap";

// Subrayado que se traza con GSAP al pasar el mouse (entra desde el lado por
// donde entró el cursor, sale por el lado contrario), aislado de Motion.
export function NavLinkUnderline({
  href,
  label,
  onClick,
}: {
  href: string;
  label: string;
  onClick?: () => void;
}) {
  const lineRef = useRef<HTMLSpanElement>(null);
  const wrapRef = useRef<HTMLAnchorElement>(null);

  function onEnter(e: MouseEvent<HTMLAnchorElement>) {
    const rect = wrapRef.current?.getBoundingClientRect();
    const fromLeft = rect ? e.clientX - rect.left < rect.width / 2 : true;
    gsap.fromTo(
      lineRef.current,
      { scaleX: 0, transformOrigin: fromLeft ? "left" : "right" },
      { scaleX: 1, duration: 0.4, ease: "power3.out" },
    );
  }

  function onLeave(e: MouseEvent<HTMLAnchorElement>) {
    const rect = wrapRef.current?.getBoundingClientRect();
    const fromLeft = rect ? e.clientX - rect.left < rect.width / 2 : true;
    gsap.to(lineRef.current, {
      scaleX: 0,
      transformOrigin: fromLeft ? "left" : "right",
      duration: 0.35,
      ease: "power3.out",
    });
  }

  return (
    <Link
      ref={wrapRef}
      href={href}
      onClick={onClick}
      onMouseEnter={onEnter}
      onMouseLeave={onLeave}
      className="relative rounded-full px-4 py-2 text-sm text-ink-soft transition-colors duration-300 hover:text-ink"
    >
      {label}
      <span className="pointer-events-none absolute inset-x-4 bottom-1.5 h-px overflow-hidden">
        <span
          ref={lineRef}
          className="block h-full w-full origin-left scale-x-0 bg-gold-deep"
        />
      </span>
    </Link>
  );
}
