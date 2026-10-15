type ToggleSwitchProps = {
  checked: boolean;
  disabled?: boolean;
  label: string;
  onText: string;
  offText: string;
  onChange: (checked: boolean) => void;
};

export default function ToggleSwitch({ checked, disabled, label, onText, offText, onChange }: ToggleSwitchProps) {
  return (
    <label className="toggle-switch">
      <input
        type="checkbox"
        role="switch"
        checked={checked}
        disabled={disabled}
        aria-label={label}
        onChange={(event) => onChange(event.target.checked)}
      />
      <span className="toggle-switch-track" aria-hidden="true" />
      <span className="toggle-switch-text">{checked ? onText : offText}</span>
    </label>
  );
}
