import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import {
  CalendarDays,
  CheckCircle2,
  ExternalLink,
  FileText,
  Layers,
  Facebook,
  Instagram,
  Mail,
  MapPin,
  MessageCircle,
  Music2,
  Phone,
  RefreshCw,
  Shield,
  Store,
  Truck,
  type LucideIcon,
} from "lucide-react";
import MerchantTopbarSearch from "../components/merchant/MerchantTopbarSearch";
import MerchantTopbarActions from "../components/merchant/MerchantTopbarActions";
import { useAuth } from "../contexts/AuthContext";
import { hasPlatformAccess, hasMerchantAccess } from "../services/permissions";
import { useActiveShop } from "../contexts/ActiveShopContext";
import { usePermissions } from "../hooks/usePermissions";
import { SkeletonForm } from "../components/feedback/Skeleton";
import ConfirmDialog from "../components/form/ConfirmDialog";
import FormField from "../components/form/FormField";
import SearchableSelect from "../components/SearchableSelect";
import ShopThemeEditor, { type ShopThemeShopInfo } from "../components/merchant/ShopThemeEditor";
import ShopHoursEditor from "../components/merchant/ShopHoursEditor";
import ShopCollectionsManager from "../components/merchant/ShopCollectionsManager";
import ShopMapPinEditor from "../components/maps/ShopMapPinEditor";
import MerchantSaveButton from "../components/merchant/MerchantSaveButton";
import { ApiError, catalogApi, mediaUrl, merchantApi, type MerchantSettings, type MerchantSettingsPayload, type ShopHour } from "../services/api";
import { MERCHANT_CATEGORY_VALUES } from "../i18n/merchantOnboardingCopy";
import { isShopImage, SHOP_IMAGE_ACCEPT } from "../services/shopImage";
import "../styles/merchant-dashboard.css";
import "../styles/product-management.css";
import "../styles/settings.css";
import "../styles/form-fields.css";
import "../styles/my-store.css";
import type { MouseEvent as ReactMouseEvent } from "react";
import MerchantSidebar from "../components/merchant/MerchantSidebar";
import MerchantBrand from "../components/merchant/MerchantBrand";

/** /merchant/store = shop profile & appearance. Operational settings (policies, payment, …) live in /merchant/settings. */
type StoreTabKey="info"|"theme"|"hours"|"collections";
const STORE_TABS:{key:StoreTabKey;label:string;icon:LucideIcon}[]=[
  {key:"info",label:"ข้อมูลร้าน",icon:Store},
  {key:"hours",label:"เวลาทำการ",icon:CalendarDays},
  {key:"collections",label:"คอลเลกชัน",icon:Layers},
];
/** Old store tabs that moved to /merchant/settings (?tab=<value> → settings tab). */
const MOVED_TO_SETTINGS:Record<string,string>={settings:"policies",policies:"policies",policy:"policies",channels:"integrations",members:"team",team:"team",plan:"plan"};
const resolveStoreTab=(raw:string|null):StoreTabKey=>raw==="theme"||raw==="hours"||raw==="collections"?raw:"info";
type PendingLeave={kind:"tab";tab:StoreTabKey}|{kind:"href";href:string}|null;
const PUBLIC_PHONE_RE=/^[0-9+ -]{9,20}$/;
const PUBLIC_EMAIL_RE=/^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const STATUS_LABEL: Record<MerchantSettings["status"], string> = {
  APPROVED: "ใช้งาน",
  PENDING_REVIEW: "รออนุมัติ",
  REJECTED: "ปฏิเสธ",
  SUSPENDED: "ถูกระงับ",
};
type StoreForm = MerchantSettingsPayload & {
  coverUrl: string | null
  logoUrl: string | null
  slug: string
  status: MerchantSettings["status"]
  createdAt: string
  openingHours: ShopHour[] | undefined
}

function emptyForm(): StoreForm {
  return {
    shopName: "", shopNameEn: "", description: "", category: "",
    phone: "", email: "", website: "", lineId: "", facebookUrl: "", instagramUrl: "", tiktokUrl: "",
    address: "", province: "", district: "", subdistrict: "", postalCode: "",
    latitude: null, longitude: null,
    publicPhone: "", publicEmail: "",
    coverUrl: null, logoUrl: null, slug: "", status: "PENDING_REVIEW", createdAt: "", openingHours: undefined,
  }
}

/** Fields saved by PUT /api/merchant/settings (images/hours have their own endpoints). */
function editableSnapshot(form: StoreForm): string {
  const { coverUrl: _cover, logoUrl: _logo, slug: _slug, status: _status, createdAt: _created, openingHours: _hours, ...editable } = form;
  return JSON.stringify(editable);
}

function fromSettings(data: MerchantSettings): StoreForm {
  return {
    shopName: data.shopName || "",
    shopNameEn: data.shopNameEn || "",
    description: data.description || "",
    category: data.category || "",
    phone: data.phone || "",
    email: data.email || "",
    website: data.website || "",
    lineId: data.lineId || "",
    facebookUrl: data.facebookUrl || "",
    instagramUrl: data.instagramUrl || "",
    tiktokUrl: data.tiktokUrl || "",
    address: data.address || "",
    province: data.province || "",
    district: data.district || "",
    subdistrict: data.subdistrict || "",
    postalCode: data.postalCode || "",
    latitude: data.latitude ?? null,
    longitude: data.longitude ?? null,
    publicPhone: data.publicPhone || "",
    publicEmail: data.publicEmail || "",
    coverUrl: data.coverUrl || null,
    logoUrl: data.logoUrl || null,
    slug: data.slug || "",
    status: data.status,
    createdAt: data.createdAt || "",
    openingHours: data.openingHours,
  }
}

/** themePage: /merchant/store/theme — full-width ThemeCustomizer (same data, upload controls and unsaved-leave guard). */
export default function MyStorePage({themePage=false}:{themePage?:boolean}={}){
  const {user,loading}=useAuth();
  const {shopId,ready}=useActiveShop();
  const {canInShop,deniedTitle}=usePermissions();
  const {t}=useTranslation("merchant");
  const canUpdate=canInShop("SHOP_UPDATE");
  const navigate=useNavigate();
  const [searchParams,setSearchParams]=useSearchParams();
  const rawTab=searchParams.get("tab");
  const activeTab:StoreTabKey=themePage?"theme":resolveStoreTab(rawTab);
  const [themeDirty,setThemeDirty]=useState(false);
  const [hoursDirty,setHoursDirty]=useState(false);
  const [pendingLeave,setPendingLeave]=useState<PendingLeave>(null);
  const [notice,setNotice]=useState("");
  const [formLoading,setFormLoading]=useState(true);
  const [saving,setSaving]=useState(false);
  const [logoBusy,setLogoBusy]=useState(false);
  const [editingAddress,setEditingAddress]=useState(false);
  const [form,setForm]=useState<StoreForm>(emptyForm);
  const [categoryNames,setCategoryNames]=useState<string[]>([...MERCHANT_CATEGORY_VALUES]);
  const baseline=useRef<StoreForm>(emptyForm());
  const logoInput=useRef<HTMLInputElement>(null);
  const notify=(text:string)=>{setNotice(text);window.setTimeout(()=>setNotice(""),1900)};
  const patch=(next:Partial<StoreForm>)=>setForm(current=>({...current,...next}));
  const onThemeDirty=useCallback((dirty:boolean)=>setThemeDirty(dirty),[]);
  const onHoursDirty=useCallback((dirty:boolean)=>setHoursDirty(dirty),[]);
  const infoDirty=!formLoading&&editableSnapshot(form)!==editableSnapshot(baseline.current);
  /** Tab-local drafts (theme/hours) are lost on tab switch; the info form lives in this page so it survives tab switches. */
  const tabDirty=(activeTab==="theme"&&themeDirty)||(activeTab==="hours"&&hoursDirty);
  const hasUnsavedTheme=tabDirty||infoDirty;
  const goTab=(tab:StoreTabKey)=>setSearchParams({tab});
  const guardSidebar=(event:ReactMouseEvent<HTMLAnchorElement>,href:string)=>{if(hasUnsavedTheme){event.preventDefault();setPendingLeave({kind:"href",href})}};
  const requestHref=(href:string)=>{
    if(hasUnsavedTheme){setPendingLeave({kind:"href",href});return}
    window.location.assign(href);
  };
  const requestTab=(tab:StoreTabKey)=>{
    if(tab==="theme"){requestHref("/merchant/store/theme");return}
    if(tab===activeTab)return;
    if(tabDirty){setPendingLeave({kind:"tab",tab});return}
    goTab(tab);
  };
  const confirmLeave=()=>{
    const target=pendingLeave;
    setPendingLeave(null);
    if(!target)return;
    setThemeDirty(false);
    setHoursDirty(false);
    if(target.kind==="tab")goTab(target.tab);
    else window.location.assign(target.href);
  };

  // Old/moved tabs (?tab=settings|policies|…) redirect to /merchant/settings; unknown values normalise to a real tab.
  useEffect(()=>{
    if(rawTab&&MOVED_TO_SETTINGS[rawTab]){navigate(`/merchant/settings?tab=${MOVED_TO_SETTINGS[rawTab]}`,{replace:true});return}
    if(themePage)return;
    if(rawTab==="theme"){navigate("/merchant/store/theme",{replace:true});return}
    if(rawTab&&rawTab!==activeTab)setSearchParams({tab:activeTab},{replace:true});
  },[rawTab,activeTab,navigate,setSearchParams,themePage]);

  useEffect(()=>{
    if(!hasUnsavedTheme)return;
    const warn=(event:BeforeUnloadEvent)=>{event.preventDefault();event.returnValue=""};
    window.addEventListener("beforeunload",warn);
    return ()=>window.removeEventListener("beforeunload",warn);
  },[hasUnsavedTheme]);

  useEffect(()=>{
    let active=true;
    catalogApi.categories().then(rows=>{
      if(active && rows.length) setCategoryNames(rows.map(row=>row.nameTh));
    }).catch(()=>{});
    return ()=>{active=false};
  },[]);

  useEffect(()=>{
    if(loading||!user||!ready)return;
    let active=true;
    setFormLoading(true);
    merchantApi.getSettings(shopId).then(data=>{
      if(!active)return;
      const loaded=fromSettings(data);
      baseline.current=loaded;
      setForm(loaded);
      setEditingAddress(false);
    }).catch(()=>notify("โหลดข้อมูลร้านไม่สำเร็จ")).finally(()=>{if(active)setFormLoading(false);});
    return ()=>{active=false};
  },[loading,user,ready,shopId]);

  const save=async()=>{
    if(saving)return;
    const publicPhone=form.publicPhone.trim();
    const publicEmail=form.publicEmail.trim();
    if(publicPhone && !PUBLIC_PHONE_RE.test(publicPhone)){
      notify("เบอร์ติดต่อหน้าร้านต้องเป็นตัวเลข 9–20 ตัว (อนุญาต +, ช่องว่าง, -)");
      return;
    }
    if(publicEmail && (publicEmail.length > 255 || !PUBLIC_EMAIL_RE.test(publicEmail))){
      notify("อีเมลติดต่อหน้าร้านไม่ถูกต้อง");
      return;
    }
    setSaving(true);
    try{
      const payload: MerchantSettingsPayload={
        shopName:form.shopName.trim(), shopNameEn:form.shopNameEn, description:form.description, category:form.category,
        phone:form.phone.trim(), email:form.email.trim(),
        website:form.website, lineId:form.lineId, facebookUrl:form.facebookUrl, instagramUrl:form.instagramUrl, tiktokUrl:form.tiktokUrl,
        address:form.address.trim(), province:form.province.trim(), district:form.district.trim(), subdistrict:form.subdistrict.trim(), postalCode:form.postalCode.trim(),
        latitude: form.latitude ?? null,
        longitude: form.longitude ?? null,
        publicPhone, publicEmail,
      };
      const saved=fromSettings(await merchantApi.saveSettings(payload, shopId));
      saved.coverUrl=form.coverUrl;
      saved.logoUrl=form.logoUrl;
      baseline.current=saved;
      setForm(saved);
      setEditingAddress(false);
      notify("บันทึกข้อมูลร้านค้าเรียบร้อยแล้ว");
    }catch(error){
      notify(error instanceof ApiError?error.message:"บันทึกไม่สำเร็จ กรุณาลองใหม่");
    }finally{setSaving(false);}
  };
  const cancel=()=>{
    setForm(baseline.current);
    setEditingAddress(false);
    notify("ยกเลิกการแก้ไขแล้ว");
  };
  const rememberImage=(kind:"coverUrl"|"logoUrl", url:string|null|undefined)=>{
    const next=url?`${url}${url.includes("?")?"&":"?"}t=${Date.now()}`:null;
    setForm(current=>({...current,[kind]:next}));
    baseline.current={...baseline.current,[kind]:next};
  };
  const uploadLogo=async(file:File)=>{
    if(!isShopImage(file)){notify("โลโก้ต้องเป็น JPG, PNG หรือ WEBP");return;}
    setLogoBusy(true);
    try{
      const saved=await merchantApi.uploadLogo(file, shopId);
      rememberImage("logoUrl", saved.logoUrl);
      notify("อัปโหลดโลโก้แล้ว");
    }catch(error){
      notify(error instanceof ApiError?error.message:"อัปโหลดโลโก้ไม่สำเร็จ");
    }finally{setLogoBusy(false);}
  };
  const openStorefront=()=>{
    if(!form.slug)return;
    window.open(`/shop/${form.slug}`,"_blank","noopener");
  };

  if(loading||!ready)return <main className="auth-loading">กำลังตรวจสอบสิทธิ์ร้านค้า...</main>;
  if(!user){window.location.replace("/login?next=/merchant/store");return null}
  if((hasPlatformAccess(user) && !hasMerchantAccess(user))){window.location.replace("/admin");return null}
  if(formLoading)return <main className="merchant-shell pm-shell my-store-shell"><section className="pm-content my-store-content"><SkeletonForm fields={10} /></section></main>;
  const statusLabel=STATUS_LABEL[form.status]||form.status;
  const createdLabel=form.createdAt?new Date(form.createdAt).toLocaleDateString("th-TH",{day:"numeric",month:"short",year:"numeric"}):"";
  const addressLine=[form.address,form.subdistrict,form.district,form.province,form.postalCode].filter(Boolean).join(" ");
  const mapQuery=encodeURIComponent(addressLine||form.shopName||"Thailand");
  const mapUrl=`https://www.google.com/maps/search/?api=1&query=${mapQuery}`;
  const categories=form.category && !categoryNames.includes(form.category)?[form.category,...categoryNames]:categoryNames;
  const previewText=form.description.split("\n").map(line=>line.trim()).find(Boolean)||"";
  const logoControl=<section className="logo-field"><label>โลโก้ร้าน</label><div className="store-logo">{form.logoUrl?<img key={form.logoUrl} src={mediaUrl(form.logoUrl)} alt="โลโก้ร้าน"/>:<span>🏪</span>}</div><button type="button" disabled={logoBusy||!canUpdate} title={deniedTitle(canUpdate)} onClick={()=>logoInput.current?.click()}>{logoBusy?"กำลังอัปโหลด...":"เปลี่ยนโลโก้"}</button><small>JPG, PNG หรือ WEBP ไม่เกิน 5 MB</small><input ref={logoInput} type="file" accept={SHOP_IMAGE_ACCEPT} hidden onChange={event=>{const file=event.target.files?.[0]; if(file) void uploadLogo(file); event.target.value="";}}/></section>;
  const themeShop:ShopThemeShopInfo={shopName:form.shopName,shopNameEn:form.shopNameEn,description:form.description,category:form.category,address:form.address,province:form.province,district:form.district,subdistrict:form.subdistrict,postalCode:form.postalCode,slug:form.slug,status:form.status,logoUrl:form.logoUrl,coverUrl:form.coverUrl,createdAt:form.createdAt||null,openingHours:form.openingHours};
  if(themePage)return <main className="merchant-shell pm-shell my-store-shell tc-page">
    <header className="merchant-topbar"><MerchantBrand /><MerchantTopbarSearch /><MerchantTopbarActions /></header>
    <MerchantSidebar active={themePage?"theme":"store"} onNavigate={guardSidebar}/>
    <section className="pm-content my-store-content tc-content">
    {formLoading?<SkeletonForm fields={8}/>:<ShopThemeEditor shopId={shopId} shop={themeShop} canUpdate={canUpdate} deniedTitle={deniedTitle(canUpdate)} logoControl={logoControl} onDirtyChange={onThemeDirty} notify={notify} onBack={()=>requestHref("/merchant/store")}/>}
    {pendingLeave&&<ConfirmDialog title={t("storeTheme.confirm.leave.title")} message={t("storeTheme.confirm.leave.message")} confirmLabel={t("storeTheme.confirm.leave.confirm")} cancelLabel={t("storeTheme.confirm.leave.stay")} danger onConfirm={confirmLeave} onCancel={()=>setPendingLeave(null)}/>}{notice&&<div className="merchant-toast" role="status">{notice}</div>}
    </section>
  </main>;
  return <main className="merchant-shell pm-shell my-store-shell">
    <header className="merchant-topbar"><MerchantBrand /><MerchantTopbarSearch /><MerchantTopbarActions /></header>
    <MerchantSidebar active={themePage?"theme":"store"} onNavigate={guardSidebar}/>
    <section className="pm-content my-store-content">
      <div className="store-page-head">
        <header className="pm-heading my-store-heading"><div><h1><Store/>ร้านของฉัน</h1><p>จัดการข้อมูลร้านค้า ปรับแต่งหน้าร้าน และตั้งค่าการขาย</p></div><button type="button" onClick={openStorefront} disabled={!form.slug}><ExternalLink/>ดูหน้าร้านจริง</button></header>
        <nav className="store-tabs" aria-label="หมวดร้านของฉัน">{STORE_TABS.map(({key,label,icon:Icon})=><button key={key} type="button" className={activeTab===key?"active":""} aria-current={activeTab===key?"page":undefined} onClick={()=>requestTab(key)}><Icon/>{key==="theme"?t("storeTheme.tab"):key==="collections"?t("collections.tab"):label}</button>)}</nav>
      </div>
      {activeTab==="collections"?<ShopCollectionsManager shopId={shopId} shopSlug={form.slug} canUpdate={canUpdate} deniedTitle={deniedTitle(canUpdate)} notify={notify}/>:activeTab==="theme"?<ShopThemeEditor shopId={shopId} shop={themeShop} canUpdate={canUpdate} deniedTitle={deniedTitle(canUpdate)} logoControl={logoControl} onDirtyChange={onThemeDirty} notify={notify}/>:activeTab==="hours"?<div className="store-hours"><ShopHoursEditor shopId={shopId} initialHours={form.openingHours} canUpdate={canUpdate} deniedTitle={deniedTitle(canUpdate)} notify={notify} onDirtyChange={onHoursDirty} onSaved={hours=>{patch({openingHours:hours});baseline.current={...baseline.current,openingHours:hours};}}/></div>:(
      <div className="store-layout">
        <section className="store-form-card store-info-card">
          <header className="store-info-head">
            <div>
              <h2><Store aria-hidden />ข้อมูลร้านค้า</h2>
              <p>ข้อมูลพื้นฐานของร้าน จะแสดงบนหน้าร้านและใช้ในการติดต่อลูกค้า</p>
            </div>
            <div className="store-enabled">
              <span><CheckCircle2 aria-hidden />{statusLabel}</span>
              {createdLabel ? <small>สร้างเมื่อ {createdLabel}</small> : null}
              {form.status === "SUSPENDED" ? <a href="/merchant/reinstatement">ยื่นคำร้องปลดระงับ</a> : null}
            </div>
            <div className="store-head-actions">
              {infoDirty ? <button type="button" className="store-cancel" onClick={cancel} disabled={saving}>ยกเลิก</button> : null}
              <MerchantSaveButton saving={saving} disabled={!canUpdate} title={deniedTitle(canUpdate)} onClick={() => void save()} />
            </div>
          </header>

          <section className="store-info-block">
            <h3>โปรไฟล์ร้าน</h3>
            <div className="store-info-stack">
              <FormField label={<>ชื่อร้านค้า <i>*</i></>} icon={Store} aside={<span>{form.shopName.length}/100</span>} className="flush">
                <input value={form.shopName} maxLength={100} onChange={(e) => patch({ shopName: e.target.value })} />
              </FormField>
              <FormField label="คำอธิบายร้านค้า" multiline aside={<span>{form.description.length}/500</span>} className="flush">
                <textarea value={form.description} maxLength={500} onChange={(e) => patch({ description: e.target.value })} />
              </FormField>
              <SearchableSelect
                label="ประเภทร้านค้า"
                required
                options={categories}
                value={form.category}
                placeholder="เลือกประเภทร้านค้า"
                disabled={!canUpdate}
                portal
                onChange={(value) => patch({ category: value })}
              />
            </div>
          </section>

          <section className="store-info-block">
            <h3>ข้อมูลติดต่อ</h3>
            <p className="store-info-hint">เบอร์/อีเมลเจ้าของใช้ภายในระบบ ส่วนช่องสาธารณะจะแสดงบนหน้าร้านเมื่อกรอก</p>
            <div className="form-grid store-info-grid">
              <FormField label={<>เบอร์โทรศัพท์เจ้าของ <i>*</i></>} icon={Phone} aside={<span>ไม่แสดงบนหน้าร้าน</span>} className="flush">
                <input value={form.phone} onChange={(e) => patch({ phone: e.target.value })} />
              </FormField>
              <FormField label={<>อีเมลเจ้าของ <i>*</i></>} icon={Mail} aside={<span>ไม่แสดงบนหน้าร้าน</span>} className="flush">
                <input value={form.email} onChange={(e) => patch({ email: e.target.value })} />
              </FormField>
              <FormField label="เบอร์ติดต่อหน้าร้าน" icon={Phone} aside={<span>เว้นว่าง = ไม่แสดง</span>} className="flush">
                <input value={form.publicPhone} onChange={(e) => patch({ publicPhone: e.target.value })} />
              </FormField>
              <FormField label="อีเมลติดต่อหน้าร้าน" icon={Mail} aside={<span>เว้นว่าง = ไม่แสดง</span>} className="flush">
                <input value={form.publicEmail} onChange={(e) => patch({ publicEmail: e.target.value })} />
              </FormField>
            </div>
          </section>

          <div className="store-lower">
            <section className="store-info-block">
              <h3>ช่องทางโซเชียล</h3>
              <p className="store-info-hint">แสดงบนหน้าติดต่อร้าน</p>
              <div className="store-info-stack">
                <FormField label="LINE" icon={MessageCircle} className="flush">
                  <input value={form.lineId} placeholder="LINE ID" onChange={(e) => patch({ lineId: e.target.value })} />
                </FormField>
                <FormField label="Facebook" icon={Facebook} className="flush">
                  <input value={form.facebookUrl} placeholder="ลิงก์ Facebook" onChange={(e) => patch({ facebookUrl: e.target.value })} />
                </FormField>
                <FormField label="Instagram" icon={Instagram} className="flush">
                  <input value={form.instagramUrl} placeholder="ลิงก์ Instagram" onChange={(e) => patch({ instagramUrl: e.target.value })} />
                </FormField>
                <FormField label="TikTok" icon={Music2} className="flush">
                  <input value={form.tiktokUrl} placeholder="ลิงก์ TikTok" onChange={(e) => patch({ tiktokUrl: e.target.value })} />
                </FormField>
              </div>
            </section>

            <aside className="store-info-block store-address-block">
              <header className="store-section-head">
                <div className="store-section-title">
                  <span className="store-section-icon" aria-hidden="true"><MapPin /></span>
                  <div>
                    <h3>ที่อยู่ร้านค้า</h3>
                    <p>ใช้แสดงบนหน้าร้านและแผนที่</p>
                  </div>
                </div>
                <button type="button" disabled={!canUpdate} title={deniedTitle(canUpdate)} onClick={() => setEditingAddress((value) => !value)}>
                  {editingAddress ? "เสร็จสิ้น" : "แก้ไข"}
                </button>
              </header>
              <div className="store-section-body">
                {editingAddress ? (
                  <div className="form-grid store-info-grid store-address-edit">
                    <FormField label="ที่อยู่" multiline className="flush wide">
                      <textarea value={form.address} onChange={(e) => patch({ address: e.target.value })} placeholder="บ้านเลขที่ ถนน" />
                    </FormField>
                    <FormField label="ตำบล / แขวง" className="flush">
                      <input value={form.subdistrict} onChange={(e) => patch({ subdistrict: e.target.value })} />
                    </FormField>
                    <FormField label="อำเภอ / เขต" className="flush">
                      <input value={form.district} onChange={(e) => patch({ district: e.target.value })} />
                    </FormField>
                    <FormField label="จังหวัด" className="flush">
                      <input value={form.province} onChange={(e) => patch({ province: e.target.value })} />
                    </FormField>
                    <FormField label="รหัสไปรษณีย์" className="flush">
                      <input value={form.postalCode} inputMode="numeric" maxLength={5} onChange={(e) => patch({ postalCode: e.target.value })} />
                    </FormField>
                  </div>
                ) : (
                  <div className={`store-address-panel${addressLine ? "" : " is-empty"}`}>
                    <div className="store-address-card">
                      <span className="store-address-icon" aria-hidden="true"><MapPin /></span>
                      <div>
                        <b>{addressLine || "ยังไม่ได้ระบุที่อยู่"}</b>
                        <small>{addressLine ? [form.district, form.province].filter(Boolean).join(" · ") || "ที่อยู่ร้านค้า" : "กดแก้ไขเพื่อเพิ่มที่อยู่ร้าน"}</small>
                      </div>
                    </div>
                  </div>
                )}
                <ShopMapPinEditor
                  address={addressLine}
                  latitude={form.latitude}
                  longitude={form.longitude}
                  editable={canUpdate}
                  onChange={({ latitude, longitude }) => patch({ latitude, longitude })}
                  hint={canUpdate ? "ลากหมุดหรือคลิกบนแผนที่เพื่อกำหนดตำแหน่งร้าน แล้วกดบันทึก" : undefined}
                />
                <a className="store-maps-link is-inline" href={mapUrl} target="_blank" rel="noreferrer">
                  <ExternalLink aria-hidden />เปิดใน Google Maps
                </a>
              </div>
            </aside>
          </div>

          <section className="store-info-block store-policy-block">
            <header className="store-section-head">
              <div className="store-section-title">
                <span className="store-section-icon" aria-hidden="true"><FileText /></span>
                <div>
                  <h3>นโยบายร้านค้า</h3>
                  <p>ลูกค้าจะเห็นนโยบายเหล่านี้บนหน้าร้าน</p>
                </div>
              </div>
              <button type="button" onClick={() => requestHref("/merchant/settings?tab=policies")}>จัดการนโยบาย →</button>
            </header>
            <div className="store-section-body">
              <div className="store-policy-grid">
                {([
                  [Truck, "การจัดส่ง"],
                  [RefreshCw, "การคืนสินค้า"],
                  [Shield, "ความเป็นส่วนตัว"],
                  [FileText, "ข้อกำหนด"],
                ] as const).map(([Icon, label]) => (
                  <button key={label} type="button" className="store-policy-item" onClick={() => requestHref("/merchant/settings?tab=policies")}>
                    <span aria-hidden="true"><Icon /></span>
                    <b>{label}</b>
                    <small>ไปตั้งค่า</small>
                  </button>
                ))}
              </div>
            </div>
          </section>
        </section>

        <aside className="store-side">
          <section className="store-summary">
            <header className="store-summary-head">
              <h2>สรุปร้าน</h2>
              <span className="store-summary-badge">{statusLabel}</span>
            </header>
            <div className="store-summary-hero">
              <div className="store-summary-mark" aria-hidden="true">{(form.shopName.trim().charAt(0) || "ร").toUpperCase()}</div>
              <div className="store-summary-copy">
                <b>{form.shopName || "ชื่อร้านค้า"}</b>
                <small>{form.category || "ยังไม่เลือกประเภท"}</small>
              </div>
            </div>
            {previewText ? <p className="store-summary-desc">{previewText}</p> : <p className="store-summary-desc is-empty">ยังไม่มีคำอธิบายร้าน</p>}
            <ul className="store-summary-meta">
              <li><span>ประเภท</span><b>{form.category || "—"}</b></li>
              <li><span>จังหวัด</span><b>{form.province || "—"}</b></li>
              <li><span>สถานะ</span><b>{statusLabel}</b></li>
              <li><span>สร้างเมื่อ</span><b>{createdLabel || "—"}</b></li>
            </ul>
            <button className="store-summary-cta" type="button" onClick={openStorefront} disabled={!form.slug}>
              <ExternalLink aria-hidden />เข้าชมหน้าร้าน
            </button>
          </section>

          <section className="store-tips">
            <header><h2>เคล็ดลับเพิ่มยอดขาย</h2></header>
            <p><CheckCircle2 aria-hidden />กรอกคำอธิบายร้านให้ครบและชัดเจน</p>
            <p><CheckCircle2 aria-hidden />ใส่ช่องทางติดต่อสาธารณะที่ลูกค้าใช้จริง</p>
            <p><CheckCircle2 aria-hidden />เชื่อมต่อ LINE / Facebook เพื่อรับข้อความจากลูกค้า</p>
          </section>
        </aside>
      </div>
      )}
    </section>{pendingLeave&&<ConfirmDialog title={t("storeTheme.confirm.leave.title")} message={t("storeTheme.confirm.leave.message")} confirmLabel={t("storeTheme.confirm.leave.confirm")} cancelLabel={t("storeTheme.confirm.leave.stay")} danger onConfirm={confirmLeave} onCancel={()=>setPendingLeave(null)}/>}{notice&&<div className="merchant-toast" role="status">{notice}</div>}
  </main>
}
