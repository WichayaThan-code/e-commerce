# AB3 Handoff note — Supitcha (order / checkout / cart)

รอบนี้: **Public order tracking** + **Buy Now / QR / Share (OG)** ในหน้าสินค้า
ไม่มี migration ใหม่ (ล่าสุดยังเป็น V43) · ไม่ได้แก้ `AB2_handoff_note_supitcha.md`

## 1. Backend — order (โซนของ Supitcha)

| ไฟล์ | สถานะ | สรุป |
|---|---|---|
| `order/OrderRepository.java` | แก้ (+1 method) | เพิ่ม `Optional<Order> findFirstByOrderNumber(String)` (เลข order unique ทั้งระบบอยู่แล้ว) — additive ไม่กระทบ query เดิม |
| `order/PublicOrderDtos.java` | ใหม่ | `LookupRequest{orderNumber, contact}` + `PublicOrderStatusView` (masked: ไม่มี id/buyerUserId/slip/note/ที่อยู่เต็ม/ติดต่อร้าน) |
| `order/PublicOrderLookupService.java` | ใหม่ | ตรวจ orderNumber (trim+upper) + contact: มี `@` = อีเมลบัญชีผู้ซื้อ (`users.email`, lower) ไม่งั้น = เบอร์ใน `order_addresses.phone` (ตัดเหลือตัวเลข, `66xxxxxxxxx`→`0xxxxxxxxx`) เทียบด้วย `MessageDigest.isEqual`; ทุกกรณีผิด = 404 `ORDER_NOT_FOUND` เหมือนกัน; สร้าง view จาก `OrderQueryService.detail(order, OrderActor.system())` + `ShipmentService.get(system)` (เช็ค `ShipmentRepository` ก่อน เพื่อไม่ให้ exception ทำ tx read-only เป็น rollback-only) |
| `order/PublicOrderLookupRateLimiter.java` | ใหม่ | in-memory: ล้มเหลว ≥5 ครั้ง/15 นาที ต่อ IP **และ** ต่อเลข order → 429 `TOO_MANY_LOOKUPS`; นับเฉพาะครั้งที่ล้มเหลว; มี `reset()` สำหรับเทสต์ |
| `order/PublicOrderLookupController.java` | ใหม่ | `POST /api/public/orders/lookup` `@PublicEndpoint`, IP = `request.getRemoteAddr()` |

Masking: ชื่อ `ส***`, เบอร์ `xxx-xxx-1234`, ที่อยู่เหลือ province + postalCode; tracking event ส่งแค่ id/eventType/status/location/eventAt (ไม่ส่ง note/actor)

## 2. Backend — catalog (ไม่ใช่โซน order แต่จดไว้)
- `catalog/ProductShareController.java` (ใหม่) `GET /share/p/{shopSlug}/{productSlug}` `@PublicEndpoint`: HTML สั้น มี OG tags (escape ด้วย `HtmlUtils`) + meta refresh ไป `APP_FRONTEND_URL/shop/{slug}/product/{slug}`; สินค้าไม่พบ/ไม่ publish → 404 + redirect ไปหน้าร้าน; ใช้กฎเดียวกับ `ProductService.publicGet`

## 3. Frontend — cart / checkout (โซนของ Supitcha)
- `contexts/CartContext.tsx`: เพิ่ม `buyNow(skuId, qty, configuration?)` (additive) = add (หรือ addConfigured) → เลิกเลือก line อื่นที่ selected → เลือกเฉพาะ line เป้าหมาย; ใช้ `seq` ticket เดิม ไม่แตะ method เดิม
- `pages/ShopProductPage.tsx`: ปุ่ม "ซื้อทันที" เดิมไป `/cart` → ตอนนี้ใช้ `cart.buyNow` แล้วไป `/checkout`; รองรับ `?buy=1` (จาก QR) auto Buy Now ครั้งเดียว — guest ถูกพาไป login พร้อม `next` แล้วกลับมาที่หน้าเดิม
- `components/shop/ProductConfiguratorPanel.tsx`: prop ใหม่ (optional) `onBuyNow`; ไม่ส่ง = เหมือนเดิม; CSS ปุ่มอยู่ใต้ `.shop-themed[data-template="CUSTOM"]` เท่านั้น
- `pages/CheckoutSuccessPage.tsx`: เพิ่มลิงก์ "ติดตามคำสั่งซื้อ" ต่อ order → `/track-order?no=...`
- ใหม่: `pages/OrderTrackingPage.tsx` + `styles/order-tracking.css` (route public `/track-order`, ไม่เรียก merchant API, ไม่พึ่ง AuthContext; ไม่ได้ reuse `OrderShipmentCard` เพราะผูกกับ buyer API — ทำ timeline ในหน้าเอง)
- ใหม่: `components/shop/ProductQrModal.tsx` + `styles/product-qr.css` (`qrcode.react`, โหมดหน้าสินค้า / ซื้อทันที `?buy=1`, ดาวน์โหลด PNG)
- `services/api.ts`: `publicOrderApi.lookup`, type `PublicOrderStatus`, `productShareUrl()` (คืน null เมื่อ API เป็น localhost → ใช้ URL หน้าเว็บแทน)
- ลิงก์: Header (เมนูบัญชี), Footer, i18n th/en/zh (`public.tracking.*`, `public.shop.view.qr*`, `public.footer.trackOrder`, `common.account.trackOrder`)
- `package.json`: เพิ่ม dependency `qrcode.react`

## 4. เทสต์
- `OrderIntegrationTests.publicOrderLookupVerifiesContactMasksPiiAndRateLimits` — เบอร์ format อื่น/อีเมลตัวใหญ่ผ่าน, ผิด = 404 เหมือนกัน, ไม่มี PII ดิบ, 429 ต่อ IP และต่อเลข order
- `OrderIntegrationTests.productShareLinkRendersEscapedOpenGraphAndRedirects` — OG escape + 404
- `EndpointAccessRuleCoverageTests` ผ่าน (endpoint ใหม่มี `@PublicEndpoint`)

## 5. ข้อจำกัด / สิ่งที่ต้องรู้
- OG preview ของ Facebook/LINE ใช้ได้จริงเมื่อ backend อยู่บนโดเมน public (`VITE_API_URL`) และตั้ง `APP_FRONTEND_URL` ถูกต้อง; บน localhost ปุ่มแชร์ใช้ URL หน้าเว็บตรง ๆ และ QR ใช้ได้เฉพาะเครื่อง/วง LAN
- Buy Now ยังต้อง login (ไม่มี guest checkout); ถ้าสินค้านั้นมีใน cart อยู่แล้ว จำนวนจะ **บวกเพิ่ม** ตามพฤติกรรม `cart.add` เดิม
- Buy Now จะ unselect line อื่นใน cart (ไม่ลบ) — กลับไปเลือกใหม่ได้ที่ `/cart`
- Rate limiter เป็น in-memory ต่อ instance (รีสตาร์ทแล้วรีเซ็ต; ถ้าหลาย instance ต้องย้ายไป Redis); IP ใช้ `getRemoteAddr()` — หลัง reverse proxy ต้องตั้ง `server.forward-headers-strategy`
- order ที่ buyer ไม่มีเบอร์/อีเมลตรงจะค้นไม่เจอ (ตั้งใจ)

## UI: ปรับหน้า Checkout ให้ดีไซน์เหมือนหน้า Cart (UI-only)
- CheckoutPage.tsx: เพิ่ม className `checkout-cartlook` ที่ <main> เท่านั้น (ไม่แตะ state/handler/API/CartContext)
- checkout.css: เพิ่มบล็อก selector ใต้ `.checkout-cartlook` ปรับสี/ขอบ/มุมโค้ง/ขนาด input/ปุ่มคูปองแบบ outline/รูปสินค้า 86px + เส้นคั่น + ราคาแดง ให้ตรงกับ cart.css
- ไม่แก้ cart.css; CheckoutSuccessPage (ใช้ .checkout-page) ไม่ได้รับผลกระทบ; สีหลักใช้ var(--shop-primary) เพื่อรองรับธีม
- ย้อนกลับ: ลบ className `checkout-cartlook` ออกจาก <main> จะกลับเป็นหน้าตาเดิมทันที
- รอบ 2 (UI): ใส่สไตล์ตั๋วคูปอง (.cart-offer) ใน checkout เป็น grid แบบเดียวกับ cart, ขยายฟอนต์การ์ด/ชื่อสินค้า/หัวร้าน/ยอดรวมร้าน, จัดระยะตัวเลือกจัดส่ง-ชำระเงิน-เงื่อนไข (เฉพาะ .checkout-cartlook)
- รอบ 3 (UI): แก้การ์ดซ้อน — .cart-main-column จาก cart.css มีกรอบ/พื้นขาวครอบ .checkout-card อีกชั้น จึงล้างกรอบ wrapper ใต้ .checkout-cartlook และเว้นระยะการ์ด 16px

## Recommendations (/recommendations) — เชื่อมข้อมูลจริง + V44 product_views
> ไม่ใช่โซน order/checkout แต่จดไว้เพราะมี migration ใหม่และหน้า recommend ใช้ CartContext เพิ่มตะกร้า
- **DB (V44__product_views.sql, additive):** ตารางใหม่ `product_views` (id uuid, product_id → products ON DELETE CASCADE, user_id → users ON DELETE CASCADE nullable, session_hash varchar(64) = SHA-256 ของ session ไม่เก็บค่าดิบ, viewed_at timestamptz) + CHECK ต้องมี user_id หรือ session_hash + index (user_id, viewed_at desc), (session_hash, viewed_at desc), (product_id). **ต้อง restart backend ให้ Flyway รัน V44**
- **Backend ใหม่ (package catalog):** `ProductView` (entity), `ProductViewService` (บันทึกวิว dedupe 30 นาที/ผู้ชม/สินค้า, rate limit 120 ครั้ง/10 นาที/IP → 429, ไม่ส่ง id กลับ), `RecommendationService` (คะแนน = log1p(ยอดขายตามกติกา SOLD_ORDER_FILTER เดียวกับ best-sellers) + rating × น้ำหนักจำนวนรีวิว + boost หมวดที่เคยดู), `PublicRecommendationController` (@PublicEndpoint)
  - `GET /api/products/recommendations` — categoryId, minPrice, maxPrice, minRating, brands (comma), sort = featured|best|price_asc|price_desc|rating|newest, page, size(≤100); token ไม่บังคับ ถ้ามี/มี header `X-View-Session` จะ personalize ไม่มีก็ใช้ ranking ทั่วไป
  - `POST /api/products/views` body `{productId}` → 204 เสมอ (สินค้าไม่ publish/ไม่รู้จัก = ไม่บันทึก)
  - `GET /api/products/recently-viewed` — ต้อง login (401 ถ้าไม่มี token), ≤20 รายการ ล่าสุดก่อน
  - response ใช้รูปแบบเดียวกับ best-sellers (PublicBestSellerResponse) ไม่มี buyer/user id
- **ProductService:** เพิ่มเมธอด public `publicCards(ids, sold, rankOffset, locale)` (reuse toPublic + review aggregates) — ไม่แก้ logic เดิม
- **Frontend:** `api.ts` เพิ่ม `recommendationApi` (list / recordView / recentlyViewed) แนบ token เฉพาะตอน login แล้ว, 401 จะลองใหม่แบบไม่ใส่ token ไม่ redirect; session id เก็บใน localStorage `tsf.viewSession`. `RecommendedProductsPage` เปลี่ยนจาก mock เป็นข้อมูลจริง (คง markup/CSS เดิม), ซ่อนราคาเดิม/ส่วนลด/rating ถ้าไม่มีข้อมูล, ตัด tag ปลอม. `ShopProductPage` เรียก recordView เมื่อโหลดสินค้าสำเร็จ (fire-and-forget)
- **CartContext: ไม่ได้แก้** — หน้า recommend ใช้ `useOptionalCart().add(skuId, 1)` / `loginPathForCart()` ที่มีอยู่ (logic เดียวกับ BestSellerPage: guest → login, หลาย SKU → ไปหน้าสินค้า)
- **ข้อจำกัด:** ยังไม่มีระบบ favorite ฝั่ง backend — หัวใจบังคับ login แต่เก็บใน state ของหน้า (เหมือน BestSellerPage); ตัวกรองแบรนด์สร้างจากแบรนด์ของสินค้าที่โหลดมา; rate limiter เป็น in-memory ต่อ instance
- **เทสต์:** `RecommendationIntegrationTests` (ranking/filters/anonymous, dedupe + hash + privacy, recently-viewed 401/200, personalization, rate limit 429)
- **แก้ CORS (รอบตรวจสด):** หน้า /recommendations ขึ้น "โหลดสินค้าแนะนำไม่สำเร็จ" เพราะ browser preflight ถูกปฏิเสธ (403) — header `X-View-Session` ไม่อยู่ใน allowed headers. แก้ `SecurityConfig` เพิ่ม `X-View-Session` ใน `setAllowedHeaders` + เทสต์ `corsPreflightAllowsViewSessionHeader` (ต้อง restart backend)

## Theme GENERAL ตามสเปก Themes (storefront /shop/:slug)
> ร้าน GENERAL เดิมเปลี่ยนหน้าตาทันที (CSS/คอมโพเนนต์) โดยไม่มี flag v2; FASHION / FOOD_BEVERAGE / CUSTOM คงเดิมทั้งหน้าตาและ validation; ไม่มี migration ใหม่
- **ThemePresets.generalConfig():** สีตามสเปก primary #0a5fd6, secondary #08245d, background #ffffff, text #132b50, accent #f28c28 + คีย์ใหม่ `borderStrong` #7d8ba0, `sale` #c4271b, `focus` #0a5fd6; ฟอนต์ IBM_PLEX_SANS_THAI; ลำดับ section Hero → Trust → Categories → Featured → Banners → Best sellers → Intro (เปิด) → Catalog (NEW_ARRIVALS / HOURS ปิดไว้ท้ายสุด)
- **customConfig():** แยกเป็น `legacyGeneralConfig()` (ค่า GENERAL เดิม #087bf5 / NOTO_SANS_THAI / ลำดับเดิม) เพื่อให้ค่าเริ่มต้น CUSTOM ไม่เปลี่ยน
- **ShopThemeService:** `stripAg1Tokens(config, template)` - GENERAL เก็บ `borderStrong` / `sale` / `focus` (ค่าว่างถูกตัด) และตรวจเป็น #RRGGBB (`THEME_COLOR_INVALID` 400) ส่วน FASHION / FOOD_BEVERAGE ตัดคีย์เหล่านี้ทิ้งเงียบๆ; surface / muted / border / cta* ยังถูกตัดเหมือนเดิมสำหรับ template เดิม (frontend คำนวณให้)
- **ร้าน GENERAL ที่บันทึกไว้แล้ว:** สี / ฟอนต์ / ลำดับ section ที่บันทึกไว้ยังคงเดิม (ไม่เขียนทับข้อมูลร้าน); preset ใหม่มีผลเมื่อร้านกดรีเซ็ต/เลือก GENERAL ใหม่
- **เทสต์:** `ShopThemeIntegrationTests.generalPresetFollowsThemesSpecAndKeepsSpecColorRoles` (preset ตามสเปก, CUSTOM ยัง NOTO, GENERAL เก็บ sale, sale ผิดรูปแบบ 400, FASHION ตัดคีย์ใหม่)
- **ต้อง restart backend** เพื่อให้ preset ใหม่มีผล

## AB3 รอบ 2 — GENERAL hero แก้ข้อความได้จากหน้าตั้งค่าธีม (backend)
- ThemePresets.generalConfig(): hero มีค่าเริ่มต้นภาษาไทย eyebrow="ส่งตรงจากชุมชนทั่วไทย", title="ของดีทั่วไทย ส่งตรงถึงบ้านคุณ", subtitle, ctaLabel="ช้อปเลย", ctaSecondaryLabel="ดูหมวดหมู่", ctaLink/ctaSecondaryLink="" (ว่าง = frontend ไปหน้าสินค้า/หมวดหมู่ของร้าน)
- คีย์ใหม่เฉพาะ GENERAL: config.hero.eyebrow, ctaSecondaryLabel, ctaSecondaryLink (ThemePresets.GENERAL_HERO_KEYS) ธีมอื่นและ CUSTOM จะถูกตัดทิ้งตอนบันทึก ไม่ใช้คีย์ ctaSecondary เดิมของ CUSTOM
- Validation (GENERAL): eyebrow ≤60 (THEME_HERO_EYEBROW_TOO_LONG), ctaSecondaryLabel ≤120, ctaSecondaryLink ต้องว่าง/เส้นทางภายใน/https (THEME_HERO_LINK_INVALID), eyebrow/title/subtitle/ctaLabel/ctaSecondaryLabel ห้ามมี < หรือ > (THEME_HERO_TEXT_INVALID)
- ไม่มี migration ใหม่ ต้องรีสตาร์ต backend; mvnw test 271 ผ่าน 0 fail
- Frontend: /shop/:slug ซ่อน Footer.tsx ของเว็บ (PublicLayout ใช้ useMatch("/shop/:slug/*"))


## 2026-10-29 — GENERAL hero layout MEDIA_FULL (themes port)
- `ShopThemeChrome.stripForNonCustom(config, keepMediaFull)` overload; `ShopThemeService` passes `true` for GENERAL (save + read), so GENERAL keeps `hero.layout` / HERO section variant `MEDIA_FULL`. FASHION / FOOD_BEVERAGE still fall back to FULL. CUSTOM-only hero extras (background, stickers, overlay, heightVh, ...) are still stripped for GENERAL.
- Allowed GENERAL hero layouts: FULL (default), STRIP, MEDIA_FULL; anything else -> THEME_HERO_LAYOUT_INVALID (message updated to list all three).
- Test: `ShopThemeChromeIntegrationTests` non-CUSTOM strip test now expects MEDIA_FULL for GENERAL.
- No migration. Backend restart required.

## อัปเดต 30/10/2026 — Hero slides (backend) + GENERAL catalog pages
- Backend: hero.slides ใน theme config JSON (สูงสุด 5 รูป, ทุก template) — ShopThemeMediaService.prepareHeroSlides/resolveHeroSlides, key slideMediaId (กันลบรูปที่ใช้อยู่), error THEME_HERO_SLIDES_INVALID / THEME_HERO_SLIDES_TOO_MANY
- อัปโหลดรูป (IMAGE) ได้ทุก template; VIDEO/FONT ยังเป็น CUSTOM เท่านั้น
- ไม่มี migration ใหม่ (เก็บใน config JSON) — ต้อง restart backend
- Tests: ShopHeroSlidesIntegrationTests (ใหม่), ShopThemeMediaIntegrationTests.nonCustomThemeUploadsImagesOnly — mvnw test 273 tests, 0 failures
- Frontend: หน้า products/category/search ของ GENERAL ใช้ดีไซน์ artifact (TsfCatalogPage), grid 4/3/2, PromoBanners หลัง Featured
- ค้าง: UI สไลด์ hero (HeroSlider + ช่องแก้ไขใน editor)
## อัปเดต 30/10/2026 — GENERAL: รูปแบบ "สินค้าทั้งหมด" (GRID / LARGE / MENU)
- เก็บใน theme config เดิม: sections[key=CATALOG].variant — backend ตรวจ enum อยู่แล้ว (ShopSectionVariant.allowedFor CATALOG = GRID/LARGE/MENU, ค่าอื่น THEME_SECTION_VARIANT_INVALID)
- ไม่มีการแก้ backend รอบนี้ ไม่มี migration ไม่ต้อง restart backend
- Frontend: editor (/merchant/store?tab=theme) มีชุดปุ่มเลือก 3 แบบสำหรับ GENERAL; หน้าแรกร้านแสดงตาม artifact ProductSection, โหลดสินค้าทั้งหมดแบบเลื่อนอัตโนมัติ ไม่มีปุ่ม ดูทั้งหมด

## 2026-10-30: PublicProductResponse.createdAt

- `CatalogDtos.PublicProductResponse` เพิ่มฟิลด์ `Instant createdAt` (จาก `AuditedEntity.createdDateTime`) ใน `ProductService.toPublic`; คอนสตรัคเตอร์เดิม (17/18 args) ยังใช้ได้ (createdAt = null), เพิ่ม `withCreatedAt`.
- ไม่มี migration ใหม่ (Flyway ยังคง V44).
- Frontend GENERAL ProductCard แสดง badge "ใหม่" เมื่อ createdAt ไม่เกิน 14 วัน.
- Test: `ProductCatalogIntegrationTests` ตรวจ `$.items[0].createdAt` ไม่ว่าง.

## 2026-10-30 — Fashion theme port (tsf tree)
- ใช้ helper `usesTsfTree()` / `tsfThemeName()` / `tsfThemeDefaults()` ใน `theme/shopTheme.ts` (TSF_TREE_TEMPLATES = GENERAL, FASHION) แทนการเช็ค `=== "GENERAL"`
- ShopThemeShell: data-theme = general/fashion, data-btn/data-card อ่านจาก config.style, data-ratio ค่าเริ่มต้น Fashion = PORTRAIT
- Backend: FASHION รับสี borderStrong/sale/focus (ตรวจ THEME_COLOR_INVALID), preset Fashion ใหม่ตาม artifact (Prompt, radius 0, PILL, FLAT)
- ShopPage Fashion เรียงตาม sections; หมวดหมู่แบบ tile; INTRO/HOURS ข้าม
- หลัง deploy: restart backend และกด reset/เลือก template Fashion ใหม่ที่ /merchant/store?tab=theme

## 2026-10-30 ธีม Food & Beverage + เรื่องราวร้าน (StoryBlock) + HoursCard

- FOOD_BEVERAGE joins the tsf tree (data-theme="food"): preset = artifact food tokens (ThemePresets.foodBeverageConfig, extra colours borderStrong/sale/focus), chip categories, SQUARE images, ShopPage follows config section order (like FASHION).
- HOURS section (all tsf themes): TsfHoursCard - day table (today bold), open/closed pill in Asia/Bangkok, Google Maps search link from address, call button only with the published phone; hidden when no hours.
- INTRO section (GENERAL/FASHION/FOOD_BEVERAGE): config.intro {eyebrow,title,body,ctaLabel,ctaLink,introMediaId,reverse}; plain text only (no < >), ctaLink internal path or http(s); image = shop IMAGE media (in-use protected); body falls back to the shop description; hidden when empty. Editor panel เรื่องราวร้าน (th/en/zh). CUSTOM drops intro.
- Colours of all GENERAL/FASHION/FOOD_BEVERAGE shops were reset to the preset colours (colours only); backup %TEMP%\tsf_colors_before_20261030.json.
- Tests: mvnw test 275 (1 known time flake in CartIntegrationTests passes on rerun); tsc clean.

## 2026-10-30 (late) - favourites, hero text stickers, trust sub-lines, announcement bar, seed themes
- V45__user_favorites.sql: user_favorites(user_id, product_id, created_at) + JPA mapping UserFavorite; FavoriteService/Controller at /api/me/favorites (GET ?shopSlug, POST {productId} 201/200, DELETE /{id} 204). Live products only, no user ids in responses.
- Frontend: hooks/useFavorites.ts (shared store, guests -> login), useShopCatalog uses it, new /shop/:slug/favorites page (ShopFavoritesPage), tab-bar heart links there.
- Theme: GENERAL hero.badges (max 3 x 24, plain text, MEDIA_FULL only, decorative, CSS .tsf-hero-badge); trust.items[].sub (80) overrides the i18n sub-line; config.announcement.items (max 2 x 120) for the tsf announcement bar, falls back to trust labels. Codes THEME_HERO_BADGES_INVALID / THEME_TRUST_SUB_INVALID / THEME_ANNOUNCEMENT_INVALID. Editor fields added.
- CartIntegrationTests coupon flake: startLocal now formatted with seconds (ISO_OFFSET_DATE_TIME).
- Data: seed-baan-snack (FOOD_BEVERAGE) and seed-shine-jewelry (FASHION) sections/font/style/hero.layout reset to preset; banners, hero texts, intro, colors kept. seed-urban-style got Thai hero title/subtitle/CTA; banners still empty (no images).
- Added public/favicon.svg + index.html link (the 375px 404 was /favicon.ico).
- Backend restart required for V45 + new endpoints/validation.

## Item 2 — Hero layouts แบ่งครึ่ง (SPLIT) / โปสเตอร์ (POSTER) (31 ต.ค. 2026)
- Backend: ShopSectionVariant เพิ่ม SPLIT, POSTER (อนุญาตสำหรับ HERO ทุก template); ไม่ถูกแปลงเป็น FULL สำหรับ non-CUSTOM (เฉพาะ MEDIA_FULL ที่ยัง fallback เป็น FULL ยกเว้น GENERAL). อัปเดตข้อความ THEME_HERO_LAYOUT_INVALID. ไม่มี Flyway (config เป็น JSON). ThemePresets ไม่เปลี่ยน (F&B ยังเป็น STRIP).
- Frontend: HeroLayout เพิ่ม SPLIT/POSTER (shopTheme.ts, api.ts). TsfHero (GENERAL/FASHION/FOOD_BEVERAGE): SPLIT = ข้อความด้านหนึ่ง รูป/สไลด์อีกด้าน บนพื้น surface, มือถือซ้อนรูปบน (container >= 720px เป็น 2 คอลัมน์); POSTER = ตัวอักษรใหญ่กลางภาพ (ใช้โครง poster เดิม + class tsf-hero-poster-type) และแสดง badges ของ GENERAL เป็นสติกเกอร์. CSS ใน tsf-general.css + hero-slider.css.
- Editor: segmented ส่วนหัวร้าน = เต็มจอ / แบ่งครึ่ง / โปสเตอร์ + ตัวเลือกเดิม แถบ (STRIP) และ เต็มจอพร้อมมีเดีย (MEDIA_FULL เฉพาะ CUSTOM/GENERAL) เพื่อร้านเดิมยังใช้ได้. i18n th/en/zh: storeTheme.variants.SPLIT/POSTER.
- Tests: ShopHeroSlidesIntegrationTests.heroSplitAndPosterLayoutsAreKeptForEveryTsfTemplate (SPLIT/POSTER + slides ทุก template, STRIP/MEDIA_FULL เดิม, POSTER+badges, ค่าไม่ถูกต้อง -> THEME_HERO_LAYOUT_INVALID).
- ต้อง restart backend ก่อนทดสอบในเบราว์เซอร์ (enum ใหม่). ยังไม่ได้ตรวจในเบราว์เซอร์: 375/768/1280 ของทั้ง 3 ธีม, สไลด์ใน SPLIT/POSTER.

## Hero พรีวิวในหน้าแก้ไขธีม (HeroSection.tsx)

- แก้ `frontend/src/components/shop/sections/HeroSection.tsx` ให้พรีวิวในหน้า /merchant/store/theme ใช้ Hero ตัวเดียวกับหน้าร้านจริง สำหรับเทมเพลต GENERAL, FASHION และ FOOD_BEVERAGE
- พรีวิวจึงแสดงเลย์เอาต์ FULL / SPLIT / POSTER / STRIP / MEDIA_FULL และสไลด์ตรงกับหน้าร้าน
- หน้าร้านใช้สไลด์แรกของ Hero แทนรูปปก (coverUrl) และใช้ coverUrl เฉพาะร้านที่ยังไม่มีสไลด์
- ไม่มีการเปลี่ยน backend หรือ schema

## ฟอนต์หัวข้อ/เนื้อหา + ตำแหน่งโลโก้ การจัดข้อความ สีเน้น (ทุกเทมเพลต)

- config ใหม่ (ไม่ต้องใช้ Flyway): `fontBody` (ฟอนต์เนื้อหา, ไม่บังคับ) และ `brand: { logoPosition: LEFT|CENTER, textAlign: LEFT|CENTER }`
- `font` เดิม = ฟอนต์หัวข้อ; ถ้าไม่มี `fontBody` จะใช้ `font` เป็นทั้งหัวข้อและเนื้อหา (ธีมเก่าแสดงผลเหมือนเดิม)
- Backend (ShopThemeService): รับ `fontBody`/`brand` ทุกเทมเพลต, ตรวจ `fontBody` (THEME_FONT_INVALID), `brand.*` (THEME_TOKEN_INVALID), สีเน้น `colors.accent` ต้องเป็น hex ทุกเทมเพลต
- หน้าแก้ไขธีม แท็บแบรนด์: เลือกฟอนต์หัวข้อ + ฟอนต์เนื้อหา, สีเน้นแสดงทุกเทมเพลต, ตำแหน่งโลโก้ (CUSTOM ยังใช้ header.layout), แท็บ Hero: การจัดข้อความ (CUSTOM ยังใช้ hero.textAlign)
- หน้าร้าน tsf: `--shop-font-body` -> `--tsf-font-body`, header `data-layout` LOGO_CENTER, hero ทุก layout มี `data-align`
- เทสต์: brandFontBodyAndAccentForEveryTemplate (backend รวม 279 ผ่าน), tsc ผ่าน
- หมายเหตุ: backend ที่รันอยู่ต้องรีสตาร์ทเพื่อให้ validation ใหม่มีผล

## Merchant Dashboard: สรุปยอด + เลือกช่วงวันที่ (A1–A11, 1 พ.ย. 2026)

- Endpoint ใหม่: `GET /api/merchant/shops/{shopId}/dashboard/summary` (สิทธิ์ `ORDER_READ`, อ่านอย่างเดียว, ไม่มี migration)
  - `MerchantDashboardController` + `MerchantDashboardService` (แพ็กเกจ `com.thaishopfun.order`)
  - พารามิเตอร์: `range=today|7d|30d|1y` หรือ `from`/`to` (YYYY-MM-DD วันไทย รวมปลายทั้งสองด้าน, สูงสุด 3 ปี) หรือ `date` (แบบเดิม); ไม่ส่งอะไร = วันนี้
  - ผิดรูปแบบ / from > to / เกิน 3 ปี / ส่ง from อย่างเดียว / range ไม่รู้จัก → 400
  - คืน `sales`, `paidOrderCount`, `orderCount` ตามช่วง + `previousSales`/`previousOrderCount` ของช่วงก่อนหน้าความยาวเท่ากัน; `pendingOrders` เป็นค่าปัจจุบัน; ยังมี `todaySales`/`todayOrderCount` ให้ client เก่า
- กติกา: ตัดวันตาม Asia/Bangkok จาก `placed_at`; ยอดขาย = `payment_status=PAID` และไม่ใช่ CANCELLED/REFUNDED; รอดำเนินการ = PAID_AWAITING_MERCHANT_CONFIRM, MERCHANT_CONFIRMED, READY_TO_PACK, PACKING, PACKED
- สิทธิ์: ไม่ใช่สมาชิกร้าน → 404 SHOP_NOT_FOUND (ตามมาตรฐานโปรเจกต์), สมาชิกที่ไม่มี ORDER_READ → 403
- Frontend: `MerchantDashboardPage.tsx` แถบเลือกช่วง (วันนี้ / 7 วัน / 30 วัน / 1 ปี / กำหนดเอง) แทนปุ่มวันที่เดิม, ชื่อการ์ดเปลี่ยนตามช่วง, แสดง % เทียบช่วงก่อนหน้าเล็กๆ; สไตล์ `.merchant-range*` ใน `merchant-dashboard.css`; `merchantDashboardApi.summary(shopId, period)` ใน `api.ts`
- เทสต์: `MerchantDashboardSummaryTests` (รอยต่อวัน, สถานะ, แยกร้าน, ช่วงวัน, 400, 403/404/401)
- ยอดขายนับรวมออเดอร์ที่ชำระแล้วแต่ร้านยังไม่ยืนยัน; สินค้าใกล้หมดยังเป็นค่าปัจจุบัน

## AB3 — Dashboard insights: sales chart + Top 5 (2026-11-01)
- Backend: `GET /api/merchant/shops/{shopId}/dashboard/insights` (ORDER_READ) in `MerchantDashboardController` / `MerchantDashboardService.insights()`; same range parsing (date / range / from-to) and paid rule as summary (`payment_status='PAID'`, not CANCELLED/REFUNDED)
- Response: `bucket` (day <=31 days, week <=186 days starting Monday and clipped to range, otherwise month), zero-filled `series` (start, end, sales, orders), `paymentMethods`, `topProducts` (top 5 by sales then qty)
- Bucketing is done in Java (no Postgres-only SQL) so H2 tests and Postgres match
- Frontend: `MerchantDashboardPage.tsx` sales chart (GMV real; fee and net = 5% labelled ประมาณการ; hover tooltip; empty state) and Top 5 (image or 📦, empty state); `merchantDashboardApi.insights()` in `api.ts`
- Not wired yet: payment-method donut and notifications panel (still sample)
- Tests: `MerchantDashboardSummaryTests.insightsSeriesPaymentMethodsTopProductsAndValidation` (buckets, top products, cancelled/other shop excluded, 400/404/401)

## AB3 — Admin Dashboard B1+B4+B5 (2026-11-01)

- Route (B1): `/admin/dashboard` แสดง `AdminDashboardPage`; `/admin` redirect แบบ replace ไป `/admin/dashboard`; `homePath` ของ admin, ลิงก์หน้าแรกใน `AdminSidebar`, และ `ROUTE_PERMISSIONS['/admin/dashboard']` = `PLATFORM_SHOP_READ` (สิทธิ์เดิมของแดชบอร์ดแอดมิน)
- Backend (B5): `GET /api/admin/dashboard/summary` ใน `AdminDashboardController` / `AdminDashboardService` (แพ็กเกจ `com.thaishopfun.admin`)
  - สิทธิ์: `@RequirePermission("PLATFORM_SHOP_READ")` — ไม่ล็อกอิน 401, ไม่มีสิทธิ์ 403
  - พารามิเตอร์: range=today|7d|30d|1y หรือ from/to (YYYY-MM-DD วัน Asia/Bangkok รวมปลาย, สูงสุด 3 ปี); ไม่ส่ง = วันนี้; รูปแบบผิด → 400 ตาม ValidationException
  - ยอดขายรวมทุกร้าน: payment_status=PAID และไม่ใช่ CANCELLED/REFUNDED ตาม placed_at (กฎเดียวกับ merchant)
  - คืน totalSales, orderCount, paidOrderCount, ยอดช่วงก่อนหน้าความยาวเท่ากัน, series (day/week/month + เติม 0), topShops (5 ร้าน: shopId/name/slug/sales/orderCount — ไม่มีเบอร์/อีเมล), counts (ร้านแยกสถานะ ACTIVE→APPROVED ตาม ShopStatusConverter, pendingApplications = ร้าน PENDING_REVIEW, usersTotal / usersNewInPeriod), recentOrders 8 รายการ (ไม่มี buyer userId/phone/email)
- Frontend (B4): `adminDashboardApi.summary` + types ใน `api.ts`; `AdminDashboardPage` ผูก KPI/กราฟ/Top 5/ออเดอร์ล่าสุดกับข้อมูลจริง ช่วงเริ่มต้น 7d ไม่เพิ่ม UI เลือกวันที่; ช่องทางขาย / ค่าธรรมเนียม / ผู้ใช้ใหม่ / ประกาศ ยังเป็นตัวอย่างและติดป้าย "ตัวอย่าง"; คง CSS `admin-dashboard.css` เดิม
- เทสต์: `AdminDashboardSummaryTests` (ยอดข้ามร้าน, รอยต่อวันไทย, ไม่นับยกเลิก/คืนเงิน/ยังไม่ชำระ, topShops, counts, 400/403/401, ไม่มี phone/email/userId ใน JSON, ล้างใน @AfterEach)
- ไม่มี migration / ไม่แก้ schema; ต้อง restart backend ก่อนลองในเบราว์เซอร์
- ค้างไว้: B6 ตัวเลือกช่วงวันที่บนแอดมิน, B7 ค่าธรรมเนียมจริง + ช่องทางขายจริง, B9 ประกาศผู้ดูแลระบบ / ผู้ใช้ใหม่จริง

## AB3 — Admin Dashboard real data: recentUsers + paymentMethods (2026-11-01)

- Backend: ขยาย `GET /api/admin/dashboard/summary` ใน `AdminDashboardService` (ไม่มี endpoint ใหม่ / ไม่มี migration)
  - `paymentMethods[]`: method, amount, orderCount — รวมทุกร้านตามกฎ PAID ไม่นับ CANCELLED/REFUNDED; method จาก payments.provider (CAPTURED/AUTHORIZED) หรือ fallback orders.payment_method; ผลรวม amount = totalSales
  - `recentUsers[]`: ผู้ใช้ใหม่ในช่วงวันที่ที่เลือก ล่าสุด 5 คน — displayName, emailMasked (เช่น a***@x.com), role (ชื่อไทยจาก auth_roles/platform_roles/shop_members), createdAt — ไม่มี userId / phone / อีเมลเต็ม
- Frontend: `AdminDashboardPage` ผูกผู้ใช้ใหม่ + สัดส่วนวิธีชำระเงิน (หัวการ์ดตามช่วงวัน, ป้ายตัวอย่างออก, empty state); ต้อนรับใช้ชื่อเดียวกับ Header; Header แสดงบทบาทไทย; paymentStatus เป็นภาษาไทย; ลิงก์ ดู/ดูทั้งหมด ไป /admin/orders?q=…, /admin/stores, /admin/users; เอาคำอธิบายค่าธรรมเนียมออกจากกราฟ; ประกาศยังเป็นตัวอย่าง
- เทสต์: `AdminDashboardSummaryTests` ครอบคลุม paymentMethods รวมข้ามร้าน, recentUsers ช่วงวัน/mask, 401/403; ล้างใน @AfterEach
- ยังค้าง: ค่าธรรมเนียมจริง (B7), ประกาศจริง (B9), เมนูการเงิน/รายงานยัง placeholder
