import { useMemo, useState } from 'react'
import {
  BarChart3, Bell, BookOpen, CalendarDays, Check, ChevronDown, ChevronLeft,
  ChevronRight, CircleDollarSign, CirclePause, FileCheck2, FileText, Gift,
  Grid2X2, Home, LogOut, Megaphone, MoreHorizontal, MoreVertical, Package,
  Plus, ReceiptText, Search, Settings, Shirt, ShoppingBag, Store, Tags,
  TicketPercent, TrendingUp, Truck, UserCog, UserRound, UsersRound,
  WalletCards, Zap,
} from 'lucide-react'
import { useAuth } from '../contexts/AuthContext'
import '../styles/admin-dashboard.css'
import '../styles/admin-promotions.css'

type PromoStatus='กำลังใช้งาน'|'รอเริ่ม'|'สิ้นสุดแล้ว'
type PromoType='ส่วนลด'|'ส่งฟรี'|'ของแถม'|'Flash Sale'|'คูปอง'
type Promo={id:number;name:string;code:string;art:string;artTone:string;type:PromoType;shop:string;shopArt:string;from:string;to:string;benefit:string;detail?:string;status:PromoStatus;joined:number}

const promos:Promo[]=[
  {id:1,name:'ลดแรงกลางเดือน',code:'#PROMO001',art:'👗',artTone:'sale',type:'ส่วนลด',shop:'Fashion House',shopArt:'𝓕',from:'1 ก.ย. 2026',to:'10 ก.ย. 2026',benefit:'ลดสูงสุด 30%',status:'กำลังใช้งาน',joined:128},
  {id:2,name:'ส่งฟรีทั่วไทย',code:'#PROMO002',art:'🚚',artTone:'shipping',type:'ส่งฟรี',shop:'MaeSomchai Official',shopArt:'👩🏻',from:'1 ก.ย. 2026',to:'30 ก.ย. 2026',benefit:'ส่งฟรี',detail:'เมื่อซื้อครบ 299 บาท',status:'กำลังใช้งาน',joined:96},
  {id:3,name:'ซื้อครบรับของแถม',code:'#PROMO003',art:'🎁',artTone:'gift',type:'ของแถม',shop:'Healthy Life',shopArt:'🎁',from:'5 ก.ย. 2026',to:'20 ก.ย. 2026',benefit:'ซื้อครบ 500 บาท',detail:'รับของแถม',status:'กำลังใช้งาน',joined:54},
  {id:4,name:'Flash Sale 9.9',code:'#PROMO004',art:'9.9',artTone:'flash',type:'Flash Sale',shop:'Narak Beauty',shopArt:'♧',from:'9 ก.ย. 2026',to:'9 ก.ย. 2026',benefit:'ลดสูงสุด 50%',status:'รอเริ่ม',joined:87},
  {id:5,name:'คูปองส่วนลดท้ายเดือน',code:'#PROMO005',art:'🧴',artTone:'coupon',type:'คูปอง',shop:'GreenLife Garden',shopArt:'🌱',from:'25 ก.ย. 2026',to:'30 ก.ย. 2026',benefit:'คูปองลด 100 บาท',detail:'(ไม่มีขั้นต่ำ)',status:'กำลังใช้งาน',joined:63},
  {id:6,name:'รับส่วนลดสมาชิกใหม่',code:'#PROMO006',art:'👤',artTone:'member',type:'ส่วนลด',shop:'Home&Living',shopArt:'⌂',from:'1 ก.ย. 2026',to:'31 ต.ค. 2026',benefit:'ลดทันที 15%',status:'กำลังใช้งาน',joined:110},
  {id:7,name:'เทศกาลของขวัญปีใหม่',code:'#PROMO007',art:'🎊',artTone:'festival',type:'ส่วนลด',shop:'ThaiCraft',shopArt:'ช',from:'15 ธ.ค. 2025',to:'15 ม.ค. 2026',benefit:'ลดสูงสุด 40%',status:'สิ้นสุดแล้ว',joined:72},
  {id:8,name:'Summer Hot Deal',code:'#PROMO008',art:'🥘',artTone:'summer',type:'Flash Sale',shop:'Pet Lover',shopArt:'🐻',from:'1 เม.ย. 2026',to:'30 เม.ย. 2026',benefit:'ลดสูงสุด 35%',status:'สิ้นสุดแล้ว',joined:58},
]

const navGroups=[
  {label:'จัดการร้านค้า',items:[[Store,'ร้านค้า',''],[FileCheck2,'ใบสมัครร้านค้า','18'],[Tags,'หมวดหมู่สินค้า',''],[Megaphone,'โปรโมชั่น/แคมเปญ','']]},
  {label:'จัดการผู้ใช้',items:[[UsersRound,'ผู้ใช้งาน',''],[UserCog,'บทบาทและสิทธิ์','']]},
  {label:'การเงิน',items:[[CircleDollarSign,'รายได้/ค่าธรรมเนียม',''],[WalletCards,'การชำระเงิน',''],[ReceiptText,'ใบแจ้งหนี้','']]},
  {label:'รายงาน',items:[[BarChart3,'รายงานภาพรวม',''],[TrendingUp,'รายงานการขาย',''],[FileText,'รายงานการเงิน','']]},
  {label:'ระบบ',items:[[Settings,'ตั้งค่า','']]},
] as const
const types=[['ทั้งหมด (24)',Grid2X2],['Flash Sale (6)',Zap],['ส่วนลด (8)',Tags],['คูปอง (5)',TicketPercent],['ส่งฟรี (3)',Truck],['ซื้อครบรับของแถม (2)',Gift],['อื่น ๆ (0)',MoreHorizontal]] as const

function AdminBrand(){return <a className="admin-brand" href="/"><span><ShoppingBag/><i>✓</i></span><div><b>ThaiShop<span>Fun</span></b><small>Shop Local · Support Thai · Smile Together</small></div></a>}

export default function AdminPromotionsPage(){
  const {user,loading,logout}=useAuth()
  const [query,setQuery]=useState('')
  const [status,setStatus]=useState('ทั้งหมด')
  const [type,setType]=useState('ทั้งหมด')
  const [activeType,setActiveType]=useState('ทั้งหมด (24)')
  const [selected,setSelected]=useState<number[]>([])
  const [notice,setNotice]=useState('')
  const filtered=useMemo(()=>promos.filter(item=>`${item.name} ${item.code} ${item.shop}`.toLowerCase().includes(query.trim().toLowerCase())&&(status==='ทั้งหมด'||item.status===status)&&(type==='ทั้งหมด'||item.type===type)&&(activeType.startsWith('ทั้งหมด')||item.type===activeType.split(' (')[0])),[query,status,type,activeType])
  if(loading)return <main className="auth-loading">กำลังตรวจสอบสิทธิ์ผู้ดูแลระบบ...</main>
  if(!user){window.location.replace('/login');return null}
  if(user.role!=='ADMIN'){window.location.replace('/dashboard');return null}
  const notify=(message:string)=>{setNotice(message);window.setTimeout(()=>setNotice(''),1900)}
  const signOut=async()=>{await logout();window.location.replace('/')}
  const reset=()=>{setQuery('');setStatus('ทั้งหมด');setType('ทั้งหมด');setActiveType('ทั้งหมด (24)')}
  const toggle=(id:number)=>setSelected(rows=>rows.includes(id)?rows.filter(row=>row!==id):[...rows,id])

  return <main className="admin-shell promotions-shell">
    <header className="admin-topbar"><AdminBrand/><form onSubmit={event=>{event.preventDefault();notify('ค้นหาในระบบแล้ว')}}><button aria-label="ค้นหา"><Search/></button><input aria-label="ค้นหาในระบบ" placeholder="ค้นหาร้านค้า, สินค้า, ผู้ใช้, หรือคำสั่งซื้อ..."/></form><span className="topbar-spacer"/><button className="admin-bell" aria-label="การแจ้งเตือน"><Bell/><i>12</i></button><div className="admin-profile"><span>{user.firstName.charAt(0).toUpperCase()}</span><p><b>{user.email}</b><small>Administrator</small></p><ChevronDown/></div></header>
    <aside className="admin-sidebar promotions-sidebar"><a href="/admin"><Home/>หน้าแรก</a>{navGroups.map(group=><section key={group.label}><h3>{group.label}</h3>{group.items.map(([Icon,label,badge])=>label==='ร้านค้า'?<a href="/admin/stores" key={label}><Icon/>{label}</a>:label==='ใบสมัครร้านค้า'?<a href="/admin/store-applications" key={label}><Icon/>{label}<i>{badge}</i></a>:label==='หมวดหมู่สินค้า'?<a href="/admin/categories" key={label}><Icon/>{label}</a>:label==='โปรโมชั่น/แคมเปญ'?<a className="active" href="/admin/promotions" key={label}><Icon/>{label}</a>:label==='ผู้ใช้งาน'?<a href="/admin/users" key={label}><Icon/>{label}</a>:label==='บทบาทและสิทธิ์'?<a href="/admin/roles" key={label}><Icon/>{label}</a>:<button key={label} onClick={()=>notify(`${label}: อยู่ระหว่างเชื่อมข้อมูล`)}><Icon/>{label}</button>)}</section>)}<button className="admin-logout" onClick={signOut}><LogOut/>ออกจากระบบ</button></aside>

    <div className="admin-content promotions-content">
      <section className="promotions-heading"><div className="promotions-title"><span><Megaphone/></span><div><h1>โปรโมชั่น/แคมเปญ</h1><p>สร้างและจัดการโปรโมชั่น เพื่อเพิ่มยอดขายให้ร้านค้าในแพลตฟอร์ม ThaiShopFun</p><small>“ส่งเสริมร้านค้าไทย ให้เติบโตไปด้วยกัน”</small></div></div><button className="create-promo" onClick={()=>notify('เปิดแบบฟอร์มสร้างโปรโมชั่นใหม่')}><Plus/>สร้างโปรโมชั่นใหม่</button></section>
      <section className="promotion-kpis"><article className="all"><span><Gift/></span><div><b>โปรโมชั่นทั้งหมด</b><strong>24 <em>↑ 33%</em></strong><small>จากเดือนก่อน</small></div></article><article className="running"><span>▶</span><div><b>กำลังใช้งาน</b><strong>12 <em>↑ 50%</em></strong><small>คิดเป็น 50.0%</small></div></article><article className="waiting"><span><CirclePause/></span><div><b>รอเริ่ม</b><strong>5 <em>-</em></strong><small>คิดเป็น 20.8%</small></div></article><article className="ended"><span><Check/></span><div><b>สิ้นสุดแล้ว</b><strong>7 <em>↓ 22%</em></strong><small>คิดเป็น 29.2%</small></div></article></section>
      <nav className="promotion-types" aria-label="ประเภทโปรโมชั่น">{types.map(([label,Icon])=><button className={activeType===label?'active':''} key={label} onClick={()=>setActiveType(label)}><Icon/>{label}</button>)}</nav>
      <section className="promotion-filters"><label className="promotion-search"><Search/><input value={query} onChange={event=>setQuery(event.target.value)} placeholder="ค้นหาโปรโมชั่น (ชื่อกิจกรรม, ร้านค้า, รหัสโปรโมชั่น...)" aria-label="ค้นหาโปรโมชั่น"/></label><label><span>สถานะ</span><select value={status} onChange={event=>setStatus(event.target.value)}><option>ทั้งหมด</option><option>กำลังใช้งาน</option><option>รอเริ่ม</option><option>สิ้นสุดแล้ว</option></select></label><label><span>ประเภท</span><select value={type} onChange={event=>setType(event.target.value)}><option>ทั้งหมด</option><option>ส่วนลด</option><option>Flash Sale</option><option>คูปอง</option><option>ส่งฟรี</option><option>ของแถม</option></select></label><label className="promotion-date"><span>ช่วงเวลา</span><button onClick={()=>notify('เปิดปฏิทินเลือกช่วงวันที่')}><CalendarDays/>เลือกช่วงวันที่</button></label><button className="promo-find" onClick={()=>notify(`พบ ${filtered.length} โปรโมชั่น`)}>ค้นหา</button><button className="promo-reset" onClick={reset}>รีเซ็ต</button></section>
      <section className="promotion-table-card"><header><h2>รายการโปรโมชั่น (24 รายการ)</h2></header><div className="promotion-table-scroll"><div className="promotion-table"><div className="promotion-tr promotion-th"><input type="checkbox" aria-label="เลือกทั้งหมด" checked={filtered.length>0&&selected.length===filtered.length} onChange={()=>setSelected(selected.length===filtered.length?[]:filtered.map(item=>item.id))}/><b>#</b><b>ชื่อโปรโมชั่น</b><b>ประเภท</b><b>ร้านค้า</b><b>ช่วงเวลา</b><b>ส่วนลด/สิทธิพิเศษ</b><b>สถานะ</b><b>เข้าร่วม</b><b>จัดการ</b><span/></div>{filtered.map(item=><div className="promotion-tr" key={item.id}><input type="checkbox" aria-label={`เลือก ${item.name}`} checked={selected.includes(item.id)} onChange={()=>toggle(item.id)}/><span>{item.id}</span><div className="promotion-name"><i className={item.artTone}>{item.art}</i><span><b>{item.name}</b><small>{item.code}</small></span></div><i className={`promo-type ${item.type.replace(' ','-')}`}>{item.type}</i><div className="promotion-shop"><i>{item.shopArt}</i><span>{item.shop}</span></div><p>{item.from}<span>{item.to}</span></p><p className="promotion-benefit"><b>{item.benefit}</b>{item.detail&&<span>{item.detail}</span>}</p><span><i className={`promo-status ${item.status==='กำลังใช้งาน'?'running':item.status==='รอเริ่ม'?'waiting':'ended'}`}><small/>{item.status}</i></span><span>{item.joined} ร้าน</span><button className="promo-manage" onClick={()=>notify(`จัดการโปรโมชั่น ${item.name}`)}>จัดการ</button><button className="promo-more" aria-label={`เมนู ${item.name}`} onClick={()=>notify(`เมนูเพิ่มเติม: ${item.name}`)}><MoreVertical/></button></div>)}{filtered.length===0&&<div className="promotion-empty">ไม่พบโปรโมชั่นที่ตรงกับตัวกรอง</div>}</div></div><footer><div>แสดง <select aria-label="จำนวนรายการต่อหน้า"><option>10</option><option>25</option></select> รายการต่อหน้า</div><span>1 - {filtered.length} จาก 24 รายการ</span><nav aria-label="แบ่งหน้า"><button disabled><ChevronLeft/></button><button className="current">1</button><button onClick={()=>notify('เปิดหน้าที่ 2')}>2</button><button onClick={()=>notify('เปิดหน้าที่ 3')}>3</button><button onClick={()=>notify('เปิดหน้าถัดไป')}><ChevronRight/></button></nav></footer></section>
    </div>

    <aside className="promotion-rail"><section className="promo-how"><h2>สร้างโปรโมชั่นอย่างไร?</h2>{[['1','เลือกประเภทโปรโมชั่น','ส่วนลด, คูปอง, ส่งฟรี, ของแถม ฯลฯ'],['2','ตั้งค่าเงื่อนไขและช่วงเวลา','กำหนดรายละเอียดและร้านค้าที่เข้าร่วม'],['3','ตรวจสอบและเผยแพร่','โปรโมชั่นร้านค้าเริ่มใช้งานได้ทันที']].map(([number,title,detail])=><div key={number}><i>{number}</i><span><b>{title}</b><small>{detail}</small></span></div>)}<button onClick={()=>notify('เปิดคู่มือการใช้งาน')}><BookOpen/>คู่มือการใช้งาน</button></section><section className="popular-promos"><header><h2>โปรโมชั่นยอดนิยม</h2><button onClick={()=>notify('แสดงโปรโมชั่นทั้งหมด')}>ดูทั้งหมด →</button></header>{[[Truck,'ส่งฟรีทั่วไทย','เข้าร่วม 96 ร้าน'],[Tags,'ลดแรงกลางเดือน','เข้าร่วม 128 ร้าน'],[Gift,'ซื้อครบรับของแถม','เข้าร่วม 54 ร้าน'],[TicketPercent,'คูปองส่วนลดท้ายเดือน','เข้าร่วม 63 ร้าน'],[UserRound,'รับส่วนลดสมาชิกใหม่','เข้าร่วม 110 ร้าน']].map(([Icon,title,detail],index)=><div className="popular-promo" key={title as string}><b>{index+1}</b><i><Icon/></i><span><strong>{title as string}</strong><small>{detail as string}</small></span></div>)}</section><section className="promo-banner"><div><small>👑</small><h2>เพิ่มยอดขายให้ร้านค้า</h2><p>ด้วยโปรโมชั่นที่ตรงใจลูกค้า<br/>สร้างแคมเปญเลยวันนี้!</p></div><i><ShoppingBag/><b>%</b></i><button onClick={()=>notify('เปิดแบบฟอร์มสร้างโปรโมชั่นใหม่')}>สร้างโปรโมชั่นใหม่ →</button></section></aside>
    {notice&&<div className="admin-toast" role="status">{notice}</div>}
  </main>
}
