import { useRef, useState } from "react";
import type React from "react";
import { isAutofillAnimation } from "./autofillSync";
import { stripThaiScript } from "../services/merchantApplicationValidation";

/**
 * Input handlers that keep Thai script (U+0E00–U+0E7F) out of an English-only field, the same way the
 * English shop name works on /merchant/register: Thai is stripped on typing, paste and browser autofill,
 * the caret stays where it was, and while an IME composition is open the raw text is kept until it ends.
 * `blocked` is true right after something was stripped (show a hint); a new clean edit clears it.
 */
export function useNoThaiInput(value: string, onValue: (next: string) => void) {
  const composing = useRef(false);
  const last = useRef(value);
  const [blocked, setBlocked] = useState(false);

  const accept = (el: HTMLInputElement) => {
    const raw = el.value;
    if (composing.current) {
      last.current = raw;
      onValue(raw);
      return;
    }
    const clean = stripThaiScript(raw);
    if (clean !== raw) {
      const caret = el.selectionStart ?? raw.length;
      const nextCaret = stripThaiScript(raw.slice(0, caret)).length;
      el.value = clean;
      if (document.activeElement === el) el.setSelectionRange(nextCaret, nextCaret);
      setBlocked(true);
    } else if (raw !== last.current) {
      // onChange and onInput both fire; only a new clean edit (not the echo of the strip) hides the hint.
      setBlocked(false);
    }
    last.current = clean;
    if (clean !== value) onValue(clean);
  };

  return {
    blocked,
    clearBlocked: () => setBlocked(false),
    handlers: {
      onChange: (event: React.ChangeEvent<HTMLInputElement>) => accept(event.currentTarget),
      onInput: (event: React.FormEvent<HTMLInputElement>) => accept(event.currentTarget),
      onAnimationStart: (event: React.AnimationEvent<HTMLInputElement>) => {
        if (isAutofillAnimation(event.animationName)) accept(event.currentTarget);
      },
      onCompositionStart: () => { composing.current = true; },
      onCompositionEnd: (event: React.CompositionEvent<HTMLInputElement>) => {
        composing.current = false;
        accept(event.currentTarget);
      },
    },
  };
}
