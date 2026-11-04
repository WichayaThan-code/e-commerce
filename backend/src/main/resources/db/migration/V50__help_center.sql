-- Help Center: topics, FAQs, guides + PLATFORM_HELP_READ / PLATFORM_HELP_MANAGE

CREATE TABLE help_topics (
    id              UUID PRIMARY KEY,
    slug            VARCHAR(80) NOT NULL,
    icon            VARCHAR(64) NOT NULL,
    sort_order      INT NOT NULL DEFAULT 0,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    title_th        VARCHAR(200) NOT NULL,
    title_en        VARCHAR(200),
    title_zh        VARCHAR(200),
    description_th  VARCHAR(500) NOT NULL,
    description_en  VARCHAR(500),
    description_zh  VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by      UUID,
    CONSTRAINT uq_help_topics_slug UNIQUE (slug)
);

CREATE TABLE help_faqs (
    id              UUID PRIMARY KEY,
    topic_id        UUID NOT NULL REFERENCES help_topics (id),
    sort_order      INT NOT NULL DEFAULT 0,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    audience        VARCHAR(16) NOT NULL DEFAULT 'ALL',
    question_th     VARCHAR(500) NOT NULL,
    question_en     VARCHAR(500),
    question_zh     VARCHAR(500),
    answer_th       TEXT NOT NULL,
    answer_en       TEXT,
    answer_zh       TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by      UUID,
    CONSTRAINT ck_help_faqs_audience CHECK (audience IN ('ALL', 'CUSTOMER', 'MERCHANT'))
);

CREATE INDEX ix_help_faqs_topic_id ON help_faqs (topic_id);
CREATE INDEX ix_help_faqs_active_sort ON help_faqs (active, sort_order);

CREATE TABLE help_guides (
    id              UUID PRIMARY KEY,
    slug            VARCHAR(80) NOT NULL,
    sort_order      INT NOT NULL DEFAULT 0,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    title_th        VARCHAR(200) NOT NULL,
    title_en        VARCHAR(200),
    title_zh        VARCHAR(200),
    summary_th      VARCHAR(500) NOT NULL,
    summary_en      VARCHAR(500),
    summary_zh      VARCHAR(500),
    url             VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by      UUID,
    CONSTRAINT uq_help_guides_slug UNIQUE (slug)
);

INSERT INTO auth_permissions (id, code, module, scope, description_th, doc_section, is_active, policy_version)
VALUES
    (gen_random_uuid(), 'PLATFORM_HELP_READ', 'PLATFORM', 'PLATFORM',
     'ดูและค้นหาเนื้อหาศูนย์ช่วยเหลือ (หัวข้อ คำถามที่พบบ่อย คู่มือ) ในแอดมิน',
     '§13', TRUE, '2.0'),
    (gen_random_uuid(), 'PLATFORM_HELP_MANAGE', 'PLATFORM', 'PLATFORM',
     'สร้าง แก้ไข เรียงลำดับ เปิด/ปิด และลบเนื้อหาศูนย์ช่วยเหลือ',
     '§13', TRUE, '2.0')
ON CONFLICT (code) DO UPDATE
   SET module = EXCLUDED.module, scope = EXCLUDED.scope, description_th = EXCLUDED.description_th,
       doc_section = EXCLUDED.doc_section, is_active = TRUE, policy_version = EXCLUDED.policy_version;

-- Same platform-admin role that already manages categories (PLATFORM_SETTING_UPDATE).
INSERT INTO auth_role_permissions (id, role_id, permission_id, policy_version)
SELECT gen_random_uuid(), role.id, permission.id, '2.0'
  FROM auth_roles role
  JOIN auth_permissions permission ON permission.code IN ('PLATFORM_HELP_READ', 'PLATFORM_HELP_MANAGE')
 WHERE role.code = 'PLATFORM_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

-- Seed topics (from HelpPage.tsx)
INSERT INTO help_topics (id, slug, icon, sort_order, active, title_th, title_en, title_zh, description_th, description_en, description_zh) VALUES
('a1000001-0000-4000-8000-000000000001', 'ordering', 'ShoppingCart', 1, TRUE,
 'การสั่งซื้อ', 'Orders', '下单',
 'วิธีสั่งซื้อ ยกเลิกคำสั่งซื้อ', 'How to order and cancel', '如何下单与取消订单'),
('a1000001-0000-4000-8000-000000000002', 'payment', 'CreditCard', 2, TRUE,
 'การชำระเงิน', 'Payments', '付款',
 'ช่องทางการชำระเงิน ออกใบกำกับภาษี', 'Payment methods and tax invoices', '付款方式与发票'),
('a1000001-0000-4000-8000-000000000003', 'shipping', 'Truck', 3, TRUE,
 'การจัดส่งสินค้า', 'Shipping', '配送',
 'ตรวจสอบสถานะ ระยะเวลาจัดส่ง', 'Track status and delivery time', '查物流与配送时效'),
('a1000001-0000-4000-8000-000000000004', 'returns', 'Box', 4, TRUE,
 'การคืนสินค้าและคืนเงิน', 'Returns & refunds', '退货退款',
 'เงื่อนไขการคืนสินค้า', 'Return conditions', '退货条件'),
('a1000001-0000-4000-8000-000000000005', 'merchant', 'Store', 5, TRUE,
 'สำหรับร้านค้า', 'For merchants', '商家',
 'สมัครร้านค้า จัดการสินค้า การรับเงิน', 'Apply, products, payouts', '开店、商品与收款'),
('a1000001-0000-4000-8000-000000000006', 'account', 'UserRound', 6, TRUE,
 'บัญชีผู้ใช้งาน', 'Account', '账户',
 'สมัครสมาชิก จัดการข้อมูลส่วนตัว', 'Sign up and profile', '注册与个人资料'),
('a1000001-0000-4000-8000-000000000007', 'security', 'ShieldCheck', 7, TRUE,
 'ความปลอดภัย', 'Security', '安全',
 'ความปลอดภัยของข้อมูล และการป้องกันการฉ้อโกง', 'Data security and fraud prevention', '数据安全与防欺诈'),
('a1000001-0000-4000-8000-000000000008', 'troubleshooting', 'Settings', 8, TRUE,
 'ปัญหาการใช้งาน', 'Troubleshooting', '使用问题',
 'แจ้งปัญหา และข้อเสนอแนะ', 'Report issues and feedback', '反馈问题与建议');

-- Seed FAQs
INSERT INTO help_faqs (id, topic_id, sort_order, active, audience, question_th, question_en, question_zh, answer_th, answer_en, answer_zh) VALUES
('a1000002-0000-4000-8000-000000000001', 'a1000001-0000-4000-8000-000000000001', 1, TRUE, 'ALL',
 'วิธีสั่งซื้อสินค้าใน ThaiShopFun ทำอย่างไร?',
 'How do I place an order on ThaiShopFun?',
 '如何在 ThaiShopFun 下单？',
 'เลือกสินค้า กดเพิ่มลงตะกร้า ตรวจสอบที่อยู่ เลือกวิธีชำระเงิน และยืนยันคำสั่งซื้อ',
 'Choose products, add to cart, confirm address, pick a payment method, and place the order.',
 '选择商品、加入购物车、确认地址、选择付款方式并提交订单。'),
('a1000002-0000-4000-8000-000000000002', 'a1000001-0000-4000-8000-000000000002', 2, TRUE, 'ALL',
 'มีช่องทางการชำระเงินอะไรบ้าง?',
 'Which payment methods are supported?',
 '支持哪些付款方式？',
 'รองรับ QR Payment, บัตรเครดิตหรือเดบิต, โอนเงินผ่านธนาคาร และกระเป๋าเงินดิจิทัล',
 'QR Payment, credit/debit cards, bank transfer, and e-wallets.',
 '支持二维码付款、信用卡/借记卡、银行转账和电子钱包。'),
('a1000002-0000-4000-8000-000000000003', 'a1000001-0000-4000-8000-000000000003', 3, TRUE, 'ALL',
 'ใช้เวลาจัดส่งสินค้ากี่วัน?',
 'How long does shipping take?',
 '配送需要多久？',
 'โดยทั่วไปใช้เวลา 1–3 วันทำการ ทั้งนี้ขึ้นอยู่กับร้านค้าและพื้นที่จัดส่ง',
 'Usually 1–3 business days, depending on the shop and destination.',
 '一般 1–3 个工作日，视店铺与配送地区而定。'),
('a1000002-0000-4000-8000-000000000004', 'a1000001-0000-4000-8000-000000000001', 4, TRUE, 'CUSTOMER',
 'สามารถยกเลิกคำสั่งซื้อได้หรือไม่?',
 'Can I cancel an order?',
 '可以取消订单吗？',
 'ยกเลิกได้ก่อนร้านค้าเริ่มจัดเตรียมสินค้า ตรวจสอบสถานะได้จากหน้าคำสั่งซื้อ',
 'You can cancel before the shop starts preparing the order. Check status on the orders page.',
 '商家开始备货前可取消。请在订单页查看状态。'),
('a1000002-0000-4000-8000-000000000005', 'a1000001-0000-4000-8000-000000000004', 5, TRUE, 'CUSTOMER',
 'วิธีขอคืนสินค้าและรับเงินคืน',
 'How do I request a return and refund?',
 '如何申请退货退款？',
 'เปิดคำสั่งซื้อ เลือกขอคืนสินค้า ระบุเหตุผลและแนบรูป ทีมงานจะตรวจสอบตามเงื่อนไข',
 'Open the order, choose return, add a reason and photos. We review per policy.',
 '打开订单选择退货，填写原因并上传图片，我们会按规则审核。'),
('a1000002-0000-4000-8000-000000000006', 'a1000001-0000-4000-8000-000000000005', 6, TRUE, 'MERCHANT',
 'วิธีสมัครเป็นร้านค้า',
 'How do I apply as a merchant?',
 '如何申请成为商家？',
 'ไปที่หน้าสมัครเป็นร้านค้า กรอกข้อมูลร้านและเจ้าของร้าน จากนั้นส่งเอกสารเพื่อตรวจสอบ',
 'Go to merchant signup, fill shop and owner details, then submit documents for review.',
 '前往商家注册页填写店铺与店主资料，并提交文件审核。'),
('a1000002-0000-4000-8000-000000000007', 'a1000001-0000-4000-8000-000000000002', 7, TRUE, 'ALL',
 'ต้องการออกใบกำกับภาษี ทำอย่างไร?',
 'How do I get a tax invoice?',
 '如何开具发票？',
 'กรอกข้อมูลสำหรับออกใบกำกับภาษีในขั้นตอนชำระเงิน หรือแจ้งร้านค้าผ่านรายละเอียดคำสั่งซื้อ',
 'Enter tax invoice details at checkout, or ask the shop via the order details.',
 '结账时填写开票信息，或通过订单详情联系店铺。'),
('a1000002-0000-4000-8000-000000000008', 'a1000001-0000-4000-8000-000000000008', 8, TRUE, 'ALL',
 'ติดต่อเจ้าหน้าที่ได้ช่องทางไหนบ้าง?',
 'How can I contact support?',
 '如何联系客服？',
 'ติดต่อได้ผ่านแชท LINE Official อีเมล support@thaishopfun.com หรือโทร 02-123-4567',
 'Contact us via LINE Official, email support@thaishopfun.com, or call 02-123-4567.',
 '可通过 LINE Official、邮箱 support@thaishopfun.com 或电话 02-123-4567 联系。');

-- Seed guides
INSERT INTO help_guides (id, slug, sort_order, active, title_th, title_en, title_zh, summary_th, summary_en, summary_zh, url) VALUES
('a1000003-0000-4000-8000-000000000001', 'ordering-guide', 1, TRUE,
 'คู่มือการสั่งซื้อสินค้า', 'Ordering guide', '下单指南',
 'ขั้นตอนการสั่งซื้อ ตั้งแต่เลือกสินค้า จนถึงชำระเงิน',
 'From picking products to checkout.',
 '从选购到付款的下单步骤。',
 NULL),
('a1000003-0000-4000-8000-000000000002', 'merchant-guide', 2, TRUE,
 'คู่มือสำหรับร้านค้า', 'Merchant guide', '商家指南',
 'วิธีสมัครร้านค้า ลงสินค้า และจัดการออเดอร์',
 'Apply, list products, and manage orders.',
 '开店、上架商品与管理订单。',
 NULL),
('a1000003-0000-4000-8000-000000000003', 'shipping-guide', 3, TRUE,
 'คู่มือติดตามการจัดส่ง', 'Shipping tracking guide', '物流跟踪指南',
 'วิธีตรวจสอบสถานะพัสดุ และระยะเวลาการจัดส่ง',
 'Track parcels and expected delivery times.',
 '查询包裹状态与预计送达时间。',
 NULL);
