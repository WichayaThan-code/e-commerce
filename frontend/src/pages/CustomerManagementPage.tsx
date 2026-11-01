import { useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import {
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Crown,
  Download,
  Globe2,
  Copy,
  ExternalLink,
  EyeOff,
  Heart,
  Mail,
  MapPin,
  MessageCircle,
  Pencil,
  ReceiptText,
  Phone,
  Search,
  ShoppingBag,
  ShoppingCart,
  UserPlus,
  UsersRound,
  Wallet,
} from "lucide-react";
import MerchantBrand from "../components/merchant/MerchantBrand";
import MerchantOrderDrawer from "../components/merchant/MerchantOrderDrawer";
import MerchantTopbarSearch from "../components/merchant/MerchantTopbarSearch";
import MerchantTopbarActions from "../components/merchant/MerchantTopbarActions";
import DateRangePicker from "../components/form/DateRangePicker";
import SearchableSelect from "../components/SearchableSelect";
import { LoadError, Skeleton, SkeletonTable } from "../components/feedback/Skeleton";
import { useAuth } from "../contexts/AuthContext";
import { useActiveShop } from "../contexts/ActiveShopContext";
import { hasPlatformAccess, hasMerchantAccess } from "../services/permissions";
import { usePermissions } from "../hooks/usePermissions";
import { useDebouncedValue } from "../hooks/useDebouncedValue";
import { usePublicTranslation } from "../hooks/usePublicTranslation";
import { ApiError, exportCustomerInsights } from "../services/api";
import { statusTone } from "../components/merchant/orderFormat";
import {
  avatarForName,
  getCustomerInsight,
  listCustomerInsightChannels,
  listCustomerInsightOrders,
  listCustomerInsights,
  type CustomerInsight,
  type CustomerInsightDetail,
  type CustomerInsightOrder,
  type CustomerInsightsStats,
} from "../services/merchantCustomers";
import "../styles/merchant-dashboard.css";
import "../styles/product-management.css";
import "../styles/customer-management.css";
import "../styles/form-fields.css";
import "../styles/order-management.css";
import MerchantSidebar from "../components/merchant/MerchantSidebar";

const SEARCH_DEBOUNCE_MS = 400;


function Channel({ name }: { name: string | null }) {
  if (!name) return <span className="cm-channel">—</span>;
  const key = name.replace(/\s+/g, "");
  return (
    <span className={`cm-channel c-${key}`}>
      {name === "STOREFRONT" || name === "WEBSITE" ? (
        <Globe2 />
      ) : name.includes("LINE") ? (
        <MessageCircle />
      ) : name === "FACEBOOK" ? (
        "f"
      ) : name.includes("TIKTOK") ? (
        "♪"
      ) : name === "SHOPEE" ? (
        "S"
      ) : (
        "L"
      )}
      <i>{name}</i>
    </span>
  );
}

function money(value: number | string | null | undefined, localeTag: string) {
  const n = typeof value === "string" ? Number(value) : value ?? 0;
  return new Intl.NumberFormat(localeTag, { style: "currency", currency: "THB", maximumFractionDigits: 0 }).format(Number.isFinite(n) ? n : 0);
}

function formatJoined(iso: string | null | undefined, localeTag: string) {
  if (!iso) return "—";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "—";
  return date.toLocaleString(localeTag, { dateStyle: "medium", timeStyle: "short" });
}

function formatOrderAt(iso: string | null | undefined, localeTag: string) {
  if (!iso) return "—";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "—";
  return date.toLocaleString(localeTag, {
    timeZone: "Asia/Bangkok",
    dateStyle: "medium",
    timeStyle: "short",
  });
}

function initialsOf(name: string | null | undefined) {
  const words = (name || "").trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return "?";
  const first = Array.from(words[0])[0] || "";
  if (words.length === 1) return first.toUpperCase();
  const last = Array.from(words[words.length - 1])[0] || "";
  return (first + last).toUpperCase();
}

function customerOrdersUrl(key: string, name: string) {
  const params = new URLSearchParams({ customer: key });
  if (name.trim()) params.set("customerName", name.trim().slice(0, 60));
  return `/merchant/orders?${params.toString()}`;
}

export default function CustomerManagementPage() {
  const { t } = useTranslation("merchant");
  const { localeTag } = usePublicTranslation();
  const { user, loading } = useAuth();
  const { shopId } = useActiveShop();
  const { canInShop, deniedTitle } = usePermissions();
  const canCreate = canInShop("CUSTOMER_CREATE");
  const canUpdate = canInShop("CUSTOMER_UPDATE");
  const canReadPii = canInShop("CUSTOMER_PII_READ");
  const canReadCustomers = canInShop("CUSTOMER_READ");
  const canReadOrders = canInShop("ORDER_READ");

  const [params, setParams] = useSearchParams();
  const qParam = params.get("q") || "";
  const segment = params.get("segment") || "";
  const channel = params.get("channel") || "";
  const status = params.get("status") || "";
  const joinedFrom = params.get("joinedFrom") || "";
  const joinedTo = params.get("joinedTo") || "";
  const sort = params.get("sort") || "lastOrderAt";
  const direction = params.get("direction") || "desc";
  const page = Math.max(0, Number(params.get("page") || "0") || 0);
  const size = [8, 16, 24].includes(Number(params.get("size"))) ? Number(params.get("size")) : 8;
  const selectedKey = params.get("selected") || "";

  const [queryDraft, setQueryDraft] = useState(qParam);
  const debouncedQuery = useDebouncedValue(queryDraft, SEARCH_DEBOUNCE_MS);

  const [items, setItems] = useState<CustomerInsight[]>([]);
  const [stats, setStats] = useState<CustomerInsightsStats | null>(null);
  const [total, setTotal] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [fetching, setFetching] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [channels, setChannels] = useState<string[]>([]);
  const [channelsLoading, setChannelsLoading] = useState(false);
  const [detail, setDetail] = useState<CustomerInsightDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [orders, setOrders] = useState<CustomerInsightOrder[]>([]);
  const [ordersTotal, setOrdersTotal] = useState(0);
  const [ordersLoading, setOrdersLoading] = useState(false);
  const [ordersError, setOrdersError] = useState<string | null>(null);
  const [ordersReload, setOrdersReload] = useState(0);
  const [openOrderId, setOpenOrderId] = useState<string | null>(null);
  const [checked, setChecked] = useState<string[]>([]);
  const [notice, setNotice] = useState("");
  const [reload, setReload] = useState(0);
  const [exporting, setExporting] = useState(false);
  const listReq = useRef(0);
  const detailReq = useRef(0);

  const patch = (next: Record<string, string | null>) => {
    setParams((current) => {
      const copy = new URLSearchParams(current);
      Object.entries(next).forEach(([key, value]) => {
        if (value == null || value === "") copy.delete(key);
        else copy.set(key, value);
      });
      return copy;
    }, { replace: true });
  };

  useEffect(() => {
    setQueryDraft(qParam);
  }, [qParam]);

  useEffect(() => {
    if (debouncedQuery === qParam) return;
    patch({ q: debouncedQuery || null, page: "0" });
  }, [debouncedQuery]);

  useEffect(() => {
    if (shopId == null) return;
    let alive = true;
    setChannelsLoading(true);
    listCustomerInsightChannels(shopId)
      .then((rows) => { if (alive) setChannels(rows); })
      .catch(() => { if (alive) setChannels([]); })
      .finally(() => { if (alive) setChannelsLoading(false); });
    return () => { alive = false; };
  }, [shopId]);

  useEffect(() => {
    if (shopId == null) return;
    const req = ++listReq.current;
    const controller = new AbortController();
    setFetching(true);
    setError(null);
    listCustomerInsights(shopId, {
      q: qParam || undefined,
      channel: channel || undefined,
      status: status || undefined,
      segment: segment || undefined,
      joinedFrom: joinedFrom || undefined,
      joinedTo: joinedTo || undefined,
      sort,
      direction,
      page,
      size,
    }, controller.signal)
      .then((result) => {
        if (listReq.current !== req) return;
        setItems(result.items);
        setStats(result.stats);
        setTotal(result.total);
        setTotalPages(result.totalPages);
        if (selectedKey && result.items.length === 0) {
          patch({ selected: null });
        } else if (!selectedKey && result.items[0]) {
          patch({ selected: result.items[0].key });
        } else if (selectedKey && !result.items.some((row) => row.key === selectedKey) && result.items[0]) {
          patch({ selected: result.items[0].key });
        }
      })
      .catch((caught) => {
        if (controller.signal.aborted || listReq.current !== req) return;
        setError(caught instanceof ApiError ? caught.message : t("customers.error"));
        setItems([]);
        setStats(null);
      })
      .finally(() => {
        if (listReq.current === req) setFetching(false);
      });
    return () => controller.abort();
  }, [shopId, qParam, channel, status, segment, joinedFrom, joinedTo, sort, direction, page, size, reload, t]);

  useEffect(() => {
    if (shopId == null || !selectedKey) {
      setDetail(null);
      setOrders([]);
      return;
    }
    const req = ++detailReq.current;
    const controller = new AbortController();
    setDetailLoading(true);
    getCustomerInsight(shopId, selectedKey, controller.signal)
      .then((result) => {
        if (detailReq.current !== req) return;
        setDetail(result);
      })
      .catch((caught) => {
        if (controller.signal.aborted || detailReq.current !== req) return;
        setDetail(null);
        if (
          caught instanceof ApiError &&
          (caught.code === "CUSTOMER_NOT_FOUND" || caught.status === 404)
        ) {
          patch({ selected: null });
        }
      })
      .finally(() => {
        if (detailReq.current === req) setDetailLoading(false);
      });

    if (canReadOrders) {
      setOrdersLoading(true);
      setOrdersError(null);
      listCustomerInsightOrders(shopId, selectedKey, 0, 5, controller.signal)
        .then((result) => {
          if (detailReq.current !== req) return;
          setOrders(result.items);
          setOrdersTotal(result.total);
        })
        .catch((caught) => {
          if (controller.signal.aborted || detailReq.current !== req) return;
          setOrders([]);
          setOrdersTotal(0);
          setOrdersError(caught instanceof ApiError ? caught.message : t("customers.detail.ordersError"));
        })
        .finally(() => {
          if (detailReq.current === req) setOrdersLoading(false);
        });
    } else {
      setOrders([]);
      setOrdersTotal(0);
      setOrdersError(null);
      setOrdersLoading(false);
    }
    return () => controller.abort();
  }, [shopId, selectedKey, canReadOrders, ordersReload, t]);

  const channelBars = useMemo(() => {
    const rows = detail?.channels || [];
    const sum = rows.reduce((acc, row) => acc + row.count, 0) || 1;
    return rows.map((row) => ({ name: row.channel, pct: Math.round((row.count / sum) * 100) }));
  }, [detail]);

  if (loading) return <main className="auth-loading">{t("customers.title")}...</main>;
  if (!user) {
    window.location.replace("/login?next=/merchant/customers");
    return null;
  }
  if (hasPlatformAccess(user) && !hasMerchantAccess(user)) {
    window.location.replace("/admin");
    return null;
  }
  if (!hasMerchantAccess(user)) {
    window.location.replace("/");
    return null;
  }

  const notify = (text: string) => {
    setNotice(text);
    window.setTimeout(() => setNotice(""), 1900);
  };

  const reset = () => {
    setQueryDraft("");
    setParams(new URLSearchParams(), { replace: true });
  };

  const allLabel = t("customers.filters.all");
  const channelOptions = [allLabel, ...channels];
  const statusOptions = [allLabel, t("customers.status.ACTIVE"), t("customers.status.INACTIVE")];
  const segmentOptions = [allLabel, t("customers.segment.VIP"), t("customers.segment.REPEAT"), t("customers.segment.GENERAL")];
  const sortOptions = [
    ["lastOrderAt", t("customers.sort.lastOrderAt")],
    ["totalSpent", t("customers.sort.totalSpent")],
    ["orderCount", t("customers.sort.orderCount")],
    ["name", t("customers.sort.name")],
  ] as const;

  const selected = items.find((row) => row.key === selectedKey) || detail?.insight || null;
  const selectedAvatar = selected ? avatarForName(selected.displayName) : null;
  const addressLine = detail?.addresses?.[0]
    ? [detail.addresses[0].district, detail.addresses[0].province].filter(Boolean).join(", ")
    : "";
  const canCopyPhone = Boolean(
    selected?.phone && !selected.piiMasked && canReadPii && typeof navigator !== "undefined" && navigator.clipboard,
  );
  const copyPhone = () => {
    if (!selected?.phone || !navigator.clipboard) return;
    navigator.clipboard.writeText(selected.phone).then(
      () => notify(t("customers.detail.copied")),
      () => undefined,
    );
  };

  const fromIdx = total === 0 ? 0 : page * size + 1;
  const toIdx = Math.min((page + 1) * size, total);
  const empty = !fetching && !error && items.length === 0;
  const filteredEmpty = empty && Boolean(qParam || channel || status || segment || joinedFrom || joinedTo);
  const generalCount = Math.max(0, (stats?.totalCustomers ?? 0) - (stats?.repeatCustomers ?? 0));

  return (
    <main className="merchant-shell cm-shell">
      <header className="merchant-topbar">
        <MerchantBrand />
        <MerchantTopbarSearch />
        <MerchantTopbarActions />
      </header>
      <MerchantSidebar active="customers"/>
      <section className="pm-content cm-content">
        <header className="pm-heading cm-heading">
          <div>
            <h1>
              <UsersRound />
              {t("customers.title")}
            </h1>
            <p>{t("customers.subtitle")}</p>
          </div>
          <div className="pm-actions">
            <button type="button" disabled title={canCreate ? t("customers.comingSoon") : deniedTitle(canCreate)} className="primary">
              <UserPlus />
              {t("customers.add")} <ChevronDown />
            </button>
            <button
              type="button"
              disabled={!canReadCustomers || exporting}
              title={
                !canReadCustomers
                  ? deniedTitle(canReadCustomers)
                  : !canReadPii
                    ? t("customers.exportMasked")
                    : undefined
              }
              onClick={() => {
                if (shopId == null || !canReadCustomers || exporting) return;
                setExporting(true);
                setError(null);
                exportCustomerInsights(shopId, {
                  q: qParam || undefined,
                  channel: channel || undefined,
                  status: status || undefined,
                  segment: segment || undefined,
                  joinedFrom: joinedFrom || undefined,
                  joinedTo: joinedTo || undefined,
                  sort,
                  direction,
                })
                  .catch((caught) => {
                    const code = caught instanceof ApiError ? caught.code : "";
                    setError(
                      code === "EXPORT_TOO_LARGE"
                        ? t("customers.exportTooLarge")
                        : caught instanceof ApiError
                          ? caught.message
                          : t("customers.error"),
                    );
                  })
                  .finally(() => setExporting(false));
              }}
            >
              <Download />
              {exporting ? t("customers.exporting") : t("customers.export")}
            </button>
          </div>
        </header>
        <section className="pm-kpis cm-kpis">
          {fetching && !stats ? (
            [0, 1, 2, 3].map((i) => (
              <article key={i} className="all">
                <Skeleton height={64} />
              </article>
            ))
          ) : (
            <>
              <article className="all">
                <span><UsersRound /></span>
                <div>
                  <small>{t("customers.kpi.total")}</small>
                  <b className="num">{(stats?.totalCustomers ?? 0).toLocaleString(localeTag)}</b>
                </div>
              </article>
              <article className="ready">
                <span><UsersRound /></span>
                <div>
                  <small>{t("customers.kpi.newThisMonth")}</small>
                  <b className="num">{(stats?.newThisMonth ?? 0).toLocaleString(localeTag)}</b>
                  <em>{t("customers.kpi.thisMonth")}</em>
                </div>
              </article>
              <article className="empty">
                <span><Heart /></span>
                <div>
                  <small>{t("customers.kpi.repeat")}</small>
                  <b className="num">{(stats?.repeatCustomers ?? 0).toLocaleString(localeTag)}</b>
                  <em>{t("customers.kpi.repeatHint")}</em>
                </div>
              </article>
              <article className="vip">
                <span><ShoppingCart /></span>
                <div>
                  <small>{t("customers.kpi.avgOrder")}</small>
                  <b className="num">{money(stats?.avgOrderValue, localeTag)}</b>
                </div>
              </article>
            </>
          )}
        </section>
        <div className="cm-grid">
          <section className="cm-list">
            <div className="cm-tabs">
              {(
                [
                  ["", t("customers.tabs.all"), stats?.totalCustomers ?? 0],
                  ["VIP", t("customers.tabs.vip"), stats?.vipCustomers ?? 0],
                  ["REPEAT", t("customers.tabs.repeat"), stats?.repeatCustomers ?? 0],
                  ["GENERAL", t("customers.tabs.general"), generalCount],
                ] as const
              ).map(([value, label, count]) => (
                <button
                  key={value || "all"}
                  type="button"
                  className={segment === value ? "active" : ""}
                  onClick={() => patch({ segment: value || null, page: "0" })}
                >
                  {label} ({Number(count).toLocaleString(localeTag)})
                </button>
              ))}
            </div>
            <form
              className="cm-filters"
              onSubmit={(e) => {
                e.preventDefault();
                patch({ q: queryDraft.trim() || null, page: "0" });
              }}
            >
              <label className="cm-search">
                <Search />
                <input
                  aria-label={t("customers.searchLabel")}
                  value={queryDraft}
                  onChange={(e) => setQueryDraft(e.target.value)}
                  placeholder={t("customers.searchPlaceholder")}
                />
              </label>
              <SearchableSelect
                label={t("customers.filters.channel")}
                options={channelOptions}
                value={channel || allLabel}
                loading={channelsLoading}
                onChange={(picked) => patch({ channel: picked === allLabel ? null : picked, page: "0" })}
              />
              <SearchableSelect
                label={t("customers.filters.status")}
                options={statusOptions}
                value={status === "ACTIVE" ? t("customers.status.ACTIVE") : status === "INACTIVE" ? t("customers.status.INACTIVE") : allLabel}
                onChange={(picked) => {
                  const next =
                    picked === t("customers.status.ACTIVE") ? "ACTIVE"
                      : picked === t("customers.status.INACTIVE") ? "INACTIVE"
                        : null;
                  patch({ status: next, page: "0" });
                }}
              />
              <SearchableSelect
                label={t("customers.filters.segment")}
                options={segmentOptions}
                value={
                  segment === "VIP" ? t("customers.segment.VIP")
                    : segment === "REPEAT" ? t("customers.segment.REPEAT")
                      : segment === "GENERAL" ? t("customers.segment.GENERAL")
                        : allLabel
                }
                onChange={(picked) => {
                  const next =
                    picked === t("customers.segment.VIP") ? "VIP"
                      : picked === t("customers.segment.REPEAT") ? "REPEAT"
                        : picked === t("customers.segment.GENERAL") ? "GENERAL"
                          : null;
                  patch({ segment: next, page: "0" });
                }}
              />
              <DateRangePicker
                className="cm-filter-date"
                label={t("customers.filters.joinedRange")}
                from={joinedFrom}
                to={joinedTo}
                clearLabel={t("customers.filters.clear")}
                applyLabel={t("customers.filters.apply")}
                presetsLabel={{
                  d7: t("customers.filters.presets.d7"),
                  d30: t("customers.filters.presets.d30"),
                  month: t("customers.filters.presets.month"),
                }}
                onChange={({ from, to }) => patch({ joinedFrom: from || null, joinedTo: to || null, page: "0" })}
              />
              <button className="cm-reset" type="button" onClick={reset}>
                {t("customers.reset")}
              </button>
            </form>
            <div className="cm-table-wrap" aria-busy={fetching}>
              {error ? (
                <LoadError message={error} onRetry={() => setReload((n) => n + 1)} retryLabel={t("customers.retry")} />
              ) : fetching && items.length === 0 ? (
                <SkeletonTable rows={6} cols={6} />
              ) : empty ? (
                <div className="cm-empty">{filteredEmpty ? t("customers.searchEmpty") : t("customers.empty")}</div>
              ) : (
                <table>
                  <thead>
                    <tr>
                      <th>
                        <input
                          aria-label={t("customers.table.name")}
                          type="checkbox"
                          checked={items.length > 0 && checked.length === items.length}
                          onChange={() => setChecked(checked.length === items.length ? [] : items.map((c) => c.key))}
                        />
                      </th>
                      <th>#</th>
                      <th>{t("customers.table.name")}</th>
                      <th>{t("customers.table.channel")}</th>
                      <th>{t("customers.table.orders")}</th>
                      <th>{t("customers.table.spent")}</th>
                      <th>{t("customers.table.group")}</th>
                      <th>{t("customers.table.status")}</th>
                      <th>{t("customers.table.joined")}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {items.map((c, i) => {
                      const avatar = avatarForName(c.displayName);
                      return (
                        <tr
                          className={selectedKey === c.key ? "selected" : ""}
                          key={c.key}
                          onClick={() => patch({ selected: c.key })}
                        >
                          <td>
                            <input
                              aria-label={c.displayName}
                              type="checkbox"
                              checked={checked.includes(c.key)}
                              onClick={(e) => e.stopPropagation()}
                              onChange={() =>
                                setChecked((xs) => (xs.includes(c.key) ? xs.filter((x) => x !== c.key) : [...xs, c.key]))
                              }
                            />
                          </td>
                          <td className="num">{page * size + i + 1}</td>
                          <td>
                            <div className="cm-person">
                              <span style={{ background: avatar.color }}>{avatar.initial}</span>
                              <p>
                                <b>{c.displayName}</b>
                                <small title={c.piiMasked ? t("customers.piiMasked") : undefined}>{c.email || "—"}</small>
                              </p>
                            </div>
                          </td>
                          <td><Channel name={c.channel} /></td>
                          <td className="num">{c.orderCount.toLocaleString(localeTag)}</td>
                          <td><b className="num">{money(c.totalSpent, localeTag)}</b></td>
                          <td>
                            <span className={`cm-group ${c.segment === "VIP" ? "vip" : c.segment === "REPEAT" ? "regular" : "normal"}`}>
                              {c.segment === "VIP" && "♛ "}
                              {t(`customers.segment.${c.segment}`)}
                            </span>
                          </td>
                          <td>
                            <span className={`cm-status ${c.status === "ACTIVE" ? "on" : "off"}`}>
                              {t(`customers.status.${c.status}`)}
                            </span>
                          </td>
                          <td className="num">{formatJoined(c.joinedAt, localeTag).split(", ").map((x) => <span key={x}>{x}</span>)}</td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              )}
            </div>
            <div className="cm-pagination">
              <span>{t("customers.table.showing", { from: fromIdx, to: toIdx, total })}</span>
              <label>
                {t("customers.table.perPage")}{" "}
                <select
                  aria-label={t("customers.table.perPage")}
                  value={size}
                  onChange={(e) => patch({ size: e.target.value, page: "0" })}
                >
                  <option value="8">8</option>
                  <option value="16">16</option>
                  <option value="24">24</option>
                </select>{" "}
                {t("customers.table.perPageSuffix")}
              </label>
              <SearchableSelect
                compact
                label={t("customers.sort.label")}
                options={sortOptions.map(([, label]) => label)}
                value={sortOptions.find(([value]) => value === sort)?.[1] || t("customers.sort.lastOrderAt")}
                onChange={(picked) => {
                  const next = sortOptions.find(([, label]) => label === picked)?.[0] || "lastOrderAt";
                  patch({ sort: next, page: "0" });
                }}
              />
              <nav>
                <button type="button" aria-label="prev" disabled={page <= 0} onClick={() => patch({ page: String(page - 1) })}>
                  <ChevronLeft />
                </button>
                <button type="button" className="active">{page + 1}</button>
                <span>/ {Math.max(totalPages, 1)}</span>
                <button type="button" aria-label="next" disabled={page + 1 >= totalPages} onClick={() => patch({ page: String(page + 1) })}>
                  <ChevronRight />
                </button>
              </nav>
            </div>
          </section>
          <aside className="cm-detail">
            {detailLoading && !detail ? (
              <div style={{ padding: 16 }}><Skeleton height={220} /></div>
            ) : !selected ? (
              <div className="cm-empty">{t("customers.empty")}</div>
            ) : (
              <>
                <header>
                  <div className="cm-avatar" style={{ background: selectedAvatar?.color }} aria-hidden="true">
                    {initialsOf(selected.displayName)}
                  </div>
                  <div className="cm-detail-id">
                    <h2 title={selected.displayName}>{selected.displayName}</h2>
                    <div className="cm-detail-meta">
                      <span className={`cm-seg-badge ${String(selected.segment || "").toLowerCase()}`}>
                        {selected.segment === "VIP" ? <Crown aria-hidden="true" /> : null}
                        {t(`customers.segment.${selected.segment}`)}
                      </span>
                      <small>{t("customers.since", { date: formatJoined(selected.joinedAt, localeTag) })}</small>
                    </div>
                  </div>
                  <button type="button" disabled title={canUpdate ? t("customers.comingSoon") : deniedTitle(canUpdate)}>
                    <Pencil />
                    {t("customers.edit")}
                  </button>
                </header>
                <dl className="cm-contact">
                  <div className="cm-contact-row" title={selected.piiMasked ? t("customers.piiMasked") : undefined}>
                    <span className="cm-icon-chip" aria-hidden="true"><Mail /></span>
                    <dt>{t("customers.detail.email")}</dt>
                    <dd>
                      {selected.email ? selected.email : <span className="cm-missing">{t("customers.detail.noData")}</span>}
                      {selected.email && selected.piiMasked ? <EyeOff className="cm-masked" aria-label={t("customers.piiMasked")} /> : null}
                    </dd>
                  </div>
                  <div className="cm-contact-row" title={selected.piiMasked ? t("customers.piiMasked") : undefined}>
                    <span className="cm-icon-chip" aria-hidden="true"><Phone /></span>
                    <dt>{t("customers.detail.phone")}</dt>
                    <dd>
                      {selected.phone ? <span className="num">{selected.phone}</span> : <span className="cm-missing">{t("customers.detail.noData")}</span>}
                      {selected.phone && selected.piiMasked ? <EyeOff className="cm-masked" aria-label={t("customers.piiMasked")} /> : null}
                      {canCopyPhone ? (
                        <button type="button" className="cm-copy" onClick={copyPhone} aria-label={t("customers.detail.copyPhone")} title={t("customers.detail.copyPhone")}>
                          <Copy aria-hidden="true" />
                        </button>
                      ) : null}
                    </dd>
                  </div>
                  <div className="cm-contact-row">
                    <span className="cm-icon-chip" aria-hidden="true"><MapPin /></span>
                    <dt>{t("customers.detail.address")}</dt>
                    <dd>{addressLine ? addressLine : <span className="cm-missing">{t("customers.detail.noData")}</span>}</dd>
                  </div>
                </dl>
                <div className="cm-customer-kpis">
                  <p className="tone-primary">
                    <span className="cm-icon-chip" aria-hidden="true"><ShoppingCart /></span>
                    <b className="num" title={selected.orderCount.toLocaleString(localeTag)}>{selected.orderCount.toLocaleString(localeTag)}</b>
                    <small>{t("customers.detail.orders")}</small>
                  </p>
                  <p className="tone-success">
                    <span className="cm-icon-chip" aria-hidden="true"><Wallet /></span>
                    <b className="num" title={money(selected.totalSpent, localeTag)}>{money(selected.totalSpent, localeTag)}</b>
                    <small>{t("customers.detail.spent")}</small>
                  </p>
                  <p className="tone-warning">
                    <span className="cm-icon-chip" aria-hidden="true"><ReceiptText /></span>
                    <b className="num">
                      {money(
                        selected.orderCount > 0 ? Number(selected.totalSpent) / selected.orderCount : 0,
                        localeTag,
                      )}
                    </b>
                    <small>{t("customers.detail.avg")}</small>
                  </p>
                </div>
                <section>
                  <h3>{t("customers.channelsTitle")}</h3>
                  {channelBars.length === 0 ? (
                    <p className="cm-missing">{t("customers.detail.noData")}</p>
                  ) : (
                    channelBars.map((row) => (
                      <div className="cm-bar" key={row.name}>
                        <Channel name={row.name} />
                        <i role="presentation"><b style={{ width: `${Math.min(100, Math.max(0, row.pct))}%` }} /></i>
                        <span className="num">{row.pct}%</span>
                      </div>
                    ))
                  )}
                </section>
                <section className="cm-note">
                  <h3>{t("customers.detail.orderHistory")}</h3>
                  {!canReadOrders ? (
                    <p className="cm-empty-state"><ShoppingBag aria-hidden="true" />{t("customers.detail.ordersDenied")}</p>
                  ) : ordersLoading ? (
                    <div className="cm-order-skel" aria-busy="true">
                      {[0, 1, 2].map((i) => <Skeleton key={i} height={48} />)}
                    </div>
                  ) : ordersError ? (
                    <LoadError message={ordersError} onRetry={() => setOrdersReload((n) => n + 1)} retryLabel={t("customers.retry")} />
                  ) : orders.length === 0 ? (
                    <p className="cm-empty-state"><ShoppingBag aria-hidden="true" />{t("customers.detail.noOrders")}</p>
                  ) : (
                    <>
                      <ul className="cm-order-list">
                        {orders.map((order) => (
                          <li key={order.orderId}>
                            <button
                              type="button"
                              className={`cm-order-row${openOrderId === order.orderId ? " active" : ""}`}
                              aria-label={t("customers.detail.openOrder")}
                              onClick={() => setOpenOrderId(order.orderId)}
                            >
                              <span>
                                <b className="num">{order.orderNumber}</b>
                                <small className="num">{formatOrderAt(order.placedAt, localeTag)}</small>
                              </span>
                              <em className={`cm-pill ${statusTone(order.status)}`}>
                                {t(`orderManagement.status.${order.status}`, { defaultValue: order.status })}
                              </em>
                              <strong className="num">{money(order.grandTotal, localeTag)}</strong>
                              <ChevronRight className="cm-chevron" aria-hidden="true" />
                            </button>
                          </li>
                        ))}
                      </ul>
                      {ordersTotal > 5 && selectedKey ? (
                        <a
                          className="cm-order-view-all"
                          href={customerOrdersUrl(selectedKey, selected.displayName)}
                          target="_blank"
                          rel="noopener noreferrer"
                        >
                          {t("customers.detail.viewAll", { count: ordersTotal })}
                          <ExternalLink aria-hidden="true" />
                        </a>
                      ) : null}
                    </>
                  )}
                </section>
                <div className="cm-detail-actions">
                  <button type="button" disabled title={canReadPii ? t("customers.comingSoon") : deniedTitle(canReadPii)}>
                    <Mail />
                    {t("customers.sendMessage")}
                  </button>
                  {canReadOrders && selectedKey ? (
                    <a
                      className="primary"
                      href={customerOrdersUrl(selectedKey, selected.displayName)}
                      target="_blank"
                      rel="noopener noreferrer"
                    >
                      <ShoppingBag />
                      {t("customers.viewOrders")}
                    </a>
                  ) : (
                    <button type="button" className="primary" disabled title={deniedTitle(canReadOrders)}>
                      <ShoppingBag />
                      {t("customers.viewOrders")}
                    </button>
                  )}
                </div>
              </>
            )}
          </aside>
        </div>
      </section>
      {shopId != null && openOrderId ? (
        <MerchantOrderDrawer
          shopId={shopId}
          orderId={openOrderId}
          onClose={() => setOpenOrderId(null)}
          onChanged={() => {
            setOrdersReload((n) => n + 1);
            setReload((n) => n + 1);
          }}
          notify={(text) => notify(text)}
        />
      ) : null}
      {notice && (
        <div className="merchant-toast" role="status">
          {notice}
        </div>
      )}
    </main>
  );
}
