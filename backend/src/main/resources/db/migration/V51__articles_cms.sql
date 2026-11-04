-- Articles CMS + help FAQ feedback metrics

-- blog_categories extensions
ALTER TABLE blog_categories
    ADD COLUMN IF NOT EXISTS name_zh VARCHAR(150),
    ADD COLUMN IF NOT EXISTS description_th VARCHAR(500),
    ADD COLUMN IF NOT EXISTS description_en VARCHAR(500),
    ADD COLUMN IF NOT EXISTS description_zh VARCHAR(500),
    ADD COLUMN IF NOT EXISTS sort_order INT NOT NULL DEFAULT 0;

-- blog_posts extensions (title/excerpt/body_md remain Thai)
ALTER TABLE blog_posts
    ADD COLUMN IF NOT EXISTS title_en VARCHAR(255),
    ADD COLUMN IF NOT EXISTS title_zh VARCHAR(255),
    ADD COLUMN IF NOT EXISTS excerpt_en VARCHAR(500),
    ADD COLUMN IF NOT EXISTS excerpt_zh VARCHAR(500),
    ADD COLUMN IF NOT EXISTS body_md_en TEXT,
    ADD COLUMN IF NOT EXISTS body_md_zh TEXT,
    ADD COLUMN IF NOT EXISTS audience VARCHAR(16) NOT NULL DEFAULT 'ALL',
    ADD COLUMN IF NOT EXISTS is_featured BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS reading_minutes INT NOT NULL DEFAULT 3,
    ADD COLUMN IF NOT EXISTS tags TEXT[] NOT NULL DEFAULT '{}',
    ADD COLUMN IF NOT EXISTS meta_title VARCHAR(120),
    ADD COLUMN IF NOT EXISTS meta_description VARCHAR(200),
    ADD COLUMN IF NOT EXISTS related_shop_id BIGINT,
    ADD COLUMN IF NOT EXISTS related_product_ids UUID[] NOT NULL DEFAULT '{}';

ALTER TABLE blog_posts DROP CONSTRAINT IF EXISTS ck_blog_posts_audience;
ALTER TABLE blog_posts
    ADD CONSTRAINT ck_blog_posts_audience CHECK (audience IN ('ALL', 'CUSTOMER', 'MERCHANT'));

CREATE INDEX IF NOT EXISTS ix_blog_posts_published
    ON blog_posts (status, published_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS ix_blog_posts_featured
    ON blog_posts (is_featured, published_at DESC)
    WHERE deleted_at IS NULL AND status = 'PUBLISHED';

-- help FAQ helpful counters
ALTER TABLE help_faqs
    ADD COLUMN IF NOT EXISTS helpful_yes INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS helpful_no INT NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS last_reviewed_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS help_feedback (
    id          UUID PRIMARY KEY,
    faq_id      UUID NOT NULL REFERENCES help_faqs (id) ON DELETE CASCADE,
    helpful     BOOLEAN NOT NULL,
    user_id     UUID,
    session_key VARCHAR(64),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_help_feedback_user_faq
    ON help_feedback (faq_id, user_id)
    WHERE user_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_help_feedback_session_faq
    ON help_feedback (faq_id, session_key)
    WHERE session_key IS NOT NULL AND user_id IS NULL;

CREATE INDEX IF NOT EXISTS ix_help_feedback_faq_id ON help_feedback (faq_id);

-- Permissions (same role set as PLATFORM_HELP_*)
INSERT INTO auth_permissions (id, code, module, scope, description_th, doc_section, is_active, policy_version)
VALUES
    (gen_random_uuid(), 'PLATFORM_CONTENT_READ', 'PLATFORM', 'PLATFORM',
     'ดูรายการและรายละเอียดบทความ/หมวดบทความในแอดมิน',
     '§13', TRUE, '2.0'),
    (gen_random_uuid(), 'PLATFORM_CONTENT_MANAGE', 'PLATFORM', 'PLATFORM',
     'สร้าง แก้ไข เผยแพร่ จัดลำดับ และลบบทความ/หมวดบทความ',
     '§13', TRUE, '2.0')
ON CONFLICT (code) DO UPDATE
   SET module = EXCLUDED.module, scope = EXCLUDED.scope, description_th = EXCLUDED.description_th,
       doc_section = EXCLUDED.doc_section, is_active = TRUE, policy_version = EXCLUDED.policy_version;

INSERT INTO auth_role_permissions (id, role_id, permission_id, policy_version)
SELECT gen_random_uuid(), role.id, permission.id, '2.0'
  FROM auth_roles role
  JOIN auth_permissions permission ON permission.code IN ('PLATFORM_CONTENT_READ', 'PLATFORM_CONTENT_MANAGE')
 WHERE role.code = 'PLATFORM_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Seed article categories (5)
INSERT INTO blog_categories (id, slug, name_th, name_en, name_zh, description_th, description_en, description_zh, sort_order, is_active)
VALUES
('b1000001-0000-4000-8000-000000000001', 'shopping-tips', 'เคล็ดลับช้อปปิ้ง', 'Shopping tips', '购物技巧',
 'เทคนิคเลือกซื้อและช้อปอย่างคุ้มค่า', 'Tips for smarter shopping', '精明购物技巧', 1, TRUE),
('b1000001-0000-4000-8000-000000000002', 'product-reviews', 'รีวิวสินค้า', 'Product reviews', '商品评测',
 'รีวิวสินค้าและประสบการณ์ใช้งาน', 'Product reviews and experiences', '商品与使用体验评测', 2, TRUE),
('b1000001-0000-4000-8000-000000000003', 'thai-brands', 'สินค้าไทย/แบรนด์ไทย', 'Thai brands', '泰国品牌',
 'เรื่องราวและสินค้าจากแบรนด์ไทย', 'Stories and products from Thai brands', '泰国品牌故事与商品', 3, TRUE),
('b1000001-0000-4000-8000-000000000004', 'promotions-news', 'ข่าวโปรโมชั่น', 'Promotion news', '促销资讯',
 'ข่าวสารแคมเปญและโปรโมชั่น', 'Campaigns and promotions', '活动与促销资讯', 4, TRUE),
('b1000001-0000-4000-8000-000000000005', 'seller-guides', 'คู่มือผู้ขาย', 'Seller guides', '卖家指南',
 'คู่มือและเทคนิคสำหรับร้านค้า', 'Guides and tips for merchants', '商家指南与技巧', 5, TRUE)
ON CONFLICT (slug) DO UPDATE
   SET name_th = EXCLUDED.name_th, name_en = EXCLUDED.name_en, name_zh = EXCLUDED.name_zh,
       description_th = EXCLUDED.description_th, description_en = EXCLUDED.description_en,
       description_zh = EXCLUDED.description_zh, sort_order = EXCLUDED.sort_order, is_active = TRUE;

-- Seed published articles from BlogPage.tsx content
INSERT INTO blog_posts (
    id, category_id, slug, title, excerpt, body_md,
    title_en, excerpt_en, body_md_en,
    status, audience, is_featured, reading_minutes, tags,
    published_at, created_date_time, modified_date_time
) VALUES
('b1000002-0000-4000-8000-000000000001', 'b1000001-0000-4000-8000-000000000005', 'start-selling-online-2026',
 'เริ่มต้นขายของออนไลน์อย่างไรให้ปังในปี 2026',
 'รวมขั้นตอนการเริ่มต้น เทคนิคการเลือกสินค้า และช่องทางการขาย สำหรับมือใหม่',
 E'## เริ่มต้นอย่างไร\n\nเลือกสินค้าที่คุณเข้าใจ ตั้งค่าร้านให้ชัดเจน และทดลองขายในช่องทางหลักก่อนขยาย\n\n## เลือกสินค้า\n\nเริ่มจากสินค้าที่จัดส่งง่าย มีมาร์จินพอ และตอบโจทย์ลูกค้าจริง\n\n## ช่องทางขาย\n\nใช้หน้าร้านบน ThaiShopFun เป็นฐาน แล้วต่อยอดด้วยโซเชียลและไลฟ์สด',
 'How to start selling online in 2026',
 'Steps for beginners: pick products, set up your shop, and choose sales channels.',
 E'## Getting started\n\nPick products you understand, set up a clear shop, and start on one main channel.\n\n## Product picks\n\nChoose items that ship easily, have enough margin, and solve a real need.\n\n## Channels\n\nUse your ThaiShopFun storefront as the base, then grow with social and live selling.',
 'PUBLISHED', 'ALL', TRUE, 6, ARRAY['ขายออนไลน์','มือใหม่','ร้านค้า'],
 TIMESTAMPTZ '2026-09-12 10:00:00+07', NOW(), NOW()),

('b1000002-0000-4000-8000-000000000002', 'b1000001-0000-4000-8000-000000000001', 'product-photo-tips-phone',
 '5 เทคนิคถ่ายภาพสินค้าด้วยมือถือให้ดูน่าสนใจและขายได้จริง',
 'เทคนิคแสง มุมกล้อง และพื้นหลัง ที่ช่วยให้รูปสินค้าดูน่าซื้อมากขึ้น',
 E'## แสงธรรมชาติ\n\nถ่ายใกล้หน้าต่างในช่วงกลางวันและหลีกเลี่ยงแฟลชโดยตรง\n\n## มุมและสัดส่วน\n\nถ่ายหลายมุม ทั้งภาพรวมและรายละเอียดเนื้อวัสดุ\n\n## พื้นหลังสะอาด\n\nใช้พื้นเรียบสีกลางเพื่อให้สินค้าเด่น',
 '5 phone tips for product photos that sell',
 'Lighting, angles, and backgrounds that make products look more appealing.',
 E'## Natural light\n\nShoot near a window in daylight and avoid direct flash.\n\n## Angles\n\nCapture overview and detail shots of texture.\n\n## Clean backdrop\n\nUse a simple neutral background so the product stands out.',
 'PUBLISHED', 'ALL', FALSE, 4, ARRAY['ถ่ายภาพ','สินค้า','เคล็ดลับ'],
 TIMESTAMPTZ '2026-09-10 10:00:00+07', NOW(), NOW()),

('b1000002-0000-4000-8000-000000000003', 'b1000001-0000-4000-8000-000000000005', 'social-media-sales-growth',
 'ใช้โซเชียลมีเดียอย่างไรให้ยอดขายโตแบบก้าวกระโดด',
 'แนวทางโพสต์คอนเทนต์ ไลฟ์สด และชวนลูกค้ากลับไปที่หน้าร้าน',
 E'## คอนเทนต์สม่ำเสมอ\n\nวางปฏิทินโพสต์สินค้าจริงพร้อมประโยชน์ชัดเจน\n\n## ไลฟ์ขาย\n\nสาธิตการใช้งานและตอบคำถามสด\n\n## ลิงก์กลับร้าน\n\nพาผู้ติดตามมาที่หน้าร้านบน ThaiShopFun เพื่อปิดการขาย',
 'Grow sales with social media',
 'Content, live selling, and driving traffic back to your shop.',
 E'## Consistent content\n\nPlan posts that show real products and clear benefits.\n\n## Live selling\n\nDemo products and answer questions live.\n\n## Link back\n\nSend followers to your ThaiShopFun storefront to convert.',
 'PUBLISHED', 'MERCHANT', FALSE, 5, ARRAY['โซเชียล','การตลาด','ยอดขาย'],
 TIMESTAMPTZ '2026-09-08 10:00:00+07', NOW(), NOW()),

('b1000002-0000-4000-8000-000000000004', 'b1000001-0000-4000-8000-000000000003', 'khaotan-brand-story',
 'จากครัวเล็ก ๆ สู่แบรนด์ข้าวแต๋นที่ดังไกลทั่วไทย',
 'เรื่องราวแบรนด์ไทยที่เริ่มจากสูตรครอบครัวสู่ตลาดออนไลน์',
 E'## จุดเริ่มต้น\n\nสูตรจากครัวบ้านถูกพัฒนาเป็นสินค้าคุณภาพคงที่\n\n## ขยายตลาด\n\nขายผ่านหน้าร้านออนไลน์และงานแสดงสินค้าท้องถิ่น\n\n## บทเรียน\n\nรักษาคุณภาพและเล่าเรื่องแบรนด์อย่างจริงใจ',
 'From a small kitchen to a nationwide khao tan brand',
 'A Thai brand story from a family recipe to online markets.',
 E'## Beginnings\n\nA home recipe became a consistent quality product.\n\n## Growing reach\n\nSales through online shops and local fairs.\n\n## Lessons\n\nKeep quality high and tell an honest brand story.',
 'PUBLISHED', 'ALL', TRUE, 5, ARRAY['แบรนด์ไทย','เรื่องราว','อาหาร'],
 TIMESTAMPTZ '2026-09-06 10:00:00+07', NOW(), NOW()),

('b1000002-0000-4000-8000-000000000005', 'b1000001-0000-4000-8000-000000000001', 'thai-gift-ideas',
 '10 ของขวัญไทย ถูกใจทั้งผู้ให้และผู้รับ ในทุกโอกาส',
 'ไอเดียของขวัญจากงานฝีมือและแบรนด์ท้องถิ่นที่มอบได้หลายเทศกาล',
 E'## ของกินและของใช้\n\nเลือกของที่เก็บได้นาน ส่งง่าย และมีเรื่องราวท้องถิ่น\n\n## แพ็กของขวัญ\n\nจัดชุดตามงบและโอกาส เช่น ปีใหม่ วันเกิด หรือของฝาก\n\n## สนับสนุนแบรนด์ไทย\n\nซื้อจากร้านบน ThaiShopFun เพื่อส่งตรงถึงผู้รับ',
 '10 Thai gift ideas for every occasion',
 'Gift ideas from local crafts and brands for many occasions.',
 E'## Food and goods\n\nPick items that keep well, ship easily, and tell a local story.\n\n## Gift sets\n\nBundle by budget and occasion such as New Year or birthdays.\n\n## Support Thai brands\n\nShop on ThaiShopFun and ship straight to the recipient.',
 'PUBLISHED', 'CUSTOMER', FALSE, 4, ARRAY['ของขวัญ','สินค้าไทย','ไลฟ์สไตล์'],
 TIMESTAMPTZ '2026-09-05 10:00:00+07', NOW(), NOW()),

('b1000002-0000-4000-8000-000000000006', 'b1000001-0000-4000-8000-000000000004', 'thaishopfun-fair-2026',
 'ThaiShopFun Fair 2026 รวมของดีไทย ไว้ที่เดียว',
 'ไฮไลต์งานรวมร้านค้าไทย โปรโมชั่น และกิจกรรมสำหรับผู้ซื้อและผู้ขาย',
 E'## ไฮไลต์งาน\n\nพบร้านค้าจากทั่วประเทศ พร้อมดีลพิเศษเฉพาะงาน\n\n## สำหรับผู้ซื้อ\n\nสำรวจสินค้าใหม่และรับคูปองภายในงาน\n\n## สำหรับผู้ขาย\n\nเรียนรู้เทคนิคการขายและขยายเครือข่าย',
 'ThaiShopFun Fair 2026 brings Thai goods together',
 'Highlights for shoppers and sellers: deals, discovery, and networking.',
 E'## Fair highlights\n\nMeet shops from across Thailand with event-only deals.\n\n## For shoppers\n\nDiscover new products and collect on-site coupons.\n\n## For sellers\n\nLearn sales tips and grow your network.',
 'PUBLISHED', 'ALL', TRUE, 3, ARRAY['อีเวนต์','โปรโมชั่น','ThaiShopFun'],
 TIMESTAMPTZ '2026-09-01 10:00:00+07', NOW(), NOW())
ON CONFLICT (slug) DO NOTHING;
