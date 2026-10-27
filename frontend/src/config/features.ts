/** Flags for backend features not shipped yet. Default false = frontend uses local/static behaviour only. */
export type FeatureFlags = {
  termsVersionApi: boolean        // POST /api/checkout accepts termsVersion
  addressIdCheckout: boolean      // POST /api/checkout accepts shippingAddressId (server resolves buyer_addresses)
  shippingCoverageApi: boolean    // GET /api/checkout/shipping-coverage
  deliveryEstimateApi: boolean    // GET /api/orders/{id}/delivery-estimate
  orderFiltersApi: boolean        // VITE_FF_ORDER_FILTERS_API   → GET orders?paymentMethod&carrierCode&hasTracking&tag
  orderNotesApi: boolean          // VITE_FF_ORDER_NOTES_API     → PATCH .../orders/{id}/internal-note
  orderTagsApi: boolean           // VITE_FF_ORDER_TAGS_API      → order-tags CRUD + PUT .../orders/{id}/tags
  bulkOrdersApi: boolean          // VITE_FF_BULK_ORDERS_API     → POST .../orders/bulk/{action}
  merchantNotificationsApi: boolean // VITE_FF_MERCHANT_NOTIFICATIONS_API → GET .../notifications
  productConfigurator: boolean    // VITE_FF_PRODUCT_CONFIGURATOR → CUSTOM product configurator (backend app.features.configurator.enabled)
}

const env = import.meta.env
const on = (v: unknown) => String(v ?? "").toLowerCase() === "true"

export const FEATURES: Readonly<FeatureFlags> = Object.freeze({
  termsVersionApi: on(env.VITE_FF_TERMS_VERSION_API),
  addressIdCheckout: on(env.VITE_FF_ADDRESS_ID_CHECKOUT),
  shippingCoverageApi: on(env.VITE_FF_SHIPPING_COVERAGE_API),
  deliveryEstimateApi: on(env.VITE_FF_DELIVERY_ESTIMATE_API),
  orderFiltersApi: on(env.VITE_FF_ORDER_FILTERS_API),
  orderNotesApi: on(env.VITE_FF_ORDER_NOTES_API),
  orderTagsApi: on(env.VITE_FF_ORDER_TAGS_API),
  bulkOrdersApi: on(env.VITE_FF_BULK_ORDERS_API),
  merchantNotificationsApi: on(env.VITE_FF_MERCHANT_NOTIFICATIONS_API),
  productConfigurator: on(env.VITE_FF_PRODUCT_CONFIGURATOR),
})

export const isFeatureOn = (key: keyof FeatureFlags) => FEATURES[key]
