import { useState } from "react";
import {
  BarChart3, Bell, Box, CalendarDays, CheckCircle2, ChevronDown, ChevronLeft,
  ChevronRight, CircleHelp, CircleUserRound, Clock3, Crown, Headphones, Home,
  MapPin, MoreHorizontal, Package, Plus, Printer, Search, Settings, ShoppingBag,
  Store, TicketPercent, Truck, WalletCards, XCircle,
} from "lucide-react";
import { useAuth } from "./AuthContext";
import "./merchant-dashboard.css";
import "./product-management.css";
import "./shipping.css";

type Shipment = { tracking:string; order:string; customer:string; carrier:string; date:string; status:string };
const shipments: Shipment[] = [
  {tracking:"TH0123456789",order:"#TSF202609120045",customer:"คุณณัชชา",carrier:"Flash Express",date:"12 ก.ย. 2566 10:32",status:"รอจัดส่ง"},
  {tracking:"SPXTH001234567",order:"#TSF202609120044",customer:"คุณสมชาย",carrier:"Shopee Express",date:"12 ก.ย. 2566 09:15",status:"อยู่ระหว่างขนส่ง"},
  {tracking:"KEX1234567890",order:"#TSF202609120043",customer:"คุณวรภา",carrier:"KERRY Express",date:"11 ก.ย. 2566 18:45",status:"จัดส่งสำเร็จ"},
  {tracking:"TH9876543210",order:"#TSF202609120042",customer:"คุณกิตติ",carrier:"ไปรษณีย์ไทย",date:"11 ก.ย. 2566 16:20",status:"อยู่ระหว่างขนส่ง"},
  {tracking:"JNT123456789",order:"#TSF202609120041",customer:"คุณปิยะ",carrier:"J&T Express",date:"10 ก.ย. 2566 14:08",status:"จัดส่งสำเร็จ"},
  {tracking:"BEST12345678",order:"#TSF202609120040",customer:"คุณสุนิสา",carrier:"Best Express",date:"10 ก.ย. 2566 10:30",status:"จัดส่งไม่สำเร็จ"},
  {tracking:"TH5566778899",order:"#TSF202609120039",customer:"คุณอรทัย",carrier:"ไปรษณีย์ไทย",date:"9 ก.ย. 2566 13:25",status:"รอจัดส่ง"},
  {tracking:"SPXTH009988776",order:"#TSF202609120038",customer:"คุณณัฐวุฒิ",carrier:"Shopee Express",date:"9 ก.ย. 2566 11:12",status:"อยู่ระหว่างขนส่ง"},
];
const menu = [
  [Home,"หน้าหลัก","/merchant/dashboard"],[Box,"จัดการสินค้า","/merchant/products"],
  [ShoppingBag,"คำสั่งซื้อ","/merchant/orders","12"],[CircleUserRound,"ลูกค้า","/merchant/customers"],
  [TicketPercent,"โปรโมชั่น/คูปอง","/merchant/promotions"],[BarChart3,"รายงาน","/merchant/reports"],
  [WalletCards,"การเงิน","/merchant/finance"],[Truck,"การจัดส่ง","/merchant/shipping"],
  [Store,"ร้านของฉัน","/merchant/store"],[Settings,"ตั้งค่า","/merchant/settings"],
] as const;
const stats = [
  [Package,"พัสดุทั้งหมด","532","↑ 12%","ในช่วงนี้","all"],
  [Clock3,"รอจัดส่ง","68","12%","รอแพ็คสินค้า","wait"],
  [Truck,"อยู่ระหว่างขนส่ง","420","↑ 18%","กำลังจัดส่ง","moving"],
  [CheckCircle2,"จัดส่งสำเร็จ","418","↑ 15%","คิดเป็น 78%","success"],
  [XCircle,"จัดส่งไม่สำเร็จ","26","5%","ตีกลับ / ลูกค้าไม่รับ","failed"],
] as const;
const carrierCode:Record<string,string>={"Flash Express":"FLASH","KERRY Express":"KERRY","ไปรษณีย์ไทย":"TH POST","J&T Express":"J&T","Shopee Express":"S","Best Express":"BEST"};
function Brand(){return <a className="merchant-brand" href="/"><span><ShoppingBag/><i>✓</i></span><div><b>ThaiShop<span>Fun</span></b><small>Shop Local · Support Thai · Smile Together</small></div></a>}

export default function ShippingPage(){
  const {user,loading}=useAuth();
  const [notice,setNotice]=useState("");
  const [tab,setTab]=useState("ทั้งหมด");
  const [query,setQuery]=useState("");
  const [carrier,setCarrier]=useState("ทั้งหมด");
  const [status,setStatus]=useState("ทั้งหมด");
  const [selected,setSelected]=useState<string[]>([]);
  if(loading)return <main className="auth-loading">กำลังตรวจสอบสิทธิ์ร้านค้า...</main>;
  if(!user){window.location.replace("/login?next=/merchant/shipping");return null}
  if(user.role==="ADMIN"){window.location.replace("/admin");return null}
  if(user.role!=="MERCHANT"){window.location.replace("/");return null}
  const notify=(text:string)=>{setNotice(text);window.setTimeout(()=>setNotice(""),1900)};
  const shopName=(user.firstName||"MaeSomchai").toLowerCase()==="merchant"?"MaeSomchai Official":`${user.firstName||"MaeSomchai"} Official`;
  const visible=shipments.filter(x=>(tab==="ทั้งหมด"||x.status===tab)&&(status==="ทั้งหมด"||x.status===status)&&(carrier==="ทั้งหมด"||x.carrier===carrier)&&(`${x.tracking} ${x.order} ${x.customer}`.toLowerCase().includes(query.toLowerCase())));
  const reset=()=>{setTab("ทั้งหมด");setQuery("");setCarrier("ทั้งหมด");setStatus("ทั้งหมด");notify("รีเซ็ตตัวกรองแล้ว")};
  return <main className="merchant-shell pm-shell shipping-shell">
    <header className="merchant-topbar"><Brand/><form onSubmit={e=>{e.preventDefault();notify("ค้นหาข้อมูลร้านค้าแล้ว")}}><Search/><input aria-label="ค้นหาทั้งระบบ" placeholder="ค้นหาสินค้า, คำสั่งซื้อ, ลูกค้า..."/></form><button className="merchant-bell" aria-label="การแจ้งเตือน"><Bell/><i>3</i></button><div className="merchant-profile"><span>🏪</span><p><b>{shopName}</b><small>เจ้าของร้าน</small></p><ChevronDown/></div></header>
    <aside className="merchant-sidebar pm-sidebar"><nav>{menu.map(([Icon,label,href,badge])=><a className={label==="การจัดส่ง"?"active":""} href={href} key={label} onClick={e=>{if((href as string)==="#"){e.preventDefault();notify(`${label}: อยู่ระหว่างเชื่อมข้อมูล`)}}}><Icon/>{label}{badge&&<i>{badge}</i>}</a>)}</nav><section className="upgrade-card"><Crown/><h3>ยกระดับร้านค้าของคุณ</h3><p>ใช้ฟีเจอร์ครบชุด เพิ่มยอดขาย<br/>ได้มากกว่าเดิม</p><button onClick={()=>notify("กำลังเปิดรายละเอียดแพ็กเกจ")}>ดูแพ็กเกจ</button></section><div className="merchant-support"><a href="/help"><CircleHelp/>ศูนย์ช่วยเหลือ</a><button onClick={()=>notify("กำลังติดต่อทีมงาน")}><Headphones/>ติดต่อทีมงาน</button></div></aside>
    <section className="pm-content shipping-content">
      <header className="pm-heading shipping-heading"><div><h1><Truck/>การจัดส่ง</h1><p>จัดการการจัดส่งพัสดุ เชื่อมต่อบริษัทขนส่ง ติดตามสถานะได้ง่ายในที่เดียว</p></div><div className="pm-actions"><button onClick={()=>notify("พร้อมพิมพ์ใบปะหน้าสำหรับรายการที่เลือก")}><Printer/>พิมพ์ใบปะหน้า<ChevronDown/></button><button className="primary" onClick={()=>notify("เปิดหน้าต่างเชื่อมต่อบริษัทขนส่ง")}><Plus/>เชื่อมต่อขนส่งเพิ่ม</button></div></header>
      <section className="shipping-kpis">{stats.map(([Icon,title,value,trend,sub,kind])=><article className={kind} key={title}><span><Icon/></span><div><small>{title}</small><b>{value}<i>{trend}</i></b><em>{sub}</em></div></article>)}</section>
      <div className="shipping-layout"><article className="shipping-table-card">
        <nav className="shipping-tabs">{[["ทั้งหมด","532"],["รอจัดส่ง","68"],["อยู่ระหว่างขนส่ง","420"],["จัดส่งสำเร็จ","418"],["จัดส่งไม่สำเร็จ","26"]].map(([x,n])=><button className={tab===x?"active":""} onClick={()=>setTab(x)} key={x}>{x} ({n})</button>)}</nav>
        <div className="shipping-filters"><label className="shipping-search"><Search/><input value={query} onChange={e=>setQuery(e.target.value)} placeholder="ค้นหาด้วยเลขพัสดุ, ชื่อลูกค้า, หมายเลขคำสั่งซื้อ..."/></label><label>บริษัทขนส่ง<select value={carrier} onChange={e=>setCarrier(e.target.value)}><option>ทั้งหมด</option>{Object.keys(carrierCode).map(x=><option key={x}>{x}</option>)}</select></label><label>สถานะ<select value={status} onChange={e=>setStatus(e.target.value)}><option>ทั้งหมด</option><option>รอจัดส่ง</option><option>อยู่ระหว่างขนส่ง</option><option>จัดส่งสำเร็จ</option><option>จัดส่งไม่สำเร็จ</option></select></label><label>ช่วงวันที่<button className="date"><CalendarDays/>1 ก.ย. 2026 - 12 ก.ย. 2026<ChevronDown/></button></label><div><button onClick={reset}>รีเซ็ต</button><button className="primary" onClick={()=>notify(`พบ ${visible.length} รายการ`)}>ค้นหา</button></div></div>
        <div className="shipping-table-wrap"><table><thead><tr><th>#</th><th><input aria-label="เลือกทั้งหมด" type="checkbox" checked={selected.length===shipments.length} onChange={e=>setSelected(e.target.checked?shipments.map(x=>x.tracking):[])}/></th><th>เลขพัสดุ</th><th>คำสั่งซื้อ</th><th>ลูกค้า</th><th>บริษัทขนส่ง</th><th>วันที่จัดส่ง</th><th>สถานะ</th><th>จัดการ</th></tr></thead><tbody>{visible.map((row,i)=><tr key={row.tracking}><td>{i+1}</td><td><input aria-label={`เลือก ${row.tracking}`} type="checkbox" checked={selected.includes(row.tracking)} onChange={e=>setSelected(e.target.checked?[...selected,row.tracking]:selected.filter(x=>x!==row.tracking))}/></td><td><b>{row.tracking}</b> ↗</td><td>{row.order}</td><td>{row.customer}</td><td><span className={`carrier-logo ${row.carrier.split(" ")[0].toLowerCase()}`}>{carrierCode[row.carrier]}</span>{row.carrier}</td><td>{row.date}</td><td><span className={`shipping-status ${row.status==="รอจัดส่ง"?"wait":row.status==="อยู่ระหว่างขนส่ง"?"moving":row.status==="จัดส่งสำเร็จ"?"success":"failed"}`}>{row.status}</span></td><td><div className="shipping-actions"><button onClick={()=>notify(row.status==="รอจัดส่ง"?`พิมพ์ใบปะหน้า ${row.tracking}`:row.status==="อยู่ระหว่างขนส่ง"?`ติดตามพัสดุ ${row.tracking}`:`ดูรายละเอียด ${row.tracking}`)}>{row.status==="รอจัดส่ง"?"พิมพ์ใบปะหน้า":row.status==="อยู่ระหว่างขนส่ง"?"ติดตามพัสดุ":"ดูรายละเอียด"}</button><button aria-label="ตัวเลือกเพิ่มเติม" onClick={()=>notify(`เปิดเมนู ${row.tracking}`)}><MoreHorizontal/></button></div></td></tr>)}</tbody></table>{!visible.length&&<div className="pm-empty">ไม่พบรายการจัดส่งที่ตรงกับตัวกรอง</div>}</div>
        <footer className="pm-pagination"><span>แสดง 1 - {visible.length} จาก 532 รายการ</span><label>แสดง <select><option>8</option><option>20</option></select> รายการ</label><nav><button><ChevronLeft/></button><button className="active">1</button><button>2</button><button>3</button><button>4</button><button>5</button><span>...</span><button>67</button><button><ChevronRight/></button></nav></footer>
      </article><aside className="shipping-side">
        <section><header><h2>เชื่อมต่อบริษัทขนส่ง</h2><button onClick={()=>notify("แสดงบริษัทขนส่งทั้งหมด")}>ดูทั้งหมด →</button></header>{Object.keys(carrierCode).map((name,i)=><p key={name}><span className={`carrier-logo ${name.split(" ")[0].toLowerCase()}`}>{carrierCode[name]}</span><b>{name}</b><button className={i===3||i===5?"connect":"connected"} onClick={()=>notify(i===3||i===5?`เริ่มเชื่อมต่อ ${name}`:`${name} เชื่อมต่อแล้ว`)}>{i===3||i===5?"เชื่อมต่อเลย":"เชื่อมต่อแล้ว"}</button></p>)}</section>
        <section className="shipping-tools"><h2>เครื่องมือการจัดส่ง</h2>{([[Printer,"พิมพ์ใบปะหน้า (หลายรายการ)"],[Package,"สร้างรายการจัดส่ง (Bulk)"],[Box,"ตั้งค่าขนาดพัสดุ"],[MapPin,"ตั้งค่าที่อยู่ผู้ส่ง"],[Clock3,"ประวัติการจัดส่ง"]] as [typeof Printer,string][]).map(([Icon,label])=><button key={label} onClick={()=>notify(`เปิด${label}`)}><Icon/>{label}<ChevronRight/></button>)}</section>
        <section className="shipping-help"><Headphones/><div><b>มีปัญหาการจัดส่ง?</b><small>ติดต่อทีมงาน หรือดูคู่มือการเชื่อมต่อขนส่ง</small></div><ChevronRight/></section>
      </aside></div>
    </section>{notice&&<div className="merchant-toast" role="status">{notice}</div>}
  </main>
}
