import { useMemo, useState } from "react";
import {
  ArrowRight,
  ChevronDown,
  Heart,
  Headphones,
  Menu,
  Minus,
  PackageCheck,
  Search,
  ShieldCheck,
  ShoppingBag,
  ShoppingCart,
  Store,
  Tag,
  Trash2,
  Truck,
  UserRound,
} from "lucide-react";
import { useAuth } from "../contexts/AuthContext";
import { accountKind } from "../services/permissions";
import cartReference from "../assets/images/references/storefront/customer-shopping.png";
import "../styles/cart.css";

type CartItem = {
  id: number;
  name: string;
  shop: string;
  price: number;
  old: number;
  discount: number;
  quantity: number;
  crop: string;
};

const initialItems: CartItem[] = [
  { id: 1, name: "เม็ดมะม่วงหิมพานต์ อบกรอบ รส original 200 กรัม", shop: "ร้านแม่สมใจ ของดีไทย", price: 89, old: 120, discount: 31, quantity: 2, crop: "cart-crop-nut" },
  { id: 2, name: "ต้นมอนสเตอร่า กระถางเซรามิก", shop: "ร้านสวนดีดี", price: 350, old: 450, discount: 100, quantity: 1, crop: "cart-crop-plant" },
  { id: 3, name: "กระบอกน้ำเก็บอุณหภูมิ 500 ml (สีขาว)", shop: "Thai Lifestyle", price: 690, old: 890, discount: 110, quantity: 1, crop: "cart-crop-cup" },
];

const suggestions = [
  ["ถั่วลิสงอบกรอบ", 59, 75, "4.8", "cart-crop-rec1"],
  ["หมอนรองคอเพื่อสุขภาพ", 299, 450, "4.7", "cart-crop-rec2"],
  ["เซ็ตสกินแคร์ออร์แกนิก", 490, 650, "4.9", "cart-crop-rec3"],
  ["กระเป๋าผ้าแคนวาส", 290, 350, "4.8", "cart-crop-rec4"],
  ["เครื่องพ่นอโรม่า", 890, 1290, "4.6", "cart-crop-rec5"],
] as const;

function CartBrand() {
  return <a className="cart-brand" href="/"><span><ShoppingBag/><i>✓</i></span><p><b>ThaiShop<em>Fun</em></b><small>Shop Local · Support Thai · Smile Together</small></p></a>;
}

function Crop({ className, alt }: { className: string; alt: string }) {
  return <span className={`cart-photo ${className}`}><img src={cartReference} alt={alt}/></span>;
}

export default function CartPage() {
  const { user } = useAuth();
  const [items, setItems] = useState(initialItems);
  const [selected, setSelected] = useState<number[]>(initialItems.map(item => item.id));
  const [coupon, setCoupon] = useState("");
  const [couponApplied, setCouponApplied] = useState(false);
  const [notice, setNotice] = useState("");
  const selectedItems = items.filter(item => selected.includes(item.id));
  const subtotal = selectedItems.reduce((sum, item) => sum + item.old * item.quantity, 0);
  const productDiscount = selectedItems.reduce((sum, item) => sum + (item.old - item.price) * item.quantity, 0);
  const couponDiscount = couponApplied ? Math.round((subtotal - productDiscount) * .1) : 0;
  const productTotal = Math.max(0, subtotal - productDiscount - couponDiscount);
  const shipping = selectedItems.length ? 40 : 0;
  const grandTotal = productTotal + shipping;
  const accountName = user ? `${user.firstName} ${user.lastName}`.trim() || user.email : "";

  const notify = (message: string) => {
    setNotice(message);
    window.setTimeout(() => setNotice(""), 2000);
  };
  const setQuantity = (id: number, difference: number) => setItems(current => current.map(item => item.id === id ? { ...item, quantity: Math.max(1, item.quantity + difference) } : item));
  const remove = (ids: number[]) => {
    setItems(current => current.filter(item => !ids.includes(item.id)));
    setSelected(current => current.filter(id => !ids.includes(id)));
    notify("ลบสินค้าออกจากตะกร้าแล้ว");
  };
  const toggle = (id: number) => setSelected(current => current.includes(id) ? current.filter(value => value !== id) : [...current, id]);
  const allSelected = items.length > 0 && selected.length === items.length;
  const applyCoupon = () => {
    if (coupon.trim().toUpperCase() === "THAI10") {
      setCouponApplied(true);
      notify("ใช้โค้ด THAI10 สำเร็จ");
    } else {
      setCouponApplied(false);
      notify("ไม่พบโค้ดส่วนลดนี้ ลองใช้ THAI10");
    }
  };

  const summary = useMemo(() => ({ subtotal, productDiscount: productDiscount + couponDiscount, productTotal, grandTotal }), [subtotal, productDiscount, couponDiscount, productTotal, grandTotal]);

  return <main className="cart-page">
    <header className="cart-header">
      <div className="cart-top cart-width">
        <CartBrand/>
        <form onSubmit={event => { event.preventDefault(); notify("กำลังค้นหาสินค้า"); }}><input aria-label="ค้นหาสินค้า" placeholder="ค้นหาสินค้า แบรนด์ หรือร้านค้า..."/><button aria-label="ค้นหา"><Search/></button></form>
        <div className="cart-account">
          {user ? <a href={accountKind(user) === "ADMIN" ? "/admin" : accountKind(user) === "MERCHANT" ? "/merchant/dashboard" : "/"}><UserRound/><span><b>{accountName}</b><small>{accountKind(user) === "CUSTOMER" ? "ลูกค้า" : "บัญชีของฉัน"}</small></span></a> : <><a href="/login"><UserRound/>เข้าสู่ระบบ</a><a className="cart-signup" href="/signup">สมัครสมาชิก</a></>}
          <a className="cart-icon" href="/cart" aria-label="ตะกร้าสินค้า"><ShoppingCart/><i>{items.length}</i></a>
        </div>
      </div>
      <nav className="cart-nav cart-width"><a href="/"><Menu/>หมวดหมู่สินค้า</a><a href="/deals">ดีลพิเศษ</a><a href="/recommendations">สินค้าแนะนำ</a><a href="/shops">ร้านค้าแนะนำ</a><a href="/best-sellers">สินค้าขายดี</a><a href="/thai-brands">แบรนด์ไทย</a><a href="/articles">บทความ</a><a href="/help">ช่วยเหลือ</a></nav>
    </header>

    <div className="cart-heading cart-width"><a href="/">← กลับไปช้อปปิ้ง</a><ol aria-label="ขั้นตอนการสั่งซื้อ"><li aria-current="step"><span>1</span> ตะกร้าสินค้า</li><li><span>2</span> ชำระเงิน</li><li><span>3</span> เสร็จสิ้น</li></ol></div>
    <div className="cart-page-title cart-width"><div className="cart-title"><ShoppingCart/><div><h1>ตะกร้าสินค้า <span>({items.length} รายการ)</span></h1><p>ตรวจสอบรายการสินค้า ก่อนดำเนินการสั่งซื้อ</p></div></div></div>
    <section className="cart-content cart-width">
      <div className="cart-main-column">

        <div className="cart-select-row"><label><input type="checkbox" checked={allSelected} onChange={() => setSelected(allSelected ? [] : items.map(item => item.id))}/>เลือกสินค้าทั้งหมด</label><button disabled={!selected.length} onClick={() => remove(selected)}><Trash2/>ลบสินค้าที่เลือก</button></div>
        <section className="cart-list">
          {items.map(item => <article key={item.id} className={selected.includes(item.id) ? "cart-item-selected" : ""}>
            <input aria-label={`เลือก ${item.name}`} type="checkbox" checked={selected.includes(item.id)} onChange={() => toggle(item.id)}/>
            <Crop className={item.crop} alt={item.name}/>
            <div className="cart-item-copy"><h2>{item.name}</h2><p><Store/>{item.shop}</p><em>พร้อมส่ง</em></div>
            <div className="cart-unit-price"><b>฿{item.price.toLocaleString()}</b><del>฿{item.old.toLocaleString()}</del><em>-{Math.round((1-item.price/item.old)*100)}%</em></div>
            <div className="cart-quantity"><button aria-label="ลดจำนวน" disabled={item.quantity <= 1} onClick={() => setQuantity(item.id, -1)}><Minus/></button><b>{item.quantity}</b><button aria-label="เพิ่มจำนวน" onClick={() => setQuantity(item.id, 1)}>＋</button></div>
            <strong className="cart-line-total">฿{(item.price * item.quantity).toLocaleString()}</strong>
            <button className="cart-remove" aria-label={`ลบ ${item.name}`} onClick={() => remove([item.id])}><Trash2/></button>
          </article>)}
          {!items.length && <div className="cart-empty"><ShoppingCart/><h2>ยังไม่มีสินค้าในตะกร้า</h2><a href="/recommendations">เลือกซื้อสินค้า</a></div>}

        </section>


      </div>

      <aside className="cart-side">
        <section className="cart-summary">
          <span className="cart-summary-kicker">พร้อมช้อปของที่ถูกใจ</span><h2>สรุปคำสั่งซื้อ</h2>
        <section className="cart-code"><h2><Tag/>โค้ดส่วนลดพิเศษ</h2><p>กรอกโค้ดเพื่อรับส่วนลด</p><div><input id="coupon" aria-label="โค้ดส่วนลด" value={coupon} onChange={event => setCoupon(event.target.value)} placeholder="กรอกโค้ดส่วนลด"/><button onClick={applyCoupon}>ใช้โค้ด</button></div></section>
          <p><span>ราคาปกติ ({selectedItems.length} รายการ)</span><b>฿{summary.subtotal.toLocaleString()}</b></p>
          <p><span>ส่วนลดสินค้า</span><b className="cart-saving">−฿{productDiscount.toLocaleString()}</b></p>
          {couponApplied && <p><span>โค้ด THAI10 <button className="cart-clear-code" onClick={()=>setCouponApplied(false)}>ยกเลิก</button></span><b className="cart-saving">−฿{couponDiscount.toLocaleString()}</b></p>}
          <p><span>ยอดรวมสินค้า</span><b>฿{summary.productTotal.toLocaleString()}</b></p>
          <p><span>ค่าจัดส่งโดยประมาณ</span><b>฿{shipping.toLocaleString()}</b></p><hr/>
          <p className="cart-grand"><span>ยอดรวมทั้งสิ้น</span><b aria-live="polite">฿{summary.grandTotal.toLocaleString()}</b></p>
          <div className="cart-savings-note"><Tag size={14}/> ประหยัดไป ฿{summary.productDiscount.toLocaleString()}</div>
          <button disabled={!selectedItems.length} onClick={() => notify("หน้าชำระเงินยังไม่เปิดให้บริการ")}>ดำเนินการสั่งซื้อ <ArrowRight/></button>
          <small className="cart-checkout-note"><ShieldCheck size={14}/> ตรวจสอบค่าจัดส่งอีกครั้งก่อนชำระเงิน</small>
        </section>


      </aside>
    </section>

    <div className="cart-width cart-recommend-wrap"><section className="cart-recommend"><header><h2>สินค้าแนะนำสำหรับคุณ</h2><a href="/recommendations">ดูทั้งหมด <ArrowRight/></a></header><div>
          {suggestions.map(([name, price, old, rating, crop]) => <article key={name}><Crop className={crop} alt={name}/><button aria-label="เพิ่มรายการโปรด"><Heart/></button><h3>{name}</h3><p><b>฿{price.toLocaleString()}</b><del>฿{old.toLocaleString()}</del><em>-{Math.round((1-price/old)*100)}%</em></p><small>★ {rating}</small></article>)}
        </div></section></div>

    <footer className="cart-footer"><div className="cart-width"><p><Heart/><span><b>ของดีไทย</b><small>ส่งถึงบ้านคุณ</small></span></p><p><ShieldCheck/><span><b>ชำระเงินปลอดภัย</b><small>SSL Secured</small></span></p><p><Truck/><span><b>จัดส่งทั่วไทย</b><small>Kerry / Flash / ไปรษณีย์</small></span></p><p><PackageCheck/><span><b>คืนสินค้าได้</b><small>ภายใน 7 วัน</small></span></p><p><Headphones/><span><b>ต้องการความช่วยเหลือ?</b><small>ติดต่อทีมงาน 02-123-4567</small></span><a href="/help">แชทกับเรา</a></p></div></footer>
    {notice && <div className="cart-toast" role="status">{notice}</div>}
  </main>;
}
