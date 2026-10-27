import { useEffect, useState } from "react";

const QUERY = "(prefers-reduced-motion: reduce)";

/** true while the user asks the OS / browser for reduced motion (used to swap autoplay video for its poster). */
export function usePrefersReducedMotion(): boolean {
  const [reduced, setReduced] = useState(() =>
    typeof window !== "undefined" && typeof window.matchMedia === "function" ? window.matchMedia(QUERY).matches : false,
  );
  useEffect(() => {
    if (typeof window.matchMedia !== "function") return undefined;
    const media = window.matchMedia(QUERY);
    const update = () => setReduced(media.matches);
    update();
    media.addEventListener("change", update);
    return () => media.removeEventListener("change", update);
  }, []);
  return reduced;
}
