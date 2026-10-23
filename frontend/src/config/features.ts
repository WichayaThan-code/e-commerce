/** Flags for backend features not shipped yet. Default false = frontend uses local/static behaviour only. */
export type FeatureFlags = {
  termsVersionApi: boolean        // POST /api/checkout accepts termsVersion
  addressIdCheckout: boolean      // POST /api/checkout accepts shippingAddressId (server resolves buyer_addresses)
  shippingCoverageApi: boolean    // GET /api/checkout/shipping-coverage
  deliveryEstimateApi: boolean    // GET /api/orders/{id}/delivery-estimate
}

const env = import.meta.env
const on = (v: unknown) => String(v ?? "").toLowerCase() === "true"

export const FEATURES: Readonly<FeatureFlags> = Object.freeze({
  termsVersionApi: on(env.VITE_FF_TERMS_VERSION_API),
  addressIdCheckout: on(env.VITE_FF_ADDRESS_ID_CHECKOUT),
  shippingCoverageApi: on(env.VITE_FF_SHIPPING_COVERAGE_API),
  deliveryEstimateApi: on(env.VITE_FF_DELIVERY_ESTIMATE_API),
})

export const isFeatureOn = (key: keyof FeatureFlags) => FEATURES[key]
