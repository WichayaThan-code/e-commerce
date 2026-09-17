import { useEffect, useState } from "react";
import {
  ArrowLeft,
  ArrowRight,
  BadgePercent,
  ChevronDown,
  CircleUserRound,
  Clock3,
  Flame,
  Gift,
  Heart,
  LogOut,
  Menu,
  Search,
  ShoppingBag,
  ShoppingCart,
  Sparkles,
  Store,
  Tag,
  TicketPercent,
  Zap,
} from "lucide-react";
import { useAuth } from "../contexts/AuthContext";
import "../styles/deals.css";
import "../styles/deal-hero-fix.css";

const tabs = [
  [Flame, "ดีลวันนี้"],
  [ShoppingBag, "ดีลทุกสัปดาห์"],
  [Zap, "Flash Sale"],
  [Tag, "ไม่เกิน 199.-"],
  [Gift, "ซื้อ 1 แถม 1"],
  [TicketPercent, "คูปองส่วนลด"],
  [BadgePercent, "สินค้าราคาพิเศษ"],
] as const;
const categories = [
  "ทั้งหมด",
  "แฟชั่น & เครื่องแต่งกาย",
  "ความงาม & สุขภาพ",
  "บ้าน & ไลฟ์สไตล์",
  "มือถือ & อิเล็กทรอนิกส์",
  "อาหาร & เครื่องดื่ม",
  "แม่และเด็ก",
  "กีฬา & กิจกรรมกลางแจ้ง",
  "ของขวัญ & ของชำร่วย",
  "สินค้าชุมชน OTOP",
  "สินค้าใหม่",
  "แบรนด์ไทย",
];
const dealProducts = [
  [
    "🥜",
    "ขนมเม็ดมะม่วงอบกรอบ รสต้นตำรับ 500 กรัม",
    199,
    399,
    50,
    "4.8",
    "1.2K",
  ],
  ["👕", "เสื้อยืดคอตตอน 100% ใส่สบาย มีหลายสี", 259, 399, 35, "4.7", "856"],
  ["🧴", "เซ็ตสกินแคร์บำรุงผิว สำหรับผิวแพ้ง่าย", 890, 1490, 40, "4.9", "320"],
  ["🔊", "ลำโพงบลูทูธพกพา เสียงดี เบสแน่น", 790, 1090, 28, "4.6", "412"],
  ["🍳", "กระทะเคลือบหินอ่อน ขนาด 28 ซม.", 690, 999, 30, "4.8", "231"],
  [
    "☕",
    "กาแฟดริปคั่วสด จากดอยเชียงใหม่ (10 ซอง)",
    199,
    399,
    50,
    "4.7",
    "1.1K",
  ],
  ["🎒", "กระเป๋าเป้กันน้ำ ดีไซน์มินิมอล", 890, 1190, 25, "4.8", "305"],
  ["🧸", "ชุดของเล่นเสริมพัฒนาการ สำหรับเด็ก 6M+", 399, 599, 33, "4.9", "642"],
];
const recommended = [
  ["🧴", "เซ็ตบำรุงผิวธรรมชาติ", "-20%"],
  ["👟", "รองเท้าผ้าใบหนังนุ่ม", "-45%"],
  ["🍜", "บะหมี่กึ่งสำเร็จรูปไทย", "-18%"],
  ["🍲", "หม้อสเตนเลสพร้อมฝา", "-32%"],
  ["🪴", "ต้นไม้ฟอกอากาศ", "-27%"],
];

function DealBrand() {
  return (
    <a className="deal-brand" href="/">
      <span className="deal-mark">
        <ShoppingBag />
        <i>✓</i>
      </span>
      <span>
        <b>
          ThaiShop<span>Fun</span>
        </b>
        <small>Shop Local · Support Thai · Smile Together</small>
      </span>
    </a>
  );
}

export default function DealsPage() {
  const { user, loading, logout } = useAuth();
  const [activeTab, setActiveTab] = useState(0);
  const [activeCategory, setActiveCategory] = useState(0);
  const [cart, setCart] = useState(0);
  const [notice, setNotice] = useState("");
  const [accountOpen, setAccountOpen] = useState(false);
  const [dealEndsAt] = useState(() => {
    const stored = Number(
      window.localStorage.getItem("thaishopfun-deal-ends-at"),
    );
    if (stored > Date.now()) return stored;
    const next = Date.now() + (5 * 60 * 60 + 32 * 60 + 18) * 1000;
    window.localStorage.setItem("thaishopfun-deal-ends-at", String(next));
    return next;
  });
  const [remaining, setRemaining] = useState(() =>
    Math.max(0, Math.floor((dealEndsAt - Date.now()) / 1000)),
  );
  useEffect(() => {
    const update = () =>
      setRemaining(Math.max(0, Math.floor((dealEndsAt - Date.now()) / 1000)));
    update();
    const timer = window.setInterval(update, 1000);
    return () => window.clearInterval(timer);
  }, [dealEndsAt]);
  const countdown = [
    Math.floor(remaining / 3600),
    Math.floor((remaining % 3600) / 60),
    remaining % 60,
  ].map((value) => String(value).padStart(2, "0"));
  const notify = (text: string) => {
    setNotice(text);
    window.setTimeout(() => setNotice(""), 1800);
  };
  const accountName = user
    ? `${user.firstName} ${user.lastName}`.trim() || user.email
    : "";
  const dashboard =
    user?.role === "ADMIN"
      ? "/admin"
      : user?.role === "MERCHANT"
        ? "/merchant/dashboard"
        : "/";
  const signOut = async () => {
    await logout();
    setAccountOpen(false);
    notify("ออกจากระบบแล้ว");
  };
  return (
    <main className="deals-page">
      <header className="deal-header">
        <div className="deal-top deal-width">
          <DealBrand />
          <form
            onSubmit={(e) => {
              e.preventDefault();
              notify("กำลังค้นหาสินค้า");
            }}
          >
            <input
              aria-label="ค้นหาสินค้า"
              placeholder="ค้นหาสินค้า แบรนด์ หรือร้านค้า..."
            />
            <button aria-label="ค้นหา">
              <Search />
            </button>
          </form>
          <div className="deal-account">
            {loading ? (
              <span className="deal-account-loading" />
            ) : user ? (
              <div className="deal-user-wrap">
                <button onClick={() => setAccountOpen((v) => !v)}>
                  <CircleUserRound />
                  <span>
                    <b>{accountName}</b>
                    <small>
                      {user.role === "CUSTOMER"
                        ? "ลูกค้า"
                        : user.role === "MERCHANT"
                          ? "เจ้าของร้าน"
                          : "ผู้ดูแลระบบ"}
                    </small>
                  </span>
                  <ChevronDown />
                </button>
                {accountOpen && (
                  <div>
                    <a href={dashboard}>บัญชีของฉัน</a>
                    <button onClick={() => void signOut()}>
                      <LogOut />
                      ออกจากระบบ
                    </button>
                  </div>
                )}
              </div>
            ) : (
              <>
                <a href="/login">
                  <CircleUserRound />
                  เข้าสู่ระบบ
                </a>
                <a className="signup" href="/signup">
                  สมัครสมาชิก
                </a>
              </>
            )}
            <button
              className="deal-cart"
              onClick={() => notify(`มีสินค้า ${cart} ชิ้นในตะกร้า`)}
            >
              <ShoppingCart />
              <i>{cart}</i>
            </button>
          </div>
        </div>
        <nav className="deal-nav deal-width">
          <a href="/">
            <Menu />
            หมวดหมู่สินค้า
          </a>
          <a className="active" href="/deals">
            ดีลพิเศษ
          </a>
          <a href="/recommendations">สินค้าแนะนำ</a>
          <a href="/#shops">ร้านค้าแนะนำ</a>
          <a href="/best-sellers">สินค้าขายดี</a>
          <a href="/thai-brands">แบรนด์ไทย</a>
          <a href="/articles">บทความ</a>
          <a href="/help">ช่วยเหลือ</a>
        </nav>
      </header>
      <div className="deal-width">
        <p className="breadcrumbs">
          <a href="/">หน้าแรก</a>
          <span>›</span>ดีลพิเศษ
        </p>
        <section className="deal-hero">
          <img
            src="/assets/images/deals/deal-reference.png"
            alt="ดีลพิเศษ ช้อปคุ้มกว่า ทุกวัน สินค้าคุณภาพจากร้านค้าไทย"
          />
        </section>
      </div>
      <section className="deal-tabs">
        <div className="deal-width">
          {tabs.map(([Icon, label], index) => (
            <button
              className={index === activeTab ? "active" : ""}
              key={label}
              onClick={() => {
                setActiveTab(index);
                notify(`เลือก ${label}`);
              }}
            >
              <span>
                <Icon />
              </span>
              {label}
            </button>
          ))}
        </div>
      </section>
      <section className="deal-catalog deal-width">
        <aside className="deal-filters">
          <div>
            <h3>หมวดหมู่ดีล</h3>
            {categories.map((item, index) => (
              <button
                className={index === activeCategory ? "active" : ""}
                key={item}
                onClick={() => setActiveCategory(index)}
              >
                <span>
                  {index === 0
                    ? "◉"
                    : ["♧", "♙", "⌂", "▯", "♨", "♧", "⌘", "□", "◎", "◴", "◉"][
                        index - 1
                      ]}
                </span>
                {item}
              </button>
            ))}
          </div>
          <div className="filter-box">
            <h3>ช่วงราคา</h3>
            {[
              "ต่ำกว่า 100 บาท",
              "100 - 300 บาท",
              "301 - 500 บาท",
              "501 - 1,000 บาท",
              "มากกว่า 1,000 บาท",
            ].map((x) => (
              <label key={x}>
                <input type="checkbox" />
                {x}
              </label>
            ))}
            <hr />
            <h3>ส่วนลด</h3>
            {["10% ขึ้นไป", "20% ขึ้นไป", "30% ขึ้นไป", "50% ขึ้นไป"].map(
              (x) => (
                <label key={x}>
                  <input type="checkbox" />
                  {x}
                </label>
              ),
            )}
          </div>
        </aside>
        <div className="deal-main">
          <div className="deal-heading">
            <div>
              <h1>ดีลวันนี้</h1>
              <p>ข้อเสนอพิเศษ คัดสรรมาเพื่อคุณเท่านั้น</p>
            </div>
            <div className="deal-countdown" role="timer" aria-live="off">
              <Clock3 />
              <b>{remaining > 0 ? "สิ้นสุดใน" : "หมดเวลา"}</b>
              {countdown.map((x, i) => (
                <span key={i}>
                  <strong>{x}</strong>
                  <small>{["ชั่วโมง", "นาที", "วินาที"][i]}</small>
                </span>
              ))}
            </div>
            <button>
              ดูทั้งหมด <ArrowRight />
            </button>
          </div>
          <div className="deal-grid">
            {dealProducts.map((p, index) => (
              <article className="deal-card" key={p[1]}>
                <span className="discount">-{p[4]}%</span>
                <button
                  className="deal-heart"
                  aria-label="เพิ่มรายการโปรด"
                  onClick={() => notify("เพิ่มในรายการโปรดแล้ว")}
                >
                  <Heart />
                </button>
                <div className={`deal-photo photo-${index}`}>
                  <span>{p[0]}</span>
                </div>
                <div className="deal-info">
                  <h2>{p[1]}</h2>
                  <p>
                    <b>฿{p[2].toLocaleString()}</b>
                    <del>฿{p[3].toLocaleString()}</del>
                  </p>
                  <small>
                    <i>★ {p[5]}</i> ({index * 23 + 98}) <em>ขายแล้ว {p[6]}</em>
                  </small>
                  <button
                    onClick={() => {
                      setCart((c) => c + 1);
                      notify("เพิ่มสินค้าลงตะกร้าแล้ว");
                    }}
                  >
                    <ShoppingCart />
                    หยิบใส่ตะกร้า
                  </button>
                </div>
              </article>
            ))}
          </div>
          <div className="deal-banners">
            <article>
              <div>
                <b>⚡ FLASH SALE</b>
                <h3>ลดแรง! เฉพาะช่วงเวลา</h3>
                <button onClick={() => notify("เปิด Flash Sale")}>
                  ช้อปเลย →
                </button>
              </div>
              <span>⏰</span>
              <strong>
                ลดสูงสุด
                <br />
                <em>70%</em>
              </strong>
            </article>
            <article>
              <div>
                <b>คูปองส่วนลด</b>
                <h3>
                  เก็บคูปองก่อนช้อป
                  <br />
                  ลดเพิ่มได้อีก
                </h3>
                <button onClick={() => notify("เก็บคูปองแล้ว")}>
                  เก็บคูปองเลย →
                </button>
              </div>
              <span>🎟️</span>
            </article>
          </div>
          <div className="recommended-title">
            <div>
              <h2>ดีลแนะนำสำหรับคุณ</h2>
              <p>สินค้าที่ตรงกับความสนใจของคุณ</p>
            </div>
            <div>
              <button aria-label="ก่อนหน้า">
                <ArrowLeft />
              </button>
              <button aria-label="ถัดไป">
                <ArrowRight />
              </button>
            </div>
          </div>
          <div className="recommended-deals">
            {recommended.map((p, index) => (
              <article key={p[1]}>
                <span className="discount">{p[2]}</span>
                <button className="deal-heart">
                  <Heart />
                </button>
                <div className={`rec-photo rec-${index}`}>{p[0]}</div>
                <h3>{p[1]}</h3>
              </article>
            ))}
          </div>
        </div>
      </section>
      {notice && (
        <div className="deal-toast" role="status">
          {notice}
        </div>
      )}
    </main>
  );
}
