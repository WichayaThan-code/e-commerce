import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { CheckCircle2, CircleAlert, LoaderCircle, UsersRound } from "lucide-react";
import AuthSplitLayout from "../components/auth/AuthSplitLayout";
import { useAuth } from "../contexts/AuthContext";
import { ApiError, shopInvitationApi, type TeamInvitePreview } from "../services/api";
import { safeInternalPath } from "../utils/safeInternalPath";
import "../styles/settings.css";

function PermissionPreview({ groups, t }: { groups: TeamInvitePreview["permissionGroups"]; t: (k: string, o?: Record<string, unknown>) => string }) {
  return (
    <div className="team-perm-groups invite-perm-groups">
      {groups.map((g) => (
        <section key={g.module}>
          <h3>{t(`team.modules.${g.module}`, { defaultValue: g.module })}</h3>
          <ul>
            {g.items.map((item) => (
              <li key={item.code} className={item.sensitive ? "is-sensitive" : undefined}>
                <code>{item.code}</code>
                {item.sensitive ? <em>{t("team.sensitive")}</em> : null}
              </li>
            ))}
          </ul>
        </section>
      ))}
    </div>
  );
}

/** Invite acceptance — never auto-accepts on GET/mount. Token stays in the path only. */
export default function InviteAcceptPage() {
  const { token = "" } = useParams<{ token: string }>();
  const { t } = useTranslation("merchant");
  const navigate = useNavigate();
  const { user, loading: authLoading } = useAuth();
  const [preview, setPreview] = useState<TeamInvitePreview | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [accepting, setAccepting] = useState(false);
  const [accepted, setAccepted] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const returnPath = safeInternalPath(`/invite/${encodeURIComponent(token)}`, "/");

  useEffect(() => {
    const meta = document.createElement("meta");
    meta.name = "referrer";
    meta.content = "no-referrer";
    document.head.appendChild(meta);
    return () => { meta.remove(); };
  }, []);

  useEffect(() => {
    if (!token || token.length < 20) {
      setLoadError(t("team.accept.conflict.INVITE_NOT_FOUND"));
      setLoading(false);
      return;
    }
    let cancelled = false;
    setLoading(true);
    setLoadError(null);
    void (async () => {
      try {
        const data = await shopInvitationApi.preview(token, Boolean(user));
        if (!cancelled) setPreview(data);
      } catch (e) {
        if (!cancelled) {
          setLoadError(e instanceof ApiError
            ? t(`team.accept.conflict.${e.code}`, { defaultValue: e.message || t("team.accept.conflict.default") })
            : t("team.accept.conflict.default"));
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [token, user?.id, t, user]);

  const conflictMessage = (code: string | null | undefined) => {
    if (!code) return null;
    return t(`team.accept.conflict.${code}`, { defaultValue: t("team.accept.conflict.default") });
  };

  const onAccept = async () => {
    if (!token || accepting || accepted) return;
    setAccepting(true);
    setActionError(null);
    try {
      await shopInvitationApi.accept(token);
      setAccepted(true);
    } catch (e) {
      if (e instanceof ApiError) {
        setActionError(t(`team.accept.conflict.${e.code}`, { defaultValue: e.message || t("team.accept.conflict.default") }));
      } else {
        setActionError(t("team.accept.conflict.default"));
      }
    } finally {
      setAccepting(false);
    }
  };

  const fmt = (iso: string | null | undefined) => {
    if (!iso) return "—";
    try {
      return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(iso));
    } catch {
      return iso;
    }
  };

  return (
    <AuthSplitLayout navAriaLabel={t("team.accept.title")}>
      <div className="login-title invite-accept-card">
        <span className="login-welcome-icon"><UsersRound size={25} /></span>
        <span className="login-kicker">{t("team.title")}</span>
        <h2>{t("team.accept.title")}</h2>
        <p>{t("team.accept.previewHint")}</p>

        {authLoading || loading ? (
          <p className="invite-accept-status" role="status"><LoaderCircle className="spin" aria-hidden /> …</p>
        ) : null}

        {loadError ? (
          <p className="invite-accept-error" role="alert"><CircleAlert aria-hidden />{loadError}</p>
        ) : null}

        {preview && !loadError ? (
          <>
            <dl className="invite-accept-meta">
              <div><dt>{t("team.accept.shop")}</dt><dd>{preview.shopName}</dd></div>
              <div><dt>{t("team.accept.inviter")}</dt><dd>{preview.inviterName}</dd></div>
              <div><dt>{t("team.accept.role")}</dt><dd>{preview.roleName || preview.role}</dd></div>
              <div><dt>{t("team.accept.expires")}</dt><dd>{fmt(preview.expiresAt)}</dd></div>
            </dl>
            <PermissionPreview groups={preview.permissionGroups} t={t} />

            {accepted ? (
              <div className="invite-accept-success" role="status">
                <CheckCircle2 aria-hidden />
                <p>{t("team.accept.success")}</p>
                <button type="button" className="shop-settings-primary" onClick={() => navigate("/merchant/dashboard")}>{t("team.accept.goDashboard")}</button>
              </div>
            ) : (
              <div className="invite-accept-actions">
                {!user ? (
                  <>
                    <a className="shop-settings-primary" href={`/login?next=${encodeURIComponent(returnPath)}`}>{t("team.accept.login")}</a>
                    <a className="secondary" href={`/signup?next=${encodeURIComponent(returnPath)}`}>{t("team.accept.signup")}</a>
                  </>
                ) : !user.emailVerified ? (
                  <a className="shop-settings-primary" href={`/verify-email?next=${encodeURIComponent(returnPath)}`}>{t("team.accept.verifyEmail")}</a>
                ) : preview.conflictCode ? (
                  <p className="invite-accept-error" role="alert">{conflictMessage(preview.conflictCode)}</p>
                ) : preview.status !== "PENDING" ? (
                  <p className="invite-accept-error" role="alert">{conflictMessage(`INVITE_${preview.status}`)}</p>
                ) : (
                  <button type="button" className="shop-settings-primary" disabled={accepting} onClick={() => void onAccept()}>
                    {accepting ? t("team.accept.accepting") : t("team.accept.accept")}
                  </button>
                )}
                {actionError ? <p className="invite-accept-error" role="alert">{actionError}</p> : null}
              </div>
            )}
          </>
        ) : null}
      </div>
    </AuthSplitLayout>
  );
}
