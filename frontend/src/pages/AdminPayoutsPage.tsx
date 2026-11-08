import { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { WalletCards } from "lucide-react";
import AdminHeader from "../components/AdminHeader";
import AdminSidebar from "../components/AdminSidebar";
import ConfirmDialog from "../components/form/ConfirmDialog";
import { useAuth } from "../contexts/AuthContext";
import { usePermissions } from "../hooks/usePermissions";
import { hasPlatformAccess } from "../services/permissions";
import { formatDateTime } from "../services/adminFormat";
import { adminPayoutApi, moneyValue, type ShopWithdrawal } from "../services/api";
import { formatBaht } from "../components/cart/cartFormat";
import "../styles/admin-dashboard.css";
import "../styles/admin-platform.css";

export default function AdminPayoutsPage() {
  const { t, i18n } = useTranslation("admin");
  const { user, loading } = useAuth();
  const localeTag = i18n.language?.startsWith("en") ? "en-US" : i18n.language?.startsWith("zh") ? "zh-CN" : "th-TH";
  const { can } = usePermissions();
  const canRead = can("PLATFORM_PAYOUT_READ");
  const canReview = can("PLATFORM_PAYOUT_REVIEW");
  const canSettle = can("PLATFORM_PAYOUT_SETTLE");
  const canCredit = can("PLATFORM_PAYOUT_CREDIT");

  const [status, setStatus] = useState("REQUESTED");
  const [items, setItems] = useState<ShopWithdrawal[]>([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [selected, setSelected] = useState<ShopWithdrawal | null>(null);
  const [busy, setBusy] = useState(false);
  const [rejectOpen, setRejectOpen] = useState(false);
  const [rejectReason, setRejectReason] = useState("");
  const [paidOpen, setPaidOpen] = useState(false);
  const [paidRef, setPaidRef] = useState("");
  const [paidDate, setPaidDate] = useState("");
  const [paidExt, setPaidExt] = useState("");
  const [creditOpen, setCreditOpen] = useState(false);
  const [creditShop, setCreditShop] = useState("");
  const [creditAmount, setCreditAmount] = useState("");
  const [reload, setReload] = useState(0);
  const req = useRef(0);

  useEffect(() => {
    if (!canRead) return;
    const id = ++req.current;
    setError("");
    adminPayoutApi.list({ status: status || undefined, page, size: 20 })
      .then((data) => {
        if (req.current !== id) return;
        setItems(data.items);
        setTotalPages(Math.max(1, data.totalPages));
      })
      .catch(() => {
        if (req.current === id) setError(t("payouts.loadFailed"));
      });
  }, [canRead, status, page, reload, t]);

  if (loading) return null;
  if (!user || !hasPlatformAccess(user) || !canRead) {
    return <main className="admin-shell"><p>{t("payouts.noPermission")}</p></main>;
  }

  const refresh = () => setReload((n) => n + 1);

  const act = async (fn: () => Promise<unknown>, ok: string) => {
    setBusy(true);
    try {
      await fn();
      setNotice(ok);
      setSelected(null);
      setRejectOpen(false);
      setPaidOpen(false);
      setCreditOpen(false);
      refresh();
    } catch {
      setNotice(t("payouts.actionFailed"));
    } finally {
      setBusy(false);
    }
  };

  return (
    <main className="admin-shell">
      <AdminHeader user={user} />
      <AdminSidebar active="payouts" />
      <section className="admin-content">
        <header className="admin-heading">
          <div>
            <h1><WalletCards />{t("payouts.title")}</h1>
            <p>{t("payouts.subtitle")}</p>
          </div>
          {canCredit ? <button type="button" className="primary" onClick={() => setCreditOpen(true)}>{t("payouts.credit")}</button> : null}
        </header>
        <p className="admin-note">{t("payouts.policy")}</p>
        <div className="admin-toolbar">
          <select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}>
            <option value="">{t("payouts.statusAll")}</option>
            {["REQUESTED", "APPROVED", "PROCESSING", "NEEDS_REVIEW", "PAID", "REJECTED", "CANCELLED"].map((s) => (
              <option key={s} value={s}>{t(`payouts.status.${s}`)}</option>
            ))}
          </select>
        </div>
        {error ? <p className="admin-error">{error}</p> : null}
        <table className="admin-table">
          <thead>
            <tr>
              <th>{t("payouts.shop")}</th>
              <th>{t("payouts.amount")}</th>
              <th>{t("payouts.account")}</th>
              <th>{t("payouts.statusLabel")}</th>
              <th>{t("payouts.requested")}</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {items.map((row) => (
              <tr key={row.id}>
                <td>#{row.shopId} {row.shopName}</td>
                <td>{formatBaht(row.amount, localeTag)}</td>
                <td>{row.accountNumberMasked}</td>
                <td>{t(`payouts.status.${row.status}`)}</td>
                <td>{formatDateTime(row.requestedAt)}</td>
                <td><button type="button" onClick={() => setSelected(row)}>{t("payouts.open")}</button></td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="admin-pager">
          <button type="button" disabled={page <= 0} onClick={() => setPage((p) => p - 1)}>{t("payouts.prev")}</button>
          <span>{page + 1} / {totalPages}</span>
          <button type="button" disabled={page + 1 >= totalPages} onClick={() => setPage((p) => p + 1)}>{t("payouts.next")}</button>
        </div>
      </section>

      {selected ? (
        <ConfirmDialog
          title={t("payouts.detail")}
          message={`${t(`payouts.status.${selected.status}`)} · ${formatBaht(selected.amount, localeTag)}`}
          confirmLabel={t("payouts.close")}
          cancelLabel={t("payouts.close")}
          onCancel={() => setSelected(null)}
          onConfirm={() => setSelected(null)}
        >
          <p>{t("payouts.account")}: {selected.accountNumberMasked}</p>
          <div className="admin-actions">
            {canReview && selected.status === "REQUESTED" ? (
              <>
                <button type="button" disabled={busy} onClick={() => void act(() => adminPayoutApi.approve(selected.id), t("payouts.actionDone"))}>{t("payouts.approve")}</button>
                <button type="button" disabled={busy} onClick={() => { setRejectReason(""); setRejectOpen(true); }}>{t("payouts.reject")}</button>
              </>
            ) : null}
            {canSettle && selected.status === "APPROVED" ? (
              <button type="button" disabled={busy} onClick={() => void act(() => adminPayoutApi.processing(selected.id), t("payouts.actionDone"))}>{t("payouts.processing")}</button>
            ) : null}
            {canSettle && (selected.status === "APPROVED" || selected.status === "PROCESSING" || selected.status === "NEEDS_REVIEW") ? (
              <button type="button" disabled={busy} onClick={() => { setPaidRef(""); setPaidDate(""); setPaidExt(""); setPaidOpen(true); }}>{t("payouts.markPaid")}</button>
            ) : null}
            {canSettle && (selected.status === "APPROVED" || selected.status === "PROCESSING") ? (
              <button type="button" disabled={busy} onClick={() => void act(() => adminPayoutApi.needsReview(selected.id, t("payouts.defaultReview")), t("payouts.actionDone"))}>{t("payouts.needsReview")}</button>
            ) : null}
          </div>
        </ConfirmDialog>
      ) : null}

      {rejectOpen && selected ? (
        <ConfirmDialog
          title={t("payouts.reject")}
          message={t("payouts.rejectHint")}
          confirmLabel={t("payouts.confirm")}
          cancelLabel={t("payouts.close")}
          confirmDisabled={busy || rejectReason.trim().length < 3}
          danger
          onCancel={() => setRejectOpen(false)}
          onConfirm={() => void act(() => adminPayoutApi.reject(selected.id, rejectReason.trim()), t("payouts.actionDone"))}
        >
          <textarea value={rejectReason} onChange={(e) => setRejectReason(e.target.value)} rows={3} />
        </ConfirmDialog>
      ) : null}

      {paidOpen && selected ? (
        <ConfirmDialog
          title={t("payouts.markPaid")}
          message={t("payouts.paidHint")}
          confirmLabel={busy ? t("payouts.saving") : t("payouts.confirm")}
          cancelLabel={t("payouts.close")}
          confirmDisabled={busy || !paidRef.trim() || !paidDate}
          onCancel={() => setPaidOpen(false)}
          onConfirm={() => void act(() => adminPayoutApi.paid(selected.id, {
            transferReference: paidRef.trim(),
            transferDate: paidDate,
            transferAmount: moneyValue(selected.amount),
            externalTransferKey: paidExt.trim() || undefined,
          }), t("payouts.actionDone"))}
        >
          <label>{t("payouts.transferRef")}<input value={paidRef} onChange={(e) => setPaidRef(e.target.value)} /></label>
          <label>{t("payouts.transferDate")}<input type="date" value={paidDate} onChange={(e) => setPaidDate(e.target.value)} /></label>
          <label>{t("payouts.externalKey")}<input value={paidExt} onChange={(e) => setPaidExt(e.target.value)} /></label>
        </ConfirmDialog>
      ) : null}

      {creditOpen ? (
        <ConfirmDialog
          title={t("payouts.credit")}
          message={t("payouts.creditHint")}
          confirmLabel={busy ? t("payouts.saving") : t("payouts.confirm")}
          cancelLabel={t("payouts.close")}
          confirmDisabled={busy || !creditShop || !creditAmount}
          onCancel={() => setCreditOpen(false)}
          onConfirm={() => void act(() => adminPayoutApi.credit(Number(creditShop), {
            amount: Number(creditAmount),
            currency: "THB",
            idempotencyKey: `admin-credit-${creditShop}-${crypto.randomUUID()}`,
            note: "admin manual platform holding",
            sourceReference: "ADMIN_UI",
          }), t("payouts.creditDone"))}
        >
          <label>{t("payouts.shopId")}<input value={creditShop} onChange={(e) => setCreditShop(e.target.value)} /></label>
          <label>{t("payouts.amount")}<input value={creditAmount} onChange={(e) => setCreditAmount(e.target.value)} /></label>
        </ConfirmDialog>
      ) : null}

      {notice ? <div className="admin-toast" role="status">{notice}</div> : null}
    </main>
  );
}
