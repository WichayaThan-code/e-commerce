import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import ConfirmDialog from "../form/ConfirmDialog";
import type { OrderTag } from "../../services/merchantOrderExtensions";

type Props = {
  tags: OrderTag[];
  selectedIds: string[];
  busy?: boolean;
  onSave: (tagIds: string[]) => void;
  onCreate: (name: string, color: string | null) => Promise<OrderTag | null>;
  onClose: () => void;
};

const MAX_TAGS = 10;

export default function OrderTagPicker({ tags, selectedIds, busy, onSave, onCreate, onClose }: Props) {
  const { t } = useTranslation("merchant");
  const [query, setQuery] = useState("");
  const [picked, setPicked] = useState<string[]>(selectedIds);
  const [creating, setCreating] = useState(false);
  const [newName, setNewName] = useState("");
  const [newColor, setNewColor] = useState("#1268d7");
  const [error, setError] = useState("");

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return tags;
    return tags.filter((tag) => tag.name.toLowerCase().includes(q));
  }, [tags, query]);

  const toggle = (id: string) => {
    setPicked((current) => {
      if (current.includes(id)) return current.filter((x) => x !== id);
      if (current.length >= MAX_TAGS) {
        setError(t("orderManagement.errors.TAG_LIMIT_EXCEEDED"));
        return current;
      }
      setError("");
      return [...current, id];
    });
  };

  const create = async () => {
    const name = newName.trim();
    if (!name || name.length > 40) {
      setError(t("orderManagement.tags.nameInvalid"));
      return;
    }
    setCreating(true);
    setError("");
    try {
      const created = await onCreate(name, newColor || null);
      if (created) {
        setPicked((current) => (current.includes(created.id) || current.length >= MAX_TAGS
          ? current
          : [...current, created.id]));
        setNewName("");
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : t("orderManagement.errors.REQUEST_FAILED"));
    } finally {
      setCreating(false);
    }
  };

  return (
    <ConfirmDialog
      title={t("orderManagement.tags.pickerTitle")}
      message={t("orderManagement.tags.pickerHint")}
      cancelLabel={t("orderManagement.drawer.cancel")}
      confirmLabel={t("orderManagement.tags.save")}
      confirmDisabled={busy || creating}
      onCancel={onClose}
      onConfirm={() => onSave(picked)}
    >
      <div className="om-tag-picker">
        <input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          placeholder={t("orderManagement.tags.search")}
          aria-label={t("orderManagement.tags.search")}
        />
        <ul className="om-tag-picker-list">
          {filtered.map((tag) => (
            <li key={tag.id}>
              <label>
                <input
                  type="checkbox"
                  checked={picked.includes(tag.id)}
                  disabled={busy}
                  onChange={() => toggle(tag.id)}
                />
                <span
                  className="om-tag-swatch"
                  style={{ background: tag.color || "var(--shop-accent, var(--shop-primary, #1268d7))" }}
                  aria-hidden="true"
                />
                {tag.name}
              </label>
            </li>
          ))}
        </ul>
        <div className="om-tag-create">
          <input
            value={newName}
            maxLength={40}
            onChange={(event) => setNewName(event.target.value)}
            placeholder={t("orderManagement.tags.newName")}
            aria-label={t("orderManagement.tags.newName")}
          />
          <input
            type="color"
            value={newColor}
            onChange={(event) => setNewColor(event.target.value)}
            aria-label={t("orderManagement.tags.color")}
          />
          <button type="button" disabled={creating || busy} onClick={() => void create()}>
            {t("orderManagement.tags.create")}
          </button>
        </div>
        {error ? <p className="om-error" role="alert">{error}</p> : null}
      </div>
    </ConfirmDialog>
  );
}
