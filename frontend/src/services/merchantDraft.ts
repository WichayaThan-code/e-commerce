export const MERCHANT_DRAFT_KEY = "merchant_signup_draft";

export type OwnerDraft = {
  ownerType: "person" | "company";
  title: string;
  firstName: string;
  lastName: string;
  citizenId: string;
  birthDate: string;
  nationality: string;
  phone: string;
  email: string;
  houseNo: string;
  villageNo: string;
  building: string;
  alley: string;
  road: string;
  province: string;
  district: string;
  subdistrict: string;
  postalCode: string;
  sameAddress: boolean;
  bankName: string;
  bankAccountNumber: string;
  bankAccountName: string;
};

export type MerchantDraft = {
  shopName?: string;
  shopNameEnglish?: string;
  description?: string;
  category?: string;
  saleType?: "owner" | "reseller";
  website?: string;
  lineId?: string;
  facebookUrl?: string;
  instagramUrl?: string;
  tiktokUrl?: string;
  address?: string;
  province?: string;
  district?: string;
  subdistrict?: string;
  postalCode?: string;
  logoBase64?: string;
  owner?: OwnerDraft;
  documentNames?: Partial<Record<"IDENTITY" | "PORTRAIT" | "ADDRESS" | "BUSINESS" | "LOGO", string>>;
  documentsConfirmed?: boolean;
  ownerId?: string;
  /** Owner id whose details/documents were already prefilled into this draft (second shop), so a reload keeps the user's edits. */
  ownerPrefilledFor?: string;
};

function storageKey(userId: string) {
  return `${MERCHANT_DRAFT_KEY}:${userId}`;
}

export function readMerchantDraft(userId?: string | null): MerchantDraft {
  localStorage.removeItem(MERCHANT_DRAFT_KEY);
  if (!userId) return {};
  try {
    const parsed = JSON.parse(localStorage.getItem(storageKey(userId)) || "{}");
    return parsed && typeof parsed === "object" ? parsed : {};
  } catch {
    return {};
  }
}

export function writeMerchantDraft(userId: string | null | undefined, draft: MerchantDraft) {
  localStorage.removeItem(MERCHANT_DRAFT_KEY);
  if (!userId) return;
  localStorage.setItem(storageKey(userId), JSON.stringify(draft));
}

export function clearMerchantDraft(userId?: string | null) {
  localStorage.removeItem(MERCHANT_DRAFT_KEY);
  if (!userId) return;
  localStorage.removeItem(storageKey(userId));
}

export function rememberDocumentName(userId: string | null | undefined, type: "IDENTITY" | "PORTRAIT" | "ADDRESS" | "BUSINESS" | "LOGO", name: string) {
  const draft = readMerchantDraft(userId);
  writeMerchantDraft(userId, { ...draft, documentNames: { ...draft.documentNames, [type]: name } });
}

/** Map a returning merchant's owner profile into the local owner draft (personal + payout). */
export function ownerDraftFromProfile(owner: {
  ownerType?: string | null;
  title?: string | null;
  firstName?: string | null;
  lastName?: string | null;
  citizenId?: string | null;
  birthDate?: string | null;
  nationality?: string | null;
  phone?: string | null;
  email?: string | null;
  houseNo?: string | null;
  villageNo?: string | null;
  building?: string | null;
  alley?: string | null;
  road?: string | null;
  province?: string | null;
  district?: string | null;
  subdistrict?: string | null;
  postalCode?: string | null;
  bankName?: string | null;
  bankAccountNumber?: string | null;
  bankAccountName?: string | null;
}): OwnerDraft {
  const digits = (value: string | null | undefined, max = 20) => (value || "").replace(/\D/g, "").slice(0, max);
  const citizen = digits(owner.citizenId, 13);
  const citizenFmt = citizen.length === 13
    ? `${citizen.slice(0, 1)}-${citizen.slice(1, 5)}-${citizen.slice(5, 10)}-${citizen.slice(10, 12)}-${citizen.slice(12)}`
    : citizen;
  const phoneDigits = digits(owner.phone, 10);
  const phoneFmt = phoneDigits.length === 10
    ? `${phoneDigits.slice(0, 3)}-${phoneDigits.slice(3, 6)}-${phoneDigits.slice(6)}`
    : phoneDigits;
  return {
    ownerType: owner.ownerType === "COMPANY" || owner.ownerType === "company" ? "company" : "person",
    title: (owner.title || "").trim(),
    firstName: (owner.firstName || "").trim(),
    lastName: (owner.lastName || "").trim(),
    citizenId: citizenFmt,
    birthDate: (owner.birthDate || "").slice(0, 10),
    nationality: (owner.nationality || "ไทย").trim() || "ไทย",
    phone: phoneFmt,
    email: (owner.email || "").trim(),
    houseNo: (owner.houseNo || "").trim(),
    villageNo: (owner.villageNo || "").trim(),
    building: (owner.building || "").trim(),
    alley: (owner.alley || "").trim(),
    road: (owner.road || "").trim(),
    province: (owner.province || "").trim(),
    district: (owner.district || "").trim(),
    subdistrict: (owner.subdistrict || "").trim(),
    postalCode: (owner.postalCode || "").trim(),
    sameAddress: false,
    bankName: (owner.bankName || "").trim(),
    bankAccountNumber: digits(owner.bankAccountNumber, 20),
    bankAccountName: (owner.bankAccountName || "").trim(),
  };
}
