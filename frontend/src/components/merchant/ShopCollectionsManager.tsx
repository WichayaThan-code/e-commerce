import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  ChevronDown,
  ChevronUp,
  Layers,
  Package,
  Plus,
  Search,
  Trash2,
} from "lucide-react";
import FormField from "../form/FormField";
import ConfirmDialog from "../form/ConfirmDialog";
import ThemeMediaPicker from "./ThemeMediaPicker";
import MerchantSaveButton from "./MerchantSaveButton";
import { MediaField, ThemeTokenSelect } from "./ThemeEditorParts";
import { ApiError, mediaUrl, productApi, type Product } from "../../services/api";
import {
  collectionApi,
  type CollectionWrite,
  type MerchantCollection,
  type MerchantCollectionDetail,
} from "../../services/collectionsApi";
import "../../styles/theme-editor-controls.css";
import "../../styles/form-fields.css";
import "../../styles/shop-collections-manager.css";

type Props = {
  shopId: number | null;
  shopSlug: string;
  canUpdate: boolean;
  deniedTitle?: string;
  notify: (text: string) => void;
};

type Draft = { slug: string; nameTh: string; nameEn: string; description: string; coverMediaId: string; published: boolean };
const EMPTY: Draft = { slug: "", nameTh: "", nameEn: "", description: "", coverMediaId: "", published: true };
const toDraft = (row: MerchantCollection): Draft => ({
  slug: row.slug, nameTh: row.nameTh, nameEn: row.nameEn ?? "", description: row.description ?? "",
  coverMediaId: row.coverMediaId ?? "", published: row.published,
});
const toWrite = (d: Draft): CollectionWrite => ({
  slug: d.slug.trim() || null, nameTh: d.nameTh.trim(), nameEn: d.nameEn.trim() || null,
  description: d.description.trim() || null, coverMediaId: d.coverMediaId || null, published: d.published,
});
const move = <T,>(items: T[], from: number, to: number) => {
  const next = [...items];
  const [item] = next.splice(from, 1);
  next.splice(to, 0, item);
  return next;
};

/** Merchant "คอลเลกชัน" tab: CRUD + ordering + product membership. Merchant API only. */
export default function ShopCollectionsManager({ shopId, shopSlug, canUpdate, deniedTitle, notify }: Props) {
  const { t } = useTranslation("merchant");
  const [rows, setRows] = useState<MerchantCollection[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detail, setDetail] = useState<MerchantCollectionDetail | null>(null);
  const [draft, setDraft] = useState<Draft>(EMPTY);
  const [creating, setCreating] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [products, setProducts] = useState<Product[]>([]);
  const [productQuery, setProductQuery] = useState("");

  const errorText = useCallback((err: unknown) => {
    if (err instanceof ApiError) {
      const map: Record<string, string> = {};
      (err.fieldErrors ?? []).forEach((fe) => { map[fe.field] = fe.message; });
      setFieldErrors(map);
      return err.message || t("collections.errors.generic");
    }
    return t("collections.errors.generic");
  }, [t]);

  const load = useCallback(async () => {
    if (shopId == null) return;
    setLoading(true);
    try { setRows(await collectionApi.list(shopId)); setError(""); }
    catch (err) { setError(errorText(err)); }
    finally { setLoading(false); }
  }, [shopId, errorText]);

  useEffect(() => { void load(); }, [load]);

  useEffect(() => {
    if (shopId == null) return;
    let alive = true;
    const timer = window.setTimeout(() => {
      productApi.list(shopId, { q: productQuery.trim() || undefined, size: 50 })
        .then((page) => { if (alive) setProducts(page.items); })
        .catch(() => { if (alive) setProducts([]); });
    }, 250);
    return () => { alive = false; window.clearTimeout(timer); };
  }, [shopId, productQuery]);

  const open = async (id: string) => {
    if (shopId == null) return;
    setCreating(false); setSelectedId(id); setFieldErrors({}); setError("");
    try {
      const next = await collectionApi.get(shopId, id);
      setDetail(next); setDraft(toDraft(next.collection));
    } catch (err) { setError(errorText(err)); }
  };

  const startCreate = () => { setCreating(true); setSelectedId(null); setDetail(null); setDraft(EMPTY); setFieldErrors({}); setError(""); };

  const save = async () => {
    if (shopId == null || !canUpdate) return;
    if (!draft.nameTh.trim()) { setFieldErrors({ nameTh: t("collections.errors.nameRequired") }); return; }
    setBusy(true); setFieldErrors({});
    try {
      const saved = creating ? await collectionApi.create(shopId, toWrite(draft)) : await collectionApi.update(shopId, selectedId!, toWrite(draft));
      setDetail(saved); setDraft(toDraft(saved.collection)); setSelectedId(saved.collection.id); setCreating(false);
      notify(t("collections.saved"));
      await load();
    } catch (err) { setError(errorText(err)); }
    finally { setBusy(false); }
  };

  const remove = async () => {
    if (shopId == null || !selectedId) return;
    setBusy(true);
    try {
      await collectionApi.remove(shopId, selectedId);
      setSelectedId(null); setDetail(null); setDraft(EMPTY); setConfirmDelete(false);
      notify(t("collections.deleted"));
      await load();
    } catch (err) { setError(errorText(err)); }
    finally { setBusy(false); }
  };

  const reorder = async (from: number, to: number) => {
    if (shopId == null || to < 0 || to >= rows.length) return;
    const next = move(rows, from, to);
    setRows(next);
    try { setRows(await collectionApi.reorder(shopId, next.map((row) => row.id))); }
    catch (err) { setError(errorText(err)); await load(); }
  };

  const mutateProducts = async (action: () => Promise<MerchantCollectionDetail>) => {
    setBusy(true);
    try { const next = await action(); setDetail(next); await load(); }
    catch (err) { setError(errorText(err)); }
    finally { setBusy(false); }
  };

  const members = detail?.products ?? [];
  const addable = products.filter((p) => !members.some((m) => m.productId === p.id));
  const disabled = !canUpdate || busy;
  const editing = creating || detail != null;
  const publishedCount = rows.filter((row) => row.published).length;
  const draftCount = rows.length - publishedCount;
  const productTotal = rows.reduce((sum, row) => sum + row.productCount, 0);
  const selectedCover = creating ? null : detail?.collection.coverUrl || rows.find((row) => row.id === selectedId)?.coverUrl || null;

  return (
    <div className="store-collections">
      <section className="sc-hero">
        <div className="sc-hero-copy">
          <span className="sc-hero-icon" aria-hidden="true"><Layers /></span>
          <div>
            <h2>{t("collections.title")}</h2>
            <p>{t("collections.subtitle")}</p>
          </div>
        </div>
        <button type="button" className="sc-add" onClick={startCreate} disabled={!canUpdate} title={deniedTitle}>
          <Plus aria-hidden />{t("collections.add")}
        </button>
      </section>

      <div className="sc-stats" aria-label={t("collections.stats.label")}>
        <div><small>{t("collections.stats.total")}</small><b>{rows.length}</b></div>
        <div><small>{t("collections.stats.published")}</small><b>{publishedCount}</b></div>
        <div><small>{t("collections.stats.draft")}</small><b>{draftCount}</b></div>
        <div><small>{t("collections.stats.products")}</small><b>{productTotal}</b></div>
      </div>

      <div className="sc-layout">
        <section className="sc-panel sc-list">
          <header className="sc-panel-head">
            <div>
              <h3>{t("collections.listTitle")}</h3>
              <p>{t("collections.listHint")}</p>
            </div>
          </header>

          {loading ? (
            <p className="sc-empty-note">{t("collections.loading")}</p>
          ) : rows.length === 0 ? (
            <div className="sc-empty">
              <span aria-hidden="true"><Layers /></span>
              <b>{t("collections.empty")}</b>
              <p>{t("collections.emptyHint")}</p>
              <button type="button" className="sc-add" onClick={startCreate} disabled={!canUpdate} title={deniedTitle}>
                <Plus aria-hidden />{t("collections.add")}
              </button>
            </div>
          ) : (
            <ul className="sc-list-items">
              {rows.map((row, index) => {
                const cover = mediaUrl(row.coverUrl);
                const active = row.id === selectedId && !creating;
                return (
                  <li key={row.id} className={`sc-list-item${active ? " active" : ""}`}>
                    <button type="button" className="sc-list-pick" onClick={() => void open(row.id)}>
                      <span className="sc-list-thumb" aria-hidden="true">
                        {cover ? <img src={cover} alt="" /> : <Layers />}
                      </span>
                      <span className="sc-list-copy">
                        <b>{row.nameTh}</b>
                        <small>{t("collections.productCount", { count: row.productCount })}</small>
                      </span>
                      <span className={`sc-badge${row.published ? " is-live" : ""}`}>
                        {row.published ? t("collections.published") : t("collections.draft")}
                      </span>
                    </button>
                    <span className="sc-move">
                      <button type="button" aria-label={t("collections.moveUp")} disabled={!canUpdate || index === 0} onClick={() => void reorder(index, index - 1)}><ChevronUp /></button>
                      <button type="button" aria-label={t("collections.moveDown")} disabled={!canUpdate || index === rows.length - 1} onClick={() => void reorder(index, index + 1)}><ChevronDown /></button>
                    </span>
                  </li>
                );
              })}
            </ul>
          )}
        </section>

        {editing ? (
          <section className="sc-panel sc-editor">
            <header className="sc-panel-head sc-editor-head">
              <div className="sc-editor-title">
                <span className="sc-list-thumb is-lg" aria-hidden="true">
                  {selectedCover ? <img src={mediaUrl(selectedCover)} alt="" /> : <Layers />}
                </span>
                <div>
                  <h3>{creating ? t("collections.newTitle") : draft.nameTh || t("collections.editTitle")}</h3>
                  <p>{creating ? t("collections.newHint") : t("collections.editHint")}</p>
                </div>
              </div>
              <div className="sc-editor-actions">
                {!creating ? (
                  <button type="button" className="sc-danger" disabled={disabled} onClick={() => setConfirmDelete(true)}>
                    <Trash2 aria-hidden />{t("collections.delete")}
                  </button>
                ) : null}
                <MerchantSaveButton saving={busy} disabled={!canUpdate} title={deniedTitle} onClick={() => void save()} />
              </div>
            </header>

            {error ? <p className="form-field-error sc-alert" role="alert">{error}</p> : null}

            <div className="sc-section">
              <h4>{t("collections.sections.details")}</h4>
              <div className="sc-form-grid">
                <FormField label={t("collections.fields.nameTh")} error={fieldErrors.nameTh} className="flush">
                  <input value={draft.nameTh} maxLength={120} disabled={disabled} onChange={(e) => setDraft({ ...draft, nameTh: e.target.value })} />
                </FormField>
                <FormField label={t("collections.fields.nameEn")} error={fieldErrors.nameEn} className="flush">
                  <input value={draft.nameEn} maxLength={120} disabled={disabled} onChange={(e) => setDraft({ ...draft, nameEn: e.target.value })} />
                </FormField>
                <FormField label={t("collections.fields.slug")} error={fieldErrors.slug} className="flush" aside={<span>{t("collections.fields.slugHint")}</span>}>
                  <input value={draft.slug} maxLength={80} disabled={disabled} placeholder={t("collections.fields.slugHint")} onChange={(e) => setDraft({ ...draft, slug: e.target.value.toLowerCase() })} />
                </FormField>
                <ThemeTokenSelect
                  label={t("collections.fields.visibility")}
                  value={draft.published ? "1" : "0"}
                  disabled={disabled}
                  options={[{ value: "1", label: t("collections.published") }, { value: "0", label: t("collections.draft") }]}
                  onChange={(v) => setDraft({ ...draft, published: v === "1" })}
                />
                <FormField label={t("collections.fields.description")} error={fieldErrors.description} multiline className="flush wide">
                  <textarea value={draft.description} maxLength={500} rows={3} disabled={disabled} onChange={(e) => setDraft({ ...draft, description: e.target.value })} />
                </FormField>
              </div>
            </div>

            <div className="sc-section">
              <h4>{t("collections.sections.cover")}</h4>
              <MediaField label={t("collections.fields.cover")} error={fieldErrors.coverMediaId}>
                <ThemeMediaPicker
                  kind="IMAGE"
                  value={draft.coverMediaId || undefined}
                  shopId={shopId}
                  shopSlug={shopSlug}
                  disabled={disabled}
                  onChange={(mediaId) => setDraft({ ...draft, coverMediaId: mediaId ?? "" })}
                />
              </MediaField>
            </div>

            {!creating && detail ? (
              <div className="sc-section">
                <div className="sc-section-head-row">
                  <h4>{t("collections.products.title", { count: members.length })}</h4>
                </div>
                <div className="sc-product-tools">
                  <FormField label={t("collections.products.search")} icon={Search} className="flush">
                    <input value={productQuery} maxLength={80} disabled={!canUpdate} placeholder={t("collections.products.searchPlaceholder")} onChange={(e) => setProductQuery(e.target.value)} />
                  </FormField>
                  {addable.length ? (
                    <ThemeTokenSelect
                      label={t("collections.products.add")}
                      value=""
                      disabled={disabled}
                      options={addable.map((p) => ({ value: p.id, label: `${p.nameTh}${p.status === "PUBLISHED" ? "" : ` (${p.status})`}` }))}
                      onChange={(id) => void mutateProducts(() => collectionApi.addProducts(shopId!, detail.collection.id, [id]))}
                    />
                  ) : (
                    <p className="sc-empty-note">{t("collections.products.noneToAdd")}</p>
                  )}
                </div>

                {members.length === 0 ? (
                  <div className="sc-empty is-compact">
                    <span aria-hidden="true"><Package /></span>
                    <b>{t("collections.products.empty")}</b>
                    <p>{t("collections.products.emptyHint")}</p>
                  </div>
                ) : (
                  <ul className="sc-product-list">
                    {members.map((m, index) => (
                      <li key={m.productId} className="sc-product-item">
                        <div className="sc-product-copy">
                          <b>{m.nameTh}</b>
                          <small>{m.status === "PUBLISHED" ? t("collections.published") : m.status}</small>
                        </div>
                        <span className="sc-move">
                          <button type="button" aria-label={t("collections.moveUp")} disabled={disabled || index === 0}
                            onClick={() => void mutateProducts(() => collectionApi.setProducts(shopId!, detail.collection.id, move(members, index, index - 1).map((x) => x.productId)))}><ChevronUp /></button>
                          <button type="button" aria-label={t("collections.moveDown")} disabled={disabled || index === members.length - 1}
                            onClick={() => void mutateProducts(() => collectionApi.setProducts(shopId!, detail.collection.id, move(members, index, index + 1).map((x) => x.productId)))}><ChevronDown /></button>
                          <button type="button" className="is-danger" aria-label={t("collections.products.remove")} disabled={disabled}
                            onClick={() => void mutateProducts(() => collectionApi.removeProduct(shopId!, detail.collection.id, m.productId))}><Trash2 /></button>
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </div>
            ) : null}
          </section>
        ) : (
          <section className="sc-panel sc-placeholder">
            <span aria-hidden="true"><Layers /></span>
            <b>{t("collections.selectTitle")}</b>
            <p>{t("collections.selectHint")}</p>
            <button type="button" className="sc-add" onClick={startCreate} disabled={!canUpdate} title={deniedTitle}>
              <Plus aria-hidden />{t("collections.add")}
            </button>
          </section>
        )}
      </div>

      {confirmDelete ? (
        <ConfirmDialog
          danger
          title={t("collections.confirmDelete.title")}
          message={t("collections.confirmDelete.message", { name: draft.nameTh })}
          confirmLabel={t("collections.delete")}
          cancelLabel={t("collections.cancel")}
          onConfirm={() => void remove()}
          onCancel={() => setConfirmDelete(false)}
        />
      ) : null}
    </div>
  );
}
