import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  AlertTriangle,
  Banknote,
  Building2,
  CheckCircle2,
  CircleX,
  Clock3,
  Plus,
  RefreshCw,
  Search,
  WalletCards,
} from "lucide-react";
import AdminHeader from "../components/AdminHeader";
import AdminSidebar from "../components/AdminSidebar";
import ConfirmDialog from "../components/form/ConfirmDialog";
import { LoadError, SkeletonTable } from "../components/feedback/Skeleton";
import { Drawer, Pager, Pill, statusTone } from "../components/admin/PlatformBits";
import { useAuth } from "../contexts/AuthContext";
import { usePermissions } from "../hooks/usePermissions";
import { hasPlatformAccess } from "../services/permissions";
import { formatDateTime } from "../services/adminFormat";
import {
  adminPayoutApi,
  moneyValue,
  type ShopWithdrawal,
  type ShopWithdrawalStatus,
} from "../services/api";
import { formatBaht } from "../components/cart/cartFormat";
import "../styles/admin-dashboard.css";
import "../styles/admin-platform.css";
import "../styles/admin-form-controls.css";
import "../styles/admin-payouts.css";

const STATUSES: ShopWithdrawalStatus[] = [
  "REQUESTED",
  "APPROVED",
  "PROCESSING",
  "NEEDS_REVIEW",
  "PAID",
  "REJECTED",
  "CANCELLED",
];

function payoutTone(status: string): "ok" | "warn" | "bad" | "info" | "muted" {
  if (status === "PROCESSING" || status === "APPROVED") return "info";
  if (status === "NEEDS_REVIEW") return "warn";
  return statusTone(status);
}

export default function AdminPayoutsPage() {
  const { t, i18n } = useTranslation("admin");
  const { user, loading } = useAuth();
  const localeTag = i18n.language?.startsWith("en") ? "en-US" : i18n.language?.startsWith("zh") ? "zh-CN" : "th-TH";
  const { can, deniedTitle } = usePermissions();
  const canRead = can("PLATFORM_PAYOUT_READ");
  const canReview = can("PLATFORM_PAYOUT_REVIEW");
  const canSettle = can("PLATFORM_PAYOUT_SETTLE");
  const canCredit = can("PLATFORM_PAYOUT_CREDIT");

  const [status, setStatus] = useState<string>("REQUESTED");
  const [q, setQ] = useState("");
  const [items, setItems] = useState<ShopWithdrawal[]>([]);
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [totalItems, setTotalItems] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [listLoading, setListLoading] = useState(true);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");
  const [selected, setSelected] = useState<ShopWithdrawal | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
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
  const detailReq = useRef(0);

  const loadList = useCallback(() => {
    if (!canRead) return;
    const id = ++req.current;
    setListLoading(true);
    setError("");
    adminPayoutApi.list({ status: status || undefined, page, size })
      .then((data) => {
        if (req.current !== id) return;
        setItems(data.items);
        setTotalItems(data.totalItems);
        setTotalPages(Math.max(1, data.totalPages));
      })
      .catch(() => {
        if (req.current === id) {
          setItems([]);
          setError(t("payouts.loadFailed"));
        }
      })
      .finally(() => {
        if (req.current === id) setListLoading(false);
      });
  }, [canRead, status, page, size, t]);

  useEffect(() => { loadList(); }, [loadList, reload]);

  useEffect(() => {
    if (!notice) return;
    const timer = window.setTimeout(() => setNotice(""), 2200);
    return () => window.clearTimeout(timer);
  }, [notice]);

  const openDetail = async (row: ShopWithdrawal) => {
    setSelected(row);
    setDetailLoading(true);
    const id = ++detailReq.current;
    try {
      const full = await adminPayoutApi.get(row.id);
      if (detailReq.current === id) setSelected(full);
    } catch {
      if (detailReq.current === id) setSelected(row);
    } finally {
      if (detailReq.current === id) setDetailLoading(false);
    }
  };

  const closeDetail = () => {
    setSelected(null);
    setDetailLoading(false);
  };

  const filtered = useMemo(() => {
    const needle = q.trim().toLowerCase();
    if (!needle) return items;
    return items.filter((row) => {
      const hay = `${row.shopId} ${row.shopName ?? ""} ${row.accountNumberMasked} ${row.accountBankName ?? ""} ${row.id}`.toLowerCase();
      return hay.includes(needle);
    });
  }, [items, q]);

  if (loading) return <main className="auth-loading">{t("payouts.checkingAccess", { defaultValue: "กำลังตรวจสอบสิทธิ์…" })}</main>;
  if (!user || !hasPlatformAccess(user)) {
    window.location.replace("/login?next=/admin/payouts");
    return null;
  }
  if (!canRead) {
    return (
      <main className="admin-shell platform-shell">
        <AdminHeader user={user} />
        <AdminSidebar active="payouts" className="platform-sidebar" />
        <div className="admin-content platform-content">
          <section className="ap-card"><p className="ap-empty" title={deniedTitle(false)}>{t("payouts.noPermission")}</p></section>
        </div>
      </main>
    );
  }

  const refresh = () => setReload((n) => n + 1);

  const act = async (fn: () => Promise<unknown>, ok: string) => {
    setBusy(true);
    try {
      await fn();
      setNotice(ok);
      closeDetail();
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
    <main className="admin-shell platform-shell">
      <AdminHeader user={user} />
      <AdminSidebar active="payouts" className="platform-sidebar" />
      <div className="admin-content platform-content payouts-page">
        <section className="payouts-hero" aria-labelledby="payouts-hero-title">
          <div className="payouts-hero-main">
            <span className="payouts-hero-icon" aria-hidden="true"><WalletCards /></span>
            <div className="payouts-hero-copy">
              <h1 id="payouts-hero-title">{t("payouts.title")}</h1>
              <p>{t("payouts.subtitle")}</p>
              <p className="payouts-hero-policy">
                <AlertTriangle aria-hidden />
                <span>{t("payouts.policy")}</span>
              </p>
            </div>
          </div>
          <div className="payouts-hero-aside">
            <div className="payouts-hero-metric" aria-live="polite">
              <span className="payouts-hero-metric-label">
                <Clock3 aria-hidden />
                {status ? t(`payouts.status.${status}`) : t("payouts.queueCount")}
              </span>
              <strong className="num">{listLoading ? "—" : totalItems.toLocaleString("en-US")}</strong>
              <small>{t("payouts.queueHint")}</small>
            </div>
            <div className="payouts-heading-actions">
              <button type="button" className="ap-ghost" disabled={listLoading} onClick={refresh}>
                <RefreshCw aria-hidden className={listLoading ? "is-spin" : undefined} />
                {t("payouts.refresh")}
              </button>
              {canCredit ? (
                <button type="button" className="ap-primary" onClick={() => setCreditOpen(true)}>
                  <Plus aria-hidden />{t("payouts.credit")}
                </button>
              ) : null}
            </div>
          </div>
        </section>

        <section className="ap-card payouts-filters-card">
          <div className="payouts-filters">
            <label className="payouts-search">
              <Search aria-hidden />
              <input
                value={q}
                onChange={(e) => setQ(e.target.value)}
                placeholder={t("payouts.searchPlaceholder")}
                aria-label={t("payouts.searchPlaceholder")}
              />
            </label>
            <div className="payouts-status-tabs" role="tablist" aria-label={t("payouts.statusLabel")}>
              <button
                type="button"
                role="tab"
                aria-selected={status === ""}
                className={status === "" ? "is-active" : undefined}
                onClick={() => { setStatus(""); setPage(0); }}
              >
                {t("payouts.statusAll")}
              </button>
              {STATUSES.map((code) => (
                <button
                  key={code}
                  type="button"
                  role="tab"
                  aria-selected={status === code}
                  className={status === code ? `is-active is-${payoutTone(code)}` : undefined}
                  onClick={() => { setStatus(code); setPage(0); }}
                >
                  {t(`payouts.status.${code}`)}
                </button>
              ))}
            </div>
          </div>
        </section>

        <section className="ap-card">
          <header className="ap-card-head">
            <h2>{t("payouts.queueTitle")}</h2>
            <span className="payouts-filter-hint">
              {status ? t(`payouts.status.${status}`) : t("payouts.statusAll")}
              {q.trim() ? ` · ${t("payouts.searching")}` : ""}
            </span>
          </header>

          {error ? <LoadError message={error} onRetry={refresh} /> : null}
          {listLoading && !items.length ? <SkeletonTable rows={8} cols={6} /> : null}

          {!listLoading && !error && filtered.length === 0 ? (
            <div className="payouts-empty">
              <WalletCards aria-hidden />
              <b>{t("payouts.emptyTitle")}</b>
              <p>{q.trim() ? t("payouts.emptySearch") : t("payouts.emptyBody")}</p>
            </div>
          ) : null}

          {!error && filtered.length > 0 ? (
            <div className="ap-table-scroll">
              <table className="ap-table payouts-table">
                <thead>
                  <tr>
                    <th>{t("payouts.shop")}</th>
                    <th className="right">{t("payouts.amount")}</th>
                    <th>{t("payouts.account")}</th>
                    <th>{t("payouts.statusLabel")}</th>
                    <th>{t("payouts.requested")}</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  {filtered.map((row) => (
                    <tr
                      key={row.id}
                      className={`clickable${selected?.id === row.id ? " selected" : ""}`}
                      tabIndex={0}
                      onClick={() => void openDetail(row)}
                      onKeyDown={(event) => {
                        if (event.key === "Enter" || event.key === " ") {
                          event.preventDefault();
                          void openDetail(row);
                        }
                      }}
                    >
                      <td>
                        <b>{row.shopName || t("payouts.unnamedShop")}</b>
                        <small>#{row.shopId}</small>
                      </td>
                      <td className="right nowrap">
                        <strong className="payouts-amount">{formatBaht(row.amount, localeTag)}</strong>
                      </td>
                      <td>
                        <span className="payouts-account">
                          {row.accountBankName || row.accountProvider}
                        </span>
                        <small>{row.accountNumberMasked}</small>
                      </td>
                      <td><Pill tone={payoutTone(row.status)}>{t(`payouts.status.${row.status}`)}</Pill></td>
                      <td className="nowrap">{formatDateTime(row.requestedAt)}</td>
                      <td className="right">
                        <button
                          type="button"
                          className="payouts-open-btn"
                          onClick={(event) => {
                            event.stopPropagation();
                            void openDetail(row);
                          }}
                        >
                          {t("payouts.open")}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : null}

          {!error ? (
            <Pager
              page={page}
              size={size}
              totalItems={totalItems}
              totalPages={totalPages}
              itemCount={items.length}
              loading={listLoading}
              onPage={setPage}
              onSize={(next) => { setSize(next); setPage(0); }}
            />
          ) : null}
        </section>
      </div>

      {selected ? (
        <Drawer
          title={selected.shopName || t("payouts.unnamedShop")}
          subtitle={
            <>
              <Pill tone={payoutTone(selected.status)}>{t(`payouts.status.${selected.status}`)}</Pill>
              <span className="om-muted">#{selected.shopId} · {t("payouts.detail")}</span>
            </>
          }
          avatarFallback={<WalletCards />}
          onClose={closeDetail}
          className="payouts-drawer"
          footer={(
            <div className="payouts-drawer-actions">
              {canReview && selected.status === "REQUESTED" ? (
                <>
                  <button type="button" className="ap-primary" disabled={busy || detailLoading} onClick={() => void act(() => adminPayoutApi.approve(selected.id), t("payouts.actionDone"))}>
                    <CheckCircle2 aria-hidden />{t("payouts.approve")}
                  </button>
                  <button type="button" className="ap-danger" disabled={busy || detailLoading} onClick={() => { setRejectReason(""); setRejectOpen(true); }}>
                    <CircleX aria-hidden />{t("payouts.reject")}
                  </button>
                </>
              ) : null}
              {canSettle && selected.status === "APPROVED" ? (
                <button type="button" className="ap-primary" disabled={busy || detailLoading} onClick={() => void act(() => adminPayoutApi.processing(selected.id), t("payouts.actionDone"))}>
                  <Clock3 aria-hidden />{t("payouts.processing")}
                </button>
              ) : null}
              {canSettle && (selected.status === "APPROVED" || selected.status === "PROCESSING" || selected.status === "NEEDS_REVIEW") ? (
                <button type="button" className="ap-primary" disabled={busy || detailLoading} onClick={() => { setPaidRef(""); setPaidDate(""); setPaidExt(""); setPaidOpen(true); }}>
                  <Banknote aria-hidden />{t("payouts.markPaid")}
                </button>
              ) : null}
              {canSettle && (selected.status === "APPROVED" || selected.status === "PROCESSING") ? (
                <button type="button" className="ap-ghost" disabled={busy || detailLoading} onClick={() => void act(() => adminPayoutApi.needsReview(selected.id, t("payouts.defaultReview")), t("payouts.actionDone"))}>
                  <AlertTriangle aria-hidden />{t("payouts.needsReview")}
                </button>
              ) : null}
              <button type="button" className="ap-ghost" onClick={closeDetail}>{t("payouts.close")}</button>
            </div>
          )}
        >
          {detailLoading ? <p className="payouts-detail-loading">{t("payouts.loadingDetail")}</p> : null}
          <div className="payouts-detail-hero">
            <div>
              <small>{t("payouts.amount")}</small>
              <b>{formatBaht(selected.amount, localeTag)}</b>
            </div>
            <Pill tone={payoutTone(selected.status)}>{t(`payouts.status.${selected.status}`)}</Pill>
          </div>

          <dl className="payouts-detail-grid">
            <div>
              <dt><Building2 aria-hidden />{t("payouts.shop")}</dt>
              <dd>{selected.shopName || t("payouts.unnamedShop")}<small>#{selected.shopId}</small></dd>
            </div>
            <div>
              <dt><Banknote aria-hidden />{t("payouts.account")}</dt>
              <dd>
                {selected.accountName || "—"}
                <small>
                  {[selected.accountBankName || selected.accountProvider, selected.accountNumberMasked]
                    .filter(Boolean)
                    .join(" · ")}
                </small>
                {selected.accountPromptPayMasked ? <small>PromptPay {selected.accountPromptPayMasked}</small> : null}
              </dd>
            </div>
            <div>
              <dt><Clock3 aria-hidden />{t("payouts.requested")}</dt>
              <dd>{formatDateTime(selected.requestedAt)}</dd>
            </div>
            {selected.reviewedAt ? (
              <div>
                <dt>{t("payouts.reviewedAt")}</dt>
                <dd>{formatDateTime(selected.reviewedAt)}</dd>
              </div>
            ) : null}
            {selected.rejectReason ? (
              <div className="span-2">
                <dt>{t("payouts.rejectReason")}</dt>
                <dd>{selected.rejectReason}</dd>
              </div>
            ) : null}
            {selected.paidAt ? (
              <div>
                <dt>{t("payouts.paidAt")}</dt>
                <dd>{formatDateTime(selected.paidAt)}</dd>
              </div>
            ) : null}
            {selected.transferReference ? (
              <div>
                <dt>{t("payouts.transferRef")}</dt>
                <dd>{selected.transferReference}</dd>
              </div>
            ) : null}
            {selected.transferDate ? (
              <div>
                <dt>{t("payouts.transferDate")}</dt>
                <dd>{selected.transferDate}</dd>
              </div>
            ) : null}
          </dl>

          {selected.events && selected.events.length > 0 ? (
            <section className="payouts-timeline" aria-label={t("payouts.timeline")}>
              <h3>{t("payouts.timeline")}</h3>
              <ol>
                {selected.events.map((event, index) => (
                  <li key={`${event.createdAt}-${index}`}>
                    <span className="payouts-timeline-dot" aria-hidden="true" />
                    <div>
                      <b>{event.toStatus ? t(`payouts.status.${event.toStatus}`, { defaultValue: event.toStatus }) : event.eventCode}</b>
                      <small>{formatDateTime(event.createdAt)}</small>
                      {event.note ? <p>{event.note}</p> : null}
                    </div>
                  </li>
                ))}
              </ol>
            </section>
          ) : null}
        </Drawer>
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
          <label className="payouts-field">
            <span>{t("payouts.rejectReason")}</span>
            <textarea value={rejectReason} onChange={(e) => setRejectReason(e.target.value)} rows={3} />
          </label>
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
          <label className="payouts-field">
            <span>{t("payouts.transferRef")}</span>
            <input value={paidRef} onChange={(e) => setPaidRef(e.target.value)} />
          </label>
          <label className="payouts-field">
            <span>{t("payouts.transferDate")}</span>
            <input type="date" value={paidDate} onChange={(e) => setPaidDate(e.target.value)} />
          </label>
          <label className="payouts-field">
            <span>{t("payouts.externalKey")}</span>
            <input value={paidExt} onChange={(e) => setPaidExt(e.target.value)} />
          </label>
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
          <label className="payouts-field">
            <span>{t("payouts.shopId")}</span>
            <input value={creditShop} onChange={(e) => setCreditShop(e.target.value)} />
          </label>
          <label className="payouts-field">
            <span>{t("payouts.amount")}</span>
            <input value={creditAmount} onChange={(e) => setCreditAmount(e.target.value)} />
          </label>
        </ConfirmDialog>
      ) : null}

      {notice ? <div className="admin-toast" role="status">{notice}</div> : null}
    </main>
  );
}
