# Templates สำหรับ Notion

## Task template

- Task ID / ชื่องาน:
- Project / Owner / Priority:
- Status / Start / Due:
- ปัญหาหรือเป้าหมาย:
- ขอบเขตงาน:
- Depends on / Blocker:
- Acceptance criteria:
  - [ ] ผลลัพธ์ที่ตรวจสอบได้ข้อ 1
  - [ ] ผลลัพธ์ที่ตรวจสอบได้ข้อ 2
- หลักฐาน: PR / commit / ผลทดสอบ / รูปหน้าจอ
- [ ] เอกสารที่เกี่ยวข้องอัปเดตแล้ว
- ผู้ตรวจรับ / วันที่ตรวจรับ:

## Bug template

- สรุปอาการ / ความรุนแรง:
- Environment / commit / browser:
- ขั้นตอนทำซ้ำ:
  1. …
  2. …
- Expected:
- Actual:
- หลักฐานที่ลบข้อมูลส่วนบุคคลแล้ว:
- สาเหตุ / วิธีแก้ / regression check:

## Technical document template

- ชื่อ / เจ้าของ / สถานะ / วันที่อัปเดต:
- เป้าหมายและผู้อ่าน:
- ขอบเขต:
- พฤติกรรมที่ต้องการ:
- API / schema / flow ที่เกี่ยวข้อง:
- Error cases / สิทธิ์การเข้าถึง:
- วิธีตรวจสอบ:
- แหล่งอ้างอิง / PR / Related tasks:

## Decision log template

- Decision ID / วันที่ / ผู้ตัดสินใจ:
- ปัญหาที่ต้องตัดสินใจ:
- ทางเลือกและข้อแลกเปลี่ยน:
- ข้อสรุปและเหตุผล:
- ผลต่อ API / schema / UX / operation:
- งานติดตาม / วันทบทวน:

## Weekly review template

- สัปดาห์:
- เป้าหมาย:
- งาน Done พร้อมหลักฐาน:
- งาน Review ที่ต้องการคนตรวจ:
- งาน Blocked และคนช่วยปลดล็อก:
- กำหนดส่งที่เปลี่ยนและเหตุผล:
- แผนสัปดาห์หน้า / Owner:

## Release checklist

- [ ] งานใน release มีเกณฑ์ตรวจรับและผ่าน review
- [ ] build และ tests ที่เกี่ยวข้องผ่าน พร้อม commit อ้างอิง
- [ ] migration ทดลองบนฐานทดสอบแล้ว
- [ ] ตั้งค่า secrets, HTTPS, origin และ storage แล้ว
- [ ] backup รวมฐานข้อมูล/ไฟล์และทดสอบ restore แล้ว
- [ ] smoke test สมัครสมาชิก เข้าสู่ระบบ สมัครร้านค้า และอัปโหลดผ่าน
- [ ] มีผู้รับผิดชอบติดตามผลและวิธีย้อนกลับ
