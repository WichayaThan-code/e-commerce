import { useCallback, useEffect, useState, type MouseEvent } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import {
  Bell,
  Clock3,
  CreditCard,
  FileText,
  Grid2X2,
  Link2,
  Settings,
  ShieldCheck,
  ShoppingBag,
  Sparkles,
  Trash2,
  Truck,
  UsersRound,
  type LucideIcon,
} from "lucide-react";
import MerchantTopbarSearch from "../components/merchant/MerchantTopbarSearch";
import MerchantTopbarActions from "../components/merchant/MerchantTopbarActions";
import ShopPoliciesEditor from "../components/merchant/ShopPoliciesEditor";
import ShippingMethodsSettings from "../components/merchant/ShippingMethodsSettings";
import PaymentSettings from "../components/merchant/PaymentSettings";
import ConfirmDialog from "../components/form/ConfirmDialog";
import { useAuth } from "../contexts/AuthContext";
import { hasPlatformAccess, hasMerchantAccess } from "../services/permissions";
import { useActiveShop } from "../contexts/ActiveShopContext";
import { usePermissions } from "../hooks/usePermissions";
import "../styles/merchant-dashboard.css";
import "../styles/product-management.css";
import "../styles/settings.css";
import MerchantSidebar from "../components/merchant/MerchantSidebar";
import MerchantBrand from "../components/merchant/MerchantBrand";


/** /merchant/settings = operational settings. Shop profile/appearance/hours live in /merchant/store. */
type SettingsTabKey = "policies" | "general" | "payment" | "shipping" | "integrations" | "team" | "notifications" | "security" | "plan";
type SettingsTab = { key: SettingsTabKey; label: string; icon: LucideIcon; group: "shop" | "sales" | "account"; link?: { href: string; label: string } };
const SETTINGS_TABS: SettingsTab[] = [
  { key: "policies", label: "นโยบายร้าน", icon: FileText, group: "shop" },
  { key: "general", label: "ทั่วไป", icon: Grid2X2, group: "shop" },
  { key: "payment", label: "การชำระเงิน", icon: CreditCard, group: "sales", link: { href: "/merchant/finance", label: "ไปที่หน้าการเงิน" } },
  { key: "shipping", label: "การจัดส่ง", icon: Truck, group: "sales", link: { href: "/merchant/shipping", label: "ไปที่หน้าการจัดส่ง" } },
  { key: "integrations", label: "ช่องทางการขาย/เชื่อมต่อ", icon: Link2, group: "sales" },
  { key: "team", label: "สมาชิกในร้าน", icon: UsersRound, group: "account" },
  { key: "notifications", label: "การแจ้งเตือน", icon: Bell, group: "account" },
  { key: "security", label: "ความปลอดภัย", icon: ShieldCheck, group: "account" },
  { key: "plan", label: "แผนการใช้งาน", icon: Sparkles, group: "account" },
];
const DEFAULT_TAB: SettingsTabKey = "policies";
/** Legacy/alias ?tab= values. Hours moved to /merchant/store. */
const TAB_ALIASES: Record<string, SettingsTabKey> = { policy: "policies", users: "team", members: "team", other: "general", "channels": "integrations" };
const MOVED_TO_STORE: Record<string, string> = { hours: "hours", info: "info", theme: "theme" };

type PendingLeave = { kind: "tab"; tab: SettingsTabKey } | { kind: "href"; href: string } | null;
function resolveTab(raw: string | null): SettingsTabKey {
  if (!raw) return DEFAULT_TAB;
  if (SETTINGS_TABS.some((tab) => tab.key === raw)) return raw as SettingsTabKey;
  return TAB_ALIASES[raw] ?? DEFAULT_TAB;
}

export default function SettingsPage(){
  const {user,loading}=useAuth();
  const {shopId,ready}=useActiveShop();
  const {canInShop,deniedTitle}=usePermissions();
  const {t}=useTranslation("merchant");
  const navigate=useNavigate();
  const [searchParams,setSearchParams]=useSearchParams();
  const rawTab=searchParams.get("tab");
  const activeTab=resolveTab(rawTab);
  const canUpdate=canInShop("SHOP_UPDATE");const canClose=canInShop("SHOP_CLOSE");
  const [notice,setNotice]=useState("");
  const [policiesDirty,setPoliciesDirty]=useState(false);
  const [pendingLeave,setPendingLeave]=useState<PendingLeave>(null);
  const [openStore,setOpenStore]=useState(true);
  const [chat,setChat]=useState(true);
  const notify=useCallback((text:string)=>{setNotice(text);window.setTimeout(()=>setNotice(""),1900)},[]);
  const onPoliciesDirty=useCallback((dirty:boolean)=>setPoliciesDirty(dirty),[]);
  const hasUnsaved=activeTab==="policies"&&policiesDirty;

  // Old links: ?tab=hours (and store tabs) now live in /merchant/store; unknown values normalise to a real tab.
  useEffect(()=>{
    if(rawTab&&MOVED_TO_STORE[rawTab]){navigate(`/merchant/store?tab=${MOVED_TO_STORE[rawTab]}`,{replace:true});return}
    if(rawTab&&rawTab!==activeTab)setSearchParams({tab:activeTab},{replace:true});
  },[rawTab,activeTab,navigate,setSearchParams]);

  useEffect(()=>{
    if(!hasUnsaved)return;
    const warn=(event:BeforeUnloadEvent)=>{event.preventDefault();event.returnValue=""};
    window.addEventListener("beforeunload",warn);
    return ()=>window.removeEventListener("beforeunload",warn);
  },[hasUnsaved]);

  const goTab=(tab:SettingsTabKey)=>setSearchParams({tab});
  const requestTab=(tab:SettingsTabKey)=>{
    if(tab===activeTab)return;
    if(hasUnsaved){setPendingLeave({kind:"tab",tab});return}
    goTab(tab);
  };
  const guardHref=(event:MouseEvent<HTMLAnchorElement>,href:string)=>{
    if(!hasUnsaved)return;
    event.preventDefault();
    setPendingLeave({kind:"href",href});
  };
  const confirmLeave=()=>{
    const target=pendingLeave;
    setPendingLeave(null);
    if(!target)return;
    setPoliciesDirty(false);
    if(target.kind==="tab")goTab(target.tab);
    else window.location.assign(target.href);
  };

  if(loading||!ready)return <main className="auth-loading">กำลังตรวจสอบสิทธิ์ร้านค้า...</main>;
  if(!user){window.location.replace("/login?next=/merchant/settings");return null}
  if((hasPlatformAccess(user) && !hasMerchantAccess(user))){window.location.replace("/admin");return null}
  const current=SETTINGS_TABS.find(tab=>tab.key===activeTab)??SETTINGS_TABS[0];
  const CurrentIcon=current.icon;

  return <main className="merchant-shell pm-shell settings-shell">
    <header className="merchant-topbar"><MerchantBrand /><MerchantTopbarSearch /><MerchantTopbarActions /></header>
    <MerchantSidebar active="settings" onNavigate={guardHref}/>
    <section className="pm-content settings-content">
      <header className="pm-heading settings-heading"><div><h1><Settings/>ตั้งค่า</h1><p>นโยบายร้าน การชำระเงิน การจัดส่ง และบัญชีผู้ใช้ · ข้อมูลร้าน ธีม และเวลาทำการ อยู่ที่ <a href="/merchant/store" onClick={e=>guardHref(e,"/merchant/store")}>ร้านของฉัน</a></p></div></header>
      <nav className="settings-tabs" aria-label="หมวดการตั้งค่า">{SETTINGS_TABS.map((tab,index)=>{const Icon=tab.icon;const sep=index>0&&SETTINGS_TABS[index-1].group!==tab.group;return [sep?<span key={`${tab.key}-sep`} className="settings-tab-sep" aria-hidden="true"/>:null,<button key={tab.key} type="button" className={activeTab===tab.key?"active":""} aria-current={activeTab===tab.key?"page":undefined} onClick={()=>requestTab(tab.key)}><Icon/>{tab.label}</button>]})}</nav>
      {activeTab==="policies"?<ShopPoliciesEditor shopId={shopId} canUpdate={canUpdate} deniedTitle={deniedTitle(canUpdate)} notify={notify} onDirtyChange={onPoliciesDirty}/>
      :activeTab==="general"?<div className="settings-layout"><div className="settings-main">
        <section className="settings-card usage-card"><header><div><h2><FileText/>การใช้งานระบบ</h2><p>ตัวเลือกในส่วนนี้ยังไม่บันทึกลงระบบ (อยู่ระหว่างเชื่อมต่อ)</p></div></header><Toggle label="เปิดร้าน (แสดงร้านต่อสาธารณะ)" value={openStore} onChange={setOpenStore}/><Toggle label="อนุญาตให้ลูกค้าแชทได้" value={chat} onChange={setChat}/><p className="settings-note">การแสดงเบอร์โทร/อีเมลบนหน้าร้าน กำหนดได้ที่ <a href="/merchant/store?tab=info" onClick={e=>guardHref(e,"/merchant/store?tab=info")}>ร้านของฉัน › ข้อมูลร้าน › ช่องทางติดต่อสาธารณะ</a> (เว้นว่าง = ไม่แสดง)</p></section>
        <section className="settings-card display-card"><header><div><h2>▣ รูปแบบการแสดงผล</h2><p>ตั้งค่าภาษาและรูปแบบในระบบ</p></div></header><label>ภาษา<select><option>🇹🇭　ไทย</option><option>🇬🇧　English</option></select></label><div><label>รูปแบบวันที่<select><option>31/12/2566 (พุทธศักราช)</option><option>31/12/2023</option></select></label><label>รูปแบบตัวเลข<select><option>1,234.56</option><option>1.234,56</option></select></label></div><div><label>โซนเวลา<select><option>(GMT+7) กรุงเทพฯ</option><option>(GMT+8) สิงคโปร์</option></select></label><label>สกุลเงิน<select><option>บาท (฿)</option><option>ดอลลาร์สหรัฐ ($)</option></select></label></div></section>
      </div><aside className="settings-side">
        <section className="settings-card danger-card"><header><Trash2/><div><h2>จัดการร้านค้า</h2><p>การดำเนินการนี้ไม่สามารถย้อนกลับได้</p></div></header><div><Clock3/><p><b>พักการใช้งานร้านค้า</b><small>ปิดการแสดงร้านชั่วคราว</small></p><button disabled={!canClose} title={deniedTitle(canClose)} onClick={()=>notify("พักการใช้งานร้านค้าแล้ว")}>พักร้าน</button></div><div><Trash2/><p><b>ลบร้านค้า</b><small>ลบข้อมูลร้านค้าและสินค้าทั้งหมด</small></p><button className="delete" disabled={!canClose} title={deniedTitle(canClose)} onClick={()=>notify("ต้องยืนยันตัวตนก่อนลบร้านค้า")}>ลบร้านค้า</button></div></section>
      </aside></div>
      :activeTab==="shipping"?<div className="settings-layout"><div className="settings-main"><ShippingMethodsSettings shopId={shopId} canUpdate={canUpdate} deniedTitle={deniedTitle(canUpdate)} notify={notify}/>{current.link&&<p className="settings-note"><a href={current.link.href}>{current.link.label} →</a></p>}</div></div>
      :activeTab==="payment"?<div className="settings-layout"><div className="settings-main"><PaymentSettings shopId={shopId} canUpdate={canUpdate} deniedTitle={deniedTitle(canUpdate)} notify={notify}/>{current.link&&<p className="settings-note"><a href={current.link.href}>{current.link.label} →</a></p>}</div></div>
      :<section className="settings-card settings-soon"><header><div><h2><CurrentIcon/>{current.label}</h2><p>ฟีเจอร์นี้จะเปิดให้ใช้เร็วๆ นี้</p></div></header>{current.link&&<a href={current.link.href}>{current.link.label} →</a>}</section>}
    </section>{pendingLeave&&<ConfirmDialog title={t("storeTheme.confirm.leave.title")} message={t("storeTheme.confirm.leave.message")} confirmLabel={t("storeTheme.confirm.leave.confirm")} cancelLabel={t("storeTheme.confirm.leave.stay")} danger onConfirm={confirmLeave} onCancel={()=>setPendingLeave(null)}/>}{notice&&<div className="merchant-toast" role="status">{notice}</div>}
  </main>
}

function Toggle({label,value,onChange}:{label:string;value:boolean;onChange:(value:boolean)=>void}){return <label className="toggle-row"><span>{label}</span><button type="button" role="switch" aria-checked={value} aria-label={label} className={value?"on":""} onClick={()=>onChange(!value)}><i/></button></label>}
