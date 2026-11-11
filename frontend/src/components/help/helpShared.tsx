import type { ComponentType } from "react";
import {
  Box,
  CreditCard,
  Settings,
  ShieldCheck,
  ShoppingCart,
  Store,
  Truck,
  UserRound,
} from "lucide-react";
import EmptyState from "../feedback/EmptyState";
import type { HelpTopic } from "../../services/api";
import { helpPlainText } from "./helpHtml";
import "../../styles/shop-page.css";

export type IconType = ComponentType<{ className?: string }>;

export const TONES = ["coral", "blue", "green", "violet", "amber", "pink", "mint", "purple"] as const;

const ICONS: Record<string, IconType> = {
  shoppingcart: ShoppingCart,
  cart: ShoppingCart,
  creditcard: CreditCard,
  payment: CreditCard,
  truck: Truck,
  shipping: Truck,
  box: Box,
  package: Box,
  store: Store,
  shop: Store,
  userround: UserRound,
  user: UserRound,
  account: UserRound,
  shieldcheck: ShieldCheck,
  shield: ShieldCheck,
  settings: Settings,
  settings2: Settings,
};
const FALLBACK_ICONS: IconType[] = [ShoppingCart, CreditCard, Truck, Box, Store, UserRound, ShieldCheck, Settings];

export function iconFor(name: string, index: number): IconType {
  const key = (name || "").toLowerCase().replace(/[^a-z0-9]/g, "");
  return ICONS[key] ?? FALLBACK_ICONS[index % FALLBACK_ICONS.length];
}

/**
 * Display order for the help home: orders/shipping, returns/refunds, payment, account/security, products, sellers.
 * Seeded slugs (V50): ordering, payment, shipping, returns, merchant, account, security, troubleshooting.
 * Unknown slugs are placed by a title/slug heuristic and otherwise keep the server order at the end.
 */
const ORDER_RULES: [RegExp, number][] = [
  [/order|ordering|cart|purchase|สั่งซื้อ|คำสั่งซื้อ|订单|下单/i, 1],
  [/ship|deliver|track|จัดส่ง|ขนส่ง|配送|物流|发货/i, 2],
  [/return|refund|คืน|เงินคืน|退/i, 3],
  [/pay|billing|ชำระ|จ่าย|支付|付款/i, 4],
  [/account|login|profile|บัญชี|เข้าสู่|账户|账号/i, 5],
  [/secur|privacy|password|ความปลอดภัย|รหัสผ่าน|安全|隐私/i, 6],
  [/product|catalog|สินค้า|商品|产品/i, 7],
  [/merchant|seller|store|shop|ผู้ขาย|ร้านค้า|商家|卖家/i, 8],
];

function topicRank(topic: HelpTopic): number {
  const slugHit = ORDER_RULES.find(([pattern]) => pattern.test(topic.slug));
  if (slugHit) return slugHit[1];
  const titleHit = ORDER_RULES.find(([pattern]) => pattern.test(topic.title));
  return titleHit ? titleHit[1] : 99;
}

export function sortTopics(topics: HelpTopic[]): HelpTopic[] {
  return topics
    .map((topic, index) => ({ topic, index, rank: topicRank(topic) }))
    .sort((a, b) => a.rank - b.rank || a.index - b.index)
    .map((entry) => entry.topic);
}

export { useAsyncData as useHelpData } from "../../hooks/useAsyncData";

export function Lines({ text }: { text: string }) {
  return (
    <>
      {text.split("\n").map((line, index) => (
        <span key={index}>{line}</span>
      ))}
    </>
  );
}

export function HelpError({
  message,
  retryLabel,
  onRetry,
  className,
}: {
  message: string;
  retryLabel: string;
  onRetry: () => void;
  className?: string;
}) {
  return (
    <EmptyState variant="error" className={className} title={message} action={{ label: retryLabel, onClick: onRetry }} />
  );
}

const SESSION_KEY = "tsf_help_session";

/** Stable anonymous key for FAQ feedback (never contains personal data). */
export function helpSessionKey(): string {
  try {
    const existing = window.localStorage.getItem(SESSION_KEY);
    if (existing) return existing;
    const created =
      typeof window.crypto?.randomUUID === "function"
        ? window.crypto.randomUUID()
        : `s-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`;
    window.localStorage.setItem(SESSION_KEY, created);
    return created;
  } catch {
    return `s-${Date.now().toString(36)}`;
  }
}

/** Splits an answer into a lead sentence (answers the question straight away) and the remaining detail. */
export function splitAnswer(answer: string): { lead: string; rest: string } {
  const text = helpPlainText(answer);
  if (!text) return { lead: "", rest: "" };
  const sentence = text.match(/^(.+?[.!?。！？])(?=\s|$)/);
  if (sentence && sentence[1].length < text.length) {
    return { lead: sentence[1].trim(), rest: text.slice(sentence[1].length).trim() };
  }
  const space = text.indexOf(" ");
  if (space > 0 && space < 120) {
    return { lead: text.slice(0, Math.min(text.length, 160)).trim(), rest: "" };
  }
  return { lead: text.length > 160 ? `${text.slice(0, 157).trim()}…` : text, rest: "" };
}
