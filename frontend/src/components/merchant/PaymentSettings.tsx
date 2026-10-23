import { useCallback, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { AlertTriangle, CreditCard, Landmark, QrCode } from "lucide-react";
import ConfirmDialog from "../form/ConfirmDialog";
import FormField from "../form/FormField";
import FormGrid from "../form/FormGrid";
import { Skeleton } from "../feedback/Skeleton";
import {
  ApiError,
  merchantShopSettingsApi,
  type PaymentAccount,
  type PaymentMethodView,
  type PaymentMethodsView,
} from "../../services/api";

type Props = {
  shopId: number | null | undefined;
  canUpdate: boolean;
  deniedTitle?: string;
  notify: (text: string) => void;
};

type BankForm = { displayName: string; bankName: string; accountNumber: string; accountName: string };
type PromptForm = { displayName: string; promptPayId: string; accountName: string };

const emptyBank = (): BankForm => ({ displayName: "", bankName: "", accountNumber: "", accountName: "" });
const emptyPrompt = (): PromptForm => ({ displayName: "", promptPayId: "", accountName: "" });

export default function PaymentSettings({ shopId, canUpdate, deniedTitle, notify }: Props) {
  const { t } = useTranslation("merchant");
  const [accounts, setAccounts] = useState<PaymentAccount[]>([]);
  const [methods, setMethods] = useState<PaymentMethodsView | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);
  const [reload, setReload] = useState(0);
  const [bank, setBank] = useState<BankForm>(emptyBank());
  const [prompt, setPrompt] = useState<PromptForm>(emptyPrompt());
  const [bankErrors, setBankErrors] = useState<Record<string, string>>({});
  const [promptErrors, setPromptErrors] = useState<Record<string, string>>({});
  const [codErrors, setCodErrors] = useState<Record<string, string>>({});
  const [savingBank, setSavingBank] = useState(false);
  const [savingPrompt, setSavingPrompt] = useState(false);
  const [savingCod, setSavingCod] = useState(false);
  const [pendingDeactivate, setPendingDeactivate] = useState<"BANK_TRANSFER" | "PROMPTPAY" | null>(null);
  const [pendingCodOff, setPendingCodOff] = useState(false);
  const [codEnabled, setCodEnabled] = useState(false);
  const [codFee, setCodFee] = useState("0");
  const [codMax, setCodMax] = useState("");

  const bankAccount = accounts.find((a) => a.provider === "BANK_TRANSFER");
  const promptAccount = accounts.find((a) => a.provider === "PROMPTPAY");
  const bankMethod = methods?.methods.find((m) => m.method === "BANK_TRANSFER");
  const codMethod = methods?.methods.find((m) => m.method === "COD");
  const bankMasked = Boolean(bankAccount?.masked);
  const promptMasked = Boolean(promptAccount?.masked);

  const applyCod = (view: PaymentMethodView | undefined) => {
    setCodEnabled(Boolean(view?.enabled));
    setCodFee(view?.fee == null ? "0" : String(view.fee));
    setCodMax(view?.maxOrderAmount == null ? "" : String(view.maxOrderAmount));
  };

  const load = useCallback(() => {
    if (shopId == null) return;
    let alive = true;
    setLoading(true);
    setFailed(false);
    Promise.all([
      merchantShopSettingsApi.paymentAccounts(shopId),
      merchantShopSettingsApi.paymentMethods(shopId),
    ])
      .then(([acc, meth]) => {
        if (!alive) return;
        setAccounts(acc);
        setMethods(meth);
        const bankRow = acc.find((a) => a.provider === "BANK_TRANSFER");
        const promptRow = acc.find((a) => a.provider === "PROMPTPAY");
        setBank({
          displayName: bankRow?.displayName ?? "",
          bankName: bankRow?.bankName ?? "",
          accountNumber: bankRow?.accountNumber ?? "",
          accountName: bankRow?.accountName ?? "",
        });
        setPrompt({
          displayName: promptRow?.displayName ?? "",
          promptPayId: promptRow?.promptPayId ?? "",
          accountName: promptRow?.accountName ?? "",
        });
        applyCod(meth.methods.find((m) => m.method === "COD"));
      })
      .catch(() => { if (alive) setFailed(true); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [shopId]);

  useEffect(() => {
    if (shopId == null) return;
    return load();
  }, [shopId, reload, load]);

  const saveBank = async () => {
    if (!canUpdate || shopId == null || savingBank || bankMasked) return;
    setSavingBank(true);
    setBankErrors({});
    try {
      await merchantShopSettingsApi.savePaymentAccount(shopId, "BANK_TRANSFER", {
        displayName: bank.displayName.trim() || undefined,
        bankName: bank.bankName.trim() || undefined,
        accountNumber: bank.accountNumber.trim() || undefined,
        accountName: bank.accountName.trim() || undefined,
        active: true,
      });
      notify(t("shopSettings.saved"));
      setReload((n) => n + 1);
    } catch (error) {
      handleAccountError(error, setBankErrors);
    } finally {
      setSavingBank(false);
    }
  };

  const savePrompt = async () => {
    if (!canUpdate || shopId == null || savingPrompt || promptMasked) return;
    setSavingPrompt(true);
    setPromptErrors({});
    try {
      await merchantShopSettingsApi.savePaymentAccount(shopId, "PROMPTPAY", {
        displayName: prompt.displayName.trim() || undefined,
        promptPayId: prompt.promptPayId.trim() || undefined,
        accountName: prompt.accountName.trim() || undefined,
        active: true,
      });
      notify(t("shopSettings.saved"));
      setReload((n) => n + 1);
    } catch (error) {
      handleAccountError(error, setPromptErrors);
    } finally {
      setSavingPrompt(false);
    }
  };

  const handleAccountError = (error: unknown, setErrors: (v: Record<string, string>) => void) => {
    if (error instanceof ApiError && error.code === "PAYMENT_PROVIDER_NOT_SUPPORTED") {
      notify(error.message);
      return;
    }
    if (error instanceof ApiError && (error.code === "VALIDATION_ERROR" || error.code === "VALIDATION_FAILED")) {
      const mapped: Record<string, string> = {};
      error.fieldErrors.forEach((f) => { mapped[f.field] = f.message; });
      setErrors(mapped);
      return;
    }
    notify(error instanceof ApiError ? error.message : t("shopSettings.loadError"));
  };

  const confirmDeactivate = async () => {
    if (!pendingDeactivate || shopId == null) return;
    try {
      await merchantShopSettingsApi.deactivatePaymentAccount(shopId, pendingDeactivate);
      notify(t("shopSettings.payment.deactivated"));
      setPendingDeactivate(null);
      setReload((n) => n + 1);
    } catch (error) {
      notify(error instanceof ApiError ? error.message : t("shopSettings.loadError"));
    }
  };

  const requestCodToggle = (next: boolean) => {
    if (!canUpdate) return;
    if (!next && codEnabled) { setPendingCodOff(true); return; }
    setCodEnabled(next);
  };

  const saveCod = async (forceEnabled?: boolean) => {
    if (!canUpdate || shopId == null || savingCod) return;
    const enabled = forceEnabled ?? codEnabled;
    const fee = Number(codFee);
    const max = codMax.trim() === "" ? null : Number(codMax);
    const errors: Record<string, string> = {};
    if (!Number.isFinite(fee) || fee < 0 || fee > 9999.99) errors.fee = t("shopSettings.payment.codFeeInvalid");
    if (max != null && (!Number.isFinite(max) || max < 0)) errors.maxOrderAmount = t("shopSettings.payment.codMaxInvalid");
    setCodErrors(errors);
    if (Object.keys(errors).length) return;
    setSavingCod(true);
    try {
      const view = await merchantShopSettingsApi.saveCod(shopId, { enabled, fee, maxOrderAmount: max });
      setMethods(view);
      applyCod(view.methods.find((m) => m.method === "COD"));
      notify(t("shopSettings.saved"));
      setPendingCodOff(false);
    } catch (error) {
      if (error instanceof ApiError && (error.code === "VALIDATION_ERROR" || error.code === "VALIDATION_FAILED")) {
        const mapped: Record<string, string> = {};
        error.fieldErrors.forEach((f) => { mapped[f.field] = f.message; });
        setCodErrors(mapped);
      } else {
        notify(error instanceof ApiError ? error.message : t("shopSettings.loadError"));
      }
    } finally {
      setSavingCod(false);
    }
  };

  if (shopId == null) {
    return <section className="settings-card"><Skeleton height={160} width="100%" /></section>;
  }

  if (loading) {
    return (
      <section className="settings-card shop-settings-card">
        <div className="shop-settings-skeleton" aria-busy="true">
          <Skeleton height={28} width="40%" /><Skeleton height={120} /><Skeleton height={120} />
        </div>
      </section>
    );
  }

  if (failed) {
    return (
      <section className="settings-card shop-settings-card">
        <div className="shop-settings-empty" role="alert">
          <AlertTriangle /><p>{t("shopSettings.loadError")}</p>
          <button type="button" onClick={() => setReload((n) => n + 1)}>{t("shopSettings.retry")}</button>
        </div>
      </section>
    );
  }

  return (
    <>
      <section className="settings-card shop-settings-card">
        <header>
          <div>
            <h2><Landmark />{t("shopSettings.payment.accountsTitle")}</h2>
            <p>{t("shopSettings.payment.accountsLead")}</p>
          </div>
        </header>

        <div className="shop-settings-account">
          <h3><Landmark size={16} />{t("shopSettings.payment.bankTransfer")}</h3>
          {bankMasked ? <p className="shop-settings-warn">{t("shopSettings.payment.masked")}</p> : null}
          <FormGrid>
            <FormField label={t("shopSettings.payment.displayName")} error={bankErrors.displayName}>
              <input value={bank.displayName} disabled={!canUpdate || bankMasked || savingBank} onChange={(e) => setBank((f) => ({ ...f, displayName: e.target.value }))} />
            </FormField>
            <FormField label={t("shopSettings.payment.bankName")} error={bankErrors.bankName}>
              <input value={bank.bankName} disabled={!canUpdate || bankMasked || savingBank} onChange={(e) => setBank((f) => ({ ...f, bankName: e.target.value }))} />
            </FormField>
            <FormField label={t("shopSettings.payment.accountNumber")} error={bankErrors.accountNumber}>
              <input className="num" value={bank.accountNumber} disabled={!canUpdate || bankMasked || savingBank} onChange={(e) => setBank((f) => ({ ...f, accountNumber: e.target.value }))} />
            </FormField>
            <FormField label={t("shopSettings.payment.accountName")} error={bankErrors.accountName}>
              <input value={bank.accountName} disabled={!canUpdate || bankMasked || savingBank} onChange={(e) => setBank((f) => ({ ...f, accountName: e.target.value }))} />
            </FormField>
          </FormGrid>
          <div className="shop-settings-form-actions">
            <button type="button" className="shop-settings-primary" disabled={!canUpdate || bankMasked || savingBank} title={deniedTitle} onClick={() => void saveBank()}>
              {savingBank ? t("shopSettings.saving") : t("shopSettings.save")}
            </button>
            {bankAccount?.active ? (
              <button type="button" className="danger" disabled={!canUpdate || savingBank} title={deniedTitle} onClick={() => setPendingDeactivate("BANK_TRANSFER")}>
                {t("shopSettings.payment.deactivate")}
              </button>
            ) : null}
          </div>
        </div>

        <div className="shop-settings-account">
          <h3><QrCode size={16} />{t("shopSettings.payment.promptPay")}</h3>
          {promptMasked ? <p className="shop-settings-warn">{t("shopSettings.payment.masked")}</p> : null}
          <FormGrid>
            <FormField label={t("shopSettings.payment.displayName")} error={promptErrors.displayName}>
              <input value={prompt.displayName} disabled={!canUpdate || promptMasked || savingPrompt} onChange={(e) => setPrompt((f) => ({ ...f, displayName: e.target.value }))} />
            </FormField>
            <FormField label={t("shopSettings.payment.promptPayId")} error={promptErrors.promptPayId}>
              <input className="num" value={prompt.promptPayId} disabled={!canUpdate || promptMasked || savingPrompt} onChange={(e) => setPrompt((f) => ({ ...f, promptPayId: e.target.value }))} />
            </FormField>
            <FormField label={t("shopSettings.payment.accountName")} error={promptErrors.accountName}>
              <input value={prompt.accountName} disabled={!canUpdate || promptMasked || savingPrompt} onChange={(e) => setPrompt((f) => ({ ...f, accountName: e.target.value }))} />
            </FormField>
          </FormGrid>
          <div className="shop-settings-form-actions">
            <button type="button" className="shop-settings-primary" disabled={!canUpdate || promptMasked || savingPrompt} title={deniedTitle} onClick={() => void savePrompt()}>
              {savingPrompt ? t("shopSettings.saving") : t("shopSettings.save")}
            </button>
            {promptAccount?.active ? (
              <button type="button" className="danger" disabled={!canUpdate || savingPrompt} title={deniedTitle} onClick={() => setPendingDeactivate("PROMPTPAY")}>
                {t("shopSettings.payment.deactivate")}
              </button>
            ) : null}
          </div>
        </div>
      </section>

      <section className="settings-card shop-settings-card">
        <header>
          <div>
            <h2><CreditCard />{t("shopSettings.payment.methodsTitle")}</h2>
            <p>{t("shopSettings.payment.methodsLead")}</p>
          </div>
        </header>

        <div className="shop-settings-method">
          <div className="shop-settings-method-row">
            <div>
              <b>{t("shopSettings.payment.bankTransfer")}</b>
              <small>{t("shopSettings.payment.bankAlwaysOn")}</small>
            </div>
            <span className="shop-settings-badge is-on">{t("shopSettings.active")}</span>
          </div>
          {bankMethod?.accountsConfigured === false ? (
            <p className="shop-settings-warn">{t("shopSettings.payment.noAccounts")}</p>
          ) : null}
        </div>

        <div className="shop-settings-method">
          <div className="shop-settings-method-row">
            <div>
              <b>{t("shopSettings.payment.cod")}</b>
              <small>{t("shopSettings.payment.codLead")}</small>
            </div>
            <label className="toggle-row shop-settings-toggle">
              <button
                type="button"
                role="switch"
                aria-checked={codEnabled}
                disabled={!canUpdate || savingCod}
                title={deniedTitle}
                className={codEnabled ? "on" : ""}
                onClick={() => requestCodToggle(!codEnabled)}
              ><i /></button>
            </label>
          </div>
          <FormGrid>
            <FormField label={t("shopSettings.payment.codFee")} error={codErrors.fee}>
              <input className="num" value={codFee} disabled={!canUpdate || savingCod} onChange={(e) => setCodFee(e.target.value)} inputMode="decimal" />
            </FormField>
            <FormField label={t("shopSettings.payment.codMax")} error={codErrors.maxOrderAmount}>
              <input className="num" value={codMax} disabled={!canUpdate || savingCod} onChange={(e) => setCodMax(e.target.value)} inputMode="decimal" placeholder={t("shopSettings.payment.codMaxHint")} />
            </FormField>
          </FormGrid>
          <div className="shop-settings-form-actions">
            <button type="button" className="shop-settings-primary" disabled={!canUpdate || savingCod} title={deniedTitle} onClick={() => void saveCod()}>
              {savingCod ? t("shopSettings.saving") : t("shopSettings.save")}
            </button>
          </div>
        </div>
      </section>

      {pendingDeactivate ? (
        <ConfirmDialog
          title={t("shopSettings.payment.deactivateTitle")}
          message={t("shopSettings.payment.deactivateMessage")}
          confirmLabel={t("shopSettings.payment.deactivate")}
          cancelLabel={t("shopSettings.cancel")}
          danger
          onConfirm={() => void confirmDeactivate()}
          onCancel={() => setPendingDeactivate(null)}
        />
      ) : null}

      {pendingCodOff ? (
        <ConfirmDialog
          title={t("shopSettings.payment.codOffTitle")}
          message={t("shopSettings.payment.codOffMessage")}
          confirmLabel={t("shopSettings.payment.codOffConfirm")}
          cancelLabel={t("shopSettings.cancel")}
          danger
          confirmDisabled={savingCod}
          onConfirm={() => void saveCod(false)}
          onCancel={() => setPendingCodOff(false)}
        />
      ) : null}
    </>
  );
}
