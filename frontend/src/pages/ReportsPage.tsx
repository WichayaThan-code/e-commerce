import { useState } from "react";
import {
  BarChart3,
  Bell,
  Box,
  CalendarDays,
  ChevronDown,
  CircleHelp,
  CircleUserRound,
  Crown,
  Download,
  FileText,
  Headphones,
  Home,
  Lightbulb,
  Package,
  Search,
  Settings,
  ShoppingBag,
  ShoppingCart,
  Store,
  TicketPercent,
  Truck,
  UsersRound,
  WalletCards,
} from "lucide-react";
import MerchantBell from "../components/merchant/MerchantBell";
import LanguageSwitcher from "../components/LanguageSwitcher";
import MerchantShopMenu from "../components/MerchantShopMenu";
import { useAuth } from "../contexts/AuthContext";
import { hasPlatformAccess, hasMerchantAccess } from "../services/permissions";
import { usePermissions } from "../hooks/usePermissions";
import "../styles/merchant-dashboard.css";
import "../styles/product-management.css";
import "../styles/reports.css";

const menu = [
  [Home, "หน้าหลัก", "/merchant/dashboard"],
  [Box, "จัดการสินค้า", "/merchant/products"],
  [ShoppingBag, "คำสั่งซื้อ", "/merchant/orders", "12"],
  [CircleUserRound, "ลูกค้า", "/merchant/customers"],
  [TicketPercent, "โปรโมชั่น/คูปอง", "/merchant/promotions"],
  [BarChart3, "รายงาน", "/merchant/reports"],
  [WalletCards, "การเงิน", "/merchant/finance"],
  [Truck, "การจัดส่ง", "/merchant/shipping"],
  [Store, "ร้านของฉัน", "/merchant/store"],
  [Settings, "ตั้งค่า", "/merchant/settings"],
] as const;

const sales = [9800, 9000, 10800, 13000, 14000, 12500, 14500, 15800, 13800, 16000, 19000, 21430];
const income = [7600, 6800, 8100, 9800, 11200, 9200, 11500, 12600, 11000, 13000, 15800, 20359];
const fees = [700, 620, 760, 920, 1030, 850, 1050, 1150, 1000, 1190, 1450, 1071];
const channels = [
  ["เว็บไซต์ร้าน", "฿ 52,340", 42, "web"], ["LINE OA", "฿ 34,870", 28, "line"], ["Facebook", "฿ 19,920", 16, "facebook"],
  ["TikTok Shop", "฿ 9,960", 8, "tiktok"], ["Shopee", "฿ 6,230", 5, "shopee"], ["Lazada", "฿ 3,120", 3, "lazada"],
] as const;
const products = [
  ["🍚", "ข้าวหอมมะลิ 5 กก.", "120", "฿ 23,880"], ["🫙", "น้ำพริกแม่สมชาย", "98", "฿ 14,700"], ["🥭", "มะม่วงอบแห้ง", "76", "฿ 11,400"],
  ["🪴", "ชาสมุนไพร", "64", "฿ 9,600"], ["🎁", "เซ็ตของฝากไทย", "52", "฿ 8,320"],
] as const;

function Brand() {
  return <a className="merchant-brand" href="/"><span><ShoppingBag /><i>✓</i></span><div><b>ThaiShop<span>Fun</span></b><small>Shop Local · Support Thai · Smile Together</small></div></a>;
}

export default function ReportsPage() {
  const { user, loading } = useAuth();
  const { canInShop, canSeeMenuItem, deniedTitle } = usePermissions();
  const canExport = canInShop("REPORT_EXPORT");
  const [activeTab, setActiveTab] = useState("ภาพรวม");
  const [range, setRange] = useState("1 ก.ย. 2026 - 12 ก.ย. 2026");
  const [notice, setNotice] = useState("");
  if (loading) return <main className="auth-loading">กำลังตรวจสอบสิทธิ์ร้านค้า...</main>;
  if (!user) { window.location.replace("/login?next=/merchant/reports"); return null; }
  if ((hasPlatformAccess(user) && !hasMerchantAccess(user))) { window.location.replace("/admin"); return null; }
  if (!hasMerchantAccess(user)) { window.location.replace("/"); return null; }

  const shopName = (user.firstName || "MaeSomchai").toLowerCase() === "merchant" ? "MaeSomchai Official" : `${user.firstName || "MaeSomchai"} Official`;
  const notify = (message: string) => { setNotice(message); window.setTimeout(() => setNotice(""), 1900); };
  const download = () => {
    const csv = "metric,value\nยอดขายรวม,124560\nรายได้,118338\nค่าธรรมเนียม,6222\nคำสั่งซื้อ,532\nลูกค้า,248";
    const url = URL.createObjectURL(new Blob([csv], { type: "text/csv;charset=utf-8" }));
    const link = document.createElement("a"); link.href = url; link.download = "thaishopfun-report.csv"; link.click(); URL.revokeObjectURL(url);
    notify("ดาวน์โหลดรายงานแล้ว");
  };

  return <main className="merchant-shell pm-shell report-shell">
    <header className="merchant-topbar"><Brand /><form onSubmit={(event) => { event.preventDefault(); notify("ค้นหาข้อมูลร้านค้าแล้ว"); }}><Search /><input aria-label="ค้นหาทั้งระบบ" placeholder="ค้นหาสินค้า, คำสั่งซื้อ, ลูกค้า..." /></form><LanguageSwitcher /> <MerchantBell /><MerchantShopMenu /></header>
    <aside className="merchant-sidebar pm-sidebar report-sidebar"><nav>{menu.filter(canSeeMenuItem).map(([Icon, label, href, badge]) => <a className={label === "รายงาน" ? "active" : ""} href={href} key={label} onClick={(event) => { if ((href as string) === "#") { event.preventDefault(); notify(`${label}: อยู่ระหว่างเชื่อมข้อมูล`); } }}><Icon />{label}{badge && <i>{badge}</i>}</a>)}</nav><section className="upgrade-card"><Crown /><h3>ยกระดับร้านค้าของคุณ</h3><p>ใช้ฟีเจอร์ครบชุด เพิ่มยอดขาย<br />ได้มากกว่าเดิม</p><button onClick={() => notify("กำลังเปิดรายละเอียดแพ็กเกจ")}>ดูแพ็กเกจ</button></section><div className="merchant-support"><a href="/help"><CircleHelp />ศูนย์ช่วยเหลือ</a><button onClick={() => notify("กำลังติดต่อทีมงาน")}><Headphones />ติดต่อทีมงาน</button></div></aside>

    <section className="pm-content report-content">
      <header className="pm-heading report-heading"><div><h1><BarChart3 />รายงาน</h1><p>ดูภาพรวมธุรกิจของคุณ วิเคราะห์ยอดขาย และติดตามการเติบโต</p></div><div className="report-actions"><button onClick={() => { const next = range.startsWith("1 ") ? "1 ส.ค. 2026 - 31 ส.ค. 2026" : "1 ก.ย. 2026 - 12 ก.ย. 2026"; setRange(next); notify(`เปลี่ยนช่วงวันที่เป็น ${next}`); }}><CalendarDays /><span>{range}</span><ChevronDown /></button><button disabled={!canExport} title={deniedTitle(canExport)} className="primary" onClick={download}><Download />ดาวน์โหลดรายงาน</button></div></header>

      <nav className="report-tabs" aria-label="ประเภทรายงาน">{["ภาพรวม", "ยอดขาย", "สินค้า", "คำสั่งซื้อ", "ลูกค้า", "โปรโมชั่น / คูปอง", "ค่าธรรมเนียม", "การจัดส่ง"].map((tab) => <button key={tab} className={activeTab === tab ? "active" : ""} onClick={() => { setActiveTab(tab); notify(`แสดงรายงาน${tab}`); }}>{tab}</button>)}</nav>

      <section className="report-kpis">
        <article className="gmv"><span><ShoppingCart /></span><div><small>ยอดขายรวม (GMV)</small><b>฿124,560 <i>↑ 18%</i></b><em>จากช่วงก่อนหน้า</em></div></article>
        <article className="income"><span><WalletCards /></span><div><small>รายได้ของคุณ</small><b>฿ 118,338 <i>↑ 18%</i></b><em>หลังหักค่าธรรมเนียมแล้ว</em></div></article>
        <article className="fee"><span>●</span><div><small>ค่าธรรมเนียม 5%</small><b>฿ 6,222 <i>↑ 18%</i></b><em>จากยอดขายรวม</em></div></article>
        <article className="orders"><span><FileText /></span><div><small>จำนวนคำสั่งซื้อ</small><b>532 <i>↑ 14%</i></b><em>จากช่วงก่อนหน้า</em></div></article>
        <article className="customer"><span><UsersRound /></span><div><small>ลูกค้าทั้งหมด</small><b>248 <i>↑ 27%</i></b><em>ลูกค้าใหม่ 68 คน</em></div></article>
      </section>

      <section className="report-primary-grid">
        <article className="report-panel sales-chart"><header><h2>ยอดขาย และ รายได้</h2><div className="report-legend"><span><i />ยอดขาย (GMV)</span><span><i />รายได้หลังหักค่าธรรมเนียม</span><span><i />ค่าธรรมเนียม 5%</span></div></header><div className="sales-graph"><div className="graph-y"><span>฿ 25,000</span><span>฿ 20,000</span><span>฿ 15,000</span><span>฿ 10,000</span><span>฿ 5,000</span><span>0</span></div><div className="graph-plot">{sales.map((value, index) => <div className="graph-day" key={index}><div><i style={{ height: `${value / 250}%` }} /><i style={{ height: `${income[index] / 250}%` }} /><i style={{ height: `${Math.max(4, fees[index] / 250)}%` }} /></div><small>{index + 1} ก.ย.</small></div>)}<svg viewBox="0 0 720 150" preserveAspectRatio="none" aria-hidden="true"><polyline points={sales.map((value, index) => `${index * 65 + 26},${150 - value / 170}`).join(" ")} /></svg><div className="report-tip"><b>12 ก.ย. 2026</b><span>● ยอดขาย <strong>฿ 21,430</strong></span><span>● รายได้ <strong>฿ 20,359</strong></span><span>● ค่าธรรมเนียม 5% <strong>฿ 1,071</strong></span></div></div></div></article>
        <article className="report-panel category-panel"><header><h2>สัดส่วนรายได้ตามหมวดสินค้า</h2><button onClick={() => notify("เปิดรายงานหมวดสินค้าทั้งหมด")}>ดูทั้งหมด →</button></header><div className="category-body"><div className="report-donut"><b>฿124,560<small>ยอดขายรวม</small></b></div><ul>{[["อาหารและเครื่องดื่ม",32],["ของใช้ในบ้าน",24],["ความงามและสุขภาพ",18],["แฟชั่น",12],["ของฝาก/ของขวัญ",8],["อื่น ๆ",6]].map(([label,value], index) => <li key={String(label)}><i className={`color-${index}`} /><span>{label}</span><b>{value}%</b></li>)}</ul></div></article>
      </section>

      <section className="report-secondary-grid">
        <article className="report-panel channel-panel"><header><h2>ยอดขายตามช่องทาง</h2><button onClick={() => notify("เปิดยอดขายทุกช่องทาง")}>ดูทั้งหมด →</button></header><div>{channels.map(([name, amount, percent, key]) => <p key={name}><i className={key}>{key === "web" ? "◎" : key === "line" ? "LINE" : key === "facebook" ? "f" : key === "tiktok" ? "♪" : key === "shopee" ? "S" : "L"}</i><span>{name}</span><b>{amount}</b><em><i style={{width:`${percent}%`}} /></em><strong>{percent}%</strong></p>)}</div></article>
        <article className="report-panel top-products"><header><h2>สินค้าขายดี (Top 5)</h2><button onClick={() => notify("เปิดสินค้าขายดีทั้งหมด")}>ดูทั้งหมด →</button></header><div className="product-head"><span>#</span><span>สินค้า</span><span>จำนวนขาย</span><span>ยอดขาย</span></div>{products.map(([emoji,name,count,total], index) => <div className="product-row" key={name}><i>{index + 1}</i><span>{emoji}</span><b>{name}</b><em>{count}</em><strong>{total}</strong></div>)}</article>
        <article className="report-panel customer-summary"><header><h2>สรุปลูกค้า</h2><button onClick={() => notify("เปิดข้อมูลลูกค้าทั้งหมด")}>ดูทั้งหมด →</button></header>{[["👥","ลูกค้าทั้งหมด","248","↑ 27%"],["👤","ลูกค้าใหม่","68","↑ 54%"],["↻","ลูกค้ากลับมาซื้อซ้ำ","180","↑ 19%"],["🛍","จำนวนการสั่งซื้อเฉลี่ย/ลูกค้า","2.1","↑ 12%"],["♨","มูลค่าการสั่งซื้อเฉลี่ย/คำสั่งซื้อ","฿ 234","↑ 8%"]].map(([icon,label,value,growth]) => <p key={label}><i>{icon}</i><span>{label}</span><b>{value}</b><strong>{growth}</strong></p>)}</article>
      </section>

      <section className="report-bottom"><article><h2>สรุปค่าธรรมเนียม</h2><div><span className="coin-stack">●</span><p>ค่าธรรมเนียมทั้งหมด<b>฿ 6,222</b></p><strong>↑ 18%</strong></div></article><article className="fee-rate"><div className="mini-donut" /><p>คิดเป็นสัดส่วน<b>5%</b></p><span>ของยอดขายรวม</span></article><article className="report-advice"><Lightbulb /><h2>คำแนะนำ</h2><p>ยอดขายเติบโตขึ้น 18% จากช่วงก่อนหน้า 🎉<br />ลองเพิ่มโปรโมชั่นสำหรับลูกค้าใหม่ เพื่อรักษาแนวโน้มการเติบโตต่อเนื่อง</p><button aria-label="ดูคำแนะนำ" onClick={() => notify("เปิดคำแนะนำเพิ่มยอดขาย")}>›</button></article></section>
    </section>{notice && <div className="merchant-toast" role="status">{notice}</div>}
  </main>;
}
