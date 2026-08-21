"use client";

import { useEffect, useRef } from "react";

// Antes, los <video autoPlay> de cada sección reproducían TODOS a la vez desde que
// cargaba la página, sin importar cuál estuviera realmente a la vista (7 videos, ~120MB
// en total, todos decodificando y reproduciendo en paralelo). Este componente reemplaza
// autoPlay por un IntersectionObserver: solo reproduce mientras la sección está
// realmente visible, y pausa (sin reiniciar) al salir del viewport. Reduce carga de CPU/
// GPU y de red real, sin cambiar nada del layout ni de la animación de scroll que ya
// envuelve a cada video.
export function VideoLazyPlay({
  src,
  poster,
  className,
}: {
  src: string;
  poster: string;
  className?: string;
}) {
  const ref = useRef<HTMLVideoElement>(null);

  useEffect(() => {
    const video = ref.current;
    if (!video) return;

    const observer = new IntersectionObserver(
      ([entrada]) => {
        if (entrada.isIntersecting) {
          video.play().catch(() => {
            // Los navegadores pueden rechazar play() si el usuario aún no interactuó
            // con la página; el poster se queda visible, sin romper nada.
          });
        } else {
          video.pause();
        }
      },
      { threshold: 0.15 },
    );

    observer.observe(video);
    return () => observer.disconnect();
  }, []);

  return (
    <video
      ref={ref}
      className={className}
      src={src}
      poster={poster}
      muted
      loop
      playsInline
      preload="metadata"
    />
  );
}
