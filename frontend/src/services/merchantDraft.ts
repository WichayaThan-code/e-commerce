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
