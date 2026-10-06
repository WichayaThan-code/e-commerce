import { useEffect, useRef, useState } from "react";
import { ChevronDown, Search, X } from "lucide-react";
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
    >
      {!compact && (
        <span className="select-label">
          {label} {required && <em>*</em>}
        </span>
      )}
      <div
        aria-label={label}
        className={`searchable-select-btn ${disabled ? "disabled" : ""} ${
          open ? "active" : ""
        }`}
        onClick={() => !disabled && setOpen((prev) => !prev)}
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
            {filteredOptions.length > 0 ? (
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
    </div>
  );
}
