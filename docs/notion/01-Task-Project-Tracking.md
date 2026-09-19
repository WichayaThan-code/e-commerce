# Task & Project Tracking

## Projects database

สร้าง Table database ชื่อ Projects พร้อม property ต่อไปนี้

| Property | ชนิด | การใช้งาน |
| --- | --- | --- |
| Name | Title | ชื่อโครงการย่อย |
| Project ID | Text | PRJ-01 เป็นต้น |
| Status | Status | Backlog / In progress / Review / Done |
| Owner | Person | ผู้รับผิดชอบหลัก |
| Start / Due | Date | วันที่ทีมตกลง |
| Tasks | Relation | เชื่อม Tasks database |
| Progress | Rollup | Tasks → Completed → Percent checked |
| Documents | Relation | เชื่อม Documents database |

รายการโครงการตั้งต้น (เป็นข้อเสนอการจัดหมวด ไม่ใช่สถานะส่งมอบ):

| Project ID | Name | ขอบเขต | สถานะตั้งต้น |
| --- | --- | --- | --- |
| PRJ-01 | สมาชิกและสิทธิ์ | Register, Login, Refresh, Logout, Me | Review |
| PRJ-02 | สมัครร้านค้า | ข้อมูลร้าน เจ้าของ เอกสาร และส่งใบสมัคร | Review |
| PRJ-03 | แอดมินตรวจร้านค้า | คิวตรวจรับ อนุมัติ ปฏิเสธ และบันทึกผู้ดำเนินการ | Backlog |
| PRJ-04 | Marketplace | สินค้า สต็อก ตะกร้า ออเดอร์ และชำระเงิน | Backlog |
| PRJ-05 | คุณภาพและการเปิดใช้งาน | ทดสอบ ความปลอดภัย สำรองข้อมูล และ deployment | Backlog |

## Tasks database

| Property | ชนิด | รายละเอียด |
| --- | --- | --- |
| Name | Title | ชื่องาน |
| Task ID | Text | TASK-001 เป็นต้น |
| Project | Relation | เชื่อม Projects; ใช้ Project ID ในตารางด้านล่างจับคู่ |
| Status | Status | Backlog / Ready / In progress / Review / Blocked / Done |
| Priority | Select | P0 เร่งด่วน / P1 สำคัญ / P2 ปกติ |
| Owner | Person | ทีมกำหนดหลัง import |
| Start / Due | Date | ทีมกำหนดหลัง import |
| Depends on | Relation | เชื่อม Tasks ด้วยตัวเอง |
| Acceptance criteria | Text | เงื่อนไขที่ตรวจสอบได้ |
| Evidence | Text | PR, ผลทดสอบ หรือ URL |
| Blocker | Text | ปัญหาและคนที่ช่วยปลดล็อกได้ |
| Completed | Formula | `prop("Status") == "Done"` |

สถานะ Review ด้านล่างหมายถึง “พบโค้ดแล้ว รอตรวจรับ” ไม่ได้หมายถึงผ่านทดสอบ; Backlog หมายถึงข้อเสนองานต่อไป ทีมปรับ scope ได้

| Task ID | Name | Project ID | Status | Priority | Depends on | Acceptance criteria / หลักฐานตั้งต้น |
| --- | --- | --- | --- | --- | --- | --- |
| TASK-001 | ตรวจรับสมัครสมาชิกและเข้าสู่ระบบ | PRJ-01 | Review | P1 | — | สมัครสำเร็จ; email/phone ซ้ำถูกปฏิเสธ; login ผิดไม่ออก token; ดู AuthIntegrationTests.java |
| TASK-002 | ตรวจรับ refresh/logout และสิทธิ์ | PRJ-01 | Review | P1 | TASK-001 | logout แล้ว refresh เดิมใช้ไม่ได้; ผู้ไม่มีสิทธิ์เข้าถึง API ไม่ได้; ดู AuthService.java และ SecurityConfig.java |
| TASK-003 | ตรวจรับ draft ข้อมูลร้านและเจ้าของ | PRJ-02 | Review | P1 | TASK-001 | บันทึกแล้วเปิดใหม่ข้อมูลครบ; ผู้ใช้หนึ่งคนอ่าน draft ของคนอื่นไม่ได้; ดู MerchantApplicationService.java |
| TASK-004 | ตรวจรับอัปโหลดและส่งใบสมัคร | PRJ-02 | Review | P1 | TASK-003 | ตรวจชนิด/ขนาดไฟล์และเอกสารบังคับ; ส่งสำเร็จมี reference; ดู MerchantApplicationIntegrationTests.java |
| TASK-005 | กำหนดกติกาตรวจร้านค้าและสิทธิ์แอดมิน | PRJ-03 | Backlog | P1 | TASK-004 | ทีมยืนยัน transition ที่อนุญาต เหตุผลปฏิเสธ และผลต่อ role |
| TASK-006 | สร้าง API แอดมินตรวจใบสมัคร | PRJ-03 | Backlog | P1 | TASK-005 | ค้นหาและแบ่งหน้า; เปลี่ยนสถานะตามสิทธิ์; มี audit trail และทดสอบ |
| TASK-007 | เชื่อมหน้าแอดมินกับข้อมูลจริง | PRJ-03 | Backlog | P1 | TASK-006 | มี loading/empty/error; อนุมัติแล้วข้อมูลสอดคล้อง backend; ตรวจ AdminApplicationsPage.tsx |
| TASK-008 | ออกแบบ schema สินค้าและสต็อก | PRJ-04 | Backlog | P1 | — | มี ERD, migration และข้อกำหนดสิทธิ์เจ้าของสินค้า |
| TASK-009 | สร้าง API สินค้าและเชื่อมหน้าจัดการ | PRJ-04 | Backlog | P1 | TASK-008 | CRUD, pagination, validation และ ownership ผ่านทดสอบ |
| TASK-010 | ออกแบบตะกร้าและวงจรออเดอร์ | PRJ-04 | Backlog | P1 | TASK-009 | ระบุสถานะออเดอร์ การคิดราคาและป้องกันสต็อกติดลบ |
| TASK-011 | เลือกและเชื่อมระบบชำระเงิน | PRJ-04 | Backlog | P2 | TASK-010 | กำหนดผู้ให้บริการ; ทดสอบ sandbox, webhook ซ้ำ และยอดไม่ตรง |
| TASK-012 | ทบทวน UX และ responsive | PRJ-04 | Backlog | P2 | — | ตรวจหน้าหลักบนมือถือ/desktop; ระบุ UI ที่ยังเป็นข้อมูลตัวอย่าง |
| TASK-013 | รัน regression และเก็บผล | PRJ-05 | Backlog | P1 | TASK-002; TASK-004 | frontend build และ backend tests ผ่าน; แนบผลพร้อม commit |
| TASK-014 | ตั้งค่าความลับสำหรับ production | PRJ-05 | Backlog | P1 | — | ไม่มี default secret; HTTPS/cookie/origin/storage ถูกต้อง; ไม่บันทึก secret ในเอกสาร |
| TASK-015 | ทดลอง backup และ restore | PRJ-05 | Backlog | P1 | — | กู้คืนลงฐานทดสอบได้; ตรวจ migrations และจำนวนแถว; รวม uploads และวิธีจัดเก็บ encryption key |
| TASK-016 | จัดทำ deployment และ smoke checklist | PRJ-05 | Backlog | P2 | TASK-013; TASK-014; TASK-015 | รันตาม runbook ได้ พร้อม rollback และผู้รับผิดชอบ |

## Views ที่ตั้งค่าใน Notion

- Board: group by Status; sort Priority แล้ว Due
- My tasks: Owner contains Me; Status ไม่ใช่ Done
- Blocked: Status = Blocked; แสดง Blocker และ Owner
- Review: Status = Review; แสดง Evidence และ Acceptance criteria
- Timeline: ใช้ Start/Due หลังทีมใส่วันที่แล้ว
- Overdue: Due ก่อนวันนี้ และ Status ไม่ใช่ Done; ไม่รวมงานไม่มีกำหนดส่ง

## แผนส่งมอบที่เสนอ

1. Milestone 1: ตรวจรับระบบสมาชิกและสมัครร้านค้า — TASK-001 ถึง 004 และ 013
2. Milestone 2: ตรวจร้านค้าโดยแอดมิน — TASK-005 ถึง 007
3. Milestone 3: สินค้าและออเดอร์ — TASK-008 ถึง 010 และ 012
4. Milestone 4: เตรียมเปิดใช้ — TASK-011 และ 014 ถึง 016

Done เมื่อเกณฑ์ตรวจรับครบ ตรวจโค้ดแล้ว ผลทดสอบที่เกี่ยวข้องผ่าน และเอกสารอัปเดต หากติดปัญหาให้ตั้ง Blocked พร้อมระบุสิ่งที่รอ
