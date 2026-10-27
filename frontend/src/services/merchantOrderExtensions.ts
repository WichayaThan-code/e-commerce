import { FEATURES } from "../config/features";
import { ApiError, request } from "./api";

export type OrderTag = { id: string; name: string; color: string | null; usageCount?: number };
export type OrderTagRequest = { name: string; color?: string | null };
export type InternalNoteRequest = { internalNote: string | null; expectedVersion: number };
export type InternalNoteResponse = { orderId: string; internalNote: string | null; version: number };
export type OrderTagsRequest = { tagIds: string[]; expectedVersion: number };
export type OrderTagsResponse = { orderId: string; tags: OrderTag[]; version: number };
export type BulkAction = "accept" | "process" | "ship" | "cancel";
export type BulkItem = { orderId: string; expectedVersion: number };
export type BulkRequest = {
  items: BulkItem[];
  reason?: string;
  status?: "READY_TO_PACK" | "PACKING" | "PACKED";
  carrierCode?: string;
  trackingNumbers?: Record<string, string>;
};
export type BulkTagsRequest = { items: BulkItem[]; addTagIds?: string[]; removeTagIds?: string[] };
export type BulkResultItem = {
  orderId: string;
  ok: boolean;
  code: string | null;
  message?: string | null;
  newVersion: number | null;
};
export type BulkResponse = {
  action: string;
  total: number;
  succeeded: number;
  failed: number;
  results: BulkResultItem[];
  replayed: boolean;
};

const base = (shopId: number) => `/api/merchant/shops/${shopId}`;

function disabled(): never {
  throw new ApiError("FEATURE_DISABLED", "Feature is disabled", 0);
}

function requireTags() {
  if (!FEATURES.orderTagsApi) disabled();
}

function requireNotes() {
  if (!FEATURES.orderNotesApi) disabled();
}

function requireBulk() {
  if (!FEATURES.bulkOrdersApi) disabled();
}

export function listTags(shopId: number) {
  requireTags();
  return request<OrderTag[]>(`${base(shopId)}/order-tags`, { cache: "no-store" }, true);
}

export function createTag(shopId: number, body: OrderTagRequest) {
  requireTags();
  return request<OrderTag>(`${base(shopId)}/order-tags`, { method: "POST", body: JSON.stringify(body) }, true);
}

export function updateTag(shopId: number, tagId: string, body: OrderTagRequest) {
  requireTags();
  return request<OrderTag>(`${base(shopId)}/order-tags/${tagId}`, { method: "PUT", body: JSON.stringify(body) }, true);
}

export function deleteTag(shopId: number, tagId: string) {
  requireTags();
  return request<void>(`${base(shopId)}/order-tags/${tagId}`, { method: "DELETE" }, true);
}

export function setOrderTags(shopId: number, orderId: string, body: OrderTagsRequest) {
  requireTags();
  return request<OrderTagsResponse>(
    `${base(shopId)}/orders/${orderId}/tags`,
    { method: "PUT", body: JSON.stringify(body) },
    true,
  );
}

export function updateInternalNote(shopId: number, orderId: string, body: InternalNoteRequest) {
  requireNotes();
  return request<InternalNoteResponse>(
    `${base(shopId)}/orders/${orderId}/internal-note`,
    { method: "PATCH", body: JSON.stringify(body) },
    true,
  );
}

export function bulkAction(shopId: number, action: BulkAction, body: BulkRequest, idemKey: string) {
  requireBulk();
  return request<BulkResponse>(
    `${base(shopId)}/orders/bulk/${action}`,
    { method: "POST", headers: { "Idempotency-Key": idemKey }, body: JSON.stringify(body) },
    true,
  );
}

export function bulkTags(shopId: number, body: BulkTagsRequest, idemKey: string) {
  if (!FEATURES.bulkOrdersApi || !FEATURES.orderTagsApi) disabled();
  return request<BulkResponse>(
    `${base(shopId)}/orders/bulk/tags`,
    { method: "POST", headers: { "Idempotency-Key": idemKey }, body: JSON.stringify(body) },
    true,
  );
}
