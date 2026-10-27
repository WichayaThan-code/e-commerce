import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { ChevronDown, ChevronUp, Plus, Trash2 } from "lucide-react";
import FormField, { FieldError } from "../form/FormField";
import ThemeMediaPicker from "./ThemeMediaPicker";
import MerchantSaveButton from "./MerchantSaveButton";
import { MediaField, ThemeTokenSelect } from "./ThemeEditorParts";
import { FEATURES } from "../../config/features";
import { ApiError, merchantApi } from "../../services/api";
import {
  configuratorApi,
  type ConfiguratorCharset,
  type ConfiguratorOption,
  type ConfiguratorStep,
  type ConfiguratorStepType,
} from "../../services/collectionsApi";
import "../../styles/theme-editor-controls.css";
import "../../styles/shop-collections-manager.css";

const MAX_STEPS = 10;
const MAX_OPTIONS = 30;
const newId = (prefix: string) => `${prefix}-${Math.random().toString(36).slice(2, 8)}`;
const move = <T,>(items: T[], from: number, to: number) => { const next = [...items]; const [it] = next.splice(from, 1); next.splice(to, 0, it); return next; };

/**
 * ตัวออกแบบสินค้าเอง (configurator) — merchant section in ProductFormPage.
 * Renders nothing unless FEATURES.productConfigurator is on AND the active shop uses the CUSTOM template.
 * Saves independently of the product form (own PUT, optimistic version).
 */
export default function ProductConfiguratorEditor({ shopId, productId }: { shopId: number | null; productId?: string }) {
  const { t } = useTranslation("merchant");
  const [custom, setCustom] = useState(false);
  const [shopSlug, setShopSlug] = useState("");
  const [enabled, setEnabled] = useState(false);
  const [steps, setSteps] = useState<ConfiguratorStep[]>([]);
  const [version, setVersion] = useState(0);
  const [loaded, setLoaded] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [notice, setNotice] = useState("");

  useEffect(() => {
    if (!FEATURES.productConfigurator || shopId == null) return;
    let alive = true;
    Promise.all([merchantApi.getTheme(shopId), merchantApi.getSettings(shopId)])
      .then(([theme, settings]) => { if (alive) { setCustom(theme.templateKey === "CUSTOM"); setShopSlug(settings.slug || ""); } })
      .catch(() => { if (alive) setCustom(false); });
    return () => { alive = false; };
  }, [shopId]);

  useEffect(() => {
    if (!custom || shopId == null || !productId) return;
    let alive = true;
    configuratorApi.get(shopId, productId).then((row) => {
      if (!alive) return;
      setEnabled(row.enabled); setSteps(row.schema?.steps ?? []); setVersion(row.version); setLoaded(true);
    }).catch((err) => { if (alive) { setError(err instanceof ApiError ? err.message : t("configurator.errors.load")); setLoaded(true); } });
    return () => { alive = false; };
  }, [custom, shopId, productId, t]);

  if (!FEATURES.productConfigurator || !custom) return null;

  const setStep = (index: number, change: Partial<ConfiguratorStep>) => setSteps(steps.map((s, i) => (i === index ? { ...s, ...change } : s)));
  const setOption = (si: number, oi: number, change: Partial<ConfiguratorOption>) =>
    setStep(si, { options: (steps[si].options ?? []).map((o, i) => (i === oi ? { ...o, ...change } : o)) });
  const addStep = (type: ConfiguratorStepType) => setSteps([...steps, type === "TEXT"
    ? { id: newId("text"), type, label: { th: "" }, required: false, maxLength: 20, charset: "ANY", priceDelta: "0" }
    : { id: newId("step"), type, label: { th: "" }, required: true, ...(type === "MULTI" ? { min: 0, max: 3 } : {}), options: [{ id: newId("opt"), label: { th: "" }, priceDelta: "0" }] }]);
  const err = (path: string) => fieldErrors[path] ? t(`configurator.errors.${fieldErrors[path]}`, { defaultValue: t("configurator.errors.invalid") }) : undefined;

  const save = async () => {
    if (shopId == null || !productId) return;
    setSaving(true); setError(""); setFieldErrors({});
    try {
      const saved = await configuratorApi.save(shopId, productId, { enabled, schema: { steps }, version });
      setEnabled(saved.enabled); setSteps(saved.schema.steps); setVersion(saved.version);
      setNotice(t("configurator.saved")); window.setTimeout(() => setNotice(""), 1900);
    } catch (e) {
      if (e instanceof ApiError) {
        const map: Record<string, string> = {};
        e.fieldErrors.forEach((fe) => { map[fe.field] = fe.code; });
        setFieldErrors(map);
        setError(e.code === "CONFIGURATOR_VERSION_CONFLICT" ? t("configurator.errors.conflict") : e.message);
      } else setError(t("configurator.errors.save"));
    } finally { setSaving(false); }
  };

  return (
    <section className="pm-configurator" aria-labelledby="pm-configurator-title">
      <header className="pm-configurator-head">
        <div><h3 id="pm-configurator-title">{t("configurator.title")}</h3><p className="theme-hint">{t("configurator.hint")}</p></div>
        {productId ? <MerchantSaveButton saving={saving} disabled={!loaded} onClick={() => void save()} /> : null}
      </header>
      {!productId ? <p className="theme-hint">{t("configurator.saveProductFirst")}</p> : !loaded ? <p className="theme-hint">{t("configurator.loading")}</p> : (
        <div className="theme-content">
          {error ? <p className="form-field-error" role="alert">{error}</p> : null}
          {notice ? <p className="theme-hint" role="status">{notice}</p> : null}
          <ThemeTokenSelect label={t("configurator.enabled")} value={enabled ? "1" : "0"}
            options={[{ value: "1", label: t("configurator.on") }, { value: "0", label: t("configurator.off") }]}
            onChange={(v) => setEnabled(v === "1")} />
          <FieldError text={err("schema.steps")} />
          <ul className="theme-content-list">
            {steps.map((step, si) => {
              const p = `schema.steps[${si}]`;
              return (
                <li key={step.id} className="theme-content-row">
                  <div className="theme-content-row-head">
                    <b>{t(`configurator.types.${step.type}`)} {si + 1}</b>
                    <span className="theme-section-move theme-content-tools">
                      <button type="button" aria-label={t("configurator.moveUp")} disabled={si === 0} onClick={() => setSteps(move(steps, si, si - 1))}><ChevronUp /></button>
                      <button type="button" aria-label={t("configurator.moveDown")} disabled={si === steps.length - 1} onClick={() => setSteps(move(steps, si, si + 1))}><ChevronDown /></button>
                      <button type="button" aria-label={t("configurator.remove")} onClick={() => setSteps(steps.filter((_, i) => i !== si))}><Trash2 /></button>
                    </span>
                  </div>
                  <FormField label={t("configurator.labelTh")} error={err(`${p}.label`) ?? err(`${p}.id`)}>
                    <input value={step.label.th} maxLength={80} onChange={(e) => setStep(si, { label: { ...step.label, th: e.target.value } })} />
                  </FormField>
                  <FormField label={t("configurator.labelEn")}>
                    <input value={step.label.en ?? ""} maxLength={80} onChange={(e) => setStep(si, { label: { ...step.label, en: e.target.value } })} />
                  </FormField>
                  <ThemeTokenSelect label={t("configurator.required")} value={step.required ? "1" : "0"}
                    options={[{ value: "1", label: t("configurator.yes") }, { value: "0", label: t("configurator.no") }]}
                    onChange={(v) => setStep(si, { required: v === "1" })} />
                  {step.type === "TEXT" ? (
                    <>
                      <FormField label={t("configurator.maxLength")} error={err(`${p}.maxLength`)}>
                        <input inputMode="numeric" value={String(step.maxLength ?? 20)} onChange={(e) => setStep(si, { maxLength: Number(e.target.value.replace(/\D/g, "")) || 0 })} />
                      </FormField>
                      <ThemeTokenSelect label={t("configurator.charset")} value={step.charset ?? "ANY"} error={err(`${p}.charset`)}
                        options={(["ANY", "LATIN", "ALNUM", "THAI_LATIN"] as ConfiguratorCharset[]).map((c) => ({ value: c, label: t(`configurator.charsets.${c}`) }))}
                        onChange={(v) => setStep(si, { charset: v as ConfiguratorCharset })} />
                      <FormField label={t("configurator.priceDelta")} error={err(`${p}.priceDelta`)}>
                        <input inputMode="decimal" value={step.priceDelta ?? "0"} onChange={(e) => setStep(si, { priceDelta: e.target.value })} />
                      </FormField>
                    </>
                  ) : (
                    <>
                      {step.type === "MULTI" ? (
                        <div className="pm-configurator-pair">
                          <FormField label={t("configurator.min")} error={err(`${p}.min`)}>
                            <input inputMode="numeric" value={String(step.min ?? 0)} onChange={(e) => setStep(si, { min: Number(e.target.value.replace(/\D/g, "")) || 0 })} />
                          </FormField>
                          <FormField label={t("configurator.max")} error={err(`${p}.max`)}>
                            <input inputMode="numeric" value={String(step.max ?? 1)} onChange={(e) => setStep(si, { max: Number(e.target.value.replace(/\D/g, "")) || 0 })} />
                          </FormField>
                        </div>
                      ) : null}
                      <FieldError text={err(`${p}.options`)} />
                      <ul className="pm-configurator-options">
                        {(step.options ?? []).map((option, oi) => {
                          const op = `${p}.options[${oi}]`;
                          const opts = step.options ?? [];
                          return (
                            <li key={option.id} className="theme-content-row">
                              <div className="theme-content-row-head">
                                <b>{t("configurator.option")} {oi + 1}</b>
                                <span className="theme-section-move theme-content-tools">
                                  <button type="button" aria-label={t("configurator.moveUp")} disabled={oi === 0} onClick={() => setStep(si, { options: move(opts, oi, oi - 1) })}><ChevronUp /></button>
                                  <button type="button" aria-label={t("configurator.moveDown")} disabled={oi === opts.length - 1} onClick={() => setStep(si, { options: move(opts, oi, oi + 1) })}><ChevronDown /></button>
                                  <button type="button" aria-label={t("configurator.remove")} disabled={opts.length <= 1} onClick={() => setStep(si, { options: opts.filter((_, i) => i !== oi) })}><Trash2 /></button>
                                </span>
                              </div>
                              <FormField label={t("configurator.labelTh")} error={err(`${op}.label`) ?? err(`${op}.id`)}>
                                <input value={option.label.th} maxLength={80} onChange={(e) => setOption(si, oi, { label: { ...option.label, th: e.target.value } })} />
                              </FormField>
                              <FormField label={t("configurator.labelEn")}>
                                <input value={option.label.en ?? ""} maxLength={80} onChange={(e) => setOption(si, oi, { label: { ...option.label, en: e.target.value } })} />
                              </FormField>
                              <FormField label={t("configurator.priceDelta")} error={err(`${op}.priceDelta`)}>
                                <input inputMode="decimal" value={option.priceDelta ?? "0"} onChange={(e) => setOption(si, oi, { priceDelta: e.target.value })} />
                              </FormField>
                              <FormField label={t("configurator.stock")} error={err(`${op}.stock`)}>
                                <input inputMode="numeric" value={option.stock == null ? "" : String(option.stock)} placeholder={t("configurator.stockUnlimited")}
                                  onChange={(e) => { const raw = e.target.value.replace(/\D/g, ""); setOption(si, oi, { stock: raw === "" ? null : Number(raw) }); }} />
                              </FormField>
                              <ThemeTokenSelect label={t("configurator.available")} value={option.available === false ? "0" : "1"}
                                options={[{ value: "1", label: t("configurator.yes") }, { value: "0", label: t("configurator.no") }]}
                                onChange={(v) => setOption(si, oi, { available: v === "1" })} />
                              <MediaField label={t("configurator.image")} error={err(`${op}.mediaId`)}>
                                <ThemeMediaPicker kind="IMAGE" value={option.mediaId || undefined} shopId={shopId} shopSlug={shopSlug}
                                  onChange={(mediaId, item) => setOption(si, oi, { mediaId: mediaId ?? null, mediaUrl: mediaId ? item?.url ?? option.mediaUrl : null })} />
                              </MediaField>
                            </li>
                          );
                        })}
                      </ul>
                      {(step.options ?? []).length < MAX_OPTIONS ? (
                        <button type="button" className="theme-banner-add" onClick={() => setStep(si, { options: [...(step.options ?? []), { id: newId("opt"), label: { th: "" }, priceDelta: "0" }] })}>
                          <Plus />{t("configurator.addOption")}
                        </button>
                      ) : null}
                    </>
                  )}
                </li>
              );
            })}
          </ul>
          {steps.length < MAX_STEPS ? (
            <ThemeTokenSelect label={t("configurator.addStep")} value=""
              options={(["SINGLE", "MULTI", "TEXT"] as ConfiguratorStepType[]).map((type) => ({ value: type, label: t(`configurator.types.${type}`) }))}
              onChange={(v) => addStep(v as ConfiguratorStepType)} />
          ) : null}
        </div>
      )}
    </section>
  );
}
