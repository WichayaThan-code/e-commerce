import type { CartActionResult, OrderItem } from "../../services/api";

type CartAdd = {
  add: (skuId: string, quantity: number) => Promise<CartActionResult | null>;
  addConfigured: (
    skuId: string,
    quantity: number,
    configuration: { selections: Record<string, unknown>; version?: number },
  ) => Promise<CartActionResult | null>;
};

export type BuyAgainResult = {
  attempted: number;
  added: number;
  adjusted: number;
  failed: number;
};

/** Re-add completed-order lines to the cart. Soft-fails unavailable / blocked SKUs. */
export async function buyAgainFromItems(cart: CartAdd, items: OrderItem[] | null | undefined): Promise<BuyAgainResult> {
  const lines = (items ?? []).filter((item) => item.skuId && item.quantity > 0);
  const result: BuyAgainResult = { attempted: lines.length, added: 0, adjusted: 0, failed: 0 };
  for (const item of lines) {
    try {
      const selections = item.configuration?.selections;
      const action = selections && Object.keys(selections).length > 0
        ? await cart.addConfigured(item.skuId, item.quantity, {
            selections,
            version: item.configuration?.version,
          })
        : await cart.add(item.skuId, item.quantity);
      if (action && action.appliedQuantity < 1) {
        result.failed += 1;
        continue;
      }
      result.added += 1;
      if (action?.adjusted) result.adjusted += 1;
    } catch {
      result.failed += 1;
    }
  }
  return result;
}
