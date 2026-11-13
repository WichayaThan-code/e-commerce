#!/usr/bin/env python3
"""Seed ~500 OTOP / Thai-craft example products (+ images) for shop BShop."""

from __future__ import annotations

import hashlib
import os
import random
import re
import sys
import uuid
from pathlib import Path

import psycopg2
from PIL import Image, ImageDraw, ImageFont

SHOP_SLUG = "bshop"
TARGET = 500
SLUG_PREFIX = "ex-otop-"
UPLOADS = Path(__file__).resolve().parents[1] / "uploads"

DB = dict(
    host=os.environ.get("DB_HOST", "localhost"),
    port=int(os.environ.get("DB_PORT", "5432")),
    dbname=os.environ.get("DB_NAME", "TSF_DB"),
    user=os.environ.get("DB_USERNAME", "postgres"),
    password=os.environ.get("DB_PASSWORD", "1234"),
)

# Mix of OTOP / Thai handicraft product bases (Thai + short English).
BASES = [
    ("ผ้าไหมมัดหมี่", "Mudmee Silk Scarf", "ผ้าไหมทอมือจากหมู่บ้านทอผ้า"),
    ("ผ้าขาวม้าทอมือ", "Handwoven Pha Khao Ma", "ผ้าขาวม้าลายดั้งเดิม ใช้คลุมไหล่หรือเป็นของฝาก"),
    ("ผลิตภัณฑ์จากกะลา", "Coconut Shell Craft", "งานแฮนด์เมดจากกะลามะพร้าวขัดเงา"),
    ("เครื่องปั้นดินเผาด่านเกวียน", "Dan Kwian Pottery", "โอ่ง/แจกันดินเผาเผาโบราณ"),
    ("บาตรพระทองเหลือง", "Brass Monk Bowl", "งานตีมือทองเหลืองลายไทย"),
    ("มีดอรัญญิก", "Aranyik Knife", "มีดทำครัวเหล็กกล้าตีมือ"),
    ("ตะกร้าไม้ไผ่สาน", "Bamboo Basket", "ตะกร้าสานมือลายขัด"),
    ("โคมไฟไม้ไผ่", "Bamboo Lantern", "โคมไฟแขวนสไตล์ล้านนา"),
    ("สบู่สมุนไพร OTOP", "Herbal OTOP Soap", "สบู่ก้อนสมุนไพรธรรมชาติ"),
    ("น้ำมันนวดสมุนไพร", "Herbal Massage Oil", "น้ำมันนวดสูตรพื้นบ้าน"),
    ("ชาสมุนไพรใบหม่อน", "Mulberry Herbal Tea", "ชาใบหม่อนอบแห้งแพ็กซอง"),
    ("กาแฟดอยอ่างขาง", "Doi Angkhang Coffee", "เมล็ดกาแฟคั่วกลางจากดอย"),
    ("น้ำผึ้งป่าธรรมชาติ", "Wild Forest Honey", "น้ำผึ้งจากรังธรรมชาติ"),
    ("กล้วยอบน้ำผึ้ง", "Honey Banana Chips", "กล้วยอบกรอบรสน้ำผึ้ง"),
    ("มะม่วงกวนโบราณ", "Dried Mango Paste", "มะม่วงกวนสูตรคุณยาย"),
    ("ทุเรียนทอดกรอบ", "Crispy Durian Chips", "ทุเรียนหมอนทองทอดกรอบ"),
    ("ข้าวเกรียบกุ้ง", "Shrimp Crackers", "ข้าวเกรียบกุ้งสมุทรสงคราม"),
    ("ปลา Capong แดดเดียว", "Sun-Dried Fish", "ปลาแดดเดียวเค็มน้อย"),
    ("น้ำปลาแท้พื้นบ้าน", "Artisan Fish Sauce", "น้ำปลาหมักถังไม้"),
    ("พริกแกงเผ็ดโบราณ", "Red Curry Paste", "พริกแกงโขลกมือ"),
    ("เครื่องเงินสุโขทัย", "Sukhothai Silverware", "เครื่องประดับเงินฉลุลาย"),
    ("ต่างหูเงินล้านนา", "Lanna Silver Earrings", "ต่างหูเงินตอกลายดอกไม้"),
    ("สร้อยคอลูกปัดชาวเขา", "Hill Tribe Bead Necklace", "ลูกปัดสีสันชาวเขา"),
    ("กระเป๋าย่ามชาวเขา", "Hill Tribe Shoulder Bag", "ย่ามปักมือลายชาติพันธุ์"),
    ("หมอนขิดอีสาน", "Isan Khit Pillow", "หมอนขิดทอมือนุ่ม"),
    ("ผ้าคลุมไหล่ฝ้ายทอ", "Cotton Shawl", "ผ้าคลุมไหล่ฝ้ายย้อมธรรมชาติ"),
    ("เสื่อกกทอมือ", "Handwoven Reed Mat", "เสื่อกกลายตาราง"),
    ("พัดใบลาน", "Palm Leaf Fan", "พัดใบลานสานมือ"),
    ("ตุ๊กตาชาววัง", "Thai Court Doll", "ตุ๊กตาชุดไทยประดิษฐ์"),
    ("โมบายปลาตะเพียน", "Fish Mobile Craft", "โมบายปลาตะเพียนใบลาน"),
    ("แจกันเซรามิกเบญจรงค์", "Benjarong Vase", "เซรามิกลายเบญจรงค์ห้าสี"),
    ("ชามเบญจรงค์", "Benjarong Bowl", "ชามลายไทยห้าสี"),
    ("ถาดไม้สักแกะสลัก", "Carved Teak Tray", "ถาดไม้สักลายดอกบัว"),
    ("ที่รองแก้วไม้สัก", "Teak Coaster Set", "ชุดที่รองแก้วไม้สัก 6 ชิ้น"),
    ("กล่องใส่เครื่องประดับไม้", "Wooden Jewelry Box", "กล่องไม้สลักลายไทย"),
    ("เทียนหอมใบเตย", "Pandan Scented Candle", "เทียนหอมกลิ่นใบเตย"),
    ("ธูปหอมสมุนไพร", "Herbal Incense", "ธูปหอมสูตรสมุนไพร"),
    ("สเปรย์ปรับอากาศตะไคร้", "Lemongrass Room Spray", "สเปรย์ตะไคร้หอมสดชื่น"),
    ("ครีมบำรุงผิวขมิ้น", "Turmeric Face Cream", "ครีมขมิ้นชันบำรุงผิว"),
    ("สครับกาแฟดอย", "Coffee Body Scrub", "สครับกาแฟขัดผิว"),
    ("ถุงเท้าทอมือ", "Hand-knit Socks", "ถุงเท้าไหมพรมทอมือ"),
    ("ผ้าพันคอฝ้ายย้อมคราม", "Indigo Cotton Scarf", "ฝ้ายย้อมครามธรรมชาติ"),
    ("เสื้อม่อฮ่อม", "Mohom Shirt", "เสื้อม่อฮ่อมฝ้ายทอ"),
    ("กางเกงเลแบบพื้นเมือง", "Ethnic Fisherman Pants", "กางเกงเลผ้าฝ้าย"),
    ("ผ้าซิ่นไหมแพรวา", "Phaewa Silk Sin", "ผ้าซิ่นไหมแพรวาทอมือ"),
    ("เข็มขัดหนังฉลุลาย", "Tooled Leather Belt", "เข็มขัดหนังแท้ฉลุลายไทย"),
    ("กระเป๋าสตางค์หนังแกะ", "Carved Leather Wallet", "กระเป๋าหนังลายไทย"),
    ("กุญแจไม้หอมกฤษณา", "Agarwood Keychain", "พวงกุญแจไม้หอม"),
    ("ลูกประคบสมุนไพร", "Herbal Compress Ball", "ลูกประคบนวดแผนไทย"),
    ("ยาดมสมุนไพร OTOP", "OTOP Herbal Inhaler", "ยาดมสูตรสมุนไพร"),
    ("แชมพูอัญชัน", "Butterfly Pea Shampoo", "แชมพูอัญชันบำรุงผม"),
    ("สบู่เปลือกมังคุด", "Mangosteen Peel Soap", "สบู่เปลือกมังคุดก้อน"),
    ("แยมส้มสายน้ำผึ้ง", "Honey Orange Jam", "แยมส้มสายน้ำผึ้งโฮมเมด"),
    ("คุกกี้ข้าวไรซ์เบอร์รี่", "Riceberry Cookies", "คุกกี้ข้าวไรซ์เบอร์รี่"),
    ("ทองม้วนใบเตย", "Pandan Crisp Rolls", "ทองม้วนกรอบกลิ่นใบเตย"),
    ("ขนมจากินไทย", "Thai Kanom Jak", "ขนมจากใบจากห่อ"),
    ("ข้าวแต๋นน้ำแตงโม", "Watermelon Rice Cracker", "ข้าวแต๋นน้ำแตงโมอบกรอบ"),
    ("มะพร้าวแก้ว", "Crispy Coconut Candy", "มะพร้าวแก้วหวานน้อย"),
    ("ถั่วตัดสูตรโบราณ", "Peanut Brittle", "ถั่วตัดกรอบหวานมัน"),
    ("งาตัดดำขาว", "Sesame Brittle", "งาตัดดำขาวสูตรคุณย่า"),
]

PROVINCES = [
    "เชียงใหม่", "เชียงราย", "ลำพูน", "ลำปาง", "แพร่", "น่าน", "พะเยา", "แม่ฮ่องสอน",
    "ขอนแก่น", "อุดรธานี", "อุบลราชธานี", "นครราชสีมา", "สุรินทร์", "บุรีรัมย์", "มหาสารคาม",
    "กาญจนบุรี", "ราชบุรี", "เพชรบุรี", "ประจวบคีรีขันธ์", "สุพรรณบุรี",
    "สุโขทัย", "พิษณุโลก", "นครสวรรค์", "อุทัยธานี",
    "สงขลา", "นครศรีธรรมราช", "สุราษฎร์ธานี", "พังงา", "กระบี่", "ตรัง",
    "จันทบุรี", "ตราด", "ระยอง", "ชลบุรี",
]

PALETTES = [
    ((180, 60, 40), (255, 236, 210)),
    ((40, 110, 90), (230, 245, 238)),
    ((40, 70, 140), (232, 238, 255)),
    ((150, 90, 30), (255, 244, 220)),
    ((90, 50, 110), (245, 235, 255)),
    ((50, 50, 50), (245, 245, 240)),
    ((20, 120, 140), (225, 245, 248)),
    ((140, 40, 70), (255, 235, 240)),
]


def slugify(text: str, n: int) -> str:
    asciiish = re.sub(r"[^a-z0-9]+", "-", text.lower())
    asciiish = re.sub(r"-+", "-", asciiish).strip("-") or "item"
    return f"{SLUG_PREFIX}{n:03d}-{asciiish[:40]}"


def make_image(path: Path, title: str, subtitle: str, seed: int) -> None:
    rng = random.Random(seed)
    fg, bg = PALETTES[seed % len(PALETTES)]
    img = Image.new("RGB", (800, 800), bg)
    draw = ImageDraw.Draw(img)
    # soft circles
    for _ in range(8):
        x, y = rng.randint(-50, 750), rng.randint(-50, 750)
        r = rng.randint(40, 180)
        c = tuple(min(255, max(0, v + rng.randint(-30, 30))) for v in fg)
        draw.ellipse((x, y, x + r, y + r), fill=(*c, ) if False else c)
    band = Image.new("RGB", (800, 220), (255, 255, 255))
    img.paste(band, (0, 540))
    draw = ImageDraw.Draw(img)
    try:
        font_lg = ImageFont.truetype("C:/Windows/Fonts/tahoma.ttf", 36)
        font_sm = ImageFont.truetype("C:/Windows/Fonts/tahoma.ttf", 22)
    except OSError:
        font_lg = ImageFont.load_default()
        font_sm = font_lg
    draw.rectangle((40, 40, 760, 100), fill=fg)
    draw.text((56, 55), "ThaiShopFun · Example", fill=(255, 255, 255), font=font_sm)
    draw.text((48, 560), title[:42], fill=(30, 30, 30), font=font_lg)
    draw.text((48, 620), subtitle[:60], fill=(80, 80, 80), font=font_sm)
    draw.text((48, 680), "OTOP / งานฝีมือไทย", fill=fg, font=font_sm)
    path.parent.mkdir(parents=True, exist_ok=True)
    img.save(path, format="JPEG", quality=85)


def build_catalog(n: int) -> list[dict]:
    items = []
    i = 1
    while len(items) < n:
        base_th, base_en, desc = BASES[(i - 1) % len(BASES)]
        province = PROVINCES[(i - 1) % len(PROVINCES)]
        variant = ((i - 1) // len(BASES)) + 1
        name_th = f"{base_th} จาก{province}" if variant == 1 else f"{base_th} จาก{province} รุ่น {variant}"
        name_en = f"{base_en} ({province}) #{variant}"
        cat = "otop" if i % 2 else "craft"
        price = round(49 + ((i * 17) % 850) + (i % 7) * 0.5, 2)
        stock = 20 + (i * 3) % 180
        items.append(
            dict(
                n=i,
                name_th=name_th[:255],
                name_en=name_en[:255],
                desc_th=f"{desc} จังหวัด{province} — ข้อมูลตัวอย่างสำหรับทดสอบร้าน",
                desc_en=f"{desc} from {province}. Example catalog seed.",
                brand="BShop OTOP",
                category=cat,
                price=price,
                compare=round(price * 1.2, 2),
                weight=100 + (i % 20) * 25,
                stock=stock,
                safety=max(5, stock // 10),
            )
        )
        i += 1
    return items


def main() -> int:
    conn = psycopg2.connect(**DB)
    conn.autocommit = False
    cur = conn.cursor()

    cur.execute(
        "SELECT id, shop_no, plan_code FROM shops WHERE lower(slug)=%s OR lower(name)=%s LIMIT 1",
        (SHOP_SLUG, SHOP_SLUG),
    )
    row = cur.fetchone()
    if not row:
        print("BShop not found", file=sys.stderr)
        return 1
    shop_id, shop_no, plan = row
    print(f"Shop BShop id={shop_id} shop_no={shop_no} plan={plan}")

    # Unlimited publish quota for local example catalog
    cur.execute("UPDATE shops SET plan_code='PRO', modified_date_time=now() WHERE id=%s", (shop_id,))

    cur.execute(
        "SELECT slug, id FROM hub_categories WHERE slug IN ('otop-products','thai-crafts-goods')"
    )
    cats = {slug: cid for slug, cid in cur.fetchall()}
    if "otop-products" not in cats or "thai-crafts-goods" not in cats:
        print("Missing hub categories", cats, file=sys.stderr)
        return 1

    cur.execute(
        "SELECT count(*) FROM products WHERE shop_id=%s AND slug LIKE %s AND deleted_at IS NULL",
        (shop_id, SLUG_PREFIX + "%"),
    )
    existing = cur.fetchone()[0]
    if existing:
        print(f"Removing previous example batch ({existing} products)…")
        cur.execute(
            "SELECT id FROM products WHERE shop_id=%s AND slug LIKE %s",
            (shop_id, SLUG_PREFIX + "%"),
        )
        old_ids = [r[0] for r in cur.fetchall()]
        for pid in old_ids:
            folder = UPLOADS / str(pid)
            if folder.exists():
                for f in folder.glob("*"):
                    try:
                        f.unlink()
                    except OSError:
                        pass
                try:
                    folder.rmdir()
                except OSError:
                    pass
        cur.execute(
            "DELETE FROM products WHERE shop_id=%s AND slug LIKE %s",
            (shop_id, SLUG_PREFIX + "%"),
        )

    catalog = build_catalog(TARGET)
    print(f"Seeding {len(catalog)} products…")

    for item in catalog:
        product_id = uuid.uuid4()
        sku_id = uuid.uuid4()
        image_id = uuid.uuid4()
        slug = f"{SLUG_PREFIX}{item['n']:03d}"
        cat_id = cats["otop-products"] if item["category"] == "otop" else cats["thai-crafts-goods"]
        token = hashlib.sha1(str(product_id).encode()).hexdigest()[:10].upper()
        rel_dir = UPLOADS / str(product_id)
        filename = f"Doc_(PRODUCT)_{token}.jpg"
        abs_path = rel_dir / filename
        make_image(abs_path, item["name_th"], item["name_en"], item["n"])

        cur.execute(
            """
            INSERT INTO products (
              id, shop_id, category_id, slug, name, name_th, name_en,
              description, description_th, description_en,
              status, product_type, brand, base_price, compare_at_price,
              currency, weight_grams, option_schema, published_at,
              created_date_time, modified_date_time
            ) VALUES (
              %s,%s,%s,%s,%s,%s,%s,
              %s,%s,%s,
              'PUBLISHED','SIMPLE',%s,%s,%s,
              'THB',%s,'[]'::jsonb, now(),
              now(), now()
            )
            """,
            (
                str(product_id),
                str(shop_id),
                str(cat_id),
                slug,
                item["name_th"],
                item["name_th"],
                item["name_en"],
                item["desc_th"],
                item["desc_th"],
                item["desc_en"],
                item["brand"],
                item["price"],
                item["compare"],
                item["weight"],
            ),
        )

        cur.execute(
            """
            INSERT INTO product_skus (
              id, product_id, shop_id, sku_code, option_label, option_values,
              price, compare_at_price, currency, weight_grams, is_active,
              created_date_time, modified_date_time
            ) VALUES (
              %s,%s,%s,%s,%s,'{}'::jsonb,
              %s,%s,'THB',%s,true,
              now(), now()
            )
            """,
            (
                str(sku_id),
                str(product_id),
                str(shop_id),
                f"EX-{item['n']:04d}",
                "มาตรฐาน",
                item["price"],
                item["compare"],
                item["weight"],
            ),
        )

        cur.execute(
            """
            INSERT INTO inventory_levels (
              id, shop_id, sku_id, on_hand, reserved_qty, available,
              low_stock_threshold, safety_stock, version,
              created_date_time, modified_date_time
            ) VALUES (
              %s,%s,%s,%s,0,%s,%s,%s,0, now(), now()
            )
            """,
            (
                str(uuid.uuid4()),
                str(shop_id),
                str(sku_id),
                item["stock"],
                item["stock"],
                item["safety"],
                item["safety"],
            ),
        )

        cur.execute(
            """
            INSERT INTO product_images (
              id, product_id, storage_path, sort_order, is_primary, alt_text,
              created_date_time, modified_date_time
            ) VALUES (
              %s,%s,%s,0,true,%s, now(), now()
            )
            """,
            (str(image_id), str(product_id), str(abs_path), item["name_th"][:255]),
        )

        if item["n"] % 50 == 0:
            conn.commit()
            print(f"  …{item['n']}/{TARGET}")

    conn.commit()
    cur.execute(
        """
        SELECT status, count(*)
        FROM products
        WHERE shop_id=%s AND deleted_at IS NULL
        GROUP BY status ORDER BY status
        """,
        (str(shop_id),),
    )
    print("BShop product counts:", cur.fetchall())
    cur.execute(
        "SELECT count(*) FROM products WHERE shop_id=%s AND slug LIKE %s",
        (str(shop_id), SLUG_PREFIX + "%"),
    )
    print("Example seeded:", cur.fetchone()[0])
    cur.close()
    conn.close()
    print("Done. Plan set to PRO so publish quota allows the full catalog.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
