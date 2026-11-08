import { shopPath } from "../services/shopRoutes";

/** Canonical absolute storefront URL for the current browser origin + public shop path. */
export function shopStorefrontUrl(slug: string, origin = typeof window !== "undefined" ? window.location.origin : ""): string {
  const clean = slug.trim();
  if (!clean || !origin) return "";
  return `${origin.replace(/\/$/, "")}${shopPath(clean)}`;
}

/** Local / private LAN hosts are not customer-reachable public links. */
export function isLocalOrPrivateHost(hostname = typeof window !== "undefined" ? window.location.hostname : ""): boolean {
  const host = hostname.trim().toLowerCase();
  if (!host) return true;
  if (host === "localhost" || host === "0.0.0.0" || host === "[::1]" || host === "::1") return true;
  if (host.startsWith("127.")) return true;
  if (host.startsWith("10.")) return true;
  if (host.startsWith("192.168.")) return true;
  if (/^172\.(1[6-9]|2\d|3[01])\./.test(host)) return true;
  return false;
}

export type StorefrontAccess = {
  canOpen: boolean;
  canSharePublic: boolean;
  reason: "ok" | "missing_slug" | "pending" | "rejected" | "suspended" | "local";
};

/** Public customers only reach APPROVED shops; merchants may still open a local/preview URL. */
export function storefrontAccess(status: string | null | undefined, slug: string | null | undefined, local = isLocalOrPrivateHost()): StorefrontAccess {
  if (!slug?.trim()) return { canOpen: false, canSharePublic: false, reason: "missing_slug" };
  if (status === "SUSPENDED") return { canOpen: true, canSharePublic: false, reason: "suspended" };
  if (status === "PENDING_REVIEW") return { canOpen: true, canSharePublic: false, reason: "pending" };
  if (status === "REJECTED") return { canOpen: true, canSharePublic: false, reason: "rejected" };
  if (status !== "APPROVED") return { canOpen: false, canSharePublic: false, reason: "pending" };
  if (local) return { canOpen: true, canSharePublic: false, reason: "local" };
  return { canOpen: true, canSharePublic: true, reason: "ok" };
}
