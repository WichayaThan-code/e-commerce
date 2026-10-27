/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_URL?: string
  readonly VITE_FF_TERMS_VERSION_API?: string
  readonly VITE_FF_ADDRESS_ID_CHECKOUT?: string
  readonly VITE_FF_SHIPPING_COVERAGE_API?: string
  readonly VITE_FF_DELIVERY_ESTIMATE_API?: string
  readonly VITE_FF_ORDER_FILTERS_API?: string
  readonly VITE_FF_ORDER_NOTES_API?: string
  readonly VITE_FF_ORDER_TAGS_API?: string
  readonly VITE_FF_BULK_ORDERS_API?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
