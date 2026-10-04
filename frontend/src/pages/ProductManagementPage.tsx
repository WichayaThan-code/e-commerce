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
  Copy,
  Crown,
  Download,
  Headphones,
  Home,
  Package,
  Pencil,
  Plus,
  Search,
  Settings,
  ShoppingBag,
  Store,
  TicketPercent,
  Trash2,
  Truck,
  Upload,
  WalletCards,
} from "lucide-react";
import LanguageSwitcher from "../components/LanguageSwitcher";
import MerchantShopMenu from "../components/MerchantShopMenu";
import { useAuth } from "../contexts/AuthContext";
import productManageImage from "../assets/images/references/merchant/product-manage.png";
import "../styles/merchant-dashboard.css";
import "../styles/product-management.css";

type Product = {
  name: string;
  detail: string;
  sku: string;
  category: string;
  price: number;
  stock: number;
  sales: number;
  updated: string;
  crop: string;
};

const initialProducts: Product[] = [
  {
    name: "ข้าวหอมมะลิ 5 กก.",
    detail: "ข้าวสารคุณภาพดี จากเกษตรกรไทย",
    sku: "RICE-001",
    category: "อาหารแห้ง",
    price: 280,
    stock: 120,
    sales: 245,
    updated: "12 ก.ย. 2566\n14:32",
    crop: "-320px -473px",
  },
  {
    name: "น้ำพริกแม่สมชาย",
    detail: "สูตรดั้งเดิม รสแซ่บ",
    sku: "CHILI-002",
    category: "อาหารแปรรูป",
    price: 120,
    stock: 38,
    sales: 189,
    updated: "12 ก.ย. 2566\n13:20",
    crop: "-320px -527px",
  },
  {
    name: "มะม่วงอบแห้ง",
    detail: "หวานธรรมชาติ ไม่ใส่สารกันบูด",
    sku: "MANGO-003",
    category: "ขนม/ของว่าง",
    price: 150,
    stock: 8,
    sales: 97,
    updated: "11 ก.ย. 2566\n18:45",
    crop: "-320px -581px",
  },
  {
    name: "เซ็ตของฝากไทย",
    detail: "ชุดของขวัญ สุดพิเศษ",
    sku: "GIFT-004",
    category: "ของฝาก",
    price: 390,
    stock: 0,
    sales: 56,
    updated: "10 ก.ย. 2566\n16:12",
    crop: "-320px -635px",
  },
  {
    name: "ต้นไม้มงคล เสริมโชค",
    detail: "เพิ่มความสดชื่นให้บ้าน",
    sku: "PLANT-005",
    category: "ต้นไม้/สวน",
    price: 250,
    stock: 25,
    sales: 76,
    updated: "10 ก.ย. 2566\n11:03",
    crop: "-320px -690px",
  },
  {
    name: "กาแฟดอยช้าง",
    detail: "กาแฟอาราบิก้า 100%",
    sku: "COFFEE-006",
    category: "เครื่องดื่ม",
    price: 320,
    stock: 16,
    sales: 132,
    updated: "9 ก.ย. 2566\n09:27",
    crop: "-320px -744px",
  },
  {
    name: "เสื้อยืดผ้าฝ้าย",
    detail: "ใส่สบาย มีหลายสี",
    sku: "TSHIRT-007",
    category: "แฟชั่น",
    price: 199,
    stock: 0,
    sales: 88,
    updated: "8 ก.ย. 2566\n20:15",
    crop: "-320px -798px",
  },
  {
    name: "สบู่สมุนไพร",
    detail: "จากธรรมชาติ 100%",
    sku: "SOAP-008",
    category: "สุขภาพ/ความงาม",
    price: 89,
    stock: 54,
    sales: 201,
    updated: "8 ก.ย. 2566\n14:08",
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

function ProductThumb({ product }: { product: Product }) {
  const [left, top] = product.crop.split(" ");
  return (
    <span className="pm-thumb">
      <img src={productManageImage} alt="" style={{ left, top }} />
    </span>
  );
}

export default function ProductManagementPage() {
  const { user, loading } = useAuth();
  const [products, setProducts] = useState(initialProducts);
  const [query, setQuery] = useState("");
  const [category, setCategory] = useState("ทั้งหมด");
  const [status, setStatus] = useState("ทั้งหมด");
  const [stock, setStock] = useState("ทั้งหมด");
  const [notice, setNotice] = useState("");
  const [selected, setSelected] = useState<string[]>([]);
  if (loading)
    return <main className="auth-loading">กำลังตรวจสอบสิทธิ์ร้านค้า...</main>;
  if (!user) {
    window.location.replace("/login?next=/merchant/products");
    return null;
  }
  if (user.role === "ADMIN") {
    window.location.replace("/admin");
    return null;
  }
  if (user.role !== "MERCHANT") {
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
  const visible = products.filter((product) => {
    const matchesQuery = `${product.name} ${product.sku} ${product.category}`
      .toLowerCase()
      .includes(query.trim().toLowerCase());
    const matchesCategory =
      category === "ทั้งหมด" || product.category === category;
    const matchesStatus =
      status === "ทั้งหมด" ||
      (status === "พร้อมขาย" ? product.stock > 0 : product.stock === 0);
    const matchesStock =
      stock === "ทั้งหมด" ||
      (stock === "ต่ำกว่า 10"
        ? product.stock < 10 && product.stock > 0
        : product.stock === 0);
    return matchesQuery && matchesCategory && matchesStatus && matchesStock;
  });
  const reset = () => {
    setQuery("");
    setCategory("ทั้งหมด");
    setStatus("ทั้งหมด");
    setStock("ทั้งหมด");
  };
  const toggleAll = () =>
    setSelected(
      selected.length === visible.length ? [] : visible.map((p) => p.sku),
    );
  const remove = (product: Product) => {
    setProducts((items) => items.filter((item) => item.sku !== product.sku));
    setSelected((items) => items.filter((sku) => sku !== product.sku));
    notify(`ลบ ${product.name} แล้ว`);
  };
  const duplicate = (product: Product) => {
    const clone = {
      ...product,
      name: `${product.name} (สำเนา)`,
      sku: `${product.sku}-COPY`,
      sales: 0,
    };
    setProducts((items) => [...items, clone]);
    notify(`ทำสำเนา ${product.name} แล้ว`);
  };

  return (
    <main className="merchant-shell pm-shell">
      <header className="merchant-topbar">
        <Brand />
        <form
          onSubmit={(event) => {
            event.preventDefault();
            notify(query ? `ค้นหา “${query}” แล้ว` : "กรุณาพิมพ์คำค้นหา");
          }}
        >
          <Search />
          <input
            aria-label="ค้นหาทั้งระบบ"
            placeholder="ค้นหาสินค้า, คำสั่งซื้อ, ลูกค้า..."
          />
        </form>
        <LanguageSwitcher /> <button className="merchant-bell" aria-label="การแจ้งเตือน">
          <Bell />
          <i>3</i>
        </button>
        <MerchantShopMenu />
      </header>

      <aside className="merchant-sidebar pm-sidebar">
        <nav>
          {menu.map(([Icon, label, href, badge]) => (
            <a
              className={label === "จัดการสินค้า" ? "active" : ""}
              href={href}
              key={label}
              onClick={(event) => {
                if ((href as string) === "#") {
                  event.preventDefault();
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

      <section className="pm-content">
        <header className="pm-heading">
          <div>
            <h1>จัดการสินค้า</h1>
            <p>เพิ่ม แก้ไข จัดหมวดหมู่ และดูสินค้าทั้งหมดในร้านของคุณ</p>
          </div>
          <div className="pm-actions">
            <button onClick={() => notify("เปิดหน้าต่างนำเข้าสินค้า")}>
              <Upload />
              นำเข้าสินค้า
            </button>
            <button onClick={() => notify("ส่งออกข้อมูลสินค้าเป็น Excel แล้ว")}>
              <Download />
              ส่งออก (Excel)
            </button>
            <button
              className="primary"
              onClick={() => notify("เปิดแบบฟอร์มเพิ่มสินค้าใหม่")}
            >
              <Plus />
              เพิ่มสินค้าใหม่
            </button>
          </div>
        </header>

        <section className="pm-kpis">
          <article className="all">
            <span>
              <Package />
            </span>
            <div>
              <small>สินค้าทั้งหมด</small>
              <b>
                {products.length === 8 ? "156" : 148 + products.length}
                <i>↑ 12%</i>
              </b>
              <em>จากเดือนก่อน</em>
            </div>
          </article>
          <article className="ready">
            <span />
            <div>
              <small>สินค้าพร้อมขาย</small>
              <b>
                142<i>91%</i>
              </b>
              <em>แสดงบนร้านค้า</em>
            </div>
          </article>
          <article className="low">
            <span />
            <div>
              <small>สินค้าสต็อกต่ำ</small>
              <b>
                8<i>5%</i>
              </b>
              <em>ต่ำกว่า 10 ชิ้น</em>
            </div>
          </article>
          <article className="empty">
            <span />
            <div>
              <small>สินค้าหมดสต็อก</small>
              <b>
                {products.filter((p) => p.stock === 0).length + 4}
                <i>4%</i>
              </b>
              <em>ไม่แสดงบนร้านค้า</em>
            </div>
          </article>
        </section>

        <section className="pm-table-card">
          <form
            className="pm-filters"
            onSubmit={(event) => {
              event.preventDefault();
              notify(`พบสินค้า ${visible.length} รายการ`);
            }}
          >
            <label className="pm-search">
              <Search />
              <input
                aria-label="ค้นหาสินค้า"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="ค้นหาชื่อสินค้า, SKU, หมวดหมู่..."
              />
            </label>
            <label>
              หมวดหมู่
              <select
                aria-label="หมวดหมู่"
                value={category}
                onChange={(event) => setCategory(event.target.value)}
              >
                <option>ทั้งหมด</option>
                {[...new Set(products.map((p) => p.category))].map((item) => (
                  <option key={item}>{item}</option>
                ))}
              </select>
            </label>
            <label>
              สถานะ
              <select
                aria-label="สถานะ"
                value={status}
                onChange={(event) => setStatus(event.target.value)}
              >
                <option>ทั้งหมด</option>
                <option>พร้อมขาย</option>
                <option>หมดสต็อก</option>
              </select>
            </label>
            <label>
              สต็อก
              <select
                aria-label="สต็อก"
                value={stock}
                onChange={(event) => setStock(event.target.value)}
              >
                <option>ทั้งหมด</option>
                <option>ต่ำกว่า 10</option>
                <option>หมดสต็อก</option>
              </select>
            </label>
            <label>
              ช่วงราคา
              <select aria-label="ช่วงราคา">
                <option>ทั้งหมด</option>
                <option>ต่ำกว่า ฿200</option>
                <option>฿200 - ฿500</option>
              </select>
            </label>
            <div>
              <button type="button" onClick={reset}>
                รีเซ็ต
              </button>
              <button className="primary">ค้นหา</button>
            </div>
          </form>
          <div className="pm-table-wrap">
            <table>
              <thead>
                <tr>
                  <th>
                    <input
                      aria-label="เลือกสินค้าทั้งหมด"
                      type="checkbox"
                      checked={
                        visible.length > 0 && selected.length === visible.length
                      }
                      onChange={toggleAll}
                    />
                  </th>
                  <th>สินค้า</th>
                  <th>SKU</th>
                  <th>หมวดหมู่</th>
                  <th>ราคา</th>
                  <th>สต็อก</th>
                  <th>สถานะ</th>
                  <th>ยอดขาย (30 วัน)</th>
                  <th>วันที่อัปเดต</th>
                  <th>จัดการ</th>
                </tr>
              </thead>
              <tbody>
                {visible.map((product) => (
                  <tr key={product.sku}>
                    <td>
                      <input
                        aria-label={`เลือก ${product.name}`}
                        type="checkbox"
                        checked={selected.includes(product.sku)}
                        onChange={() =>
                          setSelected((items) =>
                            items.includes(product.sku)
                              ? items.filter((sku) => sku !== product.sku)
                              : [...items, product.sku],
                          )
                        }
                      />
                    </td>
                    <td>
                      <div className="pm-product">
                        <ProductThumb product={product} />
                        <p>
                          <b>{product.name}</b>
                          <small>{product.detail}</small>
                        </p>
                      </div>
                    </td>
                    <td>{product.sku}</td>
                    <td>{product.category}</td>
                    <td>
                      <b>฿ {product.price}</b>
                    </td>
                    <td
                      className={
                        product.stock === 0
                          ? "stock-zero"
                          : product.stock < 10
                            ? "stock-low"
                            : "stock-ok"
                      }
                    >
                      {product.stock}
                    </td>
                    <td>
                      <span
                        className={
                          product.stock === 0
                            ? "pm-status empty"
                            : product.stock < 10
                              ? "pm-status low"
                              : "pm-status ready"
                        }
                      >
                        {product.stock === 0
                          ? "หมดสต็อก"
                          : product.stock < 10
                            ? "สต็อกต่ำ"
                            : "พร้อมขาย"}
                      </span>
                    </td>
                    <td>{product.sales}</td>
                    <td>
                      {product.updated.split("\n").map((part) => (
                        <span key={part}>{part}</span>
                      ))}
                    </td>
                    <td>
                      <div className="pm-row-actions">
                        <button
                          aria-label={`แก้ไข ${product.name}`}
                          onClick={() => notify(`กำลังแก้ไข ${product.name}`)}
                        >
                          <Pencil />
                        </button>
                        <button
                          aria-label={`ทำสำเนา ${product.name}`}
                          onClick={() => duplicate(product)}
                        >
                          <Copy />
                        </button>
                        <button
                          className="delete"
                          aria-label={`ลบ ${product.name}`}
                          onClick={() => remove(product)}
                        >
                          <Trash2 />
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {visible.length === 0 && (
              <div className="pm-empty">ไม่พบสินค้าที่ตรงกับตัวกรอง</div>
            )}
          </div>
          <div className="pm-pagination">
            <span>แสดง 1 - {visible.length} จาก 156 รายการ</span>
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
              <button>20</button>
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
