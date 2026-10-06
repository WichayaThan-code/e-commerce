import { useState } from "react";
import {
  BarChart3,
  CalendarDays,
  ChevronDown,
  CircleUserRound,
  Coffee,
  Eye,
  Grid2X2,
  Lightbulb,
  LogOut,
  Mail,
  Megaphone,
  Menu,
  MessageSquare,
  Search,
  ShoppingBag,
  ShoppingCart,
  Store,
} from "lucide-react";
import { useAuth } from "../contexts/AuthContext";
import blogReference from "../assets/images/references/storefront/blog.png";
import "../styles/recommended-products.css";
import "../styles/best-seller.css";
import "../styles/blog.css";

const categories = [
  [Grid2X2, "ไอเดียดี"],
  [Lightbulb, "ไอเดียธุรกิจ"],
  [BarChart3, "เทคนิคการขาย"],
  [Megaphone, "การตลาดออนไลน์"],
  [Store, "เรื่องราวร้านค้า"],
  [Coffee, "ไลฟ์สไตล์"],
  [CalendarDays, "ข่าวสารและกิจกรรม"],
] as const;
const articles = [
  {
    tag: "แนะนำ",
    kind: "ไอเดียธุรกิจ",
    title: "เริ่มต้นขายของออนไลน์อย่างไร\nให้ปังในปี 2026",
    summary:
      "รวมขั้นตอนการเริ่มต้น เทคนิคการเลือกสินค้า\nและช่องทางการขาย สำหรับมือใหม่",
    date: "12 ก.ย. 2026",
    views: "5.2K",
    comments: "24",
    x: 34,
    y: 530,
    w: 352,
    h: 178,
    large: true,
  },
  {
    tag: "เทคนิคการขาย",
    kind: "",
    title: "5 เทคนิคถ่ายภาพสินค้าด้วยมือถือ\nให้ดูน่าสนใจและขายได้จริง",
    date: "10 ก.ย. 2026",
    views: "3.8K",
    comments: "18",
    x: 403,
    y: 530,
    w: 232,
    h: 178,
  },
  {
    tag: "การตลาดออนไลน์",
    kind: "",
    title: "ใช้โซเชียลมีเดียอย่างไร\nให้ยอดขายโตแบบก้าวกระโดด",
    date: "8 ก.ย. 2026",
    views: "4.1K",
    comments: "22",
    x: 652,
    y: 530,
    w: 231,
    h: 178,
  },
  {
    tag: "เรื่องราวร้านค้า",
    kind: "",
    title: "จากครัวเล็ก ๆ สู่แบรนด์ข้าวแต๋น\nที่ดังไกลทั่วไทย",
    date: "6 ก.ย. 2026",
    views: "6.3K",
    comments: "40",
    x: 34,
    y: 889,
    w: 270,
    h: 134,
  },
  {
    tag: "ไลฟ์สไตล์",
    kind: "",
    title: "10 ของขวัญไทย ถูกใจทั้งผู้ให้\nและผู้รับ ในทุกโอกาส",
    date: "5 ก.ย. 2026",
    views: "4.9K",
    comments: "16",
    x: 322,
    y: 889,
    w: 270,
    h: 134,
  },
  {
    tag: "ข่าวสารและกิจกรรม",
    kind: "",
    title: "ThaiShopFun Fair 2026\nรวมของดีไทย ไว้ที่เดียว",
    date: "1 ก.ย. 2026",
    views: "8.2K",
    comments: "28",
    x: 610,
    y: 889,
    w: 271,
    h: 134,
  },
];
const popular = [
  "10 สินค้าไทยที่ขายดี\nในปี 2026",
  "เทคนิคแพ็กสินค้า\nให้ลูกค้าประทับใจ",
  "สร้างแบรนด์เล็กให้ใหญ่\nด้วยเรื่องเล่า (Storytelling)",
  "ช่องทางขายออนไลน์\nที่เหมาะกับร้านค้าไทย",
  "เทรนด์ผู้บริโภคไทย\nที่น่าจับตามองในปีหน้า",
];

function Brand() {
  return (
    <a className="recommend-brand" href="/" aria-label="ThaiShopFun หน้าแรก">
      <span className="recommend-mark">
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

export default function BlogPage() {
  const { user, loading, logout } = useAuth();
  const [activeCategory, setActiveCategory] = useState(0);
  const [cart, setCart] = useState(0);
  const [notice, setNotice] = useState("");
  const [email, setEmail] = useState("");
  const [accountOpen, setAccountOpen] = useState(false);
  const notify = (message: string) => {
    setNotice(message);
    window.setTimeout(() => setNotice(""), 2000);
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
  const subscribe = (e: React.FormEvent) => {
    e.preventDefault();
    if (!/^\S+@\S+\.\S+$/.test(email)) {
      notify("กรุณากรอกอีเมลให้ถูกต้อง");
      return;
    }
    notify("สมัครรับข่าวสารเรียบร้อยแล้ว");
    setEmail("");
  };
  return (
    <main className="blog-page best-seller-page recommend-page">
      <header className="recommend-header">
        <div className="recommend-top blog-width">
          <Brand />
          <form
            onSubmit={(e) => {
              e.preventDefault();
              notify("กำลังค้นหาบทความ");
            }}
          >
            <input
              aria-label="ค้นหาบทความ"
              placeholder="ค้นหาบทความ เช่น เทคนิคขายออนไลน์, ไอเดียธุรกิจ..."
            />
            <button aria-label="ค้นหา">
              <Search />
            </button>
          </form>
          <div className="recommend-account">
            {loading ? (
              <span className="recommend-loading" />
            ) : user ? (
              <div className="recommend-user">
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
                <a className="recommend-signup" href="/signup">
                  สมัครสมาชิก
                </a>
              </>
            )}
            <button
              className="recommend-cart"
              onClick={() => notify(`มีสินค้า ${cart} ชิ้นในตะกร้า`)}
            >
              <ShoppingCart />
              <i>{cart}</i>
            </button>
          </div>
        </div>
        <nav className="recommend-nav blog-width">
          <a href="/">
            <Menu />
            หมวดหมู่สินค้า
          </a>
          <a href="/deals">ดีลพิเศษ</a>
          <a href="/recommendations">สินค้าแนะนำ</a>
          <a href="/best-sellers">สินค้าขายดี</a>
          <a href="/thai-brands">แบรนด์ไทย</a>
          <a className="active" href="/articles">
            บทความ
          </a>
          <a href="/help">ช่วยเหลือ</a>
        </nav>
      </header>
      <section className="blog-hero blog-width">
        <img
          src={blogReference}
          alt="แรงบันดาลใจเพื่อคนไทย บทความที่คัดสรรเพื่อผู้ประกอบการไทย"
        />
      </section>
      <section className="blog-categories blog-width">
        {categories.map(([Icon, label], i) => (
          <button
            className={activeCategory === i ? "active" : ""}
            key={label}
            onClick={() => {
              setActiveCategory(i);
              notify(`เลือกหมวด ${label}`);
            }}
          >
            <Icon />
            {label}
          </button>
        ))}
      </section>
      <section className="blog-layout blog-width">
        <div className="blog-main">
          <div className="blog-title">
            <div>
              <h1>บทความแนะนำ</h1>
              <p>บทความยอดนิยม ที่ทีมงานคัดสรรมาให้คุณ</p>
            </div>
            <button onClick={() => notify("แสดงบทความทั้งหมด")}>
              ดูบทความทั้งหมด →
            </button>
          </div>
          <div className="article-grid">
            {[articles.slice(0, 3), articles.slice(3)].map((row, rowIndex) => (
              <div
                className={`article-row ${rowIndex === 0 ? "article-row-top" : "article-row-bottom"}`}
                key={rowIndex}
              >
                {row.map((a, rowItemIndex) => {
                  const i = rowIndex * 3 + rowItemIndex;
                  return (
                    <button
                      className={`article-card article-${i + 1} ${a.large ? "large" : ""}`}
                      key={a.title}
                      onClick={() =>
                        notify(`เปิดบทความ ${a.title.replace("\n", " ")}`)
                      }
                    >
                      <div className="article-photo">
                        <div
                          className="article-sprite"
                          style={
                            {
                              "--x": a.x,
                              "--y": a.y,
                              "--w": a.w,
                              "--h": a.h,
                            } as React.CSSProperties
                          }
                        >
                          <img src={blogReference} alt="" />
                        </div>
                        <span className={`article-tag tag-${i}`}>{a.tag}</span>
                      </div>
                      <div className="article-info">
                        {a.kind && <small>{a.kind}</small>}
                        <h2>
                          {a.title.split("\n").map((line, j) => (
                            <span key={j}>{line}</span>
                          ))}
                        </h2>
                        {a.summary && (
                          <p>
                            {a.summary.split("\n").map((line, j) => (
                              <span key={j}>{line}</span>
                            ))}
                          </p>
                        )}
                        <footer>
                          <span>
                            <CalendarDays />
                            {a.date}
                          </span>
                          <span>
                            <Eye />
                            {a.views}
                          </span>
                          <span>
                            <MessageSquare />
                            {a.comments}
                          </span>
                        </footer>
                      </div>
                    </button>
                  );
                })}
              </div>
            ))}
          </div>
        </div>
        <aside className="blog-sidebar">
          <section className="popular">
            <h2>🔥 บทความยอดนิยม</h2>
            {popular.map((title, i) => (
              <button
                key={title}
                onClick={() => notify(`เปิดบทความ ${title.replace("\n", " ")}`)}
              >
                <strong>{i + 1}</strong>
                <span className={`popular-photo popular-${i}`}>
                  <img src={blogReference} alt="" />
                </span>
                <span>
                  <b>
                    {title.split("\n").map((line, j) => (
                      <i key={j}>{line}</i>
                    ))}
                  </b>
                  <small>
                    <Eye />
                    {["12.5K", "9.8K", "8.7K", "7.9K", "6.1K"][i]}
                  </small>
                </span>
              </button>
            ))}
          </section>
          <form className="newsletter" onSubmit={subscribe}>
            <h2>
              <Mail /> รับบทความดี ๆ ก่อนใคร
            </h2>
            <p>
              อัปเดตไอเดียธุรกิจ เคล็ดลับการขาย
              <br />
              และเรื่องราวสร้างแรงบันดาลใจ ส่งตรงถึงคุณ
            </p>
            <input
              aria-label="อีเมลรับข่าวสาร"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="กรอกอีเมลของคุณ"
            />
            <button>สมัครรับข่าวสาร</button>
          </form>
        </aside>
      </section>
      <button
        className="blog-bottom blog-width"
        onClick={() => notify("เปิดเรื่องราวดี ๆ จากคนไทย")}
        aria-label="เรื่องราวดี ๆ จากคนไทย"
      >
        <span>
          <img
            src={blogReference}
            alt="เรื่องราวดี ๆ จากคนไทย คือพลังที่ทำให้สังคมเติบโต"
          />
        </span>
      </button>
      {notice && (
        <div className="recommend-toast" role="status">
          {notice}
        </div>
      )}
    </main>
  );
}
