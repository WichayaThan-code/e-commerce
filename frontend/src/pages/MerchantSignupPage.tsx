import { useRef, useState } from "react";
import {
  ArrowRight,
  BarChart3,
  Camera,
  Check,
  CheckCircle2,
  FileCheck2,
  Globe2,
  Headphones,
  Link2,
  Megaphone,
  PackageCheck,
  Search,
  ShieldCheck,
  ShoppingBag,
  Store,
  TrendingUp,
  Upload,
  UsersRound,
} from "lucide-react";
import { useAuth } from "../contexts/AuthContext";
import { ApiError, merchantApi, type MerchantShopPayload } from "../services/api";
import "../styles/merchant-signup.css";

const merchantBenefits = [
  {
    icon: Store,
    tone: "green",
    title: "เริ่มขายได้ทันที",
    text: "สมัครง่าย อนุมัติรวดเร็ว",
  },
  {
    icon: BarChart3,
    tone: "orange",
    title: "ค่าธรรมเนียมชัดเจน",
    text: "เพียง 5% เมื่อขายได้เท่านั้น",
  },
  {
    icon: Megaphone,
    tone: "blue",
    title: "เข้าถึงลูกค้าจำนวนมาก",
    text: "ทั่วประเทศและต่างประเทศ",
  },
  {
    icon: Headphones,
    tone: "red",
    title: "ทีมงานช่วยดูแล",
    text: "พร้อมให้คำปรึกษาตลอดการขาย",
  },
  {
    icon: TrendingUp,
    tone: "purple",
    title: "เครื่องมือครบ",
    text: "จัดการร้านค้า วิเคราะห์ยอดขาย",
  },
];

const steps = [
  ["1", "ข้อมูลร้านค้า"],
  ["2", "ข้อมูลเจ้าของร้าน"],
  ["3", "เอกสารยืนยัน"],
  ["4", "ตรวจสอบและส่ง"],
];

function MerchantBrand() {
  return (
    <a
      className="brand merchant-brand"
      href="/"
      aria-label="ThaiShopFun หน้าแรก"
    >
      <span className="brand-mark">
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

export default function MerchantSignupPage() {
  const { user } = useAuth();
  const fileRef = useRef<HTMLInputElement>(null);
  const [logo, setLogo] = useState("");
  const [logoFile, setLogoFile] = useState<File | null>(null);
  const [saleType, setSaleType] = useState<"owner" | "reseller">("owner");
  const [message, setMessage] = useState("");
  const [saving, setSaving] = useState(false);

  const chooseLogo = (file?: File) => {
    if (!file) return;
    if (file.size > 2 * 1024 * 1024) {
      setMessage("ไฟล์โลโก้ต้องมีขนาดไม่เกิน 2 MB");
      return;
    }
    if (!["image/jpeg", "image/png"].includes(file.type)) {
      setMessage("รองรับเฉพาะไฟล์ JPG หรือ PNG");
      return;
    }
    setLogo(URL.createObjectURL(file));
    setLogoFile(file);
    setMessage("");
  };

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!user) {
      window.location.assign("/login?next=/merchant/register");
      return;
    }
    if (!logoFile) {
      setMessage("กรุณาอัปโหลดโลโก้ร้านค้า");
      return;
    }
    const data = Object.fromEntries(
      new FormData(event.currentTarget).entries(),
    ) as Record<string, string>;
    const payload: MerchantShopPayload = {
      shopName: data.shopName || "",
      shopNameEn: data.shopNameEnglish || "",
      description: data.description || "",
      category: data.category || "",
      saleType: saleType === "owner" ? "OWNER" : "RESELLER",
      website: data.website || "",
      address: data.address || "",
      province: data.province || "",
      district: data.district || "",
      subdistrict: data.subdistrict || "",
      postalCode: data.postalCode || "",
    };
    setSaving(true);
    setMessage("กำลังบันทึกข้อมูลร้านค้า...");
    try {
      await merchantApi.saveShop(payload);
      await merchantApi.uploadDocument("LOGO", logoFile);
      window.location.assign("/merchant/owner-info");
    } catch (error) {
      setMessage(
        error instanceof ApiError
          ? error.message
          : "ไม่สามารถบันทึกข้อมูลร้านค้าได้",
      );
      setSaving(false);
    }
  };

  return (
    <main className="merchant-page">
      <header className="merchant-header">
        <MerchantBrand />
        <nav>
          <a href="/">หน้าหลัก</a>
          <a href="/#recommended">สินค้า</a>
          <a href="/#shops">ร้านค้า</a>
          <a className="active" href="/merchant/register">
            สมัครเป็นร้านค้า
          </a>
          <a href="/help">ช่วยเหลือ</a>
        </nav>
        <div className="merchant-actions">
          <button aria-label="ค้นหา">
            <Search />
          </button>
          <label>
            <span className="mini-flag" />
            <select aria-label="เลือกภาษา" defaultValue="th">
              <option value="th">ไทย</option>
              <option value="en">English</option>
            </select>
          </label>
          <a href={user ? "/dashboard" : "/login"}>
            {user ? "บัญชีของฉัน" : "เข้าสู่ระบบ"}
          </a>
        </div>
      </header>

      <div className="merchant-layout">
        <section className="merchant-story">
          <div className="merchant-story-shade" />
          <div className="merchant-copy">
            <h1>
              เปิดร้านกับ
              <br />
              <span>ThaiShopFun</span>
            </h1>
            <h2>เริ่มขายง่าย ขยายธุรกิจได้ไกล</h2>
            <p>
              แพลตฟอร์มตลาดออนไลน์ของคนไทย
              <br />
              ที่พร้อมสนับสนุนผู้ขายไทย
            </p>
            <div className="merchant-benefits">
              {merchantBenefits.map(({ icon: Icon, tone, title, text }) => (
                <div key={title}>
                  <span className={tone}>
                    <Icon />
                  </span>
                  <p>
                    <b>{title}</b>
                    <small>{text}</small>
                  </p>
                </div>
              ))}
            </div>
            <blockquote>
              “ ขายของไทย
              <br />
              ให้ไปได้ไกลกว่าเดิม ”<i />
            </blockquote>
          </div>
          <div className="merchant-stats">
            <div>
              <UsersRound />
              <p>
                <b>100,000+</b>
                <span>ลูกค้าที่ไว้วางใจ</span>
              </p>
            </div>
            <div>
              <Store />
              <p>
                <b>5,000+</b>
                <span>ร้านค้าไทย</span>
              </p>
            </div>
            <div>
              <Globe2 />
              <p>
                <b>ส่งไกลได้</b>
                <span>ทั่วไทยและต่างประเทศ</span>
              </p>
            </div>
          </div>
        </section>

        <section className="merchant-form-side">
          <div className="merchant-card">
            <div className="merchant-card-title">
              <h2>สมัครเป็นร้านค้า</h2>
              <p>เริ่มต้นขายกับ ThaiShopFun ในไม่กี่ขั้นตอน</p>
            </div>
            <div className="merchant-steps">
              {steps.map(([number, label], index) => (
                <div className={index === 0 ? "active" : ""} key={number}>
                  <span>{number}</span>
                  <small>{label}</small>
                </div>
              ))}
            </div>
            <form onSubmit={submit} noValidate>
              <h3>ข้อมูลร้านค้า</h3>
              <div className="merchant-two">
                <label>
                  ชื่อร้านค้า <em>*</em>
                  <input
                    name="shopName"
                    maxLength={100}
                    placeholder="กรอกชื่อร้านค้า"
                  />
                  <small>0/100</small>
                </label>
                <label>
                  ชื่อร้านค้า (ภาษาอังกฤษ)
                  <input
                    name="shopNameEnglish"
                    maxLength={100}
                    placeholder="Shop name (English)"
                  />
                </label>
              </div>
              <div className="merchant-two detail-logo">
                <label>
                  รายละเอียดร้านค้า
                  <textarea
                    name="description"
                    maxLength={500}
                    placeholder="แนะนำร้านค้าของคุณ (สินค้า จุดเด่น ฯลฯ)"
                  />
                  <small>0/500</small>
                </label>
                <label>
                  โลโก้ร้านค้า <em>*</em>
                  <button
                    className="logo-drop"
                    type="button"
                    onClick={() => fileRef.current?.click()}
                    onDragOver={(e) => e.preventDefault()}
                    onDrop={(e) => {
                      e.preventDefault();
                      chooseLogo(e.dataTransfer.files[0]);
                    }}
                  >
                    {logo ? (
                      <img src={logo} alt="ตัวอย่างโลโก้ร้านค้า" />
                    ) : (
                      <>
                        <Camera />
                        <span>ลากไฟล์มาวาง หรือ คลิกเพื่ออัปโหลด</span>
                        <small>
                          ขนาดไฟล์ไม่เกิน 2 MB
                          <br />
                          (ไฟล์ JPG, PNG)
                        </small>
                      </>
                    )}
                  </button>
                  <input
                    ref={fileRef}
                    className="file-input"
                    type="file"
                    accept="image/jpeg,image/png"
                    onChange={(e) => chooseLogo(e.target.files?.[0])}
                  />
                </label>
              </div>
              <div className="merchant-two compact-row">
                <label>
                  หมวดหมู่สินค้า <em>*</em>
                  <select name="category" defaultValue="">
                    <option value="" disabled>
                      เลือกหมวดหมู่
                    </option>
                    <option>แฟชั่นและเสื้อผ้า</option>
                    <option>อาหารและเครื่องดื่ม</option>
                    <option>ความงามและสุขภาพ</option>
                    <option>บ้านและไลฟ์สไตล์</option>
                    <option>สินค้าและงานฝีมือไทย</option>
                  </select>
                </label>
                <label>
                  เว็บไซต์ / โซเชียลมีเดีย (ถ้ามี)
                  <div className="merchant-icon-input">
                    <Link2 />
                    <input name="website" placeholder="https://" />
                  </div>
                </label>
              </div>
              <fieldset>
                <legend>
                  ประเภทการขาย <em>*</em>
                </legend>
                <div className="sale-types">
                  <button
                    type="button"
                    className={saleType === "owner" ? "active" : ""}
                    onClick={() => setSaleType("owner")}
                  >
                    <i>{saleType === "owner" ? <Check /> : null}</i>
                    <PackageCheck />
                    <span>
                      <b>ขายสินค้าของตัวเอง</b>
                      <small>(เจ้าของสินค้า / แบรนด์ของตนเอง)</small>
                    </span>
                  </button>
                  <button
                    type="button"
                    className={saleType === "reseller" ? "active" : ""}
                    onClick={() => setSaleType("reseller")}
                  >
                    <i>{saleType === "reseller" ? <Check /> : null}</i>
                    <UsersRound />
                    <span>
                      <b>ตัวแทนจำหน่าย / รีเซลเลอร์</b>
                      <small>(จำหน่ายสินค้าจากแบรนด์อื่น)</small>
                    </span>
                  </button>
                </div>
              </fieldset>
              <label>
                ที่อยู่ร้านค้า
                <input
                  name="address"
                  placeholder="กรอกที่อยู่สำหรับการติดต่อ / การจัดส่งสินค้า"
                />
              </label>
              <div className="address-grid">
                <label>
                  จังหวัด <em>*</em>
                  <select name="province" defaultValue="">
                    <option value="" disabled>
                      เลือกจังหวัด
                    </option>
                    <option>กรุงเทพมหานคร</option>
                    <option>เชียงใหม่</option>
                    <option>ขอนแก่น</option>
                    <option>ชลบุรี</option>
                  </select>
                </label>
                <label>
                  อำเภอ / เขต <em>*</em>
                  <select name="district" defaultValue="">
                    <option value="" disabled>
                      เลือกอำเภอ
                    </option>
                    <option>เมือง</option>
                    <option>บางรัก</option>
                    <option>จตุจักร</option>
                  </select>
                </label>
                <label>
                  ตำบล / แขวง <em>*</em>
                  <select name="subdistrict" defaultValue="">
                    <option value="" disabled>
                      เลือกตำบล
                    </option>
                    <option>ในเมือง</option>
                    <option>สีลม</option>
                    <option>ลาดยาว</option>
                  </select>
                </label>
                <label>
                  รหัสไปรษณีย์ <em>*</em>
                  <input
                    name="postalCode"
                    inputMode="numeric"
                    maxLength={5}
                    placeholder="กรอกรหัส"
                  />
                </label>
              </div>
              <button className="merchant-next" type="submit" disabled={saving}>
                {saving ? "กำลังบันทึก..." : "ถัดไป"} <ArrowRight />
              </button>
              {message && (
                <p className="merchant-message" role="status">
                  {message}
                </p>
              )}
              <p className="merchant-login">
                มีบัญชีร้านค้าอยู่แล้ว? <a href="/login">เข้าสู่ระบบ</a>
              </p>
            </form>
          </div>
          <footer className="merchant-trust">
            <div>
              <ShieldCheck />
              <p>
                <b>ข้อมูลปลอดภัย</b>
                <span>มาตรฐานการเข้ารหัสระดับสากล</span>
              </p>
            </div>
            <div>
              <FileCheck2 />
              <p>
                <b>สมัครฟรี</b>
                <span>ไม่มีค่าใช้จ่ายในการสมัคร</span>
              </p>
            </div>
            <div>
              <Headphones />
              <p>
                <b>ทีมงานดูแลใกล้ชิด</b>
                <span>พร้อมให้คำปรึกษา</span>
              </p>
            </div>
          </footer>
        </section>
      </div>
    </main>
  );
}
