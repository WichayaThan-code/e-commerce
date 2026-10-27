import { request } from "./api";

export type CustomerSegment = "VIP" | "REPEAT" | "GENERAL";
export type CustomerInsightStatus = "ACTIVE" | "INACTIVE";

export type CustomerInsight = {
  key: string;
  shopCustomerId: string | null;
  displayName: string;
  phone: string | null;
  email: string | null;
  piiMasked: boolean;
  channel: string | null;
  orderCount: number;
  totalSpent: number | string;
  currency: string;
  firstOrderAt: string | null;
  lastOrderAt: string | null;
  joinedAt: string | null;
  segment: CustomerSegment;
  status: CustomerInsightStatus;
};

export type CustomerInsightsStats = {
  totalCustomers: number;
  newThisMonth: number;
  repeatCustomers: number;
  vipCustomers: number;
  totalRevenue: number | string;
  avgOrderValue: number | string;
};

export type CustomerInsightsPage = {
  items: CustomerInsight[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
  stats: CustomerInsightsStats;
};

export type CustomerChannelCount = { channel: string; count: number };

export type CustomerAddress = {
  id: string;
  label: string | null;
  recipientName: string;
  phone: string | null;
  addressLine1: string;
  addressLine2: string | null;
  subdistrict: string | null;
  district: string;
  province: string;
  postalCode: string;
  countryCode: string;
  defaultShipping: boolean;
  defaultBilling: boolean;
};

export type CustomerInsightDetail = {
  insight: CustomerInsight;
  addresses: CustomerAddress[] | null;
  channels: CustomerChannelCount[];
};

export type CustomerInsightOrder = {
  orderId: string;
  orderNumber: string;
  status: string;
  paymentStatus: string;
  grandTotal: number | string;
  currency: string;
  placedAt: string | null;
};

export type CustomerInsightOrdersPage = {
  items: CustomerInsightOrder[];
  page: number;
  size: number;
  total: number;
  totalPages: number;
};

export type CustomerInsightsQuery = {
  q?: string;
  channel?: string;
  status?: string;
  segment?: string;
  joinedFrom?: string;
  joinedTo?: string;
  sort?: string;
  direction?: string;
  page?: number;
  size?: number;
};

const base = (shopId: number) => `/api/merchant/shops/${shopId}/customer-insights`;

function qs(params: Record<string, string | number | undefined | null>) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value == null || value === "") return;
    query.set(key, String(value));
  });
  const text = query.toString();
  return text ? `?${text}` : "";
}

export function listCustomerInsights(shopId: number, params: CustomerInsightsQuery = {}, signal?: AbortSignal) {
  return request<CustomerInsightsPage>(`${base(shopId)}${qs(params)}`, { cache: "no-store", signal }, true);
}

export function listCustomerInsightChannels(shopId: number, signal?: AbortSignal) {
  return request<string[]>(`${base(shopId)}/channels`, { cache: "no-store", signal }, true);
}

export function getCustomerInsight(shopId: number, key: string, signal?: AbortSignal) {
  return request<CustomerInsightDetail>(`${base(shopId)}/${encodeURIComponent(key)}`, { cache: "no-store", signal }, true);
}

export function listCustomerInsightOrders(shopId: number, key: string, page = 0, size = 10, signal?: AbortSignal) {
  return request<CustomerInsightOrdersPage>(
    `${base(shopId)}/${encodeURIComponent(key)}/orders${qs({ page, size })}`,
    { cache: "no-store", signal },
    true,
  );
}

const AVATAR_COLORS = ["#e3f0ff", "#eaf7f0", "#dceaff", "#ffe8ee", "#fff4e5", "#f0e9ff", "#e8f7ff"];

export function avatarForName(name: string) {
  const trimmed = (name || "?").trim();
  const initial = trimmed ? Array.from(trimmed)[0] : "?";
  let hash = 0;
  for (let i = 0; i < trimmed.length; i += 1) hash = (hash * 31 + trimmed.charCodeAt(i)) >>> 0;
  return { initial, color: AVATAR_COLORS[hash % AVATAR_COLORS.length] };
}
