import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  Box,
  CheckCircle2,
  ChevronRight,
  Clock3,
  Download,
  Headphones,
  MapPin,
  Package,
  Plus,
  Search,
  Truck,
  X,
  XCircle,
} from "lucide-react";
import MerchantTopbarSearch from "../components/merchant/MerchantTopbarSearch";
import MerchantTopbarActions from "../components/merchant/MerchantTopbarActions";
import MerchantPagination from "../components/merchant/MerchantPagination";
import ShipmentDrawer, { statusClass } from "../components/merchant/ShipmentDrawer";
import ToggleSwitch from "../components/form/ToggleSwitch";
import { LoadError, SkeletonTable } from "../components/feedback/Skeleton";
import { formatBangkokDateTime, orderErrorMessage } from "../components/merchant/orderFormat";
import { useActiveShop } from "../contexts/ActiveShopContext";
import { useAuth } from "../contexts/AuthContext";
import { hasPlatformAccess, hasMerchantAccess } from "../services/permissions";
import { usePermissions } from "../hooks/usePermissions";
import { usePublicTranslation } from "../hooks/usePublicTranslation";
import { ApiError, merchantShipmentApi, type ShipmentSummary, type ShopCarriersResponse } from "../services/api";
import DateRangePicker from "../components/form/DateRangePicker";
import SearchableSelect from "../components/SearchableSelect";
import "../styles/merchant-dashboard.css";
import "../styles/product-management.css";
import "../styles/shipping.css";
import "../styles/form-fields.css";
import "../styles/order-management.css";
import MerchantSidebar from "../components/merchant/MerchantSidebar";
import MerchantBrand from "../components/merchant/MerchantBrand";


/** [tab/KPI key, status filter] — a tab is a shortcut for one shipment status (the status select offers all of them) */
const TABS: readonly (readonly [string, string])[] = [
  ["all", ""],
  ["wait", "PACKED"],
  ["moving", "IN_TRANSIT"],
  ["success", "DELIVERED"],
  ["failed", "DELIVERY_FAILED"],
];
const KPI_ICONS = { all: Package, wait: Clock3, moving: Truck, success: CheckCircle2, failed: XCircle } as const;
const ALL_STATUSES = ["READY_TO_PACK", "PACKING", "PACKED", "DISPATCHED", "IN_TRANSIT", "OUT_FOR_DELIVERY", "DELIVERED", "DELIVERY_FAILED", "RETURNED", "CANCELLED"];
const SIZES = [10, 20, 50];

export default function ShippingPage(){
  const { t } = useTranslation("merchant");
  const { localeTag } = usePublicTranslation();
  const {user,loading}=useAuth();
  const { shopId } = useActiveShop();
  const {canInShop,deniedTitle}=usePermissions();
  const canConfigure=canInShop("SHOP_UPDATE");
  const [notice,setNotice]=useState("");
  const [tone,setTone]=useState<"ok"|"fail">("ok");
  const [status,setStatus]=useState("");
  const [query,setQuery]=useState("");
  const [tracking,setTracking]=useState("");
  const [carrier,setCarrier]=useState("");
  const [dateFrom,setDateFrom]=useState("");
  const [dateTo,setDateTo]=useState("");
  const [page,setPage]=useState(0);
  const [size,setSize]=useState(10);
  const [data,setData]=useState<{items:ShipmentSummary[];totalItems:number;totalPages:number}|null>(null);
  const [fetching,setFetching]=useState(true);
  const [error,setError]=useState<string|null>(null);
  const [reload,setReload]=useState(0);
  const [kpis,setKpis]=useState<(number|null)[]>([null,null,null,null,null]);
  const [carrierData,setCarrierData]=useState<ShopCarriersResponse|null>(null);
  const [carrierFailed,setCarrierFailed]=useState(false);
  const [toggling,setToggling]=useState<string|null>(null);
  const [openOrderId,setOpenOrderId]=useState<string|null>(null);
  const [exporting,setExporting]=useState(false);
  const toastTimer=useRef<number|undefined>(undefined);
  const trackingRef=useRef("");
  const carriersRef=useRef<HTMLElement|null>(null);
  const unsupported=t("orderManagement.notSupported");
  const canExport=canInShop("SHIPMENT_READ");

  const notify=useCallback((text:string,next:"ok"|"fail"="ok")=>{setTone(next);setNotice(text);window.clearTimeout(toastTimer.current);toastTimer.current=window.setTimeout(()=>setNotice(""),2400)},[]);
  useEffect(()=>()=>window.clearTimeout(toastTimer.current),[]);

  const exportShipments=async()=>{
    if(shopId==null||exporting||!canExport)return;
    const total=data?.totalItems??0;
    if(total<=0){
      notify(t("shippingPage.exportEmpty"),"fail");
      return;
    }
    setExporting(true);
    try{
      const lang=localeTag.startsWith("zh")?"zh":localeTag.startsWith("en")?"en":"th";
      await merchantShipmentApi.exportXlsx(shopId,{
        status:status||undefined,
        carrierCode:carrier||undefined,
        trackingNumber:tracking||undefined,
        from:dateFrom||undefined,
        to:dateTo||undefined,
        lang,
      });
      notify(t("shippingPage.exportDone"));
    }catch(caught){
      if(caught instanceof ApiError&&caught.code==="EXPORT_EMPTY")notify(t("shippingPage.exportEmpty"),"fail");
      else if(caught instanceof ApiError&&caught.code==="EXPORT_TOO_LARGE")notify(t("shippingPage.exportTooLarge",{max:"10000"}),"fail");
      else if(!(caught instanceof ApiError)||caught.code==="NETWORK_ERROR"||caught.status===0)notify(t("shippingPage.exportNetwork"),"fail");
      else if(caught.status===403||caught.code==="FORBIDDEN"||caught.code==="PERMISSION_DENIED")notify(deniedTitle(false)||t("shippingPage.exportFailed"),"fail");
      else notify(t("shippingPage.exportFailed"),"fail");
    }finally{
      setExporting(false);
    }
  };

  useEffect(()=>{
    const handle=window.setTimeout(()=>{
      const next=query.trim();
      if(next===trackingRef.current)return;
      trackingRef.current=next;
      setTracking(next);
      setPage(0);
    },300);
    return ()=>window.clearTimeout(handle);
  },[query]);

  useEffect(()=>{
    if(shopId==null)return;
    let alive=true;
    setCarrierFailed(false);
    merchantShipmentApi.carriers(shopId)
      .then(res=>{if(alive)setCarrierData(res)})
      .catch(()=>{if(alive){setCarrierData(null);setCarrierFailed(true)}});
    return ()=>{alive=false};
  },[shopId]);

  useEffect(()=>{
    if(shopId==null)return;
    let alive=true;
    setFetching(true);
    setError(null);
    merchantShipmentApi.list(shopId,{status:status||undefined,carrierCode:carrier||undefined,trackingNumber:tracking||undefined,from:dateFrom||undefined,to:dateTo||undefined,page,size})
      .then(res=>{if(alive)setData(res)})
      .catch(caught=>{if(alive)setError(orderErrorMessage(t,caught))})
      .finally(()=>{if(alive)setFetching(false)});
    return ()=>{alive=false};
  },[shopId,status,carrier,tracking,dateFrom,dateTo,page,size,reload,t]);

  useEffect(()=>{
    if(shopId==null)return;
    let alive=true;
    Promise.all(TABS.map(([,value])=>merchantShipmentApi.list(shopId,{status:value||undefined,size:1}).then(r=>r.totalItems).catch(()=>null)))
      .then(values=>{if(alive)setKpis(values)});
    return ()=>{alive=false};
  },[shopId,reload]);

  const handleChanged=useCallback(()=>setReload(n=>n+1),[]);

  if(loading)return <main className="auth-loading">กำลังตรวจสอบสิทธิ์ร้านค้า...</main>;
  if(!user){window.location.replace("/login?next=/merchant/shipping");return null}
  if((hasPlatformAccess(user) && !hasMerchantAccess(user))){window.location.replace("/admin");return null}
  if(!hasMerchantAccess(user)){window.location.replace("/");return null}

  const carrierList=carrierData?.carriers.filter(c=>c.active)??[];
  const carrierName=(code?:string|null)=>{
    if(!code)return "";
    const hit=carrierData?.carriers.find(c=>c.code===code);
    return hit?((localeTag.startsWith("en")?hit.nameEn:hit.nameTh)||hit.code):code;
  };
  /** Same mark used in table + right rail so logos stay identical. */
  const carrierMark=(code:string)=>{
    const key=code.toUpperCase();
    if(key==="FLASH"||key.includes("FLASH"))return {tone:"flash",label:"FLASH"};
    if(key==="KERRY"||key==="KEX"||key.includes("KERRY"))return {tone:"kerry",label:"KERRY"};
    if(key==="THAIPOST"||key.includes("THAI"))return {tone:"thaipost",label:"POST"};
    if(key==="JT"||key.includes("J&T")||key.includes("JNT"))return {tone:"jt",label:"J&T"};
    if(key==="NINJAVAN"||key.includes("NINJA"))return {tone:"ninja",label:"NINJA"};
    if(key==="BEST"||key.includes("BEST"))return {tone:"best",label:"BEST"};
    if(key==="SPX"||key.includes("SHOPEE"))return {tone:"spx",label:"SPX"};
    if(key==="DHL")return {tone:"dhl",label:"DHL"};
    if(key==="SCG")return {tone:"scg",label:"SCG"};
    if(key==="OTHER")return {tone:"other",label:"OTHER"};
    return {tone:"generic",label:key.slice(0,5)};
  };
  const logo=(code?:string|null)=>{
    if(!code)return null;
    const {tone,label}=carrierMark(code);
    return <span className={`carrier-logo ${tone}`} aria-hidden="true">{label}</span>;
  };
  const renderCarrier=(code?:string|null)=>{
    const name=carrierName(code);
    if(!code||!name){
      return <span className="shipping-carrier is-empty">{t("shippingPage.carriers.unassigned")}</span>;
    }
    return (
      <span className="shipping-carrier" title={name}>
        {logo(code)}
        <b className="carrier-name">{name}</b>
      </span>
    );
  };
  const items=data?.items??[];
  const total=data?.totalItems??0;
  const pages=Math.max(1,data?.totalPages??1);
  const allLabel=t("shippingPage.filters.all");
  const carrierOptions=[allLabel,...carrierList.map((c)=>carrierName(c.code))];
  const statusOptions=[allLabel,...ALL_STATUSES.map((s)=>t(`shippingPage.status.${s}`))];
  const carrierValue=carrier?carrierName(carrier):allLabel;
  const statusValue=status?t(`shippingPage.status.${status}`,{defaultValue:status}):allLabel;
  const filtersOn=!!(status||carrier||tracking||dateFrom||dateTo);
  const hasChipFilters=!!(carrier||tracking||dateFrom||dateTo);
  const reset=()=>{setStatus("");setCarrier("");setQuery("");trackingRef.current="";setTracking("");setDateFrom("");setDateTo("");setPage(0)};
  const clearChipFilters=()=>{setCarrier("");setQuery("");trackingRef.current="";setTracking("");setDateFrom("");setDateTo("");setPage(0)};
  const dateChipValue=dateFrom&&dateTo?`${dateFrom} – ${dateTo}`:dateFrom||dateTo||"";
  const clearChipLabel=(label:string)=>t("shippingPage.filters.clearChip",{label});
  const empty=!fetching&&!error&&items.length===0;
  const kpiValue=(i:number)=>kpis[i]==null?"—":kpis[i]!.toLocaleString(localeTag);

  const toggleCarrier=async(code:string,enabled:boolean)=>{
    if(shopId==null)return;
    setToggling(code);
    try{
      setCarrierData(await merchantShipmentApi.setCarrier(shopId,code,enabled));
      notify(t("shippingPage.carriers.saved"));
    }catch(caught){
      notify(orderErrorMessage(t,caught),"fail");
    }finally{
      setToggling(null);
    }
  };

  return <main className="merchant-shell pm-shell shipping-shell">
    <header className="merchant-topbar"><MerchantBrand /><MerchantTopbarSearch /><MerchantTopbarActions /></header>
    <MerchantSidebar active="shipping"/>
    <section className="pm-content shipping-content">
      <header className="pm-heading shipping-heading">
        <div>
          <h1><Truck aria-hidden />{t("shippingPage.title")}</h1>
          <p>{t("shippingPage.subtitle")}</p>
        </div>
        <div className="pm-actions">
          <button
            type="button"
            disabled={!canConfigure}
            title={deniedTitle(canConfigure)}
            className="primary"
            onClick={() => carriersRef.current?.scrollIntoView({ behavior: "smooth", block: "start" })}
          >
            <Plus aria-hidden />{t("shippingPage.connectMore")}
          </button>
        </div>
      </header>

      <section className="shipping-kpis" aria-label={t("shippingPage.title")}>
        {TABS.map(([key, value], i) => {
          const Icon = KPI_ICONS[key as keyof typeof KPI_ICONS];
          const active = status === value;
          return (
            <button
              type="button"
              className={`shipping-kpi ${key}${active ? " is-active" : ""}`}
              key={key}
              aria-pressed={active}
              onClick={() => { setStatus(value); setPage(0); }}
            >
              <span aria-hidden="true"><Icon /></span>
              <div>
                <small>{t(`shippingPage.kpi.${key}`)}</small>
                <b>{kpiValue(i)}</b>
              </div>
            </button>
          );
        })}
      </section>

      <div className="shipping-layout">
      <div className="shipping-main">
      <div className="pm-tabs" role="tablist" aria-label={t("shippingPage.title")}>
        {TABS.map(([key, value], i) => (
          <button
            type="button"
            role="tab"
            aria-selected={status === value}
            className={status === value ? "active" : ""}
            onClick={() => { setStatus(value); setPage(0); }}
            key={key}
          >
            {t(`shippingPage.tabs.${key}`)}
            {kpis[i] != null ? <i>{kpis[i]}</i> : null}
          </button>
        ))}
      </div>
      <article className="shipping-table-card">
        <form
          className="om-filters"
          onSubmit={(event)=>{
            event.preventDefault();
            const next=query.trim();
            trackingRef.current=next;
            setTracking(next);
            setPage(0);
          }}
        >
          <div className="om-filters-main">
            <div className="om-filter-search">
              <span className="form-label">{t("shippingPage.filters.searchLabel")}</span>
              <label className="om-search">
                <Search aria-hidden />
                <input
                  aria-label={t("shippingPage.filters.searchLabel")}
                  value={query}
                  onChange={(e)=>setQuery(e.target.value)}
                  placeholder={t("shippingPage.filters.searchPlaceholder")}
                />
              </label>
            </div>
            <DateRangePicker
              className="om-filter-range"
              label={t("shippingPage.filters.period")}
              from={dateFrom}
              to={dateTo}
              clearLabel={t("orderManagement.filters.clear")}
              applyLabel={t("orderManagement.filters.apply")}
              presetsLabel={{
                d7: t("orderManagement.filters.presets.d7"),
                d30: t("orderManagement.filters.presets.d30"),
                month: t("orderManagement.filters.presets.month"),
              }}
              onChange={({ from, to }) => {
                setDateFrom(from);
                setDateTo(to);
                setPage(0);
              }}
            />
            <SearchableSelect
              label={t("shippingPage.filters.carrier")}
              options={carrierOptions}
              value={carrierValue}
              onChange={(picked)=>{
                if(picked===allLabel){setCarrier("");setPage(0);return}
                const hit=carrierList.find((c)=>carrierName(c.code)===picked);
                setCarrier(hit?.code??"");
                setPage(0);
              }}
            />
            <SearchableSelect
              label={t("shippingPage.filters.status")}
              options={statusOptions}
              value={statusValue}
              onChange={(picked)=>{
                if(picked===allLabel){setStatus("");setPage(0);return}
                const hit=ALL_STATUSES.find((s)=>t(`shippingPage.status.${s}`)===picked)??"";
                setStatus(hit);
                setPage(0);
              }}
            />
            <div className="om-filters-actions">
              <button type="button" disabled={!filtersOn} onClick={reset}>{t("orderManagement.filters.reset")}</button>
              <button type="submit" className="primary">{t("orderManagement.filters.search")}</button>
            </div>
          </div>
        </form>
        <div className="pm-table-tools">
          <div className="pm-table-tools-chips" role="list" aria-label={t("shippingPage.filters.active")}>
            {tracking ? (
              <div className="om-filter-chip" role="listitem">
                <span>{t("shippingPage.filters.searchLabel")}: {tracking}</span>
                <button
                  type="button"
                  aria-label={clearChipLabel(t("shippingPage.filters.searchLabel"))}
                  onClick={()=>{setQuery("");trackingRef.current="";setTracking("");setPage(0)}}
                >
                  <X size={14} aria-hidden="true" />
                </button>
              </div>
            ) : null}
            {dateFrom || dateTo ? (
              <div className="om-filter-chip" role="listitem">
                <span>{t("shippingPage.filters.period")}: {dateChipValue}</span>
                <button
                  type="button"
                  aria-label={clearChipLabel(t("shippingPage.filters.period"))}
                  onClick={()=>{setDateFrom("");setDateTo("");setPage(0)}}
                >
                  <X size={14} aria-hidden="true" />
                </button>
              </div>
            ) : null}
            {carrier ? (
              <div className="om-filter-chip" role="listitem">
                <span>{t("shippingPage.filters.carrier")}: {carrierName(carrier)}</span>
                <button
                  type="button"
                  aria-label={clearChipLabel(t("shippingPage.filters.carrier"))}
                  onClick={()=>{setCarrier("");setPage(0)}}
                >
                  <X size={14} aria-hidden="true" />
                </button>
              </div>
            ) : null}
            {hasChipFilters ? (
              <button type="button" className="om-clear-all" onClick={clearChipFilters}>
                {t("shippingPage.filters.clearAll")}
              </button>
            ) : null}
          </div>
          <button
            type="button"
            className="pm-export-btn"
            disabled={!canExport || exporting || shopId == null || (data?.totalItems ?? 0) <= 0 || fetching}
            title={
              !canExport ? (deniedTitle(false) || t("shippingPage.exportHint"))
                : (data?.totalItems ?? 0) <= 0 ? t("shippingPage.exportEmptyTitle")
                  : t("shippingPage.exportHint")
            }
            onClick={() => void exportShipments()}
          >
            <Download aria-hidden="true" />
            <span>{exporting ? t("shippingPage.exporting") : t("shippingPage.export")}</span>
          </button>
        </div>
        <div className="shipping-table-wrap" aria-busy={fetching}>
          {error ? (
            <LoadError message={error} onRetry={() => setReload((n) => n + 1)} retryLabel={t("shopSettings.retry")} />
          ) : fetching && items.length === 0 ? (
            <SkeletonTable rows={6} cols={8} />
          ) : (
            <table className={fetching ? "is-refetching" : undefined}>
              <colgroup>
                <col className="col-index" />
                <col className="col-tracking" />
                <col className="col-order" />
                <col className="col-order-status" />
                <col className="col-carrier" />
                <col className="col-shipped" />
                <col className="col-status" />
              </colgroup>
              <thead>
                <tr>
                  <th scope="col" className="col-index">#</th>
                  <th scope="col" className="col-tracking">{t("shippingPage.columns.tracking")}</th>
                  <th scope="col" className="col-order">{t("shippingPage.columns.order")}</th>
                  <th scope="col" className="col-order-status">{t("shippingPage.columns.orderStatus")}</th>
                  <th scope="col" className="col-carrier">{t("shippingPage.columns.carrier")}</th>
                  <th scope="col" className="col-shipped">{t("shippingPage.columns.shippedAt")}</th>
                  <th scope="col" className="col-status">{t("shippingPage.columns.status")}</th>
                </tr>
              </thead>
              <tbody>
                {items.map((row, i) => {
                  const trackingNo = row.trackingNumber || "—";
                  return (
                    <tr
                      key={row.id}
                      className={openOrderId === row.orderId ? "selected" : undefined}
                      onClick={() => setOpenOrderId(row.orderId)}
                    >
                      <td className="col-index num">{page * size + i + 1}</td>
                      <td className="col-tracking"><b className="num" title={row.trackingNumber || undefined}>{trackingNo}</b></td>
                      <td className="col-order"><span className="num" title={row.orderNumber}>{row.orderNumber}</span></td>
                      <td className="col-order-status" title={t(`orderManagement.status.${row.orderStatus}`, { defaultValue: row.orderStatus })}>
                        <span>{t(`orderManagement.status.${row.orderStatus}`, { defaultValue: row.orderStatus })}</span>
                      </td>
                      <td className="col-carrier">{renderCarrier(row.carrierCode)}</td>
                      <td className="col-shipped"><span className="num">{formatBangkokDateTime(row.shippedAt, localeTag)}</span></td>
                      <td className="col-status">
                        <span className={`shipping-status ${statusClass(row.status)}`}>
                          {t(`shippingPage.status.${row.status}`, { defaultValue: row.status })}
                        </span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
          {empty && <div className="pm-empty">{t("shippingPage.empty")}</div>}
        </div>
        <MerchantPagination
          page={page}
          pageCount={pages}
          size={size}
          total={total}
          sizeOptions={SIZES}
          onPageChange={setPage}
          onSizeChange={(next)=>{setSize(next);setPage(0)}}
        />
      </article>
      </div>

      <aside className="shipping-side">
        <section ref={carriersRef} id="shipping-carriers" className="shipping-carriers-card">
          <header><h2>{t("shippingPage.carriers.title")}</h2></header>
          {carrierFailed ? <p className="sh-side-note" role="alert">{t("shippingPage.carriers.loadFailed")}</p> : null}
          {carrierData && !carrierData.restricted ? <p className="sh-side-note">{t("shippingPage.carriers.unrestricted")}</p> : null}
          {!carrierData && !carrierFailed ? <SkeletonTable rows={3} cols={1} /> : null}
          <div className="shipping-carrier-list">
            {carrierList.map((c) => (
              <div key={c.code} className={`shipping-carrier-row${c.enabled ? " is-on" : ""}`}>
                {logo(c.code)}
                <b className="carrier-name">{carrierName(c.code)}</b>
                <span className="sh-toggle">
                  <ToggleSwitch
                    checked={c.enabled}
                    disabled={!canConfigure || toggling === c.code}
                    label={carrierName(c.code)}
                    onText={t("shippingPage.carriers.connected")}
                    offText={t("shippingPage.carriers.connect")}
                    onChange={(v) => void toggleCarrier(c.code, v)}
                  />
                </span>
              </div>
            ))}
          </div>
          {!canConfigure ? <p className="sh-side-note">{deniedTitle(false)}</p> : null}
        </section>

        <section
          className="shipping-help"
          role="link"
          tabIndex={0}
          onClick={() => window.location.assign("/help")}
          onKeyDown={(e) => { if (e.key === "Enter") window.location.assign("/help"); }}
        >
          <Headphones aria-hidden />
          <div>
            <b>{t("shippingPage.help.title")}</b>
            <small>{t("shippingPage.help.body")}</small>
          </div>
          <ChevronRight aria-hidden />
        </section>
      </aside>
      </div>
    </section>
    {openOrderId&&shopId!=null&&<ShipmentDrawer shopId={shopId} orderId={openOrderId} carriers={carrierData?.carriers??[]} onClose={()=>setOpenOrderId(null)} onChanged={handleChanged} notify={notify}/>}
    {notice&&<div className={`merchant-toast${tone==="fail"?" om-toast-fail":""}`} role="status">{notice}</div>}
  </main>
}
