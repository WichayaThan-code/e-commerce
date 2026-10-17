import { useState } from "react";
import {
  BarChart3,
  Bell,
  Box,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  CircleHelp,
  CircleUserRound,
  Crown,
  Download,
  Eye,
  Headphones,
  Home,
  PackageCheck,
  Printer,
  Search,
  Settings,
  ShoppingBag,
  Store,
  TicketPercent,
  Truck,
  WalletCards,
} from "lucide-react";
import MerchantBell from "../components/merchant/MerchantBell";
import LanguageSwitcher from "../components/LanguageSwitcher";
import MerchantShopMenu from "../components/MerchantShopMenu";
import { useAuth } from "../contexts/AuthContext";
import { hasPlatformAccess, hasMerchantAccess } from "../services/permissions";
import { usePermissions } from "../hooks/usePermissions";
import productManageImage from "../assets/images/references/merchant/product-manage.png";
import "../styles/merchant-dashboard.css";
import "../styles/product-management.css";
import "../styles/order-management.css";

type Order = {
  id: string;
  customer: string;
  product: string;
  items: string;
  amount: number;
  payment: "ชำระแล้ว" | "รอชำระเงิน";
  delivery: "รอจัดส่ง" | "กำลังจัดส่ง" | "จัดส่งแล้ว" | "ยกเลิก";
  date: string;
  crop: string;
};
const orders: Order[] = [
  {
    id: "#TSF202609120045",
    customer: "คุณณัฐชา",
    product: "ข้าวหอมมะลิ 5 กก.",
    items: "2 รายการ",
    amount: 890,
    payment: "ชำระแล้ว",
    delivery: "รอจัดส่ง",
    date: "12 ก.ย. 2566\n14:32",
    crop: "-320px -473px",
  },
  {
    id: "#TSF202609120044",
    customer: "คุณสมชาย",
    product: "น้ำพริกแม่สมชาย",
    items: "1 รายการ",
    amount: 1250,
    payment: "ชำระแล้ว",
    delivery: "กำลังจัดส่ง",
    date: "12 ก.ย. 2566\n13:18",
    crop: "-320px -527px",
  },
  {
    id: "#TSF202609120043",
    customer: "คุณวรภา",
    product: "มะม่วงอบแห้ง",
    items: "3 รายการ",
    amount: 760,
    payment: "รอชำระเงิน",
    delivery: "รอจัดส่ง",
    date: "12 ก.ย. 2566\n12:59",
    crop: "-320px -581px",
  },
  {
    id: "#TSF202609120042",
    customer: "คุณกิตติ",
    product: "เซ็ตของฝากไทย",
    items: "4 รายการ",
    amount: 1950,
    payment: "ชำระแล้ว",
    delivery: "จัดส่งแล้ว",
    date: "12 ก.ย. 2566\n11:21",
    crop: "-320px -635px",
  },
  {
    id: "#TSF202609120041",
    customer: "คุณปิยะ",
    product: "ต้นไม้มงคล เสริมโชค",
    items: "1 รายการ",
    amount: 420,
    payment: "ชำระแล้ว",
    delivery: "ยกเลิก",
    date: "12 ก.ย. 2566\n10:05",
    crop: "-320px -690px",
  },
  {
    id: "#TSF202609110038",
    customer: "คุณอารยา",
    product: "กาแฟดอยช้าง",
    items: "2 รายการ",
    amount: 640,
    payment: "ชำระแล้ว",
    delivery: "จัดส่งแล้ว",
    date: "11 ก.ย. 2566\n18:45",
    crop: "-320px -744px",
  },
  {
    id: "#TSF202609110035",
    customer: "คุณธนกร",
    product: "เสื้อยืดผ้าฝ้าย",
    items: "2 รายการ",
    amount: 398,
    payment: "ชำระแล้ว",
    delivery: "กำลังจัดส่ง",
    date: "11 ก.ย. 2566\n16:20",
    crop: "-320px -798px",
  },
  {
    id: "#TSF202609110031",
    customer: "คุณมณีรัตน์",
    product: "สบู่สมุนไพร",
    items: "5 รายการ",
    amount: 445,
    payment: "ชำระแล้ว",
    delivery: "รอจัดส่ง",
    date: "11 ก.ย. 2566\n14:08",
    crop: "-320px -852px",
  },
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
function Thumb({ order }: { order: Order }) {
  const [left, top] = order.crop.split(" ");
  return (
    <span className="om-thumb">
      <img src={productManageImage} alt="" style={{ left, top }} />
    </span>
  );
}

export default function OrderManagementPage() {
  const { user, loading } = useAuth();
  const { canInShop, canSeeMenuItem, deniedTitle } = usePermissions();
  const canExport = canInShop("ORDER_EXPORT");
  const canPack = canInShop("SHIPMENT_PACK");
  const [query, setQuery] = useState("");
  const [payment, setPayment] = useState("ทั้งหมด");
  const [delivery, setDelivery] = useState("ทั้งหมด");
  const [period, setPeriod] = useState("30 วันล่าสุด");
  const [notice, setNotice] = useState("");
  const [selected, setSelected] = useState<string[]>([]);
  if (loading)
    return <main className="auth-loading">กำลังตรวจสอบสิทธิ์ร้านค้า...</main>;
  if (!user) {
    window.location.replace("/login?next=/merchant/orders");
    return null;
  }
  if ((hasPlatformAccess(user) && !hasMerchantAccess(user))) {
    window.location.replace("/admin");
    return null;
  }
  if (!hasMerchantAccess(user)) {
    window.location.replace("/");
    return null;
  }
  const shopName =
    (user.firstName || "MaeSomchai").toLowerCase() === "merchant"
      ? "MaeSomchai Official"
      : `${user.firstName || "MaeSomchai"} Official`;
  const notify = (text: string) => {
    setNotice(text);
    window.setTimeout(() => setNotice(""), 1900);
  };
  const visible = orders.filter(
    (o) =>
      `${o.id} ${o.customer} ${o.product}`
        .toLowerCase()
        .includes(query.trim().toLowerCase()) &&
      (payment === "ทั้งหมด" || o.payment === payment) &&
      (delivery === "ทั้งหมด" || o.delivery === delivery),
  );
  const reset = () => {
    setQuery("");
    setPayment("ทั้งหมด");
    setDelivery("ทั้งหมด");
    setPeriod("30 วันล่าสุด");
  };
  const toggleAll = () =>
    setSelected(
      selected.length === visible.length ? [] : visible.map((o) => o.id),
    );
  return (
    <main className="merchant-shell om-shell">
      <header className="merchant-topbar">
        <Brand />
        <form
          onSubmit={(e) => {
            e.preventDefault();
            notify("ค้นหาข้อมูลร้านค้าแล้ว");
          }}
        >
          <Search />
          <input
            aria-label="ค้นหาทั้งระบบ"
            placeholder="ค้นหาสินค้า, คำสั่งซื้อ, ลูกค้า..."
          />
        </form>
        <LanguageSwitcher /> <MerchantBell />
        <MerchantShopMenu />
      </header>
      <aside className="merchant-sidebar pm-sidebar om-sidebar">
        <nav>
          {menu.filter(canSeeMenuItem).map(([Icon, label, href, badge]) => (
            <a
              className={label === "คำสั่งซื้อ" ? "active" : ""}
              href={href}
              key={label}
              onClick={(e) => {
                if ((href as string) === "#") {
                  e.preventDefault();
                  notify(`${label}: อยู่ระหว่างเชื่อมข้อมูล`);
                }
              }}
            >
              <Icon />
              {label}
              {badge && <i>{badge}</i>}
            </a>
          ))}
        </nav>
        <section className="upgrade-card">
          <Crown />
          <h3>ยกระดับร้านค้าของคุณ</h3>
          <p>
            ใช้ฟีเจอร์ครบชุด เพิ่มยอดขาย
            <br />
            ได้มากกว่าเดิม
          </p>
          <button onClick={() => notify("กำลังเปิดรายละเอียดแพ็กเกจ")}>
            ดูแพ็กเกจ
          </button>
        </section>
        <div className="merchant-support">
          <a href="/help">
            <CircleHelp />
            ศูนย์ช่วยเหลือ
          </a>
          <button onClick={() => notify("กำลังติดต่อทีมงาน")}>
            <Headphones />
            ติดต่อทีมงาน
          </button>
        </div>
      </aside>
      <section className="pm-content om-content">
        <header className="pm-heading om-heading">
          <div>
            <h1>คำสั่งซื้อ</h1>
            <p>ติดตามและจัดการคำสั่งซื้อทั้งหมดของร้านคุณ</p>
          </div>
          <div className="pm-actions">
            <button disabled={!canExport} title={deniedTitle(canExport)}
              onClick={() => notify("ส่งออกข้อมูลคำสั่งซื้อเป็น Excel แล้ว")}
            >
              <Download />
              ส่งออก (Excel)
            </button>
            <button disabled={!canPack} title={deniedTitle(canPack)}
              className="primary"
              onClick={() =>
                notify(
                  selected.length
                    ? `พิมพ์ใบปะหน้า ${selected.length} รายการ`
                    : "กรุณาเลือกคำสั่งซื้อ",
                )
              }
            >
              <Printer />
              พิมพ์ใบปะหน้า
            </button>
          </div>
        </header>
        <section className="pm-kpis om-kpis">
          <article className="all">
            <span>
              <ShoppingBag />
            </span>
            <div>
              <small>คำสั่งซื้อทั้งหมด</small>
              <b>
                1,248<i>↑ 18%</i>
              </b>
              <em>จากเดือนก่อน</em>
            </div>
          </article>
          <article className="low">
            <span>฿</span>
            <div>
              <small>รอชำระเงิน</small>
              <b>
                8<i>1%</i>
              </b>
              <em>รอลูกค้าชำระเงิน</em>
            </div>
          </article>
          <article className="empty">
            <span>
              <Truck />
            </span>
            <div>
              <small>รอจัดส่ง</small>
              <b>
                12<i>วันนี้</i>
              </b>
              <em>ควรจัดส่งภายในวันนี้</em>
            </div>
          </article>
          <article className="ready">
            <span>
              <PackageCheck />
            </span>
            <div>
              <small>จัดส่งสำเร็จ</small>
              <b>
                1,189<i>95%</i>
              </b>
              <em>สำเร็จในเดือนนี้</em>
            </div>
          </article>
        </section>
        <section className="pm-table-card om-card">
          <div className="om-tabs">
            <button className="active">
              ทั้งหมด <i>1,248</i>
            </button>
            <button>
              รอชำระเงิน <i>8</i>
            </button>
            <button>
              รอจัดส่ง <i>12</i>
            </button>
            <button>
              กำลังจัดส่ง <i>39</i>
            </button>
            <button>จัดส่งแล้ว</button>
            <button>ยกเลิก</button>
          </div>
          <form
            className="om-filters"
            onSubmit={(e) => {
              e.preventDefault();
              notify(`พบ ${visible.length} คำสั่งซื้อ`);
            }}
          >
            <label className="om-search">
              <Search />
              <input
                aria-label="ค้นหาคำสั่งซื้อ"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="ค้นหาเลขที่คำสั่งซื้อ ชื่อลูกค้า หรือสินค้า..."
              />
            </label>
            <label>
              การชำระเงิน
              <select
                aria-label="การชำระเงิน"
                value={payment}
                onChange={(e) => setPayment(e.target.value)}
              >
                <option>ทั้งหมด</option>
                <option>ชำระแล้ว</option>
                <option>รอชำระเงิน</option>
              </select>
            </label>
            <label>
              สถานะจัดส่ง
              <select
                aria-label="สถานะจัดส่ง"
                value={delivery}
                onChange={(e) => setDelivery(e.target.value)}
              >
                <option>ทั้งหมด</option>
                <option>รอจัดส่ง</option>
                <option>กำลังจัดส่ง</option>
                <option>จัดส่งแล้ว</option>
                <option>ยกเลิก</option>
              </select>
            </label>
            <label>
              ช่วงเวลา
              <select
                aria-label="ช่วงเวลา"
                value={period}
                onChange={(e) => setPeriod(e.target.value)}
              >
                <option>30 วันล่าสุด</option>
                <option>7 วันล่าสุด</option>
                <option>เดือนนี้</option>
              </select>
            </label>
            <div>
              <button type="button" onClick={reset}>
                รีเซ็ต
              </button>
              <button className="primary">ค้นหา</button>
            </div>
          </form>
          <div className="om-table-wrap">
            <table>
              <thead>
                <tr>
                  <th>
                    <input
                      aria-label="เลือกคำสั่งซื้อทั้งหมด"
                      type="checkbox"
                      checked={
                        visible.length > 0 && selected.length === visible.length
                      }
                      onChange={toggleAll}
                    />
                  </th>
                  <th>คำสั่งซื้อ</th>
                  <th>ลูกค้า</th>
                  <th>สินค้า</th>
                  <th>ยอดรวม</th>
                  <th>การชำระเงิน</th>
                  <th>สถานะจัดส่ง</th>
                  <th>วันที่สั่งซื้อ</th>
                  <th>จัดการ</th>
                </tr>
              </thead>
              <tbody>
                {visible.map((o) => (
                  <tr key={o.id}>
                    <td>
                      <input
                        aria-label={`เลือก ${o.id}`}
                        type="checkbox"
                        checked={selected.includes(o.id)}
                        onChange={() =>
                          setSelected((xs) =>
                            xs.includes(o.id)
                              ? xs.filter((id) => id !== o.id)
                              : [...xs, o.id],
                          )
                        }
                      />
                    </td>
                    <td>
                      <b className="num">{o.id}</b>
                    </td>
                    <td>
                      <span className="om-customer">
                        {o.customer.slice(-2, -1)}
                      </span>
                      <b>{o.customer}</b>
                    </td>
                    <td>
                      <div className="om-product">
                        <Thumb order={o} />
                        <p>
                          <b>{o.product}</b>
                          <small>{o.items}</small>
                        </p>
                      </div>
                    </td>
                    <td>
                      <strong className="num">฿ {o.amount.toLocaleString()}</strong>
                    </td>
                    <td>
                      <span
                        className={`om-pill ${o.payment === "ชำระแล้ว" ? "paid" : "pending"}`}
                      >
                        {o.payment}
                      </span>
                    </td>
                    <td>
                      <span
                        className={`om-pill ${o.delivery === "รอจัดส่ง" ? "waiting" : o.delivery === "กำลังจัดส่ง" ? "shipping" : o.delivery === "จัดส่งแล้ว" ? "delivered" : "cancelled"}`}
                      >
                        {o.delivery}
                      </span>
                    </td>
                    <td>
                      {o.date.split("\n").map((x) => (
                        <span key={x}>{x}</span>
                      ))}
                    </td>
                    <td>
                      <div className="om-row-actions">
                        <button
                          aria-label={`ดู ${o.id}`}
                          onClick={() => notify(`เปิดรายละเอียด ${o.id}`)}
                        >
                          <Eye />
                        </button>
                        <button disabled={!canPack} title={deniedTitle(canPack)}
                          aria-label={`พิมพ์ ${o.id}`}
                          onClick={() => notify(`พิมพ์ใบปะหน้า ${o.id}`)}
                        >
                          <Printer />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {visible.length === 0 && (
              <div className="om-empty">ไม่พบคำสั่งซื้อที่ตรงกับตัวกรอง</div>
            )}
          </div>
          <div className="pm-pagination">
            <span>แสดง 1 - {visible.length} จาก 1,248 รายการ</span>
            <label>
              แสดง{" "}
              <select aria-label="จำนวนรายการ">
                <option>8</option>
                <option>16</option>
                <option>24</option>
              </select>{" "}
              รายการ
            </label>
            <nav>
              <button aria-label="หน้าแรก">«</button>
              <button aria-label="ก่อนหน้า">
                <ChevronLeft />
              </button>
              <button className="active">1</button>
              <button>2</button>
              <button>3</button>
              <button>4</button>
              <button>5</button>
              <span>...</span>
              <button>156</button>
              <button aria-label="ถัดไป">
                <ChevronRight />
              </button>
            </nav>
          </div>
        </section>
      </section>
      {notice && (
        <div className="merchant-toast" role="status">
          {notice}
        </div>
      )}
    </main>
  );
}
