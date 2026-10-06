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
  Headphones,
  Home,
  LogOut,
  Megaphone,
  PackageCheck,
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
import LanguageSwitcher from "../components/LanguageSwitcher";
import MerchantShopMenu from "../components/MerchantShopMenu";
import { useAuth } from "../contexts/AuthContext";
import "../styles/merchant-dashboard.css";

const menu = [
  [Home, "หน้าหลัก"],
  [Box, "จัดการสินค้า"],
  [ShoppingBag, "คำสั่งซื้อ", "12"],
  [CircleUserRound, "ลูกค้า"],
  [TicketPercent, "โปรโมชั่น/คูปอง"],
  [BarChart3, "รายงาน"],
  [WalletCards, "การเงิน"],
  [Truck, "การจัดส่ง"],
  [Store, "ร้านของฉัน"],
  [Settings, "ตั้งค่า"],
] as const;

const products = [
  ["🍚", "ข้าวหอมมะลิ 5 กก.", "ขายแล้ว 120 ชิ้น", "฿ 23,880"],
  ["🍯", "น้ำพริกแม่สมชาย", "ขายแล้ว 98 ชิ้น", "฿ 14,700"],
  ["🥭", "มะม่วงอบแห้ง", "ขายแล้ว 76 ชิ้น", "฿ 11,400"],
  ["🪴", "ชาสมุนไพร", "ขายแล้ว 64 ชิ้น", "฿ 9,600"],
  ["🫙", "เซ็ตของฝากไทย", "ขายแล้ว 52 ชิ้น", "฿ 8,320"],
];
const orders = [
  ["#TSF202609120045", "คุณณัฐชา", "฿ 890", "ชำระแล้ว", "12 ก.ย. 2026 14:32"],
  ["#TSF202609120044", "คุณสมชาย", "฿ 1,250", "รอจัดส่ง", "12 ก.ย. 2026 13:18"],
  ["#TSF202609120043", "คุณวรภา", "฿ 760", "รอชำระเงิน", "12 ก.ย. 2026 12:59"],
  [
    "#TSF202609120042",
    "คุณกิตติ",
    "฿ 1,950",
    "จัดส่งแล้ว",
    "12 ก.ย. 2026 11:21",
  ],
  ["#TSF202609120041", "คุณปิยะ", "฿ 420", "ยกเลิก", "12 ก.ย. 2026 10:05"],
];
const alerts = [
  ["📦", "มีคำสั่งซื้อใหม่ 3 รายการ", "14:32"],
  ["●", "ลูกค้าใหม่ลงทะเบียน", "12:18"],
  ["🚚", "พัสดุจัดส่งสำเร็จ", "11:21"],
  ["▲", "คำสั่งซื้อถูกยกเลิก", "10:05"],
  ["📣", "โปรโมชั่นใกล้สิ้นสุด (เหลือ 2 วัน)", "09:40"],
];

function MerchantBrand() {
  return (
    <a className="merchant-brand" href="/">
      <span>
        <ShoppingBag />
        <i>✓</i>
      </span>
      <div>
        <b>
          ThaiShop<span>Fun</span>
        </b>
        <small>Shop Local · Support Thai · Smile Together</small>
      </div>
    </a>
  );
}
function MerchantPanel({
  title,
  action,
  children,
  className = "",
}: {
  title: string;
  action?: string;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <section className={`merchant-panel ${className}`}>
      <header>
        <h2>{title}</h2>
        {action && <button>{action} →</button>}
      </header>
      {children}
    </section>
  );
}

export default function MerchantDashboardPage() {
  const { user, loading, logout } = useAuth();
  const [notice, setNotice] = useState("");
  if (loading)
    return <main className="auth-loading">กำลังตรวจสอบสิทธิ์ร้านค้า...</main>;
  if (!user) {
    window.location.replace("/login");
    return null;
  }
  if (user.role === "ADMIN") {
    window.location.replace("/admin");
    return null;
  }
  if (user.role !== "MERCHANT") {
    window.location.replace("/dashboard");
    return null;
  }
  const displayName = (user.firstName || "MaeSomchai").trim();
  const shopName =
    displayName.toLowerCase() === "merchant"
      ? "MaeSomchai Official"
      : `${displayName} Official`;
  const notify = (text: string) => {
    setNotice(text);
    window.setTimeout(() => setNotice(""), 1800);
  };
  const signOut = async () => {
    await logout();
    window.location.replace("/");
  };
  return (
    <main className="merchant-shell">
      <header className="merchant-topbar">
        <MerchantBrand />
        <form
          onSubmit={(e) => {
            e.preventDefault();
            notify("กำลังค้นหาข้อมูลร้านค้า");
          }}
        >
          <Search />
          <input
            aria-label="ค้นหา"
            placeholder="ค้นหาสินค้า, คำสั่งซื้อ, ลูกค้า..."
          />
        </form>
        <LanguageSwitcher /> <button className="merchant-bell" aria-label="การแจ้งเตือน">
          <Bell />
          <i>3</i>
        </button>
        <MerchantShopMenu />
      </header>
      <aside className="merchant-sidebar">
        <nav>
          {menu.map(([Icon, label, badge], index) => (
            <button
              className={index === 0 ? "active" : ""}
              key={label}
              onClick={() => {
                if (label === "จัดการสินค้า") {
                  window.location.assign("/merchant/products");
                  return;
                }
                if (label === "คำสั่งซื้อ") {
                  window.location.assign("/merchant/orders");
                  return;
                }
                if (label === "ลูกค้า") {
                  window.location.assign("/merchant/customers");
                  return;
                }
                if (label === "โปรโมชั่น/คูปอง") {
                  window.location.assign("/merchant/promotions");
                  return;
                }
                if (label === "รายงาน") {
                  window.location.assign("/merchant/reports");
                  return;
                }
                if (label === "การเงิน") {
                  window.location.assign("/merchant/finance");
                  return;
                }
                if (label === "การจัดส่ง") {
                  window.location.assign("/merchant/shipping");
                  return;
                }
                if (label === "ร้านของฉัน") {
                  window.location.assign("/merchant/store");
                  return;
                }
                if (label === "ตั้งค่า") {
                  window.location.assign("/merchant/settings");
                  return;
                }
                notify(
                  index === 0
                    ? "คุณอยู่ที่หน้าหลักแล้ว"
                    : `${label}: อยู่ระหว่างเชื่อมข้อมูล`,
                );
              }}
            >
              <Icon />
              {label}
              {badge && <i>{badge}</i>}
            </button>
          ))}
        </nav>
        <section className="upgrade-card">
          <Crown />
          <h3>ยกระดับร้านค้าของคุณ</h3>
          <p>ใช้ฟีเจอร์ครบชุด เพิ่มยอดขายได้มากกว่าเดิม</p>
          <button onClick={() => notify("กำลังเปิดรายละเอียดแพ็กเกจ")}>
            ดูแพ็กเกจ
          </button>
        </section>
        <div className="merchant-support">
          <button onClick={() => window.location.assign("/help")}>
            <CircleHelp />
            ศูนย์ช่วยเหลือ
          </button>
          <button onClick={() => notify("กำลังติดต่อทีมงาน")}>
            <Headphones />
            ติดต่อทีมงาน
          </button>
          <button onClick={signOut}>
            <LogOut />
            ออกจากระบบ
          </button>
        </div>
      </aside>
      <div className="merchant-content">
        <section className="merchant-welcome">
          <div>
            <h1>
              สวัสดีค่ะ, {displayName} <span>👋</span>
            </h1>
            <p>ยินดีต้อนรับกลับมา ดูภาพรวมร้านค้าของคุณวันนี้</p>
          </div>
          <button>
            <CalendarDays />
            <b>1 ก.ย. 2026 - 12 ก.ย. 2026</b>
            <ChevronDown />
          </button>
        </section>
        <section className="merchant-kpis">
          <article className="purple">
            <span>
              <ShoppingCart />
            </span>
            <div>
              <small>คำสั่งซื้อวันนี้</small>
              <b>
                28 <i>↑ 12%</i>
              </b>
              <em>จากเมื่อวาน</em>
            </div>
          </article>
          <article className="green">
            <span>
              <BarChart3 />
            </span>
            <div>
              <small>ยอดขายวันนี้ (GMV)</small>
              <b>
                ฿ 12,450 <i>↑ 18%</i>
              </b>
              <em>จากเมื่อวาน</em>
            </div>
          </article>
          <article className="blue">
            <span>
              <WalletCards />
            </span>
            <div>
              <small>รายได้ของคุณ</small>
              <b>
                ฿ 11,828 <i>↑ 18%</i>
              </b>
              <em>หลังหักค่าธรรมเนียมแล้ว</em>
            </div>
          </article>
          <article className="pink">
            <span>
              <ShoppingBag />
            </span>
            <div>
              <small>ค่าธรรมเนียม 5%</small>
              <b>
                ฿ 622 <i>↑ 18%</i>
              </b>
              <em>หักจากยอดขาย</em>
            </div>
          </article>
          <article className="orange">
            <span>
              <UsersRound />
            </span>
            <div>
              <small>ลูกค้าใหม่</small>
              <b>
                14 <i>↑ 27%</i>
              </b>
              <em>จากเมื่อวาน</em>
            </div>
          </article>
        </section>
        <section className="merchant-grid top">
          <MerchantPanel title="ยอดขายและรายได้" action="ดูรายงานทั้งหมด">
            <div className="merchant-legend">
              <span>
                <i />
                ยอดขาย (GMV)
              </span>
              <span>
                <i />
                รายได้หลังหักค่าธรรมเนียม
              </span>
              <span>
                <i />
                ค่าธรรมเนียม 5%
              </span>
            </div>
            <div className="merchant-chart">
              <div className="merchant-y">
                <span>฿ 25,000</span>
                <span>฿ 20,000</span>
                <span>฿ 15,000</span>
                <span>฿ 10,000</span>
                <span>฿ 5,000</span>
                <span>0</span>
              </div>
              <div className="merchant-plot">
                {[10, 12, 13, 13.5, 14, 17, 21.4].map((value, index) => (
                  <div className="merchant-day" key={index}>
                    <div>
                      <i style={{ height: `${value * 5.1}px` }} />
                      <i style={{ height: `${value * 4.8}px` }} />
                      <i style={{ height: `${Math.max(value * 0.25, 4)}px` }} />
                    </div>
                    <small>{index + 6} ก.ย.</small>
                  </div>
                ))}
                <div className="chart-tip">
                  <b>12 ก.ย. 2026</b>
                  <span>
                    <i />
                    ยอดขาย <strong>฿ 21,430</strong>
                  </span>
                  <span>
                    <i />
                    รายได้ <strong>฿ 20,359</strong>
                  </span>
                  <span>
                    <i />
                    ค่าธรรมเนียม 5% <strong>฿ 1,071</strong>
                  </span>
                </div>
              </div>
            </div>
          </MerchantPanel>
          <MerchantPanel title="สัดส่วนช่องทางการขาย" action="ดูรายละเอียด">
            <div className="merchant-channel">
              <div className="merchant-donut">
                <b>
                  ฿125,430<small>ยอดขายรวม</small>
                </b>
              </div>
              <ul>
                <li>
                  <i />
                  เว็บไซต์ร้าน (ThaiShopFun) <b>42%</b>
                </li>
                <li>
                  <i />
                  LINE OA <b>28%</b>
                </li>
                <li>
                  <i />
                  Facebook <b>16%</b>
                </li>
                <li>
                  <i />
                  TikTok Shop <b>8%</b>
                </li>
                <li>
                  <i />
                  อื่น ๆ <b>6%</b>
                </li>
              </ul>
            </div>
          </MerchantPanel>
        </section>
        <section className="merchant-grid bottom">
          <MerchantPanel title="สินค้าขายดี (Top 5)" action="ดูสินค้าทั้งหมด">
            <ol className="best-products">
              {products.map((p, index) => (
                <li key={p[1]}>
                  <i>{index + 1}</i>
                  <span>{p[0]}</span>
                  <p>
                    <b>{p[1]}</b>
                    <small>{p[2]}</small>
                  </p>
                  <strong>{p[3]}</strong>
                </li>
              ))}
            </ol>
          </MerchantPanel>
          <MerchantPanel title="คำสั่งซื้อล่าสุด" action="ดูทั้งหมด">
            <div className="recent-orders">
              {orders.map((order) => (
                <div key={order[0]}>
                  <b>{order[0]}</b>
                  <span>{order[1]}</span>
                  <strong>{order[2]}</strong>
                  <em className={`status-${order[3]}`}>{order[3]}</em>
                  <small>{order[4]}</small>
                </div>
              ))}
            </div>
          </MerchantPanel>
          <MerchantPanel title="การแจ้งเตือน" action="ดูทั้งหมด">
            <ul className="merchant-alerts">
              {alerts.map((alert, index) => (
                <li key={alert[1]} className={`alert-${index}`}>
                  <i>{alert[0]}</i>
                  <span>{alert[1]}</span>
                  <small>{alert[2]}</small>
                </li>
              ))}
            </ul>
          </MerchantPanel>
        </section>
        <section className="merchant-cta">
          <div className="cta-store">
            🏪<i>🪴</i>
          </div>
          <div>
            <h2>เพิ่มยอดขายให้ร้านของคุณ</h2>
            <p>
              ใช้เครื่องมือการตลาด เช่น คูปอง ส่วนลด และการแจ้งเตือนลูกค้าผ่าน
              LINE
            </p>
          </div>
          <button onClick={() => notify("เริ่มสร้างโปรโมชั่น")}>
            สร้างโปรโมชั่นเลย →
          </button>
          <button
            className="outline"
            onClick={() => notify("เปิดคู่มือการใช้งาน")}
          >
            ดูคู่มือการใช้งาน
          </button>
        </section>
      </div>
      {notice && (
        <div className="merchant-toast" role="status">
          {notice}
        </div>
      )}
    </main>
  );
}
