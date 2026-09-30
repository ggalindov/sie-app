import { clsx, type ClassValue } from "clsx";

export function cn(...inputs: ClassValue[]) {
  return clsx(inputs);
}

/**
 * Extrae el ID único de 11 caracteres de cualquier URL válida de YouTube:
 * - https://www.youtube.com/watch?v=ID
 * - https://youtu.be/ID
 * - https://www.youtube.com/embed/ID
 * - https://www.youtube.com/shorts/ID
 * - https://www.youtube.com/live/ID
 */
export function extraerYouTubeId(url: string | null | undefined): string | null {
  if (!url) return null;
  const limpio = url.trim();
  if (!limpio) return null;
  if (/^[a-zA-Z0-9_-]{11}$/.test(limpio)) {
    return limpio;
  }
  const match = limpio.match(
    /(?:youtu\.be\/|youtube\.com\/(?:embed\/|v\/|watch\?v=|watch\?.+&v=|shorts\/|live\/))([a-zA-Z0-9_-]{11})/i
  );
  return match ? match[1] : null;
}
