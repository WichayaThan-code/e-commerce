import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { AlertCircle, Download, FileText, Plus, RefreshCw, X } from "lucide-react";
import ConfirmDialog from "../form/ConfirmDialog";
import DateRangePicker from "../form/DateRangePicker";
import FormField from "../form/FormField";
import SearchableSelect from "../SearchableSelect";
import MerchantPagination from "./MerchantPagination";
import MerchantPanelEmpty from "./MerchantPanelEmpty";
import { numberOf } from "./orderFormat";
import { formatDateTime } from "../../services/adminFormat";
import {
  ApiError,
  merchantFinanceApi,
  merchantOrderApi,
  type ShopInvoiceDetail,
  type ShopInvoiceDocType,
  type ShopInvoicePage,
  type ShopInvoiceSummary,
  type ShopTaxProfile,
} from "../../services/api";
import "../../styles/form-fields.css";
import "../../styles/order-management.css";
import "../../styles/date-range-picker.css";
import "../../styles/date-picker.css";

const SIZES = [10, 20, 50];
const money = (v: unknown) => numberOf(v as number);
const baht = (v: number, loc: string) =>
  new Intl.NumberFormat(loc, { style: "currency", currency: "THB", maximumFractionDigits: 2 }).format(v);

const statusTone = (status: string) => {
  if (status === "ISSUED") return "success";
  if (status === "CANCELLED") return "refund";
  return "pending";
};

export type InvoiceExportFilters = {
  docType?: string;
  status?: string;
  q?: string;
  from?: string;
  to?: string;
};

type Props = {
  shopId: number;
  canManage: boolean;
  localeTag: string;
  deniedTitle: (ok: boolean) => string | undefined;
  notify: (msg: string) => void;
  /** Keeps page-header export in sync with the active list filters. */
  exportFiltersRef?: { current: InvoiceExportFilters };
  onListStateChange?: (state: { total: number; loading: boolean }) => void;
};

export default function FinanceInvoicePanel({
  shopId, canManage, localeTag, deniedTitle, notify, exportFiltersRef, onListStateChange,
}: Props) {
  const { t } = useTranslation("merchant");
  const [profile, setProfile] = useState<{ status: "loading" | "ready" | "error"; data: ShopTaxProfile | null }>({ status: "loading", data: null });
  const [page, setPage] = useState<{ status: "loading" | "ready" | "error"; data: ShopInvoicePage | null }>({ status: "loading", data: null });
  const [pageNo, setPageNo] = useState(0);
  const [size, setSize] = useState(10);
  const [docType, setDocType] = useState("");
  const [status, setStatus] = useState("");
  const [q, setQ] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [reload, setReload] = useState(0);
  const [detail, setDetail] = useState<ShopInvoiceDetail | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [profileOpen, setProfileOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [formError, setFormError] = useState("");
  const [orders, setOrders] = useState<{ id: string; label: string }[]>([]);
  const [orderId, setOrderId] = useState("");
  const [createType, setCreateType] = useState<ShopInvoiceDocType>("INVOICE");
  const [buyerTaxId, setBuyerTaxId] = useState("");
  const [note, setNote] = useState("");
  const [legalName, setLegalName] = useState("");
  const [taxId, setTaxId] = useState("");
  const [branchCode, setBranchCode] = useState("00000");
  const [vatRegistered, setVatRegistered] = useState(false);
  const [vatRate, setVatRate] = useState("");
  const [editBuyerName, setEditBuyerName] = useState("");
  const [editBuyerTaxId, setEditBuyerTaxId] = useState("");
  const [editBuyerAddress, setEditBuyerAddress] = useState("");
  const [editBuyerPhone, setEditBuyerPhone] = useState("");
  const [editBuyerEmail, setEditBuyerEmail] = useState("");
  const [editNote, setEditNote] = useState("");
  const [cancelOpen, setCancelOpen] = useState(false);
  const [cancelReason, setCancelReason] = useState("");
  const [issueOpen, setIssueOpen] = useState(false);
  const createKey = useRef("");
  const issueKey = useRef("");
  const listReq = useRef(0);
  const profileReq = useRef(0);

  const docTypeOptions = [
    { value: "", label: t("finance.invoices.filterTypeAll") },
    { value: "INVOICE", label: t("finance.invoices.type.INVOICE") },
    { value: "TAX_INVOICE", label: t("finance.invoices.type.TAX_INVOICE") },
  ];
  const statusOptions = [
    { value: "", label: t("finance.invoices.filterStatusAll") },
    { value: "DRAFT", label: t("finance.invoices.status.DRAFT") },
    { value: "ISSUED", label: t("finance.invoices.status.ISSUED") },
    { value: "CANCELLED", label: t("finance.invoices.status.CANCELLED") },
  ];

  useEffect(() => {
    const id = ++profileReq.current;
    setProfile({ status: "loading", data: null });
    merchantFinanceApi.taxProfile(shopId)
      .then((data) => { if (profileReq.current === id) setProfile({ status: "ready", data }); })
      .catch(() => { if (profileReq.current === id) setProfile({ status: "error", data: null }); });
  }, [shopId, reload]);

  useEffect(() => {
    const id = ++listReq.current;
    setPage({ status: "loading", data: null });
    merchantFinanceApi.invoices(shopId, {
      page: pageNo,
      size,
      docType: docType || undefined,
      status: status || undefined,
      q: q || undefined,
      from: from || undefined,
      to: to || undefined,
      dateField: "createdAt",
    })
      .then((data) => { if (listReq.current === id) setPage({ status: "ready", data }); })
      .catch(() => { if (listReq.current === id) setPage({ status: "error", data: null }); });
  }, [shopId, pageNo, size, docType, status, q, from, to, reload]);

  useEffect(() => { setPageNo(0); }, [docType, status, q, from, to, size, shopId]);

  useEffect(() => {
    if (!onListStateChange) return;
    onListStateChange({
      total: page.status === "ready" ? (page.data?.totalItems ?? 0) : 0,
      loading: page.status === "loading",
    });
  }, [page, onListStateChange]);

  const openCreate = async () => {
    setFormError("");
    setBuyerTaxId("");
    setNote("");
    setCreateType("INVOICE");
    setOrderId("");
    createKey.current = `inv-${shopId}-${crypto.randomUUID()}`;
    try {
      const data = await merchantOrderApi.list(shopId, { page: 0, size: 50, sort: "placedAt", direction: "desc" });
      setOrders(data.items.map((o) => ({ id: o.id, label: `${o.orderNumber} · ${baht(money(o.grandTotal), localeTag)}` })));
      setCreateOpen(true);
    } catch {
      notify(t("finance.invoices.orderLoadFailed"));
    }
  };

  const openProfile = () => {
    const p = profile.data;
    setLegalName(p?.legalName ?? "");
    setTaxId(p?.taxId ?? "");
    setBranchCode(p?.branchCode ?? "00000");
    setVatRegistered(!!p?.vatRegistered);
    setVatRate(p?.vatRate != null ? String(money(p.vatRate)) : "");
    setFormError("");
    setProfileOpen(true);
  };

  const saveProfile = async () => {
    setBusy(true);
    setFormError("");
    try {
      const data = await merchantFinanceApi.upsertTaxProfile(shopId, {
        legalName: legalName || null,
        taxId: taxId || null,
        branchCode: branchCode || "00000",
        vatRegistered,
        vatRate: vatRegistered && vatRate !== "" ? Number(vatRate) : null,
      });
      setProfile({ status: "ready", data });
      setProfileOpen(false);
      notify(t("finance.invoices.profileSaved"));
    } catch (error) {
      const code = error instanceof ApiError ? error.code : "";
      setFormError(t(`finance.invoices.errors.${code}`, { defaultValue: t("finance.invoices.profileFailed") }));
    } finally {
      setBusy(false);
    }
  };

  const submitCreate = async () => {
    if (!orderId) {
      setFormError(t("finance.invoices.orderRequired"));
      return;
    }
    if (createType === "TAX_INVOICE" && !profile.data?.canIssueTaxInvoice) {
      setFormError(t("finance.invoices.taxBlocked"));
      return;
    }
    if (!createKey.current) createKey.current = `inv-${shopId}-${crypto.randomUUID()}`;
    setBusy(true);
    setFormError("");
    try {
      const created = await merchantFinanceApi.createInvoice(shopId, {
        orderId,
        docType: createType,
        buyerTaxId: buyerTaxId || undefined,
        note: note || undefined,
        idempotencyKey: createKey.current,
      });
      setCreateOpen(false);
      notify(t("finance.invoices.draftCreated"));
      setReload((n) => n + 1);
      await openDetail(created.id);
    } catch (error) {
      const code = error instanceof ApiError ? error.code : "";
      setFormError(t(`finance.invoices.errors.${code}`, { defaultValue: t("finance.invoices.createFailed") }));
    } finally {
      setBusy(false);
    }
  };

  const openDetail = async (id: string) => {
    try {
      const data = await merchantFinanceApi.invoice(shopId, id);
      setDetail(data);
      setEditBuyerName(data.buyerName);
      setEditBuyerTaxId(data.buyerTaxId ?? "");
      setEditBuyerAddress(data.buyerAddress);
      setEditBuyerPhone(data.buyerPhone ?? "");
      setEditBuyerEmail(data.buyerEmail ?? "");
      setEditNote(data.note ?? "");
    } catch {
      notify(t("finance.invoices.detailFailed"));
    }
  };

  const saveDraft = async () => {
    if (!detail) return;
    setBusy(true);
    try {
      const data = await merchantFinanceApi.updateInvoiceDraft(shopId, detail.id, {
        buyerName: editBuyerName,
        buyerTaxId: editBuyerTaxId || null,
        buyerAddress: editBuyerAddress,
        buyerPhone: editBuyerPhone || null,
        buyerEmail: editBuyerEmail || null,
        note: editNote || null,
      });
      setDetail(data);
      notify(t("finance.invoices.draftSaved"));
      setReload((n) => n + 1);
    } catch (error) {
      const code = error instanceof ApiError ? error.code : "";
      notify(t(`finance.invoices.errors.${code}`, { defaultValue: t("finance.invoices.saveFailed") }));
    } finally {
      setBusy(false);
    }
  };

  const confirmIssue = async () => {
    if (!detail) return;
    if (!issueKey.current) issueKey.current = `issue-${detail.id}-${crypto.randomUUID()}`;
    setBusy(true);
    try {
      const data = await merchantFinanceApi.issueInvoice(shopId, detail.id, issueKey.current);
      setDetail(data);
      setIssueOpen(false);
      notify(t("finance.invoices.issued"));
      setReload((n) => n + 1);
    } catch (error) {
      const code = error instanceof ApiError ? error.code : "";
      notify(t(`finance.invoices.errors.${code}`, { defaultValue: t("finance.invoices.issueFailed") }));
    } finally {
      setBusy(false);
    }
  };

  const confirmCancel = async () => {
    if (!detail) return;
    setBusy(true);
    try {
      const data = await merchantFinanceApi.cancelInvoice(shopId, detail.id, cancelReason.trim());
      setDetail(data);
      setCancelOpen(false);
      notify(t("finance.invoices.cancelled"));
      setReload((n) => n + 1);
    } catch (error) {
      const code = error instanceof ApiError ? error.code : "";
      notify(t(`finance.invoices.errors.${code}`, { defaultValue: t("finance.invoices.cancelFailed") }));
    } finally {
      setBusy(false);
    }
  };

  if (exportFiltersRef) {
    exportFiltersRef.current = {
      docType: docType || undefined,
      status: status || undefined,
      q: q || undefined,
      from: from || undefined,
      to: to || undefined,
    };
  }

  const downloadPdf = async (row: ShopInvoiceSummary | ShopInvoiceDetail) => {
    try {
      const name = `${row.documentNumber ?? row.id}.pdf`;
      await merchantFinanceApi.downloadInvoicePdf(shopId, row.id, name);
      notify(t("finance.invoices.pdfDone"));
    } catch {
      notify(t("finance.invoices.pdfFailed"));
    }
  };

  const typeLabel = (value: string) => docTypeOptions.find((o) => o.value === value)?.label ?? t("finance.invoices.filterTypeAll");
  const statusLabel = (value: string) => statusOptions.find((o) => o.value === value)?.label ?? t("finance.invoices.filterStatusAll");

  return (
    <article className="finance-panel transaction-card is-ledger finance-invoices">
      <header>
        <h2>{t("finance.tabs.invoices")}</h2>
        <div className="finance-invoice-header-actions">
          {canManage ? (
            <>
              <button type="button" className="ghost" onClick={openProfile}>{t("finance.invoices.editProfile")}</button>
              <button type="button" className="primary" onClick={() => void openCreate()}><Plus />{t("finance.invoices.create")}</button>
            </>
          ) : (
            <button type="button" disabled title={deniedTitle(canManage)}><Plus />{t("finance.invoices.create")}</button>
          )}
        </div>
      </header>

      <div className="finance-invoice-policy">
        {profile.status === "loading" ? <p>{t("finance.loading")}</p>
          : profile.status === "error" ? (
            <div className="finance-wallet-error" role="alert">
              <span className="finance-wallet-error-icon"><AlertCircle /></span>
              <div><b>{t("finance.invoices.profileLoadFailed")}</b><p>{t("finance.error.hint")}</p></div>
              <button type="button" className="finance-wallet-retry" onClick={() => setReload((n) => n + 1)}><RefreshCw />{t("finance.error.retry")}</button>
            </div>
          ) : (
            <p>
              {profile.data?.canIssueTaxInvoice
                ? t("finance.invoices.taxReady")
                : t("finance.invoices.taxNotReady", { reason: t(`finance.invoices.block.${profile.data?.taxInvoiceBlockedReason ?? "TAX_PROFILE_MISSING"}`) })}
              {" "}{t("finance.invoices.notEtax")}
            </p>
          )}
      </div>

      <form className="om-filters finance-invoice-filters" onSubmit={(e) => { e.preventDefault(); setReload((n) => n + 1); }}>
        <div className="om-filters-main finance-invoice-filters-main">
          <label className="om-filter-search">
            <span className="form-label">{t("finance.invoices.search")}</span>
            <span className="om-search">
              <input value={q} onChange={(e) => setQ(e.target.value)} placeholder={t("finance.invoices.searchPh")} />
            </span>
          </label>
          <div className="finance-invoice-select">
            <SearchableSelect
              label={t("finance.invoices.filterType")}
              options={docTypeOptions.map((o) => o.label)}
              value={typeLabel(docType)}
              onChange={(picked) => setDocType(docTypeOptions.find((o) => o.label === picked)?.value ?? "")}
            />
          </div>
          <div className="finance-invoice-select">
            <SearchableSelect
              label={t("finance.invoices.filterStatus")}
              options={statusOptions.map((o) => o.label)}
              value={statusLabel(status)}
              onChange={(picked) => setStatus(statusOptions.find((o) => o.label === picked)?.value ?? "")}
            />
          </div>
          <div className="om-filter-range">
            <DateRangePicker
              portal
              label={t("finance.invoices.dateRange")}
              from={from}
              to={to}
              clearLabel={t("orderManagement.filters.clear")}
              applyLabel={t("orderManagement.filters.apply")}
              presetsLabel={{
                d7: t("orderManagement.filters.presets.d7"),
                d30: t("orderManagement.filters.presets.d30"),
                month: t("orderManagement.filters.presets.month"),
              }}
              onChange={({ from: f, to: next }) => { setFrom(f); setTo(next); }}
            />
          </div>
        </div>
        <p className="finance-ledger-note finance-invoice-meta">{t("finance.invoices.dateFilterNote")}</p>
      </form>

      {page.status === "loading" ? <MerchantPanelEmpty icon={<FileText />} title={t("finance.loading")} hint={t("finance.loadingHint")} />
        : page.status === "error" ? (
          <div className="finance-wallet-error finance-transfer-error" role="alert">
            <span className="finance-wallet-error-icon"><AlertCircle /></span>
            <div><b>{t("finance.invoices.listError")}</b><p>{t("finance.error.hint")}</p></div>
            <button type="button" className="finance-wallet-retry" onClick={() => setReload((n) => n + 1)}><RefreshCw />{t("finance.error.retry")}</button>
          </div>
        ) : !page.data || page.data.items.length === 0 ? (
          <MerchantPanelEmpty icon={<FileText />} title={t("finance.invoices.empty")} hint={t("finance.invoices.emptyHint")} />
        ) : (
          <>
            <div className="finance-transfer-table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>{t("finance.invoices.colNumber")}</th>
                    <th>{t("finance.invoices.colType")}</th>
                    <th>{t("finance.invoices.colBuyer")}</th>
                    <th>{t("finance.invoices.colTotal")}</th>
                    <th>{t("finance.invoices.colStatus")}</th>
                    <th>{t("finance.invoices.colCreated")}</th>
                    <th>{t("finance.invoices.colActions")}</th>
                  </tr>
                </thead>
                <tbody>
                  {page.data.items.map((row) => (
                    <tr key={row.id} className="finance-invoice-row" onClick={() => void openDetail(row.id)}>
                      <td>{row.documentNumber ?? "—"}</td>
                      <td>{t(`finance.invoices.type.${row.docType}`)}</td>
                      <td>{row.buyerName}</td>
                      <td className="num">{baht(money(row.grandTotal), localeTag)}</td>
                      <td><span className={`finance-status ${statusTone(row.status)}`}>{t(`finance.invoices.status.${row.status}`)}</span></td>
                      <td>{formatDateTime(row.createdAt)}</td>
                      <td onClick={(e) => e.stopPropagation()}>
                        <div className="finance-transfer-row-actions">
                          <button type="button" className="link" onClick={() => void openDetail(row.id)}>{t("finance.invoices.detail")}</button>
                          <button type="button" className="link" onClick={() => void downloadPdf(row)}>{t("finance.invoices.pdf")}</button>
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

      {createOpen ? (
        <ConfirmDialog
          title={t("finance.invoices.createTitle")}
          message={t("finance.invoices.createHint")}
          confirmLabel={busy ? t("finance.invoices.saving") : t("finance.invoices.createConfirm")}
          cancelLabel={t("finance.invoices.close")}
          confirmDisabled={busy || !orderId}
          onCancel={() => !busy && setCreateOpen(false)}
          onConfirm={() => void submitCreate()}
        >
          <div className="finance-withdraw-form">
            <div className="finance-withdraw-account">
              <SearchableSelect
                compact
                label={t("finance.invoices.order")}
                placeholder={t("finance.invoices.orderPh")}
                options={orders.map((o) => o.label)}
                value={orders.find((o) => o.id === orderId)?.label ?? ""}
                disabled={busy}
                onChange={(picked) => setOrderId(orders.find((o) => o.label === picked)?.id ?? "")}
              />
            </div>
            <div className="finance-withdraw-account">
              <SearchableSelect
                compact
                label={t("finance.invoices.docType")}
                options={[t("finance.invoices.type.INVOICE"), t("finance.invoices.type.TAX_INVOICE")]}
                value={t(`finance.invoices.type.${createType}`)}
                disabled={busy}
                onChange={(picked) => setCreateType(picked === t("finance.invoices.type.TAX_INVOICE") ? "TAX_INVOICE" : "INVOICE")}
              />
            </div>
            <FormField label={t("finance.invoices.buyerTaxId")}>
              <input value={buyerTaxId} onChange={(e) => setBuyerTaxId(e.target.value)} disabled={busy} placeholder="13 digits" />
            </FormField>
            <FormField label={t("finance.invoices.note")} multiline>
              <textarea value={note} onChange={(e) => setNote(e.target.value)} disabled={busy} rows={2} />
            </FormField>
            {createType === "TAX_INVOICE" && !profile.data?.canIssueTaxInvoice ? (
              <p className="form-field-error">{t("finance.invoices.taxBlocked")}</p>
            ) : null}
            {formError ? <p className="form-field-error">{formError}</p> : null}
          </div>
        </ConfirmDialog>
      ) : null}

      {profileOpen ? (
        <ConfirmDialog
          title={t("finance.invoices.profileTitle")}
          message={t("finance.invoices.profileHint")}
          confirmLabel={busy ? t("finance.invoices.saving") : t("finance.invoices.profileSave")}
          cancelLabel={t("finance.invoices.close")}
          confirmDisabled={busy}
          onCancel={() => !busy && setProfileOpen(false)}
          onConfirm={() => void saveProfile()}
        >
          <div className="finance-withdraw-form">
            <FormField label={t("finance.invoices.legalName")}>
              <input value={legalName} onChange={(e) => setLegalName(e.target.value)} disabled={busy} />
            </FormField>
            <FormField label={t("finance.invoices.taxId")}>
              <input value={taxId} onChange={(e) => setTaxId(e.target.value)} disabled={busy} />
            </FormField>
            <FormField label={t("finance.invoices.branchCode")}>
              <input value={branchCode} onChange={(e) => setBranchCode(e.target.value)} disabled={busy} />
            </FormField>
            <label className="finance-invoice-check">
              <input type="checkbox" checked={vatRegistered} onChange={(e) => setVatRegistered(e.target.checked)} disabled={busy} />
              <span>{t("finance.invoices.vatRegistered")}</span>
            </label>
            {vatRegistered ? (
              <FormField label={t("finance.invoices.vatRate")} aside={<span>{t("finance.invoices.vatRateHint")}</span>}>
                <input value={vatRate} onChange={(e) => setVatRate(e.target.value)} disabled={busy} placeholder="0.07" />
              </FormField>
            ) : null}
            {formError ? <p className="form-field-error">{formError}</p> : null}
          </div>
        </ConfirmDialog>
      ) : null}

      {detail ? (
        <div className="om-drawer-overlay finance-invoice-drawer" role="presentation" onClick={() => setDetail(null)}>
          <aside className="om-drawer" role="dialog" aria-modal="true" aria-label={t("finance.invoices.detailTitle")} onClick={(e) => e.stopPropagation()}>
            <header>
              <div>
                <h2>{t("finance.invoices.detailTitle")}</h2>
                <p>{detail.documentNumber ?? t("finance.invoices.status.DRAFT")} · {t(`finance.invoices.type.${detail.docType}`)}</p>
              </div>
              <button type="button" className="om-drawer-close" onClick={() => setDetail(null)} aria-label={t("finance.invoices.close")}><X /></button>
            </header>
            <div className="om-drawer-body">
              <p className="finance-ledger-note">{detail.disclaimer}</p>
              <div className="finance-invoice-detail-grid">
                <div>
                  <small>{t("finance.invoices.seller")}</small>
                  <b>{detail.sellerLegalName}</b>
                  <span>{detail.sellerAddress}</span>
                  {detail.sellerTaxId ? <span>{t("finance.invoices.taxId")}: {detail.sellerTaxId}</span> : null}
                </div>
                <div>
                  <small>{t("finance.invoices.buyer")}</small>
                  {detail.status === "DRAFT" && canManage ? (
                    <div className="finance-withdraw-form">
                      <FormField label={t("finance.invoices.buyerName")}>
                        <input value={editBuyerName} onChange={(e) => setEditBuyerName(e.target.value)} disabled={busy} />
                      </FormField>
                      <FormField label={t("finance.invoices.buyerTaxId")}>
                        <input value={editBuyerTaxId} onChange={(e) => setEditBuyerTaxId(e.target.value)} disabled={busy} />
                      </FormField>
                      <FormField label={t("finance.invoices.buyerAddress")} multiline>
                        <textarea value={editBuyerAddress} onChange={(e) => setEditBuyerAddress(e.target.value)} disabled={busy} rows={3} />
                      </FormField>
                      <FormField label={t("finance.invoices.buyerPhone")}>
                        <input value={editBuyerPhone} onChange={(e) => setEditBuyerPhone(e.target.value)} disabled={busy} />
                      </FormField>
                      <FormField label={t("finance.invoices.buyerEmail")}>
                        <input value={editBuyerEmail} onChange={(e) => setEditBuyerEmail(e.target.value)} disabled={busy} />
                      </FormField>
                      <FormField label={t("finance.invoices.note")} multiline>
                        <textarea value={editNote} onChange={(e) => setEditNote(e.target.value)} disabled={busy} rows={2} />
                      </FormField>
                    </div>
                  ) : (
                    <>
                      <b>{detail.buyerName}</b>
                      <span>{detail.buyerAddress}</span>
                      {detail.buyerTaxId ? <span>{t("finance.invoices.taxId")}: {detail.buyerTaxId}</span> : null}
                    </>
                  )}
                </div>
              </div>
              <table className="finance-invoice-lines">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>{t("finance.invoices.colItem")}</th>
                    <th>{t("finance.invoices.colQty")}</th>
                    <th>{t("finance.invoices.colAmount")}</th>
                  </tr>
                </thead>
                <tbody>
                  {detail.lines.map((line) => (
                    <tr key={line.lineNo}>
                      <td>{line.lineNo}</td>
                      <td>{line.description}</td>
                      <td>{String(line.quantity)}</td>
                      <td className="num">{baht(money(line.lineSubtotal), localeTag)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <div className="finance-invoice-totals">
                <span>{t("finance.invoices.subtotal")}: <b className="num">{baht(money(detail.subtotal), localeTag)}</b></span>
                <span>{t("finance.invoices.discount")}: <b className="num">{baht(money(detail.discountTotal), localeTag)}</b></span>
                <span>{t("finance.invoices.shipping")}: <b className="num">{baht(money(detail.shippingTotal), localeTag)}</b></span>
                <span>{t("finance.invoices.tax")}: <b className="num">{baht(money(detail.taxAmount), localeTag)}</b></span>
                <span>{t("finance.invoices.grandTotal")}: <b className="num">{baht(money(detail.grandTotal), localeTag)}</b></span>
              </div>
              <ul className="finance-payout-detail">
                {detail.events.map((ev, idx) => (
                  <li key={`${ev.at}-${idx}`}>{formatDateTime(ev.at)} · {ev.eventCode} · {ev.fromStatus ?? "—"} → {ev.toStatus}</li>
                ))}
              </ul>
            </div>
            <footer className="finance-invoice-drawer-actions">
              <button type="button" onClick={() => void downloadPdf(detail)}><Download />{t("finance.invoices.pdf")}</button>
              {detail.status === "DRAFT" && canManage ? (
                <>
                  <button type="button" onClick={() => void saveDraft()} disabled={busy}>{t("finance.invoices.saveDraft")}</button>
                  <button type="button" className="primary" onClick={() => { issueKey.current = `issue-${detail.id}-${crypto.randomUUID()}`; setIssueOpen(true); }} disabled={busy}>
                    {t("finance.invoices.issue")}
                  </button>
                  <button type="button" className="danger-link" onClick={() => { setCancelReason(""); setCancelOpen(true); }}>{t("finance.invoices.cancelDoc")}</button>
                </>
              ) : null}
              {detail.status === "ISSUED" && canManage ? (
                <button type="button" className="danger-link" onClick={() => { setCancelReason(""); setCancelOpen(true); }}>{t("finance.invoices.cancelDoc")}</button>
              ) : null}
            </footer>
          </aside>
        </div>
      ) : null}

      {issueOpen && detail ? (
        <ConfirmDialog
          title={t("finance.invoices.issueTitle")}
          message={t("finance.invoices.issueHint")}
          confirmLabel={busy ? t("finance.invoices.saving") : t("finance.invoices.issue")}
          cancelLabel={t("finance.invoices.close")}
          confirmDisabled={busy}
          onCancel={() => !busy && setIssueOpen(false)}
          onConfirm={() => void confirmIssue()}
        />
      ) : null}

      {cancelOpen && detail ? (
        <ConfirmDialog
          title={t("finance.invoices.cancelTitle")}
          message={t("finance.invoices.cancelHint")}
          confirmLabel={busy ? t("finance.invoices.saving") : t("finance.invoices.cancelDoc")}
          cancelLabel={t("finance.invoices.close")}
          danger
          confirmDisabled={busy || cancelReason.trim().length < 3}
          onCancel={() => !busy && setCancelOpen(false)}
          onConfirm={() => void confirmCancel()}
        >
          <FormField label={t("finance.invoices.cancelReason")} multiline>
            <textarea value={cancelReason} onChange={(e) => setCancelReason(e.target.value)} rows={3} disabled={busy} />
          </FormField>
        </ConfirmDialog>
      ) : null}
    </article>
  );
}
