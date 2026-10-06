<<<<<<< HEAD
import { useRef, useState } from "react";
=======
import { useEffect, useRef, useState } from "react";
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
import {
  ArrowRight,
  BarChart3,
  Camera,
  Check,
<<<<<<< HEAD
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
=======
  FileCheck2,
  Globe2,
  Headphones,
  Megaphone,
  PackageCheck,
  ShieldCheck,
  Store,
  TrendingUp,
  UsersRound,
} from "lucide-react";
import { useNavigate } from "react-router-dom";

import MerchantSocialFields, { type ShopSocialDraft } from "../components/merchant/MerchantSocialFields";
import { useAuth } from "../contexts/AuthContext";
import { useLanguage } from "../contexts/LanguageContext";
import MerchantHeader from "../components/MerchantHeader";
import SearchableSelect from "../components/SearchableSelect";
import ThaiAddressFields, { type ThaiAddressValue } from "../components/ThaiAddressFields";
import { FieldError } from "../components/form/FormField";
import "../styles/form-fields.css";
import { readMerchantDraft, writeMerchantDraft } from "../services/merchantDraft";
import { isShopImage, SHOP_IMAGE_ACCEPT } from "../services/shopImage";
import { catalogApi, merchantApi, type ProductCategory } from "../services/api";
import { labelForCategory } from "../services/categories";
import "../styles/merchant-signup.css";

const benefitIcons = [Store, BarChart3, Megaphone, Headphones, TrendingUp];
const benefitTones = ["green", "orange", "blue", "red", "purple"];

const fileToBase64 = (file: File): Promise<string> =>
  new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.readAsDataURL(file);
    reader.onload = () => resolve(reader.result as string);
    reader.onerror = (error) => reject(error);
  });

function readSocial(draft: ReturnType<typeof readMerchantDraft>): ShopSocialDraft {
  return {
    website: draft.website || "",
    lineId: draft.lineId || "",
    facebookUrl: draft.facebookUrl || "",
    instagramUrl: draft.instagramUrl || "",
    tiktokUrl: draft.tiktokUrl || "",
  };
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
}

export default function MerchantSignupPage() {
  const { user } = useAuth();
<<<<<<< HEAD
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
=======
  const { locale, onboarding: ob } = useLanguage();
  const r = ob.register;
  const navigate = useNavigate();
  const fileRef = useRef<HTMLInputElement>(null);

  const savedDraft = readMerchantDraft(user?.id);
  const [hasShop, setHasShop] = useState<boolean | null>(null);

  const [shopName, setShopName] = useState(savedDraft.shopName || "");
  const [shopNameEnglish, setShopNameEnglish] = useState(savedDraft.shopNameEnglish || "");
  const [description, setDescription] = useState(savedDraft.description || "");
  const [social, setSocial] = useState<ShopSocialDraft>(() => readSocial(savedDraft));
  const [address, setAddress] = useState(savedDraft.address || "");
  const [logoBase64, setLogoBase64] = useState<string>(savedDraft.logoBase64 || "");
  const [saleType, setSaleType] = useState<"owner" | "reseller">(savedDraft.saleType || "owner");
  const [selectedCategory, setSelectedCategory] = useState(savedDraft.category || "");
  const [remoteCategories, setRemoteCategories] = useState<ProductCategory[] | null>(null);
  const [thaiAddress, setThaiAddress] = useState<ThaiAddressValue>({
    province: savedDraft.province || "",
    district: savedDraft.district || "",
    subdistrict: savedDraft.subdistrict || "",
    postalCode: savedDraft.postalCode || "",
  });
  const [message, setMessage] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const categoryChoices = remoteCategories?.length
    ? remoteCategories.map((category) => ({ value: category.nameTh, label: labelForCategory(category, locale) }))
    : r.categoryOptions;
  const categoryLabel = categoryChoices.find((option) => option.value === selectedCategory)?.label || "";

  const reuseOwner = Boolean(savedDraft.ownerId) && hasShop !== false;

  useEffect(() => {
    if (!user) return;
    let active = true;
    merchantApi.shops().then((rows) => {
      if (active) setHasShop(rows.length > 0);
    }).catch(() => {
      if (active) setHasShop(false);
    });
    return () => { active = false; };
  }, [user]);

  useEffect(() => {
    let active = true;
    catalogApi.categories().then((rows) => {
      if (!active || rows.length === 0) return;
      setRemoteCategories(rows);
      setSelectedCategory((current: string) => (rows.some((row) => row.nameTh === current) ? current : ""));
    }).catch(() => {});
    return () => { active = false; };
  }, []);

  useEffect(() => {
    if (!user || hasShop !== false) return;
    const draft = readMerchantDraft(user.id);
    if (!draft.ownerId) return;
    writeMerchantDraft(user.id, { ...draft, ownerId: undefined });
  }, [user, hasShop]);

  useEffect(() => {
    if (!user) return;
    writeMerchantDraft(user?.id, {
      ...readMerchantDraft(user?.id),
      shopName,
      shopNameEnglish,
      description,
      category: selectedCategory,
      saleType,
      ...social,
      address,
      province: thaiAddress.province,
      district: thaiAddress.district,
      subdistrict: thaiAddress.subdistrict,
      postalCode: thaiAddress.postalCode,
      logoBase64,
    });
  }, [shopName, shopNameEnglish, description, selectedCategory, saleType, social, address, thaiAddress, logoBase64]);

  const chooseLogo = async (file?: File) => {
    if (!file) return;
    if (file.size > 2 * 1024 * 1024) {
      setMessage(r.errLogoSize);
      return;
    }
    if (!isShopImage(file)) {
      setMessage(r.errLogoType);
      return;
    }
    try {
      setLogoBase64(await fileToBase64(file));
      setMessage("");
    } catch {
      setMessage(r.errLogoRead);
    }
  };

  const submit = (event: React.FormEvent<HTMLFormElement>) => {
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
    event.preventDefault();
    if (!user) {
      window.location.assign("/login?next=/merchant/register");
      return;
    }
<<<<<<< HEAD
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
=======
    const nextErrors: Record<string, string> = {};
    if (!shopName) nextErrors.shopName = r.errShopName;
    if (!logoBase64) nextErrors.logo = r.errLogo;
    if (!selectedCategory) nextErrors.category = r.errCategory;
    if (!thaiAddress.province || !thaiAddress.district || !thaiAddress.subdistrict || !thaiAddress.postalCode) nextErrors.address = r.errAddress;
    setFieldErrors(nextErrors);
    setMessage("");
    if (Object.keys(nextErrors).length > 0) return;
    writeMerchantDraft(user?.id, {
      ...readMerchantDraft(user?.id),
      shopName,
      shopNameEnglish,
      description,
      category: selectedCategory,
      saleType,
      ...social,
      address,
      province: thaiAddress.province,
      district: thaiAddress.district,
      subdistrict: thaiAddress.subdistrict,
      postalCode: thaiAddress.postalCode,
      logoBase64,
    });
    navigate(reuseOwner ? "/merchant/review" : "/merchant/owner-info");
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
  };

  return (
    <main className="merchant-page">
<<<<<<< HEAD
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
=======
      <MerchantHeader />
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb

      <div className="merchant-layout">
        <section className="merchant-story">
          <div className="merchant-story-shade" />
          <div className="merchant-copy">
            <h1>
<<<<<<< HEAD
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
=======
              {r.storyTitle}
              <br />
              <span>{r.storyBrand}</span>
            </h1>
            <h2>{r.storyH2}</h2>
            <p>
              {r.storyP.split("\n").map((line, index) => (
                <span key={index}>
                  {line}
                  {index === 0 && <br />}
                </span>
              ))}
            </p>
            <div className="merchant-benefits">
              {r.benefits.map(({ title, text }, index) => {
                const Icon = benefitIcons[index] ?? Store;
                return (
                  <div key={title}>
                    <span className={benefitTones[index]}>
                      <Icon />
                    </span>
                    <p>
                      <b>{title}</b>
                      <small>{text}</small>
                    </p>
                  </div>
                );
              })}
            </div>
            <blockquote>
              {r.storyQuote.split("\n").map((line, index) => (
                <span key={index}>
                  {line}
                  {index === 0 && <br />}
                </span>
              ))}
              <i />
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
            </blockquote>
          </div>
          <div className="merchant-stats">
            <div>
              <UsersRound />
              <p>
                <b>100,000+</b>
<<<<<<< HEAD
                <span>ลูกค้าที่ไว้วางใจ</span>
=======
                <span>{r.statCustomers}</span>
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
              </p>
            </div>
            <div>
              <Store />
              <p>
                <b>5,000+</b>
<<<<<<< HEAD
                <span>ร้านค้าไทย</span>
=======
                <span>{r.statShops}</span>
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
              </p>
            </div>
            <div>
              <Globe2 />
              <p>
<<<<<<< HEAD
                <b>ส่งไกลได้</b>
                <span>ทั่วไทยและต่างประเทศ</span>
=======
                <b>{r.statShip}</b>
                <span>{r.statShipHint}</span>
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
              </p>
            </div>
          </div>
        </section>

        <section className="merchant-form-side">
          <div className="merchant-card">
            <div className="merchant-card-title">
<<<<<<< HEAD
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
=======
              <h2>{r.cardTitle}</h2>
              <p>{r.cardLead}</p>
              {reuseOwner && <p className="merchant-reuse-note">{r.reuseNote}</p>}
            </div>
            <div className="merchant-steps">
              {ob.steps.map((step, index) => (
                <div className={index === 0 ? "active" : ""} key={step.title}>
                  <span>{index + 1}</span>
                  <small>{step.title}</small>
                </div>
              ))}
            </div>

            <form onSubmit={submit} noValidate>
              <h3>{r.sectionShop}</h3>

              <div className="merchant-two">
                <label>
                  {r.shopName} <em>{ob.common.required}</em>
                  <input
                    name="shopName"
                    maxLength={100}
                    placeholder={r.shopNamePh}
                    value={shopName}
                    aria-invalid={Boolean(fieldErrors.shopName)}
                    aria-describedby={fieldErrors.shopName ? "shop-name-error" : undefined}
                    onChange={(e) => setShopName(e.target.value)}
                  />
                  <FieldError id="shop-name-error" text={fieldErrors.shopName} />
                  <small>{shopName.length}/100</small>
                </label>
                <label>
                  {r.shopNameEn}
                  <input
                    name="shopNameEnglish"
                    maxLength={100}
                    placeholder={r.shopNameEnPh}
                    value={shopNameEnglish}
                    onChange={(e) => setShopNameEnglish(e.target.value)}
                  />
                </label>
              </div>

              <div className="merchant-two detail-logo">
                <label>
                  {r.description}
                  <textarea
                    name="description"
                    maxLength={500}
                    placeholder={r.descriptionPh}
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                  />
                  <small>{description.length}/500</small>
                </label>
                <label>
                  {r.logo} <em>{ob.common.required}</em>
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
                  <button
                    className="logo-drop"
                    type="button"
                    onClick={() => fileRef.current?.click()}
                    onDragOver={(e) => e.preventDefault()}
                    onDrop={(e) => {
                      e.preventDefault();
<<<<<<< HEAD
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
=======
                      if (e.dataTransfer.files[0]) void chooseLogo(e.dataTransfer.files[0]);
                    }}
                  >
                    {logoBase64 ? (
                      <img src={logoBase64} alt={r.logoAlt} />
                    ) : (
                      <>
                        <Camera />
                        <span>{r.logoDrop}</span>
                        <small>
                          {r.logoHint.split("\n").map((line, index) => (
                            <span key={index}>
                              {line}
                              {index === 0 && <br />}
                            </span>
                          ))}
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
                        </small>
                      </>
                    )}
                  </button>
                  <input
                    ref={fileRef}
                    className="file-input"
                    type="file"
<<<<<<< HEAD
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
=======
                    accept={SHOP_IMAGE_ACCEPT}
                    onChange={(e) => {
                      if (e.target.files?.[0]) void chooseLogo(e.target.files[0]);
                    }}
                  />
                  <FieldError text={fieldErrors.logo} />
                </label>
              </div>

              <div className="merchant-two compact-row">
                <SearchableSelect
                  label={r.category}
                  required
                  placeholder={r.categoryPh}
                  options={categoryChoices.map((option) => option.label)}
                  value={categoryLabel}
                  onChange={(label) => {
                    const match = categoryChoices.find((option) => option.label === label);
                    setSelectedCategory(match?.value || "");
                  }}
                />
                <FieldError text={fieldErrors.category} />
              </div>

              <MerchantSocialFields copy={ob.social} value={social} onChange={setSocial} />

              <fieldset>
                <legend>
                  {r.saleType} <em>{ob.common.required}</em>
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
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
<<<<<<< HEAD
                      <b>ขายสินค้าของตัวเอง</b>
                      <small>(เจ้าของสินค้า / แบรนด์ของตนเอง)</small>
=======
                      <b>{r.saleOwner}</b>
                      <small>{r.saleOwnerHint}</small>
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
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
<<<<<<< HEAD
                      <b>ตัวแทนจำหน่าย / รีเซลเลอร์</b>
                      <small>(จำหน่ายสินค้าจากแบรนด์อื่น)</small>
=======
                      <b>{r.saleReseller}</b>
                      <small>{r.saleResellerHint}</small>
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
                    </span>
                  </button>
                </div>
              </fieldset>
<<<<<<< HEAD
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
=======

              <label>
                {r.address}
                <input
                  name="address"
                  placeholder={r.addressPh}
                  value={address}
                  onChange={(e) => setAddress(e.target.value)}
                />
              </label>

              <ThaiAddressFields value={thaiAddress} onChange={setThaiAddress} />
              <FieldError text={fieldErrors.address} />

              <button className="login-submit merchant-next" type="submit">
                {ob.common.next} <ArrowRight />
              </button>

>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
              {message && (
                <p className="merchant-message" role="status">
                  {message}
                </p>
              )}
<<<<<<< HEAD
              <p className="merchant-login">
                มีบัญชีร้านค้าอยู่แล้ว? <a href="/login">เข้าสู่ระบบ</a>
              </p>
            </form>
          </div>
=======

              <p className="merchant-login">
                {ob.common.hasAccount} <a href="/login">{ob.common.login}</a>
              </p>
            </form>
          </div>

>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
          <footer className="merchant-trust">
            <div>
              <ShieldCheck />
              <p>
<<<<<<< HEAD
                <b>ข้อมูลปลอดภัย</b>
                <span>มาตรฐานการเข้ารหัสระดับสากล</span>
=======
                <b>{r.trustSecure}</b>
                <span>{r.trustSecureHint}</span>
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
              </p>
            </div>
            <div>
              <FileCheck2 />
              <p>
<<<<<<< HEAD
                <b>สมัครฟรี</b>
                <span>ไม่มีค่าใช้จ่ายในการสมัคร</span>
=======
                <b>{r.trustFree}</b>
                <span>{r.trustFreeHint}</span>
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
              </p>
            </div>
            <div>
              <Headphones />
              <p>
<<<<<<< HEAD
                <b>ทีมงานดูแลใกล้ชิด</b>
                <span>พร้อมให้คำปรึกษา</span>
=======
                <b>{r.trustSupport}</b>
                <span>{r.trustSupportHint}</span>
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
              </p>
            </div>
          </footer>
        </section>
      </div>
    </main>
  );
}
