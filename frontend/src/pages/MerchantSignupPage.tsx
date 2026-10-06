import { useEffect, useRef, useState } from "react";
import {
  ArrowRight,
  BarChart3,
  Camera,
  Check,
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
}

export default function MerchantSignupPage() {
  const { user } = useAuth();
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
    event.preventDefault();
    if (!user) {
      window.location.assign("/login?next=/merchant/register");
      return;
    }
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
  };

  return (
    <main className="merchant-page">
      <MerchantHeader />

      <div className="merchant-layout">
        <section className="merchant-story">
          <div className="merchant-story-shade" />
          <div className="merchant-copy">
            <h1>
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
            </blockquote>
          </div>
          <div className="merchant-stats">
            <div>
              <UsersRound />
              <p>
                <b>100,000+</b>
                <span>{r.statCustomers}</span>
              </p>
            </div>
            <div>
              <Store />
              <p>
                <b>5,000+</b>
                <span>{r.statShops}</span>
              </p>
            </div>
            <div>
              <Globe2 />
              <p>
                <b>{r.statShip}</b>
                <span>{r.statShipHint}</span>
              </p>
            </div>
          </div>
        </section>

        <section className="merchant-form-side">
          <div className="merchant-card">
            <div className="merchant-card-title">
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
                  <button
                    className="logo-drop"
                    type="button"
                    onClick={() => fileRef.current?.click()}
                    onDragOver={(e) => e.preventDefault()}
                    onDrop={(e) => {
                      e.preventDefault();
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
                        </small>
                      </>
                    )}
                  </button>
                  <input
                    ref={fileRef}
                    className="file-input"
                    type="file"
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
                      <b>{r.saleOwner}</b>
                      <small>{r.saleOwnerHint}</small>
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
                      <b>{r.saleReseller}</b>
                      <small>{r.saleResellerHint}</small>
                    </span>
                  </button>
                </div>
              </fieldset>

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

              {message && (
                <p className="merchant-message" role="status">
                  {message}
                </p>
              )}

              <p className="merchant-login">
                {ob.common.hasAccount} <a href="/login">{ob.common.login}</a>
              </p>
            </form>
          </div>

          <footer className="merchant-trust">
            <div>
              <ShieldCheck />
              <p>
                <b>{r.trustSecure}</b>
                <span>{r.trustSecureHint}</span>
              </p>
            </div>
            <div>
              <FileCheck2 />
              <p>
                <b>{r.trustFree}</b>
                <span>{r.trustFreeHint}</span>
              </p>
            </div>
            <div>
              <Headphones />
              <p>
                <b>{r.trustSupport}</b>
                <span>{r.trustSupportHint}</span>
              </p>
            </div>
          </footer>
        </section>
      </div>
    </main>
  );
}
