import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { AlertTriangle, Pencil, Plus, Truck } from "lucide-react";
import ConfirmDialog from "../form/ConfirmDialog";
import FormField from "../form/FormField";
import FormGrid from "../form/FormGrid";
import FormSelect from "../form/FormSelect";
import { Skeleton } from "../feedback/Skeleton";
import { formatBaht } from "../cart/cartFormat";
import {
  ApiError,
  carrierApi,
  merchantShopSettingsApi,
  type Carrier,
  type ShippingMethod,
  type ShippingMethodInput,
} from "../../services/api";
import { usePublicTranslation } from "../../hooks/usePublicTranslation";

type Props = {
  shopId: number | null | undefined;
  canUpdate: boolean;
  deniedTitle?: string;
  notify: (text: string) => void;
};

type FormState = {
  name: string;
  carrierCode: string;
  feeType: "FLAT" | "WEIGHT" | "FREE";
  feeAmount: string;
  extraFeePerKg: string;
  freeShippingMinAmount: string;
  estimatedDaysMin: string;
  estimatedDaysMax: string;
};

const emptyForm = (): FormState => ({
  name: "",
  carrierCode: "",
  feeType: "FLAT",
  feeAmount: "40",
  extraFeePerKg: "",
  freeShippingMinAmount: "",
  estimatedDaysMin: "1",
  estimatedDaysMax: "3",
});

function fromMethod(method: ShippingMethod): FormState {
  return {
    name: method.name ?? "",
    carrierCode: method.carrierCode ?? "",
    feeType: method.feeType || "FLAT",
    feeAmount: method.feeAmount == null ? "" : String(method.feeAmount),
    extraFeePerKg: method.extraFeePerKg == null ? "" : String(method.extraFeePerKg),
    freeShippingMinAmount: method.freeShippingMinAmount == null ? "" : String(method.freeShippingMinAmount),
    estimatedDaysMin: method.estimatedDaysMin == null ? "" : String(method.estimatedDaysMin),
    estimatedDaysMax: method.estimatedDaysMax == null ? "" : String(method.estimatedDaysMax),
  };
}

export default function ShippingMethodsSettings({ shopId, canUpdate, deniedTitle, notify }: Props) {
  const { t } = useTranslation("merchant");
  const { localeTag } = usePublicTranslation();
  const [methods, setMethods] = useState<ShippingMethod[]>([]);
  const [carriers, setCarriers] = useState<Carrier[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [reload, setReload] = useState(0);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [form, setForm] = useState<FormState>(emptyForm);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [pendingDisable, setPendingDisable] = useState<ShippingMethod | null>(null);
  const [toggling, setToggling] = useState(false);

  const carrierName = useCallback((code?: string | null) => {
    if (!code) return "—";
    const hit = carriers.find((c) => c.code === code);
    if (!hit) return code;
    return localeTag.startsWith("en") ? hit.nameEn : hit.nameTh;
  }, [carriers, localeTag]);

  const load = useCallback(() => {
    if (shopId == null) return;
    let alive = true;
    setLoading(true);
    setFailed(false);
    Promise.all([
      merchantShopSettingsApi.shippingMethods(shopId),
      carrierApi.list().catch(() => [] as Carrier[]),
    ])
      .then(([list, carrierList]) => {
        if (!alive) return;
        setMethods(list);
        setCarriers(carrierList.filter((c) => c.active).sort((a, b) => a.sortOrder - b.sortOrder));
      })
      .catch(() => { if (alive) setFailed(true); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [shopId]);

  useEffect(() => {
    if (shopId == null) return;
    return load();
  }, [shopId, reload, load]);

  const openCreate = () => {
    setEditingId(null);
    setForm(emptyForm());
    setFieldErrors({});
    setFormOpen(true);
  };

  const openEdit = (method: ShippingMethod) => {
    setEditingId(method.id);
    setForm(fromMethod(method));
    setFieldErrors({});
    setFormOpen(true);
  };

  const validateLocal = (): Record<string, string> => {
    const errors: Record<string, string> = {};
    if (!form.name.trim()) errors.name = t("shopSettings.shipping.nameRequired");
    if (form.feeType !== "FREE") {
      const fee = Number(form.feeAmount);
      if (!Number.isFinite(fee) || fee < 0) errors.feeAmount = t("shopSettings.shipping.feeInvalid");
    }
    if (form.feeType === "WEIGHT") {
      const extra = Number(form.extraFeePerKg);
      if (form.extraFeePerKg !== "" && (!Number.isFinite(extra) || extra < 0)) {
        errors.extraFeePerKg = t("shopSettings.shipping.feeInvalid");
      }
    }
    const min = form.estimatedDaysMin === "" ? null : Number(form.estimatedDaysMin);
    const max = form.estimatedDaysMax === "" ? null : Number(form.estimatedDaysMax);
    if (min != null && max != null && Number.isFinite(min) && Number.isFinite(max) && min > max) {
      errors.estimatedDaysMin = t("shopSettings.shipping.daysInvalid");
    }
    return errors;
  };

  const toBody = (): ShippingMethodInput => ({
    name: form.name.trim(),
    carrierCode: form.carrierCode || null,
    feeType: form.feeType,
    feeAmount: form.feeType === "FREE" ? null : Number(form.feeAmount),
    extraFeePerKg: form.feeType === "WEIGHT" && form.extraFeePerKg !== "" ? Number(form.extraFeePerKg) : null,
    freeShippingMinAmount: form.freeShippingMinAmount === "" ? null : Number(form.freeShippingMinAmount),
    estimatedDaysMin: form.estimatedDaysMin === "" ? null : Number(form.estimatedDaysMin),
    estimatedDaysMax: form.estimatedDaysMax === "" ? null : Number(form.estimatedDaysMax),
    active: true,
  });

  const onSave = async () => {
    if (!canUpdate || shopId == null || saving) return;
    const local = validateLocal();
    setFieldErrors(local);
    if (Object.keys(local).length) return;
    setSaving(true);
    try {
      const body = toBody();
      if (editingId) await merchantShopSettingsApi.updateShippingMethod(shopId, editingId, body);
      else await merchantShopSettingsApi.createShippingMethod(shopId, body);
      notify(t("shopSettings.saved"));
      setFormOpen(false);
      setReload((n) => n + 1);
    } catch (error) {
      if (error instanceof ApiError && (error.code === "VALIDATION_ERROR" || error.code === "VALIDATION_FAILED")) {
        const mapped: Record<string, string> = {};
        error.fieldErrors.forEach((f) => { mapped[f.field] = f.message; });
        setFieldErrors(mapped);
      } else {
        notify(error instanceof ApiError ? error.message : t("shopSettings.loadError"));
      }
    } finally {
      setSaving(false);
    }
  };

  const onToggle = async (method: ShippingMethod, next: boolean) => {
    if (!canUpdate || shopId == null || toggling) return;
    if (!next) { setPendingDisable(method); return; }
    setToggling(true);
    try {
      await merchantShopSettingsApi.setShippingMethodActive(shopId, method.id, true);
      notify(t("shopSettings.shipping.enabled"));
      setReload((n) => n + 1);
    } catch (error) {
      notify(error instanceof ApiError ? error.message : t("shopSettings.loadError"));
    } finally {
      setToggling(false);
    }
  };

  const confirmDisable = async () => {
    if (!pendingDisable || shopId == null) return;
    setToggling(true);
    try {
      await merchantShopSettingsApi.setShippingMethodActive(shopId, pendingDisable.id, false);
      notify(t("shopSettings.shipping.disabled"));
      setPendingDisable(null);
      setReload((n) => n + 1);
    } catch (error) {
      notify(error instanceof ApiError ? error.message : t("shopSettings.loadError"));
    } finally {
      setToggling(false);
    }
  };

  const feeLabel = useMemo(() => ({
    FLAT: t("shopSettings.shipping.feeFlat"),
    WEIGHT: t("shopSettings.shipping.feeWeight"),
    FREE: t("shopSettings.shipping.feeFree"),
  }), [t]);

  if (shopId == null) {
    return <section className="settings-card"><Skeleton height={120} width="100%" /></section>;
  }

  return (
    <section className="settings-card shop-settings-card">
      <header>
        <div>
          <h2><Truck />{t("shopSettings.shipping.title")}</h2>
          <p>{t("shopSettings.shipping.lead")}</p>
        </div>
        <button type="button" className="shop-settings-primary" disabled={!canUpdate || saving} title={deniedTitle} onClick={openCreate}>
          <Plus size={16} />{t("shopSettings.shipping.add")}
        </button>
      </header>

      {loading ? (
        <div className="shop-settings-skeleton" aria-busy="true">
          <Skeleton height={40} /><Skeleton height={40} /><Skeleton height={40} />
        </div>
      ) : failed ? (
        <div className="shop-settings-empty" role="alert">
          <AlertTriangle /><p>{t("shopSettings.loadError")}</p>
          <button type="button" onClick={() => setReload((n) => n + 1)}>{t("shopSettings.retry")}</button>
        </div>
      ) : methods.length === 0 && !formOpen ? (
        <div className="shop-settings-empty" role="status">
          <Truck /><p>{t("shopSettings.shipping.empty")}</p>
          <p className="shop-settings-warn">{t("shopSettings.shipping.emptyWarn")}</p>
          <button type="button" disabled={!canUpdate} title={deniedTitle} onClick={openCreate}>{t("shopSettings.shipping.add")}</button>
        </div>
      ) : (
        <div className="shop-settings-table-wrap">
          <table className="shop-settings-table">
            <thead>
              <tr>
                <th>{t("shopSettings.shipping.colName")}</th>
                <th>{t("shopSettings.shipping.colCarrier")}</th>
                <th>{t("shopSettings.shipping.colType")}</th>
                <th>{t("shopSettings.shipping.colFee")}</th>
                <th>{t("shopSettings.shipping.colFreeMin")}</th>
                <th>{t("shopSettings.shipping.colDays")}</th>
                <th>{t("shopSettings.shipping.colStatus")}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {methods.map((method) => (
                <tr key={method.id}>
                  <td>{method.name}</td>
                  <td>{carrierName(method.carrierCode)}</td>
                  <td>{feeLabel[method.feeType] ?? method.feeType}</td>
                  <td className="num">
                    {method.feeType === "FREE" ? feeLabel.FREE : formatBaht(method.feeAmount, localeTag)}
                    {method.feeType === "WEIGHT" && method.extraFeePerKg != null
                      ? ` + ${formatBaht(method.extraFeePerKg, localeTag)}/kg`
                      : null}
                  </td>
                  <td className="num">{method.freeShippingMinAmount != null ? formatBaht(method.freeShippingMinAmount, localeTag) : "—"}</td>
                  <td className="num">
                    {method.estimatedDaysMin != null || method.estimatedDaysMax != null
                      ? `${method.estimatedDaysMin ?? "?"}–${method.estimatedDaysMax ?? "?"}`
                      : "—"}
                  </td>
                  <td>
                    <label className="toggle-row shop-settings-toggle">
                      <button
                        type="button"
                        role="switch"
                        aria-checked={method.active}
                        disabled={!canUpdate || toggling}
                        title={deniedTitle}
                        className={method.active ? "on" : ""}
                        onClick={() => void onToggle(method, !method.active)}
                      ><i /></button>
                      <span>{method.active ? t("shopSettings.active") : t("shopSettings.inactive")}</span>
                    </label>
                  </td>
                  <td>
                    <button type="button" className="shop-settings-icon" disabled={!canUpdate} title={deniedTitle || t("shopSettings.edit")} onClick={() => openEdit(method)}>
                      <Pencil size={14} />
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {formOpen ? (
        <div className="shop-settings-form">
          <h3>{editingId ? t("shopSettings.shipping.editTitle") : t("shopSettings.shipping.add")}</h3>
          <FormGrid>
            <FormField label={t("shopSettings.shipping.colName")} error={fieldErrors.name}>
              <input value={form.name} disabled={!canUpdate || saving} onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))} />
            </FormField>
            <FormSelect label={t("shopSettings.shipping.colCarrier")} value={form.carrierCode} disabled={!canUpdate || saving} onChange={(e) => setForm((f) => ({ ...f, carrierCode: e.target.value }))}>
              <option value="">{t("shopSettings.shipping.carrierNone")}</option>
              {carriers.map((c) => (
                <option key={c.code} value={c.code}>{localeTag.startsWith("en") ? c.nameEn : c.nameTh}</option>
              ))}
            </FormSelect>
            <FormSelect label={t("shopSettings.shipping.colType")} value={form.feeType} disabled={!canUpdate || saving} onChange={(e) => setForm((f) => ({ ...f, feeType: e.target.value as FormState["feeType"] }))} error={fieldErrors.feeType}>
              <option value="FLAT">{feeLabel.FLAT}</option>
              <option value="WEIGHT">{feeLabel.WEIGHT}</option>
              <option value="FREE">{feeLabel.FREE}</option>
            </FormSelect>
            {form.feeType !== "FREE" ? (
              <FormField label={t("shopSettings.shipping.colFee")} error={fieldErrors.feeAmount}>
                <input className="num" value={form.feeAmount} disabled={!canUpdate || saving} onChange={(e) => setForm((f) => ({ ...f, feeAmount: e.target.value }))} inputMode="decimal" />
              </FormField>
            ) : null}
            {form.feeType === "WEIGHT" ? (
              <FormField label={t("shopSettings.shipping.extraPerKg")} error={fieldErrors.extraFeePerKg}>
                <input className="num" value={form.extraFeePerKg} disabled={!canUpdate || saving} onChange={(e) => setForm((f) => ({ ...f, extraFeePerKg: e.target.value }))} inputMode="decimal" />
              </FormField>
            ) : null}
            <FormField label={t("shopSettings.shipping.colFreeMin")} error={fieldErrors.freeShippingMinAmount}>
              <input className="num" value={form.freeShippingMinAmount} disabled={!canUpdate || saving} onChange={(e) => setForm((f) => ({ ...f, freeShippingMinAmount: e.target.value }))} inputMode="decimal" />
            </FormField>
            <FormField label={t("shopSettings.shipping.daysMin")} error={fieldErrors.estimatedDaysMin}>
              <input className="num" value={form.estimatedDaysMin} disabled={!canUpdate || saving} onChange={(e) => setForm((f) => ({ ...f, estimatedDaysMin: e.target.value }))} inputMode="numeric" />
            </FormField>
            <FormField label={t("shopSettings.shipping.daysMax")} error={fieldErrors.estimatedDaysMax}>
              <input className="num" value={form.estimatedDaysMax} disabled={!canUpdate || saving} onChange={(e) => setForm((f) => ({ ...f, estimatedDaysMax: e.target.value }))} inputMode="numeric" />
            </FormField>
          </FormGrid>
          <div className="shop-settings-form-actions">
            <button type="button" className="secondary" disabled={saving} onClick={() => setFormOpen(false)}>{t("shopSettings.cancel")}</button>
            <button type="button" className="shop-settings-primary" disabled={!canUpdate || saving} title={deniedTitle} onClick={() => void onSave()}>
              {saving ? t("shopSettings.saving") : t("shopSettings.save")}
            </button>
          </div>
        </div>
      ) : null}

      {pendingDisable ? (
        <ConfirmDialog
          title={t("shopSettings.shipping.disableTitle")}
          message={t("shopSettings.shipping.disableMessage")}
          confirmLabel={t("shopSettings.shipping.disableConfirm")}
          cancelLabel={t("shopSettings.cancel")}
          danger
          confirmDisabled={toggling}
          onConfirm={() => void confirmDisable()}
          onCancel={() => setPendingDisable(null)}
        />
      ) : null}
    </section>
  );
}
