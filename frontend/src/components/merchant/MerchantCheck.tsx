import { useEffect, useRef, type ChangeEvent } from "react";

type Props = {
  checked: boolean;
  indeterminate?: boolean;
  disabled?: boolean;
  label: string;
  onChange: (event: ChangeEvent<HTMLInputElement>) => void;
};

/** Shared merchant multi-select checkbox (products / orders). */
export default function MerchantCheck({
  checked,
  indeterminate = false,
  disabled = false,
  label,
  onChange,
}: Props) {
  const inputRef = useRef<HTMLInputElement>(null);
  useEffect(() => {
    if (inputRef.current) inputRef.current.indeterminate = indeterminate;
  }, [indeterminate]);
  return (
    <label className={`pm-check${disabled ? " is-disabled" : ""}`}>
      <input
        ref={inputRef}
        type="checkbox"
        checked={checked}
        disabled={disabled}
        aria-label={label}
        onChange={onChange}
      />
      <span aria-hidden="true" />
    </label>
  );
}
