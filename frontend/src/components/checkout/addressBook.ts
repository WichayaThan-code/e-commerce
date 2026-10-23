import type { ThaiAddressValue } from "../ThaiAddressFields";
import { addressBookApi, type BuyerAddress } from "../../services/api";

export type SavedAddress = {
  id: string;
  label: string;
  recipientName: string;
  phone: string;
  addressLine1: string;
  addressLine2: string;
  area: ThaiAddressValue;
  updatedAt: string;
  isDefault?: boolean;
  /** Set when the row came from GET /api/me/addresses (not localStorage-only). */
  source?: "api" | "local";
};

export function isApiAddressId(row: Pick<SavedAddress, "id" | "source"> | null | undefined) {
  return Boolean(row?.source === "api" && row.id && /^[0-9a-f-]{36}$/i.test(row.id));
}

const MAX = 8;
const keyOf = (userId: string) => `thaishopfun.addressBook.${userId}`;

function fromApi(row: BuyerAddress): SavedAddress {
  return {
    id: row.id,
    label: row.label || row.recipientName,
    recipientName: row.recipientName,
    phone: row.phone,
    addressLine1: row.addressLine1,
    addressLine2: row.addressLine2 || "",
    area: {
      province: row.province,
      district: row.district,
      subdistrict: row.subdistrict || "",
      postalCode: row.postalCode,
    },
    updatedAt: row.updatedAt,
    isDefault: row.isDefault,
    source: "api",
  };
}

function readLocal(userId: string): SavedAddress[] {
  try {
    const raw = localStorage.getItem(keyOf(userId));
    if (!raw) return [];
    const parsed = JSON.parse(raw) as SavedAddress[];
    if (!Array.isArray(parsed)) return [];
    return parsed.filter((row) => row && typeof row.id === "string" && row.recipientName && row.area);
  } catch {
    return [];
  }
}

function writeLocal(userId: string, rows: SavedAddress[]) {
  try {
    localStorage.setItem(keyOf(userId), JSON.stringify(rows.slice(0, MAX)));
  } catch { /* storage full / blocked */ }
}

function sortRows(rows: SavedAddress[]) {
  return [...rows].sort((a, b) => {
    if (Boolean(a.isDefault) !== Boolean(b.isDefault)) return a.isDefault ? -1 : 1;
    return b.updatedAt.localeCompare(a.updatedAt);
  });
}

export function sameAddress(
  a: Omit<SavedAddress, "id" | "label" | "updatedAt" | "isDefault">,
  b: Omit<SavedAddress, "id" | "label" | "updatedAt" | "isDefault">,
) {
  return a.recipientName.trim() === b.recipientName.trim()
    && a.phone.replace(/[\s-]/g, "") === b.phone.replace(/[\s-]/g, "")
    && a.addressLine1.trim() === b.addressLine1.trim()
    && (a.addressLine2.trim() || "") === (b.addressLine2.trim() || "")
    && a.area.province === b.area.province
    && a.area.district === b.area.district
    && a.area.subdistrict === b.area.subdistrict
    && a.area.postalCode === b.area.postalCode;
}

function toRequest(entry: Omit<SavedAddress, "id" | "updatedAt"> & { id?: string; isDefault?: boolean }) {
  return {
    label: entry.label.trim() || entry.recipientName.trim(),
    recipientName: entry.recipientName.trim(),
    phone: entry.phone.replace(/[\s-]/g, ""),
    addressLine1: entry.addressLine1.trim(),
    addressLine2: (entry.addressLine2 || "").trim() || undefined,
    subdistrict: entry.area.subdistrict || undefined,
    district: entry.area.district,
    province: entry.area.province,
    postalCode: entry.area.postalCode,
    isDefault: entry.isDefault,
  };
}

/** Prefer API; falls back to localStorage if the endpoint is unavailable. */
export async function listAddresses(userId: string): Promise<SavedAddress[]> {
  try {
    const rows = await addressBookApi.list();
    const mapped = sortRows(rows.map(fromApi));
    writeLocal(userId, mapped);
    return mapped;
  } catch {
    return sortRows(readLocal(userId));
  }
}

export async function upsertAddress(
  userId: string,
  entry: Omit<SavedAddress, "id" | "updatedAt"> & { id?: string },
): Promise<SavedAddress[]> {
  const payload = {
    recipientName: entry.recipientName.trim(),
    phone: entry.phone.replace(/[\s-]/g, ""),
    addressLine1: entry.addressLine1.trim(),
    addressLine2: (entry.addressLine2 || "").trim(),
    area: entry.area,
  };

  try {
    const remote = await addressBookApi.list();
    const existing = entry.id
      ? remote.find((row) => row.id === entry.id)
      : remote.find((row) => sameAddress(fromApi(row), payload));
    if (existing) {
      await addressBookApi.update(existing.id, toRequest({ ...entry, label: entry.label.trim() || existing.label || payload.recipientName }));
    } else {
      await addressBookApi.create(toRequest({ ...entry, label: entry.label.trim() || payload.recipientName }));
    }
    return listAddresses(userId);
  } catch {
    return upsertLocal(userId, entry);
  }
}

export async function removeAddress(userId: string, id: string): Promise<SavedAddress[]> {
  try {
    await addressBookApi.remove(id);
    return listAddresses(userId);
  } catch {
    writeLocal(userId, readLocal(userId).filter((row) => row.id !== id));
    return sortRows(readLocal(userId));
  }
}

function upsertLocal(
  userId: string,
  entry: Omit<SavedAddress, "id" | "updatedAt"> & { id?: string },
): SavedAddress[] {
  const rows = readLocal(userId);
  const now = new Date().toISOString();
  const payload = {
    recipientName: entry.recipientName.trim(),
    phone: entry.phone.replace(/[\s-]/g, ""),
    addressLine1: entry.addressLine1.trim(),
    addressLine2: (entry.addressLine2 || "").trim(),
    area: entry.area,
  };
  const existing = entry.id
    ? rows.find((row) => row.id === entry.id)
    : rows.find((row) => sameAddress(row, payload));
  if (existing) {
    const next = rows.map((row) => (row.id === existing.id
      ? { ...row, ...payload, label: entry.label.trim() || row.label, updatedAt: now }
      : row));
    writeLocal(userId, next);
    return sortRows(readLocal(userId));
  }
  const created: SavedAddress = {
    id: crypto.randomUUID(),
    label: entry.label.trim() || payload.recipientName,
    ...payload,
    updatedAt: now,
    source: "local",
  };
  writeLocal(userId, [created, ...rows]);
  return sortRows(readLocal(userId));
}
