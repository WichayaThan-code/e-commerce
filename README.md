# ThaiShopFun

Marketplace สำหรับสินค้าไทย ใช้ Vite + React + TypeScript, Spring Boot และ PostgreSQL

## แบ่งงานตามโฟลเดอร์

| โฟลเดอร์ | งาน | จุดเริ่มต้น |
| --- | --- | --- |
| `frontend/` | งาน UX/UI ของ pond และหน้าเว็บ | [คู่มือ frontend](frontend/README.md) |
| `backend/` | API, ระบบสมาชิก, กฎธุรกิจ และฐานข้อมูล | [คู่มือ backend](backend/README.md) |
| `docs/` | รูปต้นแบบหน้าจอ แบรนด์ และแผนภาพการทำงาน | `docs/design/`, `docs/flows/` |

```text
ThaiShopFun/
├── frontend/
│   ├── src/
│   │   ├── pages/          หน้าเว็บ
│   │   ├── styles/         CSS
│   │   ├── assets/images/  รูปที่ import ในโค้ด
│   │   ├── contexts/       สถานะผู้ใช้ร่วมกัน
│   │   ├── services/       การเรียก API
│   │   ├── App.tsx         การเลือกหน้าเว็บ
│   │   └── main.tsx        จุดเริ่มต้น React
│   ├── public/assets/images/ รูปที่เรียกผ่าน URL
│   ├── package.json        คำสั่ง npm และ dependencies
│   └── .env.example        ตัวอย่างที่อยู่ API
├── backend/
│   ├── src/main/java/      โค้ด Java และ API
│   ├── src/main/resources/ การตั้งค่าและ migration ฐานข้อมูล
│   ├── src/test/           การทดสอบ backend
│   └── pom.xml             Maven dependencies
└── docs/
    ├── design/             screens, branding, variants
    └── flows/              ขั้นตอนการทำงาน
```

## pond: รันเพื่อแก้ UX/UI

เริ่มจากโฟลเดอร์ `ThaiShopFun`:

```sh
cd frontend
npm ci
npm run dev
```

เปิด http://localhost:5173 แล้วแก้ไฟล์ใน `src/pages/`, `src/styles/` และ `src/assets/images/` ได้เลย
ติดตั้ง dependencies ด้วย `npm ci` ครั้งแรกหรือเมื่อ lockfile เปลี่ยน
ตรวจงานก่อนส่งด้วย `npm run build` จาก `frontend/`

หน้าเว็บเปิดดูได้โดยไม่รัน backend แต่การเข้าสู่ระบบ สมัครสมาชิก และงานที่ใช้ API ต้องรัน backend และ PostgreSQL ด้วย

## รัน backend

เปิด Terminal อีกหน้าต่าง เริ่มจากโฟลเดอร์ `ThaiShopFun`:

```sh
cd backend
```

ทำตาม [คู่มือ backend](backend/README.md) เพื่อตั้งค่าฐานข้อมูลและรันที่ http://localhost:8081

**คำสั่ง npm รันใน `frontend/` ส่วนคำสั่ง Maven รันใน `backend/`**
หาก Terminal อยู่ใน `frontend/` แล้ว ให้ใช้ `cd ../backend` เพื่อเปลี่ยนไปทำงาน backend

## รูปภาพ

- รูปที่ import: `frontend/src/assets/images/`
- รูปที่ใช้ URL: `frontend/public/assets/images/` อ้างอิงเป็น `/assets/images/<หมวด>/<ชื่อไฟล์>`
- รูปต้นแบบที่ไม่ได้ใช้บนเว็บ: `docs/design/`
- แผนภาพ: `docs/flows/`
- ตั้งชื่อรูปเป็นตัวพิมพ์เล็กและขีดกลาง เช่น `merchant-dashboard.png`

## Git สำหรับทีม

Repository หลัก: https://github.com/WichayaThan-code/e-commerce

```sh
git clone https://github.com/WichayaThan-code/e-commerce.git
cd e-commerce/frontend
npm ci
npm run dev
```

แยก branch สำหรับแต่ละงาน แล้วเปิด Pull Request เข้า `main`:

```sh
git switch -c feature/your-task
git add <ไฟล์ที่แก้>
git commit -m "Describe your change"
git push -u origin feature/your-task
```

สมาชิกแต่ละคนต้องตั้งค่าฐานข้อมูลของเครื่องตนเองตาม `backend/README.md` และไม่ commit `.env.local`
