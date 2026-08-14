import fs from "node:fs";
import path from "node:path";
import Image from "next/image";

// Server Component a propósito (sin "use client"): comprueba en disco si el
// archivo de video ya existe en /public antes de renderizar nada, así nunca
// hay un <video> roto ni un parpadeo de error en el cliente. Mientras el
// usuario no suba el archivo de video real, se ve la foto de reserva; en
// cuanto lo suba (con el nombre exacto esperado) y se reinicie el server de
// desarrollo, el video aparece solo, sin tocar código de nuevo.
export function SectionVideo({
  src,
  poster,
  posterAlt,
  className = "",
}: {
  src: string;
  poster: string;
  posterAlt: string;
  className?: string;
}) {
  const existeVideo = fs.existsSync(path.join(process.cwd(), "public", src));

  if (!existeVideo) {
    return (
      <Image
        src={poster}
        alt={posterAlt}
        fill
        sizes="100vw"
        className={className}
        priority
      />
    );
  }

  return (
    <video
      className={className}
      src={src}
      poster={poster}
      autoPlay
      muted
      loop
      playsInline
      preload="metadata"
    />
  );
}
