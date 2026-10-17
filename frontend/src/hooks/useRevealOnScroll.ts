import { useEffect, useRef } from "react";

function alreadyInView(node: HTMLElement) {
  const rect = node.getBoundingClientRect();
  const viewport = window.innerHeight || document.documentElement.clientHeight || 0;
  if (viewport <= 0) return true;
  // Reveal if any part is near the viewport (including just below the fold).
  return rect.top < viewport + 80 && rect.bottom > -40;
}

/** Adds `.is-revealed` when the node enters the viewport. Honours prefers-reduced-motion. */
export function useRevealOnScroll<T extends HTMLElement>() {
  const ref = useRef<T | null>(null);

  useEffect(() => {
    const node = ref.current;
    if (!node) return;

    const reveal = () => node.classList.add("is-revealed");

    if (typeof window.matchMedia === "function" && window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      reveal();
      return;
    }
    // Theme editor preview / tiny mounts — never leave opacity:0 empty space.
    if (node.closest(".shop-preview-frame")) {
      reveal();
      return;
    }
    if (typeof IntersectionObserver === "undefined") {
      reveal();
      return;
    }

    if (alreadyInView(node)) {
      reveal();
      return;
    }

    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting || entry.intersectionRatio > 0) {
            reveal();
            observer.unobserve(entry.target);
          }
        }
      },
      { threshold: 0, rootMargin: "80px 0px 80px 0px" },
    );
    observer.observe(node);

    // Second chance after layout (absolute images / fonts) so zero-height mounts still reveal.
    const retry = window.setTimeout(() => {
      if (!node.classList.contains("is-revealed") && alreadyInView(node)) reveal();
    }, 120);

    return () => {
      window.clearTimeout(retry);
      observer.disconnect();
    };
  }, []);

  return ref;
}
