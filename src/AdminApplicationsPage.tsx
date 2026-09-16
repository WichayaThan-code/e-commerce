import { useMemo, useState } from 'react'
import {
  BarChart3, Bell, Building2, CalendarDays, Check, ChevronDown, ChevronLeft,
  ChevronRight, CircleDollarSign, CircleX, Clock3, Facebook, FileCheck2,
  FileText, Home, Instagram, LockKeyhole, LogOut, Mail, MapPin, Megaphone,
  MessageCircle, MoreVertical, Phone, Plus, ReceiptText, Search, Settings,
  ShoppingBag, Store, Tags, TrendingUp, UserCog, UsersRound, WalletCards, X,
} from 'lucide-react'
import { useAuth } from './AuthContext'
import './admin-dashboard.css'
import './admin-applications.css'

type ApplicationStatus = 'รอการตรวจสอบ' | 'อนุมัติแล้ว' | 'ปฏิเสธแล้ว'
type ApplicationRecord = {
  id:number; logo:string; logoClass:string; shop:string; owner:string; phone:string;
  province:string; category:string; categoryClass:string; appliedAt:string;
  status:ApplicationStatus; email:string; address:string; description:string;
}

const navGroups=[
  {label:'จัดการร้านค้า',items:[[Store,'ร้านค้า',''],[FileCheck2,'ใบสมัครร้านค้า','18'],[Tags,'หมวดหมู่สินค้า',''],[Megaphone,'โปรโมชั่น/แคมเปญ','']]},
  {label:'จัดการผู้ใช้',items:[[UsersRound,'ผู้ใช้งาน',''],[UserCog,'บทบาทและสิทธิ์','']]},
  {label:'การเงิน',items:[[CircleDollarSign,'รายได้/ค่าธรรมเนียม',''],[WalletCards,'การชำระเงิน',''],[ReceiptText,'ใบแจ้งหนี้','']]},
  {label:'รายงาน',items:[[BarChart3,'รายงานภาพรวม',''],[TrendingUp,'รายงานการขาย',''],[FileText,'รายงานการเงิน','']]},
  {label:'ระบบ',items:[[Settings,'ตั้งค่า','']]},
] as const

const applications:ApplicationRecord[]=[
  {id:1,logo:'🪴',logoClass:'garden',shop:'บ้านสวนคุณต้น',owner:'คุณสมชาย ใจดี',phone:'081-234-5678',province:'เชียงใหม่',category:'ต้นไม้และสวน',categoryClass:'garden-cat',appliedAt:'12 ก.ย. 2026 10:15',status:'รอการตรวจสอบ',email:'somchai@gmail.com',address:'123 หมู่ 4 ต.สุเทพ อ.เมือง จ.เชียงใหม่ 50200',description:'จำหน่ายต้นไม้ กระถาง และอุปกรณ์สวน สินค้าคุณภาพ ปลูกง่าย ดูแลง่าย'},
  {id:2,logo:'🥫',logoClass:'foodshop',shop:'ขนมไทยแม่มานี',owner:'คุณกานต์ศิริ วงศ์พัฒนา',phone:'089-765-4321',province:'กรุงเทพฯ',category:'อาหารและเครื่องดื่ม',categoryClass:'food-cat',appliedAt:'11 ก.ย. 2026 14:32',status:'รอการตรวจสอบ',email:'manee@gmail.com',address:'88 ถ.สุขุมวิท เขตวัฒนา กรุงเทพฯ 10110',description:'ขนมไทยสูตรดั้งเดิม ผลิตสดใหม่ทุกวัน'},
  {id:3,logo:'👗',logoClass:'fashion-story',shop:'Fashion Story',owner:'คุณศิริสกุล',phone:'091-987-6543',province:'นนทบุรี',category:'แฟชั่นและเสื้อผ้า',categoryClass:'fashion-cat',appliedAt:'10 ก.ย. 2026 09:21',status:'รอการตรวจสอบ',email:'fashionstory@gmail.com',address:'45/8 อ.เมือง จ.นนทบุรี 11000',description:'เสื้อผ้าแฟชั่นร่วมสมัยสำหรับทุกวัน'},
  {id:4,logo:'👩🏻',logoClass:'healthy-life',shop:'Healthy Life',owner:'คุณวราภรณ์ มีสุข',phone:'082-111-2233',province:'ขอนแก่น',category:'สุขภาพและความงาม',categoryClass:'beauty-cat',appliedAt:'9 ก.ย. 2026 16:18',status:'อนุมัติแล้ว',email:'healthy@gmail.com',address:'79 ถ.มิตรภาพ อ.เมือง จ.ขอนแก่น 40000',description:'ผลิตภัณฑ์ดูแลสุขภาพและความงามที่ผ่านการคัดสรร'},
  {id:5,logo:'🐻',logoClass:'pet-lover',shop:'Pet Lover',owner:'คุณศิรินทร์ บุญนิ่ม',phone:'096-888-7766',province:'ชลบุรี',category:'สัตว์เลี้ยง',categoryClass:'pet-cat',appliedAt:'9 ก.ย. 2026 11:05',status:'รอการตรวจสอบ',email:'petlover@gmail.com',address:'19/2 อ.บางละมุง จ.ชลบุรี 20150',description:'อาหารและอุปกรณ์สำหรับสัตว์เลี้ยง'},
  {id:6,logo:'☕',logoClass:'thai-coffee',shop:'กาแฟดอยไทย',owner:'คุณชยพล คำดี',phone:'080-555-8899',province:'เชียงราย',category:'อาหารและเครื่องดื่ม',categoryClass:'food-cat',appliedAt:'8 ก.ย. 2026 13:40',status:'ปฏิเสธแล้ว',email:'doicoffee@gmail.com',address:'102 อ.แม่สาย จ.เชียงราย 57130',description:'เมล็ดกาแฟจากเกษตรกรภาคเหนือ'},
  {id:7,logo:'♙',logoClass:'jane',shop:'ของใช้ในบ้าน By Jane',owner:'คุณเจนจิรา สวัสดี',phone:'099-222-3344',province:'ปทุมธานี',category:'บ้านและไลฟ์สไตล์',categoryClass:'home-cat',appliedAt:'8 ก.ย. 2026 10:12',status:'อนุมัติแล้ว',email:'jane@gmail.com',address:'12/9 อ.คลองหลวง จ.ปทุมธานี 12120',description:'ของใช้ในบ้านดีไซน์เรียบง่าย'},
  {id:8,logo:'N',logoClass:'narak-handmade',shop:'Narak Handmade',owner:'คุณนภัสสร รักงาน',phone:'098-333-4455',province:'นครปฐม',category:'งานฝีมือและของขวัญ',categoryClass:'craft-cat',appliedAt:'7 ก.ย. 2026 09:50',status:'รอการตรวจสอบ',email:'narakhandmade@gmail.com',address:'55 อ.สามพราน จ.นครปฐม 73110',description:'ของขวัญทำมือสำหรับทุกโอกาส'},
]

function AdminBrand(){return <a className="admin-brand" href="/"><span><ShoppingBag/><i>✓</i></span><div><b>ThaiShop<span>Fun</span></b><small>Shop Local · Support Thai · Smile Together</small></div></a>}

export default function AdminApplicationsPage(){
  const {user,loading,logout}=useAuth()
  const [query,setQuery]=useState('')
  const [status,setStatus]=useState('ทั้งหมด')
  const [category,setCategory]=useState('ทั้งหมด')
  const [province,setProvince]=useState('ทั้งหมด')
  const [selectedId,setSelectedId]=useState(1)
  const [selectedRows,setSelectedRows]=useState<number[]>([])
  const [tab,setTab]=useState<'ข้อมูลร้านค้า'|'เอกสารแนบ'|'ประวัติ'>('ข้อมูลร้านค้า')
  const [notice,setNotice]=useState('')
  const filtered=useMemo(()=>applications.filter(item=>`${item.shop} ${item.owner} ${item.email} ${item.phone}`.toLowerCase().includes(query.trim().toLowerCase())&&(status==='ทั้งหมด'||item.status===status)&&(category==='ทั้งหมด'||item.category===category)&&(province==='ทั้งหมด'||item.province===province)),[query,status,category,province])
  const selected=applications.find(item=>item.id===selectedId)??applications[0]

  if(loading)return <main className="auth-loading">กำลังตรวจสอบสิทธิ์ผู้ดูแลระบบ...</main>
  if(!user){window.location.replace('/login');return null}
  if(user.role!=='ADMIN'){window.location.replace('/dashboard');return null}

  const notify=(text:string)=>{setNotice(text);window.setTimeout(()=>setNotice(''),1900)}
  const signOut=async()=>{await logout();window.location.replace('/')}
  const reset=()=>{setQuery('');setStatus('ทั้งหมด');setCategory('ทั้งหมด');setProvince('ทั้งหมด')}
  const toggleRow=(id:number)=>setSelectedRows(rows=>rows.includes(id)?rows.filter(row=>row!==id):[...rows,id])

  return <main className="admin-shell applications-shell">
    <header className="admin-topbar"><AdminBrand/><form onSubmit={event=>{event.preventDefault();notify('ค้นหาในระบบแล้ว')}}><button aria-label="ค้นหา"><Search/></button><input aria-label="ค้นหาในระบบ" placeholder="ค้นหาร้านค้า ผู้ใช้ ใบสมัคร หรือคำสั่งซื้อ..."/></form><span className="topbar-spacer"/><button className="admin-bell" aria-label="การแจ้งเตือน"><Bell/><i>12</i></button><div className="admin-profile"><span>{user.firstName.charAt(0).toUpperCase()}</span><p><b>{user.email}</b><small>Administrator</small></p><ChevronDown/></div></header>
    <aside className="admin-sidebar applications-sidebar"><a href="/admin"><Home/>หน้าแรก</a>{navGroups.map(group=><section key={group.label}><h3>{group.label}</h3>{group.items.map(([Icon,label,badge])=>label==='ร้านค้า'?<a href="/admin/stores" key={label}><Icon/>{label}</a>:label==='ใบสมัครร้านค้า'?<a className="active" href="/admin/store-applications" key={label}><Icon/>{label}<i>{badge}</i></a>:label==='หมวดหมู่สินค้า'?<a href="/admin/categories" key={label}><Icon/>{label}</a>:label==='โปรโมชั่น/แคมเปญ'?<a href="/admin/promotions" key={label}><Icon/>{label}</a>:label==='ผู้ใช้งาน'?<a href="/admin/users" key={label}><Icon/>{label}</a>:label==='บทบาทและสิทธิ์'?<a href="/admin/roles" key={label}><Icon/>{label}</a>:<button key={label} onClick={()=>notify(`${label}: อยู่ระหว่างเชื่อมข้อมูล`)}><Icon/>{label}</button>)}</section>)}<button className="admin-logout" onClick={signOut}><LogOut/>ออกจากระบบ</button></aside>

    <div className="admin-content applications-content">
      <section className="applications-heading"><div className="applications-title"><span><FileCheck2/></span><div><h1>ใบสมัครร้านค้า</h1><p>ตรวจสอบและอนุมัติใบสมัครร้านค้าบนแพลตฟอร์ม ThaiShopFun</p><small>“สนับสนุนร้านค้าไทย เพื่อธุรกิจไทยเติบโตไปด้วยกัน”</small></div></div><button className="manual-store" onClick={()=>notify('เปิดแบบฟอร์มเพิ่มร้านค้าด้วยตนเอง')}><Plus/>เพิ่มร้านค้าด้วยตนเอง</button></section>
      <section className="application-kpis">
        <article className="total"><span><Clock3/></span><div><b>ใบสมัครทั้งหมด</b><strong>18 <em>↑ 80%</em></strong><small>จากเดือนก่อน</small></div></article>
        <article className="review"><span><span className="hourglass">⌛</span></span><div><b>รอการตรวจสอบ</b><strong>12 <em>↑ 33%</em></strong><small>คิดเป็น 66.7%</small></div></article>
        <article className="approved"><span><Check/></span><div><b>อนุมัติแล้ว</b><strong>4 <em>↑ 100%</em></strong><small>คิดเป็น 22.2%</small></div></article>
        <article className="rejected"><span><CircleX/></span><div><b>ปฏิเสธแล้ว</b><strong>2 <em>↓ 33%</em></strong><small>คิดเป็น 11.1%</small></div></article>
      </section>
      <section className="application-filters"><label className="application-search"><Search/><input value={query} onChange={event=>setQuery(event.target.value)} placeholder="ค้นหาร้านค้า (ชื่อร้าน, เจ้าของ, อีเมล, เบอร์โทร...)" aria-label="ค้นหาใบสมัคร"/></label><label><span>สถานะ</span><select value={status} onChange={event=>setStatus(event.target.value)}><option>ทั้งหมด</option><option>รอการตรวจสอบ</option><option>อนุมัติแล้ว</option><option>ปฏิเสธแล้ว</option></select></label><label><span>หมวดหมู่</span><select value={category} onChange={event=>setCategory(event.target.value)}><option>ทั้งหมด</option>{[...new Set(applications.map(item=>item.category))].map(value=><option key={value}>{value}</option>)}</select></label><label><span>จังหวัด</span><select value={province} onChange={event=>setProvince(event.target.value)}><option>ทั้งหมด</option>{[...new Set(applications.map(item=>item.province))].map(value=><option key={value}>{value}</option>)}</select></label><label className="date-filter"><span>ช่วงวันที่สมัคร</span><button onClick={()=>notify('เปิดปฏิทินเลือกช่วงวันที่')}><CalendarDays/>เลือกช่วงวันที่</button></label><button className="filter-search" onClick={()=>notify(`พบ ${filtered.length} ใบสมัคร`)}>ค้นหา</button><button className="filter-reset" onClick={reset}>รีเซ็ต</button></section>
      <section className="applications-table-card"><header><h2>รายการใบสมัครร้านค้า (18 รายการ)</h2></header><div className="applications-table-scroll"><div className="applications-table"><div className="applications-tr applications-th"><input type="checkbox" aria-label="เลือกทั้งหมด" checked={filtered.length>0&&selectedRows.length===filtered.length} onChange={()=>setSelectedRows(selectedRows.length===filtered.length?[]:filtered.map(item=>item.id))}/><b>#</b><b>ชื่อร้านค้า</b><b>เจ้าของร้าน</b><b>จังหวัด</b><b>หมวดหมู่</b><b>วันที่สมัคร</b><b>สถานะ</b><b>การดำเนินการ</b><span/></div>{filtered.map(item=><div className={`applications-tr ${selectedId===item.id?'selected':''}`} key={item.id} onClick={()=>setSelectedId(item.id)}><input type="checkbox" aria-label={`เลือก ${item.shop}`} checked={selectedRows.includes(item.id)} onClick={event=>event.stopPropagation()} onChange={()=>toggleRow(item.id)}/><span>{item.id}</span><div className="application-shop"><i className={item.logoClass}>{item.logo}</i><b>{item.shop}</b></div><p><b>{item.owner}</b><span>{item.phone}</span></p><span>{item.province}</span><span><i className={`application-category ${item.categoryClass}`}>{item.category}</i></span><span className="applied-at">{item.appliedAt}</span><span><i className={`application-status ${item.status==='รอการตรวจสอบ'?'pending':item.status==='อนุมัติแล้ว'?'approved':'rejected'}`}><small/>{item.status}</i></span><button className="review-button" onClick={event=>{event.stopPropagation();setSelectedId(item.id)}}>{item.status==='รอการตรวจสอบ'?'ตรวจสอบ':'ดูรายละเอียด'}</button><button className="more-button" aria-label={`เมนู ${item.shop}`} onClick={event=>{event.stopPropagation();notify(`เมนูเพิ่มเติม: ${item.shop}`)}}><MoreVertical/></button></div>)}{filtered.length===0&&<div className="applications-empty">ไม่พบใบสมัครที่ตรงกับตัวกรอง</div>}</div></div><footer><div>แสดง <select aria-label="จำนวนรายการต่อหน้า"><option>10</option><option>25</option></select> รายการต่อหน้า</div><span>1 - {filtered.length} จาก 18 รายการ</span><nav aria-label="แบ่งหน้า"><button disabled><ChevronLeft/></button><button className="current">1</button><button onClick={()=>notify('เปิดหน้าที่ 2')}>2</button><button onClick={()=>notify('เปิดหน้าถัดไป')}><ChevronRight/></button></nav></footer></section>
    </div>

    <aside className="application-detail">
      <header><h2>รายละเอียดใบสมัคร</h2><button aria-label="ปิดรายละเอียด" onClick={()=>notify('เลือกใบสมัครอื่นเพื่อดูรายละเอียด')}><X/></button></header>
      <section className="detail-summary"><i className={selected.logoClass}>{selected.logo}</i><div><b>{selected.shop}</b><span className={`application-status ${selected.status==='รอการตรวจสอบ'?'pending':selected.status==='อนุมัติแล้ว'?'approved':'rejected'}`}><small/>{selected.status}</span><p>สมัครเมื่อ {selected.appliedAt}</p></div></section>
      <nav className="detail-tabs">{(['ข้อมูลร้านค้า','เอกสารแนบ','ประวัติ'] as const).map(value=><button className={tab===value?'active':''} onClick={()=>setTab(value)} key={value}>{value}</button>)}</nav>
      {tab==='ข้อมูลร้านค้า'?<div className="detail-body"><dl><div><dt><Home/>ชื่อร้านค้า</dt><dd>{selected.shop}</dd></div><div><dt><UsersRound/>เจ้าของร้าน</dt><dd>{selected.owner}</dd></div><div><dt><Mail/>อีเมล</dt><dd>{selected.email}</dd></div><div><dt><Phone/>เบอร์โทร</dt><dd>{selected.phone}</dd></div><div><dt><MapPin/>จังหวัด</dt><dd>{selected.province}</dd></div><div><dt><MapPin/>ที่อยู่</dt><dd>{selected.address}</dd></div><div><dt><FileText/>หมวดหมู่สินค้า</dt><dd>{selected.category}</dd></div><div><dt><LockKeyhole/>คำอธิบายร้าน</dt><dd>{selected.description}</dd></div></dl><div className="social-row"><b><Building2/>ช่องทางโซเชียล</b><p><span><MessageCircle/>LINE</span><a>@baansuan</a></p><p><span><Facebook/>Facebook</span><a>บ้านสวนคุณต้น</a></p><p><span><Instagram/>Instagram</span><a>baansuan_ton</a></p></div></div>:tab==='เอกสารแนบ'?<div className="detail-documents"><p><FileCheck2/><span><b>สำเนาบัตรประชาชน</b><small>identity-card.pdf · 1.2 MB</small></span><Check/></p><p><FileCheck2/><span><b>สำเนาบัญชีธนาคาร</b><small>bank-book.pdf · 860 KB</small></span><Check/></p><p><FileCheck2/><span><b>หลักฐานที่อยู่ร้าน</b><small>shop-address.pdf · 2.1 MB</small></span><Check/></p></div>:<div className="detail-history"><p><Clock3/><span><b>ส่งใบสมัครแล้ว</b><small>{selected.appliedAt}</small></span></p><p><FileCheck2/><span><b>เอกสารครบถ้วน</b><small>ระบบตรวจสอบอัตโนมัติ</small></span></p></div>}
      <footer><div><button className="approve" onClick={()=>notify(`อนุมัติร้าน ${selected.shop} แล้ว`)}><Check/>อนุมัติร้านค้า</button><button className="reject" onClick={()=>notify(`เปิดแบบฟอร์มปฏิเสธ ${selected.shop}`)}><CircleX/>ปฏิเสธ</button></div><button className="contact" onClick={()=>notify(`กำลังเปิดช่องทางติดต่อ ${selected.owner}`)}>ติดต่อเจ้าของร้าน</button></footer>
    </aside>
    {notice&&<div className="admin-toast" role="status">{notice}</div>}
  </main>
}
