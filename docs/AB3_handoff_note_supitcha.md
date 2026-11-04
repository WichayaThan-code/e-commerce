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

## AB3 — Admin System Status Phase 1 (2026-11-02)

- Migration: `V46__platform_system_read.sql` เพิ่มสิทธิ์ `PLATFORM_SYSTEM_READ` (scope PLATFORM) และ map ให้ role `PLATFORM_ADMIN`; อัปเดต `db/seed/seed_reference_data.sql` ให้เทสต์ H2 โหลดแคตตาล็อกเดียวกัน (ห้ามแก้ migration เก่า; ล่าสุด V46)
- Backend: `GET /api/admin/system/status` ใน `AdminSystemStatusController` / `AdminSystemStatusService`
  - สิทธิ์: `@RequirePermission("PLATFORM_SYSTEM_READ")` — ไม่ล็อกอิน 401, ไม่มีสิทธิ์ 403 (JSON `PERMISSION_DENIED`)
  - คืน overall / checkedAt / app (version, buildTime nullable, startedAt, uptimeSeconds, javaVersion, profile) + components: database (SELECT 1 timeout 2s + latency), migrations (Flyway bean; ถ้าไม่มีใน test = UP detail n/a), mail (เช็ก host อย่างเดียว ไม่ส่งเมล/ไม่เปิด connection → UNKNOWN เมื่อยังไม่ตั้งค่า), storage (โฟลเดอร์ upload เขียนได้ + freeBytes/totalBytes)
  - overall: database DOWN → DOWN; component อื่น DOWN → DEGRADED; นอกนั้น UP
  - ไม่ส่ง host / password / jdbc: / path เต็ม / stack trace; ไม่แตะ `GET /api/health`
  - ไม่เพิ่ม dependency ใหม่ (ใช้ Flyway / BuildProperties / JdbcTemplate ที่มีอยู่; version ถอย Implementation-Version แล้ว `dev`)
- Frontend: `/admin/system-status` — `AdminSystemStatusPage` + `admin-system-status.css`; เมนู `สถานะระบบ` (Activity) ใน AdminSidebar เมื่อ `can('PLATFORM_SYSTEM_READ')`; `adminPlatformApi.getSystemStatus`; `ROUTE_PERMISSIONS`; ข้อความไทยทั้งหมด; รีเฟรช 60 วินาที (หยุดเมื่อแท็บซ่อน); skeleton / 403 / ลองใหม่
- เทสต์: `AdminSystemStatusTests` (200+UP, 401, 403, ไม่มี password/jdbc:/host) + `EndpointAccessRuleCoverageTests` ครอบคลุม endpoint ใหม่
- ต้อง restart backend เพื่อรัน Flyway V46 ก่อนลองหน้าจริง

## AB3 — System Status connection resilience (2026-11-02)

- Backend: เมื่อ JWT pv lookup เจอ DataAccessException โยน DatabaseUnavailableException → JsonSecurityErrorHandlers ตอบ 503 code=DATABASE_UNAVAILABLE message=ฐานข้อมูลใช้งานไม่ได้ (ไม่ใช่ 401 TOKEN_*); ApiExceptionHandler จับ DataAccessException หลัง auth ด้วยรูปแบบเดียวกัน — ไม่มี jdbc/password/host/stack
- เทสต์: DatabaseUnavailableAuthTests (spy UserAccountRepository.findActivePermissionVersion → 503 + ไม่มีข้อมูลลับ)
- Frontend: AdminSystemStatusPage แยก connection ONLINE/NETWORK_OFFLINE/BACKEND_UNREACHABLE/BACKEND_ERROR/SESSION_EXPIRED; คง lastGood เป็นสีเทา; poll 30s + backoff 5/10/20/30; timeout 8s; toast เมื่อกลับมา; request flags redirectOn401=false สำหรับ getSystemStatus
## AB3 — System Status bugfix (2026-11-02)

- Backend: 503 DATABASE_UNAVAILABLE เฉพาะ DataAccessResourceFailureException (รวม CannotGetJdbcConnectionException), TransientDataAccessResourceException, QueryTimeoutException, DatabaseUnavailableException — ผ่าน DatabaseConnectivityFailures; EmptyResult/BadSqlGrammar ฯลฯ ไม่ถูก map เป็น 503; log.error พร้อม stack แต่ body ไม่มี jdbc/password
- เทสต์: DatabaseUnavailableAuthTests (empty/bad-sql → 500, jdbc-down/pv → 503, integrity → 409 CONFLICT)
- Frontend: AdminSystemStatusPage unmountedRef + clear toast/poll timers; ไม่ setState/schedule หลัง unmount; แท็บซ่อนไม่เพิ่ม backoff

## AB3 — Suspend/Reactivate Audit Log ครบทุกเส้นทาง (2026-11-02)

### Action names (คงชื่อเดิม — ไม่เปลี่ยนเป็น MERCHANT_SUSPEND)
| Action ที่ใช้จริง | ความหมาย |
|---|---|
| MERCHANT_SUSPENDED | ระงับผู้ขาย |
| MERCHANT_REACTIVATED | เปิดใช้งานผู้ขายอีกครั้ง |
| SHOP_SUSPENDED | ระงับร้านค้า |
| SHOP_REACTIVATED | เปิดใช้งานร้านค้าอีกครั้ง |
| ACCOUNT_SUSPENDED | ระงับผู้ใช้ |
| ACCOUNT_RESTORED | คืนสิทธิ์ผู้ใช้ |

เขียนผ่าน `PlatformAuditService` → ตาราง `audit_logs` เดิม (อ่านด้วย `AdminAuditLogService`) — ไม่มีระบบ audit ใหม่ / ไม่มี migration ใหม่ (correlationId เก็บใน after_data JSON)

### Backend
- `ShopSuspensionService`: cascade merchant↔shop ใส่ `correlationId` เดียวกัน + `parentResourceType`/`parentResourceId`/`cascadeFromMerchantId` ใน after ของลูก; `targetName` (ชื่อแสดงผล ไม่มีเบอร์/อีเมล); parent audit ก่อน child
- `AdminUserManagementService.setStatus`: after มี status + targetName (ชื่อ-นามสกุลอย่างเดียว)
- `AdminAuditLogService` / DTO: `AuditActor.role` (platform role ปัจจุบัน), `AuditTarget.name` (SHOP/OWNER/USER)
- `AdminUserDirectoryService.history`: ชื่อไทยสำหรับ 6 action ข้างต้น
- บันทึกใน transaction เดียวกับการเปลี่ยนสถานะ — ถ้า insert log ล้ม → rollback สถานะ

### Frontend (ไม่เปลี่ยนดีไซน์หลัก)
- `/admin/audit-logs`: กรอง 6 action ด้วย AdminSelect; อ่าน `targetType`/`targetId` (alias ของ resourceType/resourceId); รายละเอียดแสดงข้อความไทย + สถานะก่อน/หลัง
- ลิงก์จากรายละเอียด Merchant / Shop / แท็บประวัติ User → `/admin/audit-logs?...`

### Tests
- `PlatformAdminIntegrationTests`: cascade + correlationId, 6 action + 400/403/404 ไม่มี log สำเร็จ, ไม่มีเบอร์/อีเมลใน detail
- `SuspendAuditRollbackTests`: mock `PlatformAuditLogRepository.save` throw → สถานะร้าน/ผู้ใช้ไม่เปลี่ยน

### ไฟล์หลัก
ShopSuspensionService, AdminUserManagementService, AdminAuditLogService, AdminPlatformDtos, AdminUserDirectoryService, AdminAuditLogPage, AdminMerchantsPage, AdminUsersPage, locales th/en/zh, SuspendAuditRollbackTests

## AB3 — Audit log polish: shop link, i18n, bulk reason (2026-11-02)

### 1) ลิงก์ดูประวัติร้าน
- AdminMerchantsPage (shops drawer): `/admin/audit-logs?targetType=SHOP&targetId={shopId}&kind=BUSINESS`
- AdminStoresPage: ลิงก์ใต้ชื่อร้าน (เมื่อ PLATFORM_AUDIT_READ)
- AdminApplicationsPage (แท็บประวัติ): ลิงก์เดียวกัน
- AdminAuditLogPage: `targetType=SHOP` + ตัวเลข → กรองด้วย shopId; แสดงชิปตัวกรองกดลบได้

### 2) i18n
- `adminPlatform.audit.openForTarget`: th ดูประวัติในบันทึกการใช้งาน / en View in audit log / zh 在审计日志中查看
- AdminUsersPage ใช้ key นี้แทนข้อความตายตัว

### 3) Bulk / legacy reason
- Backend: BulkShopRequest + OptionalReasonRequest มี reasonCode/reason (≤500); suspend ต้องมี reasonCode มิฉะนั้น 400 REASON_CODE_REQUIRED; bulk ใช้ correlationId เดียวกัน; LEGACY_NO_REASON เหลือเฉพาะ log เก่า
- Frontend: AdminStoresPage bulk เปิด ReasonDialog (dropdown เหตุผล + หมายเหตุ); adminApplicationApi.suspend/bulk/reactivate ส่งเหตุผล
- Tests: bulkSuspendRequiresReasonAndSharesCorrelationId (ก)(ข)(ค)

ไม่มี migration ใหม่
## AB3 — reasonCode allowlist + bulk i18n (2026-11-02)

### 1) Backend allowlist (`SuspensionReasonCodes`)
- ไฟล์ใหม่: `admin/SuspensionReasonCodes.java` — รายการเดียวร่วมกับ FE `SUSPEND_REASON_CODES`
- **Suspend อนุญาต (client):** `ADMIN_SUSPEND`, `POLICY_VIOLATION`, `FRAUD`, `ABUSE`, `OTHER`
- **Restore อนุญาต (client):** `ADMIN_RESTORE`, `OTHER` — ว่าง = default `ADMIN_RESTORE`
- **รหัสสงวน (ห้ามส่งจาก API → 400 `REASON_CODE_INVALID`, ไม่เขียน log):** `MERCHANT_SUSPENDED`, `NO_REASON_GIVEN` (LEGACY_NO_REASON), และรหัสที่ไม่ได้อยู่ใน allowlist รวม `ADMIN_RESTORE` บนเส้นทาง suspend / `ADMIN_STATUS` ถ้าส่งตรง ๆ
- ผูกตรวจ: `AdminShopService`, `AdminMerchantService`, `StoreReviewService` (legacy + bulk), `AdminUserManagementService` / `AdminUserController` (reasonCode ถ้าส่งมา)
- Cascade merchant→shop ยังเขียน `MERCHANT_SUSPENDED` ภายในได้; log เก่าที่รหัสสงวนยังกรองได้ตามเดิม (ไม่แก้ DB/migration)
- ไม่สร้าง filter-options ใหม่ (ที่มีอยู่เป็น roles/statuses ของ users ไม่เกี่ยวกับเหตุผลระงับ)

### 2) ลบ overload ที่ไม่มีผู้เรียก
- ลบ `StoreReviewService.suspend(Long shopId)` และ `suspendMany(List<Long>)` (ไม่มี reason) — ตรวจแล้วไม่มี caller/test

### 3) Frontend i18n bulk
- keys ใน th/en/zh `admin.json` `confirm.bulkSuspendTitle|Body`, `bulkReactivateTitle|Body`, `bulkSuspendedToast`, `bulkReactivatedToast`, `bulkNoneToast` (interpolation `{{count}}`)
- `AdminStoresPage.tsx` dialog / toast / ปุ่ม bulk ใช้ key เหล่านี้; `ReasonDialog.tsx` ไม่มีข้อความไทยตายตัวของ bulk

### 4) Tests
- `PlatformAdminIntegrationTests.reservedReasonCodesRejectedWithoutAuditOrStatusChange` — (ก) MERCHANT_SUSPENDED บน shop suspend = 400 ไม่มี log สถานะไม่เปลี่ยน; (ข) NO_REASON_GIVEN บน bulk = 400; (ค) รหัสอนุญาตทั้ง 5 ผ่าน
- (ง) cascade ยังอยู่ที่ `merchantSuspendCascadesToShopsAndReactivateRestoresOnlyThose`
- `.\mvnw test` ผ่านทั้งหมด; `npx tsc -b --noEmit` ผ่าน

ไม่มี migration / ไม่แตะ MerchantTopbarActions / .env / AB2 / ไม่ commit-push / ไม่ start-stop server
## AB3 — polish หลัง allowlist: reactivate overload / ADMIN_STATUS / stores i18n (2026-11-02)

### 1) ลบ overload ที่ไม่มีผู้เรียก
- ลบ `StoreReviewService.reactivate(Long)` และ `reactivateMany(List<Long>)` (ไม่มี reason) — ตรวจ main+test แล้วไม่มี caller

### 2) ADMIN_STATUS แสดง/กรองคู่กับรหัสใหม่
- คงรหัสเขียนใหม่ `ADMIN_SUSPEND` / `ADMIN_RESTORE` — ไม่แก้ข้อมูลเก่าใน DB
- `AdminUserDirectoryService.reasonCodeLabelTh`: `ADMIN_STATUS` → "เปลี่ยนสถานะโดยแอดมิน"; `ADMIN_SUSPEND`/`ADMIN_RESTORE` มีป้ายไทยชัดเจนในประวัติผู้ใช้
- `AdminAuditLogService`: กรอง `reasonCode=ADMIN_STATUS|ADMIN_SUSPEND|ADMIN_RESTORE` รวมทั้ง 3 รหัส (ครอบครัวเดียวกัน)
- FE: `adminPlatform.reason.codes.ADMIN_STATUS|ADMIN_RESTORE` (th/en/zh); `AdminAuditLogPage` แสดงป้าย + รหัสดิบ
- Test: `userHistoryShowsLegacyAdminStatusAndNewAdminSuspendRows` — ประวัติเห็นทั้ง 2 แถว + filter ADMIN_STATUS ได้ 2 รายการ

### 3) AdminStoresPage i18n ครบ
- คีย์ใหม่ `adminPlatform.storesPage.*` ใน th/en/zh (ข้อความไทยเดิมทุกตัวอักษร)
- หน้าใช้ status code ภายใน + แปลป้าย; regex Thai ใน `.tsx` = 0

`.\mvnw test` + `npx tsc -b --noEmit` ผ่าน; ไม่มี migration / ไม่แตะไฟล์ห้าม / ไม่ commit-push / ไม่ start-stop server
## AB3 — Admin Usage page /admin/usage (2026-11-03)

### Frontend
- ใหม่: `pages/admin/AdminUsagePage.tsx` — สรุปแพ็กเกจจาก `GET /api/admin/plans` + `GET /api/admin/plans/usage`, ตารางร้านจาก `GET /api/admin/shops` (ค้นหา/สถานะ/แพ็กเกจ), แผงขวา `GET /api/admin/shops/{id}/usage` พร้อม progress bar (≥80% warn, ≥100% over, ไม่มี limit = "ไม่จำกัด")
- Route `/admin/usage` + `ROUTE_PERMISSIONS` = `PLATFORM_SHOP_READ`; เมนู "การใช้งาน (Usage)" ใน AdminSidebar (แสดงเมื่อ canVisit)
- CSS: progress/quota ใน `admin-platform.css` (ไม่เพิ่ม dependency)
- i18n: `adminPlatform.usage.*` th/en/zh — ไม่มีข้อความไทยตายตัวในหน้า, ไม่แสดงเบอร์/อีเมลเจ้าของ

### Backend
- ไม่มี endpoint/migration ใหม่ — ใช้ API เดิม `@RequirePermission PLATFORM_SHOP_READ`
- Tests ที่มีอยู่แล้วครอบคลุม:
  - `shopsListFiltersPaginatesAndDetailShowsMembersCountsAndUsage` — admin 200 ที่ `/plans/usage` และ `/shops/{id}/usage`
  - `merchantOwnerGets403OnEveryAdminEndpoint` — merchant 403 ทุก /api/admin/**
  - `platformRoleWithoutThePermissionGets403` — บทบาทไม่มี PLATFORM_SHOP_READ ได้ 403 ที่ `/plans/usage`

`.\mvnw test` + `npx tsc -b --noEmit` ผ่าน; ไม่ commit/push; ไม่ start-stop server


## AB3 — Admin Usage fixes (server plan filter / summary / limit=0 / QuotaBar) (2026-11-03)

### เป้าหมาย
แก้ 5 จุดในหน้า Admin Usage โดยไม่เปลี่ยนดีไซน์ ไม่มี migration ไม่แตะไฟล์ห้าม

### Backend
- `AdminShopService.Filter` เพิ่ม `plan` (optional) — ใช้กับ `usagePage` / `listKeys` (ตารางยังไม่มีคอลัมน์ plan จริง จึงกรองในหน่วยความจำหลังได้ planCode)
- `GET /api/admin/plans/usage` รับ `plan` (รวม `__NONE__` = ไม่มีแพ็กเกจ) คู่กับ `q`/`status` — totalItems/Pager ตรงผลกรอง
- ใหม่: `GET /api/admin/plans/usage-summary` (`@RequirePermission PLATFORM_SHOP_READ`) คืน `List<PlanUsageSummary>` รวมทุกร้าน (ไม่จำกัด 200)
- `AdminPlatformDtos.PlanUsageSummary` + `ShopUsage.slug` (additive) + `planCode` เป็น NON_NULL
- `AdminShopUsageService`: `assignPlan`/`clearPlanAssignments` (test hook), `matchesPlanFilter`, `summarize`, `ShopKey` มี slug

### Frontend
- `AdminUsagePage.tsx`: ตารางโหลดจาก `planUsage` (ส่ง q/status/plan ฝั่ง server) เลิกกรอง client และเลิก map 200 ร้าน; สรุปจาก `planUsageSummary`
- QuotaBar: limit=0 ใช้=0 → 100% ปกติ, ใช้>0 → over; ส่ง `format` (storage เป็น MB/GB); `showCaption=false` เพราะหัวการ์ดมี used/limit แล้ว
- `api.ts`: `planUsageSummary` + type `AdminPlanUsageSummary` + `slug` บน `AdminShopUsage`

### Tests (`PlatformAdminIntegrationTests`)
- `usageEndpointsForbiddenForMerchantAndSupportWithoutShopRead` — merchant 403 ที่ `/plans/usage`, `/shops/{id}/usage`, `/plans/usage-summary`; support ไม่มี PLATFORM_SHOP_READ → 403 ที่ `/shops/{id}/usage`
- `planUsageFilterReturnsOnlyMatchingShopsAndSummaryCountsAll` — plan=BASIC/PRO/__NONE__ + totalItems; summary นับครบ

`.\mvnw test` + `npx tsc -b --noEmit` (รันหลังแก้); ไม่ commit/push; ไม่ start-stop server; ไม่มี migration


## AB3 — Admin Usage API polish: planCode null + plan param validation (2026-11-03)

### 1) planCode ส่งใน JSON เสมอ
- ลบ `@JsonInclude(NON_NULL)` ออกจาก `ShopUsage.planCode` — ร้านไม่มีแพ็กเกจได้ `"planCode": null`
- แก้ test `shopsListFiltersPaginatesAndDetailShowsMembersCountsAndUsage` ให้ assert `planCode` เป็น null (ไม่ใช่ doesNotExist)

### 2) ตรวจรูปแบบพารามิเตอร์ plan
- `AdminShopUsageService.normalizePlan`: trim + upper; ว่าง/null = ไม่กรอง; อนุญาต `^[A-Z0-9_]{1,50}$` หรือ `__NONE__`; นอกนั้น 400 `PLAN_INVALID` ผ่าน `AuthException` → `ApiExceptionHandler`
- ใช้ใน `usagePage` / `usageSummaries`; `GET /plans/usage-summary` รับ `plan` (validate แล้วไม่ใช้กรองสรุป)
- Test ใหม่ `planParamNormalizesCaseRejectsInvalidAndBlankMeansNoFilter`: basic=BASIC, bad-value! → PLAN_INVALID, plan= ว่าง = ไม่กรอง
- i18n `adminPlatform.errors.PLAN_INVALID` th/en/zh

`.\mvnw test` + `npx tsc -b --noEmit`; ไม่ migration / ไม่ commit-push / ไม่ start-stop server


## AB3 — Shop plans ใน DB (V47) + SQL usage/summary (2026-11-03)

### ต้อง restart backend
ให้ Flyway รัน **V47__shop_plans.sql** — ร้านเดิมยัง `plan_code = NULL` (ยังไม่ถูกกำหนดแพ็กเกจ)

### V47 โครงสร้าง
- ตาราง `plans`: code PK, name, limit_products, limit_orders_per_month, limit_staff, limit_storage_bytes (null = ไม่จำกัด), active, created_at, updated_at
- `shops.plan_code` varchar(50) null → FK `plans(code)` + index `idx_shops_plan_code`
- **Seed:** FREE (50 สินค้า / 100 ออเดอร์ต่อเดือน / 2 พนักงาน / 100 MB), BASIC (500 / 1000 / 10 / 1 GB), PRO (ไม่จำกัดสินค้าและออเดอร์ / 50 พนักงาน / 10 GB)

### Backend
- `PlanCatalog` อ่านจาก `plans` (`source=DB`); entity `ShopPlan` สำหรับ Hibernate ในเทสต์ (Flyway ปิดใน test)
- ลบ ConcurrentHashMap / assignPlan ใน memory — เทสต์ใช้ `UPDATE shops SET plan_code`
- กรอง `__NONE__` = `plan_code IS NULL` และแบ่งหน้าใน SQL; `/plans/usage` โหลด usage แบบ batch ต่อหน้า
- `/plans/usage-summary` = GROUP BY plan_code + subquery รวม products/staff/storage/orders 30 วัน (ไม่ N+1)
- เพิ่ม `PUT /api/admin/shops/{id}/plan` (`PLATFORM_SHOP_SUSPEND`) + audit `SHOP_PLAN_CHANGED`
- `normalizePlan` / `PLAN_INVALID` เหมือนเดิม

### Tests
- `planNoneFilterPaginationSummaryAndBatchedUsage` — `__NONE__`, pagination+totalItems, summary, lastUsageBatchSize, PUT plan

`.\mvnw test` + `npx tsc -b --noEmit`; ไม่แตะ FE ดีไซน์ / ไม่ commit-push / ไม่ start-stop จาก agent

## AB3 — AdminStores detail panel + owner contact mask Phase 1 (2026-11-03)

### 1) แผงรายละเอียดร้าน (`AdminStoresPage`)
- คลิกแถวร้าน หรือปุ่ม more → เปิด Drawer ขวา (pattern/class จาก `admin-platform.css` / `PlatformBits.Drawer` เดียวกับ Merchants/Usage)
- โหลด `GET /api/admin/shops/{id}` + `GET /api/admin/shops/{id}/usage` (cancelled flag กัน race)
- เนื้อหา: โลโก้/ชื่อ, slug ลิงก์ `/shop/{slug}` (แท็บใหม่), สถานะ, วันที่สร้าง, merchant ลิงก์ `/admin/merchants`, แพ็กเกจ+QuotaBar, เหตุผลการระงับ (reasonCode label + reason + วันที่), suspend/reactivate (`can` + ReasonDialog), ลิงก์ audit เมื่อ `PLATFORM_AUDIT_READ`
- skeleton / error+retry / 403; ปิดด้วย X หรือ Esc; หลัง suspend/reactivate refresh แผง+ตาราง

### 2) ปิดบังเบอร์/อีเมลเจ้าของร้าน
- FE: `frontend/src/utils/maskContact.ts` (`maskPhone` → `08x-xxx-1234`, `maskEmail` → `s***@domain`, idempotent)
- ตาราง + แผงรายละเอียดใช้ helper นี้
- BE: `AdminContactMask` — mask ใน `StoreReviewService.toAdmin` (store-applications) และ `AdminShopService` list/detail `UserRef` (owner)
- **ยังไม่ mask เบอร์เต็ม:** merchant APIs / owner application ของร้านตัวเอง, `AdminMerchantService` (merchant list ยังส่ง phone/email เต็ม), `AdminUserDirectoryService` (user directory), shop **members** email ใน shop detail (เทสต์ยัง assert อีเมลเต็มของ staff)

### 3) QuotaBar กลาง
- `frontend/src/components/admin/QuotaBar.tsx` — export `QuotaBar`, `buildUsageQuotas`, `quotaRatio`, `quotaTone`
- `AdminUsagePage` เปลี่ยนมา import ของกลาง

### 4) i18n `adminPlatform.stores.detail.*` (th/en/zh ครบ)
title, info, openStorefront, status, createdAt, merchant, owner, phone, email, planUsage, plan, noPlan, suspension, suspendedAt, reasonCode, reason, suspend, reactivate, auditLink, loadError, forbidden

### 5) Tests
- `adminShopOwnerContactIsMaskedInShopAndStoreApplicationApis` — shop detail/list + store-applications ไม่มีเบอร์/อีเมลเต็ม
- `assignShopPlanForbiddenForMerchantAndSupportWithoutSuspendPermission` — PUT `/api/admin/shops/{id}/plan` 403 สำหรับ merchant และ support ที่มีแค่ SHOP_READ

### ผลตรวจ
- `.\mvnw test` ผ่านทั้งหมด
- `npx tsc -b --noEmit` ผ่าน
- Manual `/admin/stores`: ต้อง restart backend ถ้ายังไม่โหลดโค้ดใหม่ — คลิก tinzshop ดูแผง+usage, suspend/reactivate จากแผง, เบอร์ปิดบัง, สลับ EN/ZH ไม่มีไทยค้าง

ไม่ migration / ไม่ commit-push / ไม่ start-stop server / ไม่แตะ MerchantTopbarActions / .env / AB2

## AB3 — Contact mask fix + PLATFORM_CONTACT_REVEAL + stores polish (2026-11-03)

### ต้อง restart backend
ให้ Flyway รัน **V48__platform_contact_reveal.sql** — เพิ่มสิทธิ์ `PLATFORM_CONTACT_REVEAL` ให้ `PLATFORM_ADMIN`

### 1) แก้ mask (FE + BE)
- `maskContact.ts`: `alreadyMasked` เช็กเฉพาะรูปแบบที่ระบบสร้าง (`/^\S\*{3}@/` สำหรับอีเมล, `NNx-xxx-dddd` / `xxx-xxx-dddd` สำหรับเบอร์) ไม่เช็กแค่ว่ามี x หรือ *
- แปลง +66/66 → 0 ก่อนปิดบัง ทั้ง FE และ `AdminContactMask.java`
- null/ว่าง → null (BE) / "—" (FE); เบอร์สั้นกว่า 4 หลัก → `****`
- Unit test: `AdminContactMaskTest` (ไม่มี FE test runner)

### 2) V48 + เปิดดูข้อมูลติดต่อ
- Migration `V48__platform_contact_reveal.sql` + อัปเดต `seed_reference_data.sql` (permission + mapping + allowlist รวม `PLATFORM_SYSTEM_READ`)
- `POST /api/admin/store-applications/{shopId}/contact` — `@RequirePermission("PLATFORM_CONTACT_REVEAL")` คืน phone/email เต็ม
- Audit `CONTACT_REVEALED` (actor/target/permission/เวลา) — **ไม่**ใส่ค่าเต็มใน before/after
- `AdminApplicationsPage`: แสดงแบบปิดบัง; ปุ่ม "แสดงข้อมูลติดต่อ" เมื่อ `can('PLATFORM_CONTACT_REVEAL')`; ค่าเต็มอยู่ใน state ของหน้าเท่านั้น (ไม่ localStorage)
- i18n `adminPlatform.applications.contactReveal.*` th/en/zh

### 3) Stores polish
- ค้นหาเบอร์/อีเมล: `GET /api/admin/shops?q=` ค้นจากค่าเต็มใน DB (users+owners) response ยังปิดบัง; `AdminStoresPage` ส่ง q/status ไป server แล้วตัดแถวด้วย shopId
- ลิงก์ merchant → `/admin/merchants?merchant={id}`; `AdminMerchantsPage` เปิดแผง merchant จาก param
- **Suspend reason:** backend `ReasonRequest` บังคับ reason ข้อความ ≥5 อยู่แล้ว → **ReasonDialog บังคับกรอก** (ถอด `reasonOptional` / เลิกส่ง `ADMIN_SUSPEND` เป็น reason)

### Tests
- `AdminContactMaskTest` — +66, อีเมลมี x, สั้น, null
- `shopsSearchMatchesFullPhoneAndEmailButResponseStaysMasked`
- `revealStoreApplicationContactAuditsWithoutStoringRawValues` — 200+audit, merchant 403, support 403
- `PermissionMatrixIntegrationTests` — PLATFORM_ADMIN size 19 + hasItems CONTACT_REVEAL

`.\mvnw test` ผ่าน · `npx tsc -b --noEmit` ผ่าน · ไม่ commit-push · ไม่ start-stop

## AB3 — Contact search lockdown + AdminApplications i18n (2026-11-03)

### 1) ค้นหาเบอร์/อีเมล (ปิดช่องโหว่เดา)
`AdminShopService.where()` ไม่ใช้ `LIKE %digits%` / LIKE ส่วนหน้าอีเมลอีกต่อไป
- **เบอร์:** 4 หลัก = ตรงท้ายพอดี (`RIGHT(...,4)`); 9–10 หลักหลัง +66→0 = ตรงเต็ม (รองรับรูปแบบ 66… ใน DB); หลักอื่นค้นแค่ชื่อ/slug/id
- **อีเมล:** ตรงเต็ม (case-insensitive) หรือโดเมนพอดีเมื่อขึ้นต้นด้วย `@` (`RIGHT(email)=@domain`)
- ค้นเบอร์เต็มหรืออีเมลเต็ม → audit `CONTACT_SEARCHED` (actor + `searchType` PHONE|EMAIL) **ไม่**บันทึกค่าที่ค้น
- response ยังปิดบัง

### 2) ปรับเล็ก
- `POST .../store-applications/{id}/contact` → header `Cache-Control: no-store`
- `maskContact.ts`: ไม่มีเงื่อนไข `^\d{4}$` ใน alreadyMasked (คงรูปแบบ mask ที่ระบบสร้างเท่านั้น)

### 3) i18n `AdminApplicationsPage`
- ข้อความไทยตายตัวทั้งหมด → `adminPlatform.applications.*` (th/en/zh คีย์ตรงกัน; ไทยคงตัวอักษรเดิม)
- ไม่มีอักษรไทยในซอร์สหน้า (regex [\u0E00-\u0E7F] ผ่าน)
- **งานถัดไป:** หัวคอลัมน์ export ใน `StoreReviewService.java` (~บรรทัด 63) ยังเป็นไทยตายตัว — ยังไม่แก้ในรอบนี้

### Tests
- `shopsContactSearchAllowsLast4AndFullExactOnlyAndAuditsFullLookup`
- `.\mvnw test` + `npx tsc -b --noEmit` ผ่าน
- ไม่ migration / ไม่ commit-push / ไม่ start-stop

## AB3 — Shared AdminContactSearch for Merchants / Users (2026-11-03)

### 1) Helper กลาง
- ใหม่: `AdminContactSearch.java`
  - เบอร์: 4 ตัวท้ายพอดี หรือเบอร์เต็มพอดี (9-10 หลัก หลัง +66→0)
  - อีเมล: ตรงเต็มพอดี (ไม่สนตัวพิมพ์) หรือโดเมนพอดีเมื่อขึ้นต้นด้วย `@`
  - `CONTACT_SEARCHED` เมื่อค้นเบอร์/อีเมลเต็ม — เก็บแค่ `searchType` ไม่เก็บค่าที่ค้น
  - SQL ใช้ named parameters เท่านั้น
- `AdminShopService.where()` ย้ายไปใช้ helper — พฤติกรรมเดิมคงเดิม

### 2) Merchants / Users
- `AdminMerchantService` / `AdminUserDirectoryService`: ลบ LIKE บน email/phone (และ qDigits LIKE ของ users)
- ชื่อ / ชื่อร้านยัง LIKE ได้; list รับ actorId เพื่อ audit
- `AdminPlatformController` / `AdminUserController` ส่ง `RequestUser.id(jwt)`

### 3) Response masking (สถานะเดิม — ยังไม่ปิดบังในรอบนี้)
- `AdminMerchantService` list/detail ยังส่ง phone/email เต็ม (FE `AdminMerchantsPage` แสดงตรงๆ)
- `AdminUserDirectoryService` list/detail/export ยังส่ง phone/email เต็ม (FE `AdminUsersPage` แสดงตรงๆ)
- ปิดบังอยู่แล้วเฉพาะ shops owner + store-applications

### 4) Frontend
- `maskContact.ts` `alreadyMaskedPhone`: ไม่มีเงื่อนไข `/^\d{4}$/` อยู่แล้ว (ตรวจยืนยัน)

### 5) Tests (`PlatformAdminIntegrationTests`)
- ปรับ `shopsContactSearch…` ใช้โดเมน `@phonesearch.test` + assert มีร้านเป้าหมายในผล (ไม่ผูกจำนวน fixture)
- ใหม่: `merchantsContactSearchAllowsLast4AndFullExactOnlyAndAuditsFullLookup`
- ใหม่: `usersContactSearchAllowsLast4AndFullExactOnlyAndAuditsFullLookup`
- ปรับ `merchantsListAndDetail…` จาก `q=mer-a@` → อีเมลเต็ม

### ข้อห้ามที่ยึด
ไม่ migration / ไม่แก้ดีไซน์ / ไม่แตะ MerchantTopbarActions / .env / AB2 / ไม่ dependency ใหม่ / ไม่ commit-push / ไม่ start-stop server

### 6) ปรับ test ที่เกี่ยวข้อง
- `AdminDirectoryIntegrationTests.userListFiltersOnTheServer`: ไม่ใช้ prefix อีเมลแล้ว — ค้นอีเมลเต็ม / ชื่อ; soft-delete assert ด้วยอีเมลเต็ม

### ผลตรวจ
- `.\mvnw test` ผ่านทั้งหมด (313 tests, 0 fail)
- `npx tsc -b --noEmit` ผ่าน
- Manual `/admin/merchants` และ `/admin/users`: ต้อง restart backend ถ้ายังไม่โหลดโค้ดใหม่ — ค้น 4 ตัวท้ายแล้วเจอ, ค้น 2 หลักแล้วไม่เจอจากเบอร์
## AB3 — maskContact + users export CONTACT_SEARCHED (2026-11-03)

### 1) maskContact.ts
- lreadyMaskedPhone: ไม่มีเงื่อนไข `|| /^\d{4}$/.test(value)` อยู่แล้ว (ตรวจยืนยัน ไม่ต้องลบ)

### 2) Users export audit
- AdminUserDirectoryService.exportCsv: เรียก `AdminContactSearch.auditIfNeeded` เหมือน list (permission `PLATFORM_ACCOUNT_READ`, resource `USER`); ถอด `readOnly` เพื่อเขียน audit
- AdminUserController.export: ส่ง `RequestUser.id(jwt)`

### 3) Test
- ใหม่: `usersExportWithFullPhoneAuditsContactSearchedWithoutStoringQuery` — export ด้วยเบอร์เต็มแล้วมี `CONTACT_SEARCHED` ไม่เก็บค่าที่ค้น

### ผลตรวจ
- `.\mvnw test` ผ่านทั้งหมด (314 tests, 0 fail)
- `npx tsc -b --noEmit` ผ่าน
## AB3 — Password ASCII-only inputs (signup / reset / login)

วันที่: 2026-11-04

### สรุป
กันพิมพ์ภาษาไทยและอักษรนอก ASCII ในช่องรหัสผ่านทุกช่องฝั่ง frontend และล็อก pattern ฝั่ง backend สำหรับ register / reset / change-password

### Backend
- `AuthDtos.java`: `@Pattern` ของ password / newPassword (3 จุด) เป็น `^(?=.*[A-Za-z])(?=.*\\d)[\\x21-\\x7E]+$` พร้อม message ภาษาไทย UTF-8
- `LoginRequest.password` ไม่ใส่ pattern นี้ — บัญชีเดิม login ได้ตามปกติ
- `AuthIntegrationTests.registerRejectsInvalidPasswordsAndAcceptsAsciiPassword`: ปฏิเสธไทยปน / มีช่องว่าง / ไม่มีตัวเลข / ไม่มีตัวอักษร / สั้นกว่า 8 ตัว (400, ไม่สร้าง user); รับ `Password1` และ `P@ssw0rd!`

### Frontend
- `utils/passwordRules.ts`: `ALLOWED`, `sanitizePassword`, `passwordRules`, `isPasswordValid`
- `hooks/useAsciiPasswordInput.ts` + `components/form/AsciiPasswordInput.tsx` (+ `PasswordInputHints`): กรอง onChange/onPaste/onCompositionEnd, Caps Lock, inputMode/lang/autoCapitalize/autoCorrect/spellCheck
- ใช้ที่: `SignupPage` (password + confirm), `PasswordResetPages` (new + confirm), `LoginPage` (password — ไม่เช็กความแข็งแรง)
- ไม่พบช่อง password ที่ `/merchant/register` หรือหน้า change-password ใน frontend ตอนนี้
- i18n th/en/zh: `passwordField.asciiOnly`, `passwordField.capsLock`

### ผลตรวจ
- `.\mvnw test` ผ่านทั้งหมด (319 tests, 0 fail)
- `npx tsc -b --noEmit` ผ่าน
## AB3 — Email verification gate + OTP verify link (2026-11-04)

วันที่: 2026-11-04

### สรุป
บัญชีที่ยังไม่ยืนยันอีเมล login/register/refresh ได้ตามเดิม แต่ถูกบังคับไปหน้า `/verify-email` ก่อนใช้ API อื่น และอีเมล OTP แบบ EMAIL_VERIFY มีทั้งรหัสและลิงก์ยืนยันครั้งเดียว

### Backend
- `EmailNotVerifiedInterceptor` + ลงทะเบียนใน `AuthorizationWebConfig` — ถ้า `app.auth.require-email-verification=true` (ค่าเริ่มต้น) และ JWT ของผู้ใช้ที่ `email_verified_at` ว่าง จะตอบ 403 `EMAIL_NOT_VERIFIED`
- endpoint ที่ยกเว้น (ยังใช้ได้ก่อนยืนยัน): `GET /api/auth/me`, `POST /api/auth/logout`, `POST /api/auth/refresh`, `POST /api/auth/otp/send`, `POST /api/auth/otp/verify`, `POST /api/auth/otp/verify-link`
- endpoint สาธารณะที่ไม่ต้อง login ทำงานตามเดิม; login/register/refresh ยังออก session ได้ และ `UserResponse.emailVerified` มีอยู่แล้ว
- API ใหม่: `POST /api/auth/otp/verify-link` body `{ otpId, token }` — ไม่ต้อง login; สำเร็จแล้ว markVerified + markEmailVerified เหมือน verify OTP; ผิด/หมดอายุ/ใช้ซ้ำ/ถูกแทนที่ = `INVALID_VERIFY_LINK` (ข้อความเดียวกัน)
- ตอนส่ง OTP `EMAIL_VERIFY`: สร้าง token 32 bytes base64url เก็บเฉพาะ SHA-256 ใน `otp_verifications.verify_link_token_hash` อายุเท่า OTP (5 นาที) ใช้ครั้งเดียว; ขอ OTP ใหม่จะ expire แถว PENDING เดิม
- ลิงก์ในอีเมล: `{APP_FRONTEND_URL}/verify-email?otpId=<id>&token=<token>` (yml: `app.notification.frontend-url` ← `APP_FRONTEND_URL` หรือ `FRONTEND_URL`)
- `AuthMessageTemplates.otpEmail`: ปุ่ม "ยืนยันอีเมล" + คงรหัส OTP; ข้อความไทยในไฟล์เป็น UTF-8 ถูกต้องแล้ว
- Flyway `V49__email_verify_link_and_grandfather.sql`: เพิ่มคอลัมน์ `verify_link_token_hash` และตั้ง `email_verified_at` ให้บัญชีเดิมที่ยังว่าง (รวม seed/แอดมิน) เพื่อไม่โดนเกต
- เทสต์ปิดเกตด้วย `app.auth.require-email-verification=false` ใน `application-test.yml`; `EmailVerificationIntegrationTests` เปิดเกตเฉพาะคลาสนี้
- แฟล็ก: `AuthProperties.requireEmailVerification` / env `REQUIRE_EMAIL_VERIFICATION` (ค่าเริ่มต้น true)

### Frontend
- หน้า `/verify-email`: ถ้ามี `otpId`+`token` เรียก verify-link ครั้งเดียว (กัน StrictMode); สำเร็จแล้ว reload user แล้วไป `next` (หรือ `/`); ถ้าไม่มีพารามิเตอร์และ login แล้วยังไม่ยืนยัน จะส่ง OTP อัตโนมัติ 1 ครั้ง + ฟอร์ม OTP/นับเวลา/ส่งใหม่ผ่าน `useOtpTimer`
- `EmailOtpPanel` แชร์กับ Signup (Signup หลังสมัครพาไป `/verify-email`); มีปุ่มออกจากระบบ
- `EmailVerifiedGate`: user ที่ `emailVerified === false` ถูกพาไป `/verify-email?next=...` ยกเว้นหน้าสาธารณะ + `/verify-email` + `/logout` + auth pages; `safeInternalPath` กัน open redirect
- Login สำเร็จถ้ายังไม่ยืนยัน → `/verify-email` ทันที; API client เจอ 403 `EMAIL_NOT_VERIFIED` → `/verify-email`
- i18n th/en/zh: `verifyEmailPage.*`

### เทสต์
- `EmailVerificationIntegrationTests`: login ได้แต่ API อื่น 403; ส่ง/verify OTP แล้วปลดล็อก; ลิงก์ใช้ได้ครั้งเดียว / ผิด / หมดอายุ / ถูกแทนที่ด้วย OTP ใหม่ใช้ไม่ได้; บัญชีที่ยืนยันแล้วใช้งานปกติ

### Env (ไม่ได้แก้ backend/.env)
- มี `FRONTEND_URL` อยู่แล้วก็พอ (yml อ่านเป็น fallback ของ `APP_FRONTEND_URL`)
- ถ้าต้องการตั้งชัด: เพิ่ม `APP_FRONTEND_URL=http://localhost:5173` (หรือ URL จริงของ frontend)
- ปิดเกตชั่วคราวได้ด้วย `REQUIRE_EMAIL_VERIFICATION=false`

### ผลตรวจ
- `.\mvnw test` ผ่านทั้งหมด (323 tests, 0 fail)
- `npx tsc -b --noEmit` ผ่าน
