import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import ConfirmDialog from "../form/ConfirmDialog";
import { Skeleton } from "../feedback/Skeleton";
import { orderErrorMessage } from "./orderFormat";
import {
  createTag,
  deleteTag,
  listTags,
  updateTag,
  type OrderTag,
} from "../../services/merchantOrderExtensions";

type Props = {
  shopId: number;
  open: boolean;
  onClose: () => void;
  onChanged: (tags: OrderTag[]) => void;
  notify: (text: string, tone?: "ok" | "fail") => void;
};

export default function OrderTagManager({ shopId, open, onClose, onChanged, notify }: Props) {
  const { t } = useTranslation("merchant");
  const [tags, setTags] = useState<OrderTag[] | null>(null);
  const [busy, setBusy] = useState(false);
  const [name, setName] = useState("");
  const [color, setColor] = useState("#1268d7");
  const [editId, setEditId] = useState<string | null>(null);
  const [deleteId, setDeleteId] = useState<string | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!open) return;
    let alive = true;
    setTags(null);
    setError("");
    listTags(shopId)
      .then((rows) => { if (alive) setTags(rows); })
      .catch((caught) => { if (alive) setError(orderErrorMessage(t, caught)); });
    return () => { alive = false; };
  }, [open, shopId, t]);

  if (!open) return null;

  const save = async () => {
    const trimmed = name.trim();
    if (!trimmed || trimmed.length > 40) {
      setError(t("orderManagement.tags.nameInvalid"));
      return;
    }
    setBusy(true);
    setError("");
    try {
      const body = { name: trimmed, color: color || null };
      const next = editId
        ? await updateTag(shopId, editId, body)
        : await createTag(shopId, body);
      const rows = editId
        ? (tags ?? []).map((tag) => (tag.id === next.id ? next : tag))
        : [...(tags ?? []), next];
      setTags(rows);
      onChanged(rows);
      setName("");
      setEditId(null);
      notify(t("orderManagement.tags.saved"));
    } catch (caught) {
      setError(orderErrorMessage(t, caught));
    } finally {
      setBusy(false);
    }
  };

  const confirmDelete = async () => {
    if (!deleteId) return;
    setBusy(true);
    try {
      await deleteTag(shopId, deleteId);
      const rows = (tags ?? []).filter((tag) => tag.id !== deleteId);
      setTags(rows);
      onChanged(rows);
      setDeleteId(null);
      notify(t("orderManagement.tags.deleted"));
    } catch (caught) {
      setError(orderErrorMessage(t, caught));
      setDeleteId(null);
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <ConfirmDialog
        title={t("orderManagement.tags.managerTitle")}
        message={t("orderManagement.tags.managerHint")}
        cancelLabel={t("orderManagement.drawer.close")}
        confirmLabel={editId ? t("orderManagement.tags.update") : t("orderManagement.tags.create")}
        confirmDisabled={busy}
        onCancel={onClose}
        onConfirm={() => void save()}
      >
        <div className="om-tag-manager">
          {tags == null && !error ? (
            <div aria-busy="true"><Skeleton height={80} /><Skeleton height={40} /></div>
          ) : error && tags == null ? (
            <p className="om-error" role="alert">{error}</p>
          ) : (
            <ul className="om-tag-manager-list">
              {(tags ?? []).map((tag) => (
                <li key={tag.id}>
                  <span
                    className="om-tag-swatch"
                    style={{ background: tag.color || "var(--shop-accent, var(--shop-primary, #1268d7))" }}
                    aria-hidden="true"
                  />
                  <b>{tag.name}</b>
                  {tag.usageCount != null ? <small className="num">{tag.usageCount}</small> : null}
                  <button
                    type="button"
                    disabled={busy}
                    onClick={() => {
                      setEditId(tag.id);
                      setName(tag.name);
                      setColor(tag.color || "#1268d7");
                    }}
                  >
                    {t("orderManagement.tags.edit")}
                  </button>
                  <button type="button" className="danger" disabled={busy} onClick={() => setDeleteId(tag.id)}>
                    {t("orderManagement.tags.delete")}
                  </button>
                </li>
              ))}
            </ul>
          )}
          <div className="om-tag-create">
            <input
              value={name}
              maxLength={40}
              onChange={(event) => setName(event.target.value)}
              placeholder={t("orderManagement.tags.newName")}
              aria-label={t("orderManagement.tags.newName")}
            />
            <input
              type="color"
              value={color}
              onChange={(event) => setColor(event.target.value)}
              aria-label={t("orderManagement.tags.color")}
            />
          </div>
          {error && tags != null ? <p className="om-error" role="alert">{error}</p> : null}
        </div>
      </ConfirmDialog>
      {deleteId ? (
        <ConfirmDialog
          title={t("orderManagement.tags.deleteTitle")}
          message={t("orderManagement.tags.deleteBody")}
          cancelLabel={t("orderManagement.drawer.cancel")}
          confirmLabel={t("orderManagement.tags.delete")}
          danger
          confirmDisabled={busy}
          onCancel={() => setDeleteId(null)}
          onConfirm={() => void confirmDelete()}
        />
      ) : null}
    </>
  );
}
