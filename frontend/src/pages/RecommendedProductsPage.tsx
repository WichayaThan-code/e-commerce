import { useMemo, useState } from "react";
<<<<<<< HEAD
import {
  Armchair,
  ArrowRight,
  Baby,
  ChevronDown,
  CircleEllipsis,
  CircleUserRound,
=======
import { Link } from "react-router-dom";
import {
  ArrowRight,
  Baby,
  CircleEllipsis,
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
  Dumbbell,
  Gift,
  Grid2X2,
  Heart,
  Leaf,
  List,
<<<<<<< HEAD
  LogOut,
  Menu,
=======
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
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
<<<<<<< HEAD
import { useAuth } from "../contexts/AuthContext";
import recommendReference from "../assets/images/references/storefront/recommend-product.png";
import "../styles/recommended-products.css";

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
  [Gift, "ของขวัญ"],
  [Leaf, "สินค้าที่ดีต่อสิ่งแวดล้อม"],
  [CircleEllipsis, "อื่นๆ"],
] as const;

const products = [
  {
    tag: "แนะนำ",
    tagClass: "red",
    name: "ข้าวแต๋นธัญพืชอบกรอบ\nสูตรต้นตำรับ",
    shop: "ร้านข้าวแต๋นแม่จันทร์",
    price: 89,
    old: 120,
    discount: 26,
    rating: "4.8",
    reviews: 320,
    sold: "1.2K",
    crop: "crop-1",
  },
  {
    tag: "ขายดี",
    tagClass: "green",
    name: "เสื้อยืดคอตตอน 100%\nใส่สบาย ผ้าฝ้าย",
    shop: "GoodWear Thailand",
    price: 259,
    old: 399,
    discount: 35,
    rating: "4.7",
    reviews: 210,
    sold: "856",
    crop: "crop-2",
  },
  {
    tag: "ใหม่",
    tagClass: "orange",
    name: "ต้นมอนสเตอร่า\nพร้อมกระถางสไตล์มินิมอล",
    shop: "Green Home",
    price: 350,
    old: 450,
    discount: 22,
    rating: "4.9",
    reviews: 180,
    sold: "412",
    crop: "crop-3",
  },
  {
    tag: "ลดพิเศษ",
    tagClass: "red",
    name: "เซ็ตสกินแคร์บำรุงผิว\nจากธรรมชาติ",
    shop: "Natura Official",
    price: 890,
    old: 1490,
    discount: 40,
    rating: "4.8",
    reviews: 142,
    sold: "320",
    crop: "crop-4",
  },
  {
    tag: "รักษ์โลก",
    tagClass: "green",
    name: "กระเป๋าผ้าแคนวาส\nลายไทยสุดคูล",
    shop: "ThaiCraft",
    price: 290,
    old: 350,
    discount: 17,
    rating: "4.7",
    reviews: 98,
    sold: "564",
    crop: "crop-5",
  },
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

export default function RecommendedProductsPage() {
  const { user, loading, logout } = useAuth();
  const [activeCategory, setActiveCategory] = useState(0);
  const [cart, setCart] = useState(0);
  const [notice, setNotice] = useState("");
  const [accountOpen, setAccountOpen] = useState(false);
  const [sort, setSort] = useState("สินค้าแนะนำ");
  const [grid, setGrid] = useState(true);
  const [brandQuery, setBrandQuery] = useState("");
  const [favorites, setFavorites] = useState<number[]>([]);
  const accountName = user
    ? `${user.firstName} ${user.lastName}`.trim() || user.email
    : "";
  const dashboard =
    user?.role === "ADMIN"
      ? "/admin"
      : user?.role === "MERCHANT"
        ? "/merchant/dashboard"
        : "/";
=======
import { usePublicTranslation } from "../hooks/usePublicTranslation";
import recommendReference from "../assets/images/references/storefront/recommend-product.png";
import "../styles/recommended-products.css";

const categoryIcons = [Sparkles, Shirt, Wine, Sofa, ShoppingBag, Utensils, Dumbbell, Baby, Gift, Gift, Leaf, CircleEllipsis];
const ratingStars = ["★★★★", "★★★½", "★★★", "★★"] as const;
type SortKey = "featured" | "price" | "best";

type RecommendProductRow = {
  tagClass: string;
  price: number;
  old: number;
  discount: number;
  rating: string;
  reviews: number;
  sold: string;
  crop: string;
};
type RecommendProductInfo = { tag: string; name: string; shop: string };
type RecommendBanner = { title: string; text: string; btn: string; toast: string };

export default function RecommendedProductsPage() {
  const { tp, localeTag } = usePublicTranslation();
  const [activeCategory, setActiveCategory] = useState(0);
  const [notice, setNotice] = useState("");
  const [sort, setSort] = useState<SortKey>("featured");
  const [grid, setGrid] = useState(true);
  const [brandQuery, setBrandQuery] = useState("");
  const [favorites, setFavorites] = useState<number[]>([]);

  const recommendCategories = tp("recommend.categories", { returnObjects: true }) as unknown as string[];
  const priceRanges = tp("catalog.priceRanges", { returnObjects: true }) as unknown as string[];
  const ratingRanges = tp("catalog.ratingRanges", { returnObjects: true }) as unknown as string[];
  const brandTags = tp("catalog.brandTags", { returnObjects: true }) as unknown as string[];
  const banners = tp("recommend.banners", { returnObjects: true }) as unknown as RecommendBanner[];

  const products = useMemo(() => {
    const productRows = tp("recommend.productRows", { returnObjects: true }) as unknown as RecommendProductRow[];
    const productInfos = tp("recommend.products", { returnObjects: true }) as unknown as RecommendProductInfo[];
    return productRows.map((row, index) => {
      const info = productInfos[index];
      return { ...row, tag: info.tag, name: info.name, shop: info.shop };
    });
  }, [tp]);

>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
  const notify = (message: string) => {
    setNotice(message);
    window.setTimeout(() => setNotice(""), 1900);
  };
<<<<<<< HEAD
  const visibleProducts = useMemo(
    () =>
      sort === "ราคาต่ำสุด"
        ? [...products].sort((a, b) => a.price - b.price)
        : products,
    [sort],
  );
  const signOut = async () => {
    await logout();
    setAccountOpen(false);
    notify("ออกจากระบบแล้ว");
  };

  return (
    <main className="recommend-page">
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
              aria-label="ตะกร้าสินค้า"
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
          <a className="active" href="/recommendations">
            สินค้าแนะนำ
          </a>
          <a href="/#shops">ร้านค้าแนะนำ</a>
          <a href="/best-sellers">สินค้าขายดี</a>
          <a href="/thai-brands">แบรนด์ไทย</a>
          <a href="/articles">บทความ</a>
          <a href="/help">ช่วยเหลือ</a>
        </nav>
      </header>

      <section className="recommend-intro recommend-width">
        <div>
          <p className="recommend-breadcrumb">
            <a href="/">หน้าแรก</a>
            <span>›</span>สินค้าแนะนำ
          </p>
          <h1>สินค้าแนะนำ</h1>
          <p>คัดสรรสินค้าคุณภาพ จากร้านค้าไทย เพื่อคุณโดยเฉพาะ</p>
        </div>
        <div className="recommend-hero">
          <img
            src={recommendReference}
            alt="ของดีไทย ใกล้ตัวคุณ สนับสนุนร้านค้าไทย สร้างโอกาสให้ชุมชน"
          />
=======

  const visibleProducts = useMemo(
    () => (sort === "price" ? [...products].sort((a, b) => a.price - b.price) : products),
    [products, sort],
  );

  const bannerEmojis = ["🎁", "🚚", "🌍"];

  return (
    <main className="recommend-page">
      <section className="recommend-intro recommend-width">
        <div>
          <p className="recommend-breadcrumb">
            <Link to="/">{tp("common.home")}</Link>
            <span>›</span>
            {tp("recommend.breadcrumb")}
          </p>
          <h1>{tp("recommend.title")}</h1>
          <p>{tp("recommend.subtitle")}</p>
        </div>
        <div className="recommend-hero">
          <img src={recommendReference} alt={tp("recommend.heroAlt")} />
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
        </div>
      </section>

      <section className="recommend-categories recommend-width">
<<<<<<< HEAD
        {categories.map(([Icon, label], index) => (
          <button
            className={activeCategory === index ? "active" : ""}
            key={`${label}-${index}`}
            onClick={() => {
              setActiveCategory(index);
              notify(`เลือกหมวด ${label}`);
            }}
          >
            <Icon />
            <span>{label}</span>
          </button>
        ))}
=======
        {recommendCategories.map((label, index) => {
          const Icon = categoryIcons[index % categoryIcons.length];
          return (
            <button
              className={activeCategory === index ? "active" : ""}
              key={`${label}-${index}`}
              type="button"
              onClick={() => {
                setActiveCategory(index);
                notify(tp("catalog.pickCategory", { name: label }));
              }}
            >
              <Icon />
              <span>{label}</span>
            </button>
          );
        })}
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
      </section>

      <section className="recommend-catalog recommend-width">
        <aside className="recommend-filters">
<<<<<<< HEAD
          <h2>ตัวกรองสินค้า</h2>
          <hr />
          <h3>ช่วงราคา</h3>
          {[
            "ต่ำกว่า 100 บาท",
            "100 - 300 บาท",
            "301 - 500 บาท",
            "501 - 1,000 บาท",
            "มากกว่า 1,000 บาท",
          ].map((x) => (
=======
          <h2>{tp("catalog.filters")}</h2>
          <hr />
          <h3>{tp("catalog.price")}</h3>
          {priceRanges.map((x) => (
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
            <label key={x}>
              <input type="checkbox" />
              {x}
            </label>
          ))}
          <hr />
<<<<<<< HEAD
          <h3>คะแนนสินค้า</h3>
          {[
            ["★★★★", "4.5 ขึ้นไป"],
            ["★★★½", "4.0 ขึ้นไป"],
            ["★★★", "3.5 ขึ้นไป"],
            ["★★", "3.0 ขึ้นไป"],
          ].map(([stars, text]) => (
            <label className="rating-filter" key={text}>
              <input type="checkbox" />
              <b>{stars}</b>
=======
          <h3>{tp("catalog.rating")}</h3>
          {ratingRanges.map((text, index) => (
            <label className="rating-filter" key={text}>
              <input type="checkbox" />
              <b>{ratingStars[index]}</b>
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
              {text}
            </label>
          ))}
          <hr />
<<<<<<< HEAD
          <h3>แบรนด์</h3>
=======
          <h3>{tp("catalog.brands")}</h3>
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
          <div className="brand-search">
            <Search />
            <input
              value={brandQuery}
              onChange={(e) => setBrandQuery(e.target.value)}
<<<<<<< HEAD
              placeholder="ค้นหาแบรนด์..."
            />
          </div>
          {["สินค้าแบรนด์ไทย", "สินค้า OTOP", "สินค้ารักษ์โลก"]
            .filter((x) => x.includes(brandQuery))
=======
              placeholder={tp("catalog.brandSearch")}
            />
          </div>
          {brandTags
            .filter((x) => x.toLowerCase().includes(brandQuery.trim().toLowerCase()))
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
            .map((x) => (
              <label key={x}>
                <input type="checkbox" />
                {x}
              </label>
            ))}
        </aside>
        <div className="recommend-results">
          <div className="recommend-tools">
<<<<<<< HEAD
            <span>เรียงตาม</span>
            <select
              value={sort}
              onChange={(e) => setSort(e.target.value)}
              aria-label="เรียงสินค้า"
            >
              <option>สินค้าแนะนำ</option>
              <option>ราคาต่ำสุด</option>
              <option>ขายดีที่สุด</option>
            </select>
            <button
              className={grid ? "active" : ""}
              onClick={() => setGrid(true)}
              aria-label="มุมมองตาราง"
=======
            <span>{tp("common.sortBy")}</span>
            <select value={sort} onChange={(e) => setSort(e.target.value as SortKey)} aria-label={tp("catalog.sortAria")}>
              <option value="featured">{tp("catalog.sortFeatured")}</option>
              <option value="price">{tp("catalog.sortPrice")}</option>
              <option value="best">{tp("catalog.sortBest")}</option>
            </select>
            <button
              className={grid ? "active" : ""}
              type="button"
              onClick={() => setGrid(true)}
              aria-label={tp("common.gridView")}
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
            >
              <Grid2X2 />
            </button>
            <button
              className={!grid ? "active" : ""}
<<<<<<< HEAD
              onClick={() => setGrid(false)}
              aria-label="มุมมองรายการ"
=======
              type="button"
              onClick={() => setGrid(false)}
              aria-label={tp("common.listView")}
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
            >
              <List />
            </button>
          </div>
          <div className={`recommend-product-grid ${grid ? "" : "list-view"}`}>
            {visibleProducts.map((p, index) => (
<<<<<<< HEAD
              <article className="recommend-product" key={p.name}>
=======
              <article className="recommend-product" key={`${p.name}-${index}`}>
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
                <div className="recommend-photo">
                  <div className={`recommend-sprite ${p.crop}`}>
                    <img src={recommendReference} alt="" />
                  </div>
                  <span className={`recommend-tag ${p.tagClass}`}>{p.tag}</span>
                  <button
<<<<<<< HEAD
                    className={favorites.includes(index) ? "liked" : ""}
                    onClick={() =>
                      setFavorites((v) =>
                        v.includes(index)
                          ? v.filter((x) => x !== index)
                          : [...v, index],
                      )
                    }
                    aria-label="รายการโปรด"
=======
                    type="button"
                    className={favorites.includes(index) ? "liked" : ""}
                    onClick={() =>
                      setFavorites((v) => (v.includes(index) ? v.filter((x) => x !== index) : [...v, index]))
                    }
                    aria-label={tp("catalog.favorite")}
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
                  >
                    <Heart />
                  </button>
                </div>
                <div className="recommend-product-info">
                  <h2>
                    {p.name.split("\n").map((line, i) => (
                      <span key={i}>{line}</span>
                    ))}
                  </h2>
                  <p className="recommend-shop">
                    <Store /> {p.shop}
                  </p>
                  <p className="recommend-price">
<<<<<<< HEAD
                    <b>฿{p.price.toLocaleString()}</b>
                    <del>฿{p.old.toLocaleString()}</del>
                    <em>-{p.discount}%</em>
                  </p>
                  <p className="recommend-meta">
                    <b>★ {p.rating}</b> ({p.reviews}){" "}
                    <span>ขายแล้ว {p.sold}</span>
                  </p>
                  <button
                    onClick={() => {
                      setCart((c) => c + 1);
                      notify("เพิ่มสินค้าลงตะกร้าแล้ว");
                    }}
                  >
                    <ShoppingCart />
                    เพิ่มลงตะกร้า
=======
                    <b>฿{p.price.toLocaleString(localeTag)}</b>
                    <del>฿{p.old.toLocaleString(localeTag)}</del>
                    <em>-{p.discount}%</em>
                  </p>
                  <p className="recommend-meta">
                    <b>★ {p.rating}</b> ({p.reviews.toLocaleString(localeTag)}){" "}
                    <span>
                      {tp("common.sold")} {p.sold}
                    </span>
                  </p>
                  <button type="button" onClick={() => notify(tp("home.addedCart"))}>
                    <ShoppingCart />
                    {tp("recommend.addToCart")}
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
                  </button>
                </div>
              </article>
            ))}
          </div>
          <div className="recommend-banners">
<<<<<<< HEAD
            <article>
              <div>
                <h2>
                  ของขวัญสุดพิเศษ
                  <br />
                  สำหรับคนพิเศษ
                </h2>
                <p>เลือกของขวัญจากร้านค้าไทย</p>
                <button onClick={() => notify("เปิดดูของขวัญแนะนำ")}>
                  ดูของขวัญแนะนำ →
                </button>
              </div>
              <span>🎁</span>
            </article>
            <article>
              <div>
                <h2>
                  สินค้าคุณภาพ
                  <br />
                  ส่งตรงถึงบ้าน
                </h2>
                <p>ช้อปสะดวก ปลอดภัย มั่นใจได้</p>
                <button onClick={() => notify("เปิดดูสินค้าขายดี")}>
                  ดูสินค้าขายดี →
                </button>
              </div>
              <span>🚚</span>
            </article>
            <article>
              <div>
                <h2>
                  เลือกช้อปสินค้ารักษ์โลก
                  <br />
                  เพื่ออนาคตที่ยั่งยืน
                </h2>
                <p>สนับสนุนแบรนด์ไทย ร่วมดูแลโลก</p>
                <button onClick={() => notify("เปิดดูสินค้ารักษ์โลก")}>
                  ดูสินค้ารักษ์โลก →
                </button>
              </div>
              <span>🌍</span>
            </article>
          </div>
          <button
            className="view-all"
            onClick={() => notify("กำลังโหลดสินค้าแนะนำเพิ่มเติม")}
          >
            ดูสินค้าแนะนำทั้งหมด <ArrowRight />
=======
            {banners.map((banner, index) => (
              <article key={banner.btn}>
                <div>
                  <h2>
                    {banner.title.split("\n").map((line, i) => (
                      <span key={i}>
                        {line}
                        {i === 0 && banner.title.includes("\n") && <br />}
                      </span>
                    ))}
                  </h2>
                  <p>{banner.text}</p>
                  <button type="button" onClick={() => notify(banner.toast)}>
                    {banner.btn}
                  </button>
                </div>
                <span>{bannerEmojis[index]}</span>
              </article>
            ))}
          </div>
          <button className="view-all" type="button" onClick={() => notify(tp("recommend.loadMore"))}>
            {tp("recommend.viewAllRecommended")} <ArrowRight />
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
          </button>
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
