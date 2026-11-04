import { useEffect, useId, useRef, useState } from "react";
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
  field,
  error,
  onBlur,
  loading = false,
}: SearchableSelectProps) {
  const [open, setOpen] = useState(false);
  const [searchTerm, setSearchTerm] = useState("");
  const [highlight, setHighlight] = useState(0);
  const containerRef = useRef<HTMLDivElement>(null);
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

  useEffect(() => {
    if (!open) return;
    const handlePointerDownOutside = (event: PointerEvent) => {
      if (containerRef.current?.contains(event.target as Node)) return;
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

  return (
    <div
      className={`searchable-select-wrap${compact ? " compact" : ""}`}
      ref={containerRef}
      data-field={field}
      onBlur={(event) => {
        if (ignoreBlurRef.current) return;
        if (event.currentTarget.contains(event.relatedTarget as Node | null)) return;
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

      {open && !disabled && (
        <div className="searchable-dropdown">
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
      )}
      <FieldError text={error} id={errorId} />
    </div>
  );
}
