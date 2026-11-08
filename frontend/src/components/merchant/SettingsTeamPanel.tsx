import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { createPortal } from "react-dom";
import { useTranslation } from "react-i18next";
import { CalendarDays, Copy, History, Mail, MailPlus, RefreshCw, Search, Shield, UserMinus, UserRound, UsersRound, X } from "lucide-react";
import ConfirmDialog from "../form/ConfirmDialog";
import SearchableSelect from "../SearchableSelect";
import { LoadError, Skeleton, SkeletonTable } from "../feedback/Skeleton";
import MerchantPagination from "./MerchantPagination";
import MerchantSaveButton from "./MerchantSaveButton";
import { usePermissions } from "../../hooks/usePermissions";
import {
  ApiError,
  merchantTeamApi,
  type TeamInvitationCreated,
  type TeamInvitationSummary,
  type TeamMemberDetail,
  type TeamMemberSummary,
  type TeamPermissionGroup,
  type TeamRoleCatalog,
  type TeamRoleOption,
} from "../../services/api";
import "../../styles/product-management.css";
import "../../styles/order-management.css";
import "../../styles/customer-management.css";
import "../../styles/form-fields.css";

type Props = {
  shopId: number;
  notify: (text: string) => void;
  onDirtyChange?: (dirty: boolean) => void;
};

type Section = "members" | "invitations";
type ConfirmKind =
  | { kind: "invite" }
  | { kind: "role"; userId: string; role: string }
  | { kind: "remove"; userId: string; name: string }
  | { kind: "revoke"; id: string; email: string }
  | null;

function displayName(m: { firstName: string | null; lastName: string | null; email: string }) {
  const name = [m.firstName, m.lastName].filter(Boolean).join(" ").trim();
  return name || m.email;
}

function PermissionGroups({ groups, t }: { groups: TeamPermissionGroup[]; t: (k: string, o?: Record<string, unknown>) => string }) {
  if (!groups.length) return <p className="om-note-text">{t("team.noPermissions", { defaultValue: "—" })}</p>;
  return (
    <div className="team-perm-groups" role="list">
      {groups.map((g) => (
        <div key={g.module} className="team-perm-module" role="listitem">
          <h4>{t(`team.modules.${g.module}`, { defaultValue: g.module })}</h4>
          <ul className="team-perm-list">
            {g.items.map((item) => (
              <li key={item.code} className={item.sensitive ? "is-sensitive" : undefined}>
                <code>{item.code}</code>
                {item.sensitive ? <span className="om-badge warn">{t("team.sensitive")}</span> : null}
              </li>
            ))}
          </ul>
        </div>
      ))}
    </div>
  );
}

export default function SettingsTeamPanel({ shopId, notify, onDirtyChange }: Props) {
  const { t, i18n } = useTranslation("merchant");
  const { canInShop, deniedTitle } = usePermissions();
  const canRead = canInShop("USER_READ");
  const canInvite = canInShop("USER_INVITE");
  const canRole = canInShop("USER_ROLE_UPDATE");
  const canRemove = canInShop("USER_REMOVE");

  const [section, setSection] = useState<Section>("members");
  const [catalog, setCatalog] = useState<TeamRoleCatalog | null>(null);
  const [members, setMembers] = useState<TeamMemberSummary[]>([]);
  const [memberTotal, setMemberTotal] = useState(0);
  const [memberActive, setMemberActive] = useState(0);
  const [memberPage, setMemberPage] = useState(0);
  const [memberSize, setMemberSize] = useState(20);
  const [memberPages, setMemberPages] = useState(0);
  const [invites, setInvites] = useState<TeamInvitationSummary[]>([]);
  const [inviteTotal, setInviteTotal] = useState(0);
  const [invitePending, setInvitePending] = useState(0);
  const [invitePage, setInvitePage] = useState(0);
  const [inviteSize, setInviteSize] = useState(20);
  const [invitePages, setInvitePages] = useState(0);

  const [q, setQ] = useState("");
  const [roleFilter, setRoleFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState("");
  const [inviteStatus, setInviteStatus] = useState("");

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);

  const [inviteOpen, setInviteOpen] = useState(false);
  const [inviteEmail, setInviteEmail] = useState("");
  const [inviteRole, setInviteRole] = useState("");
  const [inviteErrors, setInviteErrors] = useState<{ email?: string; role?: string }>({});
  const [createdLink, setCreatedLink] = useState<TeamInvitationCreated | null>(null);
  const [busy, setBusy] = useState(false);

  const [detail, setDetail] = useState<TeamMemberDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailRole, setDetailRole] = useState("");
  const [confirm, setConfirm] = useState<ConfirmKind>(null);

  const reqSeq = useRef(0);
  const dirty = inviteOpen && (inviteEmail.trim() !== "" || inviteRole !== "") && !createdLink;
  useEffect(() => { onDirtyChange?.(dirty); }, [dirty, onDirtyChange]);

  const roleOptions = useMemo(() => catalog?.roles ?? [], [catalog]);
  const assignableRoles = useMemo(() => roleOptions.filter((r) => r.assignableByActor), [roleOptions]);
  const selectedRole: TeamRoleOption | undefined = roleOptions.find((r) => r.code === inviteRole);
  const detailRoleOpt = roleOptions.find((r) => r.code === detailRole);

  const load = useCallback(async () => {
    if (!canRead || !shopId) return;
    const seq = ++reqSeq.current;
    setLoading(true);
    setError(null);
    try {
      const [roles, mem, inv] = await Promise.all([
        merchantTeamApi.roles(shopId),
        merchantTeamApi.members(shopId, {
          q: q.trim() || undefined,
          role: roleFilter || undefined,
          status: statusFilter || undefined,
          page: memberPage,
          size: memberSize,
        }),
        merchantTeamApi.invitations(shopId, {
          q: q.trim() || undefined,
          status: inviteStatus || undefined,
          page: invitePage,
          size: inviteSize,
        }),
      ]);
      if (seq !== reqSeq.current) return;
      setCatalog(roles);
      setMembers(mem.items);
      setMemberTotal(mem.page.totalItems);
      setMemberActive(mem.totalActive);
      setMemberPages(mem.page.totalPages);
      setInvites(inv.items);
      setInviteTotal(inv.page.totalItems);
      setInvitePending(inv.totalPending);
      setInvitePages(inv.page.totalPages);
    } catch (e) {
      if (seq !== reqSeq.current) return;
      setError(e instanceof Error ? e.message : t("team.loadError"));
    } finally {
      if (seq === reqSeq.current) setLoading(false);
    }
  }, [canRead, shopId, q, roleFilter, statusFilter, inviteStatus, memberPage, memberSize, invitePage, inviteSize, t, reload]);

  useEffect(() => { void load(); }, [load]);

  useEffect(() => {
    setMemberPage(0);
    setInvitePage(0);
  }, [shopId]);

  const openMember = async (userId: string) => {
    setDetailLoading(true);
    setDetail(null);
    try {
      const row = await merchantTeamApi.member(shopId, userId);
      setDetail(row);
      setDetailRole(row.role);
    } catch (e) {
      notify(e instanceof Error ? e.message : t("team.loadError"));
    } finally {
      setDetailLoading(false);
    }
  };

  const closeInvite = () => {
    setInviteOpen(false);
    setInviteEmail("");
    setInviteRole("");
    setInviteErrors({});
    setCreatedLink(null);
  };

  const validateInvite = () => {
    const next: { email?: string; role?: string } = {};
    const email = inviteEmail.trim().toLowerCase();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) next.email = t("team.emailInvalid");
    if (!inviteRole) next.role = t("team.roleRequired");
    setInviteErrors(next);
    return Object.keys(next).length === 0;
  };

  const runConfirm = async () => {
    if (!confirm) return;
    setBusy(true);
    try {
      if (confirm.kind === "invite") {
        const created = await merchantTeamApi.createInvite(shopId, { email: inviteEmail.trim(), role: inviteRole });
        setCreatedLink(created);
        notify(t("team.inviteCreated"));
        setReload((n) => n + 1);
      } else if (confirm.kind === "role") {
        await merchantTeamApi.changeRole(shopId, confirm.userId, confirm.role);
        notify(t("team.roleUpdated"));
        setReload((n) => n + 1);
        await openMember(confirm.userId);
      } else if (confirm.kind === "remove") {
        await merchantTeamApi.removeMember(shopId, confirm.userId);
        notify(t("team.memberRemoved"));
        setDetail(null);
        setReload((n) => n + 1);
      } else if (confirm.kind === "revoke") {
        await merchantTeamApi.revokeInvite(shopId, confirm.id);
        notify(t("team.inviteRevoked"));
        setReload((n) => n + 1);
      }
      setConfirm(null);
    } catch (e) {
      notify(e instanceof ApiError ? e.message : t("team.loadError"));
    } finally {
      setBusy(false);
    }
  };

  const onInviteSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!validateInvite()) return;
    setConfirm({ kind: "invite" });
  };

  const copyLink = async (url: string) => {
    try {
      await navigator.clipboard.writeText(url);
      notify(t("team.linkCopied"));
    } catch {
      notify(url);
    }
  };

  const roleDiff = useMemo(() => {
    if (!detail || !detailRoleOpt || detail.role === detailRole) return null;
    const current = new Set(detail.permissions);
    const next = new Set(detailRoleOpt.permissions);
    const added = [...next].filter((p) => !current.has(p));
    const removed = [...current].filter((p) => !next.has(p));
    return { added, removed };
  }, [detail, detailRole, detailRoleOpt]);

  const locale = i18n.language?.startsWith("en") ? "en-US" : i18n.language?.startsWith("zh") ? "zh-CN" : "th-TH";
  const fmt = (iso: string | null | undefined) => {
    if (!iso) return "—";
    try {
      return new Intl.DateTimeFormat(locale, { dateStyle: "medium", timeStyle: "short" }).format(new Date(iso));
    } catch {
      return iso;
    }
  };

  const roleLabel = (code: string, name: string | null) => {
    if (i18n.language?.startsWith("en")) {
      const en = roleOptions.find((r) => r.code === code)?.nameEn;
      return en || name || code;
    }
    return name || code;
  };

  const statusPill = (active: boolean) => (
    <span className={`pm-status ${active ? "ready" : "empty"}`}>
      {active ? t("team.statusActive") : t("team.statusInactive")}
    </span>
  );

  const statusOmPill = (active: boolean) => (
    <span className={`om-pill ${active ? "delivered" : "cancelled"}`}>
      {active ? t("team.statusActive") : t("team.statusInactive")}
    </span>
  );

  const inviteStatusPill = (status: string) => {
    const label =
      status === "PENDING" ? t("team.statusPending")
        : status === "ACCEPTED" ? t("team.statusAccepted")
          : status === "EXPIRED" ? t("team.statusExpired")
            : status === "REVOKED" ? t("team.statusRevoked")
              : status;
    const tone = status === "PENDING" ? "low" : status === "ACCEPTED" ? "ready" : "empty";
    return <span className={`pm-status ${tone}`}>{label}</span>;
  };

  if (!canRead) {
    return (
      <section className="product-form-card team-panel">
        <h3><UsersRound aria-hidden />{t("team.title")}</h3>
        <p className="pm-lang-hint">{deniedTitle(false)}</p>
      </section>
    );
  }

  return (
    <div className="team-settings">
      <section className="product-form-card team-panel">
        <header className="team-panel-head">
          <div>
            <h3><UsersRound aria-hidden />{t("team.title")}</h3>
            <p className="pm-lang-hint">{t("team.subtitle")}</p>
            {catalog?.staffQuota ? (
              <p className="pm-lang-hint">
                {t("team.quotaNote")}: {t("team.quotaUsed", {
                  used: catalog.staffQuota.staffUsed,
                  limit: catalog.staffQuota.quotaEnforced && catalog.staffQuota.staffLimit != null
                    ? t("team.ofLimit", { limit: catalog.staffQuota.staffLimit })
                    : "",
                })}
                {catalog.staffQuota.quotaNote ? ` — ${catalog.staffQuota.quotaNote}` : null}
              </p>
            ) : null}
          </div>
          <div className="pm-actions">
            <button
              type="button"
              className="primary"
              disabled={!canInvite || busy}
              title={!canInvite ? t("team.deniedInvite") : undefined}
              onClick={() => { setCreatedLink(null); setInviteOpen(true); }}
            >
              <MailPlus aria-hidden />{t("team.invite")}
            </button>
          </div>
        </header>

      </section>

      <nav className="pm-tabs team-section-tabs" role="tablist" aria-label={t("team.title")}>
        <button type="button" role="tab" aria-selected={section === "members"} className={section === "members" ? "active" : ""} onClick={() => setSection("members")}>
          {t("team.members")}
          <i>{memberActive}</i>
        </button>
        <button type="button" role="tab" aria-selected={section === "invitations"} className={section === "invitations" ? "active" : ""} onClick={() => setSection("invitations")}>
          {t("team.invitations")}
          <i>{invitePending}</i>
        </button>
      </nav>

      <section className="pm-table-card team-table-card">
        <form
          className="pm-filters team-filters"
          onSubmit={(e) => { e.preventDefault(); setMemberPage(0); setInvitePage(0); setReload((n) => n + 1); }}
        >
          <label className="pm-search">
            <Search aria-hidden />
            <input value={q} onChange={(e) => setQ(e.target.value)} placeholder={t("team.search")} aria-label={t("team.search")} />
          </label>
          {section === "members" ? (
            <>
              <SearchableSelect
                portal
                label={t("team.filterRole")}
                options={[t("team.allRoles"), ...roleOptions.map((r) => roleLabel(r.code, r.nameTh))]}
                value={roleFilter ? roleLabel(roleFilter, roleOptions.find((r) => r.code === roleFilter)?.nameTh ?? roleFilter) : t("team.allRoles")}
                onChange={(picked) => {
                  if (picked === t("team.allRoles")) { setRoleFilter(""); setMemberPage(0); return; }
                  const hit = roleOptions.find((r) => roleLabel(r.code, r.nameTh) === picked);
                  setRoleFilter(hit?.code ?? "");
                  setMemberPage(0);
                }}
              />
              <SearchableSelect
                portal
                label={t("team.filterStatus")}
                options={[t("team.allStatuses"), t("team.statusActive"), t("team.statusInactive")]}
                value={statusFilter === "ACTIVE" ? t("team.statusActive") : statusFilter === "INACTIVE" ? t("team.statusInactive") : t("team.allStatuses")}
                onChange={(picked) => {
                  if (picked === t("team.allStatuses")) setStatusFilter("");
                  else if (picked === t("team.statusActive")) setStatusFilter("ACTIVE");
                  else setStatusFilter("INACTIVE");
                  setMemberPage(0);
                }}
              />
            </>
          ) : (
            <SearchableSelect
              portal
              label={t("team.filterStatus")}
              options={[t("team.allStatuses"), t("team.statusPending"), t("team.statusAccepted"), t("team.statusExpired"), t("team.statusRevoked")]}
              value={
                inviteStatus === "PENDING" ? t("team.statusPending")
                  : inviteStatus === "ACCEPTED" ? t("team.statusAccepted")
                    : inviteStatus === "EXPIRED" ? t("team.statusExpired")
                      : inviteStatus === "REVOKED" ? t("team.statusRevoked")
                        : t("team.allStatuses")
              }
              onChange={(picked) => {
                if (picked === t("team.allStatuses")) setInviteStatus("");
                else if (picked === t("team.statusPending")) setInviteStatus("PENDING");
                else if (picked === t("team.statusAccepted")) setInviteStatus("ACCEPTED");
                else if (picked === t("team.statusExpired")) setInviteStatus("EXPIRED");
                else setInviteStatus("REVOKED");
                setInvitePage(0);
              }}
            />
          )}
          <div className="team-filter-actions">
            <button type="submit" className="primary">{t("team.applyFilters")}</button>
            <button type="button" onClick={() => { setQ(""); setRoleFilter(""); setStatusFilter(""); setInviteStatus(""); setMemberPage(0); setInvitePage(0); setReload((n) => n + 1); }}>{t("team.resetFilters")}</button>
          </div>
        </form>

        {loading ? <SkeletonTable rows={6} cols={4} /> : null}
        {error ? <LoadError message={error} onRetry={() => setReload((n) => n + 1)} /> : null}

        {!loading && !error && section === "members" ? (
          <>
            <div className="pm-table-wrap team-table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>{t("team.members")}</th>
                    <th>{t("team.filterRole")}</th>
                    <th>{t("team.filterStatus")}</th>
                    <th>{t("team.joinedAt")}</th>
                  </tr>
                </thead>
                <tbody>
                  {members.length === 0 ? (
                    <tr><td colSpan={4} className="team-empty">{t("team.emptyMembers")}</td></tr>
                  ) : members.map((m) => (
                    <tr
                      key={m.userId}
                      tabIndex={0}
                      className="team-row"
                      onClick={() => void openMember(m.userId)}
                      onKeyDown={(e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); void openMember(m.userId); } }}
                    >
                      <td>
                        <div className="pm-product">
                          <p>
                            <b title={displayName(m)}>{displayName(m)}</b>
                            <small title={m.email}>{m.email}</small>
                          </p>
                        </div>
                      </td>
                      <td>{roleLabel(m.role, m.roleName)}</td>
                      <td className="pm-state-cell">{statusPill(m.active)}</td>
                      <td className="num">{fmt(m.joinedAt)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <MerchantPagination
              page={memberPage}
              pageCount={memberPages}
              size={memberSize}
              total={memberTotal}
              onPageChange={setMemberPage}
              onSizeChange={(n) => { setMemberSize(n); setMemberPage(0); }}
            />
          </>
        ) : null}

        {!loading && !error && section === "invitations" ? (
          <>
            <div className="pm-table-wrap team-table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>{t("team.inviteEmail")}</th>
                    <th>{t("team.filterRole")}</th>
                    <th>{t("team.filterStatus")}</th>
                    <th>{t("team.expiresAt")}</th>
                    <th>{t("team.actions")}</th>
                  </tr>
                </thead>
                <tbody>
                  {invites.length === 0 ? (
                    <tr><td colSpan={5} className="team-empty">{t("team.emptyInvites")}</td></tr>
                  ) : invites.map((inv) => (
                    <tr key={inv.id}>
                      <td>
                        <div className="pm-product">
                          <p>
                            <b title={inv.email}>{inv.email}</b>
                            <small>{t("team.invitedBy")}: {inv.invitedByName || "—"}</small>
                          </p>
                        </div>
                      </td>
                      <td>{roleLabel(inv.role, inv.roleName)}</td>
                      <td className="pm-state-cell">{inviteStatusPill(inv.status)}</td>
                      <td className="num">{fmt(inv.expiresAt)}</td>
                      <td>
                        {inv.actionable ? (
                          <div className="pm-row-actions" onClick={(e) => e.stopPropagation()}>
                            <button type="button" disabled={!canInvite || busy} title={!canInvite ? t("team.deniedInvite") : t("team.resend")} aria-label={t("team.resend")} onClick={() => {
                              void (async () => {
                                setBusy(true);
                                try {
                                  const created = await merchantTeamApi.resendInvite(shopId, inv.id);
                                  setCreatedLink(created);
                                  setInviteOpen(true);
                                  notify(t("team.inviteResent"));
                                  setReload((n) => n + 1);
                                } catch (e) {
                                  notify(e instanceof Error ? e.message : t("team.loadError"));
                                } finally {
                                  setBusy(false);
                                }
                              })();
                            }}><RefreshCw aria-hidden /></button>
                            <button type="button" disabled={!canInvite || busy} title={!canInvite ? t("team.deniedInvite") : t("team.revoke")} aria-label={t("team.revoke")} onClick={() => setConfirm({ kind: "revoke", id: inv.id, email: inv.email })}><X aria-hidden /></button>
                          </div>
                        ) : <span className="pm-lang-hint">—</span>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <MerchantPagination
              page={invitePage}
              pageCount={invitePages}
              size={inviteSize}
              total={inviteTotal}
              onPageChange={setInvitePage}
              onSizeChange={(n) => { setInviteSize(n); setInvitePage(0); }}
            />
          </>
        ) : null}
      </section>

      {inviteOpen ? createPortal(
        <div
          className="om-drawer-overlay cm-drawer-overlay om-order-overlay"
          role="presentation"
          onMouseDown={(e) => { if (e.target === e.currentTarget && !dirty) closeInvite(); }}
        >
          <aside
            className="om-drawer cm-detail sh-detail om-detail team-drawer"
            role="dialog"
            aria-modal="true"
            aria-labelledby="team-invite-title"
            onKeyDown={(e) => { if (e.key === "Escape" && !dirty) closeInvite(); }}
          >
            <header className="cm-detail-head">
              <div className={`cm-avatar om-avatar ${createdLink ? "tone-success" : "tone-moving"}`} aria-hidden="true">
                <MailPlus />
              </div>
              <div className="cm-detail-id">
                <h2 id="team-invite-title">{t("team.inviteTitle")}</h2>
                <div className="cm-detail-meta">
                  <span className="om-badge">{t("team.invitations")}</span>
                </div>
              </div>
              <button
                type="button"
                className="om-drawer-close"
                aria-label={t("team.cancel")}
                onClick={() => { if (!dirty) closeInvite(); else { setConfirm(null); closeInvite(); } }}
              >
                <X aria-hidden />
              </button>
            </header>
            <div className="om-drawer-body cm-detail-body">
              {createdLink ? (
                <section className="om-section">
                  <h3><Mail aria-hidden />{t("team.copyLink")}</h3>
                  <p className="om-note">{createdLink.deliveryNote || t("team.deliveryNote")}</p>
                  <p className="num team-invite-url">{createdLink.inviteUrl}</p>
                </section>
              ) : (
                <form className="team-drawer-form" onSubmit={onInviteSubmit}>
                  <section className="om-section">
                    <h3><Mail aria-hidden />{t("team.inviteEmail")}</h3>
                    <label className="team-drawer-field">
                      <span>{t("team.inviteEmail")} <em>*</em></span>
                      <input
                        id="team-invite-email"
                        value={inviteEmail}
                        onChange={(e) => setInviteEmail(e.target.value)}
                        autoComplete="email"
                        aria-invalid={Boolean(inviteErrors.email)}
                        aria-describedby={inviteErrors.email ? "team-invite-email-err" : undefined}
                      />
                      {inviteErrors.email ? <span className="form-field-error" id="team-invite-email-err" role="alert">{inviteErrors.email}</span> : null}
                    </label>
                    <SearchableSelect
                      portal
                      label={t("team.inviteRole")}
                      options={assignableRoles.map((r) => roleLabel(r.code, r.nameTh))}
                      value={inviteRole ? roleLabel(inviteRole, selectedRole?.nameTh ?? inviteRole) : ""}
                      onChange={(picked) => {
                        const hit = assignableRoles.find((r) => roleLabel(r.code, r.nameTh) === picked);
                        setInviteRole(hit?.code ?? "");
                      }}
                    />
                    {inviteErrors.role ? <p className="form-field-error" role="alert">{inviteErrors.role}</p> : null}
                    <p className="om-note">{t("team.deliveryNote")}</p>
                  </section>
                  {selectedRole ? (
                    <section className="om-section">
                      <h3><Shield aria-hidden />{t("team.permissionPreview")}</h3>
                      <PermissionGroups groups={selectedRole.permissionGroups} t={t} />
                    </section>
                  ) : null}
                </form>
              )}
            </div>
            <footer className="om-drawer-actions">
              {createdLink ? (
                <button type="button" className="primary" onClick={() => void copyLink(createdLink.inviteUrl)}>
                  <Copy aria-hidden />{t("team.copyLink")}
                </button>
              ) : (
                <>
                  <button type="button" onClick={closeInvite}>{t("team.cancel")}</button>
                  <MerchantSaveButton
                    saving={busy}
                    disabled={!canInvite}
                    title={!canInvite ? t("team.deniedInvite") : undefined}
                    label={t("team.saveInvite")}
                    onClick={() => { if (validateInvite()) setConfirm({ kind: "invite" }); }}
                  />
                </>
              )}
            </footer>
          </aside>
        </div>,
        document.body,
      ) : null}

      {(detail || detailLoading) ? createPortal(
        <div
          className="om-drawer-overlay cm-drawer-overlay om-order-overlay"
          role="presentation"
          onMouseDown={(e) => { if (e.target === e.currentTarget) setDetail(null); }}
        >
          <aside
            className="om-drawer cm-detail sh-detail om-detail team-drawer"
            role="dialog"
            aria-modal="true"
            aria-labelledby="team-member-title"
            onKeyDown={(e) => { if (e.key === "Escape") setDetail(null); }}
          >
            {detailLoading || !detail ? (
              <div className="om-drawer-body cm-detail-body">
                <div className="om-skel">
                  <Skeleton height={20} width="60%" />
                  <Skeleton height={90} radius={8} />
                  <Skeleton height={120} radius={8} />
                </div>
              </div>
            ) : (
              <>
                <header className="cm-detail-head">
                  <div className={`cm-avatar om-avatar ${detail.active ? "tone-success" : "tone-failed"}`} aria-hidden="true">
                    <UserRound />
                  </div>
                  <div className="cm-detail-id">
                    <h2 id="team-member-title" title={displayName(detail)}>{displayName(detail)}</h2>
                    <div className="cm-detail-meta">
                      <span className="om-pill waiting">{roleLabel(detail.role, detail.roleName)}</span>
                      {statusOmPill(detail.active)}
                    </div>
                  </div>
                  <button type="button" className="om-drawer-close" aria-label={t("team.cancel")} onClick={() => setDetail(null)}>
                    <X aria-hidden />
                  </button>
                </header>

                <div className="om-drawer-body cm-detail-body">
                  <dl className="cm-contact">
                    <div className="cm-contact-row">
                      <span className="cm-icon-chip" aria-hidden="true"><Mail /></span>
                      <dt>{t("team.inviteEmail")}</dt>
                      <dd>{detail.email}</dd>
                    </div>
                    <div className="cm-contact-row">
                      <span className="cm-icon-chip" aria-hidden="true"><CalendarDays /></span>
                      <dt>{t("team.joinedAt")}</dt>
                      <dd>{fmt(detail.joinedAt)}</dd>
                    </div>
                    <div className="cm-contact-row">
                      <span className="cm-icon-chip" aria-hidden="true"><Shield /></span>
                      <dt>{t("team.filterRole")}</dt>
                      <dd>{roleLabel(detail.role, detail.roleName)}</dd>
                    </div>
                  </dl>

                  <section className="om-section">
                    <h3><Shield aria-hidden />{t("team.permissionPreview")}</h3>
                    <PermissionGroups groups={detail.permissionGroups} t={t} />
                  </section>

                  {detail.active && detail.role !== "STORE_OWNER" ? (
                    <section className="om-section">
                      <h3><UsersRound aria-hidden />{t("team.changeRole")}</h3>
                      <SearchableSelect
                        portal
                        label={t("team.changeRole")}
                        options={assignableRoles.map((r) => roleLabel(r.code, r.nameTh))}
                        value={detailRole ? roleLabel(detailRole, detailRoleOpt?.nameTh ?? detailRole) : roleLabel(detail.role, detail.roleName)}
                        onChange={(picked) => {
                          const hit = assignableRoles.find((r) => roleLabel(r.code, r.nameTh) === picked);
                          if (hit) setDetailRole(hit.code);
                        }}
                      />
                      {roleDiff ? (
                        <div className="team-role-diff">
                          <p><b>{t("team.roleDiffAdded")}</b>: {roleDiff.added.join(", ") || "—"}</p>
                          <p><b>{t("team.roleDiffRemoved")}</b>: {roleDiff.removed.join(", ") || "—"}</p>
                        </div>
                      ) : null}
                    </section>
                  ) : null}

                  <section className="om-section">
                    <h3><History aria-hidden />{t("team.history")}</h3>
                    {detail.events.length === 0 ? (
                      <p className="om-note-text">{t("team.noHistory")}</p>
                    ) : (
                      <ul className="team-history">
                        {detail.events.map((ev, i) => (
                          <li key={`${ev.action}-${i}`}>
                            <div>
                              <b>{ev.action}</b>
                              {ev.note ? <span> — {ev.note}</span> : null}
                            </div>
                            <time className="num">{fmt(ev.at)}</time>
                          </li>
                        ))}
                      </ul>
                    )}
                  </section>
                </div>

                {detail.active && detail.role !== "STORE_OWNER" ? (
                  <footer className="om-drawer-actions">
                    <button
                      type="button"
                      className="danger"
                      disabled={!canRemove || busy}
                      title={!canRemove ? t("team.deniedRemove") : undefined}
                      onClick={() => setConfirm({ kind: "remove", userId: detail.userId, name: displayName(detail) })}
                    >
                      <UserMinus aria-hidden />{t("team.remove")}
                    </button>
                    <MerchantSaveButton
                      saving={busy}
                      disabled={!canRole || detailRole === detail.role}
                      title={!canRole ? t("team.deniedRole") : undefined}
                      label={t("team.changeRole")}
                      onClick={() => setConfirm({ kind: "role", userId: detail.userId, role: detailRole })}
                    />
                  </footer>
                ) : null}
              </>
            )}
          </aside>
        </div>,
        document.body,
      ) : null}

      {confirm ? (
        <ConfirmDialog
          title={
            confirm.kind === "invite" ? t("team.confirmInvite")
              : confirm.kind === "role" ? t("team.confirmRole")
                : confirm.kind === "remove" ? t("team.confirmRemove")
                  : t("team.confirmRevoke")
          }
          message={
            confirm.kind === "invite" ? t("team.confirmInviteMsg")
              : confirm.kind === "role" ? t("team.confirmRoleMsg")
                : confirm.kind === "remove" ? t("team.confirmRemoveMsg")
                  : t("team.confirmRevokeMsg")
          }
          confirmLabel={confirm.kind === "remove" || confirm.kind === "revoke" ? t("team.remove") : t("team.confirmInvite")}
          cancelLabel={t("team.cancel")}
          danger={confirm.kind === "remove" || confirm.kind === "revoke"}
          onConfirm={() => { void runConfirm(); }}
          onCancel={() => setConfirm(null)}
        />
      ) : null}
    </div>
  );
}
