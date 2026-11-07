import { useMemo, useState } from "react";
import {
  Baby,
  ChevronDown,
  CircleEllipsis,
  CircleUserRound,
  Gift,
  Grid2X2,
  Heart,
  House,
  Leaf,
  List,
  LogOut,
  Menu,
  Search,
  Shirt,
  ShoppingCart,
  Sofa,
  Sparkles,
  Store,
  Utensils,
  Wine,
} from "lucide-react";
import { useAuth } from "../contexts/AuthContext";
import { accountKind } from "../services/permissions";
import thaiBrandReference from "../assets/images/references/storefront/thai-brand.png";
import "../styles/recommended-products.css";
import "../styles/best-seller.css";
import "../styles/thai-brands.css";
const popularBrands = [
  "ชาตรามือ",
  "ดอยคำ",
  "ภูฟ้า",
  "สมุนไพร",
  "นารายา",
  "ของดีเมืองเหนือ",
  "บ้านบางเขน",
  "ข้าวไทย",
  "ภูมิใจไทย",
  "อีกหลายแบรนด์",
];
const categories = [
  [Sparkles, "ทั้งหมด"],
  [Utensils, "อาหารและเครื่องดื่ม"],
  [Wine, "ความงามและสุขภาพ"],
  [Shirt, "แฟชั่นและเครื่องแต่งกาย"],
  [Sofa, "บ้านและไลฟ์สไตล์"],
  [House, "ของใช้ในครัวเรือน"],
  [Baby, "แม่และเด็ก"],
  [Gift, "ของขวัญและงานฝีมือ"],
  [Leaf, "สมุนไพรและออร์แกนิก"],
  [Store, "สินค้า OTOP"],
  [CircleEllipsis, "อื่นๆ"],
] as const;
const products = [
  [
    "ชาไทย สูตรต้นตำรับ\nตรามาตรฐานเมือง",
    "ชาตรามือ",
    120,
    140,
    14,
    "4.9",
    "1.2K",
    286,
    591,
    "แบรนด์ไทย",
    "red",
  ],
  [
    "กล้วยอบน้ำผึ้ง\nจากกล้วยหอมทอง",
    "บ้านธารน้ำไทย",
    89,
    120,
    26,
    "4.8",
    "856",
    489,
    591,
    "OTOP",
    "green",
  ],
  [
    "เสื้อผ้าฝ้ายทอมือ\nสไตล์ไทยร่วมสมัย",
    "ภูฟ้าผ้าฝ้าย",
    590,
    790,
    25,
    "4.7",
    "320",
    691,
    591,
    "แบรนด์ไทย",
    "red",
  ],
  [
    "ยาหม่องสมุนไพรไทย\nสูตรเย็น",
    "ภูมิใจไทย",
    129,
    180,
    28,
    "4.8",
    "642",
    895,
    591,
    "แบรนด์ไทย",
    "red",
  ],
  [
    "กระเป๋าผ้าทอมือ\nลายไทย",
    "Lanna Craft",
    350,
    490,
    29,
    "4.9",
    "210",
    1100,
    591,
    "ใหม่",
    "orange",
  ],
  [
    "ข้าวหอมมะลิ 100%\nจากเกษตรกรไทย",
    "ข้าวไทย",
    259,
    320,
    19,
    "4.8",
    "985",
    1305,
    591,
    "แบรนด์ไทย",
    "red",
  ],
] as const;

export default function ThaiBrandsPage() {
  const { user, loading, logout } = useAuth();
  const [activeCategory, setActiveCategory] = useState(0);
  const [activeTab, setActiveTab] = useState(0);
  const [cart, setCart] = useState(0);
  const [notice, setNotice] = useState("");
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState("ยอดนิยม");
  const [grid, setGrid] = useState(true);
  const [favorites, setFavorites] = useState<number[]>([]);
  const [accountOpen, setAccountOpen] = useState(false);
  const notify = (message: string) => {
    setNotice(message);
    window.setTimeout(() => setNotice(""), 1900);
  };
  const accountName = user
    ? `${user.firstName} ${user.lastName}`.trim() || user.email
    : "";
  const dashboard =
    accountKind(user) === "ADMIN"
      ? "/admin"
      : accountKind(user) === "MERCHANT"
        ? "/merchant/dashboard"
        : "/";
  const signOut = async () => {
    await logout();
    setAccountOpen(false);
    notify("ออกจากระบบแล้ว");
  };
  const visibleProducts = useMemo(
    () =>
      sort === "ราคาต่ำสุด"
        ? [...products].sort((a, b) => Number(a[2]) - Number(b[2]))
        : products,
    [sort],
  );
  return (
    <main className="thai-brand-page best-seller-page recommend-page">

      <section className="thai-hero">
        <img
          src={thaiBrandReference}
          alt="แบรนด์ไทย ของดีไทยสู่ทุกบ้าน สนับสนุนผู้ประกอบการไทย"
        />
      </section>
      <p className="thai-breadcrumb recommend-width">
        <a href="/">หน้าแรก</a>
        <span>›</span>แบรนด์ไทย
      </p>
      <section className="thai-content recommend-width">
        <aside className="thai-filters">
          <h2>ค้นหาแบรนด์ไทย</h2>
          <div className="thai-search">
            <Search />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="ค้นหาแบรนด์ไทย..."
            />
          </div>
          <h3>หมวดหมู่สินค้า</h3>
          {categories
            .filter(([, label]) => String(label).includes(query))
            .map(([Icon, label], i) => (
              <button
                className={activeCategory === i ? "active" : ""}
                key={label}
                onClick={() => setActiveCategory(i)}
              >
                <Icon />
                {label}
              </button>
            ))}
          <div className="thai-merchant">
            <span>🏪</span>
            <h3>
              เป็นส่วนหนึ่ง
              <br />
              ในการสนับสนุนแบรนด์ไทย
            </h3>
            <a href="/merchant/register">สมัครเปิดร้านค้า →</a>
          </div>
        </aside>
        <div className="thai-main">
          <div className="thai-section-head">
            <h1>แบรนด์ไทยยอดนิยม</h1>
            <button onClick={() => notify("แสดงแบรนด์ไทยทั้งหมด")}>
              ◉ ดูทั้งหมด →
            </button>
          </div>
          <div className="thai-brand-strip">
            <img src={thaiBrandReference} alt="แบรนด์ไทยยอดนิยม" />
            <div>
              {popularBrands.map((x) => (
                <button
                  key={x}
                  onClick={() => notify(`เลือกแบรนด์ ${x}`)}
                  aria-label={x}
                />
              ))}
            </div>
          </div>
          <div className="thai-product-head">
            <h2>สินค้าแบรนด์ไทย</h2>
            <div>
              <span>เรียงตาม</span>
              <select
                value={sort}
                onChange={(e) => setSort(e.target.value)}
                aria-label="เรียงสินค้า"
              >
                <option>ยอดนิยม</option>
                <option>ราคาต่ำสุด</option>
                <option>สินค้าใหม่</option>
              </select>
              <button
                className={grid ? "active" : ""}
                onClick={() => setGrid(true)}
                aria-label="มุมมองตาราง"
              >
                <Grid2X2 />
              </button>
              <button
                className={!grid ? "active" : ""}
                onClick={() => setGrid(false)}
                aria-label="มุมมองรายการ"
              >
                <List />
              </button>
            </div>
          </div>
          <div className="thai-tabs">
            {[
              "ทั้งหมด",
              "สินค้าแนะนำ",
              "สินค้าขายดี",
              "สินค้าใหม่",
              "ราคาพิเศษ",
            ].map((x, i) => (
              <button
                className={activeTab === i ? "active" : ""}
                key={x}
                onClick={() => setActiveTab(i)}
              >
                {x}
              </button>
            ))}
          </div>
          <div className={`thai-product-grid ${grid ? "" : "list-view"}`}>
            {visibleProducts.map((p, index) => (
              <article className="thai-product" key={String(p[0])}>
                <div className="thai-photo">
                  <div
                    className="thai-sprite"
                    style={
                      {
                        "--crop-x": `${p[7]}`,
                        "--crop-y": `${p[8]}`,
                      } as React.CSSProperties
                    }
                  >
                    <img src={thaiBrandReference} alt="" />
                  </div>
                  <span className={`thai-tag ${p[10]}`}>{p[9]}</span>
                  <button
                    className={favorites.includes(index) ? "liked" : ""}
                    onClick={() =>
                      setFavorites((v) =>
                        v.includes(index)
                          ? v.filter((x) => x !== index)
                          : [...v, index],
                      )
                    }
                    aria-label="รายการโปรด"
                  >
                    <Heart />
                  </button>
                </div>
                <div className="thai-info">
                  <h2>
                    {String(p[0])
                      .split("\n")
                      .map((line, i) => (
                        <span key={i}>{line}</span>
                      ))}
                  </h2>
                  <p className="thai-shop">
                    <Store /> {p[1]}
                  </p>
                  <p className="thai-price">
                    <b>฿{Number(p[2]).toLocaleString()}</b>
                    <del>฿{Number(p[3]).toLocaleString()}</del>
                    <em>-{p[4]}%</em>
                  </p>
                  <p className="thai-meta">
                    <b>★ {p[5]}</b> ({p[6]})
                  </p>
                  <button
                    onClick={() => {
                      setCart((c) => c + 1);
                      notify("เพิ่มสินค้าลงตะกร้าแล้ว");
                    }}
                  >
                    <ShoppingCart />
                    เพิ่มลงตะกร้า
                  </button>
                </div>
              </article>
            ))}
          </div>
          <div className="thai-banners">
            <button
              onClick={() => notify("เลือกซื้อสินค้าเกษตรไทย")}
              aria-label="เลือกซื้อสินค้าเกษตรไทย"
            >
              <span className="thai-banner-sprite banner-1">
                <img
                  src={thaiBrandReference}
                  alt="จากภูมิปัญญาไทย สู่สินค้าคุณภาพ"
                />
              </span>
            </button>
            <button
              onClick={() => notify("ชมสินค้าหัตถกรรม")}
              aria-label="ชมสินค้าหัตถกรรม"
            >
              <span className="thai-banner-sprite banner-2">
                <img src={thaiBrandReference} alt="หัตถกรรมไทย สืบสานคุณค่า" />
              </span>
            </button>
            <button
              onClick={() => notify("ร่วมสนับสนุนแบรนด์ไทย")}
              aria-label="ร่วมสนับสนุนแบรนด์ไทย"
            >
              <span className="thai-banner-sprite banner-3">
                <img src={thaiBrandReference} alt="ไทยซื้อไทย ช่วยคนไทย" />
              </span>
            </button>
          </div>
        </div>
      </section>
      {notice && (
        <div className="recommend-toast" role="status">
          {notice}
        </div>
      )}
    </main>
  );
}
