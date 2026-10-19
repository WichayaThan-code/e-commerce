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
  Crown,
  Download,
  Globe2,
  Headphones,
  Heart,
  Home,
  Mail,
  MapPin,
  MessageCircle,
  Pencil,
  Phone,
  Search,
  Settings,
  ShoppingBag,
  ShoppingCart,
  Store,
  TicketPercent,
  Truck,
  UserPlus,
  UsersRound,
  WalletCards,
} from "lucide-react";
import MerchantBell from "../components/merchant/MerchantBell";
import MerchantTopbarSearch from "../components/merchant/MerchantTopbarSearch";
import LanguageSwitcher from "../components/LanguageSwitcher";
import MerchantShopMenu from "../components/MerchantShopMenu";
import { useAuth } from "../contexts/AuthContext";
import { hasPlatformAccess, hasMerchantAccess } from "../services/permissions";
import { usePermissions } from "../hooks/usePermissions";
import "../styles/merchant-dashboard.css";
import "../styles/product-management.css";
import "../styles/customer-management.css";

type Customer = {
  name: string;
  email: string;
  channel: string;
  orders: number;
  spent: number;
  group: "VIP" | "ลูกค้าประจำ" | "ทั่วไป";
  status: "ใช้งาน" | "ไม่ใช้งาน";
  joined: string;
  phone: string;
  address: string;
  initial: string;
  color: string;
};
const customers: Customer[] = [
  {
    name: "สมชาย ใจดี",
    email: "somchai88@gmail.com",
    channel: "เว็บไซต์",
    orders: 12,
    spent: 24560,
    group: "VIP",
    status: "ใช้งาน",
    joined: "12 ก.ย. 2026\n14:32",
    phone: "081-234-5678",
    address: "กรุงเทพมหานคร, ไทย",
    initial: "ส",
    color: "#e3f0ff",
  },
  {
    name: "ณัฐชา วงศ์ศิริ",
    email: "natcha@gmail.com",
    channel: "LINE OA",
    orders: 5,
    spent: 8750,
    group: "ลูกค้าประจำ",
    status: "ใช้งาน",
    joined: "12 ก.ย. 2026\n13:18",
    phone: "089-245-6721",
    address: "นนทบุรี, ไทย",
    initial: "ณ",
    color: "#eaf7f0",
  },
  {
    name: "กฤษฎา มานะ",
    email: "kritsada@gmail.com",
    channel: "Facebook",
    orders: 3,
    spent: 2480,
    group: "ทั่วไป",
    status: "ใช้งาน",
    joined: "11 ก.ย. 2026\n18:45",
    phone: "086-552-1108",
    address: "เชียงใหม่, ไทย",
    initial: "ก",
    color: "#dceaff",
  },
  {
    name: "วิภา สุดสวย",
    email: "wipa@gmail.com",
    channel: "TikTok Shop",
    orders: 8,
    spent: 6290,
    group: "ลูกค้าประจำ",
    status: "ใช้งาน",
    joined: "10 ก.ย. 2026\n16:12",
    phone: "092-345-8890",
    address: "ชลบุรี, ไทย",
    initial: "ว",
    color: "#ffe8ee",
  },
  {
    name: "ปิยะพงษ์ ดีมาก",
    email: "piyapong@gmail.com",
    channel: "Shopee",
    orders: 2,
    spent: 1080,
    group: "ทั่วไป",
    status: "ใช้งาน",
    joined: "10 ก.ย. 2026\n11:03",
    phone: "085-114-7620",
    address: "นครราชสีมา, ไทย",
    initial: "ป",
    color: "#fff0dc",
  },
  {
    name: "อรทัย มีสุข",
    email: "orathai@gmail.com",
    channel: "Lazada",
    orders: 6,
    spent: 4350,
    group: "ลูกค้าประจำ",
    status: "ใช้งาน",
    joined: "9 ก.ย. 2026\n09:27",
    phone: "098-772-3401",
    address: "ขอนแก่น, ไทย",
    initial: "อ",
    color: "#ece6ff",
  },
  {
    name: "Chanatip K.",
    email: "chanatip@gmail.com",
    channel: "เว็บไซต์",
    orders: 1,
    spent: 890,
    group: "ทั่วไป",
    status: "ไม่ใช้งาน",
    joined: "8 ก.ย. 2026\n20:15",
    phone: "094-368-2110",
    address: "ภูเก็ต, ไทย",
    initial: "C",
    color: "#9c78eb",
  },
  {
    name: "สุนิสา ใจงาม",
    email: "sunisa@gmail.com",
    channel: "LINE OA",
    orders: 4,
    spent: 3120,
    group: "ทั่วไป",
    status: "ใช้งาน",
    joined: "8 ก.ย. 2026\n14:08",
    phone: "081-995-4862",
    address: "สมุทรปราการ, ไทย",
    initial: "ส",
    color: "#f4d5bb",
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
function Channel({ name }: { name: string }) {
  return (
    <span className={`cm-channel c-${name.replace(" ", "")}`}>
      {name === "เว็บไซต์" ? (
        <Globe2 />
      ) : name === "LINE OA" ? (
        <MessageCircle />
      ) : name === "Facebook" ? (
        "f"
      ) : name === "TikTok Shop" ? (
        "♪"
      ) : name === "Shopee" ? (
        "S"
      ) : (
        "L"
      )}
      <i>{name}</i>
    </span>
  );
}

export default function CustomerManagementPage() {
  const { user, loading } = useAuth();
  const { canInShop, canSeeMenuItem, deniedTitle } = usePermissions();
  const canCreate = canInShop("CUSTOMER_CREATE");
  const canUpdate = canInShop("CUSTOMER_UPDATE");
  const canReadPii = canInShop("CUSTOMER_PII_READ");
  const canReadOrders = canInShop("ORDER_READ");
  const [query, setQuery] = useState("");
  const [channel, setChannel] = useState("ทั้งหมด");
  const [status, setStatus] = useState("ทั้งหมด");
  const [group, setGroup] = useState("ทั้งหมด");
  const [selected, setSelected] = useState(customers[0]);
  const [checked, setChecked] = useState<string[]>([]);
  const [notice, setNotice] = useState("");
  if (loading)
    return <main className="auth-loading">กำลังตรวจสอบสิทธิ์ร้านค้า...</main>;
  if (!user) {
    window.location.replace("/login?next=/merchant/customers");
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
  const visible = customers.filter(
    (c) =>
      `${c.name} ${c.email} ${c.phone}`
        .toLowerCase()
        .includes(query.trim().toLowerCase()) &&
      (channel === "ทั้งหมด" || c.channel === channel) &&
      (status === "ทั้งหมด" || c.status === status) &&
      (group === "ทั้งหมด" || c.group === group),
  );
  const reset = () => {
    setQuery("");
    setChannel("ทั้งหมด");
    setStatus("ทั้งหมด");
    setGroup("ทั้งหมด");
  };
  return (
    <main className="merchant-shell cm-shell">
      <header className="merchant-topbar">
        <Brand />
        <MerchantTopbarSearch />
        <LanguageSwitcher /> <MerchantBell />
        <MerchantShopMenu />
      </header>
      <aside className="merchant-sidebar pm-sidebar cm-sidebar">
        <nav>
          {menu.filter(canSeeMenuItem).map(([Icon, label, href, badge]) => (
            <a
              className={label === "ลูกค้า" ? "active" : ""}
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
      <section className="pm-content cm-content">
        <header className="pm-heading cm-heading">
          <div>
            <h1>
              <UsersRound />
              ลูกค้า
            </h1>
            <p>ดูแลลูกค้าของคุณ สร้างความสัมพันธ์ และเพิ่มยอดขายอย่างยั่งยืน</p>
          </div>
          <div className="pm-actions">
            <button disabled={!canCreate} title={deniedTitle(canCreate)}
              className="primary"
              onClick={() => notify("เปิดแบบฟอร์มเพิ่มลูกค้า")}
            >
              <UserPlus />
              เพิ่มลูกค้า <ChevronDown />
            </button>
            <button disabled={!canReadPii} title={deniedTitle(canReadPii)} onClick={() => notify("ส่งออกข้อมูลลูกค้าเป็น Excel แล้ว")}>
              <Download />
              ส่งออก (Excel)
            </button>
          </div>
        </header>
        <section className="pm-kpis cm-kpis">
          <article className="all">
            <span>
              <UsersRound />
            </span>
            <div>
              <small>ลูกค้าทั้งหมด</small>
              <b>
                3,892<i>↑ 15%</i>
              </b>
              <em>จากเดือนก่อน</em>
            </div>
          </article>
          <article className="ready">
            <span>
              <UsersRound />
            </span>
            <div>
              <small>ลูกค้าใหม่</small>
              <b>
                248<i>↑ 27%</i>
              </b>
              <em>จากเดือนก่อน</em>
            </div>
          </article>
          <article className="empty">
            <span>
              <Heart />
            </span>
            <div>
              <small>ลูกค้าประจำ</small>
              <b>
                1,235<i>↑ 12%</i>
              </b>
              <em>ซื้อซ้ำ 2 ครั้งขึ้นไป</em>
            </div>
          </article>
          <article className="vip">
            <span>
              <ShoppingCart />
            </span>
            <div>
              <small>ยอดซื้อเฉลี่ยต่อคน</small>
              <b>
                ฿ 850<i>↑ 10%</i>
              </b>
              <em>จากเดือนก่อน</em>
            </div>
          </article>
        </section>
        <div className="cm-grid">
          <section className="cm-list">
            <div className="cm-tabs">
              <button className="active">ทั้งหมด (3,892)</button>
              <button>ลูกค้าใหม่ (248)</button>
              <button>ลูกค้าประจำ (1,235)</button>
              <button>ลูกค้า VIP (86)</button>
            </div>
            <form
              className="cm-filters"
              onSubmit={(e) => {
                e.preventDefault();
                notify(`พบลูกค้า ${visible.length} รายการ`);
              }}
            >
              <label className="cm-search">
                <Search />
                <input
                  aria-label="ค้นหาลูกค้า"
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder="ค้นหาชื่อลูกค้า, เบอร์โทร, อีเมล..."
                />
              </label>
              <label>
                ช่องทาง
                <select
                  aria-label="ช่องทาง"
                  value={channel}
                  onChange={(e) => setChannel(e.target.value)}
                >
                  <option>ทั้งหมด</option>
                  {[...new Set(customers.map((c) => c.channel))].map((x) => (
                    <option key={x}>{x}</option>
                  ))}
                </select>
              </label>
              <label>
                สถานะ
                <select
                  aria-label="สถานะลูกค้า"
                  value={status}
                  onChange={(e) => setStatus(e.target.value)}
                >
                  <option>ทั้งหมด</option>
                  <option>ใช้งาน</option>
                  <option>ไม่ใช้งาน</option>
                </select>
              </label>
              <label>
                กลุ่มลูกค้า
                <select
                  aria-label="กลุ่มลูกค้า"
                  value={group}
                  onChange={(e) => setGroup(e.target.value)}
                >
                  <option>ทั้งหมด</option>
                  <option>VIP</option>
                  <option>ลูกค้าประจำ</option>
                  <option>ทั่วไป</option>
                </select>
              </label>
              <label>
                ช่วงวันที่สมัคร
                <button type="button">
                  <CalendarDays />1 ก.ย. 2026 - 12 ก.ย. 2026
                  <ChevronDown />
                </button>
              </label>
              <button className="cm-reset" type="button" onClick={reset}>
                รีเซ็ต
              </button>
            </form>
            <div className="cm-table-wrap">
              <table>
                <thead>
                  <tr>
                    <th>
                      <input
                        aria-label="เลือกลูกค้าทั้งหมด"
                        type="checkbox"
                        checked={
                          visible.length > 0 &&
                          checked.length === visible.length
                        }
                        onChange={() =>
                          setChecked(
                            checked.length === visible.length
                              ? []
                              : visible.map((c) => c.email),
                          )
                        }
                      />
                    </th>
                    <th>#</th>
                    <th>ชื่อ–นามสกุล</th>
                    <th>ช่องทาง</th>
                    <th>คำสั่งซื้อ</th>
                    <th>ยอดใช้จ่ายรวม</th>
                    <th>กลุ่มลูกค้า</th>
                    <th>สถานะ</th>
                    <th>วันที่สมัคร</th>
                  </tr>
                </thead>
                <tbody>
                  {visible.map((c, i) => (
                    <tr
                      className={selected.email === c.email ? "selected" : ""}
                      key={c.email}
                      onClick={() => setSelected(c)}
                    >
                      <td>
                        <input
                          aria-label={`เลือก ${c.name}`}
                          type="checkbox"
                          checked={checked.includes(c.email)}
                          onClick={(e) => e.stopPropagation()}
                          onChange={() =>
                            setChecked((xs) =>
                              xs.includes(c.email)
                                ? xs.filter((x) => x !== c.email)
                                : [...xs, c.email],
                            )
                          }
                        />
                      </td>
                      <td>{i + 1}</td>
                      <td>
                        <div className="cm-person">
                          <span style={{ background: c.color }}>
                            {c.initial}
                          </span>
                          <p>
                            <b>{c.name}</b>
                            <small>{c.email}</small>
                          </p>
                        </div>
                      </td>
                      <td>
                        <Channel name={c.channel} />
                      </td>
                      <td>{c.orders}</td>
                      <td>
                        <b>฿ {c.spent.toLocaleString()}</b>
                      </td>
                      <td>
                        <span
                          className={`cm-group ${c.group === "VIP" ? "vip" : c.group === "ลูกค้าประจำ" ? "regular" : "normal"}`}
                        >
                          {c.group === "VIP" && "♛ "}
                          {c.group}
                        </span>
                      </td>
                      <td>
                        <span
                          className={`cm-status ${c.status === "ใช้งาน" ? "on" : "off"}`}
                        >
                          {c.status}
                        </span>
                      </td>
                      <td>
                        {c.joined.split("\n").map((x) => (
                          <span key={x}>{x}</span>
                        ))}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              {visible.length === 0 && (
                <div className="cm-empty">ไม่พบลูกค้าที่ตรงกับตัวกรอง</div>
              )}
            </div>
            <div className="cm-pagination">
              <span>แสดง 1 - {visible.length} จาก 3,892 รายการ</span>
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
                <button aria-label="ก่อนหน้า">
                  <ChevronLeft />
                </button>
                <button className="active">1</button>
                <button>2</button>
                <button>3</button>
                <button>4</button>
                <button>5</button>
                <span>...</span>
                <button>487</button>
                <button aria-label="ถัดไป">
                  <ChevronRight />
                </button>
              </nav>
            </div>
          </section>
          <aside className="cm-detail">
            <header>
              <div className="cm-avatar" style={{ background: selected.color }}>
                {selected.initial}
              </div>
              <div>
                <h2>{selected.name}</h2>
                <span>♛ VIP</span>
              </div>
              <button disabled={!canUpdate} title={deniedTitle(canUpdate)} onClick={() => notify(`แก้ไขข้อมูล ${selected.name}`)}>
                <Pencil />
                แก้ไข
              </button>
            </header>
            <ul className="cm-contact">
              <li>
                <Mail />
                {selected.email}
              </li>
              <li>
                <Phone />
                {selected.phone}
              </li>
              <li>
                <MapPin />
                {selected.address}
              </li>
              <li>
                <CalendarDays />
                เป็นลูกค้ามาตั้งแต่ 12 ก.ย. 2026 (10 วัน)
              </li>
            </ul>
            <div className="cm-customer-kpis">
              <p>
                <ShoppingCart />
                <b>{selected.orders}</b>
                <small>คำสั่งซื้อ</small>
              </p>
              <p>
                ●<b>฿ {selected.spent.toLocaleString()}</b>
                <small>ยอดใช้จ่ายรวม</small>
              </p>
              <p>
                <Heart />
                <b>
                  ฿{" "}
                  {Math.round(
                    selected.spent / Math.max(selected.orders, 1),
                  ).toLocaleString()}
                </b>
                <small>ยอดเฉลี่ยต่อครั้ง</small>
              </p>
            </div>
            <section>
              <h3>ช่องทางที่ซื้อ</h3>
              {[
                ["เว็บไซต์", 50],
                ["LINE OA", 25],
                ["Facebook", 17],
                ["TikTok Shop", 8],
              ].map(([name, value]) => (
                <div className="cm-bar" key={name}>
                  <Channel name={String(name)} />
                  <i>
                    <b style={{ width: `${Number(value) * 2}%` }} />
                  </i>
                  <span>{value}%</span>
                </div>
              ))}
            </section>
            <section className="cm-note">
              <h3>
                หมายเหตุ{" "}
                <button disabled={!canUpdate} title={deniedTitle(canUpdate)} onClick={() => notify("เปิดแก้ไขหมายเหตุ")}>
                  <Pencil />
                  แก้ไข
                </button>
              </h3>
              <p>
                ลูกค้า VIP ซื้อเป็นประจำ สนใจสินค้ากลุ่มสุขภาพ
                <br />
                ชอบรับโปรโมชั่นผ่านทาง LINE
              </p>
            </section>
            <div className="cm-detail-actions">
              <button disabled={!canReadPii} title={deniedTitle(canReadPii)}
                onClick={() => notify(`กำลังส่งข้อความถึง ${selected.name}`)}
              >
                <Mail />
                ส่งข้อความ
              </button>
              <button disabled={!canReadOrders} title={deniedTitle(canReadOrders)}
                className="primary"
                onClick={() =>
                  notify(`เปิดประวัติการสั่งซื้อของ ${selected.name}`)
                }
              >
                <ShoppingBag />
                ดูประวัติการสั่งซื้อ
              </button>
            </div>
          </aside>
        </div>
      </section>
      {notice && (
        <div className="merchant-toast" role="status">
          {notice}
        </div>
      )}
    </main>
  );
}
