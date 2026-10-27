import { Children, isValidElement, useEffect, useState, type ReactNode } from "react";
import { Plus, Trash2 } from "lucide-react";
import FormField, { FieldError } from "../form/FormField";
import SearchableSelect from "../SearchableSelect";
import { labelOf, uniqueOptions, valueOf } from "./selectOptions";
import type { ThemeColors, ThemeGradient, ThemeGradientStop } from "../../theme/shopTheme";

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