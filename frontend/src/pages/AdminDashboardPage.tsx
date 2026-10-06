import { useState } from 'react'
import {
  BarChart3, Bell, CalendarDays, ChevronDown, CircleDollarSign, ClipboardList,
  FileCheck2, FileText, Headphones, Home, LayoutDashboard, LogOut, Megaphone,
  Package, ReceiptText, Search, Settings, ShieldCheck, ShoppingBag, Store,
  Tags, TrendingUp, UserCog, UsersRound, WalletCards,
} from 'lucide-react'
<<<<<<< HEAD
import { useAuth } from '../contexts/AuthContext'
import '../styles/admin-dashboard.css'

const navGroups=[
  {label:'จัดการร้านค้า',items:[[Store,'ร้านค้า'],[FileCheck2,'ใบสมัครร้านค้า','18'],[Tags,'หมวดหมู่สินค้า'],[Megaphone,'โปรโมชั่น/แคมเปญ']]},
  {label:'จัดการผู้ใช้',items:[[UsersRound,'ผู้ใช้งาน'],[UserCog,'บทบาทและสิทธิ์']]},
  {label:'การเงิน',items:[[CircleDollarSign,'รายได้/ค่าธรรมเนียม'],[WalletCards,'การชำระเงิน'],[ReceiptText,'ใบแจ้งหนี้']]},
  {label:'รายงาน',items:[[BarChart3,'รายงานภาพรวม'],[TrendingUp,'รายงานการขาย'],[FileText,'รายงานการเงิน']]},
  {label:'ระบบ',items:[[Settings,'ตั้งค่า'],[ClipboardList,'จัดการเนื้อหา'],[Bell,'แจ้งเตือน'],[FileCheck2,'บันทึกการใช้งาน']]},
] as const

=======
import AdminHeader from '../components/AdminHeader'
import AdminSidebar from '../components/AdminSidebar'
import { useAuth } from '../contexts/AuthContext'
import '../styles/admin-dashboard.css'

>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
const applications=[
  ['#REG20260912001','บ้านขนมไทย','คุณสมหญิง ใจดี','12 ก.ย. 2026 10:15','รออนุมัติ'],
  ['#REG20260912002','Green Farm','คุณอนุชา เกษตร','12 ก.ย. 2026 09:42','รออนุมัติ'],
  ['#REG20260912003','Craft & Co.','คุณวิภา รักษางานฝีมือ','11 ก.ย. 2026 18:30','รออนุมัติ'],
  ['#REG20260912004','Healthy Life','คุณณภัทร สุขภาพดี','11 ก.ย. 2026 16:21','รอเอกสารเพิ่ม'],
  ['#REG20260911098','Pet Planet','คุณธนกฤต ใจดี','10 ก.ย. 2026 14:03','รออนุมัติ'],
]
const stores=[['🥇','MaeSomchai Official','฿82,430','12%'],['🥈','Healthy Life','฿64,120','9%'],['🥉','บ้านขนมไทย','฿52,300','8%'],['4','Craft & Co.','฿48,950','15%'],['5','Pet Planet','฿36,200','11%']]
const chartBars=[68,56,73,88,94,72,91,102,86,108,132,101]
const linePoints='20,78 80,88 140,70 200,50 260,41 320,62 380,38 440,29 500,49 560,29 620,9 680,26'

<<<<<<< HEAD
function AdminBrand(){return <a className="admin-brand" href="/"><span><ShoppingBag/><i>✓</i></span><div><b>ThaiShop<span>Fun</span></b><small>Shop Local · Support Thai · Smile Together</small></div></a>}
function Panel({title,action,children,className=''}:{title:string;action?:string;children:React.ReactNode;className?:string}){return <section className={`admin-panel ${className}`}><header><h2>{title}</h2>{action&&<button>{action} →</button>}</header>{children}</section>}

export default function AdminDashboardPage(){
  const {user,loading,logout}=useAuth()
=======
function Panel({title,action,children,className=''}:{title:string;action?:string;children:React.ReactNode;className?:string}){return <section className={`admin-panel ${className}`}><header><h2>{title}</h2>{action&&<button>{action} →</button>}</header>{children}</section>}

export default function AdminDashboardPage(){
  const {user,loading}=useAuth()
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
  const [notice,setNotice]=useState('')
  if(loading)return <main className="auth-loading">กำลังตรวจสอบสิทธิ์ผู้ดูแลระบบ...</main>
  if(!user){window.location.replace('/login');return null}
  if(user.role!=='ADMIN'){window.location.replace('/dashboard');return null}
  const notify=(text:string)=>{setNotice(text);window.setTimeout(()=>setNotice(''),1800)}
<<<<<<< HEAD
  const signOut=async()=>{await logout();window.location.replace('/')}
  return <main className="admin-shell">
    <header className="admin-topbar"><AdminBrand/><form onSubmit={e=>{e.preventDefault();notify('กำลังค้นหาในระบบ')}}><input aria-label="ค้นหา" placeholder="ค้นหาร้านค้า ผู้ใช้ ใบสมัคร หรือคำสั่งซื้อ..."/><button aria-label="ค้นหา"><Search/></button></form><button className="admin-bell" aria-label="การแจ้งเตือน"><Bell/><i>12</i></button><div className="admin-profile"><span>{user.firstName.charAt(0).toUpperCase()}</span><p><b>{user.email}</b><small>Administrator</small></p><ChevronDown/></div></header>
    <aside className="admin-sidebar"><a className="active" href="/admin"><Home/>หน้าแรก</a>{navGroups.map(group=><section key={group.label}><h3>{group.label}</h3>{group.items.map(([Icon,label,badge])=>label==='ร้านค้า'?<a href="/admin/stores" key={label}><Icon/>{label}</a>:label==='ใบสมัครร้านค้า'?<a href="/admin/store-applications" key={label}><Icon/>{label}{badge&&<i>{badge}</i>}</a>:label==='หมวดหมู่สินค้า'?<a href="/admin/categories" key={label}><Icon/>{label}</a>:label==='โปรโมชั่น/แคมเปญ'?<a href="/admin/promotions" key={label}><Icon/>{label}</a>:label==='ผู้ใช้งาน'?<a href="/admin/users" key={label}><Icon/>{label}</a>:label==='บทบาทและสิทธิ์'?<a href="/admin/roles" key={label}><Icon/>{label}</a>:<button key={label} onClick={()=>notify(`${label}: อยู่ระหว่างเชื่อมข้อมูล`)}><Icon/>{label}{badge&&<i>{badge}</i>}</button>)}</section>)}<button className="admin-help"><Headphones/>ศูนย์ช่วยเหลือ</button><button className="admin-logout" onClick={signOut}><LogOut/>ออกจากระบบ</button></aside>
=======
  return <main className="admin-shell">
    <AdminHeader user={user} />
    <AdminSidebar active="home" showHelp extended/>
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
    <div className="admin-content"><section className="admin-welcome"><div><span>👋</span><div><h1>สวัสดีครับ, {user.email}</h1><p>ภาพรวมการใช้งานแพลตฟอร์ม ThaiShopFun</p><small>“สนับสนุนร้านค้าไทย เพื่อธุรกิจไทยเติบโตไปด้วยกัน”</small></div></div><button><CalendarDays/>ช่วงวันที่ <b>1 ก.ย. 2026 - 12 ก.ย. 2026</b><ChevronDown/></button></section>
      <section className="admin-kpis">
        <article className="blue"><span><Store/></span><div><small>ร้านค้าทั้งหมด</small><b>1,245 <i>↑ 12%</i></b><em>จากเดือนก่อน</em></div></article>
        <article className="orange"><span><FileCheck2/></span><div><small>ใบสมัครที่รอตรวจสอบ</small><b>18 <i>↑ 80%</i></b><em>รออนุมัติ 18 | ปฏิเสธ 4</em></div></article>
        <article className="green"><span><UsersRound/></span><div><small>ผู้ใช้งานทั้งหมด</small><b>3,892 <i>↑ 15%</i></b><em>ลูกค้า 3,621 | เจ้าของร้าน 271</em></div></article>
        <article className="purple"><span><ShoppingBag/></span><div><small>ยอดขายรวม (GMV)</small><b>฿ 625,430 <i>↑ 22%</i></b><em>จากเดือนก่อน</em></div></article>
        <article className="red"><span><CircleDollarSign/></span><div><small>ค่าธรรมเนียม 5%</small><b>฿ 31,271 <i>↑ 22%</i></b><em>จากเดือนก่อน</em></div></article>
      </section>
      <section className="admin-grid top"><Panel title="ยอดขายและค่าธรรมเนียม (รายวัน)" action="ดูรายละเอียด"><div className="chart-legend"><span><i/>ยอดขาย (บาท)</span><span><i/>ค่าธรรมเนียม 5% (บาท)</span></div><div className="sales-chart"><div className="y-labels"><span>200,000</span><span>150,000</span><span>100,000</span><span>50,000</span><span>0</span></div><div className="plot">{chartBars.map((height,index)=><div className="bar-column" key={index}><i style={{height}}/><small>{index+1} ก.ย.</small></div>)}<svg viewBox="0 0 700 105" preserveAspectRatio="none"><polyline points={linePoints}/>{linePoints.split(' ').map((point,index)=>{const [cx,cy]=point.split(',');return <circle key={index} cx={cx} cy={cy} r="4"/>})}</svg></div></div></Panel><Panel title="สัดส่วนช่องทางการขาย" action="ดูทั้งหมด"><div className="channel-wrap"><div className="donut"><b>฿625,430<small>ยอดขายรวม</small></b></div><ul><li><i/>หน้าร้านออนไลน์ <b>42%</b></li><li><i/>LINE OA <b>24%</b></li><li><i/>Facebook <b>16%</b></li><li><i/>TikTok Shop <b>10%</b></li><li><i/>Shopee <b>5%</b></li><li><i/>Lazada <b>3%</b></li></ul></div></Panel></section>
      <section className="admin-grid middle"><Panel title="ใบสมัครร้านค้าที่รอตรวจสอบ" action="ดูทั้งหมด"><div className="application-table"><div className="table-row head"><b>#</b><b>ชื่อร้านค้า</b><b>เจ้าของร้าน</b><b>วันที่สมัคร</b><b>เอกสาร</b><b>สถานะ</b><b>การดำเนินการ</b></div>{applications.map((row,index)=><div className="table-row" key={row[0]}><span>{row[0]}</span><b><i className="store-avatar">{['🍰','🌿','☕','🍀','🐕'][index]}</i>{row[1]}</b><span>{row[2]}</span><span>{row[3]}</span><FileText/><em className={row[4]==='รอเอกสารเพิ่ม'?'waiting-doc':''}>{row[4]}</em><button onClick={()=>notify(`เปิดตรวจสอบ ${row[0]}`)}>ตรวจสอบ</button></div>)}</div></Panel><Panel title="ร้านค้ายอดนิยม (ตามยอดขาย)" action="ดูทั้งหมด"><ol className="top-stores">{stores.map(row=><li key={row[1]}><i>{row[0]}</i><b>{row[1]}</b><span>{row[2]}</span><em>↑ {row[3]}</em></li>)}</ol></Panel></section>
      <section className="admin-grid bottom"><Panel title="ผู้ใช้งานใหม่ (7 วันล่าสุด)" action="ดูทั้งหมด"><div className="mini-table"><b>ชื่อผู้ใช้</b><b>อีเมล</b><b>ประเภท</b><span>somchai88</span><span>somchai88@gmail.com</span><span>เจ้าของร้าน</span><span>natcha_shop</span><span>natcha@gmail.com</span><span>เจ้าของร้าน</span><span>kanyarat</span><span>kanyarat@gmail.com</span><span>ลูกค้า</span></div></Panel><Panel title="รายได้ค่าธรรมเนียม (5%)" action="ดูรายละเอียด"><div className="fee-number"><b>฿31,271 <i>↑ 22%</i></b><small>จากเดือนก่อน</small><div><span style={{height:44}}>฿18,430</span><span style={{height:61}}>฿24,210</span><span style={{height:82}}>฿31,271</span></div></div></Panel><Panel title="ประกาศจากผู้ดูแลระบบ" action="ดูทั้งหมด"><ul className="announcements"><li><i/>12 ก.ย. 2026 <span>อัปเดตระบบการชำระเงิน (QR)</span></li><li><i/>10 ก.ย. 2026 <span>เพิ่มช่องทางเชื่อมต่อ TikTok Shop</span></li><li><i/>5 ก.ย. 2026 <span>ปรับปรุงหน้าการสมัครร้านค้า</span></li></ul></Panel></section>
    </div>{notice&&<div className="admin-toast" role="status">{notice}</div>}
  </main>
}
