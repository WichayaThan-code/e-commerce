import { useState } from "react";
import {
  BarChart3, Bell, Box, CheckCircle2, ChevronDown, CircleHelp, CircleUserRound,
  Crown, ExternalLink, Facebook, Headphones, Home, Instagram, LineChart, MapPin,
  PackageCheck, Plus, Save, Search, Settings, ShieldCheck, ShoppingBag, Store,
  TicketPercent, Truck, UsersRound, WalletCards,
} from "lucide-react";
import { useAuth } from "./AuthContext";
import "./merchant-dashboard.css";
import "./product-management.css";
import "./my-store.css";

const menu = [
  [Home,"หน้าหลัก","/merchant/dashboard"],[Box,"จัดการสินค้า","/merchant/products"],
  [ShoppingBag,"คำสั่งซื้อ","/merchant/orders","12"],[CircleUserRound,"ลูกค้า","/merchant/customers"],
  [TicketPercent,"โปรโมชั่น/คูปอง","/merchant/promotions"],[BarChart3,"รายงาน","/merchant/reports"],
  [WalletCards,"การเงิน","/merchant/finance"],[Truck,"การจัดส่ง","/merchant/shipping"],
  [Store,"ร้านของฉัน","/merchant/store"],[Settings,"ตั้งค่า","/merchant/settings"],
] as const;
const tabs=["ข้อมูลร้านค้า","หน้าร้าน","การตั้งค่า","ช่องทางการขาย","สมาชิกในร้าน","แผนการใช้งาน"];
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
        <section className="store-tips"><header><h2>💡 เคล็ดลับเพิ่มยอดขาย</h2><button onClick={()=>notify("แสดงเคล็ดลับทั้งหมด")}>ดูทั้งหมด →</button></header><p><CheckCircle2/>เพิ่มรูปภาพหน้าร้าน และวิดีโอแนะนำ <b>+20%</b></p><p><CheckCircle2/>ตั้งค่าคูปองส่วนลดสำหรับลูกค้าใหม่ <b>+15%</b></p><p><CheckCircle2/>เชื่อมต่อ LINE OA เพื่อแจ้งโปรโมชั่น <b>+30%</b></p></section>
      </aside></div>
    </section>{notice&&<div className="merchant-toast" role="status">{notice}</div>}
  </main>
}
