import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import {
  CheckCircle2,
  CircleAlert,
  Eye,
  EyeOff,
  History,
  KeyRound,
  LockKeyhole,
  Mail,
  Phone,
  ShieldCheck,
  Store,
  X,
} from "lucide-react";
import ConfirmDialog from "../form/ConfirmDialog";
import FormField from "../form/FormField";
import { AsciiPasswordInput, PasswordInputHints } from "../form/AsciiPasswordInput";
import DateRangePicker from "../form/DateRangePicker";
import { LoadError, SkeletonForm, SkeletonTable } from "../feedback/Skeleton";
import MerchantSaveButton from "./MerchantSaveButton";
import MerchantPagination from "./MerchantPagination";
import { useAsciiPasswordInput } from "../../hooks/useAsciiPasswordInput";
import { useAuth } from "../../contexts/AuthContext";
import { usePermissions } from "../../hooks/usePermissions";
import { formatDateTime } from "../../services/adminFormat";
import {
  ApiError,
  authApi,
  merchantSecurityActivityApi,
  type MerchantSecurityActivityDetail,
  type MerchantSecurityActivityRow,
} from "../../services/api";
import { isPasswordValid, passwordRules } from "../../utils/passwordRules";
import "../../styles/form-fields.css";
import "../../styles/product-management.css";
import "../../styles/order-management.css";
import "../../styles/customer-management.css";

type Props = {
  shopId: number;
  shopName: string;
  notify: (text: string) => void;
  onDirtyChange?: (dirty: boolean) => void;
};

type PasswordFields = { current: string; next: string; confirm: string };
const emptyPasswords = (): PasswordFields => ({ current: "", next: "", confirm: "" });

function actorLabel(row: { actorName: string | null; actorType: string }, unknown: string, system: string) {
  if (row.actorName?.trim()) return row.actorName.trim();
  if (row.actorType === "SYSTEM") return system;
  return unknown;
}

export default function SettingsSecurityPanel({ shopId, shopName, notify, onDirtyChange }: Props) {
  const { t } = useTranslation("merchant");
  const { user, logout } = useAuth();
  const { canInShop, deniedTitle } = usePermissions();
  const canAudit = canInShop("AUDIT_READ");

  const [passwords, setPasswords] = useState<PasswordFields>(emptyPasswords);
  const [showCurrent, setShowCurrent] = useState(false);
  const [showNext, setShowNext] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [confirmChange, setConfirmChange] = useState(false);
  const pendingSubmit = useRef(false);

  const currentField = useAsciiPasswordInput({
    value: passwords.current,
    onValueChange: (value) => setPasswords((prev) => ({ ...prev, current: value })),
  });
  const nextField = useAsciiPasswordInput({
    value: passwords.next,
    onValueChange: (value) => setPasswords((prev) => ({ ...prev, next: value })),
  });
  const confirmField = useAsciiPasswordInput({
    value: passwords.confirm,
    onValueChange: (value) => setPasswords((prev) => ({ ...prev, confirm: value })),
  });
  const rules = useMemo(() => passwordRules(passwords.next), [passwords.next]);

  const dirty = Boolean(passwords.current || passwords.next || passwords.confirm);
  useEffect(() => { onDirtyChange?.(dirty); }, [dirty, onDirtyChange]);
  useEffect(() => () => {
    setPasswords(emptyPasswords());
    setFieldErrors({});
    setFormError(null);
    onDirtyChange?.(false);
  }, [onDirtyChange]);

  const clearPasswords = () => {
    setPasswords(emptyPasswords());
    setFieldErrors({});
    setFormError(null);
    setShowCurrent(false);
    setShowNext(false);
    setShowConfirm(false);
  };

  const validate = () => {
    const next: Record<string, string> = {};
    if (!passwords.current.trim()) next.current = t("security.password.requiredCurrent");
    if (!passwords.next.trim()) next.next = t("security.password.requiredNew");
    else if (!isPasswordValid(passwords.next)) next.next = t("security.password.rules");
    if (!passwords.confirm.trim()) next.confirm = t("security.password.requiredConfirm");
    else if (passwords.next !== passwords.confirm) next.confirm = t("security.password.mismatch");
    if (passwords.current && passwords.next && passwords.current === passwords.next) {
      next.next = t("security.password.sameAsCurrent");
    }
    setFieldErrors(next);
    return Object.keys(next).length === 0;
  };

  const submitPassword = async () => {
    if (saving || pendingSubmit.current) return;
    if (!validate()) return;
    pendingSubmit.current = true;
    setSaving(true);
    setFormError(null);
    try {
      const result = await authApi.changePassword(passwords.current, passwords.next);
      clearPasswords();
      onDirtyChange?.(false);
      notify(result.message || t("security.password.success"));
      await logout();
      window.location.replace(`/login?next=${encodeURIComponent("/merchant/settings?tab=security")}`);
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.code === "INVALID_CREDENTIALS") {
          setFieldErrors({ current: t("security.password.wrongCurrent") });
        } else if (error.fieldErrors?.length) {
          const mapped: Record<string, string> = {};
          for (const row of error.fieldErrors) {
            if (row.field === "currentPassword") mapped.current = row.message;
            else if (row.field === "newPassword") mapped.next = row.message;
            else mapped.next = row.message;
          }
          setFieldErrors(mapped);
        } else {
          setFormError(error.message || t("security.password.failed"));
        }
      } else {
        setFormError(error instanceof Error ? error.message : t("security.password.failed"));
      }
    } finally {
      pendingSubmit.current = false;
      setSaving(false);
      setConfirmChange(false);
    }
  };

  const onPasswordSubmit = (event: FormEvent) => {
    event.preventDefault();
    if (!validate()) return;
    setConfirmChange(true);
  };

  /* ---- shop activity ---- */
  const [action, setAction] = useState("");
  const [outcome, setOutcome] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [page, setPage] = useState(0);
  const [size, setSize] = useState(20);
  const [reload, setReload] = useState(0);
  const [loading, setLoading] = useState(false);
  const [listError, setListError] = useState<string | null>(null);
  const [items, setItems] = useState<MerchantSecurityActivityRow[]>([]);
  const [totalItems, setTotalItems] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [supportedActions, setSupportedActions] = useState<string[]>([]);
  const [detail, setDetail] = useState<MerchantSecurityActivityDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);
  const reqSeq = useRef(0);
  const closeBtnRef = useRef<HTMLButtonElement>(null);
  const returnFocusRef = useRef<HTMLElement | null>(null);

  const loadActivity = useCallback(async () => {
    if (!canAudit) {
      setItems([]);
      setTotalItems(0);
      setTotalPages(0);
      setLoading(false);
      setListError(null);
      return;
    }
    const seq = ++reqSeq.current;
    setLoading(true);
    setListError(null);
    try {
      const row = await merchantSecurityActivityApi.list(shopId, {
        action: action || undefined,
        outcome: outcome || undefined,
        from: from || undefined,
        to: to || undefined,
        page,
        size,
      });
      if (seq !== reqSeq.current) return;
      setItems(row.items);
      setTotalItems(row.totalItems);
      setTotalPages(row.totalPages);
      setSupportedActions(row.supportedActions);
    } catch (error) {
      if (seq !== reqSeq.current) return;
      setItems([]);
      setTotalItems(0);
      setTotalPages(0);
      if (error instanceof ApiError && (error.status === 403 || error.code === "FORBIDDEN")) {
        setListError("restricted");
      } else {
        setListError(error instanceof Error ? error.message : t("security.activity.loadError"));
      }
    } finally {
      if (seq === reqSeq.current) setLoading(false);
    }
  }, [canAudit, shopId, action, outcome, from, to, page, size, reload, t]);

  useEffect(() => { void loadActivity(); }, [loadActivity]);

  const openDetail = async (row: MerchantSecurityActivityRow, source: HTMLElement | null) => {
    returnFocusRef.current = source;
    setDetail(null);
    setDetailError(null);
    setDetailLoading(true);
    try {
      const next = await merchantSecurityActivityApi.detail(shopId, row.id);
      setDetail(next);
      window.setTimeout(() => closeBtnRef.current?.focus(), 0);
    } catch (error) {
      setDetailError(error instanceof Error ? error.message : t("security.activity.detailError"));
    } finally {
      setDetailLoading(false);
    }
  };

  const closeDetail = () => {
    setDetail(null);
    setDetailError(null);
    setDetailLoading(false);
    window.setTimeout(() => returnFocusRef.current?.focus(), 0);
  };

  useEffect(() => {
    if (!detail && !detailLoading && !detailError) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") closeDetail();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [detail, detailLoading, detailError]);

  const emailVerified = typeof user?.emailVerified === "boolean" ? user.emailVerified : null;
  const phoneVerified = typeof user?.phoneVerified === "boolean" ? user.phoneVerified : null;

  return (
    <div className="security-settings">
      <section className="settings-card security-scope-card" aria-label={t("security.side.title")}>
        <header className="security-scope-head">
          <div>
            <h2>{t("security.side.title")}</h2>
            <p>{t("security.side.body")}</p>
          </div>
        </header>
        <ul className="security-scope-grid">
          <li>
            <span className="security-side-icon" aria-hidden="true"><LockKeyhole /></span>
            <span>{t("security.side.pointAccount")}</span>
          </li>
          <li>
            <span className="security-side-icon tone-shop" aria-hidden="true"><ShieldCheck /></span>
            <span>{t("security.side.pointShop")}</span>
          </li>
          <li>
            <span className="security-side-icon tone-muted" aria-hidden="true"><CircleAlert /></span>
            <span>{t("security.side.pointNoDevices")}</span>
          </li>
        </ul>
      </section>

      <section className="settings-card security-account-card">
        <header className="security-section-head">
          <div className="security-section-title">
            <span className="security-section-icon" aria-hidden="true"><LockKeyhole /></span>
            <div>
              <h2>{t("security.account.title")}</h2>
              <p>{t("security.account.subtitle")}</p>
            </div>
          </div>
        </header>

        <div className="security-card-body">
          <p className="security-scope-note">{t("security.account.scopeNote")}</p>

          <div className="security-account-layout">
            <div className="security-account-meta">
              <div className="security-identity-tile">
                <span className="security-identity-icon" aria-hidden="true"><Mail /></span>
                <div className="security-identity-copy">
                  <b>{t("security.account.email")}</b>
                  <small>{user?.email || "—"}</small>
                </div>
                {emailVerified === null ? null : (
                  <em className={emailVerified ? "ok" : "warn"}>
                    {emailVerified ? t("security.account.verified") : t("security.account.unverified")}
                  </em>
                )}
              </div>
              <div className="security-identity-tile">
                <span className="security-identity-icon tone-phone" aria-hidden="true"><Phone /></span>
                <div className="security-identity-copy">
                  <b>{t("security.account.phone")}</b>
                  <small>{user?.phone || "—"}</small>
                </div>
                {phoneVerified === null ? null : (
                  <em className={phoneVerified ? "ok" : "warn"}>
                    {phoneVerified ? t("security.account.verified") : t("security.account.unverified")}
                  </em>
                )}
              </div>
            </div>

            <form className="security-password-form" onSubmit={onPasswordSubmit} noValidate>
            <div className="security-password-head">
              <div>
                <h3><KeyRound aria-hidden />{t("security.password.title")}</h3>
                <p>{t("security.password.hint")}</p>
              </div>
              {dirty ? <span className="security-dirty" role="status">{t("security.password.unsaved")}</span> : null}
            </div>

            <div className="security-password-stack">
              <FormField
                label={t("security.password.current")}
                icon={LockKeyhole}
                error={fieldErrors.current}
                errorId="security-current-error"
                className="flush"
              >
                <AsciiPasswordInput
                  field={currentField}
                  visible={showCurrent}
                  name="currentPassword"
                  autoComplete="current-password"
                  aria-invalid={Boolean(fieldErrors.current)}
                  aria-describedby={fieldErrors.current ? "security-current-error" : undefined}
                />
                <button
                  type="button"
                  className="security-eye"
                  aria-pressed={showCurrent}
                  aria-label={showCurrent ? t("security.password.hide") : t("security.password.show")}
                  onClick={() => setShowCurrent((v) => !v)}
                >
                  {showCurrent ? <EyeOff aria-hidden /> : <Eye aria-hidden />}
                </button>
              </FormField>
              <PasswordInputHints asciiWarning={currentField.asciiWarning} capsLockOn={currentField.capsLockOn} />

              <FormField
                label={t("security.password.next")}
                icon={KeyRound}
                error={fieldErrors.next}
                errorId="security-next-error"
                className="flush"
              >
                <AsciiPasswordInput
                  field={nextField}
                  visible={showNext}
                  name="newPassword"
                  autoComplete="new-password"
                  aria-invalid={Boolean(fieldErrors.next)}
                  aria-describedby={["security-password-rules", fieldErrors.next ? "security-next-error" : ""].filter(Boolean).join(" ") || undefined}
                />
                <button
                  type="button"
                  className="security-eye"
                  aria-pressed={showNext}
                  aria-label={showNext ? t("security.password.hide") : t("security.password.show")}
                  onClick={() => setShowNext((v) => !v)}
                >
                  {showNext ? <EyeOff aria-hidden /> : <Eye aria-hidden />}
                </button>
              </FormField>
              <PasswordInputHints asciiWarning={nextField.asciiWarning} capsLockOn={nextField.capsLockOn} />
              <p id="security-password-rules" className="security-password-rules">
                <i className={rules.length ? "ok" : ""}><CheckCircle2 aria-hidden />{t("security.password.ruleLength")}</i>
                <i className={rules.number ? "ok" : ""}><CheckCircle2 aria-hidden />{t("security.password.ruleNumber")}</i>
                <i className={rules.letter ? "ok" : ""}><CheckCircle2 aria-hidden />{t("security.password.ruleLetter")}</i>
              </p>

              <FormField
                label={t("security.password.confirm")}
                icon={ShieldCheck}
                error={fieldErrors.confirm}
                errorId="security-confirm-error"
                className="flush"
              >
                <AsciiPasswordInput
                  field={confirmField}
                  visible={showConfirm}
                  name="confirmPassword"
                  autoComplete="new-password"
                  aria-invalid={Boolean(fieldErrors.confirm)}
                  aria-describedby={fieldErrors.confirm ? "security-confirm-error" : undefined}
                />
                <button
                  type="button"
                  className="security-eye"
                  aria-pressed={showConfirm}
                  aria-label={showConfirm ? t("security.password.hide") : t("security.password.show")}
                  onClick={() => setShowConfirm((v) => !v)}
                >
                  {showConfirm ? <EyeOff aria-hidden /> : <Eye aria-hidden />}
                </button>
              </FormField>
              <PasswordInputHints asciiWarning={confirmField.asciiWarning} capsLockOn={confirmField.capsLockOn} />
            </div>

            {formError ? <p className="security-form-error" role="alert">{formError}</p> : null}
            <p className="security-session-note">{t("security.password.sessionNote")}</p>
            <div className="security-password-actions">
              <button type="button" className="security-cancel" disabled={saving || !dirty} onClick={clearPasswords}>
                {t("security.password.cancel")}
              </button>
              <MerchantSaveButton
                saving={saving}
                disabled={!dirty}
                label={t("security.password.save")}
                savingLabel={t("security.password.saving")}
                onClick={() => {
                  if (validate()) setConfirmChange(true);
                }}
              />
            </div>
            </form>
          </div>
        </div>
      </section>

      <section className="settings-card security-activity-card">
        <header className="security-section-head">
          <div className="security-section-title">
            <span className="security-section-icon tone-history" aria-hidden="true"><History /></span>
            <div>
              <h2>{t("security.activity.title")}</h2>
              <p>{t("security.activity.subtitle", { shop: shopName || "—" })}</p>
            </div>
          </div>
        </header>

        <div className="security-card-body">
          <p className="security-scope-note">{t("security.activity.scopeNote")}</p>

          {!canAudit ? (
            <div className="security-restricted" role="status">
              <CircleAlert aria-hidden />
              <div>
                <b>{t("security.activity.restrictedTitle")}</b>
                <p>{deniedTitle(false) || t("security.activity.restrictedBody")}</p>
              </div>
            </div>
          ) : (
            <>
              <form
                className="security-filters"
                onSubmit={(event) => {
                  event.preventDefault();
                  setPage(0);
                  setReload((n) => n + 1);
                }}
              >
                <label>
                  <span>{t("security.activity.filterAction")}</span>
                  <select value={action} onChange={(e) => { setAction(e.target.value); setPage(0); }}>
                    <option value="">{t("security.activity.allActions")}</option>
                    {(supportedActions.length
                      ? supportedActions
                      : ["SHOP_MEMBER_ROLE_ASSIGNED", "SHOP_MEMBER_REMOVED", "SHOP_SUSPENDED", "SHOP_REACTIVATED", "SHOP_PLAN_CHANGED"]
                    ).map((code) => (
                      <option key={code} value={code}>{t(`security.activity.actions.${code}`, { defaultValue: code })}</option>
                    ))}
                  </select>
                </label>
                <label>
                  <span>{t("security.activity.filterOutcome")}</span>
                  <select value={outcome} onChange={(e) => { setOutcome(e.target.value); setPage(0); }}>
                    <option value="">{t("security.activity.allOutcomes")}</option>
                    <option value="SUCCESS">{t("security.activity.outcomes.SUCCESS")}</option>
                    <option value="FAILURE">{t("security.activity.outcomes.FAILURE")}</option>
                    <option value="DENIED">{t("security.activity.outcomes.DENIED")}</option>
                  </select>
                </label>
                <DateRangePicker
                  portal
                  label={t("security.activity.filterDate")}
                  from={from}
                  to={to}
                  onChange={(range) => {
                    setFrom(range.from);
                    setTo(range.to);
                    setPage(0);
                  }}
                />
                <button type="submit">{t("security.activity.applyFilters")}</button>
              </form>

              {loading ? <SkeletonTable rows={6} /> : null}
              {!loading && listError === "restricted" ? (
                <div className="security-restricted" role="status">
                  <CircleAlert aria-hidden />
                  <div>
                    <b>{t("security.activity.restrictedTitle")}</b>
                    <p>{t("security.activity.restrictedBody")}</p>
                  </div>
                </div>
              ) : null}
              {!loading && listError && listError !== "restricted" ? (
                <LoadError message={listError} onRetry={() => setReload((n) => n + 1)} />
              ) : null}
              {!loading && !listError && items.length === 0 ? (
                <div className="security-empty">
                  <History aria-hidden />
                  <p>{action ? t("security.activity.emptyFiltered") : t("security.activity.empty")}</p>
                </div>
              ) : null}
              {!loading && !listError && items.length > 0 ? (
                <>
                  <div className="pm-table-wrap security-table-wrap">
                    <table>
                      <thead>
                        <tr>
                          <th>{t("security.activity.colTime")}</th>
                          <th>{t("security.activity.colActor")}</th>
                          <th>{t("security.activity.colAction")}</th>
                          <th>{t("security.activity.colOutcome")}</th>
                          <th>{t("security.activity.colSummary")}</th>
                        </tr>
                      </thead>
                      <tbody>
                        {items.map((row) => (
                          <tr
                            key={row.id}
                            className="security-activity-row"
                            tabIndex={0}
                            onClick={(event) => void openDetail(row, event.currentTarget)}
                            onKeyDown={(event) => {
                              if (event.key === "Enter" || event.key === " ") {
                                event.preventDefault();
                                void openDetail(row, event.currentTarget);
                              }
                            }}
                          >
                            <td className="num">{formatDateTime(row.occurredAt)}</td>
                            <td>{actorLabel(row, t("security.activity.actorUnknown"), t("security.activity.actorSystem"))}</td>
                            <td>{t(`security.activity.actions.${row.action}`, { defaultValue: row.action })}</td>
                            <td>
                              <span className={`om-pill ${row.outcome === "SUCCESS" ? "delivered" : row.outcome === "DENIED" ? "waiting" : "cancelled"}`}>
                                {t(`security.activity.outcomes.${row.outcome}`, { defaultValue: row.outcome })}
                              </span>
                            </td>
                            <td>{row.summary || "—"}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                  <MerchantPagination
                    page={page}
                    pageCount={Math.max(1, totalPages)}
                    size={size}
                    total={totalItems}
                    onPageChange={setPage}
                    onSizeChange={(next) => { setSize(next); setPage(0); }}
                  />
                </>
              ) : null}
            </>
          )}
        </div>
      </section>

      {confirmChange ? (
        <ConfirmDialog
          title={t("security.password.confirmTitle")}
          message={t("security.password.confirmMessage")}
          confirmLabel={t("security.password.confirmSubmit")}
          cancelLabel={t("security.password.cancel")}
          danger
          onConfirm={() => { void submitPassword(); }}
          onCancel={() => setConfirmChange(false)}
        />
      ) : null}

      {(detail || detailLoading || detailError) ? createPortal(
        <div
          className="om-drawer-overlay cm-drawer-overlay om-order-overlay"
          role="presentation"
          onMouseDown={(event) => {
            if (event.target === event.currentTarget) closeDetail();
          }}
        >
          <aside
            className="om-drawer cm-detail sh-detail om-detail team-drawer security-activity-drawer"
            role="dialog"
            aria-modal="true"
            aria-labelledby="security-activity-title"
          >
            <header className="cm-detail-head">
              <div className="cm-avatar om-avatar tone-waiting" aria-hidden="true"><History /></div>
              <div className="cm-detail-id">
                <h2 id="security-activity-title">{t("security.activity.detailTitle")}</h2>
                <div className="cm-detail-meta">
                  <span className="om-muted">{detail ? formatDateTime(detail.occurredAt) : "—"}</span>
                </div>
              </div>
              <button type="button" className="om-drawer-close" aria-label={t("security.activity.close")} ref={closeBtnRef} onClick={closeDetail}>
                <X aria-hidden />
              </button>
            </header>
            <div className="om-drawer-body cm-detail-body">
              {detailLoading ? <SkeletonForm fields={5} /> : null}
              {detailError ? <LoadError message={detailError} onRetry={() => detail && void openDetail(detail, returnFocusRef.current)} /> : null}
              {detail && !detailLoading ? (
                <dl className="cm-contact">
                  <div className="cm-contact-row">
                    <span className="cm-icon-chip" aria-hidden="true"><History /></span>
                    <dt>{t("security.activity.colAction")}</dt>
                    <dd>{t(`security.activity.actions.${detail.action}`, { defaultValue: detail.action })}</dd>
                  </div>
                  <div className="cm-contact-row">
                    <span className="cm-icon-chip" aria-hidden="true"><ShieldCheck /></span>
                    <dt>{t("security.activity.colOutcome")}</dt>
                    <dd>{t(`security.activity.outcomes.${detail.outcome}`, { defaultValue: detail.outcome })}</dd>
                  </div>
                  <div className="cm-contact-row">
                    <span className="cm-icon-chip" aria-hidden="true"><Store /></span>
                    <dt>{t("security.activity.colActor")}</dt>
                    <dd>{actorLabel(detail, t("security.activity.actorUnknown"), t("security.activity.actorSystem"))}</dd>
                  </div>
                  <div className="cm-contact-row">
                    <span className="cm-icon-chip" aria-hidden="true"><History /></span>
                    <dt>{t("security.activity.colSummary")}</dt>
                    <dd>{detail.summary || "—"}</dd>
                  </div>
                  {detail.before || detail.after ? (
                    <section className="om-section">
                      <h3>{t("security.activity.changes")}</h3>
                      <div className="security-diff">
                        <div>
                          <b>{t("security.activity.before")}</b>
                          <pre>{detail.before ? JSON.stringify(detail.before, null, 2) : "—"}</pre>
                        </div>
                        <div>
                          <b>{t("security.activity.after")}</b>
                          <pre>{detail.after ? JSON.stringify(detail.after, null, 2) : "—"}</pre>
                        </div>
                      </div>
                    </section>
                  ) : (
                    <p className="om-note">{t("security.activity.noDiff")}</p>
                  )}
                </dl>
              ) : null}
            </div>
            <footer className="om-drawer-actions">
              <button type="button" onClick={closeDetail}>{t("security.activity.close")}</button>
            </footer>
          </aside>
        </div>,
        document.body,
      ) : null}
    </div>
  );
}
