import Image from "next/image";
import Link from "next/link";
import {
  FacebookLogo,
  InstagramLogo,
  LinkedinLogo,
  EnvelopeSimple,
  LockSimple,
} from "@phosphor-icons/react/dist/ssr";
import { navLinks, siteConfig } from "@/lib/site-config";
import { areasPractica } from "@/lib/content";

export function SiteFooter() {
  return (
    <footer className="snap-footer bg-night text-night-ink">
      <div className="mx-auto max-w-7xl px-6 py-16 md:py-20">
        <div className="grid gap-12 md:grid-cols-12">
          <div className="md:col-span-5">
            <Link href="/" className="flex items-center gap-2">
              <Image
                src="/marca/logo.png"
                alt={siteConfig.nombre}
                width={36}
                height={36}
                className="h-9 w-9 object-contain"
              />
              <span className="font-display text-lg font-semibold">
                {siteConfig.nombre}
              </span>
            </Link>
            <p className="mt-4 max-w-xs text-sm leading-relaxed text-night-ink/60">
              Asesoría jurídica integral para empresas y personas naturales,
              en Bogotá desde hace más de 20 años.
            </p>
            <div className="mt-6 flex items-center gap-3">
              <a
                href={siteConfig.redes.facebook}
                target="_blank"
                rel="noopener noreferrer"
                aria-label="Facebook"
                className="flex h-10 w-10 items-center justify-center rounded-full bg-night-ink/10 transition-colors hover:bg-gold hover:text-night"
              >
                <FacebookLogo weight="light" className="h-5 w-5" />
              </a>
              <a
                href={siteConfig.redes.instagram}
                target="_blank"
                rel="noopener noreferrer"
                aria-label="Instagram"
                className="flex h-10 w-10 items-center justify-center rounded-full bg-night-ink/10 transition-colors hover:bg-gold hover:text-night"
              >
                <InstagramLogo weight="light" className="h-5 w-5" />
              </a>
              <a
                href={siteConfig.redes.linkedin}
                target="_blank"
                rel="noopener noreferrer"
                aria-label="LinkedIn"
                className="flex h-10 w-10 items-center justify-center rounded-full bg-night-ink/10 transition-colors hover:bg-gold hover:text-night"
              >
                <LinkedinLogo weight="light" className="h-5 w-5" />
              </a>
              <a
                href={`mailto:${siteConfig.correo}`}
                aria-label="Correo"
                className="flex h-10 w-10 items-center justify-center rounded-full bg-night-ink/10 transition-colors hover:bg-gold hover:text-night"
              >
                <EnvelopeSimple weight="light" className="h-5 w-5" />
              </a>
            </div>
          </div>

          <div className="md:col-span-3">
            <p className="text-xs font-medium uppercase tracking-[0.14em] text-night-ink/50">
              Enlaces útiles
            </p>
            <nav className="mt-4 flex flex-col gap-3 text-sm">
              {navLinks.map((link) => (
                <Link
                  key={link.href}
                  href={link.href}
                  className="text-night-ink/80 transition-colors hover:text-gold"
                >
                  {link.label}
                </Link>
              ))}
              <Link
                href="/preguntas-frecuentes"
                className="text-night-ink/80 transition-colors hover:text-gold"
              >
                Preguntas frecuentes
              </Link>
              <Link
                href="/consulta-caso"
                className="text-night-ink/80 transition-colors hover:text-gold"
              >
                Consultar mi caso
              </Link>
            </nav>
          </div>

          <div className="md:col-span-4">
            <p className="text-xs font-medium uppercase tracking-[0.14em] text-night-ink/50">
              Áreas de práctica
            </p>
            <nav className="mt-4 grid grid-cols-2 gap-x-4 gap-y-3 text-sm">
              {areasPractica.map((area) => (
                <Link
                  key={area.slug}
                  href={`/areas/${area.slug}`}
                  className="text-night-ink/80 transition-colors hover:text-gold"
                >
                  {area.nombre.replace("Derecho ", "")}
                </Link>
              ))}
            </nav>
          </div>
        </div>

        <div className="mt-14 flex flex-col gap-4 border-t border-night-ink/10 pt-8 text-xs text-night-ink/50 sm:flex-row sm:items-center sm:justify-between">
          <p>
            © {new Date().getFullYear()} {siteConfig.nombre}. Todos los
            derechos reservados.
          </p>
          <div className="flex items-center gap-4">
            <p>Tratamiento de datos personales conforme a la Ley 1581 de 2012.</p>
            <Link
              href="/admin/login"
              aria-label="Acceso interno para administradores y abogados"
              title="Acceso interno"
              className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full text-night-ink/30 transition-colors hover:bg-night-ink/10 hover:text-gold"
            >
              <LockSimple weight="light" className="h-3.5 w-3.5" />
            </Link>
          </div>
        </div>
      </div>
    </footer>
  );
}
