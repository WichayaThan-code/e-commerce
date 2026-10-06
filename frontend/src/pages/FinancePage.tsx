import { useState } from "react";
import {
  BarChart3, Bell, Box, Building2, CalendarDays, ChevronDown, ChevronLeft, ChevronRight,
  CircleHelp, CircleUserRound, Clock3, Crown, Download, FileText, Headphones, Home,
  Landmark, Search, Settings, ShoppingBag, Store, TicketPercent, TrendingUp, Truck,
  WalletCards,
} from "lucide-react";
<<<<<<< HEAD
=======
import LanguageSwitcher from "../components/LanguageSwitcher";
import MerchantShopMenu from "../components/MerchantShopMenu";
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
import { useAuth } from "../contexts/AuthContext";
import "../styles/merchant-dashboard.css";
import "../styles/product-management.css";
import "../styles/finance.css";

const menu = [
  [Home, "หน้าหลัก", "/merchant/dashboard"], [Box, "จัดการสินค้า", "/merchant/products"],
  [ShoppingBag, "คำสั่งซื้อ", "/merchant/orders", "12"], [CircleUserRound, "ลูกค้า", "/merchant/customers"],
  [TicketPercent, "โปรโมชั่น/คูปอง", "/merchant/promotions"], [BarChart3, "รายงาน", "/merchant/reports"],
  [WalletCards, "การเงิน", "/merchant/finance"], [Truck, "การจัดส่ง", "/merchant/shipping"],
  [Store, "ร้านของฉัน", "/merchant/store"], [Settings, "ตั้งค่า", "/merchant/settings"],
] as const;
const sales = [9500, 9000, 10200, 12800, 13800, 12900, 14600, 16000, 13700, 15800, 17600, 15800, 18200, 21430];
const income = [8200, 7100, 8400, 10400, 11600, 10200, 12000, 13500, 11100, 13500, 15100, 13400, 15900, 19000];
const transactions = [
  ["12 ก.ย. 2026\n14:32", "การชำระเงิน (QR)", "#TSF202609120045", "890", "44.50", "845.50", "สำเร็จ"],
  ["12 ก.ย. 2026\n13:18", "การชำระเงิน (บัตร)", "#TSF202609120044", "1,250", "62.50", "1,187.50", "สำเร็จ"],
  ["12 ก.ย. 2026\n12:59", "การชำระเงิน (โอน)", "#TSF202609120043", "760", "38.00", "722.00", "สำเร็จ"],
  ["12 ก.ย. 2026\n11:21", "คืนเงิน (Refund)", "#TSF202609120042", "-1,950", "-97.50", "-1,852.50", "คืนเงินแล้ว"],
  ["12 ก.ย. 2026\n10:05", "การชำระเงิน (QR)", "#TSF202609120041", "420", "21.00", "399.00", "สำเร็จ"],
] as const;
const transfers = [["#T2566-015","1 ก.ย. 2026","24,560"],["#T2566-014","15 ส.ค. 2026","18,320"],["#T2566-013","1 ส.ค. 2026","21,450"],["#T2566-012","15 ก.ค. 2026","19,880"],["#T2566-011","1 ก.ค. 2026","22,610"]] as const;

function Brand(){return <a className="merchant-brand" href="/"><span><ShoppingBag/><i>✓</i></span><div><b>ThaiShop<span>Fun</span></b><small>Shop Local · Support Thai · Smile Together</small></div></a>}

export default function FinancePage(){
  const {user,loading}=useAuth();
  const [tab,setTab]=useState("ภาพรวม");
  const [range,setRange]=useState("1 ก.ย. 2026 - 12 ก.ย. 2026");
  const [notice,setNotice]=useState("");
  if(loading)return <main className="auth-loading">กำลังตรวจสอบสิทธิ์ร้านค้า...</main>;
  if(!user){window.location.replace("/login?next=/merchant/finance");return null}
  if(user.role==="ADMIN"){window.location.replace("/admin");return null}
  if(user.role!=="MERCHANT"){window.location.replace("/");return null}
  const shopName=(user.firstName||"MaeSomchai").toLowerCase()==="merchant"?"MaeSomchai Official":`${user.firstName||"MaeSomchai"} Official`;
  const notify=(text:string)=>{setNotice(text);window.setTimeout(()=>setNotice(""),1900)};
  const download=(kind="รายงาน")=>{const csv="date,type,order,sales,fee,income\n12/09/2026,QR,#TSF202609120045,890,44.50,845.50\n12/09/2026,Card,#TSF202609120044,1250,62.50,1187.50";const url=URL.createObjectURL(new Blob([csv],{type:"text/csv;charset=utf-8"}));const link=document.createElement("a");link.href=url;link.download=`thaishopfun-${kind}.csv`;link.click();URL.revokeObjectURL(url);notify(`ดาวน์โหลด${kind}แล้ว`)};
  return <main className="merchant-shell pm-shell finance-shell">
<<<<<<< HEAD
    <header className="merchant-topbar"><Brand/><form onSubmit={e=>{e.preventDefault();notify("ค้นหาข้อมูลร้านค้าแล้ว")}}><Search/><input aria-label="ค้นหาทั้งระบบ" placeholder="ค้นหาสินค้า, คำสั่งซื้อ, ลูกค้า..."/></form><button className="merchant-bell" aria-label="การแจ้งเตือน"><Bell/><i>3</i></button><div className="merchant-profile"><span>🏪</span><p><b>{shopName}</b><small>เจ้าของร้าน</small></p><ChevronDown/></div></header>
=======
    <header className="merchant-topbar"><Brand/><form onSubmit={e=>{e.preventDefault();notify("ค้นหาข้อมูลร้านค้าแล้ว")}}><Search/><input aria-label="ค้นหาทั้งระบบ" placeholder="ค้นหาสินค้า, คำสั่งซื้อ, ลูกค้า..."/></form><LanguageSwitcher /> <button className="merchant-bell" aria-label="การแจ้งเตือน"><Bell/><i>3</i></button><MerchantShopMenu /></header>
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
    <aside className="merchant-sidebar pm-sidebar"><nav>{menu.map(([Icon,label,href,badge])=><a className={label==="การเงิน"?"active":""} href={href} key={label} onClick={e=>{if((href as string)==="#"){e.preventDefault();notify(`${label}: อยู่ระหว่างเชื่อมข้อมูล`)}}}><Icon/>{label}{badge&&<i>{badge}</i>}</a>)}</nav><section className="upgrade-card"><Crown/><h3>ยกระดับร้านค้าของคุณ</h3><p>ใช้ฟีเจอร์ครบชุด เพิ่มยอดขาย<br/>ได้มากกว่าเดิม</p><button onClick={()=>notify("กำลังเปิดรายละเอียดแพ็กเกจ")}>ดูแพ็กเกจ</button></section><div className="merchant-support"><a href="/help"><CircleHelp/>ศูนย์ช่วยเหลือ</a><button onClick={()=>notify("กำลังติดต่อทีมงาน")}><Headphones/>ติดต่อทีมงาน</button></div></aside>
    <section className="pm-content finance-content">
      <header className="pm-heading finance-heading"><div><h1><WalletCards/>การเงิน</h1><p>ตรวจสอบยอดขาย รายได้ ค่าธรรมเนียม และการโอนเงิน</p></div><div className="finance-actions"><button onClick={()=>{const next=range.startsWith("1 ก.ย.")?"1 ส.ค. 2026 - 31 ส.ค. 2026":"1 ก.ย. 2026 - 12 ก.ย. 2026";setRange(next);notify(`เปลี่ยนช่วงวันที่เป็น ${next}`)}}><CalendarDays/><span>{range}</span><ChevronDown/></button><button onClick={()=>download()}><Download/>ดาวน์โหลดรายงาน</button></div></header>
      <nav className="finance-tabs" aria-label="เมนูการเงิน">{["ภาพรวม","รายการรายรับ-รายจ่าย","ค่าธรรมเนียม","การโอนเงิน","ใบแจ้งหนี้ / ใบกำกับภาษี"].map((item,index)=><button className={tab===item?"active":""} onClick={()=>{setTab(item);notify(`แสดง${item}`)}} key={item}>{index===0&&<Landmark/>}{item}</button>)}</nav>
      <section className="finance-kpis"><article className="sales"><span><TrendingUp/></span><div><small>ยอดขายรวม (GMV)</small><b>฿ 124,560 <i>↑ 18%</i></b><em>จากช่วงก่อนหน้า</em></div></article><article className="income"><span><WalletCards/></span><div><small>รายได้หลังหักค่าธรรมเนียม</small><b>฿ 118,338 <i>↑ 18%</i></b><em>จากช่วงก่อนหน้า</em></div></article><article className="fee"><span>●</span><div><small>ค่าธรรมเนียม 5%</small><b>฿ 6,222 <i>↑ 18%</i></b><em>จากช่วงก่อนหน้า</em></div></article><article className="pending"><span><Building2/></span><div><small>ยอดเงินรอการโอน ⓘ</small><b>฿ 12,450</b><em>กำหนดโอน 15 ก.ย. 2026</em></div></article></section>
      <div className="finance-top-grid"><article className="finance-panel finance-chart"><header><h2>ยอดขาย และ การรับเงิน</h2><div className="finance-legend"><span><i/>ยอดขาย (GMV)</span><span><i/>รายได้หลังหักค่าธรรมเนียม</span><span><i/>ค่าธรรมเนียม 5%</span></div><button onClick={()=>notify("เปิดรายละเอียดกราฟ")}>ดูรายละเอียด →</button></header><div className="finance-graph"><div className="f-y">{["฿ 25,000","฿ 20,000","฿ 15,000","฿ 10,000","฿ 5,000","0"].map(x=><span key={x}>{x}</span>)}</div><div className="f-plot">{sales.map((value,index)=><div className="f-day" key={index}><div><i style={{height:`${value/250}%`}}/><i style={{height:`${income[index]/250}%`}}/><i style={{height:`${Math.max(4,value/5000)}%`}}/></div><small>{index<2?index+1:index} ก.ย.</small></div>)}<svg viewBox="0 0 780 150" preserveAspectRatio="none"><polyline points={sales.map((v,i)=>`${i*58+18},${150-v/170}`).join(" ")}/></svg></div></div></article>
      <aside className="finance-panel balance-card"><header><h2>ยอดเงินในบัญชี</h2><button onClick={()=>notify("เปิดประวัติการโอน")}>ดูประวัติการโอน →</button></header><div className="paid"><span>💸</span><p>ยอดเงินที่โอนแล้ว<b>฿ 106,820</b><small>รวมทั้งหมด</small></p></div><div className="waiting"><Clock3/><p>ยอดเงินรอการโอน<b>฿ 12,450</b><small>รอบโอนถัดไป 15 ก.ย. 2026</small></p></div><button className="transfer" onClick={()=>notify("เปิดข้อมูลการโอนเงิน")}><Landmark/>ดูข้อมูลการโอนเงิน</button></aside></div>
      <div className="finance-bottom-grid"><article className="finance-panel transaction-card"><header><h2>รายการการเงินล่าสุด</h2><button onClick={()=>notify("เปิดรายการการเงินทั้งหมด")}>ดูทั้งหมด →</button></header><table><thead><tr><th>#</th><th>วันที่</th><th>รายการ</th><th>คำสั่งซื้อ</th><th>ยอดขาย (฿)</th><th>ค่าธรรมเนียม (฿)</th><th>รายได้สุทธิ (฿)</th><th>สถานะ</th><th>หลักฐาน</th></tr></thead><tbody>{transactions.map((row,index)=><tr key={row[2]}><td>{index+1}</td><td>{row[0].split("\n").map(x=><span key={x}>{x}</span>)}</td><td>{row[1]}</td><td>{row[2]}</td><td className={index===3?"negative":""}>{row[3]}</td><td className={index===3?"negative":""}>{row[4]}</td><td className={index===3?"negative":""}>{row[5]}</td><td><span className={`finance-status ${index===3?"refund":"success"}`}>{row[6]}</span></td><td><button aria-label={`ดูหลักฐาน ${row[2]}`} onClick={()=>notify(`เปิดหลักฐาน ${row[2]}`)}><FileText/></button></td></tr>)}</tbody></table><footer><span>แสดง 1 - 5 จาก 532 รายการ</span><nav><button><ChevronLeft/></button><button className="active">1</button><button>2</button><button>3</button><button>4</button><button>5</button><span>...</span><button>107</button><button><ChevronRight/></button></nav></footer></article>
      <aside className="finance-side"><article className="finance-panel transfer-history"><header><h2>สรุปการโอนเงิน</h2><button onClick={()=>notify("เปิดประวัติการโอนทั้งหมด")}>ดูทั้งหมด →</button></header><div className="transfer-head"><span>รอบโอน</span><span>วันที่โอน</span><span>จำนวนเงิน (฿)</span><span>สถานะ</span></div>{transfers.map(row=><p key={row[0]}><span>{row[0]}</span><span>{row[1]}</span><b>{row[2]}</b><i>โอนแล้ว</i></p>)}</article><article className="finance-panel documents"><h2>เอกสารทางการเงิน</h2><div><button onClick={()=>download("ใบแจ้งหนี้")}><FileText/>ดาวน์โหลดใบแจ้งหนี้</button><button onClick={()=>download("ใบกำกับภาษี")}><FileText/>ดาวน์โหลดใบกำกับภาษี</button></div></article></aside></div>
    </section>{notice&&<div className="merchant-toast" role="status">{notice}</div>}
  </main>
}
