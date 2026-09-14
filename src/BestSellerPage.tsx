import { useMemo, useState } from "react";
import {
  Baby,
  BarChart3,
  ChevronDown,
  CircleEllipsis,
  CircleUserRound,
  Dumbbell,
  Gift,
  Grid2X2,
  Heart,
  Leaf,
  List,
  LogOut,
  Menu,
  Search,
  Shirt,
  ShoppingBag,
  ShoppingCart,
  Sofa,
  Sparkles,
  Store,
  Utensils,
  Wine,
} from "lucide-react";
import { useAuth } from "./AuthContext";
import bestSellerReference from "../best_seller.png";
import "./recommended-products.css";
import "./best-seller.css";

const categories = [
  [Sparkles, "ทั้งหมด"],
  [Shirt, "แฟชั่น"],
  [Wine, "ความงาม"],
  [Sofa, "บ้านและไลฟ์สไตล์"],
  [ShoppingBag, "มือถือและอุปกรณ์"],
  [Utensils, "อาหารและเครื่องดื่ม"],
  [Dumbbell, "กีฬาและกิจกรรม"],
  [Baby, "แม่และเด็ก"],
  [Gift, "ของขวัญ"],
  [Leaf, "สินค้าเพื่อสิ่งแวดล้อม"],
  [CircleEllipsis, "อื่นๆ"],
] as const;
const productData = [
  [
    "ขนมเม็ดมะม่วงหิมพานต์ อบกรอบ\nรสต้นตำรับ 500 กรัม",
    "สวนไทยฟู้ดส์",
    199,
    320,
    38,
    "4.8",
    "1,256",
    "12.5K",
    297,
    545,
  ],
  [
    "เสื้อยืดคอตตอน 100%\nใส่สบาย ไม่หด ไม่ย้วย",
    "GoodWear Thailand",
    259,
    390,
    34,
    "4.7",
    "892",
    "9.8K",
    542,
    545,
  ],
  [
    "เซ็ตสกินแคร์บำรุงผิว\nเพื่อผิวกระจ่างใส",
    "Natura Official",
    890,
    1490,
    40,
    "4.9",
    "742",
    "9.1K",
    783,
    545,
  ],
  [
    "ต้นมอนสเตอร่า พร้อมกระถาง\nแต่งบ้านสวย ๆ",
    "Green Home",
    350,
    450,
    22,
    "4.8",
    "620",
    "8.6K",
    1027,
    545,
  ],
  [
    "กระเป๋าผ้าแคนวาส\nลายไทยสุดชิค",
    "ThaiCraft",
    290,
    350,
    17,
    "4.7",
    "518",
    "7.9K",
    1270,
    545,
  ],
  [
    "หูฟังบลูทูธไร้สาย เสียงคมชัด",
    "Soundmate",
    790,
    1090,
    28,
    "4.8",
    "480",
    "7.5K",
    297,
    890,
  ],
  [
    "กาแฟคั่วสดอาราบิก้า ดอยเชียงใหม่",
    "Doi Coffee",
    229,
    350,
    35,
    "4.9",
    "416",
    "7.1K",
    542,
    890,
  ],
  [
    "โซฟาผ้านุ่ม สไตล์มินิมอล",
    "Home Living",
    4590,
    5990,
    23,
    "4.7",
    "308",
    "6.8K",
    783,
    890,
  ],
  [
    "สมาร์ทวอทช์ จอคมชัด กันน้ำ",
    "Tech Thai",
    1290,
    1690,
    24,
    "4.8",
    "395",
    "6.4K",
    1027,
    890,
  ],
  [
    "ชุดขนมไทยของฝากพรีเมียม",
    "บ้านขนมไทย",
    490,
    650,
    25,
    "4.9",
    "522",
    "6.1K",
    1270,
    890,
  ],
] as const;

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

export default function BestSellerPage() {
  const { user, loading, logout } = useAuth();
  const [activeCategory, setActiveCategory] = useState(0);
  const [activeRank, setActiveRank] = useState(0);
  const [cart, setCart] = useState(0);
  const [notice, setNotice] = useState("");
  const [accountOpen, setAccountOpen] = useState(false);
  const [sort, setSort] = useState("ยอดขายสูงสุด");
  const [grid, setGrid] = useState(true);
  const [favorites, setFavorites] = useState<number[]>([]);
  const [minPrice, setMinPrice] = useState("");
  const [maxPrice, setMaxPrice] = useState("");
  const notify = (message: string) => {
    setNotice(message);
    window.setTimeout(() => setNotice(""), 1900);
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
  const products = useMemo(
    () =>
      sort === "ราคาต่ำสุด"
        ? [...productData].sort((a, b) => Number(a[2]) - Number(b[2]))
        : productData,
    [sort],
  );
  return (
    <main className="best-seller-page recommend-page">
      <header className="recommend-header">
        <div className="recommend-top recommend-width">
          <Brand />
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
        <nav className="recommend-nav recommend-width">
          <a href="/">
            <Menu />
            หมวดหมู่สินค้า
          </a>
          <a href="/deals">ดีลพิเศษ</a>
          <a href="/recommendations">สินค้าแนะนำ</a>
          <a className="active" href="/best-sellers">
            สินค้าขายดี
          </a>
          <a href="/thai-brands">แบรนด์ไทย</a>
          <a href="/articles">บทความ</a>
          <a href="/help">ช่วยเหลือ</a>
        </nav>
      </header>
      <section className="best-hero">
        <img
          src={bestSellerReference}
          alt="สินค้าขายดี สินค้ายอดนิยมจากร้านค้าไทย ที่ลูกค้าเลือกซื้อมากที่สุด"
        />
      </section>
      <section className="recommend-categories best-categories recommend-width">
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
            <span>{label}</span>
          </button>
        ))}
      </section>
      <section className="best-catalog recommend-width">
        <aside className="best-filters">
          <h2>ตัวกรองสินค้า</h2>
          <h3>หมวดหมู่</h3>
          {[
            "ทั้งหมด",
            "แฟชั่น & เครื่องแต่งกาย",
            "ความงาม & สุขภาพ",
            "บ้าน & ไลฟ์สไตล์",
            "มือถือ & อิเล็กทรอนิกส์",
            "อาหาร & เครื่องดื่ม",
            "แม่และเด็ก",
            "กีฬา & กิจกรรมกลางแจ้ง",
            "ของขวัญ & ของชำร่วย",
            "สินค้า OTOP",
            "สินค้าใหม่",
          ].map((x, i) => (
            <label key={x}>
              <input type="checkbox" defaultChecked={i === 0} />
              {x}
            </label>
          ))}
          <hr />
          <h3>ช่วงราคา</h3>
          {[
            "ทั้งหมด",
            "ต่ำกว่า 100 บาท",
            "100 - 300 บาท",
            "301 - 500 บาท",
            "501 - 1,000 บาท",
            "มากกว่า 1,000 บาท",
          ].map((x, i) => (
            <label key={x}>
              <input type="radio" name="price" defaultChecked={i === 0} />
              {x}
            </label>
          ))}
          <div className="best-price-range">
            <input
              aria-label="ราคาต่ำสุด"
              value={minPrice}
              onChange={(e) => setMinPrice(e.target.value)}
              placeholder="฿ ขั้นต่ำ"
            />
            <span>-</span>
            <input
              aria-label="ราคาสูงสุด"
              value={maxPrice}
              onChange={(e) => setMaxPrice(e.target.value)}
              placeholder="฿ สูงสุด"
            />
            <button onClick={() => notify("ใช้ตัวกรองราคาแล้ว")}>ตกลง</button>
          </div>
        </aside>
        <div className="best-results">
          <div className="best-heading">
            <div>
              <BarChart3 />
              <span>
                <h1>สินค้าขายดี</h1>
                <p>อัปเดตยอดขายล่าสุด สินค้าที่ลูกค้าเลือกซื้อมากที่สุด</p>
              </span>
            </div>
            <div>
              <small>แสดง 1 - 24 จาก 1,268 รายการ</small>
              <b>เรียงตาม</b>
              <select
                aria-label="เรียงสินค้า"
                value={sort}
                onChange={(e) => setSort(e.target.value)}
              >
                <option>ยอดขายสูงสุด</option>
                <option>ราคาต่ำสุด</option>
                <option>คะแนนรีวิวสูงสุด</option>
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
          <div className="best-rank-tabs">
            {[
              "ยอดขายสูงสุด",
              "คะแนนรีวิวสูงสุด",
              "สินค้ายอดนิยมประจำสัปดาห์",
              "สินค้า OTOP ขายดี",
            ].map((x, i) => (
              <button
                className={activeRank === i ? "active" : ""}
                key={x}
                onClick={() => {
                  setActiveRank(i);
                  notify(`เลือก ${x}`);
                }}
              >
                {x}
              </button>
            ))}
          </div>
          <div className={`best-product-grid ${grid ? "" : "list-view"}`}>
            {products.map((p, index) => (
              <article className="best-product" key={String(p[0])}>
                <div className="best-photo">
                  <div
                    className="best-sprite"
                    style={
                      {
                        "--crop-x": `${p[8]}`,
                        "--crop-y": `${p[9]}`,
                      } as React.CSSProperties
                    }
                  >
                    <img src={bestSellerReference} alt="" />
                  </div>
                  <strong className={`rank rank-${index + 1}`}>
                    {index + 1}
                  </strong>
                  {index === 0 && (
                    <span className="best-badge">
                      BEST
                      <br />
                      SELLER
                    </span>
                  )}
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
                <div className="best-info">
                  <h2>
                    {String(p[0])
                      .split("\n")
                      .map((line, i) => (
                        <span key={i}>{line}</span>
                      ))}
                  </h2>
                  <p className="best-shop">
                    <Store /> {p[1]}
                  </p>
                  <p className="best-price">
                    <b>฿{Number(p[2]).toLocaleString()}</b>
                    <del>฿{Number(p[3]).toLocaleString()}</del>
                    <em>-{p[4]}%</em>
                  </p>
                  <p className="best-meta">
                    <b>★ {p[5]}</b> ({p[6]}) <span>ขายแล้ว {p[7]} ชิ้น</span>
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
