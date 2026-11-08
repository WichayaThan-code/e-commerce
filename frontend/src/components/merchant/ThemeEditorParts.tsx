import { Children, isValidElement, useEffect, useId, useRef, useState, type ReactNode } from "react";
import { ChevronDown, Plus, Search, Trash2, X } from "lucide-react";
import FormField, { FieldError } from "../form/FormField";
import SearchableSelect from "../SearchableSelect";
import { labelOf, uniqueOptions, valueOf } from "./selectOptions";
import {
  ensureThemeFontLink,
  shopThemeFontStack,
  type ShopThemeFontKey,
  type ThemeColors,
  type ThemeGradient,
  type ThemeGradientStop,
} from "../../theme/shopTheme";

export const HEX = /^#[0-9a-fA-F]{6}$/;

export function ColorField({
  label, value, error, disabled, hexError, onChange,
}: {
  label: string;
  value: string;
  error?: string;
  hexError: string;
  disabled: boolean;
  onChange: (value: string) => void;
}) {
  const [text, setText] = useState(value);
  useEffect(() => setText(value), [value]);
  const invalid = !HEX.test(text);
  return (
    <FormField label={label} error={error || (invalid ? hexError : undefined)}>
      <input
        type="color"
        className="theme-color-swatch"
        aria-label={label}
        value={HEX.test(value) ? value : "#000000"}
        disabled={disabled}
        onChange={(event) => onChange(event.target.value)}
      />
      <input
        type="text"
        className="num"
        value={text}
        maxLength={7}
        spellCheck={false}
        disabled={disabled}
        aria-label={`${label} (hex)`}
        aria-invalid={invalid || Boolean(error)}
        onChange={(event) => {
          const next = event.target.value.trim();
          setText(next);
          if (HEX.test(next)) onChange(next.toLowerCase());
        }}
      />
    </FormField>
  );
}

function defaultGradientStops(colors: ThemeColors): ThemeGradientStop[] {
  return [
    { color: colors.primary, at: 0 },
    { color: colors.background, at: 100 },
  ];
}

export function GradientEditor({
  title,
  angleLabel,
  stopLabel,
  addStopLabel,
  removeStopLabel,
  gradient,
  fallbackColors,
  disabled,
  hexError,
  errorFor,
  prefix,
  onChange,
}: {
  title: string;
  angleLabel: string;
  stopLabel: (index: number) => string;
  addStopLabel: string;
  removeStopLabel: string;
  gradient: ThemeGradient | undefined;
  fallbackColors: ThemeColors;
  disabled: boolean;
  hexError: string;
  errorFor: (path: string) => string | undefined;
  prefix: string;
  onChange: (next: ThemeGradient) => void;
}) {
  const current = gradient ?? { angle: 180, stops: defaultGradientStops(fallbackColors) };
  const stops = current.stops.length >= 2 ? current.stops : defaultGradientStops(fallbackColors);

  const setStop = (index: number, change: Partial<ThemeGradientStop>) => {
    onChange({
      ...current,
      stops: stops.map((row, rowIndex) => (rowIndex === index ? { ...row, ...change } : row)),
    });
  };

  const addStop = () => {
    if (stops.length >= 4) return;
    const last = stops[stops.length - 1];
    onChange({ ...current, stops: [...stops, { color: last?.color ?? "#ffffff", at: Math.min(100, (last?.at ?? 80) + 10) }] });
  };

  const removeStop = (index: number) => {
    if (stops.length <= 2) return;
    onChange({ ...current, stops: stops.filter((_, rowIndex) => rowIndex !== index) });
  };

  return (
    <fieldset className="theme-gradient-block">
      <legend>{title}</legend>
      <FormField label={angleLabel} error={errorFor(`${prefix}.angle`)}>
        <input
          type="number"
          className="num"
          min={0}
          max={360}
          step={1}
          value={current.angle}
          disabled={disabled}
          onChange={(event) => {
            const next = Number.parseInt(event.target.value, 10);
            if (!Number.isFinite(next)) return;
            onChange({ ...current, angle: Math.max(0, Math.min(360, next)) });
          }}
        />
      </FormField>
      <ul className="theme-gradient-stops">
        {stops.map((stop, index) => (
          <li key={index} className="theme-gradient-stop">
            <ColorField
              label={stopLabel(index)}
              value={stop.color}
              hexError={hexError}
              disabled={disabled}
              error={errorFor(`${prefix}.stops[${index}].color`)}
              onChange={(color) => setStop(index, { color })}
            />
            <FormField label="%" error={errorFor(`${prefix}.stops[${index}].at`)}>
              <input
                type="number"
                className="num"
                min={0}
                max={100}
                step={1}
                value={stop.at}
                disabled={disabled}
                onChange={(event) => {
                  const next = Number.parseInt(event.target.value, 10);
                  if (!Number.isFinite(next)) return;
                  setStop(index, { at: Math.max(0, Math.min(100, next)) });
                }}
              />
            </FormField>
            <button type="button" className="theme-banner-remove" disabled={disabled || stops.length <= 2} aria-label={removeStopLabel} onClick={() => removeStop(index)}>
              <Trash2 />
            </button>
          </li>
        ))}
      </ul>
      <button type="button" className="theme-banner-add" disabled={disabled || stops.length >= 4} onClick={addStop}>
        <Plus />{addStopLabel}
      </button>
    </fieldset>
  );
}

/** AG1.1: SearchableSelect (40px, like /merchant/orders) for CUSTOM-only token dropdowns. Maps labels back to values. */
export function ThemeTokenSelect({
  label,
  value,
  options,
  onChange,
  disabled,
  error,
}: {
  label: string;
  value: string;
  options: { value: string; label: string }[];
  onChange: (value: string) => void;
  disabled?: boolean;
  error?: string;
}) {
  const opts = uniqueOptions(options);
  return (
    <div className="theme-token-select">
      <SearchableSelect
        label={label}
        options={opts.map((opt) => opt.label)}
        value={labelOf(opts, value)}
        disabled={disabled}
        error={error}
        onChange={(picked) => {
          const next = valueOf(opts, picked);
          if (next) onChange(next);
        }}
      />
    </div>
  );
}

/**
 * Field shell for ThemeMediaPicker (CUSTOM only). Same label/spacing as FormField, but a <div>
 * instead of <label> + fixed-height .form-control, so the multi-row picker never overlaps.
 */
export function MediaField({ label, error, children }: { label: ReactNode; error?: string; children: ReactNode }) {
  return (
    <div className="form-field theme-media-field">
      <span className="form-label">{label}</span>
      {children}
      <FieldError text={error} />
    </div>
  );
}
/**
 * Drop-in replacement for a native <select>/<FormSelect> in the theme editor that renders the same
 * SearchableSelect as /merchant/products/new (category). Reads the <option> children so option
 * values and labels stay identical; onChange receives a select-like event ({ target: { value } }).
 */
export function ThemeSelect({
  label, value, disabled, error, inline, onChange, children,
}: {
  label: string;
  value: string;
  disabled?: boolean;
  error?: string;
  /** Inside a list row: label kept for a11y but visually hidden. */
  inline?: boolean;
  "aria-invalid"?: boolean;
  onChange: (event: { target: { value: string } }) => void;
  children: ReactNode;
}) {
  const options: { value: string; label: string }[] = [];
  Children.toArray(children).forEach((child) => {
    if (!isValidElement<{ value?: string | number; children?: ReactNode }>(child)) return;
    const optValue = String(child.props.value ?? "");
    const text = Children.toArray(child.props.children).filter((part) => typeof part === "string" || typeof part === "number").join("");
    options.push({ value: optValue, label: text || optValue });
  });
  const opts = uniqueOptions(options);
  return (
    <div className={`theme-select${inline ? " theme-select-inline" : ""}`}>
      <SearchableSelect
        label={label}
        options={opts.map((opt) => opt.label)}
        value={labelOf(opts, value)}
        disabled={disabled}
        error={error}
        onChange={(picked) => {
          const next = valueOf(opts, picked);
          if (opts.some((opt) => opt.label === picked)) onChange({ target: { value: next } });
        }}
      />
    </div>
  );
}

type ThemeFontOption = { value: ShopThemeFontKey; label: string };

/**
 * Heading/body font picker for the theme editor: searchable list with real Thai+Latin samples,
 * keyboard navigation, and lazy Google Font loads (selected + opened options only).
 */
export function ThemeFontSelect({
  label,
  value,
  options,
  sample,
  searchPlaceholder,
  emptyLabel,
  disabled,
  error,
  onChange,
}: {
  label: string;
  value: ShopThemeFontKey;
  options: ThemeFontOption[];
  sample: string;
  searchPlaceholder: string;
  emptyLabel: string;
  disabled?: boolean;
  error?: string;
  onChange: (font: ShopThemeFontKey) => void;
}) {
  const [open, setOpen] = useState(false);
  const [searchTerm, setSearchTerm] = useState("");
  const [highlight, setHighlight] = useState(0);
  const [loaded, setLoaded] = useState<Partial<Record<ShopThemeFontKey, true>>>({});
  const containerRef = useRef<HTMLDivElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);
  const listId = useId();
  const opts = uniqueOptions(options);
  const selected = opts.find((opt) => opt.value === value) ?? opts[0];
  const filtered = opts.filter((opt) =>
    opt.label.toLowerCase().includes(searchTerm.toLowerCase())
      || opt.value.toLowerCase().includes(searchTerm.toLowerCase()),
  );

  const markLoaded = (font: ShopThemeFontKey) => {
    ensureThemeFontLink(font);
    setLoaded((prev) => (prev[font] ? prev : { ...prev, [font]: true }));
  };

  useEffect(() => {
    markLoaded(value);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- load current selection only
  }, [value]);

  useEffect(() => {
    if (!open) return;
    const selectedIndex = filtered.findIndex((opt) => opt.value === value);
    setHighlight(selectedIndex >= 0 ? selectedIndex : 0);
    const focusSearch = window.setTimeout(() => searchRef.current?.focus(), 0);
    // Warm visible options only (dedupe via ensureThemeFontLink id check).
    filtered.slice(0, 12).forEach((opt) => markLoaded(opt.value as ShopThemeFontKey));
    return () => window.clearTimeout(focusSearch);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const handlePointerDownOutside = (event: PointerEvent) => {
      if (containerRef.current?.contains(event.target as Node)) return;
      setOpen(false);
      setSearchTerm("");
    };
    document.addEventListener("pointerdown", handlePointerDownOutside);
    return () => document.removeEventListener("pointerdown", handlePointerDownOutside);
  }, [open]);

  useEffect(() => {
    if (!open || !filtered[highlight]) return;
    markLoaded(filtered[highlight].value as ShopThemeFontKey);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [highlight, open]);

  const close = () => {
    setOpen(false);
    setSearchTerm("");
  };

  const commit = (font: ShopThemeFontKey) => {
    markLoaded(font);
    onChange(font);
    close();
  };

  const moveHighlight = (delta: number) => {
    if (filtered.length === 0) return;
    setHighlight((current) => (current + delta + filtered.length) % filtered.length);
  };

  return (
    <div className="theme-select theme-font-select">
      <div className="searchable-select-wrap" ref={containerRef}>
        <span className="select-label">{label}</span>
        <div
          role="combobox"
          aria-expanded={open}
          aria-controls={listId}
          aria-label={label}
          aria-invalid={Boolean(error)}
          tabIndex={disabled ? -1 : 0}
          className={`searchable-select-btn${disabled ? " disabled" : ""}${open ? " active" : ""}${error ? " is-invalid" : ""}`}
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
              close();
            }
          }}
        >
          <span style={{ fontFamily: shopThemeFontStack(selected?.value) }}>
            {selected?.label ?? value}
          </span>
          <ChevronDown size={16} />
        </div>

        {open && !disabled ? (
          <div className="searchable-dropdown">
            <div className="dropdown-search-box">
              <Search size={16} />
              <input
                ref={searchRef}
                type="text"
                placeholder={searchPlaceholder}
                value={searchTerm}
                aria-autocomplete="list"
                aria-controls={listId}
                onChange={(event) => {
                  setSearchTerm(event.target.value);
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
                    const picked = filtered[highlight];
                    if (picked) commit(picked.value as ShopThemeFontKey);
                  } else if (event.key === "Escape") {
                    event.preventDefault();
                    close();
                  }
                }}
              />
              {searchTerm ? (
                <button type="button" className="clear-btn" onClick={() => setSearchTerm("")}>
                  <X size={14} />
                </button>
              ) : null}
            </div>
            <ul className="dropdown-options-list" id={listId} role="listbox">
              {filtered.length > 0 ? (
                filtered.map((opt, index) => {
                  const font = opt.value as ShopThemeFontKey;
                  return (
                    <li
                      key={font}
                      role="option"
                      aria-selected={font === value}
                      className={`theme-font-option${font === value ? " selected" : ""}${index === highlight ? " highlight" : ""}`}
                      onPointerDown={(event) => {
                        event.preventDefault();
                        commit(font);
                      }}
                      onMouseEnter={() => {
                        setHighlight(index);
                        markLoaded(font);
                      }}
                    >
                      <span className="theme-font-option-name">{opt.label}</span>
                      <span
                        className="theme-font-option-sample"
                        style={{ fontFamily: loaded[font] || font === value ? shopThemeFontStack(font) : undefined }}
                      >
                        {sample}
                      </span>
                    </li>
                  );
                })
              ) : (
                <li className="no-options">{emptyLabel}</li>
              )}
            </ul>
          </div>
        ) : null}
        <FieldError text={error} />
      </div>
    </div>
  );
}