import { useMemo, useState } from 'react'
import {
  BarChart3, Bell, Check, ChevronDown, ChevronLeft, ChevronRight,
  CircleDollarSign, CircleX, ClipboardList, Clock3, Download, FileCheck2,
  FileText, Headphones, Home, LogOut, Megaphone, MoreVertical, Plus,
  ReceiptText, Search, Settings, ShoppingBag, SlidersHorizontal, Store,
  Tags, TrendingUp, UserCog, UsersRound, WalletCards,
} from 'lucide-react'
import { useAuth } from '../contexts/AuthContext'
import '../styles/admin-dashboard.css'
import '../styles/admin-stores.css'

type StoreStatus = 'ใช้งาน' | 'รออนุมัติ' | 'ถูกระงับ'
type StoreRecord = {
  id: number
  logo: string
  logoClass: string
  name: string
  handle: string
  products: number
  owner: string
  phone: string
  category: string
  categoryClass: string
  province: string
  status: StoreStatus
  registeredAt: string
  sales: number
  trend: number | null
}

const navGroups = [
  {label:'จัดการร้านค้า',items:[[Store,'ร้านค้า',''],[FileCheck2,'ใบสมัครร้านค้า','18'],[Tags,'หมวดหมู่สินค้า',''],[Megaphone,'โปรโมชั่น/แคมเปญ','']]},
  {label:'จัดการผู้ใช้',items:[[UsersRound,'ผู้ใช้งาน',''],[UserCog,'บทบาทและสิทธิ์','']]},
  {label:'การเงิน',items:[[CircleDollarSign,'รายได้/ค่าธรรมเนียม',''],[WalletCards,'การชำระเงิน',''],[ReceiptText,'ใบแจ้งหนี้','']]},
  {label:'รายงาน',items:[[BarChart3,'รายงานภาพรวม',''],[TrendingUp,'รายงานการขาย',''],[FileText,'รายงานการเงิน','']]},
  {label:'ระบบ',items:[[Settings,'ตั้งค่า','']]},
] as const

const storeRows: StoreRecord[] = [
  {id:1,logo:'👩🏻',logoClass:'somchai',name:'MaeSomchai Official',handle:'@maesomchai',products:120,owner:'คุณสมใจ ใจดี',phone:'081-234-5678',category:'อาหารและเครื่องดื่ม',categoryClass:'food',province:'เชียงใหม่',status:'ใช้งาน',registeredAt:'12 ก.ย. 2026 10:15',sales:82430,trend:12},
  {id:2,logo:'𝓕',logoClass:'fashion',name:'Fashion House',handle:'@fashionhouse',products:358,owner:'คุณธิดา วงศ์สุวรรณ',phone:'090-123-4567',category:'แฟชั่นและเสื้อผ้า',categoryClass:'fashion-cat',province:'กรุงเทพฯ',status:'ใช้งาน',registeredAt:'10 ก.ย. 2026 09:42',sales:64120,trend:9},
  {id:3,logo:'🌱',logoClass:'greenlife',name:'GreenLife Garden',handle:'@greenlife',products:210,owner:'คุณอนันต์ พฤกษา',phone:'089-987-6543',category:'บ้านและไลฟ์สไตล์',categoryClass:'home',province:'นนทบุรี',status:'ใช้งาน',registeredAt:'9 ก.ย. 2026 14:20',sales:52300,trend:8},
  {id:4,logo:'N',logoClass:'narak',name:'Narak Beauty',handle:'@narakbeauty',products:526,owner:'คุณกมลรัตน์ รักสวย',phone:'082-345-6789',category:'ความงามและสุขภาพ',categoryClass:'beauty',province:'ชลบุรี',status:'ใช้งาน',registeredAt:'8 ก.ย. 2026 16:30',sales:48900,trend:15},
  {id:5,logo:'⌂',logoClass:'homeliving',name:'Home&Living',handle:'@homeliving',products:310,owner:'คุณวิทยา ต้นกุล',phone:'080-111-2233',category:'บ้านและไลฟ์สไตล์',categoryClass:'home',province:'ปทุมธานี',status:'รออนุมัติ',registeredAt:'12 ก.ย. 2026 11:18',sales:12450,trend:null},
  {id:6,logo:'ช',logoClass:'thaicraft',name:'ThaiCraft',handle:'@thaicraft',products:85,owner:'คุณกมล ช่างมือ',phone:'092-222-3344',category:'งานฝีมือและของขวัญ',categoryClass:'craft',province:'เชียงราย',status:'ถูกระงับ',registeredAt:'5 ก.ย. 2026 10:05',sales:0,trend:-100},
  {id:7,logo:'🐾',logoClass:'pet',name:'Pet Lover',handle:'@petlover',products:198,owner:'คุณศิรินทร์ บุญนิ่ม',phone:'096-888-7766',category:'สัตว์เลี้ยง',categoryClass:'pet-cat',province:'สมุทรปราการ',status:'ใช้งาน',registeredAt:'3 ก.ย. 2026 13:50',sales:35210,trend:11},
  {id:8,logo:'♥',logoClass:'healthy',name:'Healthy Life',handle:'@healthylife',products:275,owner:'คุณปัญญา สุขภาพดี',phone:'063-555-8899',category:'สุขภาพและอาหารเสริม',categoryClass:'health',province:'ขอนแก่น',status:'ใช้งาน',registeredAt:'1 ก.ย. 2026 09:12',sales:28760,trend:7},
]

function AdminBrand(){return <a className="admin-brand" href="/"><span><ShoppingBag/><i>✓</i></span><div><b>ThaiShop<span>Fun</span></b><small>Shop Local · Support Thai · Smile Together</small></div></a>}

export default function AdminStoresPage(){
  const {user,loading,logout}=useAuth()
  const [query,setQuery]=useState('')
  const [status,setStatus]=useState('ทั้งหมด')
  const [category,setCategory]=useState('ทั้งหมด')
  const [province,setProvince]=useState('ทั้งหมด')
  const [notice,setNotice]=useState('')
  const [selected,setSelected]=useState<number[]>([])

  const filtered=useMemo(()=>storeRows.filter(store=>{
    const haystack=`${store.name} ${store.handle} ${store.owner} ${store.phone}`.toLowerCase()
    return haystack.includes(query.trim().toLowerCase())
      && (status==='ทั้งหมด'||store.status===status)
      && (category==='ทั้งหมด'||store.category===category)
      && (province==='ทั้งหมด'||store.province===province)
  }),[query,status,category,province])

  if(loading)return <main className="auth-loading">กำลังตรวจสอบสิทธิ์ผู้ดูแลระบบ...</main>
  if(!user){window.location.replace('/login');return null}
  if(user.role!=='ADMIN'){window.location.replace('/dashboard');return null}

  const notify=(text:string)=>{setNotice(text);window.setTimeout(()=>setNotice(''),1900)}
  const signOut=async()=>{await logout();window.location.replace('/')}
  const reset=()=>{setQuery('');setStatus('ทั้งหมด');setCategory('ทั้งหมด');setProvince('ทั้งหมด')}
  const toggleAll=()=>setSelected(selected.length===filtered.length?[]:filtered.map(row=>row.id))
  const toggle=(id:number)=>setSelected(items=>items.includes(id)?items.filter(item=>item!==id):[...items,id])

  return <main className="admin-shell stores-shell">
    <header className="admin-topbar">
      <AdminBrand/>
      <form onSubmit={event=>{event.preventDefault();notify(query?`ค้นหา “${query}” แล้ว`:'กรุณาพิมพ์คำค้นหา')}}><button aria-label="ค้นหา"><Search/></button><input aria-label="ค้นหาในระบบ" placeholder="ค้นหาร้านค้า ผู้ใช้ ใบสมัคร หรือคำสั่งซื้อ..."/></form>
      <span className="topbar-spacer"/>
      <button className="admin-bell" aria-label="การแจ้งเตือน"><Bell/><i>12</i></button>
      <div className="admin-profile"><span>{user.firstName.charAt(0).toUpperCase()}</span><p><b>{user.email}</b><small>Administrator</small></p><ChevronDown/></div>
    </header>

    <aside className="admin-sidebar stores-sidebar">
      <a href="/admin"><Home/>หน้าแรก</a>
      {navGroups.map(group=><section key={group.label}><h3>{group.label}</h3>{group.items.map(([Icon,label,badge])=>label==='ร้านค้า'
        ? <a className="active" href="/admin/stores" key={label}><Icon/>{label}</a>
        : label==='ใบสมัครร้านค้า' ? <a href="/admin/store-applications" key={label}><Icon/>{label}{badge&&<i>{badge}</i>}</a>
        : label==='หมวดหมู่สินค้า' ? <a href="/admin/categories" key={label}><Icon/>{label}</a>
        : label==='โปรโมชั่น/แคมเปญ' ? <a href="/admin/promotions" key={label}><Icon/>{label}</a>
        : label==='ผู้ใช้งาน' ? <a href="/admin/users" key={label}><Icon/>{label}</a>
        : label==='บทบาทและสิทธิ์' ? <a href="/admin/roles" key={label}><Icon/>{label}</a>
        : <button key={label} onClick={()=>notify(`${label}: อยู่ระหว่างเชื่อมข้อมูล`)}><Icon/>{label}{badge&&<i>{badge}</i>}</button>)}</section>)}
      <button className="admin-logout" onClick={signOut}><LogOut/>ออกจากระบบ</button>
    </aside>

    <div className="admin-content stores-content">
      <section className="stores-heading">
        <div className="stores-title"><span><Store/></span><div><h1>ร้านค้า</h1><p>จัดการร้านค้าทั้งหมดบนแพลตฟอร์ม ThaiShopFun</p></div></div>
        <button className="add-store" onClick={()=>notify('เปิดแบบฟอร์มเพิ่มร้านค้า')}><Plus/>เพิ่มร้านค้า</button>
      </section>

      <section className="store-kpis" aria-label="ภาพรวมร้านค้า">
        <article className="all"><span><Store/></span><div><b>ร้านค้าทั้งหมด</b><strong>1,245 <em>↑ 12%</em></strong><small>จากเดือนก่อน</small></div></article>
        <article className="approved"><span><Check/></span><div><b>ร้านค้าที่อนุมัติแล้ว</b><strong>1,182 <em>↑ 14%</em></strong><small>คิดเป็น 94.9%</small></div></article>
        <article className="pending"><span><Clock3/></span><div><b>รอการตรวจสอบ</b><strong>38 <em>↓ 24%</em></strong><small>คิดเป็น 3.1%</small></div></article>
        <article className="suspended"><span><CircleX/></span><div><b>ร้านค้าที่ถูกระงับ</b><strong>25 <em>↓ 8%</em></strong><small>คิดเป็น 2.0%</small></div></article>
      </section>

      <section className="store-filters">
        <label className="store-search"><Search/><input value={query} onChange={event=>setQuery(event.target.value)} placeholder="ค้นหาร้านค้า (ชื่อร้าน, เจ้าของ, อีเมล, เบอร์โทร...)" aria-label="ค้นหาร้านค้า"/></label>
        <label><span>สถานะร้านค้า</span><select value={status} onChange={event=>setStatus(event.target.value)}><option>ทั้งหมด</option><option>ใช้งาน</option><option>รออนุมัติ</option><option>ถูกระงับ</option></select></label>
        <label><span>หมวดหมู่</span><select value={category} onChange={event=>setCategory(event.target.value)}><option>ทั้งหมด</option>{[...new Set(storeRows.map(row=>row.category))].map(value=><option key={value}>{value}</option>)}</select></label>
        <label><span>จังหวัด</span><select value={province} onChange={event=>setProvince(event.target.value)}><option>ทั้งหมด</option>{[...new Set(storeRows.map(row=>row.province))].map(value=><option key={value}>{value}</option>)}</select></label>
        <button className="reset-filter" onClick={reset}>รีเซ็ต</button>
        <button className="search-filter" onClick={()=>notify(`พบ ${filtered.length} ร้านค้า`)}>ค้นหา</button>
      </section>

      <section className="stores-table-card">
        <header><h2>รายการร้านค้า (1,245 รายการ)</h2><div><button onClick={()=>notify('เตรียมส่งออกข้อมูลร้านค้า')}><Download/>ส่งออกข้อมูล<ChevronDown/></button><button onClick={()=>notify('เปิดการจัดการคอลัมน์')}><SlidersHorizontal/>จัดการคอลัมน์</button></div></header>
        <div className="stores-table-scroll"><div className="stores-table">
          <div className="stores-tr stores-th"><input type="checkbox" aria-label="เลือกร้านค้าทั้งหมด" checked={filtered.length>0&&selected.length===filtered.length} onChange={toggleAll}/><b>#</b><b>ร้านค้า</b><b>เจ้าของร้าน</b><b>หมวดหมู่</b><b>จังหวัด</b><b>สถานะ</b><b>วันที่สมัคร</b><b>ยอดขาย (30 วัน)</b><b>การดำเนินการ</b><span/></div>
          {filtered.map(store=><div className="stores-tr" key={store.id}>
            <input type="checkbox" aria-label={`เลือก ${store.name}`} checked={selected.includes(store.id)} onChange={()=>toggle(store.id)}/><span>{store.id}</span>
            <div className="store-identity"><i className={store.logoClass}>{store.logo}</i><p><b>{store.name}{store.id===1&&<small className="verified">✓</small>}</b><span>{store.handle} | {store.products} สินค้า</span></p></div>
            <p className="owner"><b>{store.owner}</b><span>{store.phone}</span></p>
            <span><i className={`category-pill ${store.categoryClass}`}>{store.category}</i></span><span>{store.province}</span>
            <span><i className={`status-pill ${store.status==='ใช้งาน'?'active':store.status==='รออนุมัติ'?'waiting':'blocked'}`}><small/>{store.status}</i></span>
            <span className="registered">{store.registeredAt}</span>
            <span className="sales"><b>฿{store.sales.toLocaleString()}</b>{store.trend!==null&&<em className={store.trend<0?'down':''}>{store.trend>0?'↑':'↓'} {Math.abs(store.trend)}%</em>}</span>
            <button className="manage" onClick={()=>notify(`เปิดข้อมูล ${store.name}`)}>{store.status==='รออนุมัติ'?'ตรวจสอบ':store.status==='ถูกระงับ'?'ดูรายละเอียด':'จัดการ'}</button>
            <button className="more" aria-label={`เมนู ${store.name}`} onClick={()=>notify(`เมนูเพิ่มเติม: ${store.name}`)}><MoreVertical/></button>
          </div>)}
          {filtered.length===0&&<div className="stores-empty">ไม่พบร้านค้าที่ตรงกับตัวกรอง กรุณาลองเปลี่ยนคำค้นหา</div>}
        </div></div>
        <footer><div>แสดง <select aria-label="จำนวนรายการต่อหน้า"><option>10</option><option>25</option><option>50</option></select> รายการต่อหน้า</div><span>1 - {filtered.length} จาก 1,245 รายการ</span><nav aria-label="แบ่งหน้า"><button disabled><ChevronLeft/></button>{[1,2,3,4,5].map(page=><button className={page===1?'current':''} key={page} onClick={()=>notify(`เปิดหน้าที่ ${page}`)}>{page}</button>)}<i>...</i><button onClick={()=>notify('เปิดหน้าที่ 125')}>125</button><button onClick={()=>notify('เปิดหน้าถัดไป')}><ChevronRight/></button></nav></footer>
      </section>
    </div>
    {notice&&<div className="admin-toast" role="status">{notice}</div>}
  </main>
}
