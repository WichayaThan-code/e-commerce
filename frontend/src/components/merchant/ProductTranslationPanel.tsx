import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import type { TFunction } from "i18next";
import ToggleSwitch from "../form/ToggleSwitch";
import { productApi, type Product, type TranslationMeta } from "../../services/api";
import ProductDescriptionEditor from "./ProductDescriptionEditor";
import ProductDescriptionHtml, { productDescriptionHasContent, productDescriptionPlainText } from "./ProductDescriptionHtml";

type Locale = "en" | "zh";
type FieldKey = "name" | "description";

type DraftPatch = {
  nameEn?: string;
  nameZh?: string;
  descriptionEn?: string;
  descriptionZh?: string;
};

type Props = {
  shopId: number;
  productId?: string;
  product?: Product | null;
  nameTh: string;
  descriptionTh: string;
  nameEn: string;
  nameZh: string;
  descriptionEn: string;
  descriptionZh: string;
  onChange: (patch: DraftPatch) => void;
  onUseAuto?: (field: FieldKey, locale: Locale) => void;
  onRetry?: (field: FieldKey, locale: Locale) => void;
};

const LOCALES: Locale[] = ["en", "zh"];

function isManual(meta?: TranslationMeta | null) {
  return meta?.origin === "HUMAN" || meta?.origin === "MANUAL";
}

function translationBadge(t: TFunction<"merchant">, meta?: TranslationMeta | null, editing?: boolean) {
  if (editing) return t("catalog.form.translationManual");
  if (!meta) return t("catalog.form.translationPreview");
  if (meta.status === "PENDING") return t("catalog.form.translationPending");
  if (meta.status === "FAILED") return t("catalog.form.translationFailed");
  return isManual(meta) ? t("catalog.form.translationManual") : t("catalog.form.translationAuto");
}

function draftKey(locale: Locale, field: FieldKey): keyof DraftPatch {
  if (field === "name") return locale === "en" ? "nameEn" : "nameZh";
  return locale === "en" ? "descriptionEn" : "descriptionZh";
}

function metaKey(locale: Locale, field: FieldKey): keyof Product {
  if (field === "name") return locale === "en" ? "nameEnMeta" : "nameZhMeta";
  return locale === "en" ? "descriptionEnMeta" : "descriptionZhMeta";
}

function valueOf(props: Props, locale: Locale, field: FieldKey) {
  if (field === "name") return locale === "en" ? props.nameEn : props.nameZh;
  return locale === "en" ? props.descriptionEn : props.descriptionZh;
}

export default function ProductTranslationPanel({
  shopId,
  productId,
  product,
  nameTh,
  descriptionTh,
  nameEn,
  nameZh,
  descriptionEn,
  descriptionZh,
  onChange,
  onUseAuto,
  onRetry,
}: Props) {
  const { t } = useTranslation("merchant");
  const [editing, setEditing] = useState<Record<Locale, Record<FieldKey, boolean>>>({
    en: { name: false, description: false },
    zh: { name: false, description: false },
  });
  const [preview, setPreview] = useState<Record<Locale, Record<FieldKey, string>>>({
    en: { name: "", description: "" },
    zh: { name: "", description: "" },
  });
  const [status, setStatus] = useState<"idle" | "loading" | "error">("idle");
  const seeded = useRef(false);

  // Seed edit mode from saved product meta (once per product load). New products start in display/preview mode.
  useEffect(() => {
    seeded.current = false;
  }, [productId]);

  useEffect(() => {
    if (seeded.current) return;
    const next = {
      en: { name: false, description: false },
      zh: { name: false, description: false },
    };
    if (product) {
      for (const locale of LOCALES) {
        for (const field of ["name", "description"] as FieldKey[]) {
          const meta = product[metaKey(locale, field)] as TranslationMeta | null | undefined;
          next[locale][field] = isManual(meta);
        }
      }
    }
    setEditing(next);
    seeded.current = true;
  }, [product, productId]);

  const sourceKey = useMemo(
    () => JSON.stringify({
      nameTh: nameTh.trim(),
      descriptionTh: productDescriptionPlainText(descriptionTh).trim(),
    }),
    [nameTh, descriptionTh],
  );

  useEffect(() => {
    const name = nameTh.trim();
    const description = productDescriptionPlainText(descriptionTh).trim();
    if (!name && !description) {
      setPreview({ en: { name: "", description: "" }, zh: { name: "", description: "" } });
      setStatus("idle");
      return;
    }

    let cancelled = false;
    const timer = window.setTimeout(() => {
      setStatus("loading");
      productApi
        .previewTranslations(shopId, {
          fields: { name, description },
          locales: ["en", "zh"],
        })
        .then((response) => {
          if (cancelled) return;
          const next = {
            en: {
              name: response.translations?.en?.name ?? "",
              description: response.translations?.en?.description ?? "",
            },
            zh: {
              name: response.translations?.zh?.name ?? "",
              description: response.translations?.zh?.description ?? "",
            },
          };
          setPreview(next);
          // Successful HTTP but empty output usually means provider=fake/none or upstream failure.
          const expected = Boolean(name) || Boolean(description);
          const got = Boolean(next.en.name || next.en.description || next.zh.name || next.zh.description);
          setStatus(expected && !got ? "error" : "idle");
        })
        .catch(() => {
          if (cancelled) return;
          setStatus("error");
        });
    }, 450);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [shopId, sourceKey, nameTh, descriptionTh]);

  const setEdit = (locale: Locale, field: FieldKey, enabled: boolean) => {
    setEditing((current) => ({
      ...current,
      [locale]: { ...current[locale], [field]: enabled },
    }));
    const key = draftKey(locale, field);
    if (enabled) {
      const existing = valueOf(
        { nameEn, nameZh, descriptionEn, descriptionZh } as Props,
        locale,
        field,
      ).trim();
      const suggested = preview[locale][field] || existing;
      onChange({ [key]: suggested });
    } else {
      // Leave blank so save uses machine translation; for saved manuals, parent can call use-auto.
      onChange({ [key]: "" });
      if (productId && isManual(product?.[metaKey(locale, field)] as TranslationMeta | null | undefined)) {
        onUseAuto?.(field, locale);
      }
    }
  };

  return (
    <section className="pm-translation-panel" aria-label={t("catalog.form.translationSection")}>
      <header className="pm-translation-panel-head">
        <h3>{t("catalog.form.translationSection")}</h3>
        <p className="pm-lang-hint">{t("catalog.form.translationLiveHint")}</p>
      </header>
      {status === "error" ? <p className="pm-lang-hint pm-translation-error" role="status">{t("catalog.form.translationPreviewFailed")}</p> : null}
      {LOCALES.map((locale) => (
        <div className="pm-lang-block" key={locale}>
          <h4>
            {t(`catalog.form.lang${locale === "en" ? "En" : "Zh"}`)}
            <small>{t("catalog.form.langOptional")}</small>
          </h4>
          {(["name", "description"] as FieldKey[]).map((field) => {
            const edit = editing[locale][field];
            const meta = product?.[metaKey(locale, field)] as TranslationMeta | null | undefined;
            const draftValue = valueOf(
              { nameEn, nameZh, descriptionEn, descriptionZh } as Props,
              locale,
              field,
            );
            const display = edit
              ? draftValue
              : (preview[locale][field] || (isManual(meta) ? "" : draftValue) || "");
            const sourceEmpty = field === "name"
              ? !nameTh.trim()
              : !productDescriptionHasContent(descriptionTh);
            const pending = !edit && status === "loading" && !sourceEmpty;
            return (
              <div className="pm-translation-field" key={`${locale}-${field}`}>
                <div className="pm-translation-field-head">
                  <span className="pm-lang-label">
                    {t(field === "name" ? "catalog.form.name" : "catalog.form.description")}
                    <span className="pm-translation-badge">{translationBadge(t, meta, edit)}</span>
                  </span>
                  <ToggleSwitch
                    checked={edit}
                    disabled={sourceEmpty && !edit}
                    label={t("catalog.form.editTranslation")}
                    onText={t("catalog.form.editTranslationOn")}
                    offText={t("catalog.form.editTranslationOff")}
                    onChange={(checked) => setEdit(locale, field, checked)}
                  />
                </div>
                {edit ? (
                  field === "name" ? (
                    <input
                      value={draftValue}
                      onChange={(event) => onChange({ [draftKey(locale, field)]: event.target.value })}
                      placeholder={t("catalog.form.namePh")}
                    />
                  ) : (
                    <ProductDescriptionEditor
                      shopId={shopId}
                      compact
                      value={draftValue}
                      onChange={(html) => onChange({ [draftKey(locale, field)]: html })}
                      placeholder={t("catalog.form.descriptionPh")}
                    />
                  )
                ) : field === "description" && display ? (
                  <div
                    className={`pm-translation-display pm-translation-display-html${pending ? " is-loading" : ""}`}
                    role="status"
                  >
                    {pending ? t("catalog.form.translationPending") : <ProductDescriptionHtml value={display} />}
                  </div>
                ) : (
                  <div
                    className={`pm-translation-display${pending ? " is-loading" : ""}${!display && !pending ? " is-empty" : ""}`}
                    role="status"
                  >
                    {pending
                      ? t("catalog.form.translationPending")
                      : display || (sourceEmpty ? t("catalog.form.translationNeedThai") : t("catalog.form.translationEmpty"))}
                  </div>
                )}
                {!edit && meta?.status === "FAILED" && productId ? (
                  <button type="button" className="pm-use-auto" onClick={() => onRetry?.(field, locale)}>
                    {t("catalog.form.translationRetry")}
                  </button>
                ) : null}
              </div>
            );
          })}
        </div>
      ))}
    </section>
  );
}
