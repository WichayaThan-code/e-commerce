import { useMemo } from "react";
import { Link, useParams } from "react-router-dom";
import {
  CalendarClock, Headset, Link2, RotateCcw, ScrollText, SearchX, ShieldCheck, Truck, type LucideIcon,
} from "lucide-react";
import ShopLogo from "../../components/ShopLogo";
import EmptyState from "../../components/feedback/EmptyState";
import { Skeleton, SkeletonText } from "../../components/feedback/Skeleton";
import { useShopPublic, useShopCrumb } from "../../contexts/ShopPublicContext";
import { useDocumentMeta } from "../../hooks/useDocumentMeta";
import { SHOP_POLICY_TYPES, useShopPolicies, type ShopPolicy, type ShopPolicyType } from "../../hooks/useShopPolicies";
import { usePublicTranslation } from "../../hooks/usePublicTranslation";
import { shopContactPath, shopPoliciesPath } from "../../services/shopRoutes";
import NotFoundPage from "../NotFoundPage";
import "../../styles/shop-contact.css";
import "../../styles/shop-policies.css";

const POLICY_ICONS: Record<ShopPolicyType, LucideIcon> = {
  shipping: Truck,
  returns: RotateCcw,
  privacy: ShieldCheck,
  terms: ScrollText,
};

type PolicyBlock =
  | { kind: "heading"; text: string }
  | { kind: "para"; text: string }
  | { kind: "ul"; items: string[] }
  | { kind: "ol"; items: string[] };

const BULLET_RE = /^\s*[•\-*–]\s+/;
const NUMBER_RE = /^\s*\d+[.)]\s+/;

function isPolicyType(value: string | undefined): value is ShopPolicyType {
  return Boolean(value && (SHOP_POLICY_TYPES as string[]).includes(value));
}

/**
 * Plain-text policy body → readable blocks. Blank line = new group;
 * a short first line followed by more lines = sub-heading; "• / - / *" = bullet list; "1." = numbered list.
 * Text only — React escapes everything, no HTML/markdown rendering.
 */
function parsePolicyBody(body: string): PolicyBlock[] {
  const blocks: PolicyBlock[] = [];
  const groups = body.replace(/\r\n?/g, "\n").split(/\n\s*\n/);
  for (const group of groups) {
    const lines = group.split("\n").map((line) => line.trimEnd()).filter((line) => line.trim() !== "");
    if (lines.length === 0) continue;
    let index = 0;
    const first = lines[0].trim();
    if (lines.length > 1 && !BULLET_RE.test(first) && !NUMBER_RE.test(first) && first.length <= 80) {
      blocks.push({ kind: "heading", text: first });
      index = 1;
    }
    let para: string[] = [];
    const flushPara = () => {
      if (para.length) {
        blocks.push({ kind: "para", text: para.join("\n") });
        para = [];
      }
    };
    for (; index < lines.length; index += 1) {
      const line = lines[index];
      const kind = BULLET_RE.test(line) ? "ul" : NUMBER_RE.test(line) ? "ol" : null;
      if (!kind) {
        para.push(line.trim());
        continue;
      }
      flushPara();
      const text = line.replace(kind === "ul" ? BULLET_RE : NUMBER_RE, "").trim();
      const last = blocks[blocks.length - 1];
      if (last && (last.kind === "ul" || last.kind === "ol") && last.kind === kind) last.items.push(text);
      else blocks.push({ kind, items: [text] });
    }
    flushPara();
  }
  return blocks;
}

function formatDate(iso: string | null | undefined, tag: string): string | null {
  if (!iso) return null;
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return null;
  return new Intl.DateTimeFormat(tag, { day: "2-digit", month: "2-digit", year: "numeric" }).format(date);
}

function latestUpdate(policies: ShopPolicy[]): string | null {
  let best: string | null = null;
  let bestTime = -Infinity;
  for (const policy of policies) {
    const time = policy.updatedAt ? new Date(policy.updatedAt).getTime() : Number.NaN;
    if (!Number.isNaN(time) && time > bestTime) {
      bestTime = time;
      best = policy.updatedAt ?? null;
    }
  }
  return best;
}

function PolicyBody({ body }: { body: string }) {
  const blocks = useMemo(() => parsePolicyBody(body), [body]);
  return (
    <div className="shop-policy-body">
      {blocks.map((block, index) => {
        if (block.kind === "heading") return <h3 key={index}>{block.text}</h3>;
        if (block.kind === "para") return <p key={index}>{block.text}</p>;
        const items = block.items.map((item, itemIndex) => <li key={itemIndex}>{item}</li>);
        return block.kind === "ul" ? <ul key={index}>{items}</ul> : <ol key={index}>{items}</ol>;
      })}
    </div>
  );
}

export default function ShopPoliciesPage() {
  const { slug, shop } = useShopPublic();
  const { policyType } = useParams();
  const { tp, localeTag } = usePublicTranslation();
  const { state, policies, reload } = useShopPolicies(slug);

  const knownType = policyType == null || isPolicyType(policyType);
  const selectedType = isPolicyType(policyType) ? policyType : null;
  const typeLabel = selectedType ? tp(`shop.policies.types.${selectedType}`) : null;
  const label = typeLabel ?? tp("shop.policies.title");
  useShopCrumb(knownType ? label : null);
  useDocumentMeta({ title: knownType ? `${label} · ${shop.name}` : null });

  const byType = useMemo(() => new Map(policies.map((policy) => [policy.type, policy])), [policies]);
  const lastUpdated = useMemo(() => formatDate(latestUpdate(policies), localeTag), [policies, localeTag]);

  if (!knownType) {
    return <NotFoundPage kind="page" bare />;
  }

  const visible: ShopPolicy[] = selectedType
    ? (byType.has(selectedType) ? [byType.get(selectedType)!] : [])
    : SHOP_POLICY_TYPES.map((type) => byType.get(type)).filter((policy): policy is ShopPolicy => Boolean(policy));
  const loaded = state === "ok";

  const hero = (
    <section className="shop-mall-hero shop-contact-hero shop-policies-hero recommend-width">
      <div className="shop-mall-hero-body">
        <div className="shop-mall-hero-brand">
          <div className="shop-mall-logo">
            <ShopLogo src={shop.logoUrl} name={shop.name} />
          </div>
          <div className="shop-mall-hero-copy">
            <div className="shop-mall-title-row">
              <h1>{label}</h1>
            </div>
            <p className="shop-mall-sub">{tp("shop.policies.subtitle", { name: shop.name })}</p>
            {loaded && policies.length > 0 ? (
              <div className="shop-contact-meta">
                <span>
                  <ScrollText aria-hidden="true" />
                  <span className="num">{policies.length}</span>
                  {tp("shop.policies.countLabel")}
                </span>
                {lastUpdated ? (
                  <span>
                    <CalendarClock aria-hidden="true" />
                    {tp("shop.policies.updated")} <span className="num">{lastUpdated}</span>
                  </span>
                ) : null}
              </div>
            ) : null}
          </div>
        </div>
        <div className="shop-mall-actions">
          <Link className="shop-mall-btn is-outline" to={shopContactPath(slug)}>
            <Headset />{tp("shop.policies.contactCta")}
          </Link>
        </div>
      </div>
    </section>
  );

  const nav = (
    <nav className="shop-policies-nav" aria-label={tp("shop.policies.navAria")}>
      <ul>
        {selectedType ? (
          <li>
            <Link className="shop-policies-chip" to={shopPoliciesPath(slug)}>
              <ScrollText aria-hidden="true" /><span>{tp("shop.policies.all")}</span>
            </Link>
          </li>
        ) : null}
        {SHOP_POLICY_TYPES.map((type) => {
          const Icon = POLICY_ICONS[type];
          const has = byType.has(type);
          const active = selectedType === type;
          // Overview + content exists → in-page anchor (ShopLayout scrolls to location.hash); else → type page.
          const to = !selectedType && has ? { hash: `#policy-${type}` } : `${shopPoliciesPath(slug)}/${type}`;
          const classes = ["shop-policies-chip", active ? "is-active" : "", loaded && !has ? "is-empty" : ""]
            .filter(Boolean)
            .join(" ");
          return (
            <li key={type}>
              <Link className={classes} to={to} aria-current={active ? "page" : undefined}>
                <Icon aria-hidden="true" />
                <span>{tp(`shop.policies.types.${type}`)}</span>
                {loaded && !has ? <em>{tp("shop.policies.notSet")}</em> : null}
              </Link>
            </li>
          );
        })}
      </ul>
    </nav>
  );

  let content;
  if (state === "loading") {
    content = (
      <div className="shop-policies-skeleton" aria-hidden="true">
        {[0, 1].map((index) => (
          <div className="shop-contact-panel shop-policy-section" key={index}>
            <div className="shop-policy-head">
              <Skeleton width={44} height={44} radius={12} />
              <div className="shop-policy-head-copy">
                <Skeleton width="45%" height={20} />
                <Skeleton width="30%" height={12} />
              </div>
            </div>
            <SkeletonText lines={6} />
          </div>
        ))}
      </div>
    );
  } else if (state === "error") {
    content = (
      <section className="shop-contact-panel">
        <EmptyState
          variant="error"
          title={tp("shop.loadError")}
          action={{ label: tp("shop.productsRetry"), onClick: reload }}
        />
      </section>
    );
  } else if (visible.length === 0) {
    content = (
      <section className="shop-contact-panel">
        <EmptyState
          variant={selectedType ? "filtered" : "empty"}
          icon={selectedType ? SearchX : ScrollText}
          title={selectedType ? tp("shop.policies.emptyOne", { type: typeLabel }) : tp("shop.policies.empty")}
          description={tp("shop.policies.emptyHint")}
          actions={[
            { label: tp("shop.policies.contactCta"), to: shopContactPath(slug) },
            ...(selectedType ? [{ label: tp("shop.policies.all"), to: shopPoliciesPath(slug) }] : []),
          ]}
        />
      </section>
    );
  } else {
    content = visible.map((policy) => {
      const Icon = POLICY_ICONS[policy.type];
      const fallbackTitle = tp(`shop.policies.types.${policy.type}`);
      const title = policy.title || fallbackTitle;
      const updated = formatDate(policy.updatedAt, localeTag);
      return (
        <article
          key={policy.type}
          id={`policy-${policy.type}`}
          className="shop-contact-panel shop-policy-section"
          aria-labelledby={`policy-${policy.type}-title`}
        >
          <header className="shop-policy-head">
            <span className="shop-contact-icon" aria-hidden="true"><Icon /></span>
            <div className="shop-policy-head-copy">
              {policy.title ? <span className="shop-policy-kicker">{fallbackTitle}</span> : null}
              <h2 id={`policy-${policy.type}-title`}>{title}</h2>
              {updated ? (
                <p className="shop-policy-updated">
                  <CalendarClock aria-hidden="true" />
                  {tp("shop.policies.updated")} <span className="num">{updated}</span>
                </p>
              ) : null}
            </div>
            {!selectedType ? (
              <Link
                className="shop-policy-permalink"
                to={`${shopPoliciesPath(slug)}/${policy.type}`}
                aria-label={tp("shop.policies.openOne", { title })}
                title={tp("shop.policies.openOne", { title })}
              >
                <Link2 />
              </Link>
            ) : null}
          </header>
          <PolicyBody body={policy.body} />
        </article>
      );
    });
  }

  return (
    <>
      {hero}
      <div className="shop-policies recommend-width">
        <aside className="shop-policies-aside">{nav}</aside>
        <div className="shop-policies-main" aria-busy={state === "loading" ? true : undefined}>
          {content}
          {loaded ? (
            <section className="shop-contact-panel shop-policies-cta" aria-labelledby="shop-policies-cta-title">
              <span className="shop-contact-icon" aria-hidden="true"><Headset /></span>
              <div>
                <h2 id="shop-policies-cta-title">{tp("shop.policies.ctaTitle")}</h2>
                <p>{tp("shop.policies.ctaText")}</p>
              </div>
              <Link className="shop-contact-action" to={shopContactPath(slug)}>{tp("shop.policies.contactCta")}</Link>
            </section>
          ) : null}
        </div>
      </div>
    </>
  );
}
