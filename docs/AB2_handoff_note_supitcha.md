# Backend handoff สำหรับ Supitcha (24 ต.ค. 2026)

> สร้างจาก `git status` + `git diff ebb7432 -- backend` + ไฟล์ untracked ณ 24 ต.ค. 2026 ยังไม่ได้ commit ใดๆ
> base API ฝั่งร้าน: `/api/merchant/shops/{shopId}` ใช้ `@RequireShopPermission` / `authz.checkShop`

## 1. ภาพรวม
- AB2: tag ของออเดอร์ + internal note + bulk action (ทำทีละหลายออเดอร์) + filter ใหม่ในรายการออเดอร์
- AC1: ผู้ซื้อค้นหาออเดอร์ด้วยชื่อร้านได้
- AD1/AD2/AD3: Customer Insights (รายการลูกค้า, channels, export xlsx, รายละเอียด + ออเดอร์ของลูกค้า)
- AD5: `customerKey` แบบเข้ารหัส (AES-256-GCM) แทน base64 เดิม + คอลัมน์/filter ลูกค้าในรายการออเดอร์
- AE1: การแจ้งเตือนฝั่งร้าน (feed + unread count + mark read)
- AG1: ธีมร้าน CUSTOM (Phase A free tokens) — template + config tokens ใน jsonb
- Migration ใหม่: V36, V37, V38, V39, V40 (next = V41)
- AG2-AG5: CUSTOM theme Phase B (media library V40, blocks, sections, hero/header/footer/fonts) - CUSTOM only

## 2. Flyway migrations
### V36 (AB2)
- `order_tags`: unique `ux_order_tags_shop_name` (shop_id, lower(name)), index `ix_order_tags_shop_id`, trigger `modified_date_time`
- `order_tag_assignments`: PK (order_id, tag_id), index `ix_order_tag_assignments_tag`
- `order_bulk_requests`: unique (shop_id, actor_user_id, idempotency_key), index `ix_order_bulk_requests_shop` (shop_id, created_date_time DESC)
- `ix_orders_shop_payment_method` (ซ้ำกับ `ix_orders_payment_method` ของ V32)
### V37 (AE1)
- `merchant_notification_cursor`: PK (shop_id, user_id), `read_all_at` default epoch, FK shops ON DELETE CASCADE
- `merchant_notification_reads`: PK (shop_id, user_id, item_key), index `ix_merchant_notification_reads_shop_user`
- index `ix_order_status_history_order_created`
- หมายเหตุ: ใช้ `CREATE TABLE` ธรรมดา (ไม่มี IF NOT EXISTS)

## 3. ฟีเจอร์
### AB2: tags / note / bulk / filters
| Endpoint | Permission |
|---|---|
| `GET /order-tags` | ORDER_READ |
| `POST /order-tags`, `PUT/DELETE /order-tags/{tagId}` | ORDER_UPDATE |
| `PATCH /orders/{orderId}/internal-note` | ORDER_UPDATE |
| `PUT /orders/{orderId}/tags` (สูงสุด 10) | ORDER_UPDATE |
| `POST /orders/bulk/accept` | ORDER_UPDATE |
| `POST /orders/bulk/process` | SHIPMENT_PACK |
| `POST /orders/bulk/ship` | SHIPMENT_DISPATCH |
| `POST /orders/bulk/cancel` | ORDER_CANCEL |
| `POST /orders/bulk/tags` | ORDER_UPDATE |
- รายการออเดอร์รับ query ใหม่: `tag`, `paymentMethod`, `carrierCode`, `hasTracking`
- bulk: แต่ละออเดอร์รันใน transaction `REQUIRES_NEW` แยกกัน, idempotency เก็บใน `order_bulk_requests`
- DTO: `TagView`, `OrderTagRequest`, `InternalNoteRequest/Response`, `OrderTagsRequest/Response`, `BulkItem`, `BulkRequest`, `BulkTagsRequest`, `BulkResultItem`, `BulkResponse`; `OrderSummary` + `tags`, `carrierCode`, `trackingNumber`; `OrderDetail` + `internalNote`, `tags` (เฉพาะร้าน)
- ไฟล์ใหม่ (order): `MerchantOrderBulkController`, `MerchantOrderTagController`, `OrderBulkService`, `OrderTagService`, `OrderTag`, `OrderTagAssignment`, `OrderTagAssignmentId`, `OrderTagAssignmentRepository`, `OrderTagRepository`, `OrderBulkRequest`, `OrderBulkRequestRepository`, `OrderAddressRepository`

### AC1: ผู้ซื้อค้นหาด้วยชื่อร้าน
- ไม่มี endpoint ใหม่: `q` ของรายการออเดอร์ผู้ซื้อค้นชื่อร้านได้ด้วย (subquery `shopSub`)
- ไฟล์: `OrderQueryService` (ดูข้อ 4), test `BuyerOrderSearchTests`

### AD1 / AD2 / AD3: Customer Insights
| Endpoint (`/customer-insights`) | Permission |
|---|---|
| `GET` (list), `GET /channels` | CUSTOMER_READ |
| `GET /export` (xlsx) | CUSTOMER_READ |
| `GET /{key}`, `GET /{key}/orders` | CUSTOMER_READ |
- ไฟล์ใหม่: `CustomerInsightsService`, `CustomerInsightsDtos`, `MerchantCustomerInsightsController`
- tests: `CustomerInsightsTests`, `CustomerInsightsExportTests`

### AD5: customerKey เข้ารหัส + คอลัมน์ลูกค้า
- `CustomerInsightKeys` (Spring component): AES-256-GCM, IV แบบ deterministic จาก HMAC, AAD = `tsf-ck1:` + shopUuid, รูปแบบ `c1` + base64url, `resolve` คืน `Optional`
- รายการออเดอร์ร้านรับ `customer=<key>`; key ไม่ถูกต้อง -> `CUSTOMER_KEY_INVALID`
- `OrderSummary` + `customerName` (mask ถ้าไม่มี CUSTOMER_PII_READ), `customerKey` (ทั้งคู่ `NON_NULL`)
- ไฟล์ใหม่: `customer/CustomerInsightKeys`; tests: `CustomerInsightKeysTests`, `MerchantOrderCustomerColumnTests`, `MerchantOrderCustomerFilterTests`

### AE1: Merchant notifications
| Endpoint (`/notifications`) | Permission |
|---|---|
| `GET` (list), `GET /unread-count` | SHOP_READ (class) + เช็กรายประเภทใน service |
| `POST /read-all`, `POST /{key}/read` | SHOP_READ |
- ประเภท: ออเดอร์ใหม่/PAID/CANCELLED (ORDER_READ), `REFUND_REQUESTED`, `CUSTOMER_NEW` (CUSTOMER_READ, ชื่อ mask ตาม CUSTOMER_PII_READ), `LOW_STOCK` (PRODUCT_READ)
- ย้อนหลัง 30 วัน (low stock 7 วัน); history กรอง `from_status IS DISTINCT FROM to_status` แล้ว (`MerchantNotificationService:179,191`)
- `customerKey` ใช้ `customerKeys.encode(...)` (`MerchantNotificationService:293-295`)
- ไฟล์ใหม่: `merchant/notification/` -> `MerchantNotificationController`, `MerchantNotificationService`, `MerchantNotificationDtos`, `MerchantNotificationCursor`, `MerchantNotificationRead`; test `MerchantNotificationTests`
- ไม่แตะไฟล์ของ Supitcha (hook `OrderPlacedEvent` ใน `OrderCheckoutService` ~:269 เป็นแค่ทางเลือกในอนาคต ต้องขอความยินยอมก่อน)

## 4. การแก้ในไฟล์ของ Supitcha
### `OrderQueryService` (+242 บรรทัด)
| บรรทัด | เปลี่ยนอะไร | เหตุผล |
|---|---|---|
| :45-63 | `Filters` + `tag`, `paymentMethod`, `carrierCode`, `hasTracking`, `customer`; constructor เดิมยังอยู่ :62-63 | AB2/AD5 filter, ไม่ทำ caller เดิมพัง |
| :68 | `HIDDEN_SORTS` (`grandTotal`, `status`, `paidAt`) | sort ที่ UI ไม่ได้เปิด |
| :76, :83, :92 | inject `CustomerInsightKeys` | AD5 |
| :131-141 | filter tag/tracking + `customerKeys.resolve` -> `CUSTOMER_KEY_INVALID` | AB2/AD5 |
| :190 | subquery tag | AB2 |
| :193-215 | filter carrier / hasTracking | AB2 |
| :242-248 | ผู้ซื้อค้นด้วยชื่อร้าน (`shopSub`) | AC1 |
| :266-267 | `tagMap`, `shipMap` (batch, กัน N+1) | AB2 |
| :270-274 | `canReadPii` ผ่าน CUSTOMER_PII_READ | AD5 |
| :290-310 | `customerName` (`maskName`) + `customerKey` (`encode` :304, :306) | AD5 |
| :388-420 | `detail()` + `internalNote`, `tags` (ร้านเท่านั้น) | AB2 |
| :449-471 | overload `summary()` | ส่ง tags/shipment เข้า summary |
| :489 | ใช้ `findByOrderIdIn` | batch shipment |
### `MerchantOrderController`
- เพิ่ม constant path `ORDER` (regex UUID) ใช้กับ mapping `/{orderId}` ทั้งหมด เพื่อไม่ให้ `/bulk` ถูกจับเป็น orderId
- inject `OrderTagService`; list รับ `tag`, `paymentMethod`, `carrierCode`, `hasTracking`, `customer`
- endpoint ใหม่ `PATCH ORDER/internal-note`, `PUT ORDER/tags`; comment ลูกศรเปลี่ยนเป็น ASCII
### ไฟล์อื่น
- `OrderDtos` :64-90 record ใหม่; `OrderSummary` ~:101-106; `OrderDetail` ~:126-128
- `Order.java:136-137` getter/setter `internalNote` (คอลัมน์มีอยู่แล้ว)
- `ShipmentRepository` + `findByOrderIdIn`
- `ShopCustomerRepository` + `findByIdInAndShopIdAndDeletedAtIsNull`
- `CustomerService:613` `maskName` เป็น public (ใช้ร่วม)
- `MerchantProperties` + `customerKeySecret`
- `OrderIntegrationTests:131` truncate เพิ่ม `order_tag_assignments`, `order_tags`, `order_bulk_requests`

## 5. Config / env
- `application.yml:38`: `app.merchant.customer-key-secret: ${CUSTOMER_KEY_SECRET:}` ถ้าว่างจะ derive จาก `dataEncryptionSecret` ด้วย HMAC แยก domain
- `application-test.yml`: `customer-key-secret:` (ว่าง)
- Frontend (`.env.example`, `.env.development`): `VITE_FF_ORDER_FILTERS`, `ORDER_NOTES`, `ORDER_TAGS`, `BULK_ORDERS`, `MERCHANT_NOTIFICATIONS_API` = true; `TERMS_VERSION`, `ADDRESS_ID_CHECKOUT`, `SHIPPING_COVERAGE`, `DELIVERY_ESTIMATE` = false
- root `.gitignore` มี `!frontend/.env.development`

## 6. Tests
- `.\mvnw test` (24 ต.ค. 2026 14:29 เวลาไทย): **208 run / 9 failures / 0 errors**
- 9 failures เป็นของเดิม (OPTION_VALUE_UNUSED -> 400) ใน `ProductCatalogIntegrationTests`, `ProductVariantIntegrationTests`, `TranslationIntegrationTests`; ไม่เกี่ยวกับงานวันนี้

## 7. ความเสี่ยงที่ยังเปิดอยู่
- bulk ส่งข้อความ exception ดิบกลับ client (`OrderBulkService:95`, `:114`)
- idempotency race: บันทึก request ตอนท้าย (`store` :118) และ client สร้าง key ใหม่เมื่อ retry
- mask ไม่ตรงกัน: list ใช้ CUSTOMER_PII_READ แต่ drawer ใช้ SHIPMENT_ADDRESS_READ (`OrderQueryService:433-438`); ค้น `q` ด้วยชื่อ/เบอร์ได้โดยไม่ดูสิทธิ์ PII (:236-237)
- Insights คำนวณใน memory จากออเดอร์ทั้งร้าน (ร้านใหญ่จะช้า)
- `shopCustomerId` โผล่ใน DTO (`CustomerInsightsDtos:17`)
- `OrderTagService.writeHistory` (~:205) เขียน status history ที่ from = to (feed AE1 กรองแล้ว แต่ timeline อื่นอาจเห็น)
- V36 index ซ้ำ; `order_bulk_requests` ไม่มี cleanup

## 8. Merge / action items
- [ ] ตั้ง `CUSTOMER_KEY_SECRET` บน prod (ห้ามใช้ซ้ำกับ data-encryption secret, ห้าม log)
- [ ] รัน Flyway V36, V37
- [ ] แจ้งทีม: key/URL ลูกค้าแบบ base64 เดิมใช้ไม่ได้แล้ว
- [ ] review การแก้ในข้อ 4 (`OrderQueryService`, `MerchantOrderController`, `OrderDtos`, `Order`, repos, `CustomerService`)
- [ ] `git add` ไฟล์ใหม่ทั้งหมด (backend + migrations + tests + `docs/` note นี้)
- [ ] ตั้ง frontend flags ตามข้อ 5 ในแต่ละ environment

## 9. P0 fixes (25/10/2026)
- `OrderQueryService.timeline()` (ไฟล์ร่วม — แก้เฉพาะตอนอ่าน, การเขียน history ไม่เปลี่ยน): แถวที่ `fromStatus == toStatus`
  - มี `metadata.event` (ORDER_ACCEPTED, NOTE_UPDATED, TAGS_UPDATED, money events) → คงไว้เป็น event นั้น แต่ส่ง `fromStatus = null`
  - ไม่มี event → ไม่แสดง (จะกลายเป็น STATUS_CHANGED ปลอม)
- `OrderBulkService` (ของเรา): error รายออเดอร์ไม่ส่งข้อความดิบอีกต่อไป (lock/optimistic → `ORDER_VERSION_CONFLICT`, อื่น ๆ → `REQUEST_FAILED` + log ฝั่ง server); Idempotency-Key ซ้ำพร้อมกัน → replay แทน 500
- ไม่ได้แตะ order/checkout/cart services อื่นของ Supitcha

## 10. แก้ test ที่ flaky 2 ตัว (25/10/2026) — ไม่มี migration
1. `CustomerIntegrationTests.crudWithMultipleAddressesTagsNotesSearchFilterAndPaging:123` (บางครั้ง `addresses[0].defaultShipping` = false)
   - สาเหตุ: ที่อยู่หลายรายการที่บันทึกใน request เดียวได้ `created_date_time` เท่ากัน (`Instant.now()` บน Windows ละเอียดไม่พอ) → order by `createdDateTime, id` ไปตัดสินด้วย UUID สุ่ม → ลำดับสลับแบบสุ่ม
   - แก้: `catalog/AuditedEntity.java:34-38` เพิ่ม `initCreatedDateTime(Instant)` (ตั้งได้ก่อน persist เท่านั้น); `customer/CustomerService.java:203-208` (create) และ `:542-547` (`replaceAddresses`) ตั้งเวลาเริ่ม + i ไมโครวินาที ต่อรายการ
   - พฤติกรรม: ลำดับที่อยู่ = ลำดับใน request เสมอ (ตั้งใจไว้แต่เดิม); ไม่เปลี่ยน schema / query
2. `TranslationIntegrationTests.thaiOnlyProductPublishesAndPublicLocaleFallsBackUntilTranslated:195` (บางครั้งได้ `[zh]` แทน `[en, zh]`)
   - สาเหตุ: `TranslationService.afterCommit` เป็น `@Async` → background thread แปล `en` เสร็จก่อนที่ test จะอ่านแถว PENDING (race)
   - แก้: `translation/TranslationService.java:5,160-167` เพิ่ม property `app.translation.process-after-commit` (ค่า default `true` = prod เหมือนเดิม); `src/test/resources/application-test.yml:17-19` ตั้ง `false` และ `poll-delay-ms: 86400000` → test เรียก `processPendingForTests()` เอง
   - พฤติกรรม prod: ไม่เปลี่ยน (ถ้าไม่ตั้ง property)
- ผล: `.\mvnw test` 2 รอบ = 210 tests, เหลือ 9 failures baseline (ProductVariant 5, ProductCatalog 3, Translation optionCodesStayUntranslated 1)

## Best sellers: backend + mock orders (2026-10-25)

The catalog package reads `orders` / `order_items` **read-only**. Nothing in `com.thaishopfun.order` (Supitcha) changed, there are no migrations (latest is still V37), and stock is untouched.

**Sold rule (`ProductService.SOLD_ORDER_FILTER`):** an order line counts when the order is `DELIVERED` / `COMPLETED`, or when `payment_status = PAID` and the status is one of `PAID_AWAITING_MERCHANT_CONFIRM`, `MERCHANT_CONFIRMED`, `READY_TO_PACK`, `PACKING`, `PACKED` or `DISPATCHED`. Unpaid, cancelled, refunded and refund-requested orders never count.

**New: `GET /api/products/best-sellers`** (`PublicBestSellerController`, `@PublicEndpoint`)
- Params: `categoryId`, `shopSlug`, `period=7d|30d|all` (by `placed_at`; anything else returns 400 `INVALID_PERIOD`), `minPrice`, `maxPrice` (on `base_price`), `page`, `size` (max 100), `lang` / `locale` / `Accept-Language`.
- Lists only products that are PUBLISHED, not deleted, in an APPROVED shop, and have at least one counted sale. Order: `soldCount desc`, then newest product, then id.
- Response: `PageResponse<PublicBestSellerResponse>` with `{rank, soldCount, id, slug, nameTh, nameEn, nameZh, categoryId, brand, basePrice, compareAtPrice, currency, skus, images, shopSlug, shopName, shopNameEn, rating: null, reviewCount: null}`.
- The response contains no buyer, order or customer data.

**Changed: `/api/shops/{slug}/products`**
- New `sort=best_selling` (aliases `bestselling`, `sold`). It orders by all-time sold quantity, then newest.
- Every row now carries `soldCount`, the all-time total (0 when there are no sales).
- `PublicProductResponse` gained a trailing `Long soldCount`. The old 17-argument constructor is kept, so the other views return `soldCount: null`.

**Frontend**
- `api.ts`: `bestSellerApi.list`, plus the `BestSellerProduct` and `BestSellerQuery` types; `ProductSort` gains `'best_selling'`; `PublicProduct.soldCount`.
- `BestSellersSection` now uses `sort: "best_selling"`, and `BESTSELLERS_ENABLED` is `true`.
- `BestSellerPage.tsx` is wired to the API with the same markup and CSS:
  - Real images sit in the `.best-sprite` frame.
  - Category chips and checkboxes come from `/api/categories`, and the price radios and custom min/max work.
  - Rank tabs map to periods: ยอดขายสูงสุด → all, คะแนนรีวิวสูงสุด → 30d (there are no reviews yet), ประจำสัปดาห์ → 7d, OTOP → all plus the OTOP category if one exists.
  - Add-to-cart goes through `useOptionalCart().add` (CartContext is unchanged). Stars are hidden while `rating` is null, and the page shows a loading state and the empty state "ยังไม่มีข้อมูลยอดขาย".

**Tests:** `BestSellerIntegrationTests` covers ranking, the cancelled exclusion, shop, price and category filters, paging rank, the 7d/30d/all periods, the bad period 400, unpublished exclusion, the `best_selling` sort and `soldCount`.

**Mock orders (dev DB only):** `C:\Users\DELL\seed-mock\seed_mock_orders.sql` and `cleanup_mock_orders.sql`, applied with `q.ps1 -File`.
- Creates 5–15 orders per `seed-*` shop, each `COMPLETED` / `PAID` / `FULFILLED`, `channel=MANUAL`, `stock_committed=false`.
- `order_number` is `SEED-<SHOP>-NNN` and `idempotency_key` is `seed-mock-order:<slug>:<n>`.
- `placed_at` is random within the last 30 days. Quantities are skewed toward the first products of each shop.
- `buyer_user_id` and `shop_customer_id` are NULL, so no user is linked.
- The script is re-runnable because it deletes earlier SEED- orders of seed shops first. The cleanup script deletes only those orders.
- Applied on 2026-10-25: 106 orders and 487 units across 12 shops.


## Product reviews + OTOP category (V38)

**Migration `V38__otop_category_and_review_index.sql`** (runs on the next backend restart):
- `product_reviews` already existed since V1 (id, shop_id, product_id, order_id, order_item_id **UNIQUE**, buyer_user_id → users, rating smallint CHECK 1..5, title, body = comment, status PENDING/PUBLISHED/HIDDEN/REJECTED default PUBLISHED, deleted_at, audit columns). V38 only adds `ix_product_reviews_product_status (product_id, status)` and a table comment.
- Idempotent OTOP category: `INSERT INTO hub_categories … slug 'otop-products', 'สินค้า OTOP' / 'OTOP Products' / 'OTOP 一村一品', sort_order 14 … ON CONFLICT (slug) DO NOTHING`. `ProductCategorySeeder` also ensures the row (fresh DBs/H2 tests). Always look it up **by slug**, never by id.

**Backend (`com.thaishopfun.review`)**: `ProductReview` (entity on the V1 table), `ProductReviewRepository`, `ProductReviewService`, `ReviewDtos`, `BuyerReviewController`, `PublicReviewController`.
- `POST /api/me/reviews` (signed-in buyer, `@RequirePermission("BUYER_ORDER_READ")`, implicit BUYER role) body `{orderItemId, rating 1-5, comment?}` → 201 `{id, rating, comment, reviewerName, createdAt}`. Errors: 401 anonymous; 400 `INVALID_RATING` / `ORDER_ITEM_REQUIRED` / `COMMENT_TOO_LONG` (2000); 404 `ORDER_ITEM_NOT_FOUND` (missing **or someone else's** item); 409 `REVIEW_NOT_ALLOWED` (order not DELIVERED/COMPLETED); 409 `REVIEW_ALREADY_EXISTS` (one review per order item).
- `GET /api/products/{productId|productSlug}/reviews?shopSlug=&page=&size=` (public, size ≤ 50, newest first, PUBLISHED + not deleted, product must be PUBLISHED) → `{items[{id, rating, comment, reviewerName, createdAt}], page, size, total, ratingAvg, reviewCount}`. A slug used by several shops → 400 `PRODUCT_AMBIGUOUS` (pass `shopSlug`). `reviewerName` is masked (`ส*** ผ.`); user id, email, phone are never returned.
- **Read-only dependency on the order module (Supitcha)**: eligibility reads `OrderItem`/`Order` (buyer_user_id, status) via JPQL; the order package is not modified.
- Aggregates (avg rounded to 1 decimal + count, PUBLISHED only) fill `rating` / `reviewCount` in `GET /api/products/best-sellers` (null when no reviews). Not added to the per-shop `PublicProductResponse` yet (use the reviews endpoint).
- Best sellers new params: `sort=best_selling` (default) | `top_rated` (products with ≥ 1 review; avg desc → count desc → sold desc; 400 `INVALID_SORT` otherwise) and `categorySlug=` (e.g. `otop-products`).
- Merchant hide: not exposed as an API yet (status `HIDDEN` is honoured by every read; set via SQL for now).

**Frontend**: `api.ts` `BestSellerQuery.sort/categorySlug`, `reviewApi.list/create`, types `ProductReview(Page)`. `BestSellerPage` tab "คะแนนรีวิวสูงสุด" → `sort=top_rated`; tab "สินค้า OTOP ขายดี" → `categorySlug=otop-products`; stars show when `rating` is present. The product detail page has no reviews section, so nothing was added there (design unchanged).

**Seed (`C:\Users\DELL\seed-mock`, re-runnable, seed data only)**:
- `seed_mock_orders.sql` now upserts fake buyers `seed-buyer1..8@example.com` (phones 0999000101..08, password hash copied from `seed-owner1`, never real users) and assigns SEED-% orders to them round-robin; deletes the SEED orders' reviews before re-creating orders.
- `seed_mock_reviews.sql`: idempotent OTOP row, OTOP assignment (all `seed-thai-craft` products + first 30 `seed-baan-snack`), ~75% of SEED order lines get a PUBLISHED review (ratings skewed 4–5, Thai comments). Cleanup: `cleanup_mock_reviews.sql` (reviews + category revert); `cleanup_mock_orders.sql` also removes the reviews and unreferenced seed buyers.

**Tests**: `ProductReviewIntegrationTests` (eligibility, duplicate, masking, aggregates, top_rated, OTOP slug, HIDDEN excluded).

## AG1: Custom shop theme (Phase A — free tokens)

### V39 (`V39__shop_theme_custom_template.sql`)
- Drop / re-add `shop_themes_template_key_check` ให้รับ `'GENERAL','FASHION','FOOD_BEVERAGE','CUSTOM'`
- ไม่แก้ migration เดิม และไม่เปลี่ยนแถว `shop_themes` ของร้านที่มีอยู่

### Config tokens (jsonb ใน `shop_themes.config` — ไม่มีคอลัมน์ใหม่)
**ใช้ได้เฉพาะ `CUSTOM`** (ดู AG1.1 ด้านล่าง); editor แสดง control เฉพาะ CUSTOM; field เป็น optional + default:
- `colors`: `accent` (validate `#RRGGBB`), `surface`, `muted`, `ctaBackground`, `ctaText`, `border`
- `gradients`: `{ hero?, page?, section? }` → `{ angle: 0–360, stops: [{ color:#RRGGBB, at:0–100 }] }` 2–4 stops (โครงสร้างเท่านั้น ห้าม CSS string)
- `style`: `shadow` = `NONE|SOFT|MEDIUM|STRONG`, `headingTextShadow` boolean; `radius` 0–32 เดิม
- `productCard`: `imageRatio`, `ctaStyle`, `showPrice`, `edgeToEdge`, `titleAlign`
- `sectionSettings`: map ตาม section key 10 ตัว → `background`, `align`, `columns` (เฉพาะ section สินค้า), `paddingY`

### Validation / strip
- CUSTOM: `ShopThemeService.stripUnknown` whitelist ระดับบน + ภายใน object ใหม่ (รวม gradient → `angle`,`stops` และ stop → `color`,`at`; section key ที่ไม่รู้จักถูกตัดทิ้ง ไม่ 400) ก่อนบันทึก
- ตรวจ `url(`, `expression`, `;`, `}`, `<` เฉพาะค่า token (สี/gradient) — **ไม่ตรวจข้อความ hero** (hero มี `;` ได้ทุกเทมเพลต)
- error ใหม่: `THEME_GRADIENT_INVALID`, `THEME_TOKEN_INVALID` (+ path ของ field)
- endpoint เดิมไม่เปลี่ยน; response เดิมเพิ่ม field ได้เท่านั้น
- `CUSTOM` ไม่ถูก fallback เป็น GENERAL; คีย์ template ที่ไม่รู้จักยัง fallback GENERAL

### ขอบเขตที่ไม่แตะ
- ไม่แก้ package order / cart / checkout, `AuthContext`, `CartContext`
- ร้านที่ไม่ได้ตั้งค่าใหม่ต้องแสดงผลเหมือนเดิม (GENERAL / FASHION / FOOD_BEVERAGE)

### AG1.1: แยก CUSTOM ออกจากธีมเดิม (ไม่กระทบ GENERAL / FASHION / FOOD_BEVERAGE)
- **Backend**: non-CUSTOM ทำงานเหมือนก่อน AG1 — ไม่มี whitelist ใหม่, validate สีแค่ 4 สีหลักแบบเดิม; คีย์ token ของ AG1 (`gradients`, `productCard`, `sectionSettings`, `colors.surface/muted/ctaBackground/ctaText/border`, `style.shadow/headingTextShadow`) ถูก **ตัดทิ้งเงียบ ๆ** (ไม่ validate, ไม่ 400)
- **Frontend**: `normalizeTheme` ทิ้ง token AG1 เมื่อ template ≠ CUSTOM; `themeCssVars(config, templateKey)` คืนชุดตัวแปร CSS แบบก่อน AG1 ทุกตัวสำหรับธีมเดิม; `ProductGrid`/`ProductRail` ใช้ `useCustomProductCard` (memo + CUSTOM เท่านั้น); hero gradient ใช้ class `shop-hero-gradient` (CUSTOM เท่านั้น)
- **CSS**: ทุก rule AG1 ท้าย `shop-mall.css` scope ด้วย `.shop-themed[data-template="CUSTOM"]` (shell root ใส่ `data-template` อยู่แล้ว) + media query columns: ≤1024px สูงสุด 3 คอลัมน์, ≤640px 2 คอลัมน์; ตรวจด้วย `node frontend/scripts/check-ag1-css-scope.mjs`
- **Editor**: dropdown ใหม่ (shadow, productCard, sectionSettings) ใช้ `SearchableSelect` สูง 40px (`styles/theme-token-select.css`) และแสดงเฉพาะ CUSTOM

**Tests**: `ShopThemeIntegrationTests` (บันทึก CUSTOM, strip unknown keys, gradient/hex/enum 400, GENERAL ยังอ่าน/เขียนได้, public shop ส่ง token ใหม่, GENERAL/FASHION hero มี `;` บันทึกได้ + token AG1 ถูกตัดทิ้ง, CUSTOM ตัดคีย์ภายใน gradient/stop/sectionSettings)

## AG2–AG5: CUSTOM theme Phase B

ทุกอย่างในส่วนนี้ **ใช้ได้เฉพาะ template `CUSTOM`** — บันทึกเป็น GENERAL / FASHION / FOOD_BEVERAGE แล้ว backend ตัดทิ้งเงียบ ๆ (ไม่ 400) และ frontend ไม่ render. ไม่มีคอลัมน์ใหม่ใน `shop_themes` (ทุกอย่างอยู่ใน jsonb `config`).

### V40 media table (`V40__shop_theme_media.sql`)
- ตาราง `shop_theme_media` (id uuid, `shop_id` → `shops(id)` ON DELETE CASCADE, `kind` = `IMAGE|VIDEO|FONT`, `storage_path`, `original_filename`, `mime_type`, `size_bytes`, `width/height`, `sha256`, `deleted_at` soft delete) + index `(shop_id, kind)` + partial unique `(shop_id, sha256) WHERE deleted_at IS NULL` + trigger `modified_date_time`
- ไฟล์จริงอยู่ที่ `{STORAGE_ROOT}/theme-media/{shop uuid}/{random uuid}.{ext}` — ชื่อไฟล์สุ่มใหม่เสมอ ไม่ใช้ชื่อจาก client
- ไม่แก้ migration เดิม (V39 และก่อนหน้าไม่ถูกแตะ)
- Quota (`application.yml` → `app.merchant.theme-media`): รวม 200 MB / ร้าน, รูป 200 ไฟล์ (≤5 MB, ≤4000 px), วิดีโอ 5 ไฟล์ (≤20 MB), ฟอนต์ 4 ไฟล์ (≤1 MB); เต็ม → 409 `THEME_MEDIA_QUOTA_EXCEEDED`

### Media APIs
- Merchant (`/api/merchant/settings/theme/media`, ต้องเป็น CUSTOM): `POST` multipart (`kind`, `file`) / `GET ?kind=` / `DELETE /{id}` — ลบสื่อที่ config ยังอ้างอิงอยู่ → 409 `THEME_MEDIA_IN_USE` (นับจาก `ShopThemeMediaService.collectMediaIds` ครอบคลุม banners, blocks, hero/header/footer/fontCustom)
- Public: `GET /api/shops/{slug}/theme-media/{uuid}` (ส่ง `X-Content-Type-Options: nosniff`, 404 ถ้า slug/uuid ไม่ตรงร้าน)
- URL เป็น **derived field** (`/api/shops/{slug}/theme-media/{uuid}`) คำนวณตอนอ่านจาก `mediaId` เท่านั้น ไม่เก็บใน DB และ frontend ไม่ส่งกลับตอนบันทึก (backend whitelist ตัดอยู่แล้ว)

### `config.layout.blocks` (AG3)
- CUSTOM แก้ลำดับ/เปิด-ปิด/ซ้ำ block ได้ (≤30 block, id `^[a-z0-9-]{1,40}$`, `HERO` และ `HOURS` เป็น singleton). `ShopSectionKey` ยังคง 10 ตัว; `ShopBlockType` = 10 ตัวเดิม + 5 section ใหม่
- แต่ละ block มี `settings` (`background` NONE|SURFACE|PRIMARY|GRADIENT|IMAGE, `align`, `paddingY`, `columns` เฉพาะ block สินค้า, `fullWidth`, `overlay` 0–80 เมื่อเป็น IMAGE); `sections` เดิมถูก derive จาก blocks (sync ฝั่ง backend) เพื่อให้ client เก่ายังอ่านได้

### New sections (AG4) — `content` ต่อ block
- `STEPS` (2–6 ขั้น, icon / mediaId), `FEATURE` (IMAGE_LEFT|IMAGE_RIGHT, bullets ≤5, CTA), `GALLERY` (GRID|MASONRY|CAROUSEL, ≤24 รูป จากคลังสื่อ), `SOCIAL` (TikTok `videoIds` ≤6 เท่านั้น), `RICH_TEXT` (markdown ≤5000 ตัวอักษร, backend ทำ sanitized HTML ให้)
- ข้อความทั้งหมดเป็น `LocalizedString` `{ th, en? }`; ลิงก์เป็น internal path หรือ `https://` เท่านั้น

### AG5 chrome (hero MEDIA_FULL / header / footer / fonts)
- **Hero `MEDIA_FULL`** (`ShopSectionVariant.MEDIA_FULL`, อนุญาตเฉพาะ HERO; non-CUSTOM → strip เป็น `FULL`) — extras อยู่ใน `config.hero`:
  `background {type IMAGE|VIDEO|GRADIENT, mediaId?, posterMediaId?}`, `overlay` 0–80, `heightVh` 50–100, `textAlign` LEFT|CENTER, `headlineSize` L|XL, `stickers` ≤6 `{mediaId(IMAGE), x 0–100, y 0–100, size 40–240, rotate -30–30, hideOnMobile}`, `ctaSecondary {label, link}`
  - วิดีโอ muted + autoplay + loop + playsinline; ผู้ใช้ `prefers-reduced-motion` เห็นรูป poster แทน
  - editor: ปรับค่าด้วย number input หรือ **ลากสติกเกอร์บน preview** (`HeroStickerDragContext`)
  - Legacy hero `FULL` / `STRIP` ทำงานเหมือนเดิมทุก template
- **Header** (`config.header`): `layout` LOGO_LEFT|LOGO_CENTER, `background` NONE|SURFACE|PRIMARY|GRADIENT|IMAGE (+ `gradient` / `backgroundMediaId` / `overlay`), `sticky`, `menu` ≤8 `{label, link}`, `showSearch` (ไปหน้าค้นหาในร้านเดิม), `showSocialIcons`, `announcementBar? {text, link?, backgroundColor, textColor}`. **ไม่มี header = ใช้ mall header/tabs เดิม** (header ใหม่ render *เพิ่ม* เหนือของเดิม ไม่แทนที่)
- **Footer** (`config.footer`): `columns` ≤4 `{title, links ≤8}`, `about?`, `showContacts`, `showSocial`, `background`, `copyright?`. ข้อมูลติดต่อมาจาก `publicPhone` / `publicEmail` / ที่อยู่ร้านที่อยู่ใน `PublicShop` **เท่านั้น — ห้ามใช้ข้อมูลส่วนตัวของ owner**
- **Fonts**: `font` (string เดิม) ยังเป็น fallback; CUSTOM เพิ่ม `fontCustom {headingMediaId?, bodyMediaId?, fallback}` (ต้องเป็นสื่อ `FONT` / WOFF2). Frontend สร้าง `@font-face` เอง (family `shop-font-{slugHash}`, `font-display: swap`) จาก URL สื่อของเราใน `<style>` ของ React — ไม่มี CSS จาก merchant และไม่ใช้ `dangerouslySetInnerHTML`; โหลดไม่สำเร็จ → ใช้ fallback
- Validation: ช่วงตัวเลขนอกกำหนด / media ไม่มี-ไม่ใช่ของร้าน-kind ผิด / ลิงก์ไม่ปลอดภัย / สีไม่ใช่ `#RRGGBB` → 400 `VALIDATION_ERROR` พร้อม `fieldErrors[]` (path เช่น `config.hero.stickers[0].x`, `config.header.menu[1].link`); logic อยู่ใน `ShopThemeChrome` (pure helper เหมือน `ShopThemeBlocks`)
- **CSS**: `frontend/src/styles/shop-chrome.css` (marker `/* AG5 chrome`) ทุก selector scope ด้วย `.shop-themed[data-template="CUSTOM"]`; `check-ag1-css-scope.mjs` ตรวจ `shop-hero-media`, `shop-chrome-`, `shop-custom-header`, `shop-custom-footer`, `shop-sticker`

### Security
- **Magic bytes**: ตรวจ signature จริงของไฟล์ ไม่เชื่อ `Content-Type`/นามสกุลจาก client — JPEG / PNG / WebP (`RIFF…WEBP`), MP4 (`ftyp` ที่ offset 4), WOFF2 (`wOF2`); ไฟล์เกินขนาด/มิติ/ไม่ตรง kind → 400
- **Block SVG**: ไม่รับ SVG (หรือ HTML/script ที่ปลอมเป็นรูป) เพราะไม่ผ่าน magic bytes; ไฟล์เสิร์ฟด้วย `X-Content-Type-Options: nosniff` และ path ถูกจำกัดใต้ `theme-media/` (ไม่มี path traversal)
- **Sanitize**: ทุก object ใน config ผ่าน whitelist ตอนบันทึก; `RICH_TEXT` ถูก sanitize เป็น HTML ที่ปลอดภัยฝั่ง server; ไม่มีสตริง CSS/HTML จาก merchant ถูกนำไป render โดยตรง (gradient เป็นโครงสร้าง angle + stops)
- **TikTok allowlist**: บล็อก SOCIAL รับเฉพาะ video id ตัวเลข `^\d{10,25}$` — frontend สร้าง URL `https://www.tiktok.com/embed/v2/{id}` เอง ไม่รับ embed HTML/URL
- **CSP meta**: `frontend/index.html` มี `<meta http-equiv="Content-Security-Policy" content="frame-src 'self' blob: https://www.tiktok.com; base-uri 'self'">` (จำกัด iframe ให้เฉพาะ TikTok และ same-origin)
- **ลิงก์**: internal `^/(?!/)…` หรือ `https://` เท่านั้น (ทั้ง hero CTA, menu, footer, announcement) — `javascript:` / `data:` / `//host` → 400

### ขอบเขตที่ไม่แตะ (AG2–AG5)
- **ไม่แตะ order / cart / checkout**, `AuthContext`, `CartContext`, migration เดิม, `.env`
- GENERAL / FASHION / FOOD_BEVERAGE render และบันทึกเหมือนก่อน AG2

### ไฟล์หลัก AG5
- Backend: `merchant/theme/ShopThemeChrome.java` (ใหม่), `ShopSectionVariant` (+`MEDIA_FULL`), `ShopThemeService` (strip/sanitize/validate/responseFor), `ShopThemeMediaService` (mediaRefs + kind check), `ShopThemeBlockContent` (helper visibility), `ValidationMessages` (+`THEME_CHROME_INVALID`, `THEME_FONT_CUSTOM_INVALID`)
- Frontend: `theme/shopTheme.ts`, `theme/customFonts.ts`, `components/shop/{ShopThemeShell,ShopCustomHeader,ShopCustomFooter,chromeShop}`, `components/shop/sections/{HeroSection,HeroMediaFull}`, `components/merchant/{ShopChromeEditor,ShopThemeEditor,ShopBlocksEditor}`, `hooks/usePrefersReducedMotion.ts`, `styles/shop-chrome.css`, locales th/en/zh (`merchant.json` → `storeTheme.chrome.*`, `public.json` → `shop.chrome.*`)

**Tests**: `ShopThemeChromeIntegrationTests` (ช่วงตัวเลข/limit, media kind + ownership, ลิงก์ไม่ปลอดภัย → 400, non-CUSTOM strip header/footer/fontCustom/MEDIA_FULL → FULL, CUSTOM บันทึก→อ่านครบ, hero FULL/STRIP เดิมยังใช้ได้) + `ShopThemeMediaIntegrationTests`, `ShopThemeBlock*IntegrationTests`

### AG2–AG5: ส่วนเสริมสำหรับ Supitcha (ตรวจจาก `git diff` 26 ต.ค. 2026)
- **Endpoint สรุป**
  - `POST /api/merchant/settings/theme/media` (multipart `kind` + `file`), `GET /api/merchant/settings/theme/media?kind=`, `DELETE /api/merchant/settings/theme/media/{id}`: ต้องล็อกอินเป็น merchant ของร้าน (`MerchantThemeMediaController`) และร้านต้องเป็น CUSTOM
  - `GET /api/shops/{slug}/theme-media/{id}`: public (`ShopController`), เงื่อนไขการมองเห็นเหมือนหน้าร้าน, `Cache-Control: public, max-age=1h`
- **`MerchantStorageService`**: เพิ่ม `storeThemeMedia(...)` / `openThemeMediaFile(...)` รับนามสกุล `.jpg .png .webp .mp4 .woff2` เท่านั้น และจำกัด path ใต้ `theme-media/`. ฟังก์ชันอัปโหลดเดิม (รูปสินค้า/โลโก้) ไม่เปลี่ยน
- **Config keys ใหม่ใน `application.yml`** (บอกแค่ชื่อคีย์):
  - `app.merchant.theme-media.max-total-bytes`, `max-images`, `max-videos`, `max-fonts`, `max-image-bytes`, `max-video-bytes`, `max-font-bytes`, `max-image-dimension` (`ThemeMediaProperties`)
  - `spring.servlet.multipart.max-file-size` / `max-request-size` ถูกขยายจาก 5MB/6MB เป็น 20MB/21MB เพื่อรองรับวิดีโอ **กระทบการอัปโหลดทุก endpoint** ส่วนขนาดของรูปสินค้ายังถูกจำกัดใน service เดิม
  - `customer-key-secret` มาจากงาน AD5 (ค่าอยู่ใน `.env` ห้าม commit)
- **คลาสใหม่ (`merchant/theme`)**: `ShopThemeMedia`, `ShopThemeMediaKind`, `ShopThemeMediaRepository`, `ShopThemeMediaService`, `MerchantThemeMediaController`, `ThemeMediaProperties`, `ShopBlockType`, `ShopThemeBlocks`, `ShopThemeBlockContent`, `ShopThemeChrome`, `ShopThemeRichText`. แก้: `ShopThemeService`, `ShopSectionVariant`, `ThemePresets`, `MerchantDtos`, `ShopController`, `ValidationMessages`
- **ผลกระทบต่อส่วนของ Supitcha: ไม่มี** งาน AG2–AG5 ไม่แตะ order / cart / checkout, `AuthContext`, `CartContext`. ไฟล์ `order/*` ที่ขึ้นใน `git status` เป็นงาน AB2/AC1/AD5 เดิม (ดูหัวข้อด้านบน) และ diff ของ `order/*` ไม่มีโค้ดที่เกี่ยวกับ theme
- **Flyway**: migration ล่าสุดตอนนี้คือ **V40** (`V40__shop_theme_media.sql`) **ไฟล์ถัดไปต้องเป็น V41**
- **Tests ใหม่**: `ShopThemeMediaIntegrationTests`, `ShopThemeBlocksIntegrationTests`, `ShopThemeBlockContentIntegrationTests`, `ShopThemeChromeIntegrationTests` (+ `ShopThemeIntegrationTests` จาก AG1). จำนวนเทสรวม **ยังไม่ได้ตรวจซ้ำหลัง AG2–AG5** (ก่อน AG2 คือ 221 ผ่าน, 0 fail)

## Collections (V41) + Product configurator (V42) — 27 ต.ค. 2026
> สร้างจาก `git status` ณ 27 ต.ค. 2026 ยังไม่ได้ commit. หน้าร้าน (public) ใช้ได้เฉพาะร้าน template **CUSTOM**; GENERAL / FASHION / FOOD_BEVERAGE ไม่เปลี่ยน

### Flyway
- **V41** `V41__shop_collections.sql`
  - `shop_collections` (id, shop_id FK shops ON DELETE CASCADE, `slug` unique ต่อร้าน `uq_shop_collections_shop_slug` + check `^[a-z0-9]+(-[a-z0-9]+)*$`, `name_th`, `name_en`, `description`, `cover_media_id` FK `shop_theme_media` ON DELETE SET NULL, `sort_order`, `is_published` default false, audit cols + trigger `modified_date_time`)
  - `shop_collection_products` PK (collection_id, product_id), `sort_order`; FK ทั้งสองฝั่ง ON DELETE CASCADE; index `ix_shop_collection_products_sort`, `ix_shop_collection_products_product`
- **V42** `V42__product_configurators.sql`
  - `product_configurators` PK `product_id` (FK products ON DELETE CASCADE), `enabled` default false, `schema` jsonb default `{"steps":[]}`, `version` int (>0, +1 ทุกครั้งที่บันทึก), audit cols + trigger
- **migration ล่าสุด = V42 → ไฟล์ถัดไปต้องเป็น V43**

### Config keys ใหม่ (`application.yml`)
- `app.merchant.collections.max-per-shop` = 50, `app.merchant.collections.max-products-per-collection` = 500 (`CollectionProperties`)
- `app.features.configurator.enabled` = `${APP_FEATURES_CONFIGURATOR_ENABLED:false}` (`ConfiguratorProperties`) — **ค่าเริ่มต้นปิด**; `application-test.yml` เปิดไว้สำหรับเทส
- Frontend flag: `FEATURES.productConfigurator` ← `VITE_FF_PRODUCT_CONFIGURATOR` (default false) ใน `frontend/src/config/features.ts`

### Endpoints — Collections
| Method | Path | Auth |
|---|---|---|
| GET | `/api/merchant/shops/{shopId}/collections` | PRODUCT_READ |
| POST | `/api/merchant/shops/{shopId}/collections` | PRODUCT_UPDATE |
| PUT | `/api/merchant/shops/{shopId}/collections/order` (ลำดับคอลเลกชันครบทุกตัว) | PRODUCT_UPDATE |
| GET | `/api/merchant/shops/{shopId}/collections/{id}` | PRODUCT_READ |
| PUT / DELETE | `/api/merchant/shops/{shopId}/collections/{id}` | PRODUCT_UPDATE |
| POST | `/api/merchant/shops/{shopId}/collections/{id}/products` (เพิ่มสินค้า) | PRODUCT_UPDATE |
| PUT | `/api/merchant/shops/{shopId}/collections/{id}/products` (ตั้ง/เรียงลำดับทั้งชุด) | PRODUCT_UPDATE |
| DELETE | `/api/merchant/shops/{shopId}/collections/{id}/products/{productId}` | PRODUCT_UPDATE |
| GET | `/api/shops/{slug}/collections` | public |
| GET | `/api/shops/{slug}/collections/{collectionSlug}` (มี page ของสินค้า) | public |
- กติกา: สินค้าต้องเป็นของร้านเดียวกันและยังไม่ถูกลบ; cover ต้องเป็น IMAGE ในคลังสื่อของร้าน; description ≤1,000 ตัวอักษร ไม่รับ HTML; slug ว่าง = สร้างอัตโนมัติ; เกิน limit → `COLLECTION_LIMIT_REACHED` / `COLLECTION_PRODUCT_LIMIT_REACHED`; ลำดับไม่ครบ/ซ้ำ → `COLLECTION_ORDER_INVALID`
- Public: แสดงเฉพาะ `is_published` ของร้าน APPROVED + template CUSTOM และเฉพาะสินค้า PUBLISHED ไม่ถูกลบ, ใช้ DTO การ์ดเดียวกับรายการสินค้า (`ProductService.publicCards`) — ไม่มีข้อมูล owner / user id
- Theme block ใหม่ `COLLECTIONS` (`ShopBlockType`, content: `collectionIds` ≤12 + `limit`) — CUSTOM เท่านั้น; route หน้าร้าน `/shop/:slug/collections/:collectionSlug`

### Endpoints — Configurator (ปิดอยู่ = 404 `FEATURE_DISABLED` ทุกตัว)
| Method | Path | Auth |
|---|---|---|
| GET / PUT | `/api/merchant/shops/{shopId}/products/{productId}/configurator` | PRODUCT_READ / PRODUCT_UPDATE |
| GET | `/api/shops/{slug}/products/{productId}/configurator` | public |
| POST | `/api/shops/{slug}/products/{productId}/configurator/quote` | public |
- `PUT` body `{ enabled, schema: { steps: [...] }, version }` — `version` ไม่ตรง → 409 `CONFIGURATOR_VERSION_STALE`; ร้านไม่ใช่ CUSTOM → 400; สินค้าร้านอื่น → 404
- Schema: ≤10 step, step ละ ≤30 option; type `SINGLE` | `MULTI` (min/max) | `TEXT` (maxLength, charset `ANY|LATIN|ALNUM|THAI_LATIN`); option = `id`, `label {th,en?}`, `priceDelta` (ทศนิยม ≤2, ±1,000,000), `stock` (null = ไม่จำกัด), `available`, `mediaId` (IMAGE ของร้าน)
- Quote request `{ selections: { stepId: optionId | [optionIds] | "text" }, version }` → response `{ complete, missing[], basePrice, lines[{stepId, optionId, label, priceDelta}], total, currency, version, configHash, selections }` (selections ถูก normalize; `configHash` = SHA-256 ของ selections ที่ canonical + version). **ราคาคำนวณที่ server เท่านั้น**; ราคารวมติดลบ → `CONFIGURATOR_TOTAL_NEGATIVE`
- Error codes ใหม่ใน `ValidationMessages`: `COLLECTION_*` (9 ตัว), `CONFIGURATOR_*` (9 ตัว)

### Configurator → Cart/Order integration (งานของ Supitcha) — ✅ DONE 27 ต.ค. 2026 (V43, ดูหัวข้อถัดไปท้ายไฟล์)
ฝั่งนี้ทำแค่ schema + quote + UI (ปุ่ม "ใส่ตะกร้า" ถูก disable แสดง "เร็วๆ นี้"; stub `onConfiguredAddToCart` ใน `components/shop/ProductConfiguratorPanel.tsx` throw ไว้ ยังไม่มีใครเรียก). ไม่ได้แตะ `order/*`, cart service/controller, หน้า cart/checkout, `AuthContext`, `CartContext`. สิ่งที่ต้องทำต่อ:
1. **DB (migration ใหม่ V43+)**: เพิ่มคอลัมน์ jsonb บน cart line และ order line เช่น `configuration` (snapshot: `version`, `selections`, `lines[]` พร้อม label ณ เวลานั้น) + price breakdown (`basePrice`, `configDelta`, `unitPrice`) + `config_hash`
2. **Re-quote ที่ server ตอน add-to-cart** — เรียก `ProductConfiguratorService` (quote) อีกครั้งด้วย selections จาก client ห้ามเชื่อราคาจาก client; `complete=false` → 400
3. **Re-quote อีกครั้งตอน checkout** — ถ้า version เปลี่ยน / ตัวเลือกหมด / ราคาเปลี่ยน → แจ้งผู้ซื้อให้ยืนยันใหม่ (ใช้ `CONFIGURATOR_VERSION_STALE` / `CONFIGURATOR_SELECTION_INVALID`); option `stock` ควรตัดตอนสร้างออเดอร์
4. **Cart-line identity** = (productId, skuId, `configHash`) — สินค้าเดียวกันต่าง config ต้องเป็นคนละบรรทัด; config เดียวกันให้รวมจำนวน
5. **แสดง configuration** ในรายละเอียดออเดอร์ทั้งฝั่ง merchant และ buyer (และใน cart) จาก snapshot (ไม่ใช่จาก schema ปัจจุบัน); ข้อความ TEXT ให้แสดงเป็น plain text เท่านั้น
6. เปลี่ยน `onConfiguredAddToCart` ให้เรียก cart API จริงแล้วเอา disable ของปุ่มออก
7. **แล้วจึงเปิด flag**: `APP_FEATURES_CONFIGURATOR_ENABLED=true` (backend) + `VITE_FF_PRODUCT_CONFIGURATOR=true` (frontend)

### คลาส / ไฟล์ใหม่และที่แก้
- ใหม่ `catalog/collection/`: `ShopCollection`, `ShopCollectionProduct`, `ShopCollectionRepository`, `ShopCollectionProductRepository`, `ShopCollectionService`, `MerchantCollectionController`, `PublicCollectionController`, `CollectionDtos`, `CollectionProperties`, `CatalogShopAccess`
- ใหม่ `catalog/configurator/`: `ProductConfigurator`, `ProductConfiguratorRepository`, `ProductConfiguratorService`, `ConfiguratorSchema`, `ConfiguratorDtos`, `ConfiguratorProperties`, `MerchantConfiguratorController`, `PublicConfiguratorController`
- แก้: `ProductService` (+`publicCards`), `ValidationMessages`, `merchant/theme/ShopBlockType` (+`COLLECTIONS`), `ShopThemeBlockContent`, `application.yml`, `application-test.yml`
- Frontend: `ShopCollectionsManager` (แท็บ "คอลเลกชัน" ใน MyStorePage), `ProductConfiguratorEditor` (ใน ProductFormPage), `ProductConfiguratorPanel` (ใน ShopProductPage), `CollectionsSection`, `ShopCollectionPage`, `services/collectionsApi.ts`, `styles/shop-collections.css` (scope `.shop-themed[data-template="CUSTOM"]`), `styles/shop-collections-manager.css`, locales th/en/zh

### Tests
- ใหม่: `ShopCollectionIntegrationTests`, `ProductConfiguratorIntegrationTests`, `ProductConfiguratorFlagOffTests`, helper `CatalogExtrasTestSupport`
- `.\mvnw -o test` (27 ต.ค. 2026): **259 tests, 0 failures, 0 errors** — BUILD SUCCESS
- Mock data: `C:\Users\DELL\seed-mock\seed_charm_collections.sql` / `cleanup_charm_collections.sql` (ร้าน `seed-charm-studio`, ยังไม่ได้รัน)

## ✅ DONE: Configurator → Cart/Order (V43) — 27 ต.ค. 2026
> เขียนโดย assistant ตามที่ผู้ใช้ (supachok) อนุมัติเมื่อ 2026-10-27 ให้ทำงานส่วนที่เหลือของ Supitcha แทน — โปรดรีวิว
> Flag ยัง **ปิด** เป็นค่าเริ่มต้น: เปิดด้วย `APP_FEATURES_CONFIGURATOR_ENABLED=true` (backend, ต้อง restart) + `VITE_FF_PRODUCT_CONFIGURATOR=true` (frontend)

### Flyway V43 (`V43__configurator_cart_order.sql`) — additive/nullable เท่านั้น
- `cart_items` + `order_items`: เพิ่ม `configuration JSONB NULL`, `config_hash VARCHAR(64) NULL` (+ comment)
- `cart_items`: drop unique `uq_cart_items_cart_id_sku_id` → แทนด้วย partial unique index
  - `uq_cart_items_cart_sku_plain (cart_id, sku_id) WHERE config_hash IS NULL` (บรรทัดปกติเหมือนเดิม)
  - `uq_cart_items_cart_sku_config (cart_id, sku_id, config_hash) WHERE config_hash IS NOT NULL`
- Migration ถัดไปคือ **V44**

### API ที่เปลี่ยน (เพิ่ม field แบบ optional, client เดิมไม่กระทบ)
- `POST /api/cart/items` body เพิ่ม `configuration: { selections, version }` (optional). ไม่ส่ง = พฤติกรรมเดิม 100%
  - flag ปิด + ส่ง configuration → 404 `FEATURE_DISABLED`
  - เลือกไม่ครบ → 400 (`configuration.selections.<stepId>` / `CONFIGURATOR_SELECTION_REQUIRED`); option ไม่ถูกต้อง → 400; version เก่า → 409 `CONFIGURATOR_VERSION_STALE`; สต็อกไม่พอ → 409 เดิม
- Cart `Line` และ Order `Item` (buyer + merchant) เพิ่ม `configuration` (snapshot; ไม่ส่งออกเมื่อเป็น null)
- Snapshot: `{version, selections, lines[{stepId, stepLabel{th,en..}, optionId, label{..}, text?, priceDelta}], configDelta, currency, configHash}`

### กติกา
- Server re-quote ทุกครั้ง (`ProductConfiguratorService.cartQuote`) ไม่เชื่อราคาจาก client; ราคาบรรทัด = ราคา SKU + `configDelta`
- Cart-line identity = (cart, sku, `config_hash`); config เดียวกันรวมจำนวน, ต่าง config แยกบรรทัด; hash ของบรรทัดคงเป็นค่าตอน add แม้ re-quote ภายหลัง
- `validate`/checkout re-quote อีกครั้ง: ราคาเปลี่ยน → `PRICE_CHANGED` (flow เดิม, กด accept-prices เพื่อ re-quote + อัปเดต snapshot); config ใช้ไม่ได้แล้ว/ปิด configurator → issue `CONFIGURATION_INVALID` (ERROR) ต้องลบบรรทัด
- สร้างออเดอร์: copy snapshot + `config_hash` ไป `order_items` (แสดงจาก snapshot ไม่ใช่ schema ปัจจุบัน)
- ข้อความ TEXT/label แสดงเป็น plain text เท่านั้น (React escape)
- สต็อกตรวจที่ระดับ SKU (รวมทุกบรรทัดของ SKU เดียวกันตอน add)

### ไฟล์ที่แก้
- Backend: `cart/CartItem`, `cart/CartItemRepository` (+query IsNull / ConfigHash), `cart/CartDtos` (+`ConfigurationRequest`, `Line.configuration`), `cart/CartService` (add/validate/acceptPrices/merge/toLine/switchVariant), `catalog/configurator/ProductConfiguratorService` (+`cartQuote`, `CartQuote`), `order/OrderItem`, `order/OrderDtos` (`Item.configuration`), `order/OrderCheckoutService`, `OrderQueryService`
- Frontend: `services/api.ts` (`ConfigurationSnapshot`, `cartApi.addConfigured`), `contexts/CartContext.tsx` (+`addConfigured` เท่านั้น), `components/shop/ProductConfiguratorPanel.tsx` (ลบ stub `onConfiguredAddToCart` → prop `onAddToCart`, ปุ่มใช้งานได้เมื่อ quote ครบ), `pages/ShopProductPage.tsx` (ใช้ check เดิมของ `placeInCart`), ใหม่ `components/cart/ConfigurationSummary.tsx` + `configurationSummary.css` (class กลาง `.cart-config-summary`), แสดงใน `CartPage`, `CheckoutPage`, `MyOrderDetailPage`, `MerchantOrderDrawer`; locales th/en/zh `shop.configurator.add/adding`
- ไม่แตะ: `AuthContext`, ธีม GENERAL/FASHION/FOOD_BEVERAGE

### Tests
- ใหม่: `ConfiguredCartIntegrationTests` (ราคา/hash identity/การปฏิเสธ, PRICE_CHANGED → accept → checkout copy snapshot, configurator ถูกปิด → CONFIGURATION_INVALID), `ConfiguredCartFlagOffTests`
- ผล `.\mvnw -o test`: **263 tests, 0 failures, 0 errors** — BUILD SUCCESS (27 ต.ค. 2026 ~21:00 น. เวลาไทย); `tsc` ผ่าน, `check-ag1-css-scope` ผ่าน

### ฝากรีวิว
- ยังไม่ตัด `stock` ของ option ตอนสร้างออเดอร์ (ตัดเฉพาะ SKU)
- `validate` ตรวจสต็อกต่อบรรทัด ไม่รวมต่อ SKU (แต่ checkout reserve ตามลำดับจึงไม่ oversell)
- `merge` (guest→login) นับ "already" ต่อบรรทัด
- Label ที่เก็บเป็น snapshot ตามภาษาใน schema ตอนนั้น
