import { useMemo, useState } from 'react'
import {
  BookOpen, Check, ChevronLeft, ChevronRight, CirclePause, Gift, Grid2X2,
  Megaphone, MoreHorizontal, MoreVertical, Plus, Search, ShoppingBag, Tags,
  TicketPercent, Truck, UserRound, Zap,
} from 'lucide-react'
import AdminHeader from '../components/AdminHeader'
import AdminSidebar from '../components/AdminSidebar'
import AdminSidePanel from '../components/admin/AdminSidePanel'
import AdminSelect from '../components/admin/AdminSelect'
import AdminDateRange from '../components/admin/AdminDateRange'
import { useAuth } from '../contexts/AuthContext'
import { hasPlatformAccess } from '../services/permissions'
import '../styles/admin-dashboard.css'
import '../styles/admin-promotions.css'
import '../styles/admin-platform.css'
import '../styles/admin-form-controls.css'
import '../styles/admin-side-panel.css'

const STATUS_OPTIONS = [
  { value: '', label: 'ทั้งหมด' },
  { value: 'กำลังใช้งาน', label: 'กำลังใช้งาน' },
  { value: 'รอเริ่ม', label: 'รอเริ่ม' },
  { value: 'สิ้นสุดแล้ว', label: 'สิ้นสุดแล้ว' },
] as const

const TYPE_OPTIONS = [
  { value: '', label: 'ทั้งหมด' },
  { value: 'ส่วนลด', label: 'ส่วนลด' },
  { value: 'Flash Sale', label: 'Flash Sale' },
  { value: 'คูปอง', label: 'คูปอง' },
  { value: 'ส่งฟรี', label: 'ส่งฟรี' },
  { value: 'ของแถม', label: 'ของแถม' },
] as const

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

const types=[['ทั้งหมด (24)',Grid2X2],['Flash Sale (6)',Zap],['ส่วนลด (8)',Tags],['คูปอง (5)',TicketPercent],['ส่งฟรี (3)',Truck],['ซื้อครบรับของแถม (2)',Gift],['อื่น ๆ (0)',MoreHorizontal]] as const

export default function AdminPromotionsPage(){
  const {user,loading}=useAuth()
  const [query,setQuery]=useState('')
  const [status,setStatus]=useState('')
  const [type,setType]=useState('')
  const [dateFrom,setDateFrom]=useState('')
  const [dateTo,setDateTo]=useState('')
  const [activeType,setActiveType]=useState('ทั้งหมด (24)')
  const [selected,setSelected]=useState<number[]>([])
  const [pageSize,setPageSize]=useState(10)
  const [notice,setNotice]=useState('')
  const filtered=useMemo(()=>promos.filter(item=>{
    const haystack=`${item.name} ${item.code} ${item.shop}`.toLowerCase()
    if(!haystack.includes(query.trim().toLowerCase())) return false
    if(status&&item.status!==status) return false
    if(type&&item.type!==type) return false
    if(!activeType.startsWith('ทั้งหมด')&&item.type!==activeType.split(' (')[0]) return false
    return true
  }),[query,status,type,activeType])
  if(loading)return <main className="auth-loading">กำลังตรวจสอบสิทธิ์ผู้ดูแลระบบ...</main>
  if(!user){window.location.replace('/login');return null}
  if(!hasPlatformAccess(user)){window.location.replace('/dashboard');return null}
  const notify=(message:string)=>{setNotice(message);window.setTimeout(()=>setNotice(''),1900)}
  const reset=()=>{setQuery('');setStatus('');setType('');setDateFrom('');setDateTo('');setActiveType('ทั้งหมด (24)')}
  const toggle=(id:number)=>setSelected(rows=>rows.includes(id)?rows.filter(row=>row!==id):[...rows,id])
  const visible=filtered.slice(0, pageSize)

  return <main className="admin-shell promotions-shell">
    <AdminHeader user={user} />
    <AdminSidebar active="promotions" className="promotions-sidebar"/>

    <div className="admin-content promotions-content">
      <section className="promotions-heading"><div className="promotions-title"><span><Megaphone/></span><div><h1>โปรโมชั่น/แคมเปญ</h1><p>สร้างและจัดการโปรโมชั่น เพื่อเพิ่มยอดขายให้ร้านค้าในแพลตฟอร์ม ThaiShopFun</p><small>“ส่งเสริมร้านค้าไทย ให้เติบโตไปด้วยกัน”</small></div></div><button className="create-promo" onClick={()=>notify('เปิดแบบฟอร์มสร้างโปรโมชั่นใหม่')}><Plus/>สร้างโปรโมชั่นใหม่</button></section>
      <section className="promotion-kpis"><article className="all"><span><Gift/></span><div><b>โปรโมชั่นทั้งหมด</b><strong>24 <em>↑ 33%</em></strong><small>จากเดือนก่อน</small></div></article><article className="running"><span>▶</span><div><b>กำลังใช้งาน</b><strong>12 <em>↑ 50%</em></strong><small>คิดเป็น 50.0%</small></div></article><article className="waiting"><span><CirclePause/></span><div><b>รอเริ่ม</b><strong>5 <em>-</em></strong><small>คิดเป็น 20.8%</small></div></article><article className="ended"><span><Check/></span><div><b>สิ้นสุดแล้ว</b><strong>7 <em>↓ 22%</em></strong><small>คิดเป็น 29.2%</small></div></article></section>
      <nav className="promotion-types" aria-label="ประเภทโปรโมชั่น">{types.map(([label,Icon])=><button className={activeType===label?'active':''} key={label} onClick={()=>setActiveType(label)}><Icon/>{label}</button>)}</nav>
      <form className="ap-filters promotion-filters" onSubmit={event => { event.preventDefault(); notify(`พบ ${filtered.length} โปรโมชั่น`) }}>
        <div className="ap-filter-search wide">
          <span className="ap-filter-label">ค้นหา</span>
          <label className="ap-search adm-search promotion-search"><Search/><input value={query} onChange={event=>setQuery(event.target.value)} placeholder="ค้นหาโปรโมชั่น (ชื่อกิจกรรม, ร้านค้า, รหัสโปรโมชั่น...)" aria-label="ค้นหาโปรโมชั่น"/></label>
        </div>
        <AdminSelect label="สถานะ" value={status} onChange={setStatus} options={[...STATUS_OPTIONS]} />
        <AdminSelect label="ประเภท" value={type} onChange={setType} options={[...TYPE_OPTIONS]} />
        <AdminDateRange label="ช่วงเวลา" from={dateFrom} to={dateTo} onChange={range => { setDateFrom(range.from); setDateTo(range.to) }} />
        <div className="ap-filter-actions">
          <button type="button" className="ap-ghost promo-reset" onClick={reset}>รีเซ็ต</button>
          <button type="submit" className="ap-primary promo-find">ค้นหา</button>
        </div>
      </form>
      <div className="admin-split admin-split--with-aside">
        <section className="promotion-table-card"><header><h2>รายการโปรโมชั่น (24 รายการ)</h2></header><div className="promotion-table-scroll"><div className="promotion-table"><div className="promotion-tr promotion-th"><input type="checkbox" aria-label="เลือกทั้งหมด" checked={visible.length>0&&selected.length===visible.length} onChange={()=>setSelected(selected.length===visible.length?[]:visible.map(item=>item.id))}/><b>#</b><b>ชื่อโปรโมชั่น</b><b>ประเภท</b><b>ร้านค้า</b><b>ช่วงเวลา</b><b>ส่วนลด/สิทธิพิเศษ</b><b>สถานะ</b><b>เข้าร่วม</b><b>จัดการ</b><span/></div>{visible.map(item=><div className="promotion-tr" key={item.id}><input type="checkbox" aria-label={`เลือก ${item.name}`} checked={selected.includes(item.id)} onChange={()=>toggle(item.id)}/><span>{item.id}</span><div className="promotion-name"><i className={item.artTone}>{item.art}</i><span><b>{item.name}</b><small>{item.code}</small></span></div><i className={`promo-type ${item.type.replace(' ','-')}`}>{item.type}</i><div className="promotion-shop"><i>{item.shopArt}</i><span>{item.shop}</span></div><p>{item.from}<span>{item.to}</span></p><p className="promotion-benefit"><b>{item.benefit}</b>{item.detail&&<span>{item.detail}</span>}</p><span><i className={`promo-status ${item.status==='กำลังใช้งาน'?'running':item.status==='รอเริ่ม'?'waiting':'ended'}`}><small/>{item.status}</i></span><span>{item.joined} ร้าน</span><button className="promo-manage" onClick={()=>notify(`จัดการโปรโมชั่น ${item.name}`)}>จัดการ</button><button className="promo-more" aria-label={`เมนู ${item.name}`} onClick={()=>notify(`เมนูเพิ่มเติม: ${item.name}`)}><MoreVertical/></button></div>)}{filtered.length===0&&<div className="promotion-empty">ไม่พบโปรโมชั่นที่ตรงกับตัวกรอง</div>}</div></div><footer><div>แสดง <AdminSelect compact className="adm-page-size" label="จำนวนรายการต่อหน้า" value={String(pageSize)} onChange={value => setPageSize(Number(value))} options={[{ value: '10', label: '10' }, { value: '25', label: '25' }]} /> รายการต่อหน้า</div><span>1 - {Math.min(visible.length, filtered.length)} จาก {filtered.length} รายการ</span><nav aria-label="แบ่งหน้า"><button disabled><ChevronLeft/></button><button className="current">1</button><button onClick={()=>notify('เปิดหน้าที่ 2')}>2</button><button onClick={()=>notify('เปิดหน้าที่ 3')}>3</button><button onClick={()=>notify('เปิดหน้าถัดไป')}><ChevronRight/></button></nav></footer></section>
        <AdminSidePanel variant="aside" bare className="promotion-rail">
          <section className="promo-how"><h2>สร้างโปรโมชั่นอย่างไร?</h2>{[['1','เลือกประเภทโปรโมชั่น','ส่วนลด, คูปอง, ส่งฟรี, ของแถม ฯลฯ'],['2','ตั้งค่าเงื่อนไขและช่วงเวลา','กำหนดรายละเอียดและร้านค้าที่เข้าร่วม'],['3','ตรวจสอบและเผยแพร่','โปรโมชั่นร้านค้าเริ่มใช้งานได้ทันที']].map(([number,title,detail])=><div key={number}><i>{number}</i><span><b>{title}</b><small>{detail}</small></span></div>)}<button type="button" onClick={()=>notify('เปิดคู่มือการใช้งาน')}><BookOpen/>คู่มือการใช้งาน</button></section>
          <section className="popular-promos"><header><h2>โปรโมชั่นยอดนิยม</h2><button type="button" onClick={()=>notify('แสดงโปรโมชั่นทั้งหมด')}>ดูทั้งหมด →</button></header>{[[Truck,'ส่งฟรีทั่วไทย','เข้าร่วม 96 ร้าน'],[Tags,'ลดแรงกลางเดือน','เข้าร่วม 128 ร้าน'],[Gift,'ซื้อครบรับของแถม','เข้าร่วม 54 ร้าน'],[TicketPercent,'คูปองส่วนลดท้ายเดือน','เข้าร่วม 63 ร้าน'],[UserRound,'รับส่วนลดสมาชิกใหม่','เข้าร่วม 110 ร้าน']].map(([Icon,title,detail],index)=><div className="popular-promo" key={title as string}><b>{index+1}</b><i><Icon/></i><span><strong>{title as string}</strong><small>{detail as string}</small></span></div>)}</section>
          <section className="promo-banner"><div><small>👑</small><h2>เพิ่มยอดขายให้ร้านค้า</h2><p>ด้วยโปรโมชั่นที่ตรงใจลูกค้า<br/>สร้างแคมเปญเลยวันนี้!</p></div><i><ShoppingBag/><b>%</b></i><button type="button" onClick={()=>notify('เปิดแบบฟอร์มสร้างโปรโมชั่นใหม่')}>สร้างโปรโมชั่นใหม่ →</button></section>
        </AdminSidePanel>
      </div>
    </div>
    {notice&&<div className="admin-toast" role="status">{notice}</div>}
  </main>
}
