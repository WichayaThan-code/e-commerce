import { useEffect, useId, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { AlertCircle, Download, Landmark, RefreshCw, Wallet } from "lucide-react";
import ConfirmDialog from "../form/ConfirmDialog";
import SearchableSelect from "../SearchableSelect";
import MerchantPagination from "./MerchantPagination";
import MerchantPanelEmpty from "./MerchantPanelEmpty";
import { numberOf } from "./orderFormat";
import { formatDateTime } from "../../services/adminFormat";
import {
  ApiError,
  merchantFinanceApi,
  merchantShopSettingsApi,
  type ShopWalletBalance,
  type ShopWithdrawal,
  type ShopWithdrawalPage,
} from "../../services/api";

const SIZES = [10, 20, 50];
const STATUSES = ["REQUESTED", "APPROVED", "PROCESSING", "NEEDS_REVIEW", "PAID", "REJECTED", "CANCELLED"] as const;

const money = (v: unknown) => numberOf(v as number);
const baht = (v: number, loc: string) =>
  new Intl.NumberFormat(loc, { style: "currency", currency: "THB", maximumFractionDigits: 2 }).format(v);

const statusTone = (status: string) => {
  if (status === "PAID") return "success";
  if (status === "REJECTED" || status === "CANCELLED") return "refund";
  if (status === "NEEDS_REVIEW") return "review";
  if (status === "APPROVED" || status === "PROCESSING") return "progress";
  return "pending";
};

type Props = {
  shopId: number;
  canWithdraw: boolean;
  canExport: boolean;
  localeTag: string;
  deniedTitle: (ok: boolean) => string | undefined;
  notify: (msg: string) => void;
  mode: "balance" | "transfers";
};

export default function FinanceWithdrawPanel({
  shopId, canWithdraw, canExport, localeTag, deniedTitle, notify, mode,
}: Props) {
  const { t } = useTranslation("merchant");
  const [wallet, setWallet] = useState<{ status: "loading" | "ready" | "error"; data: ShopWalletBalance | null }>({ status: "loading", data: null });
  const [page, setPage] = useState<{ status: "loading" | "ready" | "error"; data: ShopWithdrawalPage | null }>({ status: "loading", data: null });
  const [pageNo, setPageNo] = useState(0);
  const [size, setSize] = useState(10);
  const [statusFilter, setStatusFilter] = useState("");
  const [reload, setReload] = useState(0);
  const [exporting, setExporting] = useState(false);
  const [openForm, setOpenForm] = useState(false);
  const [amount, setAmount] = useState("");
  const [provider, setProvider] = useState("BANK_TRANSFER");
  const [accounts, setAccounts] = useState<{ provider: string; label: string }[]>([]);
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState("");
  const [detail, setDetail] = useState<ShopWithdrawal | null>(null);
  const idempotencyRef = useRef<string>("");
  const walletReq = useRef(0);
  const listReq = useRef(0);
  const formId = useId();

  useEffect(() => {
    const id = ++walletReq.current;
    setWallet({ status: "loading", data: null });
    merchantFinanceApi.wallet(shopId)
      .then((data) => { if (walletReq.current === id) setWallet({ status: "ready", data }); })
      .catch(() => { if (walletReq.current === id) setWallet({ status: "error", data: null }); });
  }, [shopId, reload]);

  useEffect(() => {
    if (mode !== "transfers") return;
    const id = ++listReq.current;
    setPage({ status: "loading", data: null });
    merchantFinanceApi.withdrawals(shopId, {
      page: pageNo,
      size,
      status: statusFilter || undefined,
      dateField: "requestedAt",
    })
      .then((data) => { if (listReq.current === id) setPage({ status: "ready", data }); })
      .catch(() => { if (listReq.current === id) setPage({ status: "error", data: null }); });
  }, [shopId, mode, pageNo, size, statusFilter, reload]);

  useEffect(() => { setPageNo(0); }, [statusFilter, size, shopId]);

  const openWithdraw = async () => {
    setFormError("");
    setAmount("");
    idempotencyRef.current = `wd-${shopId}-${crypto.randomUUID()}`;
    try {
      const rows = await merchantShopSettingsApi.paymentAccounts(shopId);
      const active = rows.filter((r) => r.active && (r.provider === "BANK_TRANSFER" || r.provider === "PROMPTPAY"));
      setAccounts(active.map((r) => ({
        provider: r.provider,
        label: r.provider === "PROMPTPAY"
          ? `${t("finance.payout.promptpay")} · ${r.promptPayId ?? "****"}`
          : `${r.bankName ?? t("finance.payout.bank")} · ${r.accountNumber ?? "****"}`,
      })));
      if (active[0]) setProvider(active[0].provider);
      setOpenForm(true);
    } catch {
      notify(t("finance.payout.accountLoadFailed"));
    }
  };

  const submitWithdraw = async () => {
    const value = Number(amount);
    if (!Number.isFinite(value) || value <= 0) {
      setFormError(t("finance.payout.amountInvalid"));
      return;
    }
    if (!idempotencyRef.current) idempotencyRef.current = `wd-${shopId}-${crypto.randomUUID()}`;
    setBusy(true);
    setFormError("");
    try {
      await merchantFinanceApi.createWithdrawal(shopId, {
        amount: value,
        currency: "THB",
        accountProvider: provider,
        idempotencyKey: idempotencyRef.current,
      });
      setOpenForm(false);
      notify(t("finance.payout.requested"));
      setReload((n) => n + 1);
    } catch (error) {
      const code = error instanceof ApiError ? error.code : "";
      setFormError(t(`finance.payout.errors.${code}`, { defaultValue: t("finance.payout.requestFailed") }));
    } finally {
      setBusy(false);
    }
  };

  const cancel = async (row: ShopWithdrawal) => {
    try {
      await merchantFinanceApi.cancelWithdrawal(shopId, row.id);
      notify(t("finance.payout.cancelled"));
      setReload((n) => n + 1);
      setDetail(null);
    } catch {
      notify(t("finance.payout.cancelFailed"));
    }
  };

  const exportCsv = async () => {
    setExporting(true);
    try {
      await merchantFinanceApi.exportWithdrawalsCsv(shopId, {
        status: statusFilter || undefined,
        dateField: "requestedAt",
      });
      notify(t("finance.payout.exportDone"));
    } catch {
      notify(t("finance.payout.exportFailed"));
    } finally {
      setExporting(false);
    }
  };

  const available = money(wallet.data?.available);
  const reserved = money(wallet.data?.reserved);
  const canRequest = !!wallet.data?.canRequestWithdrawal;
  const statusOptions = [
    { value: "", label: t("finance.payout.statusAll") },
    ...STATUSES.map((s) => ({ value: s, label: t(`finance.payout.status.${s}`) })),
  ];
  const statusLabel = statusOptions.find((o) => o.value === statusFilter)?.label ?? t("finance.payout.statusAll");

  if (mode === "balance") {
    return (
      <section className="finance-rail-block finance-wallet-block">
        <header className="finance-wallet-head">
          <span className="finance-wallet-mark" aria-hidden="true"><Wallet /></span>
          <h2>{t("finance.payout.walletTitle")}</h2>
        </header>

        {wallet.status === "loading" ? (
          <div className="finance-rail-hero finance-wallet-hero is-loading" aria-busy="true">
            <small>{t("finance.payout.available")}</small>
            <b>—</b>
            <em>{t("finance.loading")}</em>
          </div>
        ) : wallet.status === "error" ? (
          <div className="finance-wallet-error" role="alert">
            <span className="finance-wallet-error-icon" aria-hidden="true"><AlertCircle /></span>
            <div>
              <b>{t("finance.payout.walletError")}</b>
              <p>{t("finance.error.hint")}</p>
            </div>
            <button type="button" className="finance-wallet-retry" onClick={() => setReload((n) => n + 1)}>
              <RefreshCw />{t("finance.error.retry")}
            </button>
          </div>
        ) : (
          <>
            <div className="finance-rail-hero finance-wallet-hero">
              <small>{t("finance.payout.available")}</small>
              <b>{baht(available, localeTag)}</b>
              <em>{t("finance.payout.availableNote")}</em>
            </div>
            <div className="finance-rail-metric">
              <span><Landmark /></span>
              <p>
                <small>{t("finance.payout.reserved")}</small>
                <strong>{baht(reserved, localeTag)}</strong>
                <em>{t("finance.payout.reservedNote")}</em>
              </p>
            </div>
            <p className="finance-ledger-note finance-wallet-note">{t("finance.payout.policyHint")}</p>
            <button
              type="button"
              className={`finance-rail-cta${canWithdraw && canRequest ? " is-ready" : ""}`}
              disabled={!canWithdraw || !canRequest || busy}
              title={!canWithdraw ? deniedTitle(canWithdraw) : (!canRequest ? t("finance.payout.noBalance") : t("finance.payout.withdraw"))}
              onClick={() => void openWithdraw()}
            >
              <Landmark />{t("finance.payout.withdraw")}
            </button>
          </>
        )}

        {openForm ? (
          <ConfirmDialog
            title={t("finance.payout.confirmTitle")}
            message={t("finance.payout.confirmBody", { amount: baht(Number(amount) || 0, localeTag) })}
            confirmLabel={busy ? t("finance.payout.submitting") : t("finance.payout.confirm")}
            cancelLabel={t("finance.payout.cancel")}
            confirmDisabled={busy || accounts.length === 0}
            onCancel={() => !busy && setOpenForm(false)}
            onConfirm={() => void submitWithdraw()}
          >
            <div className="finance-withdraw-form" id={formId}>
              <label>
                <span>{t("finance.payout.amount")}</span>
                <input type="number" min="0.01" step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} disabled={busy} />
              </label>
              <div className="finance-withdraw-account">
                <SearchableSelect
                  compact
                  label={t("finance.payout.account")}
                  placeholder={t("finance.payout.account")}
                  options={accounts.map((a) => a.label)}
                  value={accounts.find((a) => a.provider === provider)?.label ?? ""}
                  disabled={busy || accounts.length === 0}
                  onChange={(picked) => {
                    const next = accounts.find((a) => a.label === picked);
                    if (next) setProvider(next.provider);
                  }}
                />
              </div>
              {accounts.length === 0 ? <p className="form-field-error">{t("finance.payout.noAccount")}</p> : null}
              {formError ? <p className="form-field-error">{formError}</p> : null}
            </div>
          </ConfirmDialog>
        ) : null}
      </section>
    );
  }

  return (
    <article className="finance-panel transaction-card is-ledger finance-transfers">
      <header>
        <h2>{t("finance.tabs.transfers")}</h2>
      </header>

      <div className="finance-transfer-toolbar">
        <div className="finance-transfer-filter">
          <SearchableSelect
            compact
            label={t("finance.payout.statusFilter")}
            placeholder={t("finance.payout.statusAll")}
            options={statusOptions.map((o) => o.label)}
            value={statusLabel}
            onChange={(picked) => {
              const next = statusOptions.find((o) => o.label === picked);
              if (next) setStatusFilter(next.value);
            }}
          />
        </div>
      </div>
      <p className="finance-ledger-note finance-transfer-meta">{t("finance.payout.dateFilterNote")}</p>

      {page.status === "loading" ? <MerchantPanelEmpty icon={<Landmark />} title={t("finance.loading")} hint={t("finance.loadingHint")} />
        : page.status === "error" ? (
          <div className="finance-wallet-error finance-transfer-error" role="alert">
            <span className="finance-wallet-error-icon" aria-hidden="true"><AlertCircle /></span>
            <div>
              <b>{t("finance.payout.listError")}</b>
              <p>{t("finance.error.hint")}</p>
            </div>
            <button type="button" className="finance-wallet-retry" onClick={() => setReload((n) => n + 1)}>
              <RefreshCw />{t("finance.error.retry")}
            </button>
          </div>
        ) : !page.data || page.data.items.length === 0 ? (
          <MerchantPanelEmpty icon={<Landmark />} title={t("finance.payout.empty")} hint={t("finance.payout.emptyHint")} />
        ) : (
          <>
            <div className="finance-transfer-table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>{t("finance.payout.colRequested")}</th>
                    <th>{t("finance.payout.colAmount")}</th>
                    <th>{t("finance.payout.colAccount")}</th>
                    <th>{t("finance.payout.colStatus")}</th>
                    <th>{t("finance.payout.colPaid")}</th>
                    <th>{t("finance.payout.colRef")}</th>
                    <th>{t("finance.payout.detail")}</th>
                  </tr>
                </thead>
                <tbody>
                  {page.data.items.map((row) => (
                    <tr key={row.id}>
                      <td>{formatDateTime(row.requestedAt)}</td>
                      <td className="num">{baht(money(row.amount), localeTag)}</td>
                      <td>{row.accountNumberMasked}</td>
                      <td>
                        <span className={`finance-status ${statusTone(row.status)}`}>
                          {t(`finance.payout.status.${row.status}`)}
                        </span>
                      </td>
                      <td>{row.paidAt ? formatDateTime(row.paidAt) : "—"}</td>
                      <td className="ref">{row.transferReference ?? "—"}</td>
                      <td>
                        <div className="finance-transfer-row-actions">
                          <button type="button" className="link" onClick={() => setDetail(row)}>
                            {t("finance.payout.detail")}
                          </button>
                          {row.status === "REQUESTED" && canWithdraw ? (
                            <button type="button" className="danger-link" onClick={() => void cancel(row)}>
                              {t("finance.payout.cancelRequest")}
                            </button>
                          ) : null}
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <MerchantPagination
              className="finance-pagination"
              page={pageNo}
              pageCount={Math.max(1, page.data.totalPages)}
              size={size}
              total={page.data.totalItems}
              sizeOptions={SIZES}
              onPageChange={setPageNo}
              onSizeChange={(next) => { setSize(next); setPageNo(0); }}
            />
          </>
        )}

      {detail ? (
        <ConfirmDialog
          title={t("finance.payout.detailTitle")}
          message={`${t(`finance.payout.status.${detail.status}`)} · ${baht(money(detail.amount), localeTag)}`}
          confirmLabel={t("finance.payout.close")}
          cancelLabel={t("finance.payout.close")}
          onCancel={() => setDetail(null)}
          onConfirm={() => setDetail(null)}
        >
          <ul className="finance-payout-detail">
            <li>{t("finance.payout.colAccount")}: {detail.accountNumberMasked}</li>
            <li>{t("finance.payout.colRef")}: {detail.transferReference ?? "—"}</li>
            {detail.rejectReason ? <li>{t("finance.payout.rejectReason")}: {detail.rejectReason}</li> : null}
          </ul>
        </ConfirmDialog>
      ) : null}
    </article>
  );
}
