import { useEffect, useRef, useState } from "react";
import {
  ArrowRight,
  BarChart3,
  Camera,
  Check,
  CircleAlert,
  FileCheck2,
  Globe2,
  Headphones,
  MapPin,
  Megaphone,
  PackageCheck,
  ShieldCheck,
  Store,
  TrendingUp,
  Type,
  UsersRound,
} from "lucide-react";
import { useNavigate } from "react-router-dom";

import MerchantSocialFields, { type ShopSocialDraft } from "../components/merchant/MerchantSocialFields";
import RegisterStepper from "../components/merchant/RegisterStepper";
import { useAuth } from "../contexts/AuthContext";
import { useLanguage } from "../contexts/LanguageContext";
import MerchantHeader from "../components/MerchantHeader";
import SearchableSelect from "../components/SearchableSelect";
import ThaiAddressFields, { type ThaiAddressValue } from "../components/ThaiAddressFields";
import FieldError from "../components/form/FieldError";
import FormField from "../components/form/FormField";
import FormGrid from "../components/form/FormGrid";
import "../styles/form-fields.css";
import "../styles/login.css";
import { readMerchantDraft, writeMerchantDraft } from "../services/merchantDraft";
import { autofillSyncHandlers, isAutofillAnimation, readFormTextValue } from "../hooks/autofillSync";
import { focusFirstInvalid, MAX_LOGO_BYTES, mergeFieldError, stripThaiScript, validateShop, type ShopInput } from "../services/merchantApplicationValidation";
import { isShopImage, SHOP_IMAGE_ACCEPT } from "../services/shopImage";
import i18n from "../i18n";
import { catalogApi, merchantApi, type ProductCategory } from "../services/api";
import { labelForCategory } from "../services/categories";
import "../styles/merchant-signup.css";
import "../styles/merchant-register-shell.css";
import "../styles/form-invalid.css";

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
  const [englishThaiBlocked, setEnglishThaiBlocked] = useState(false);
  const englishComposing = useRef(false);
  const lastShopNameEnglish = useRef(shopNameEnglish);
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
  const [submitting, setSubmitting] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const categoryChoices = remoteCategories?.length
    ? remoteCategories.map((category) => ({ value: category.nameTh, label: labelForCategory(category, locale) }))
    : r.categoryOptions;
  const categoryLabel = categoryChoices.find((option) => option.value === selectedCategory)?.label || "";


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

  const shopInput = (): ShopInput => ({
    shopName,
    shopNameEnglish,
    description,
    category: selectedCategory,
    website: social.website,
    lineId: social.lineId,
    facebookUrl: social.facebookUrl,
    instagramUrl: social.instagramUrl,
    tiktokUrl: social.tiktokUrl,
    address,
    province: thaiAddress.province,
    district: thaiAddress.district,
    subdistrict: thaiAddress.subdistrict,
    postalCode: thaiAddress.postalCode,
    logoBase64,
  });

  const clearFieldError = (field: string) => {
    setFieldErrors((prev) => mergeFieldError(prev, field));
    setMessage("");
  };

  const blurShop = (field: string) => {
    setFieldErrors((prev) => mergeFieldError(prev, field, validateShop(shopInput())[field]));
  };

  const shopNameHandlers = autofillSyncHandlers({
    onValue: setShopName,
    onClearError: () => clearFieldError("shopName"),
  });
  // English shop name: strip Thai script as the user types/pastes/autofills, keeping the caret in place.
  // While an IME composition is open the raw text is kept; it is cleaned once the composition ends.
  const acceptShopNameEnglish = (el: HTMLInputElement) => {
    const raw = el.value;
    if (englishComposing.current) {
      lastShopNameEnglish.current = raw;
      setShopNameEnglish(raw);
      return;
    }
    const clean = stripThaiScript(raw);
    if (clean !== raw) {
      const caret = el.selectionStart ?? raw.length;
      const nextCaret = stripThaiScript(raw.slice(0, caret)).length;
      el.value = clean;
      if (document.activeElement === el) el.setSelectionRange(nextCaret, nextCaret);
      setEnglishThaiBlocked(true);
    } else if (raw !== lastShopNameEnglish.current) {
      // A new clean edit (not the echo of the strip above, since onChange and onInput both fire) hides the hint.
      setEnglishThaiBlocked(false);
    }
    lastShopNameEnglish.current = clean;
    setShopNameEnglish(clean);
    clearFieldError("shopNameEnglish");
  };
  const shopNameEnglishHandlers = {
    onChange: (event: React.ChangeEvent<HTMLInputElement>) => acceptShopNameEnglish(event.currentTarget),
    onInput: (event: React.FormEvent<HTMLInputElement>) => acceptShopNameEnglish(event.currentTarget),
    onAnimationStart: (event: React.AnimationEvent<HTMLInputElement>) => {
      if (isAutofillAnimation(event.animationName)) acceptShopNameEnglish(event.currentTarget);
    },
    onCompositionStart: () => { englishComposing.current = true; },
    onCompositionEnd: (event: React.CompositionEvent<HTMLInputElement>) => {
      englishComposing.current = false;
      acceptShopNameEnglish(event.currentTarget);
    },
  };
  const descriptionHandlers = autofillSyncHandlers({
    onValue: setDescription,
    onClearError: () => clearFieldError("description"),
  });
  const addressHandlers = autofillSyncHandlers({
    onValue: setAddress,
    onClearError: () => clearFieldError("address"),
  });


  const patchThaiAddress = (next: ThaiAddressValue) => {
    setThaiAddress(next);
    setFieldErrors((prev) => {
      const updated = { ...prev };
      for (const key of ["province", "district", "subdistrict", "postalCode"] as const) {
        // Clear stale errors on select / cascade reset (same-commit select must not flash error).
        delete updated[key];
      }
      return updated;
    });
    setMessage("");
  };

  const chooseLogo = async (file?: File) => {
    if (!file) return;
    if (file.size > MAX_LOGO_BYTES) {
      setFieldErrors((prev) => ({ ...prev, logo: r.errLogoSize }));
      setMessage(r.errLogoSize);
      return;
    }
    if (!isShopImage(file)) {
      setFieldErrors((prev) => ({ ...prev, logo: r.errLogoType }));
      setMessage(r.errLogoType);
      return;
    }
    try {
      setLogoBase64(await fileToBase64(file));
      setFieldErrors((prev) => mergeFieldError(prev, "logo"));
      setMessage("");
    } catch {
      setMessage(r.errLogoRead);
    }
  };

  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (submitting) return;
    if (!user) {
      window.location.assign("/login?next=/merchant/register");
      return;
    }
    const form = event.currentTarget;
    // Autofill may fill the DOM without React onChange — sync before validate/draft.
    const syncedSocial: ShopSocialDraft = {
      website: readFormTextValue(form, "website", social.website),
      lineId: readFormTextValue(form, "lineId", social.lineId),
      facebookUrl: readFormTextValue(form, "facebookUrl", social.facebookUrl),
      instagramUrl: readFormTextValue(form, "instagramUrl", social.instagramUrl),
      tiktokUrl: readFormTextValue(form, "tiktokUrl", social.tiktokUrl),
    };
    const synced: ShopInput = {
      ...shopInput(),
      shopName: readFormTextValue(form, "shopName", shopName),
      shopNameEnglish: readFormTextValue(form, "shopNameEnglish", shopNameEnglish),
      description: readFormTextValue(form, "description", description),
      address: readFormTextValue(form, "address", address),
      ...syncedSocial,
    };
    setShopName(synced.shopName);
    setShopNameEnglish(synced.shopNameEnglish);
    setDescription(synced.description);
    setAddress(synced.address);
    setSocial(syncedSocial);
    const nextErrors = validateShop(synced);
    setFieldErrors(nextErrors);
    const errorCount = Object.keys(nextErrors).length;
    if (errorCount > 0) {
      setMessage(i18n.t("validation.summary", { ns: "merchant", count: errorCount }));
      focusFirstInvalid(nextErrors);
      return;
    }
    setMessage("");
    setSubmitting(true);
    // Server-side shop-name uniqueness (same rule as submit) before leaving this step; submit re-checks as the final guard.
    try {
      const { available } = await merchantApi.checkShopName(synced.shopName.trim());
      if (!available) {
        const taken = { shopName: i18n.t("validation.shopNameTaken", { ns: "merchant" }) };
        setFieldErrors(taken);
        setMessage(taken.shopName);
        setSubmitting(false);
        focusFirstInvalid(taken);
        return;
      }
    } catch {
      setMessage(i18n.t("validation.shopNameCheckFailed", { ns: "merchant" }));
      setSubmitting(false);
      return;
    }
    writeMerchantDraft(user?.id, {
      ...readMerchantDraft(user?.id),
      shopName: synced.shopName,
      shopNameEnglish: synced.shopNameEnglish,
      description: synced.description,
      category: selectedCategory,
      saleType,
      ...syncedSocial,
      address: synced.address,
      province: thaiAddress.province,
      district: thaiAddress.district,
      subdistrict: thaiAddress.subdistrict,
      postalCode: thaiAddress.postalCode,
      logoBase64,
    });
    // Never skip the owner + document steps, also for a second shop of an existing owner (they are prefilled there).
    navigate("/merchant/owner-info");
  };

  return (
    <main className="merchant-page merchant-page--register">
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
            </div>
            <RegisterStepper currentStep={1} />

            <form onSubmit={submit} noValidate>
              {message && (
                <p className="login-message" role="alert">
                  <CircleAlert size={18} />
                  <span>{message}</span>
                </p>
              )}
              <h3>{r.sectionShop}</h3>

              <FormGrid>
                <FormField
                  className="flush"
                  label={<>{r.shopName} <em>{ob.common.required}</em></>}
                  icon={Store}
                  error={fieldErrors.shopName}
                  errorId="field-error-shopName"
                  aside={`${shopName.length}/100`}
                >
                  <input
                    name="shopName"
                    maxLength={100}
                    placeholder={r.shopNamePh}
                    value={shopName}
                    autoComplete="organization"
                    aria-invalid={Boolean(fieldErrors.shopName)}
                    aria-describedby={fieldErrors.shopName ? "field-error-shopName" : undefined}
                    {...shopNameHandlers}
                    onBlur={() => blurShop("shopName")}
                  />
                </FormField>
                <FormField
                  className="flush"
                  label={r.shopNameEn}
                  icon={Type}
                  error={fieldErrors.shopNameEnglish || (englishThaiBlocked ? i18n.t("validation.shopNameEnThaiBlocked", { ns: "merchant" }) : undefined)}
                  errorId="field-error-shopNameEnglish"
                >
                  <input
                    name="shopNameEnglish"
                    maxLength={100}
                    placeholder={r.shopNameEnPh}
                    value={shopNameEnglish}
                    autoComplete="off"
                    aria-invalid={Boolean(fieldErrors.shopNameEnglish)}
                    aria-describedby={fieldErrors.shopNameEnglish || englishThaiBlocked ? "field-error-shopNameEnglish" : undefined}
                    lang="en"
                    {...shopNameEnglishHandlers}
                    onBlur={() => { setEnglishThaiBlocked(false); blurShop("shopNameEnglish"); }}
                  />
                </FormField>
              </FormGrid>

              <div className="merchant-two detail-logo">
                <div>
                  <FormField
                    className="flush"
                    label={r.description}
                    multiline
                    error={fieldErrors.description}
                    errorId="field-error-description"
                    aside={`${description.length}/500`}
                  >
                    <textarea
                      name="description"
                      maxLength={500}
                      placeholder={r.descriptionPh}
                      value={description}
                      autoComplete="off"
                      aria-invalid={Boolean(fieldErrors.description)}
                      aria-describedby={fieldErrors.description ? "field-error-description" : undefined}
                      {...descriptionHandlers}
                      onBlur={() => blurShop("description")}
                    />
                  </FormField>
                </div>
                <label data-field="logo">
                  {r.logo} <em>{ob.common.required}</em>
                  <button
                    className={`logo-drop${fieldErrors.logo ? " is-invalid" : ""}`}
                    type="button"
                    aria-invalid={Boolean(fieldErrors.logo)}
                    aria-describedby={fieldErrors.logo ? "field-error-logo" : undefined}
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
                  <FieldError text={fieldErrors.logo} id="field-error-logo" />
                </label>
              </div>

              <div className="merchant-two compact-row">
                <SearchableSelect
                  field="category"
                  error={fieldErrors.category}
                  onBlur={() => blurShop("category")}
                  label={r.category}
                  required
                  placeholder={r.categoryPh}
                  options={categoryChoices.map((option) => option.label)}
                  value={categoryLabel}
                  onChange={(label) => {
                    const match = categoryChoices.find((option) => option.label === label);
                    setSelectedCategory(match?.value || "");
                    clearFieldError("category");
                  }}
                />
              </div>

              <MerchantSocialFields
                copy={ob.social}
                value={social}
                onChange={setSocial}
                errors={fieldErrors}
                onBlur={blurShop}
                onClearError={clearFieldError}
              />

              <fieldset data-field="saleType">
                <legend>
                  {r.saleType} <em>{ob.common.required}</em>
                </legend>
                <div
                  className="sale-types"
                  role="radiogroup"
                  aria-label={r.saleType}
                  aria-invalid={Boolean(fieldErrors.saleType)}
                  aria-describedby={fieldErrors.saleType ? "field-error-saleType" : undefined}
                >
                  <button
                    type="button"
                    className={saleType === "owner" ? "active" : ""}
                    aria-pressed={saleType === "owner"}
                    onClick={() => {
                      clearFieldError("saleType");
                      setSaleType("owner");
                    }}
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
                    aria-pressed={saleType === "reseller"}
                    onClick={() => {
                      clearFieldError("saleType");
                      setSaleType("reseller");
                    }}
                  >
                    <i>{saleType === "reseller" ? <Check /> : null}</i>
                    <UsersRound />
                    <span>
                      <b>{r.saleReseller}</b>
                      <small>{r.saleResellerHint}</small>
                    </span>
                  </button>
                </div>
                <FieldError text={fieldErrors.saleType} id="field-error-saleType" />
              </fieldset>

              <FormField
                label={<>{r.address} <em>{ob.common.required}</em></>}
                icon={MapPin}
                error={fieldErrors.address}
                errorId="field-error-address"
              >
                <input
                  name="address"
                  maxLength={500}
                  placeholder={r.addressPh}
                  value={address}
                  autoComplete="street-address"
                  aria-invalid={Boolean(fieldErrors.address)}
                  aria-describedby={fieldErrors.address ? "field-error-address" : undefined}
                  {...addressHandlers}
                  onBlur={() => blurShop("address")}
                />
              </FormField>

              <ThaiAddressFields
                value={thaiAddress}
                onChange={patchThaiAddress}
                errors={fieldErrors}
                onBlurField={blurShop}
              />

              <button className="login-submit merchant-next" type="submit" disabled={submitting}>
                {submitting ? i18n.t("validation.checking", { ns: "merchant" }) : ob.common.next} <ArrowRight />
              </button>

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
