import { useCallback, useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  BarChart3, Box, CheckCircle2, ChevronLeft,
  ChevronRight, CircleHelp, CircleUserRound, Clock3, Crown, Headphones, Home,
  MapPin, Package, Plus, Printer, Search, Settings, ShoppingBag,
  Store, TicketPercent, Truck, WalletCards, XCircle,
} from "lucide-react";
import MerchantBell from "../components/merchant/MerchantBell";
import MerchantTopbarSearch from "../components/merchant/MerchantTopbarSearch";
import ShipmentDrawer, { statusClass } from "../components/merchant/ShipmentDrawer";
import LanguageSwitcher from "../components/LanguageSwitcher";
import MerchantShopMenu from "../components/MerchantShopMenu";
import ToggleSwitch from "../components/form/ToggleSwitch";
import { LoadError, SkeletonTable } from "../components/feedback/Skeleton";
import { formatBangkokDateTime, orderErrorMessage, periodRange, type PeriodKey } from "../components/merchant/orderFormat";
import { useActiveShop } from "../contexts/ActiveShopContext";
import MerchantLogoutButton from "../components/merchant/MerchantLogoutButton";
import { useAuth } from "../contexts/AuthContext";
import { hasPlatformAccess, hasMerchantAccess } from "../services/permissions";
import { usePermissions } from "../hooks/usePermissions";
import { usePublicTranslation } from "../hooks/usePublicTranslation";
import { merchantShipmentApi, type ShipmentSummary, type ShopCarriersResponse } from "../services/api";
import "../styles/merchant-dashboard.css";
import "../styles/product-management.css";
import "../styles/shipping.css";

const menu = [
  [Home,"หน้าหลัก","/merchant/dashboard"],[Box,"จัดการสินค้า","/merchant/products"],
  [ShoppingBag,"คำสั่งซื้อ","/merchant/orders"],[CircleUserRound,"ลูกค้า","/merchant/customers"],
  [TicketPercent,"โปรโมชั่น/คูปอง","/merchant/promotions"],[BarChart3,"รายงาน","/merchant/reports"],
  [WalletCards,"การเงิน","/merchant/finance"],[Truck,"การจัดส่ง","/merchant/shipping"],
  [Store,"ร้านของฉัน","/merchant/store"],[Settings,"ตั้งค่า","/merchant/settings"],
] as const;

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

function Brand(){return <a className="merchant-brand" href="/"><span><ShoppingBag/><i>✓</i></span><div><b>ThaiShop<span>Fun</span></b><small>Shop Local · Support Thai · Smile Together</small></div></a>}

function pageNumbers(page: number, pages: number) {
  const first = Math.max(0, Math.min(page - 2, pages - 5));
  const last = Math.min(pages - 1, first + 4);
  const numbers: number[] = [];
  for (let n = first; n <= last; n += 1) numbers.push(n);
  return numbers;
}

export default function ShippingPage(){
  const { t } = useTranslation("merchant");
  const { localeTag } = usePublicTranslation();
  const {user,loading}=useAuth();
  const { shopId } = useActiveShop();
  const {canInShop,canSeeMenuItem,deniedTitle}=usePermissions();
  const canConfigure=canInShop("SHOP_UPDATE");
  const [notice,setNotice]=useState("");
  const [tone,setTone]=useState<"ok"|"fail">("ok");
  const [status,setStatus]=useState("");
  const [query,setQuery]=useState("");
  const [tracking,setTracking]=useState("");
  const [carrier,setCarrier]=useState("");
  const [period,setPeriod]=useState<PeriodKey>("all");
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
  const toastTimer=useRef<number|undefined>(undefined);
  const trackingRef=useRef("");
  const carriersRef=useRef<HTMLElement|null>(null);
  const unsupported=t("orderManagement.notSupported");

  const notify=useCallback((text:string,next:"ok"|"fail"="ok")=>{setTone(next);setNotice(text);window.clearTimeout(toastTimer.current);toastTimer.current=window.setTimeout(()=>setNotice(""),2400)},[]);
  useEffect(()=>()=>window.clearTimeout(toastTimer.current),[]);

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
    const range=periodRange(period);
    merchantShipmentApi.list(shopId,{status:status||undefined,carrierCode:carrier||undefined,trackingNumber:tracking||undefined,from:range.from,to:range.to,page,size})
      .then(res=>{if(alive)setData(res)})
      .catch(caught=>{if(alive)setError(orderErrorMessage(t,caught))})
      .finally(()=>{if(alive)setFetching(false)});
    return ()=>{alive=false};
  },[shopId,status,carrier,tracking,period,page,size,reload,t]);

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
    if(!code)return "—";
    const hit=carrierData?.carriers.find(c=>c.code===code);
    return hit?((localeTag.startsWith("en")?hit.nameEn:hit.nameTh)||hit.code):code;
  };
  const logo=(code?:string|null)=><span className={`carrier-logo ${(code??"").toLowerCase()}`}>{(code??"?").slice(0,5)}</span>;
  const items=data?.items??[];
  const total=data?.totalItems??0;
  const pages=Math.max(1,data?.totalPages??1);
  const from=total===0?0:page*size+1;
  const to=Math.min(total,page*size+items.length);
  const filtersOn=!!(status||carrier||tracking||period!=="all");
  const reset=()=>{setStatus("");setCarrier("");setQuery("");trackingRef.current="";setTracking("");setPeriod("all");setPage(0)};
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
    <header className="merchant-topbar"><Brand/><MerchantTopbarSearch /><LanguageSwitcher /> <MerchantBell /><MerchantShopMenu /></header>
    <aside className="merchant-sidebar pm-sidebar"><nav>{menu.filter(canSeeMenuItem).map(([Icon,label,href])=><a className={label==="การจัดส่ง"?"active":""} href={href} key={label}><Icon/>{label}</a>)}</nav><section className="upgrade-card"><Crown/><h3>ยกระดับร้านค้าของคุณ</h3><p>ใช้ฟีเจอร์ครบชุด เพิ่มยอดขาย<br/>ได้มากกว่าเดิม</p><button onClick={()=>notify("กำลังเปิดรายละเอียดแพ็กเกจ")}>ดูแพ็กเกจ</button></section><div className="merchant-support"><a href="/help"><CircleHelp/>ศูนย์ช่วยเหลือ</a><button onClick={()=>notify("กำลังติดต่อทีมงาน")}><Headphones/>ติดต่อทีมงาน</button><MerchantLogoutButton /></div></aside>
    <section className="pm-content shipping-content">
      <header className="pm-heading shipping-heading"><div><h1><Truck/>{t("shippingPage.title")}</h1><p>{t("shippingPage.subtitle")}</p></div><div className="pm-actions"><button disabled title={unsupported}><Printer/>{t("shippingPage.printLabels")}</button><button disabled={!canConfigure} title={deniedTitle(canConfigure)} className="primary" onClick={()=>carriersRef.current?.scrollIntoView({behavior:"smooth",block:"start"})}><Plus/>{t("shippingPage.connectMore")}</button></div></header>
      <section className="shipping-kpis">{TABS.map(([key],i)=>{const Icon=KPI_ICONS[key as keyof typeof KPI_ICONS];return <article className={key} key={key}><span><Icon/></span><div><small>{t(`shippingPage.kpi.${key}`)}</small><b>{kpiValue(i)}</b><em>{t(`shippingPage.kpi.${key}Hint`)}</em></div></article>})}</section>
      <div className="shipping-layout"><article className="shipping-table-card">
        <nav className="shipping-tabs" role="tablist">{TABS.map(([key,value],i)=><button role="tab" aria-selected={status===value} className={status===value?"active":""} onClick={()=>{setStatus(value);setPage(0)}} key={key}>{t(`shippingPage.tabs.${key}`)}{kpis[i]!=null?` (${kpis[i]})`:""}</button>)}</nav>
        <div className="shipping-filters"><label className="shipping-search"><Search/><input aria-label={t("shippingPage.filters.searchLabel")} value={query} onChange={e=>setQuery(e.target.value)} placeholder={t("shippingPage.filters.searchPlaceholder")}/></label><label>{t("shippingPage.filters.carrier")}<select value={carrier} onChange={e=>{setCarrier(e.target.value);setPage(0)}}><option value="">{t("shippingPage.filters.all")}</option>{carrierList.map(c=><option key={c.code} value={c.code}>{carrierName(c.code)}</option>)}</select></label><label>{t("shippingPage.filters.status")}<select value={status} onChange={e=>{setStatus(e.target.value);setPage(0)}}><option value="">{t("shippingPage.filters.all")}</option>{ALL_STATUSES.map(s=><option key={s} value={s}>{t(`shippingPage.status.${s}`)}</option>)}</select></label><label>{t("shippingPage.filters.period")}<select value={period} onChange={e=>{setPeriod(e.target.value as PeriodKey);setPage(0)}}><option value="all">{t("orderManagement.filters.periods.all")}</option><option value="7d">{t("orderManagement.filters.periods.7d")}</option><option value="30d">{t("orderManagement.filters.periods.30d")}</option><option value="month">{t("orderManagement.filters.periods.month")}</option></select></label><div><button type="button" disabled={!filtersOn} onClick={reset}>{t("orderManagement.filters.reset")}</button></div></div>
        <div className="shipping-table-wrap" aria-busy={fetching}>{error?<LoadError message={error} onRetry={()=>setReload(n=>n+1)} retryLabel={t("shopSettings.retry")}/>:fetching&&items.length===0?<SkeletonTable rows={6} cols={8}/>:<table className={fetching?"is-refetching":undefined}><thead><tr><th>#</th><th><input aria-label={t("shippingPage.columns.selectAll")} type="checkbox" disabled title={unsupported}/></th><th>{t("shippingPage.columns.tracking")}</th><th>{t("shippingPage.columns.order")}</th><th>{t("shippingPage.columns.orderStatus")}</th><th>{t("shippingPage.columns.carrier")}</th><th>{t("shippingPage.columns.shippedAt")}</th><th>{t("shippingPage.columns.status")}</th><th>{t("shippingPage.columns.actions")}</th></tr></thead><tbody>{items.map((row,i)=>{const live=["DISPATCHED","IN_TRANSIT","OUT_FOR_DELIVERY"].includes(row.status);return <tr key={row.id}><td>{page*size+i+1}</td><td><input aria-label={row.trackingNumber||row.orderNumber} type="checkbox" disabled title={unsupported}/></td><td><b>{row.trackingNumber||"—"}</b></td><td>{row.orderNumber}</td><td>{t(`orderManagement.status.${row.orderStatus}`,{defaultValue:row.orderStatus})}</td><td>{logo(row.carrierCode)}{carrierName(row.carrierCode)}</td><td>{formatBangkokDateTime(row.shippedAt,localeTag)}</td><td><span className={`shipping-status ${statusClass(row.status)}`}>{t(`shippingPage.status.${row.status}`,{defaultValue:row.status})}</span></td><td><div className="shipping-actions"><button onClick={()=>setOpenOrderId(row.orderId)}>{live?t("shippingPage.rowTrack"):t("shippingPage.rowDetails")}</button><button aria-label={t("shippingPage.printOne")} disabled title={unsupported}><Printer/></button></div></td></tr>})}</tbody></table>}{empty&&<div className="pm-empty">{t("shippingPage.empty")}</div>}</div>
        <footer className="pm-pagination"><span>{t("orderManagement.pagination.range",{from,to,total})}</span><label>{t("orderManagement.pagination.show")} <select aria-label={t("orderManagement.pagination.perPage")} value={size} onChange={e=>{setSize(Number(e.target.value));setPage(0)}}>{SIZES.map(n=><option key={n} value={n}>{n}</option>)}</select> {t("orderManagement.pagination.rows")}</label><nav><button aria-label={t("orderManagement.pagination.prev")} disabled={page===0} onClick={()=>setPage(page-1)}><ChevronLeft/></button>{pageNumbers(page,pages).map(n=><button key={n} className={n===page?"active":""} onClick={()=>setPage(n)}>{n+1}</button>)}{pageNumbers(page,pages).slice(-1)[0]<pages-1&&<span>...</span>}<button aria-label={t("orderManagement.pagination.next")} disabled={page>=pages-1} onClick={()=>setPage(page+1)}><ChevronRight/></button></nav></footer>
      </article><aside className="shipping-side">
        <section ref={carriersRef} id="shipping-carriers"><header><h2>{t("shippingPage.carriers.title")}</h2></header>
          {carrierFailed&&<p className="sh-side-note" role="alert">{t("shippingPage.carriers.loadFailed")}</p>}
          {carrierData&&!carrierData.restricted&&<p className="sh-side-note">{t("shippingPage.carriers.unrestricted")}</p>}
          {!carrierData&&!carrierFailed&&<SkeletonTable rows={3} cols={1}/>}
          {carrierList.map(c=><p key={c.code}>{logo(c.code)}<b>{carrierName(c.code)}</b><span className="sh-toggle"><ToggleSwitch checked={c.enabled} disabled={!canConfigure||toggling===c.code} label={carrierName(c.code)} onText={t("shippingPage.carriers.connected")} offText={t("shippingPage.carriers.connect")} onChange={v=>void toggleCarrier(c.code,v)}/></span></p>)}
          {!canConfigure&&<p className="sh-side-note">{deniedTitle(false)}</p>}
        </section>
        <section className="shipping-tools"><h2>{t("shippingPage.tools.title")}</h2><button disabled title={unsupported}><Printer/>{t("shippingPage.tools.printBulk")}<ChevronRight/></button><a href="/merchant/orders"><Package/>{t("shippingPage.tools.createShipment")}<ChevronRight/></a><button disabled title={unsupported}><Box/>{t("shippingPage.tools.parcelSize")}<ChevronRight/></button><button disabled title={unsupported}><MapPin/>{t("shippingPage.tools.senderAddress")}<ChevronRight/></button><button disabled title={unsupported}><Clock3/>{t("shippingPage.tools.history")}<ChevronRight/></button></section>
        <section className="shipping-help" role="link" tabIndex={0} onClick={()=>window.location.assign("/help")} onKeyDown={e=>{if(e.key==="Enter")window.location.assign("/help")}}><Headphones/><div><b>{t("shippingPage.help.title")}</b><small>{t("shippingPage.help.body")}</small></div><ChevronRight/></section>
      </aside></div>
    </section>
    {openOrderId&&shopId!=null&&<ShipmentDrawer shopId={shopId} orderId={openOrderId} carriers={carrierData?.carriers??[]} onClose={()=>setOpenOrderId(null)} onChanged={handleChanged} notify={notify}/>}
    {notice&&<div className={`merchant-toast${tone==="fail"?" om-toast-fail":""}`} role="status">{notice}</div>}
  </main>
}
