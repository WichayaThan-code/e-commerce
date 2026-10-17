import { useEffect, useRef, useState } from "react";
import { ChevronDown, Search, X } from "lucide-react";
import { Skeleton } from "./feedback/Skeleton";
import { FieldError } from "./form/FormField";
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
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (
        containerRef.current &&
        !containerRef.current.contains(event.target as Node)
      ) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const filteredOptions = options.filter((opt) =>
    opt.toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <div
      className={`searchable-select-wrap${compact ? " compact" : ""}`}
      ref={containerRef}
      data-field={field}
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget as Node | null)) onBlur?.()
      }}
    >
      {!compact && (
        <span className="select-label">
          {label} {required && <em>*</em>}
        </span>
      )}
      <div
        aria-label={label}
        aria-invalid={Boolean(error)}
        tabIndex={disabled ? -1 : 0}
        className={`searchable-select-btn ${disabled ? "disabled" : ""} ${
          open ? "active" : ""
        }`}
        onClick={() => !disabled && setOpen((prev) => !prev)}
        onKeyDown={(event) => {
          if (disabled) return
          if (event.key === "Enter" || event.key === " ") {
            event.preventDefault()
            setOpen((prev) => !prev)
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
              type="text"
              placeholder="ค้นหา..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              autoFocus
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
          <ul className="dropdown-options-list">
            {loading ? (
              Array.from({ length: 3 }, (_, index) => (
                <li key={index} className="dropdown-skeleton" aria-hidden="true">
                  <Skeleton height={14} width="85%" />
                </li>
              ))
            ) : filteredOptions.length > 0 ? (
              filteredOptions.map((opt) => (
                <li
                  key={opt}
                  className={opt === value ? "selected" : ""}
                  onClick={() => {
                    onChange(opt);
                    setOpen(false);
                    setSearchTerm("");
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
      <FieldError text={error} />
    </div>
  );
}
