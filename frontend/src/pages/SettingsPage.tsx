import { useEffect, useState } from "react";
import {
  BarChart3, Bell, Box, CalendarDays, CheckCircle2, ChevronDown, CircleHelp,
  CircleUserRound, Clock3, CreditCard, Crown, FileText, Grid2X2, Headphones,
  Home, Languages, Link2, PackageCheck, Save, Search, Settings, ShieldCheck,
  ShoppingBag, Store, TicketPercent, Trash2, Truck, UsersRound,
  WalletCards,
} from "lucide-react";
import LanguageSwitcher from "../components/LanguageSwitcher";
import MerchantShopMenu from "../components/MerchantShopMenu";
import { useAuth } from "../contexts/AuthContext";
import { hasPlatformAccess, hasMerchantAccess } from "../services/permissions";
import { useActiveShop } from "../contexts/ActiveShopContext";
import { usePermissions } from "../hooks/usePermissions";
import { ApiError, merchantApi, type ShopHour } from "../services/api";
import "../styles/merchant-dashboard.css";
import "../styles/product-management.css";
import "../styles/settings.css";

const menu = [
  [Home,"หน้าหลัก","/merchant/dashboard"],[Box,"จัดการสินค้า","/merchant/products"],
  [ShoppingBag,"คำสั่งซื้อ","/merchant/orders","12"],[CircleUserRound,"ลูกค้า","/merchant/customers"],
  [TicketPercent,"โปรโมชั่น/คูปอง","/merchant/promotions"],[BarChart3,"รายงาน","/merchant/reports"],
  [WalletCards,"การเงิน","/merchant/finance"],[Truck,"การจัดส่ง","/merchant/shipping"],
  [Store,"ร้านของฉัน","/merchant/store"],[Settings,"ตั้งค่า","/merchant/settings"],
] as const;
const settingTabs = [
  [Grid2X2,"ทั่วไป"],[Bell,"การแจ้งเตือน"],[CreditCard,"การชำระเงิน"],[Truck,"การจัดส่ง"],
  [UsersRound,"ผู้ใช้งาน"],[ShieldCheck,"ความปลอดภัย"],[Link2,"เชื่อมต่อบริการ"],[Grid2X2,"อื่น ๆ"],
] as const;
function Brand(){return <a className="merchant-brand" href="/"><span><ShoppingBag/><i>✓</i></span><div><b>ThaiShop<span>Fun</span></b><small>Shop Local · Support Thai · Smile Together</small></div></a>}

const DAY_NAMES = ["จันทร์","อังคาร","พุธ","พฤหัสบดี","ศุกร์","เสาร์","อาทิตย์"] as const;
const defaultHours = (): ShopHour[] => DAY_NAMES.map((_, index) => ({
  open: index < 5,
  start: index < 5 ? "08:00" : "09:00",
  end: index < 5 ? "20:00" : "18:00",
}));

export default function SettingsPage(){
  const {user,loading}=useAuth();
  const {shopId,ready}=useActiveShop();
  const {canInShop,canSeeMenuItem,deniedTitle}=usePermissions();
  const canUpdate=canInShop("SHOP_UPDATE");const canClose=canInShop("SHOP_CLOSE");
  const [activeTab,setActiveTab]=useState("ทั่วไป");
  const [notice,setNotice]=useState("");
  const [settingsLoading,setSettingsLoading]=useState(true);
  const [saving,setSaving]=useState(false);
  const [hours,setHours]=useState<ShopHour[]>(defaultHours);
  const [openStore,setOpenStore]=useState(true);
  const [chat,setChat]=useState(true);
  const [showPhone,setShowPhone]=useState(true);
  const [showEmail,setShowEmail]=useState(false);
  const [showAddress,setShowAddress]=useState(true);
  const notify=(text:string)=>{setNotice(text);window.setTimeout(()=>setNotice(""),1900)};
  useEffect(()=>{
    if(loading||!user||!ready)return;
    let active=true;
    setSettingsLoading(true);
    merchantApi.getSettings(shopId).then(data=>{
      if(!active)return;
      setHours(data.openingHours?.length===7?data.openingHours:defaultHours());
    }).catch(()=>notify("โหลดเวลาทำการไม่สำเร็จ")).finally(()=>{if(active)setSettingsLoading(false);});
    return ()=>{active=false};
  },[loading,user,ready,shopId]);
  const updateHour=(index:number, patch:Partial<ShopHour>)=>setHours(current=>current.map((day,i)=>i===index?{...day,...patch}:day));
  const saveHours=async()=>{
    if(saving)return;
    if(hours.some(day=>day.open && day.start>=day.end)){
      notify("เวลาปิดต้องอยู่หลังเวลาเปิด");
      return;
    }
    setSaving(true);
    try{
      const saved=await merchantApi.saveHours(hours, shopId);
      setHours(saved.openingHours?.length===7?saved.openingHours:hours);
      notify("บันทึกเวลาทำการแล้ว");
    }catch(error){
      notify(error instanceof ApiError?error.message:"บันทึกไม่สำเร็จ กรุณาลองใหม่");
    }finally{setSaving(false);}
  };
  if(loading||!ready||settingsLoading)return <main className="auth-loading">กำลังตรวจสอบสิทธิ์ร้านค้า...</main>;
  if(!user){window.location.replace("/login?next=/merchant/settings");return null}
  if((hasPlatformAccess(user) && !hasMerchantAccess(user))){window.location.replace("/admin");return null}
  return <main className="merchant-shell pm-shell settings-shell">
    <header className="merchant-topbar"><Brand/><form onSubmit={e=>{e.preventDefault();notify("ค้นหาข้อมูลร้านค้าแล้ว")}}><Search/><input aria-label="ค้นหาทั้งระบบ" placeholder="ค้นหาสินค้า, คำสั่งซื้อ, ลูกค้า..."/></form><LanguageSwitcher /> <button className="merchant-bell" aria-label="การแจ้งเตือน"><Bell/><i>3</i></button><MerchantShopMenu /></header>
    <aside className="merchant-sidebar pm-sidebar"><nav>{menu.filter(canSeeMenuItem).map(([Icon,label,href,badge])=><a className={label==="ตั้งค่า"?"active":""} href={href} key={label}><Icon/>{label}{badge&&<i>{badge}</i>}</a>)}</nav><section className="upgrade-card"><Crown/><h3>ยกระดับร้านค้าของคุณ</h3><p>ใช้ฟีเจอร์ครบชุด เพิ่มยอดขาย<br/>ได้มากกว่าเดิม</p><button onClick={()=>notify("กำลังเปิดรายละเอียดแพ็กเกจ")}>ดูแพ็กเกจ</button></section><div className="merchant-support"><a href="/help"><CircleHelp/>ศูนย์ช่วยเหลือ</a><button onClick={()=>notify("กำลังติดต่อทีมงาน")}><Headphones/>ติดต่อทีมงาน</button></div></aside>
    <section className="pm-content settings-content">
      <header className="pm-heading settings-heading"><div><h1><Settings/>ตั้งค่า</h1><p>กำหนดวันและเวลาที่ร้านเปิดให้บริการ รวมถึงการใช้งานระบบ</p></div></header>
      <nav className="settings-tabs">{settingTabs.map(([Icon,label])=><button key={label} className={activeTab===label?"active":""} onClick={()=>{setActiveTab(label);notify(`เปิดการตั้งค่า${label}`)}}><Icon/>{label}</button>)}</nav>
      <div className="settings-layout"><div className="settings-main">
        <section className="settings-card general-card"><header><div><h2><CalendarDays/>เวลาทำการ</h2><p>เลือกวันที่ร้านเปิด และกำหนดเวลาเปิดถึงเวลาปิดของแต่ละวัน</p></div><button type="button" disabled={saving||!canUpdate} title={deniedTitle(canUpdate)} onClick={()=>void saveHours()}><Save/>{saving?"กำลังบันทึก...":"บันทึก"}</button></header>
          <ul className="hours-list">{DAY_NAMES.map((day,index)=>{const item=hours[index];return <li key={day}><label><input type="checkbox" checked={item?.open??false} onChange={()=>updateHour(index,{open:!item?.open})}/><b>{day}</b></label><input type="time" aria-label={`เวลาเปิด${day}`} value={item?.start||"08:00"} disabled={!item?.open} onChange={event=>updateHour(index,{start:event.target.value})}/><span>ถึง</span><input type="time" aria-label={`เวลาปิด${day}`} value={item?.end||"20:00"} disabled={!item?.open} onChange={event=>updateHour(index,{end:event.target.value})}/></li>})}</ul>
          <div className="settings-grid lower"><label>โซนเวลา<select><option>(GMT+7) กรุงเทพฯ</option><option>(GMT+8) สิงคโปร์</option></select></label><label>สกุลเงิน<select><option>บาท (฿)</option><option>ดอลลาร์สหรัฐ ($)</option></select></label></div>
        </section>
        <section className="settings-card policy-card"><header><div><h2><FileText/>นโยบายร้านค้า</h2><p>ตั้งค่าข้อปฏิบัติและข้อความที่แสดงให้ลูกค้าทราบ</p></div></header>{[["นโยบายการคืนสินค้า","สามารถคืนสินค้าได้ภายใน 7 วัน (เฉพาะสินค้าที่มีปัญหา)"],["นโยบายการจัดส่ง","จัดส่งภายใน 1-3 วันทำการ"],["เงื่อนไขการสั่งซื้อ","สั่งซื้อขั้นต่ำ 100 บาท"],["ข้อความต้อนรับลูกค้า","ขอบคุณที่อุดหนุนสินค้าไทย สนับสนุนชุมชนค่ะ"]].map(([title,text])=><div className="policy-row" key={title}><b>{title}</b><span>{text}</span><button disabled={!canUpdate} title={deniedTitle(canUpdate)} onClick={()=>notify(`แก้ไข${title}`)}>✎ แก้ไข</button></div>)}</section>
      </div><aside className="settings-side">
        <section className="settings-card display-card"><header><div><h2>▣ รูปแบบการแสดงผล</h2><p>ตั้งค่าภาษาและรูปแบบในระบบ</p></div></header><label>ภาษา<select><option>🇹🇭　ไทย</option><option>🇬🇧　English</option></select></label><div><label>รูปแบบวันที่<select><option>31/12/2566 (พุทธศักราช)</option><option>31/12/2023</option></select></label><label>รูปแบบตัวเลข<select><option>1,234.56</option><option>1.234,56</option></select></label></div></section>
        <section className="settings-card usage-card"><header><div><h2><FileText/>การใช้งานระบบ</h2></div></header><Toggle label="เปิดร้าน (แสดงร้านต่อสาธารณะ)" value={openStore} onChange={setOpenStore}/><Toggle label="อนุญาตให้ลูกค้าแชทได้" value={chat} onChange={setChat}/><Toggle label="แสดงเบอร์โทรศัพท์ในหน้าร้าน" value={showPhone} onChange={setShowPhone}/><Toggle label="แสดงอีเมลในหน้าร้าน" value={showEmail} onChange={setShowEmail}/><Toggle label="แสดงที่อยู่ร้านในหน้าร้าน" value={showAddress} onChange={setShowAddress}/></section>
        <section className="settings-card danger-card"><header><Trash2/><div><h2>จัดการร้านค้า</h2><p>การดำเนินการนี้ไม่สามารถย้อนกลับได้</p></div></header><div><Clock3/><p><b>พักการใช้งานร้านค้า</b><small>ปิดการแสดงร้านชั่วคราว</small></p><button disabled={!canClose} title={deniedTitle(canClose)} onClick={()=>notify("พักการใช้งานร้านค้าแล้ว")}>พักร้าน</button></div><div><Trash2/><p><b>ลบร้านค้า</b><small>ลบข้อมูลร้านค้าและสินค้าทั้งหมด</small></p><button className="delete" disabled={!canClose} title={deniedTitle(canClose)} onClick={()=>notify("ต้องยืนยันตัวตนก่อนลบร้านค้า")}>ลบร้านค้า</button></div></section>
      </aside></div>
    </section>{notice&&<div className="merchant-toast" role="status">{notice}</div>}
  </main>
}

function Toggle({label,value,onChange}:{label:string;value:boolean;onChange:(value:boolean)=>void}){return <label className="toggle-row"><span>{label}</span><button type="button" role="switch" aria-checked={value} aria-label={label} className={value?"on":""} onClick={()=>onChange(!value)}><i/></button></label>}
