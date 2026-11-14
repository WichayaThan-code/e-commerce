import { useEffect, useMemo, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { useSearchParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import {
  ChevronDown,
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
  X,
} from "lucide-react";
import MerchantBrand from "../components/merchant/MerchantBrand";
import MerchantOrderDrawer from "../components/merchant/MerchantOrderDrawer";
import MerchantPagination from "../components/merchant/MerchantPagination";
import MerchantTopbarSearch from "../components/merchant/MerchantTopbarSearch";
import MerchantTopbarActions from "../components/merchant/MerchantTopbarActions";
import DateRangePicker from "../components/form/DateRangePicker";
import SearchableSelect from "../components/SearchableSelect";
import { LoadError, Skeleton } from "../components/feedback/Skeleton";
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

function CustomerTableSkeleton({ rows, headers, label }: { rows: number; headers: string[]; label: string }) {
  const widths = ["70%", "55%", "60%", "45%", "65%", "50%"];
  const list = Array.from({ length: rows }, (_, index) => index);
  return (
    <div aria-busy="true" aria-label={label}>
      <table>
        <thead>
          <tr>
            <th>{headers[0]}</th>
            <th>{headers[1]}</th>
            <th>{headers[2]}</th>
            <th>{headers[3]}</th>
            <th>{headers[4]}</th>
            <th>{headers[5]}</th>
            <th>{headers[6]}</th>
            <th>{headers[7]}</th>
          </tr>
        </thead>
        <tbody>
          {list.map((row) => (
            <tr key={row}>
              <td className="num"><Skeleton width={16} height={13} /></td>
              <td>
                <div className="cm-person">
                  <Skeleton width={36} height={36} radius="50%" />
                  <p style={{ flex: 1, display: "grid", gap: 6 }}>
                    <Skeleton width={widths[row % widths.length]} height={13} />
                    <Skeleton width="50%" height={11} />
                  </p>
                </div>
              </td>
              <td>
                <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
                  <Skeleton width={20} height={20} radius="50%" />
                  <Skeleton width={72} height={13} />
                </div>
              </td>
              <td className="num"><Skeleton width={24} height={13} /></td>
              <td><Skeleton width={72} height={13} /></td>
              <td><Skeleton width={72} height={26} radius={13} /></td>
              <td><Skeleton width={72} height={26} radius={13} /></td>
              <td className="num">
                <div style={{ display: "grid", gap: 4 }}>
                  <Skeleton width="80%" height={13} />
                  <Skeleton width="55%" height={11} />
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
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
  const size = [10, 20, 50].includes(Number(params.get("size"))) ? Number(params.get("size")) : 10;
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
    if (!selectedKey) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !openOrderId) patch({ selected: null });
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [selectedKey, openOrderId]);

  /* Drawer must live on document.body — .cm-shell overflow traps position:fixed and clips content. */
  useEffect(() => {
    if (!selectedKey) return;
    const { overflow, paddingRight } = document.body.style;
    const scrollbar = window.innerWidth - document.documentElement.clientWidth;
    document.body.style.overflow = "hidden";
    if (scrollbar > 0) document.body.style.paddingRight = `${scrollbar}px`;
    return () => {
      document.body.style.overflow = overflow;
      document.body.style.paddingRight = paddingRight;
    };
  }, [selectedKey]);

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
  const clearChipFilters = () => {
    setQueryDraft("");
    patch({
      q: null,
      joinedFrom: null,
      joinedTo: null,
      channel: null,
      status: null,
      page: "0",
    });
  };
  const dateChipValue =
    joinedFrom && joinedTo ? `${joinedFrom} – ${joinedTo}`
      : joinedFrom ? joinedFrom
        : joinedTo || "";
  const hasChipFilters = Boolean(qParam || joinedFrom || joinedTo || channel || status);
  const clearChipLabel = (label: string) => t("customers.filters.clearChip", { label });
  const exportCustomers = () => {
    if (shopId == null || !canReadCustomers || exporting || fetching || total <= 0) return;
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
            <button type="button" disabled title={deniedTitle(canCreate)} className="primary">
              <UserPlus />
              {t("customers.add")} <ChevronDown />
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
            <div className="pm-tabs" role="tablist">
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
                  role="tab"
                  aria-selected={segment === value}
                  className={segment === value ? "active" : ""}
                  onClick={() => patch({ segment: value || null, page: "0" })}
                >
                  {label}
                  <i>{Number(count).toLocaleString(localeTag)}</i>
                </button>
              ))}
            </div>
            <form
              className="om-filters"
              onSubmit={(e) => {
                e.preventDefault();
                patch({ q: queryDraft.trim() || null, page: "0" });
              }}
            >
              <div className="om-filters-main">
                <div className="om-filter-search">
                  <span className="form-label">{t("customers.searchLabel")}</span>
                  <label className="om-search">
                    <Search aria-hidden />
                    <input
                      aria-label={t("customers.searchLabel")}
                      value={queryDraft}
                      onChange={(e) => setQueryDraft(e.target.value)}
                      placeholder={t("customers.searchPlaceholder")}
                    />
                  </label>
                </div>
                <DateRangePicker
                  className="om-filter-range"
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
                <SearchableSelect
                  label={t("customers.sort.label")}
                  options={sortOptions.map(([, label]) => label)}
                  value={sortOptions.find(([value]) => value === sort)?.[1] || t("customers.sort.lastOrderAt")}
                  onChange={(picked) => {
                    const next = sortOptions.find(([, label]) => label === picked)?.[0] || "lastOrderAt";
                    patch({ sort: next, page: "0" });
                  }}
                />
                <div className="om-filters-actions">
                  <button type="button" onClick={reset}>
                    {t("customers.reset")}
                  </button>
                  <button type="submit" className="primary">
                    {t("customers.filters.search")}
                  </button>
                </div>
              </div>
            </form>
            <div className="pm-table-tools">
              <div className="pm-table-tools-chips" role="list" aria-label={t("customers.filters.active")}>
                {qParam ? (
                  <div className="om-filter-chip" role="listitem">
                    <span>{t("customers.searchLabel")}: {qParam}</span>
                    <button
                      type="button"
                      aria-label={clearChipLabel(t("customers.searchLabel"))}
                      onClick={() => {
                        setQueryDraft("");
                        patch({ q: null, page: "0" });
                      }}
                    >
                      <X size={14} aria-hidden="true" />
                    </button>
                  </div>
                ) : null}
                {joinedFrom || joinedTo ? (
                  <div className="om-filter-chip" role="listitem">
                    <span>{t("customers.filters.joinedRange")}: {dateChipValue}</span>
                    <button
                      type="button"
                      aria-label={clearChipLabel(t("customers.filters.joinedRange"))}
                      onClick={() => patch({ joinedFrom: null, joinedTo: null, page: "0" })}
                    >
                      <X size={14} aria-hidden="true" />
                    </button>
                  </div>
                ) : null}
                {channel ? (
                  <div className="om-filter-chip" role="listitem">
                    <span>{t("customers.filters.channel")}: {channel}</span>
                    <button
                      type="button"
                      aria-label={clearChipLabel(t("customers.filters.channel"))}
                      onClick={() => patch({ channel: null, page: "0" })}
                    >
                      <X size={14} aria-hidden="true" />
                    </button>
                  </div>
                ) : null}
                {status ? (
                  <div className="om-filter-chip" role="listitem">
                    <span>{t("customers.filters.status")}: {t(`customers.status.${status}`)}</span>
                    <button
                      type="button"
                      aria-label={clearChipLabel(t("customers.filters.status"))}
                      onClick={() => patch({ status: null, page: "0" })}
                    >
                      <X size={14} aria-hidden="true" />
                    </button>
                  </div>
                ) : null}
                {hasChipFilters ? (
                  <button type="button" className="om-clear-all" onClick={clearChipFilters}>
                    {t("customers.filters.clearAll")}
                  </button>
                ) : null}
              </div>
              <button
                type="button"
                className="pm-export-btn"
                disabled={!canReadCustomers || exporting || shopId == null || fetching || total <= 0}
                title={!canReadCustomers
                  ? (deniedTitle(canReadCustomers) || t("customers.exportHint"))
                  : total <= 0
                    ? t("customers.exportEmptyTitle")
                    : t("customers.exportHint")}
                onClick={exportCustomers}
              >
                <Download aria-hidden="true" />
                <span>{exporting ? t("customers.exporting") : t("customers.export")}</span>
              </button>
            </div>
            <div className="cm-table-wrap" aria-busy={fetching}>
              {error ? (
                <LoadError message={error} onRetry={() => setReload((n) => n + 1)} retryLabel={t("customers.retry")} />
              ) : fetching ? (
                <CustomerTableSkeleton
                  rows={Math.min(size, 8)}
                  label={t("customers.loading", { defaultValue: "กำลังโหลดลูกค้า..." })}
                  headers={["#", t("customers.table.name"), t("customers.table.channel"), t("customers.table.orders"), t("customers.table.spent"), t("customers.table.group"), t("customers.table.status"), t("customers.table.joined")]}
                />
              ) : empty ? (
                <div className="cm-empty">{filteredEmpty ? t("customers.searchEmpty") : t("customers.empty")}</div>
              ) : (
                <table>
                  <thead>
                    <tr>
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
                          <td className="num">{page * size + i + 1}</td>
                          <td>
                            <div className="cm-person">
                              <span style={{ background: avatar.color }}>{avatar.initial}</span>
                              <p>
                                <b title={c.displayName}>{c.displayName}</b>
                                <small title={c.piiMasked ? t("customers.piiMasked") : c.email || undefined}>{c.email || "—"}</small>
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
            <MerchantPagination
              page={page}
              pageCount={Math.max(totalPages, 1)}
              size={size}
              total={total}
              onPageChange={(next) => patch({ page: String(next) })}
              onSizeChange={(next) => patch({ size: String(next), page: "0" })}
            />
          </section>
        </div>
      </section>
      {selectedKey
        ? createPortal(
            <div
              className="om-drawer-overlay cm-drawer-overlay"
              onMouseDown={(event) => {
                if (event.target === event.currentTarget && !openOrderId) patch({ selected: null });
              }}
            >
              <aside
                className="om-drawer cm-detail"
                role="dialog"
                aria-modal="true"
                aria-label={selected?.displayName || t("customers.detail.title")}
              >
                {detailLoading && !selected ? (
                  <div className="om-drawer-body cm-detail-body"><Skeleton height={220} /></div>
                ) : !selected ? (
                  <div className="om-drawer-body cm-detail-body"><div className="cm-empty">{t("customers.empty")}</div></div>
                ) : (
                  <>
                    <header className="cm-detail-head">
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
                      <button type="button" className="cm-detail-edit" disabled title={deniedTitle(canUpdate)}>
                        <Pencil />
                        {t("customers.edit")}
                      </button>
                      <button
                        type="button"
                        className="om-drawer-close"
                        aria-label={t("customers.detail.close")}
                        onClick={() => patch({ selected: null })}
                      >
                        <X />
                      </button>
                    </header>
                    <div className="om-drawer-body cm-detail-body">
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
                                    <span className="cm-order-main">
                                      <b className="num">{order.orderNumber}</b>
                                      <small className="num">{formatOrderAt(order.placedAt, localeTag)}</small>
                                    </span>
                                    <span className="cm-order-side">
                                      <em className={`cm-pill ${statusTone(order.status)}`}>
                                        {t(`orderManagement.status.${order.status}`, { defaultValue: order.status })}
                                      </em>
                                      <strong className="num">{money(order.grandTotal, localeTag)}</strong>
                                    </span>
                                    <ChevronRight className="cm-chevron" aria-hidden="true" />
                                  </button>
                                </li>
                              ))}
                            </ul>
                            {ordersTotal > 5 ? (
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
                    </div>
                    <div className="om-drawer-actions cm-detail-actions">
                      <button type="button" disabled title={deniedTitle(canReadPii)}>
                        <Mail />
                        {t("customers.sendMessage")}
                      </button>
                      {canReadOrders ? (
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
            </div>,
            document.body,
          )
        : null}
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
