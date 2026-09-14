import { useMemo, useState } from "react";
import {
  Box,
  ChevronDown,
  ChevronRight,
  CircleUserRound,
  CreditCard,
  Headphones,
  LogOut,
  Mail,
  Menu,
  MessageCircle,
  PackageCheck,
  Phone,
  RotateCcw,
  Search,
  Settings,
  ShieldCheck,
  ShoppingBag,
  ShoppingCart,
  Store,
  Truck,
  UserRound,
} from "lucide-react";
import { useAuth } from "./AuthContext";
import helpReference from "../help.png";
import "./recommended-products.css";
import "./best-seller.css";
import "./help.css";

const topics = [
  {
    icon: ShoppingCart,
    title: "การสั่งซื้อ",
    text: "วิธีสั่งซื้อ ยกเลิกคำสั่งซื้อ",
    tone: "coral",
  },
  {
    icon: CreditCard,
    title: "การชำระเงิน",
    text: "ช่องทางการชำระเงิน\nออกใบกำกับภาษี",
    tone: "blue",
  },
  {
    icon: Truck,
    title: "การจัดส่งสินค้า",
    text: "ตรวจสอบสถานะ\nระยะเวลาจัดส่ง",
    tone: "green",
  },
  {
    icon: Box,
    title: "การคืนสินค้า\nและคืนเงิน",
    text: "เงื่อนไขการคืนสินค้า",
    tone: "violet",
  },
  {
    icon: Store,
    title: "สำหรับร้านค้า",
    text: "สมัครร้านค้า จัดการสินค้า\nการรับเงิน",
    tone: "amber",
  },
  {
    icon: UserRound,
    title: "บัญชีผู้ใช้งาน",
    text: "สมัครสมาชิก\nจัดการข้อมูลส่วนตัว",
    tone: "pink",
  },
  {
    icon: ShieldCheck,
    title: "ความปลอดภัย",
    text: "ความปลอดภัยของข้อมูล\nและการป้องกันการฉ้อโกง",
    tone: "mint",
  },
  {
    icon: Settings,
    title: "ปัญหาการใช้งาน",
    text: "แจ้งปัญหา\nและข้อเสนอแนะ",
    tone: "purple",
  },
];

const faqs = [
  [
    "วิธีสั่งซื้อสินค้าใน ThaiShopFun ทำอย่างไร?",
    "เลือกสินค้า กดเพิ่มลงตะกร้า ตรวจสอบที่อยู่ เลือกวิธีชำระเงิน และยืนยันคำสั่งซื้อ",
  ],
  [
    "มีช่องทางการชำระเงินอะไรบ้าง?",
    "รองรับ QR Payment, บัตรเครดิตหรือเดบิต, โอนเงินผ่านธนาคาร และกระเป๋าเงินดิจิทัล",
  ],
  [
    "ใช้เวลาจัดส่งสินค้ากี่วัน?",
    "โดยทั่วไปใช้เวลา 1–3 วันทำการ ทั้งนี้ขึ้นอยู่กับร้านค้าและพื้นที่จัดส่ง",
  ],
  [
    "สามารถยกเลิกคำสั่งซื้อได้หรือไม่?",
    "ยกเลิกได้ก่อนร้านค้าเริ่มจัดเตรียมสินค้า ตรวจสอบสถานะได้จากหน้าคำสั่งซื้อ",
  ],
  [
    "วิธีขอคืนสินค้าและรับเงินคืน",
    "เปิดคำสั่งซื้อ เลือกขอคืนสินค้า ระบุเหตุผลและแนบรูป ทีมงานจะตรวจสอบตามเงื่อนไข",
  ],
  [
    "วิธีสมัครเป็นร้านค้า",
    "ไปที่หน้าสมัครเป็นร้านค้า กรอกข้อมูลร้านและเจ้าของร้าน จากนั้นส่งเอกสารเพื่อตรวจสอบ",
  ],
  [
    "ต้องการออกใบกำกับภาษี ทำอย่างไร?",
    "กรอกข้อมูลสำหรับออกใบกำกับภาษีในขั้นตอนชำระเงิน หรือแจ้งร้านค้าผ่านรายละเอียดคำสั่งซื้อ",
  ],
  [
    "ติดต่อเจ้าหน้าที่ได้ช่องทางไหนบ้าง?",
    "ติดต่อได้ผ่านแชท LINE Official อีเมล support@thaishopfun.com หรือโทร 02-123-4567",
  ],
];

const guides = [
  {
    title: "คู่มือการสั่งซื้อสินค้า",
    text: "ขั้นตอนการสั่งซื้อ ตั้งแต่เลือกสินค้า\nจนถึงชำระเงิน",
    crop: "guide-one",
  },
  {
    title: "คู่มือสำหรับร้านค้า",
    text: "วิธีสมัครร้านค้า ลงสินค้า\nและจัดการออเดอร์",
    crop: "guide-two",
  },
  {
    title: "คู่มือติดตามการจัดส่ง",
    text: "วิธีตรวจสอบสถานะพัสดุ\nและระยะเวลาการจัดส่ง",
    crop: "guide-three",
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

export default function HelpPage() {
  const { user, loading, logout } = useAuth();
  const [query, setQuery] = useState("");
  const [openFaq, setOpenFaq] = useState<number | null>(null);
  const [activeTopic, setActiveTopic] = useState(0);
  const [notice, setNotice] = useState("");
  const [accountOpen, setAccountOpen] = useState(false);
  const notify = (message: string) => {
    setNotice(message);
    window.setTimeout(() => setNotice(""), 2200);
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
  const visibleFaqs = useMemo(() => {
    const term = query.trim().toLowerCase();
    return term
      ? faqs.filter(([question, answer]) =>
          `${question} ${answer}`.toLowerCase().includes(term),
        )
      : faqs;
  }, [query]);
  const submitSearch = (event: React.FormEvent) => {
    event.preventDefault();
    notify(
      query.trim()
        ? `พบคำตอบที่เกี่ยวข้อง ${visibleFaqs.length} รายการ`
        : "กรุณาพิมพ์คำถามที่ต้องการค้นหา",
    );
  };
  const signOut = async () => {
    await logout();
    setAccountOpen(false);
    notify("ออกจากระบบแล้ว");
  };

  return (
    <main className="help-page best-seller-page recommend-page">
      <header className="recommend-header">
        <div className="recommend-top help-head-width">
          <Brand />
          <form onSubmit={submitSearch}>
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="ค้นหาสินค้า แบรนด์ หรือร้านค้า..."
              aria-label="ค้นหาเว็บไซต์"
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
              onClick={() => notify("ยังไม่มีสินค้าในตะกร้า")}
            >
              <ShoppingCart />
              <i>0</i>
            </button>
          </div>
        </div>
        <nav className="recommend-nav help-head-width">
          <a href="/">
            <Menu />
            หมวดหมู่สินค้า
          </a>
          <a href="/deals">ดีลพิเศษ</a>
          <a href="/recommendations">สินค้าแนะนำ</a>
          <a href="/best-sellers">สินค้าขายดี</a>
          <a href="/thai-brands">แบรนด์ไทย</a>
          <a href="/articles">บทความ</a>
          <a className="active" href="/help">
            ช่วยเหลือ
          </a>
        </nav>
      </header>

      <section className="help-hero">
        <img src={helpReference} alt="ศูนย์ช่วยเหลือ ThaiShopFun" />
        <form onSubmit={submitSearch}>
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            aria-label="ค้นหาคำถาม"
          />
          <button aria-label="ค้นหาคำถาม" />
        </form>
      </section>

      <section className="help-width help-topics">
        <h1>เลือกหัวข้อที่ต้องการความช่วยเหลือ</h1>
        <div>
          {topics.map(({ icon: Icon, title, text, tone }, i) => (
            <button
              className={`${tone} ${activeTopic === i ? "active" : ""}`}
              key={title}
              onClick={() => {
                setActiveTopic(i);
                notify(`เลือกหัวข้อ ${title.replace("\n", " ")}`);
              }}
            >
              <Icon />
              <b>
                {title.split("\n").map((line, j) => (
                  <span key={j}>{line}</span>
                ))}
              </b>
              <small>
                {text.split("\n").map((line, j) => (
                  <span key={j}>{line}</span>
                ))}
              </small>
            </button>
          ))}
        </div>
      </section>

      <section className="help-width help-columns">
        <section className="faq-panel">
          <div className="help-section-title">
            <h2>คำถามที่พบบ่อย</h2>
            <button onClick={() => setQuery("")}>ดูคำถามทั้งหมด →</button>
          </div>
          <div className="faq-list">
            {visibleFaqs.map(([question, answer], i) => (
              <article key={question}>
                <button
                  aria-expanded={openFaq === i}
                  onClick={() => setOpenFaq(openFaq === i ? null : i)}
                >
                  <i>{i + 1}</i>
                  <span>{question}</span>
                  <ChevronDown />
                </button>
                {openFaq === i && <p>{answer}</p>}
              </article>
            ))}
            {visibleFaqs.length === 0 && (
              <p className="empty-faq">
                ไม่พบคำตอบ ลองใช้คำค้นอื่นหรือติดต่อทีมงาน
              </p>
            )}
          </div>
        </section>
        <section className="guide-panel">
          <div className="help-section-title">
            <h2>คู่มือการใช้งาน</h2>
            <button onClick={() => notify("แสดงคู่มือทั้งหมด")}>
              ดูคู่มือทั้งหมด →
            </button>
          </div>
          {guides.map((guide, i) => (
            <button
              className="guide-item"
              key={guide.title}
              onClick={() => notify(`เปิด${guide.title}`)}
            >
              <span className={`guide-photo ${guide.crop}`}>
                <img src={helpReference} alt="" />
              </span>
              <span>
                <b>{guide.title}</b>
                <small>
                  {guide.text.split("\n").map((line, j) => (
                    <span key={j}>{line}</span>
                  ))}
                </small>
              </span>
              <ChevronRight />
            </button>
          ))}
        </section>
        <section className="contact-panel">
          <div className="help-section-title">
            <div>
              <h2>ติดต่อเรา</h2>
              <p>หากยังไม่ได้คำตอบ ทีมงานพร้อมช่วยเหลือคุณ</p>
            </div>
          </div>
          <div className="contact-grid">
            <article>
              <MessageCircle />
              <div>
                <b>แชทกับเรา</b>
                <small>จ.–อา. 08:00 - 22:00</small>
                <button onClick={() => notify("กำลังเปิดแชท")}>
                  เริ่มแชทเลย →
                </button>
              </div>
            </article>
            <article className="line">
              <b className="line-logo">LINE</b>
              <div>
                <b>LINE Official</b>
                <small>@thaishopfun</small>
                <button onClick={() => notify("กำลังเปิด LINE Official")}>
                  เพิ่มเพื่อน →
                </button>
              </div>
            </article>
            <article>
              <Mail />
              <div>
                <b>ส่งอีเมล</b>
                <small>support@thaishopfun.com</small>
                <a href="mailto:support@thaishopfun.com">ส่งอีเมล →</a>
              </div>
            </article>
            <article>
              <Phone />
              <div>
                <b>โทรสอบถาม</b>
                <small>
                  02-123-4567
                  <br />
                  จ.–ศ. 08:00 - 18:00
                </small>
                <a href="tel:021234567">โทรเลย →</a>
              </div>
            </article>
          </div>
        </section>
      </section>
      {notice && (
        <div className="recommend-toast" role="status">
          {notice}
        </div>
      )}
    </main>
  );
}
