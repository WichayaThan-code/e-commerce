# Frontend — งาน UX/UI ของ pond

## รันบน macOS / Windows

จากโฟลเดอร์ ThaiShopFun:

```sh
cd frontend
npm ci
npm run dev
```

เปิด http://localhost:5173 กด Ctrl+C เพื่อหยุดเซิร์ฟเวอร์
ใช้ Node.js LTS; เครื่องนี้ทดสอบด้วย Node.js 24

## แก้ไฟล์ตรงไหน

| งาน | ไฟล์หรือโฟลเดอร์ |
| --- | --- |
| หน้าตาและองค์ประกอบแต่ละหน้า | `src/pages/` |
| สี ฟอนต์ ระยะห่าง responsive | `src/styles/` |
| หน้าแรกและการเลือกหน้า | `src/App.tsx` |
| รูปที่ import | `src/assets/images/` |
| รูปที่ใช้ URL | `public/assets/images/` |
| การเชื่อม API | `src/services/api.ts` |
| สถานะล็อกอิน | `src/contexts/AuthContext.tsx` |
| รูปต้นแบบ | `../docs/design/` |

## ตั้งค่า API

ค่าเริ่มต้นคือ `http://localhost:8081` หากต้องเปลี่ยนให้คัดลอก `.env.example` เป็น `.env` ในโฟลเดอร์นี้ แล้วแก้ `VITE_API_URL` และรีสตาร์ต dev server
อย่าใส่รหัสผ่านหรือ secret ในตัวแปร `VITE_*` เพราะถูกส่งไปยังเบราว์เซอร์

## ตรวจสอบก่อนส่งงาน

```sh
npm run build
```

ผลลัพธ์อยู่ใน `dist/` ทดสอบผล build ด้วย `npm run preview`
การทดสอบสมัครสมาชิก ล็อกอิน หรือหน้าที่ดึงข้อมูล ต้องเปิด backend และ PostgreSQL ด้วย
