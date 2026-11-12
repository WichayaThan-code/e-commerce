import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Bell,
  Package,
  CreditCard,
  Ban,
  RotateCcw,
  UserPlus,
  TriangleAlert,
  BadgeCheck,
  CircleX,
  ShieldOff,
  ShieldCheck,
  FileWarning,
  Star,
  Truck,
  PackageCheck,
  Users,
  UserMinus,
} from "lucide-react";
import { useTranslation } from "react-i18next";
import { FEATURES } from "../../config/features";
import { useImportActivity } from "../../contexts/ImportActivityContext";
import { useActiveShop } from "../../contexts/ActiveShopContext";
import {
  MERCHANT_NOTIFICATION_PREFS_EVENT,
  merchantNotificationsApi,
  type MerchantNotificationItem,
  type MerchantNotificationType,
} from "../../services/api";

const POLL_MS = 60_000;

function hrefFor(item: MerchantNotificationItem): string {
  switch (item.type) {
    case "ORDER_NEW":
      return "/merchant/orders";
    case "ORDER_PAID":
    case "ORDER_CANCELLED":
    case "REFUND_REQUESTED":
    case "REFUND_DISPUTED":
    case "REFUND_COMPLETED":
    case "PAYMENT_SLIP_PENDING":
    case "SHIPMENT_EXCEPTION":
      return item.orderNo
        ? `/merchant/orders?q=${encodeURIComponent(item.orderNo)}`
        : "/merchant/orders";
    case "CUSTOMER_NEW":
      return item.customerKey
        ? `/merchant/customers?selected=${encodeURIComponent(item.customerKey)}`
        : "/merchant/customers";
    case "LOW_STOCK": {
      const q = item.titleParams?.skuCode
        ? `q=${encodeURIComponent(String(item.titleParams.skuCode))}&`
        : "";
      return `/merchant/products?${q}stock=low`;
    }
    case "PRODUCT_REVIEW_NEW": {
      const name = item.titleParams?.productName;
      return name
        ? `/merchant/products?q=${encodeURIComponent(String(name))}`
        : "/merchant/products";
    }
    case "SHOP_APPROVED":
    case "SHOP_RESTORED":
    case "SHOP_PLAN_CHANGED":
      return "/merchant/dashboard";
    case "SHOP_SUSPENDED":
      return "/merchant/reinstatement";
    case "SHOP_REJECTED":
      return "/merchant/review";
    case "SHOP_MEMBER_ROLE_CHANGED":
    case "SHOP_MEMBER_REMOVED":
      return "/merchant/settings";
    default:
      return "/merchant/dashboard";
  }
}

function TypeIcon({ type }: { type: MerchantNotificationType }) {
  switch (type) {
    case "ORDER_NEW":
      return <Package aria-hidden />;
    case "ORDER_PAID":
      return <CreditCard aria-hidden />;
    case "ORDER_CANCELLED":
      return <Ban aria-hidden />;
    case "REFUND_REQUESTED":
    case "REFUND_DISPUTED":
    case "REFUND_COMPLETED":
      return <RotateCcw aria-hidden />;
    case "PAYMENT_SLIP_PENDING":
      return <FileWarning aria-hidden />;
    case "CUSTOMER_NEW":
      return <UserPlus aria-hidden />;
    case "LOW_STOCK":
      return <TriangleAlert aria-hidden />;
    case "PRODUCT_REVIEW_NEW":
      return <Star aria-hidden />;
    case "SHIPMENT_EXCEPTION":
      return <Truck aria-hidden />;
    case "SHOP_APPROVED":
      return <BadgeCheck aria-hidden />;
    case "SHOP_REJECTED":
      return <CircleX aria-hidden />;
    case "SHOP_SUSPENDED":
      return <ShieldOff aria-hidden />;
    case "SHOP_RESTORED":
      return <ShieldCheck aria-hidden />;
    case "SHOP_PLAN_CHANGED":
      return <PackageCheck aria-hidden />;
    case "SHOP_MEMBER_ROLE_CHANGED":
      return <Users aria-hidden />;
    case "SHOP_MEMBER_REMOVED":
      return <UserMinus aria-hidden />;
    default:
      return <Bell aria-hidden />;
  }
}

function relativeTime(iso: string, locale: string): string {
  const then = Date.parse(iso);
  if (!Number.isFinite(then)) return "";
  const seconds = Math.round((then - Date.now()) / 1000);
  const rtf = new Intl.RelativeTimeFormat(locale, { numeric: "auto" });
  const abs = Math.abs(seconds);
  if (abs < 60) return rtf.format(seconds, "second");
  const minutes = Math.round(seconds / 60);
  if (Math.abs(minutes) < 60) return rtf.format(minutes, "minute");
  const hours = Math.round(minutes / 60);
  if (Math.abs(hours) < 24) return rtf.format(hours, "hour");
  const days = Math.round(hours / 24);
  return rtf.format(days, "day");
}

export default function MerchantBell() {
  const { t, i18n } = useTranslation("merchant");
  const navigate = useNavigate();
  const { notice, attention, dismiss } = useImportActivity();
  const { shopId, ready } = useActiveShop();
  const [open, setOpen] = useState(false);
  const panelRef = useRef<HTMLDivElement>(null);
  const working = notice != null && notice.phase !== "done" && notice.phase !== "error";
  const apiOn = FEATURES.merchantNotificationsApi;

  const [unread, setUnread] = useState(0);
  const [items, setItems] = useState<MerchantNotificationItem[]>([]);
  const [nextCursor, setNextCursor] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [listError, setListError] = useState(false);
  const requestGen = useRef(0);

  const refreshUnread = useCallback(() => {
    if (!apiOn || shopId == null || document.hidden) return;
    const gen = requestGen.current;
    merchantNotificationsApi.unreadCount(shopId)
      .then((row) => {
        if (gen !== requestGen.current) return;
        setUnread(row.count);
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === "AbortError") return;
        // Keep last known unread; do not force 0 on transient failures.
      });
  }, [apiOn, shopId]);

  const loadList = useCallback(async (cursor?: string | null, append = false) => {
    if (!apiOn || shopId == null) return;
    if (append) setLoadingMore(true);
    else setLoading(true);
    if (!append) setListError(false);
    try {
      const page = await merchantNotificationsApi.list(shopId, { cursor: cursor || undefined, size: 20 });
      setItems((prev) => append ? [...prev, ...page.items] : page.items);
      setNextCursor(page.nextCursor);
      setListError(false);
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") return;
      if (!append) {
        setListError(true);
        // Do not clear items to an empty success state on failure.
      }
    } finally {
      setLoading(false);
      setLoadingMore(false);
    }
  }, [apiOn, shopId]);

  const openRef = useRef(open);
  openRef.current = open;
  const loadListRef = useRef(loadList);
  loadListRef.current = loadList;

  useEffect(() => {
    requestGen.current += 1;
    setUnread(0);
    setItems([]);
    setNextCursor(null);
    setListError(false);
    setOpen(false);
    if (!apiOn || shopId == null || !ready) return;
    refreshUnread();
    const onFocus = () => refreshUnread();
    const onVis = () => { if (!document.hidden) refreshUnread(); };
    const onPrefs = (event: Event) => {
      const detail = (event as CustomEvent<{ shopId?: number }>).detail;
      if (detail?.shopId != null && detail.shopId !== shopId) return;
      refreshUnread();
      if (openRef.current) void loadListRef.current(null, false);
    };
    window.addEventListener("focus", onFocus);
    document.addEventListener("visibilitychange", onVis);
    window.addEventListener(MERCHANT_NOTIFICATION_PREFS_EVENT, onPrefs);
    const timer = window.setInterval(() => {
      if (!document.hidden) refreshUnread();
    }, POLL_MS);
    return () => {
      window.removeEventListener("focus", onFocus);
      document.removeEventListener("visibilitychange", onVis);
      window.removeEventListener(MERCHANT_NOTIFICATION_PREFS_EVENT, onPrefs);
      window.clearInterval(timer);
    };
  }, [apiOn, shopId, ready, refreshUnread]);

  useEffect(() => {
    if (attention > 0) setOpen(true);
  }, [attention]);

  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") setOpen(false);
    };
    const onPointer = (event: MouseEvent) => {
      if (!panelRef.current?.contains(event.target as Node)) setOpen(false);
    };
    window.addEventListener("keydown", onKey);
    document.addEventListener("mousedown", onPointer);
    return () => {
      window.removeEventListener("keydown", onKey);
      document.removeEventListener("mousedown", onPointer);
    };
  }, [open]);

  useEffect(() => {
    if (!open || !apiOn || shopId == null) return;
    void loadList();
  }, [open, apiOn, shopId, loadList]);

  // Unread count wins over a lingering import notice; import only badges while running or when unread is clear.
  const badge = working
    ? String(notice.percent)
    : apiOn && unread > 0
      ? (unread > 99 ? "99+" : String(unread))
      : notice
        ? "1"
        : null;

  const onItemClick = async (item: MerchantNotificationItem) => {
    if (apiOn && shopId != null && !item.read) {
      setItems((prev) => prev.map((row) => (row.key === item.key ? { ...row, read: true } : row)));
      setUnread((n) => Math.max(0, n - 1));
      try {
        await merchantNotificationsApi.markRead(shopId, item.key);
      } catch {
        refreshUnread();
      }
    }
    setOpen(false);
    navigate(hrefFor(item));
  };

  const onReadAll = async () => {
    if (!apiOn || shopId == null || unread <= 0) return;
    const previous = unread;
    setItems((prev) => prev.map((row) => ({ ...row, read: true })));
    setUnread(0);
    try {
      await merchantNotificationsApi.markAllRead(shopId);
    } catch {
      setUnread(previous);
      void loadList(null, false);
      refreshUnread();
    }
  };

  const titleOf = (item: MerchantNotificationItem) =>
    t(`notifications.types.${item.type}`, { ...item.titleParams, defaultValue: item.type });

  const showFeed = apiOn && shopId != null;
  const feedEmpty = !loading && !listError && items.length === 0;
  const importEmpty = notice == null;

  return (
    <div className="merchant-notify" ref={panelRef}>
      <button
        className="merchant-bell"
        type="button"
        aria-label={t("catalog.bell.label")}
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
      >
        <Bell />
        {badge ? <i>{badge}</i> : null}
      </button>
      {open ? (
        <section className="merchant-notify-panel" role="dialog" aria-label={t("catalog.bell.label")}>
          <header>
            <b>{showFeed ? t("notifications.title") : t("catalog.bell.label")}</b>
            {showFeed && unread > 0 ? (
              <button type="button" className="merchant-notify-read-all" onClick={() => void onReadAll()}>
                {t("notifications.readAll")}
              </button>
            ) : null}
          </header>

          {notice != null ? (
            <div className="merchant-notify-import">
              {showFeed ? <h3>{t("notifications.importing")}</h3> : null}
              <article className={notice.phase === "error" ? "fail" : undefined}>
                <b>{notice.title}</b>
                {working || notice.phase === "done" ? (
                  <div className="merchant-notify-track" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={notice.percent}>
                    <span style={{ width: `${notice.percent}%` }} />
                  </div>
                ) : null}
                <small>{notice.detail}</small>
                {notice.errors.length > 0 ? (
                  <ul>
                    {notice.errors.map((line) => <li key={line}>{line}</li>)}
                  </ul>
                ) : null}
                {notice.phase === "done" || notice.phase === "error" ? (
                  <button type="button" onClick={() => { dismiss(); setOpen(false); }}>{t("catalog.bell.dismiss")}</button>
                ) : null}
              </article>
            </div>
          ) : !showFeed ? (
            <p className="merchant-notify-empty">{t("catalog.bell.empty")}</p>
          ) : null}

          {showFeed ? (
            <div className="merchant-notify-feed">
              {loading ? (
                <div className="merchant-notify-skeleton" aria-busy="true">
                  <span /><span /><span />
                </div>
              ) : listError ? (
                <div className="merchant-notify-empty">
                  <p>{t("notifications.loadError")}</p>
                  <button type="button" className="merchant-notify-more" onClick={() => void loadList()}>
                    {t("notifications.retry")}
                  </button>
                </div>
              ) : feedEmpty && importEmpty ? (
                <p className="merchant-notify-empty">{t("notifications.empty")}</p>
              ) : items.length > 0 ? (
                <ul className="merchant-notify-list">
                  {items.map((item) => (
                    <li key={item.key}>
                      <button
                        type="button"
                        className={item.read ? "read" : "unread"}
                        onClick={() => void onItemClick(item)}
                      >
                        {!item.read ? <i className="merchant-notify-dot" aria-hidden /> : <i className="merchant-notify-dot spacer" aria-hidden />}
                        <span className="merchant-notify-icon"><TypeIcon type={item.type} /></span>
                        <span className="merchant-notify-body">
                          <b>{titleOf(item)}</b>
                          <small className="num">{relativeTime(item.createdAt, i18n.language)}</small>
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
              ) : null}
              {nextCursor ? (
                <button
                  type="button"
                  className="merchant-notify-more"
                  disabled={loadingMore}
                  onClick={() => void loadList(nextCursor, true)}
                >
                  {t("notifications.loadMore")}
                </button>
              ) : null}
            </div>
          ) : null}
        </section>
      ) : null}
    </div>
  );
}
