import { useEffect, useRef, useState } from "react";
import "./styles/member-home.css";
import { useAuth } from "./contexts/AuthContext";
import LoginPage from "./pages/LoginPage";
import SignupPage from "./pages/SignupPage";
import DashboardPage from "./pages/DashboardPage";
import AdminDashboardPage from "./pages/AdminDashboardPage";
import AdminStoresPage from "./pages/AdminStoresPage";
import AdminApplicationsPage from "./pages/AdminApplicationsPage";
import AdminCategoriesPage from "./pages/AdminCategoriesPage";
import AdminPromotionsPage from "./pages/AdminPromotionsPage";
import AdminUsersPage from "./pages/AdminUsersPage";
import AdminRolesPage from "./pages/AdminRolesPage";
import MerchantDashboardPage from "./pages/MerchantDashboardPage";
import ProductManagementPage from "./pages/ProductManagementPage";
import OrderManagementPage from "./pages/OrderManagementPage";
import CustomerManagementPage from "./pages/CustomerManagementPage";
import PromotionManagementPage from "./pages/PromotionManagementPage";
import ReportsPage from "./pages/ReportsPage";
import FinancePage from "./pages/FinancePage";
import ShippingPage from "./pages/ShippingPage";
import MyStorePage from "./pages/MyStorePage";
import SettingsPage from "./pages/SettingsPage";
import DealsPage from "./pages/DealsPage";
import RecommendedProductsPage from "./pages/RecommendedProductsPage";
import BestSellerPage from "./pages/BestSellerPage";
import ThaiBrandsPage from "./pages/ThaiBrandsPage";
import BlogPage from "./pages/BlogPage";
import HelpPage from "./pages/HelpPage";
import MerchantSignupPage from "./pages/MerchantSignupPage";
import OwnerInfoPage from "./pages/OwnerInfoPage";
import DocumentConfirmPage from "./pages/DocumentConfirmPage";
import VerifySendPage from "./pages/VerifySendPage";
import CartPage from "./pages/CartPage";
import {
  ArrowRight,
  ChevronDown,
  ChevronRight,
  CircleEllipsis,
  Gift,
  Headphones,
  Heart,
  LogOut,
  Menu,
  PackageCheck,
  Search,
  ShieldCheck,
  ShoppingBag,
  ShoppingCart,
  Store,
  Truck,
  UserRound,
  X,
} from "lucide-react";

const categories = [
  ["👕", "แฟชั่น\nเสื้อผ้า"],
  ["🧴", "ความงาม\nและสุขภาพ"],
  ["🛋️", "บ้านและ\nไลฟ์สไตล์"],
  ["📱", "มือถือและ\nอิเล็กทรอนิกส์"],
  ["🥘", "อาหารและ\nเครื่องดื่ม"],
  ["🏋️", "กีฬาและ\nกิจกรรมกลางแจ้ง"],
  ["🧸", "ของเล่น\nและเด็ก"],
  ["🪑", "เฟอร์นิเจอร์\nและของแต่งบ้าน"],
  ["🪴", "สินค้าเพื่อ\nสิ่งแวดล้อม"],
  ["🎁", "ของขวัญ\nและงานฝีมือ"],
  ["•••", "อื่นๆ"],
];

const products = [
  {
    name: "กระเป๋าผ้าแคนวาส ลายไทย",
    price: 290,
    old: 350,
    rating: "4.8",
    sold: 120,
    emoji: "👜",
    bg: "#f3eee8",
  },
  {
    name: "ขนมข้าวกล้องอบกรอบ รสธัญพืช",
    price: 89,
    old: 120,
    rating: "4.7",
    sold: 320,
    emoji: "🍘",
    bg: "#fff4dd",
  },
  {
    name: "เซรั่มบำรุงผิว สารสกัดธรรมชาติ",
    price: 590,
    old: 790,
    rating: "4.9",
    sold: 85,
    emoji: "🧴",
    bg: "#f3f1ef",
  },
  {
    name: "ลำโพงบลูทูธพกพา",
    price: 790,
    old: 1090,
    rating: "4.6",
    sold: 210,
    emoji: "🔊",
    bg: "#eff1f4",
  },
  {
    name: "ชุดของเล่นไม้ เสริมพัฒนาการ",
    price: 490,
    old: 650,
    rating: "4.8",
    sold: 95,
    emoji: "🧸",
    bg: "#fff6e7",
  },
  {
    name: "ต้นมอนสเตอร่า พร้อมกระถาง",
    price: 350,
    old: 450,
    rating: "4.7",
    sold: 180,
    emoji: "🪴",
    bg: "#eef8ef",
  },
];

const benefits = [
  { icon: Truck, title: "ส่งได้ทั่วไทย", text: "หลายช่องทางจัดส่ง" },
  { icon: ShieldCheck, title: "ชำระเงินปลอดภัย", text: "รองรับ QR Payment" },
  { icon: Store, title: "ร้านค้าไทยคุณภาพ", text: "คัดสรรมาเพื่อคุณ" },
  { icon: PackageCheck, title: "คืนสินค้าได้", text: "มั่นใจทุกการสั่งซื้อ" },
  { icon: Headphones, title: "ดูแลทุกออเดอร์", text: "พร้อมช่วยเหลือเสมอ" },
];

function Brand() {
  return (
    <a className="brand" href="#" aria-label="ThaiShopFun หน้าแรก">
      <span className="brand-mark">
        <ShoppingBag size={24} />
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

function MarketplacePage() {
  const [menuOpen, setMenuOpen] = useState(false);
  const [query, setQuery] = useState("");
  const [cart, setCart] = useState(0);
  const [notice, setNotice] = useState("");
  const [accountOpen, setAccountOpen] = useState(false);
  const { user, loading, logout } = useAuth();
  const accountRef = useRef<HTMLDivElement>(null);
  const accountButton = useRef<HTMLButtonElement>(null);
  const [signingOut, setSigningOut] = useState(false);
  useEffect(() => {
    if (!accountOpen) return;
    const outside = (event: PointerEvent) => {
      if (!accountRef.current?.contains(event.target as Node)) setAccountOpen(false);
    };
    const escape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') { setAccountOpen(false); accountButton.current?.focus(); }
    };
    document.addEventListener('pointerdown', outside);
    document.addEventListener('keydown', escape);
    return () => { document.removeEventListener('pointerdown', outside); document.removeEventListener('keydown', escape); };
  }, [accountOpen]);

  const notify = (message: string) => {
    setNotice(message);
    window.setTimeout(() => setNotice(""), 2200);
  };

  const submitSearch = (event: React.FormEvent) => {
    event.preventDefault();
    notify(
      query.trim()
        ? `กำลังค้นหา “${query.trim()}”`
        : "พิมพ์ชื่อสินค้าที่ต้องการค้นหา",
    );
  };
  const signOut = async () => {
    if (signingOut) return;
    setSigningOut(true);
    try { await logout(); setAccountOpen(false); notify("ออกจากระบบแล้ว"); }
    catch { notify("ออกจากระบบไม่สำเร็จ กรุณาลองอีกครั้ง"); }
    finally { setSigningOut(false); }
  };
  const dashboardPath =
    user?.role === "ADMIN"
      ? "/admin"
      : user?.role === "MERCHANT"
        ? "/merchant/dashboard"
        : "/";
  const accountName = user
    ? `${user.firstName} ${user.lastName}`.trim() || user.email
    : "";

  return (
    <div className={`app ${user ? "member-home" : ""}`}>
      <header>
        <div className="topbar page-width">
          <button
            className="mobile-menu"
            onClick={() => setMenuOpen(true)}
            aria-label="เปิดเมนู"
          >
            <Menu />
          </button>
          <Brand />
          <form className="search" onSubmit={submitSearch}>
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="ค้นหาสินค้า แบรนด์ หรือร้านค้า..."
              aria-label="ค้นหาสินค้า"
            />
            <button aria-label="ค้นหา">
              <Search size={21} />
            </button>
          </form>
          <div className={`account-actions ${user ? "authenticated" : ""}`}>
            {loading ? (
              <span
                className="account-loading"
                aria-label="กำลังตรวจสอบบัญชี"
              />
            ) : user ? (
              <div className="account-profile-wrap" ref={accountRef} onBlur={event => { if (!event.currentTarget.contains(event.relatedTarget)) setAccountOpen(false); }}>
                <button
                  className="account-profile"
                  ref={accountButton}
                  aria-controls="member-account-menu"
                  onClick={() => setAccountOpen((open) => !open)}
                  aria-expanded={accountOpen}
                  aria-label={`บัญชี ${accountName}`}
                >
                  <span className="member-avatar" aria-hidden="true">{(user.firstName || user.email).slice(0,1).toUpperCase()}</span>
                  <p>
                    <b>{accountName}</b>
                    <small>
                      {user.role === "CUSTOMER"
                        ? "ลูกค้า"
                        : user.role === "MERCHANT"
                          ? "เจ้าของร้าน"
                          : "ผู้ดูแลระบบ"}
                    </small>
                  </p>
                  <ChevronDown />
                </button>
                {accountOpen && (
                  <div className="account-menu" id="member-account-menu">
                    <div className="member-menu-heading"><b>{accountName}</b><span>{user.email}</span><small><ShieldCheck size={13}/> เข้าสู่ระบบแล้ว</small></div>
                    {user.role !== 'CUSTOMER' && <a href={dashboardPath}><Store/> {user.role === 'ADMIN' ? 'จัดการระบบ' : 'จัดการร้านค้า'} <ChevronRight/></a>}
                    <a href="/cart"><ShoppingCart/> ตะกร้าสินค้าของฉัน <ChevronRight/></a>
                    <a href="/deals"><Gift/> ดีลพิเศษ <ChevronRight/></a>
                    <a href="/help"><Headphones/> ศูนย์ช่วยเหลือ <ChevronRight/></a>
                    <button className="member-signout" disabled={signingOut} onClick={() => void signOut()}><LogOut/>{signingOut ? 'กำลังออกจากระบบ...' : 'ออกจากระบบ'}</button>
                  </div>
                )}
              </div>
            ) : (
              <>
                <a className="login" href="/login">
                  <UserRound size={22} />
                  เข้าสู่ระบบ
                </a>
                <a className="outline" href="/signup">
                  สมัครสมาชิก
                </a>
              </>
            )}
            <a
              className="cart"
              href="/cart"
              aria-label="ตะกร้าสินค้า"
            >
              <ShoppingCart />
              <em>{cart}</em>
            </a>
          </div>
        </div>
        <nav className="nav page-width" aria-label="เมนูหลัก">
          <a href="#categories">
            <Menu size={18} /> หมวดหมู่สินค้า
          </a>
          <a href="/deals">ดีลพิเศษ</a>
          <a href="/recommendations">สินค้าแนะนำ</a>
          <a href="#shops">ร้านค้าแนะนำ</a>
          <a href="/best-sellers">สินค้าขายดี</a>
          <a href="/thai-brands">แบรนด์ไทย</a>
          <a href="/articles">บทความ</a>
          <a href="/help">ช่วยเหลือ</a>
        </nav>
        {menuOpen && (
          <div className="drawer-wrap" onClick={() => setMenuOpen(false)}>
            <aside className="drawer" onClick={(e) => e.stopPropagation()}>
              <button
                className="drawer-close"
                onClick={() => setMenuOpen(false)}
              >
                <X />
              </button>
              <Brand />
              {[
                "หมวดหมู่สินค้า",
                "ดีลพิเศษ",
                "สินค้าแนะนำ",
                "ร้านค้าแนะนำ",
                "สินค้าขายดี",
                "แบรนด์ไทย",
                "บทความ",
                "ช่วยเหลือ",
              ].map((x) => (
                <a
                  key={x}
                  href={
                    x === "ดีลพิเศษ"
                      ? "/deals"
                      : x === "สินค้าแนะนำ"
                        ? "/recommendations"
                        : x === "สินค้าขายดี"
                          ? "/best-sellers"
                          : x === "แบรนด์ไทย"
                            ? "/thai-brands"
                            : x === "บทความ"
                              ? "/articles"
                              : x === "ช่วยเหลือ"
                                ? "/help"
                                : "#categories"
                  }
                  onClick={() => setMenuOpen(false)}
                >
                  {x}
                  <ChevronRight size={17} />
                </a>
              ))}
            </aside>
          </div>
        )}
      </header>

      <main>
        {user && <section className="member-welcome page-width" aria-labelledby="member-welcome-title">
          <div className="member-welcome-copy"><span className="member-eyebrow"><span/> พื้นที่ของคนรักของไทย</span><h1 id="member-welcome-title">สวัสดีคุณ {user.firstName || accountName} <span>วันนี้ช้อปอะไรดี?</span></h1><p>ของดีจากร้านค้าไทย พร้อมเติมความสุขให้วันของคุณ</p></div>
          <div className="member-shortcuts"><a href="/deals"><span><Gift/></span><b>ดีลพิเศษ</b><small>ค้นหาของถูกใจ</small></a><a href="/cart"><span><ShoppingBag/></span><b>ตะกร้าของฉัน</b><small>ช้อปต่อได้เลย</small></a><a href="/help"><span><Headphones/></span><b>ช่วยเหลือ</b><small>ให้เราช่วยดูแล</small></a></div>
        </section>}
        <section className="hero">
          <img
            className="hero-image"
            src="/assets/images/home/hero-complete-clean-v4.png"
            alt="ช้อปสนุก สนับสนุนของไทย สินค้าคุณภาพจากร้านค้าไทยส่งตรงถึงบ้านคุณ"
          />
          <button
            className="hero-hotspot"
            aria-label="ช้อปเลย"
            onClick={() =>
              document
                .getElementById("recommended")
                ?.scrollIntoView({ behavior: "smooth" })
            }
          />
        </section>

        <section className="benefits">
          <div className="page-width benefit-grid">
            {benefits.map(({ icon: Icon, title, text }) => (
              <div className="benefit" key={title}>
                <span>
                  <Icon />
                </span>
                <div>
                  <b>{title}</b>
                  <small>{text}</small>
                </div>
              </div>
            ))}
          </div>
        </section>

        <section className="page-width section" id="categories">
          <div className="section-head">
            <h2>ช้อปตามหมวดหมู่</h2>
            <a href="#categories">
              ดูทั้งหมด <ArrowRight size={15} />
            </a>
          </div>
          <div className="category-grid">
            {categories.map(([emoji, label], i) => (
              <button
                className={`category ${i === 10 ? "category-more" : ""}`}
                key={label}
                onClick={() => notify(`เลือกหมวด ${label.replace("\n", " ")}`)}
              >
                <span>{i === 10 ? <CircleEllipsis /> : emoji}</span>
                <b>
                  {label.split("\n").map((x, j) => (
                    <span key={j}>{x}</span>
                  ))}
                </b>
              </button>
            ))}
          </div>
        </section>

        <section className="page-width promo-grid" id="deals">
          <article className="promo thai">
            <div>
              <h3>
                ของดีจากทั่วไทย
                <br />
                ส่งถึงบ้านคุณ
              </h3>
              <p>
                สนับสนุนผู้ประกอบการไทย
                <br />
                ช้อปง่าย ได้ของดี
              </p>
              <button onClick={() => notify("เปิดดูสินค้าไทย")}>
                ช้อปสินค้าไทย <ArrowRight />
              </button>
            </div>
            <div className="promo-art">
              🥜<span>🥭</span>
            </div>
          </article>
          <article className="promo gadget">
            <div>
              <h3>
                Gadget ที่ใช่
                <br />
                ในราคาสุดคุ้ม
              </h3>
              <p>อัปเกรดไลฟ์สไตล์ของคุณวันนี้</p>
              <button onClick={() => notify("เปิดดู Gadget")}>
                ช้อปเลย <ArrowRight />
              </button>
            </div>
            <div className="promo-art">
              🎧<span>💻</span>
            </div>
          </article>
          <article className="promo beauty">
            <div>
              <h3>
                สวยได้
                <br />
                ในแบบคุณ
              </h3>
              <p>
                ผลิตภัณฑ์ความงาม
                <br />
                จากแบรนด์ไทย
              </p>
              <button onClick={() => notify("เปิดดูสินค้าความงาม")}>
                ช้อปเลย <ArrowRight />
              </button>
            </div>
            <div className="promo-art">
              🧴<span>🌿</span>
            </div>
          </article>
        </section>

        <section className="page-width section recommended" id="recommended">
          <div className="section-head">
            <h2>สินค้าแนะนำ</h2>
            <a href="/recommendations">
              ดูทั้งหมด <ArrowRight size={15} />
            </a>
          </div>
          <div className="product-layout">
            <div className="product-grid">
              {products.map((p, i) => {
                const discount = Math.round((1 - p.price / p.old) * 100);
                return (
                  <article className="product-card" key={p.name}>
                    <button
                      className="heart"
                      aria-label="เพิ่มรายการโปรด"
                      onClick={() => notify("เพิ่มในรายการโปรดแล้ว")}
                    >
                      <Heart size={17} />
                    </button>
                    <button
                      className="product-image"
                      style={{ background: p.bg }}
                      onClick={() => {
                        setCart((c) => c + 1);
                        notify("เพิ่มสินค้าลงตะกร้าแล้ว");
                      }}
                    >
                      <span>{p.emoji}</span>
                    </button>
                    <div className="product-info">
                      <h3>{p.name}</h3>
                      <div>
                        <strong>฿{p.price.toLocaleString()}</strong>
                        <del>฿{p.old.toLocaleString()}</del>
                        <em>-{discount}%</em>
                      </div>
                      <small>
                        <b>★ {p.rating}</b> ({p.sold})
                      </small>
                    </div>
                  </article>
                );
              })}
            </div>
            <aside className="seller-card" id="shops">
              <div className="shop-illustration">
                <span>🏪</span>
                <i>📦</i>
              </div>
              <h3>เปิดร้านค้ากับเรา</h3>
              <h4>เริ่มขายออนไลน์ได้ง่ายๆ</h4>
              <ul>
                <li>เข้าถึงลูกค้าทั่วประเทศ</li>
                <li>ไม่มีค่ารายเดือน</li>
                <li>เสียเพียง 5% เมื่อขายได้</li>
                <li>มีทีมงานดูแลและให้คำปรึกษา</li>
              </ul>
              <a className="primary" href="/merchant/register">
                สมัครเปิดร้านค้า <ArrowRight size={17} />
              </a>
            </aside>
          </div>
        </section>
      </main>

      <footer>
        <div className="page-width">
          <Brand />
          <p>พื้นที่ของคนไทย เพื่อร้านค้าไทยทุกคน</p>
          <small>© 2026 ThaiShopFun. All rights reserved.</small>
        </div>
      </footer>
      {notice && (
        <div className="toast" role="status">
          {notice}
        </div>
      )}
    </div>
  );
}

export default function App() {
  if (window.location.pathname === "/login") return <LoginPage />;
  if (window.location.pathname === "/signup") return <SignupPage />;
  if (window.location.pathname === "/dashboard") return <DashboardPage />;
  if (window.location.pathname === "/admin") return <AdminDashboardPage />;
  if (window.location.pathname === "/admin/stores") return <AdminStoresPage />;
  if (window.location.pathname === "/admin/store-applications") return <AdminApplicationsPage />;
  if (window.location.pathname === "/admin/categories") return <AdminCategoriesPage />;
  if (window.location.pathname === "/admin/promotions") return <AdminPromotionsPage />;
  if (window.location.pathname === "/admin/users") return <AdminUsersPage />;
  if (window.location.pathname === "/admin/roles") return <AdminRolesPage />;
  if (window.location.pathname === "/merchant/dashboard")
    return <MerchantDashboardPage />;
  if (window.location.pathname === "/merchant/products")
    return <ProductManagementPage />;
  if (window.location.pathname === "/merchant/orders")
    return <OrderManagementPage />;
  if (window.location.pathname === "/merchant/customers")
    return <CustomerManagementPage />;
  if (window.location.pathname === "/merchant/promotions")
    return <PromotionManagementPage />;
  if (window.location.pathname === "/merchant/reports")
    return <ReportsPage />;
  if (window.location.pathname === "/merchant/finance")
    return <FinancePage />;
  if (window.location.pathname === "/merchant/shipping")
    return <ShippingPage />;
  if (window.location.pathname === "/merchant/store")
    return <MyStorePage />;
  if (window.location.pathname === "/merchant/settings")
    return <SettingsPage />;
  if (window.location.pathname === "/deals") return <DealsPage />;
  if (window.location.pathname === "/recommendations")
    return <RecommendedProductsPage />;
  if (window.location.pathname === "/best-sellers") return <BestSellerPage />;
  if (window.location.pathname === "/thai-brands") return <ThaiBrandsPage />;
  if (window.location.pathname === "/articles") return <BlogPage />;
  if (window.location.pathname === "/help") return <HelpPage />;
  if (window.location.pathname === "/cart") return <CartPage />;
  if (window.location.pathname === "/merchant/register")
    return <MerchantSignupPage />;
  if (window.location.pathname === "/merchant/owner-info")
    return <OwnerInfoPage />;
  if (window.location.pathname === "/merchant/documents")
    return <DocumentConfirmPage />;
  if (window.location.pathname === "/merchant/review")
    return <VerifySendPage />;
  return <MarketplacePage />;
}
