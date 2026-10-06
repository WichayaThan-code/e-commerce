<<<<<<< HEAD
import { useState } from "react";
=======
import { useEffect, useRef, useState } from "react";
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
import {
  BarChart3, Bell, Box, CheckCircle2, ChevronDown, CircleHelp, CircleUserRound,
  Crown, ExternalLink, Facebook, Headphones, Home, Instagram, LineChart, MapPin,
  PackageCheck, Plus, Save, Search, Settings, ShieldCheck, ShoppingBag, Store,
  TicketPercent, Truck, UsersRound, WalletCards,
} from "lucide-react";
<<<<<<< HEAD
import { useAuth } from "../contexts/AuthContext";
=======
import LanguageSwitcher from "../components/LanguageSwitcher";
import MerchantShopMenu from "../components/MerchantShopMenu";
import { useAuth } from "../contexts/AuthContext";
import { useActiveShop } from "../contexts/ActiveShopContext";
import { ApiError, catalogApi, mediaUrl, merchantApi, type MerchantSettings, type MerchantSettingsPayload } from "../services/api";
import { MERCHANT_CATEGORY_VALUES } from "../i18n/merchantOnboardingCopy";
import { isShopImage, SHOP_IMAGE_ACCEPT } from "../services/shopImage";
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
import "../styles/merchant-dashboard.css";
import "../styles/product-management.css";
import "../styles/my-store.css";

const menu = [
  [Home,"หน้าหลัก","/merchant/dashboard"],[Box,"จัดการสินค้า","/merchant/products"],
  [ShoppingBag,"คำสั่งซื้อ","/merchant/orders","12"],[CircleUserRound,"ลูกค้า","/merchant/customers"],
  [TicketPercent,"โปรโมชั่น/คูปอง","/merchant/promotions"],[BarChart3,"รายงาน","/merchant/reports"],
  [WalletCards,"การเงิน","/merchant/finance"],[Truck,"การจัดส่ง","/merchant/shipping"],
  [Store,"ร้านของฉัน","/merchant/store"],[Settings,"ตั้งค่า","/merchant/settings"],
] as const;
const tabs=["ข้อมูลร้านค้า","หน้าร้าน","การตั้งค่า","ช่องทางการขาย","สมาชิกในร้าน","แผนการใช้งาน"];
<<<<<<< HEAD
function Brand(){return <a className="merchant-brand" href="/"><span><ShoppingBag/><i>✓</i></span><div><b>ThaiShop<span>Fun</span></b><small>Shop Local · Support Thai · Smile Together</small></div></a>}

export default function MyStorePage(){
  const {user,loading}=useAuth();
  const [activeTab,setActiveTab]=useState("ข้อมูลร้านค้า");
  const [notice,setNotice]=useState("");
  const [name,setName]=useState("MaeSomchai Official");
  const [description,setDescription]=useState("คัดสรรของดีจากทั่วไทย สินค้าคุณภาพ ส่งตรงถึงบ้านคุณ\nอุดหนุนของไทย สนับสนุนชุมชน");
  const [phone,setPhone]=useState("081-234-5678");
  const [email,setEmail]=useState("maesomchai@gmail.com");
  const notify=(text:string)=>{setNotice(text);window.setTimeout(()=>setNotice(""),1900)};
  if(loading)return <main className="auth-loading">กำลังตรวจสอบสิทธิ์ร้านค้า...</main>;
  if(!user){window.location.replace("/login?next=/merchant/store");return null}
  if(user.role==="ADMIN"){window.location.replace("/admin");return null}
  if(user.role!=="MERCHANT"){window.location.replace("/");return null}
  const shopName=(user.firstName||"MaeSomchai").toLowerCase()==="merchant"?"MaeSomchai Official":`${user.firstName||"MaeSomchai"} Official`;
  const save=()=>notify("บันทึกข้อมูลร้านค้าเรียบร้อยแล้ว");
  return <main className="merchant-shell pm-shell my-store-shell">
    <header className="merchant-topbar"><Brand/><form onSubmit={e=>{e.preventDefault();notify("ค้นหาข้อมูลร้านค้าแล้ว")}}><Search/><input aria-label="ค้นหาทั้งระบบ" placeholder="ค้นหาสินค้า, คำสั่งซื้อ, ลูกค้า..."/></form><button className="merchant-bell" aria-label="การแจ้งเตือน"><Bell/><i>3</i></button><div className="merchant-profile"><span>🏪</span><p><b>{shopName}</b><small>เจ้าของร้าน</small></p><ChevronDown/></div></header>
    <aside className="merchant-sidebar pm-sidebar"><nav>{menu.map(([Icon,label,href,badge])=><a className={label==="ร้านของฉัน"?"active":""} href={href} key={label} onClick={e=>{if((href as string)==="#"){e.preventDefault();notify(`${label}: อยู่ระหว่างเชื่อมข้อมูล`)}}}><Icon/>{label}{badge&&<i>{badge}</i>}</a>)}</nav><section className="upgrade-card"><Crown/><h3>ยกระดับร้านค้าของคุณ</h3><p>ใช้ฟีเจอร์ครบชุด เพิ่มยอดขาย<br/>ได้มากกว่าเดิม</p><button onClick={()=>notify("กำลังเปิดรายละเอียดแพ็กเกจ")}>ดูแพ็กเกจ</button></section><div className="merchant-support"><a href="/help"><CircleHelp/>ศูนย์ช่วยเหลือ</a><button onClick={()=>notify("กำลังติดต่อทีมงาน")}><Headphones/>ติดต่อทีมงาน</button></div></aside>
    <section className="pm-content my-store-content">
      <header className="pm-heading my-store-heading"><div><h1><Store/>ร้านของฉัน</h1><p>จัดการข้อมูลร้านค้า ปรับแต่งหน้าร้าน และตั้งค่าการขาย</p></div><button onClick={()=>notify("เปิดหน้าร้านจริงในแท็บใหม่")}><ExternalLink/>ดูหน้าร้านจริง</button></header>
      <nav className="store-tabs">{tabs.map((tab,i)=><button key={tab} className={activeTab===tab?"active":""} onClick={()=>{setActiveTab(tab);notify(`เปิดแท็บ${tab}`)}}>{i===0?<Store/>:i===1?<ShoppingBag/>:i===2?<Settings/>:i===3?<PackageCheck/>:i===4?<UsersRound/>:<ShieldCheck/>}{tab}</button>)}</nav>
      <div className="store-layout"><section className="store-form-card"><header><div><h2>ข้อมูลร้านค้า</h2><p>ข้อมูลพื้นฐานของร้านค้า จะแสดงบนหน้าร้านและใช้ในการติดต่อ</p></div><div className="store-enabled"><span><CheckCircle2/>เปิดใช้งาน</span><small>สร้างเมื่อ 1 ม.ค. 2026</small></div></header>
        <div className="store-form-grid"><section className="logo-field"><label>โลโก้ร้าน</label><div className="store-logo"><span>👩🏻‍🌾</span><b>แม่สมใจ</b><small>- ของดีไทย -</small></div><button onClick={()=>notify("เลือกไฟล์โลโก้ร้าน")}>เปลี่ยนโลโก้</button><small>ขนาดแนะนำ 500 x 500 px<br/>ไฟล์ JPG, PNG (ไม่เกิน 2 MB)</small></section>
        <div className="store-fields"><label>ชื่อร้านค้า <i>*</i><input value={name} maxLength={100} onChange={e=>setName(e.target.value)}/><small>{name.length}/100</small></label><label>คำอธิบายร้านค้า<textarea value={description} maxLength={500} onChange={e=>setDescription(e.target.value)}/><small>{description.length}/500</small></label></div></div>
        <div className="store-contact"><label>ประเภทร้านค้า <i>*</i><select><option>อาหารและเครื่องดื่ม</option><option>แฟชั่นและเครื่องแต่งกาย</option><option>ของฝากและของขวัญ</option></select></label><label>เบอร์โทรศัพท์ <i>*</i><input value={phone} onChange={e=>setPhone(e.target.value)}/></label><label>อีเมล <i>*</i><input value={email} onChange={e=>setEmail(e.target.value)}/></label></div>
        <div className="store-lower"><section><h3>ช่องทางโซเชียล</h3>{[["line","@maesomchai"],["facebook","https://www.facebook.com/maesomchai"],["instagram","https://www.instagram.com/maesomchai"],["tiktok","https://www.tiktok.com/@maesomchai"]].map(([kind,value])=><label key={kind}><span className={kind}>{kind==="line"?"LINE":kind==="facebook"?<Facebook/>:kind==="instagram"?<Instagram/>:"♪"}</span><input defaultValue={value}/><ExternalLink/></label>)}</section><aside><header><h3>ที่อยู่ร้านค้า</h3><button onClick={()=>notify("เปิดแก้ไขที่อยู่")}>✎ แก้ไขที่อยู่</button></header><div className="address"><MapPin/><span>123 หมู่ 4 ต.บางรัก<br/>อ.เมือง จ.เชียงใหม่ 50000<br/>ประเทศไทย</span></div><div className="mini-map"><MapPin/><button onClick={()=>notify("เปิด Google Maps")}>ดูบน Google Maps</button></div><header><h3>นโยบายร้านค้า</h3><button onClick={()=>notify("เปิดแก้ไขนโยบาย")}>✎ แก้ไข</button></header><ul><li>นโยบายการคืนสินค้า</li><li>นโยบายการจัดส่ง</li><li>เงื่อนไขการสั่งซื้อ</li></ul></aside></div>
        <footer><button className="save" onClick={save}><Save/>บันทึกข้อมูล</button><button onClick={()=>{setName("MaeSomchai Official");setDescription("คัดสรรของดีจากทั่วไทย สินค้าคุณภาพ ส่งตรงถึงบ้านคุณ\nอุดหนุนของไทย สนับสนุนชุมชน");notify("ยกเลิกการแก้ไขแล้ว")}}>ยกเลิก</button></footer>
      </section><aside className="store-side"><section className="store-preview"><header><h2>ตัวอย่างหน้าร้าน</h2><button onClick={()=>notify("เปิดหน้าร้าน")}>ดูหน้าร้าน →</button></header><div className="preview-cover"><strong>ของดีไทย<br/>ส่งถึงบ้านคุณ</strong><small>อุดหนุนของไทย สนับสนุนชุมชน ❤️</small></div><div className="preview-profile"><div className="preview-logo">👩🏻‍🌾</div><p><b>{name||"ชื่อร้านค้า"}</b><small>{description.split("\n")[0]}</small></p></div><div className="preview-stats"><span>⭐<b>4.8</b><small>(1,245 รีวิว)</small></span><span>🛒<b>5.2 พัน</b><small>รายการสินค้า</small></span><span>👤<b>12.3 พัน</b><small>ผู้ติดตาม</small></span></div><button className="enter-store" onClick={()=>notify("กำลังเข้าสู่หน้าร้าน")}>เข้าชมหน้าร้าน →</button></section>
        <section className="store-status"><header><h2>สถานะร้านค้า</h2><span>● เปิดให้บริการ</span></header>{[[ShieldCheck,"ยืนยันตัวตนร้านค้า","ยืนยันแล้ว"],[PackageCheck,"เอกสารร้านค้า","ครบถ้วน"],[CheckCircle2,"จำนวนสินค้า","532 รายการ"],[LineChart,"เข้าร่วมแคมเปญ","3 แคมเปญ"]].map(([Icon,label,value])=><p key={label as string}><Icon/><b>{label as string}</b><span>{value as string}</span></p>)}</section>
=======
const STATUS_LABEL: Record<MerchantSettings["status"], string> = {
  APPROVED: "ใช้งาน",
  PENDING_REVIEW: "รออนุมัติ",
  REJECTED: "ปฏิเสธ",
  SUSPENDED: "ถูกระงับ",
};
function Brand(){return <a className="merchant-brand" href="/"><span><ShoppingBag/><i>✓</i></span><div><b>ThaiShop<span>Fun</span></b><small>Shop Local · Support Thai · Smile Together</small></div></a>}

type StoreForm = MerchantSettingsPayload & {
  coverUrl: string | null
  logoUrl: string | null
  slug: string
  status: MerchantSettings["status"]
  createdAt: string
}

function emptyForm(): StoreForm {
  return {
    shopName: "", shopNameEn: "", description: "", category: "",
    phone: "", email: "", website: "", lineId: "", facebookUrl: "", instagramUrl: "", tiktokUrl: "",
    address: "", province: "", district: "", subdistrict: "", postalCode: "",
    coverUrl: null, logoUrl: null, slug: "", status: "PENDING_REVIEW", createdAt: "",
  }
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
    coverUrl: data.coverUrl || null,
    logoUrl: data.logoUrl || null,
    slug: data.slug || "",
    status: data.status,
    createdAt: data.createdAt || "",
  }
}

export default function MyStorePage(){
  const {user,loading}=useAuth();
  const {shopId,ready}=useActiveShop();
  const [activeTab,setActiveTab]=useState("ข้อมูลร้านค้า");
  const [notice,setNotice]=useState("");
  const [formLoading,setFormLoading]=useState(true);
  const [saving,setSaving]=useState(false);
  const [coverBusy,setCoverBusy]=useState(false);
  const [logoBusy,setLogoBusy]=useState(false);
  const [editingAddress,setEditingAddress]=useState(false);
  const [form,setForm]=useState<StoreForm>(emptyForm);
  const [categoryNames,setCategoryNames]=useState<string[]>([...MERCHANT_CATEGORY_VALUES]);
  const baseline=useRef<StoreForm>(emptyForm());
  const coverInput=useRef<HTMLInputElement>(null);
  const logoInput=useRef<HTMLInputElement>(null);
  const notify=(text:string)=>{setNotice(text);window.setTimeout(()=>setNotice(""),1900)};
  const patch=(next:Partial<StoreForm>)=>setForm(current=>({...current,...next}));

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
    setSaving(true);
    try{
      const payload: MerchantSettingsPayload={
        shopName:form.shopName.trim(), shopNameEn:form.shopNameEn, description:form.description, category:form.category,
        phone:form.phone.trim(), email:form.email.trim(),
        website:form.website, lineId:form.lineId, facebookUrl:form.facebookUrl, instagramUrl:form.instagramUrl, tiktokUrl:form.tiktokUrl,
        address:form.address.trim(), province:form.province.trim(), district:form.district.trim(), subdistrict:form.subdistrict.trim(), postalCode:form.postalCode.trim(),
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
  const uploadCover=async(file:File)=>{
    if(!isShopImage(file)){notify("รูปปกต้องเป็น JPG, PNG หรือ WEBP");return;}
    setCoverBusy(true);
    try{
      const saved=await merchantApi.uploadCover(file, shopId);
      rememberImage("coverUrl", saved.coverUrl);
      notify("อัปโหลดรูปปกแล้ว");
    }catch(error){
      notify(error instanceof ApiError?error.message:"อัปโหลดรูปปกไม่สำเร็จ");
    }finally{setCoverBusy(false);}
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

  if(loading||!ready||formLoading)return <main className="auth-loading">กำลังตรวจสอบสิทธิ์ร้านค้า...</main>;
  if(!user){window.location.replace("/login?next=/merchant/store");return null}
  if(user.role==="ADMIN"){window.location.replace("/admin");return null}
  const statusLabel=STATUS_LABEL[form.status]||form.status;
  const createdLabel=form.createdAt?new Date(form.createdAt).toLocaleDateString("th-TH",{day:"numeric",month:"short",year:"numeric"}):"";
  const addressLine=[form.address,form.subdistrict,form.district,form.province,form.postalCode].filter(Boolean).join(" ");
  const mapUrl=`https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(addressLine||form.shopName)}`;
  const categories=form.category && !categoryNames.includes(form.category)?[form.category,...categoryNames]:categoryNames;
  const previewText=form.description.split("\n").map(line=>line.trim()).find(Boolean)||"";
  return <main className="merchant-shell pm-shell my-store-shell">
    <header className="merchant-topbar"><Brand/><form onSubmit={e=>{e.preventDefault();notify("ค้นหาข้อมูลร้านค้าแล้ว")}}><Search/><input aria-label="ค้นหาทั้งระบบ" placeholder="ค้นหาสินค้า, คำสั่งซื้อ, ลูกค้า..."/></form><LanguageSwitcher /> <button className="merchant-bell" aria-label="การแจ้งเตือน"><Bell/><i>3</i></button><MerchantShopMenu /></header>
    <aside className="merchant-sidebar pm-sidebar"><nav>{menu.map(([Icon,label,href,badge])=><a className={label==="ร้านของฉัน"?"active":""} href={href} key={label} onClick={e=>{if((href as string)==="#"){e.preventDefault();notify(`${label}: อยู่ระหว่างเชื่อมข้อมูล`)}}}><Icon/>{label}{badge&&<i>{badge}</i>}</a>)}</nav><section className="upgrade-card"><Crown/><h3>ยกระดับร้านค้าของคุณ</h3><p>ใช้ฟีเจอร์ครบชุด เพิ่มยอดขาย<br/>ได้มากกว่าเดิม</p><button onClick={()=>notify("กำลังเปิดรายละเอียดแพ็กเกจ")}>ดูแพ็กเกจ</button></section><div className="merchant-support"><a href="/help"><CircleHelp/>ศูนย์ช่วยเหลือ</a><button onClick={()=>notify("กำลังติดต่อทีมงาน")}><Headphones/>ติดต่อทีมงาน</button></div></aside>
    <section className="pm-content my-store-content">
      <div className="store-page-head">
        <header className="pm-heading my-store-heading"><div><h1><Store/>ร้านของฉัน</h1><p>จัดการข้อมูลร้านค้า ปรับแต่งหน้าร้าน และตั้งค่าการขาย</p></div><button type="button" onClick={openStorefront} disabled={!form.slug}><ExternalLink/>ดูหน้าร้านจริง</button></header>
        <nav className="store-tabs">{tabs.map((tab,i)=><button key={tab} className={activeTab===tab?"active":""} onClick={()=>{setActiveTab(tab);if(tab!=="ข้อมูลร้านค้า")notify(`เปิดแท็บ${tab}`)}}>{i===0?<Store/>:i===1?<ShoppingBag/>:i===2?<Settings/>:i===3?<PackageCheck/>:i===4?<UsersRound/>:<ShieldCheck/>}{tab}</button>)}</nav>
      </div>
      <div className="store-layout"><section className="store-form-card"><header><div><h2>ข้อมูลร้านค้า</h2><p>ข้อมูลพื้นฐานของร้านค้า จะแสดงบนหน้าร้านและใช้ในการติดต่อ</p></div><div className="store-enabled"><span><CheckCircle2/>{statusLabel}</span>{createdLabel&&<small>สร้างเมื่อ {createdLabel}</small>}{form.status==="SUSPENDED"&&<a href="/merchant/reinstatement">ยื่นคำร้องปลดระงับ</a>}</div></header>
        <div className="store-cover-banner">{form.coverUrl?<img key={form.coverUrl} src={mediaUrl(form.coverUrl)} alt="รูปปกร้าน"/>:<span>ยังไม่มีรูปปก</span>}<button type="button" disabled={coverBusy} onClick={()=>coverInput.current?.click()}>{coverBusy?"กำลังอัปโหลด...":"เปลี่ยนรูปปก"}</button><input ref={coverInput} type="file" accept={SHOP_IMAGE_ACCEPT} hidden onChange={event=>{const file=event.target.files?.[0]; if(file) void uploadCover(file); event.target.value="";}}/></div>
        <div className="store-form-grid"><section className="logo-field"><label>โลโก้ร้าน</label><div className="store-logo">{form.logoUrl?<img key={form.logoUrl} src={mediaUrl(form.logoUrl)} alt="โลโก้ร้าน"/>:<span>🏪</span>}</div><button type="button" disabled={logoBusy} onClick={()=>logoInput.current?.click()}>{logoBusy?"กำลังอัปโหลด...":"เปลี่ยนโลโก้"}</button><small>JPG, PNG หรือ WEBP ไม่เกิน 5 MB</small><input ref={logoInput} type="file" accept={SHOP_IMAGE_ACCEPT} hidden onChange={event=>{const file=event.target.files?.[0]; if(file) void uploadLogo(file); event.target.value="";}}/></section>
        <div className="store-fields"><label>ชื่อร้านค้า <i>*</i><input value={form.shopName} maxLength={100} onChange={e=>patch({shopName:e.target.value})}/><small>{form.shopName.length}/100</small></label><label>คำอธิบายร้านค้า<textarea value={form.description} maxLength={500} onChange={e=>patch({description:e.target.value})}/><small>{form.description.length}/500</small></label></div></div>
        <div className="store-contact"><label>ประเภทร้านค้า <i>*</i><select value={form.category} onChange={e=>patch({category:e.target.value})}>{categories.map(option=><option key={option}>{option}</option>)}</select></label><label>เบอร์โทรศัพท์ <i>*</i><input value={form.phone} onChange={e=>patch({phone:e.target.value})}/></label><label>อีเมล <i>*</i><input value={form.email} onChange={e=>patch({email:e.target.value})}/></label></div>
        <div className="store-lower"><section><h3>ช่องทางโซเชียล</h3>{([
          ["line","lineId",form.lineId],
          ["facebook","facebookUrl",form.facebookUrl],
          ["instagram","instagramUrl",form.instagramUrl],
          ["tiktok","tiktokUrl",form.tiktokUrl],
        ] as const).map(([kind,key,value])=><label key={kind}><span className={kind}>{kind==="line"?"LINE":kind==="facebook"?<Facebook/>:kind==="instagram"?<Instagram/>:"♪"}</span><input value={value} placeholder={kind==="line"?"LINE ID":`ลิงก์ ${kind}`} onChange={e=>patch({[key]:e.target.value})}/></label>)}</section>
        <aside><header><h3>ที่อยู่ร้านค้า</h3><button type="button" onClick={()=>setEditingAddress(value=>!value)}>{editingAddress?"ซ่อนแบบฟอร์ม":"✎ แก้ไขที่อยู่"}</button></header>
          {editingAddress?<div className="store-address-edit"><textarea value={form.address} onChange={e=>patch({address:e.target.value})} placeholder="บ้านเลขที่ ถนน"/><input value={form.subdistrict} onChange={e=>patch({subdistrict:e.target.value})} placeholder="ตำบล / แขวง"/><input value={form.district} onChange={e=>patch({district:e.target.value})} placeholder="อำเภอ / เขต"/><input value={form.province} onChange={e=>patch({province:e.target.value})} placeholder="จังหวัด"/><input value={form.postalCode} inputMode="numeric" maxLength={5} onChange={e=>patch({postalCode:e.target.value})} placeholder="รหัสไปรษณีย์"/></div>:<div className="address"><MapPin/><span>{addressLine||"ยังไม่มีที่อยู่"}</span></div>}
          <div className="mini-map"><MapPin/><a href={mapUrl} target="_blank" rel="noreferrer">ดูบน Google Maps</a></div>
          <header><h3>นโยบายร้านค้า</h3><button type="button" onClick={()=>notify("เปิดแก้ไขนโยบาย")}>✎ แก้ไข</button></header><ul><li>นโยบายการคืนสินค้า</li><li>นโยบายการจัดส่ง</li><li>เงื่อนไขการสั่งซื้อ</li></ul></aside></div>
        <footer><button type="button" onClick={cancel} disabled={saving}>ยกเลิก</button><button className="save" type="button" onClick={()=>void save()} disabled={saving}><Save/>{saving?"กำลังบันทึก...":"บันทึกข้อมูล"}</button></footer>
      </section><aside className="store-side"><section className="store-preview"><header><h2>ตัวอย่างหน้าร้าน</h2><button type="button" onClick={openStorefront} disabled={!form.slug}>ดูหน้าร้าน</button></header><div className="preview-cover" style={form.coverUrl?{backgroundImage:`linear-gradient(180deg,rgba(8,24,48,.08),rgba(8,24,48,.45)),url(${mediaUrl(form.coverUrl)})`}:undefined}>{!form.coverUrl&&<span>ยังไม่มีรูปปก</span>}</div><div className="preview-profile"><div className="preview-logo">{form.logoUrl?<img src={mediaUrl(form.logoUrl)} alt=""/>:<span>🏪</span>}</div><p><b>{form.shopName||"ชื่อร้านค้า"}</b><small>{form.category||"ยังไม่เลือกหมวด"}</small></p></div>{previewText&&<p className="preview-desc">{previewText}</p>}<div className="preview-stats"><span><small>ประเภท</small><b>{form.category||"—"}</b></span><span><small>จังหวัด</small><b>{form.province||"—"}</b></span><span><small>สถานะ</small><b>{statusLabel}</b></span></div><button className="enter-store" type="button" onClick={openStorefront} disabled={!form.slug}>เข้าชมหน้าร้าน</button></section>
        <section className="store-status"><header><h2>สถานะร้านค้า</h2><span>● {statusLabel}</span></header><p><ShieldCheck/><b>สถานะร้าน</b><span>{statusLabel}</span></p><p><PackageCheck/><b>ประเภทร้าน</b><span>{form.category||"—"}</span></p><p><CheckCircle2/><b>รูปปก</b><span>{form.coverUrl?"มีแล้ว":"ยังไม่มี"}</span></p><p><LineChart/><b>จังหวัด</b><span>{form.province||"—"}</span></p></section>
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
        <section className="store-tips"><header><h2>💡 เคล็ดลับเพิ่มยอดขาย</h2><button onClick={()=>notify("แสดงเคล็ดลับทั้งหมด")}>ดูทั้งหมด →</button></header><p><CheckCircle2/>เพิ่มรูปภาพหน้าร้าน และวิดีโอแนะนำ <b>+20%</b></p><p><CheckCircle2/>ตั้งค่าคูปองส่วนลดสำหรับลูกค้าใหม่ <b>+15%</b></p><p><CheckCircle2/>เชื่อมต่อ LINE OA เพื่อแจ้งโปรโมชั่น <b>+30%</b></p></section>
      </aside></div>
    </section>{notice&&<div className="merchant-toast" role="status">{notice}</div>}
  </main>
}
