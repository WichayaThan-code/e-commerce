import { useEffect, useId, useLayoutEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { ChevronDown, Search, X } from "lucide-react";
import { Skeleton } from "./feedback/Skeleton";
import FieldError from "./form/FieldError";
import "../styles/thai-address.css";

interface SearchableSelectProps {
  label: string;
  required?: boolean;
  options: string[];
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  disabled?: boolean;
  compact?: boolean;
  /** Render menu in document.body with fixed coords (avoids table/pagination stacking). */
  portal?: boolean;
  field?: string;
  error?: string;
  onBlur?: () => void;
  loading?: boolean;
}

/**
 * Searchable dropdown used across merchant onboarding (category, address, owner title/bank, …).
 * Selecting an option must not validate against a stale empty value: option commit uses
 * pointerdown + preventDefault, skips the interim blur, then re-validates after `value` updates.
 */
export default function SearchableSelect({
  label,
  required,
  options,
  value,
  onChange,
  placeholder = "เลือกข้อมูล",
  disabled = false,
  compact = false,
  portal = false,
  field,
  error,
  onBlur,
  loading = false,
}: SearchableSelectProps) {
  const [open, setOpen] = useState(false);
  const [searchTerm, setSearchTerm] = useState("");
  const [highlight, setHighlight] = useState(0);
  const [menuPos, setMenuPos] = useState<{ top: number; left: number; width: number; maxHeight: number } | null>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLDivElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);
  const ignoreBlurRef = useRef(false);
  const validateAfterSelectRef = useRef(false);
  const listId = useId();
  const errorId = field ? `field-error-${field}` : undefined;

  const filteredOptions = options.filter((opt) =>
    opt.toLowerCase().includes(searchTerm.toLowerCase())
  );

  useEffect(() => {
    if (!validateAfterSelectRef.current) return;
    validateAfterSelectRef.current = false;
    ignoreBlurRef.current = false;
    onBlur?.();
  }, [value, onBlur]);

  useEffect(() => {
    if (!open) return;
    const selected = options.indexOf(value);
    setHighlight(selected >= 0 ? selected : 0);
    const focusSearch = window.setTimeout(() => searchRef.current?.focus(), 0);
    return () => window.clearTimeout(focusSearch);
  }, [open, options, value]);

  useLayoutEffect(() => {
    if (!open || !portal) {
      setMenuPos(null);
      return;
    }
    const update = () => {
      const trigger = triggerRef.current;
      if (!trigger) return;
      const rect = trigger.getBoundingClientRect();
      const width = Math.max(rect.width, 180);
      const gap = 4;
      const spaceBelow = window.innerHeight - rect.bottom - gap - 8;
      const spaceAbove = rect.top - gap - 8;
      const preferBelow = spaceBelow >= 160 || spaceBelow >= spaceAbove;
      const maxHeight = Math.max(120, Math.min(280, preferBelow ? spaceBelow : spaceAbove));
      const top = preferBelow
        ? rect.bottom + gap
        : Math.max(8, rect.top - gap - maxHeight);
      let left = rect.left;
      if (left + width > window.innerWidth - 8) left = Math.max(8, window.innerWidth - width - 8);
      if (left < 8) left = 8;
      setMenuPos({ top, left, width, maxHeight });
    };
    update();
    window.addEventListener("resize", update);
    window.addEventListener("scroll", update, true);
    return () => {
      window.removeEventListener("resize", update);
      window.removeEventListener("scroll", update, true);
    };
  }, [open, portal, filteredOptions.length]);

  useEffect(() => {
    if (!open) return;
    const handlePointerDownOutside = (event: PointerEvent) => {
      const target = event.target as Node;
      if (containerRef.current?.contains(target)) return;
      if (menuRef.current?.contains(target)) return;
      setOpen(false);
      setSearchTerm("");
      onBlur?.();
    };
    document.addEventListener("pointerdown", handlePointerDownOutside);
    return () => document.removeEventListener("pointerdown", handlePointerDownOutside);
  }, [open, onBlur]);

  const closeWithoutCommit = () => {
    setOpen(false);
    setSearchTerm("");
  };

  const commit = (opt: string) => {
    ignoreBlurRef.current = true;
    validateAfterSelectRef.current = true;
    onChange(opt);
    closeWithoutCommit();
    if (opt === value) {
      // Same option: `value` effect will not re-run; validate now to clear a stale error.
      validateAfterSelectRef.current = false;
      queueMicrotask(() => {
        ignoreBlurRef.current = false;
        onBlur?.();
      });
    }
  };

  const moveHighlight = (delta: number) => {
    if (filteredOptions.length === 0) return;
    setHighlight((current) => (current + delta + filteredOptions.length) % filteredOptions.length);
  };

  const menu = open && !disabled ? (
    <div
      ref={menuRef}
      className={`searchable-dropdown${portal ? " is-portaled" : ""}`}
      style={
        portal && menuPos
          ? {
              position: "fixed",
              top: menuPos.top,
              left: menuPos.left,
              width: menuPos.width,
              minWidth: menuPos.width,
              maxWidth: menuPos.width,
              maxHeight: menuPos.maxHeight,
              marginTop: 0,
              zIndex: 1200,
            }
          : undefined
      }
    >
      <div className="dropdown-search-box">
        <Search size={16} />
        <input
          ref={searchRef}
          type="text"
          placeholder="ค้นหา..."
          value={searchTerm}
          aria-autocomplete="list"
          aria-controls={listId}
          onChange={(e) => {
            setSearchTerm(e.target.value);
            setHighlight(0);
          }}
          onKeyDown={(event) => {
            if (event.key === "ArrowDown") {
              event.preventDefault();
              moveHighlight(1);
            } else if (event.key === "ArrowUp") {
              event.preventDefault();
              moveHighlight(-1);
            } else if (event.key === "Enter") {
              event.preventDefault();
              const picked = filteredOptions[highlight];
              if (picked) commit(picked);
            } else if (event.key === "Escape") {
              event.preventDefault();
              closeWithoutCommit();
              onBlur?.();
            }
          }}
        />
        {searchTerm && (
          <button
            type="button"
            className="clear-btn"
            onClick={() => setSearchTerm("")}
          >
            <X size={14} />
          </button>
        )}
      </div>
      <ul className="dropdown-options-list" id={listId} role="listbox">
        {loading ? (
          Array.from({ length: 3 }, (_, index) => (
            <li key={index} className="dropdown-skeleton" aria-hidden="true">
              <Skeleton height={14} width="85%" />
            </li>
          ))
        ) : filteredOptions.length > 0 ? (
          filteredOptions.map((opt, index) => (
            <li
              key={opt}
              role="option"
              aria-selected={opt === value}
              className={`${opt === value ? "selected" : ""}${index === highlight ? " highlight" : ""}`}
              onPointerDown={(event) => {
                // Prevent search input blur → empty validate before the value is committed.
                event.preventDefault();
                commit(opt);
              }}
            >
              {opt}
            </li>
          ))
        ) : (
          <li className="no-options">ไม่พบข้อมูล</li>
        )}
      </ul>
    </div>
  ) : null;

  return (
    <div
      className={`searchable-select-wrap${compact ? " compact" : ""}${open ? " open" : ""}`}
      ref={containerRef}
      data-field={field}
      onBlur={(event) => {
        if (ignoreBlurRef.current) return;
        if (event.currentTarget.contains(event.relatedTarget as Node | null)) return;
        if (menuRef.current?.contains(event.relatedTarget as Node | null)) return;
        closeWithoutCommit();
        onBlur?.();
      }}
    >
      {!compact && (
        <span className="select-label">
          {label} {required && <em>*</em>}
        </span>
      )}
      <div
        ref={triggerRef}
        role="combobox"
        aria-expanded={open}
        aria-controls={listId}
        aria-label={label}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? errorId : undefined}
        tabIndex={disabled ? -1 : 0}
        className={`searchable-select-btn ${disabled ? "disabled" : ""} ${
          open ? "active" : ""
        }${error ? " is-invalid" : ""}`}
        onClick={() => !disabled && setOpen((prev) => !prev)}
        onKeyDown={(event) => {
          if (disabled) return;
          if (event.key === "Enter" || event.key === " ") {
            event.preventDefault();
            setOpen((prev) => !prev);
          } else if (event.key === "ArrowDown") {
            event.preventDefault();
            setOpen(true);
          } else if (event.key === "Escape" && open) {
            event.preventDefault();
            closeWithoutCommit();
            onBlur?.();
          }
        }}
      >
        <span>{value || placeholder}</span>
        <ChevronDown size={16} />
      </div>

      {portal ? (menu && menuPos ? createPortal(menu, document.body) : null) : menu}
      <FieldError text={error} id={errorId} />
    </div>
  );
}
