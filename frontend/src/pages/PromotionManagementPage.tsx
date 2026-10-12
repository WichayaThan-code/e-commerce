import { useState } from "react";
import {
  BarChart3,
  Bell,
  Box,
  CalendarDays,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  CircleHelp,
  CircleUserRound,
  Copy,
  Crown,
  Ellipsis,
  Gift,
  Headphones,
  Home,
  Lightbulb,
  Megaphone,
  Pencil,
  Plus,
  Search,
  Settings,
  ShoppingBag,
  Store,
  Tag,
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
import "../styles/promotion-management.css";

type Promotion = {
  code: string;
  description: string;
  type: "ส่วนลด %" | "ส่งฟรี" | "ส่วนลดบาท";
  benefit: string;
  condition: string;
  period: string;
  used: number;
  limit: number;
  status: "ใช้งานอยู่" | "หมดอายุ" | "ใกล้หมดอายุ";
  icon: "ticket" | "truck" | "bag" | "gift" | "tag" | "cart";
};

const initialPromotions: Promotion[] = [
  { code: "WELCOME10", description: "ลดทันที 10% สำหรับลูกค้าใหม่", type: "ส่วนลด %", benefit: "ลด 10%", condition: "สูงสุด 100 บาท\nขั้นต่ำ 300 บาท", period: "1 ก.ย. 2566 -\n30 ก.ย. 2566", used: 532, limit: 1000, status: "ใช้งานอยู่", icon: "ticket" },
  { code: "FREESHIP", description: "ส่งฟรีทั่วไทย", type: "ส่งฟรี", benefit: "ฟรีค่าจัดส่ง", condition: "ขั้นต่ำ 500 บาท", period: "1 ก.ย. 2566 -\n31 ธ.ค. 2566", used: 412, limit: 1000, status: "ใช้งานอยู่", icon: "truck" },
  { code: "MIDYEAR50", description: "ลด 50 บาท ไม่มีขั้นต่ำ", type: "ส่วนลดบาท", benefit: "ลด 50 บาท", condition: "ไม่มีขั้นต่ำ", period: "15 มิ.ย. 2566 -\n30 มิ.ย. 2566", used: 1000, limit: 1000, status: "หมดอายุ", icon: "bag" },
  { code: "BIRTHDAY20", description: "ส่วนลดวันเกิดลูกค้า", type: "ส่วนลด %", benefit: "ลด 20%", condition: "สูงสุด 200 บาท", period: "1 ม.ค. 2566 -\n31 ธ.ค. 2566", used: 86, limit: 500, status: "ใช้งานอยู่", icon: "gift" },
  { code: "FLASHSALE30", description: "ลดแรง 30% เฉพาะสินค้าที่ร่วมรายการ", type: "ส่วนลด %", benefit: "ลด 30%", condition: "สูงสุด 300 บาท", period: "10 ก.ย. 2566 -\n12 ก.ย. 2566", used: 220, limit: 300, status: "ใกล้หมดอายุ", icon: "tag" },
  { code: "BUNDLE100", description: "ซื้อครบ 1,000 ลด 100", type: "ส่วนลดบาท", benefit: "ลด 100 บาท", condition: "ขั้นต่ำ 1,000 บาท", period: "1 ก.ย. 2566 -\n30 พ.ย. 2566", used: 64, limit: 200, status: "ใช้งานอยู่", icon: "cart" },
];

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

function Brand() {
  return (
    <a className="merchant-brand" href="/">
      <span><ShoppingBag /><i>✓</i></span>
      <div><b>ThaiShop<span>Fun</span></b><small>Shop Local · Support Thai · Smile Together</small></div>
    </a>
  );
}

function PromoIcon({ kind }: { kind: Promotion["icon"] }) {
  const Icon = kind === "truck" ? Truck : kind === "gift" ? Gift : kind === "tag" ? Tag : kind === "cart" ? ShoppingBag : kind === "bag" ? ShoppingBag : TicketPercent;
  return <span className={`promo-kind ${kind}`}><Icon /></span>;
}

export default function PromotionManagementPage() {
  const { user, loading } = useAuth();
  // interim: no PROMOTION_* permission in the seed yet, promotions are shop settings
  const { canInShop, canSeeMenuItem, deniedTitle } = usePermissions();
  const canManage = canInShop("SHOP_UPDATE");
  const [promotions] = useState(initialPromotions);
  const [query, setQuery] = useState("");
  const [status, setStatus] = useState("ทั้งหมด");
  const [type, setType] = useState("ทั้งหมด");
  const [channel, setChannel] = useState("ทั้งหมด");
  const [tab, setTab] = useState("ทั้งหมด");
  const [selected, setSelected] = useState<string[]>([]);
  const [notice, setNotice] = useState("");

  if (loading) return <main className="auth-loading">กำลังตรวจสอบสิทธิ์ร้านค้า...</main>;
  if (!user) { window.location.replace("/login?next=/merchant/promotions"); return null; }
  if ((hasPlatformAccess(user) && !hasMerchantAccess(user))) { window.location.replace("/admin"); return null; }
  if (!hasMerchantAccess(user)) { window.location.replace("/"); return null; }

  const shopName = (user.firstName || "MaeSomchai").toLowerCase() === "merchant" ? "MaeSomchai Official" : `${user.firstName || "MaeSomchai"} Official`;
  const notify = (message: string) => {
    setNotice(message);
    window.setTimeout(() => setNotice(""), 1900);
  };
  const visible = promotions.filter((promotion) => {
    const matchQuery = `${promotion.code} ${promotion.description}`.toLowerCase().includes(query.trim().toLowerCase());
    const matchStatus = status === "ทั้งหมด" || promotion.status === status;
    const matchType = type === "ทั้งหมด" || promotion.type === type;
    const matchTab = tab === "ทั้งหมด" || (tab === "คูปองส่วนลด" && promotion.type !== "ส่งฟรี") || (tab === "ส่งฟรี" && promotion.type === "ส่งฟรี") || (tab === "หมดอายุแล้ว" && promotion.status === "หมดอายุ") || (tab === "โปรโมชั่นพิเศษ" && promotion.code.includes("FLASH"));
    return matchQuery && matchStatus && matchType && matchTab;
  });
  const reset = () => { setQuery(""); setStatus("ทั้งหมด"); setType("ทั้งหมด"); setChannel("ทั้งหมด"); setTab("ทั้งหมด"); };
  const toggleAll = () => setSelected(selected.length === visible.length ? [] : visible.map((item) => item.code));

  return (
    <main className="merchant-shell pm-shell promo-shell">
      <header className="merchant-topbar">
        <Brand />
        <form onSubmit={(event) => { event.preventDefault(); notify("ค้นหาข้อมูลร้านค้าแล้ว"); }}>
          <Search /><input aria-label="ค้นหาทั้งระบบ" placeholder="ค้นหาสินค้า, คำสั่งซื้อ, ลูกค้า..." />
        </form>
        <LanguageSwitcher /> <MerchantBell />
        <MerchantShopMenu />
      </header>

      <aside className="merchant-sidebar pm-sidebar promo-sidebar">
        <nav>{menu.filter(canSeeMenuItem).map(([Icon, label, href, badge]) => (
          <a className={label === "โปรโมชั่น/คูปอง" ? "active" : ""} href={href} key={label} onClick={(event) => { if ((href as string) === "#") { event.preventDefault(); notify(`${label}: อยู่ระหว่างเชื่อมข้อมูล`); } }}>
            <Icon />{label}{badge && <i>{badge}</i>}
          </a>
        ))}</nav>
        <section className="upgrade-card"><Crown /><h3>ยกระดับร้านค้าของคุณ</h3><p>ใช้ฟีเจอร์ครบชุด เพิ่มยอดขาย<br />ได้มากกว่าเดิม</p><button onClick={() => notify("กำลังเปิดรายละเอียดแพ็กเกจ")}>ดูแพ็กเกจ</button></section>
        <div className="merchant-support"><a href="/help"><CircleHelp />ศูนย์ช่วยเหลือ</a><button onClick={() => notify("กำลังติดต่อทีมงาน")}><Headphones />ติดต่อทีมงาน</button></div>
      </aside>

      <section className="pm-content promo-content">
        <header className="pm-heading promo-heading">
          <div><h1><Tag />โปรโมชั่น / คูปอง</h1><p>สร้างและจัดการโปรโมชั่น เพื่อกระตุ้นยอดขายให้ร้านของคุณ</p></div>
          <div className="pm-actions"><button onClick={() => notify("เปิดประวัติการใช้คูปอง")}><TicketPercent />ดูประวัติการใช้คูปอง</button><button disabled={!canManage} title={deniedTitle(canManage)} className="primary" onClick={() => notify("เปิดแบบฟอร์มสร้างโปรโมชั่น / คูปอง")}><Plus />สร้างโปรโมชั่น / คูปอง</button></div>
        </header>

        <section className="pm-kpis promo-kpis">
          <article className="total"><span><TicketPercent /></span><div><small>โปรโมชั่นทั้งหมด</small><b>12<i>↑ 20%</i></b><em>จากเดือนก่อน</em></div></article>
          <article className="active"><span><TicketPercent /></span><div><small>คูปองที่ใช้งานอยู่</small><b>8<i>↑ 33%</i></b><em>จากเดือนก่อน</em></div></article>
          <article className="uses"><span><UsersRound /></span><div><small>จำนวนการใช้คูปอง</small><b>1,245<i>↑ 18%</i></b><em>จากเดือนก่อน</em></div></article>
          <article className="value"><span><BarChart3 /></span><div><small>มูลค่าลดรวม</small><b>฿ 24,560<i>↑ 25%</i></b><em>จากเดือนก่อน</em></div></article>
        </section>

        <div className="promo-layout">
          <section className="promo-table-card">
            <div className="promo-tabs-search">
              <div className="promo-tabs">{[["ทั้งหมด", "12"], ["คูปองส่วนลด", "8"], ["ส่งฟรี", "2"], ["โปรโมชั่นพิเศษ", "2"], ["หมดอายุแล้ว", "4"]].map(([label, count]) => <button key={label} className={tab === label ? "active" : ""} onClick={() => setTab(label)}>{label} ({count})</button>)}</div>
              <label><Search /><input aria-label="ค้นหาโปรโมชั่น" value={query} onChange={(event) => setQuery(event.target.value)} placeholder="ค้นหาชื่อโปรโมชั่น หรือรหัสคูปอง..." /></label>
            </div>
            <form className="promo-filters" onSubmit={(event) => { event.preventDefault(); notify(`พบโปรโมชั่น ${visible.length} รายการ`); }}>
              <label>สถานะ<select aria-label="สถานะโปรโมชั่น" value={status} onChange={(event) => setStatus(event.target.value)}><option>ทั้งหมด</option><option>ใช้งานอยู่</option><option>ใกล้หมดอายุ</option><option>หมดอายุ</option></select></label>
              <label>ประเภท<select aria-label="ประเภทโปรโมชั่น" value={type} onChange={(event) => setType(event.target.value)}><option>ทั้งหมด</option><option>ส่วนลด %</option><option>ส่วนลดบาท</option><option>ส่งฟรี</option></select></label>
              <label>ช่องทาง<select aria-label="ช่องทางโปรโมชั่น" value={channel} onChange={(event) => setChannel(event.target.value)}><option>ทั้งหมด</option><option>เว็บไซต์</option><option>LINE OA</option></select></label>
              <label>ช่วงวันที่<span className="promo-date"><CalendarDays />ทั้งหมด<ChevronDown /></span></label>
              <div><button type="button" onClick={reset}>รีเซ็ต</button><button className="primary">ค้นหา</button></div>
            </form>
            <div className="promo-table-wrap">
              <table>
                <thead><tr><th><input type="checkbox" aria-label="เลือกโปรโมชั่นทั้งหมด" checked={visible.length > 0 && selected.length === visible.length} onChange={toggleAll} /></th><th>ชื่อโปรโมชั่น / คูปอง</th><th>ประเภท</th><th>ส่วนลด / เงื่อนไข</th><th>ช่วงเวลา</th><th>การใช้งาน</th><th>สถานะ</th><th>จัดการ</th></tr></thead>
                <tbody>{visible.map((promotion) => {
                  const percent = Math.round((promotion.used / promotion.limit) * 100);
                  return <tr key={promotion.code}>
                    <td><input type="checkbox" aria-label={`เลือก ${promotion.code}`} checked={selected.includes(promotion.code)} onChange={() => setSelected((items) => items.includes(promotion.code) ? items.filter((code) => code !== promotion.code) : [...items, promotion.code])} /></td>
                    <td><div className="promo-name"><PromoIcon kind={promotion.icon} /><p><b>{promotion.code}</b><small>{promotion.description}</small></p></div></td>
                    <td><span className={`promo-type ${promotion.type === "ส่งฟรี" ? "shipping" : promotion.type === "ส่วนลดบาท" ? "cash" : "percent"}`}>{promotion.type}</span></td>
                    <td><b>{promotion.benefit}</b>{promotion.condition.split("\n").map((line) => <small key={line}>{line}</small>)}</td>
                    <td>{promotion.period.split("\n").map((line) => <span key={line}>{line}</span>)}</td>
                    <td><b>{promotion.used.toLocaleString()} / {promotion.limit.toLocaleString()} ครั้ง</b><div className="promo-progress"><i style={{ width: `${percent}%` }} /></div><small>{percent}%</small></td>
                    <td><span className={`promo-status ${promotion.status === "ใช้งานอยู่" ? "on" : promotion.status === "หมดอายุ" ? "expired" : "ending"}`}>{promotion.status}</span></td>
                    <td><div className="promo-row-actions"><button disabled={!canManage} title={deniedTitle(canManage)} aria-label={`แก้ไข ${promotion.code}`} onClick={() => notify(`กำลังแก้ไข ${promotion.code}`)}><Pencil /></button><button disabled={!canManage} title={deniedTitle(canManage)} aria-label={`ทำสำเนา ${promotion.code}`} onClick={() => notify(`ทำสำเนา ${promotion.code} แล้ว`)}><Copy /></button><button disabled={!canManage} title={deniedTitle(canManage)} aria-label={`ตัวเลือก ${promotion.code}`} onClick={() => notify(`เปิดตัวเลือกของ ${promotion.code}`)}><Ellipsis /></button></div></td>
                  </tr>;
                })}</tbody>
              </table>
              {visible.length === 0 && <div className="promo-empty">ไม่พบโปรโมชั่นที่ตรงกับตัวกรอง</div>}
            </div>
            <div className="promo-pagination"><span>แสดง 1 - {visible.length} จาก 12 รายการ</span><label>แสดง<select aria-label="จำนวนรายการ"><option>10</option><option>20</option></select>รายการ</label><nav aria-label="หน้าโปรโมชั่น"><button aria-label="ก่อนหน้า"><ChevronLeft /></button><button className="active">1</button><button>2</button><button aria-label="ถัดไป"><ChevronRight /></button></nav></div>
          </section>

          <aside className="promo-aside">
            <section className="promo-guide"><h2><Megaphone />สร้างโปรโมชั่นง่ายๆ</h2>{["เลือกประเภทโปรโมชั่น", "ตั้งค่าส่วนลดและเงื่อนไข", "กำหนดช่วงเวลา", "เลือกสินค้าที่ร่วมรายการ (ถ้ามี)", "เผยแพร่ และเริ่มใช้งานได้ทันที"].map((text) => <p key={text}><b>✓</b>{text}</p>)}</section>
            <section className="promo-ideas"><h2><Lightbulb />ไอเดียโปรโมชั่น</h2>{["ส่วนลดต้อนรับลูกค้าใหม่", "คูปองวันเกิด", "ส่งฟรีเมื่อซื้อครบตามกำหนด", "ซื้อครบ X ลด Y", "แฟลชเซลล์ช่วงเทศกาล", "โปรเฉพาะสมาชิก"].map((text) => <button key={text} onClick={() => notify(`เลือกไอเดีย: ${text}`)}><span>♨</span>{text}<ChevronRight /></button>)}</section>
            <section className="promo-cta"><Gift /><h2>โปรโมชั่นดี ๆ<br />เพิ่มยอดขายให้ร้าน</h2><button disabled={!canManage} title={deniedTitle(canManage)} onClick={() => notify("เปิดแบบฟอร์มสร้างโปรโมชั่น / คูปอง")}>เริ่มสร้างเลยวันนี้!</button></section>
          </aside>
        </div>
      </section>
      {notice && <div className="merchant-toast" role="status">{notice}</div>}
    </main>
  );
}
