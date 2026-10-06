<<<<<<< HEAD
import { useMemo, useState } from 'react'
import {
  BarChart3, Bell, Building2, CalendarDays, Check, ChevronDown, ChevronLeft,
  ChevronRight, CircleDollarSign, CircleX, Clock3, Facebook, FileCheck2,
  FileText, Home, Instagram, LockKeyhole, LogOut, Mail, MapPin, Megaphone,
  MessageCircle, MoreVertical, Phone, Plus, ReceiptText, Search, Settings,
  ShoppingBag, Store, Tags, TrendingUp, UserCog, UsersRound, WalletCards, X,
} from 'lucide-react'
import { useAuth } from '../contexts/AuthContext'
import '../styles/admin-dashboard.css'
import '../styles/admin-applications.css'

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
=======
import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  Building2, CalendarDays, Check, ChevronLeft,
  ChevronRight, CircleX, Clock3, Download, Facebook, FileCheck2,
  FileText, Home, Instagram, LockKeyhole, Mail, MapPin,
  MessageCircle, MoreVertical, Phone, Plus, Search,
  UsersRound, X,
} from 'lucide-react'
import AdminHeader from '../components/AdminHeader'
import AdminSidebar, { refreshAdminStoreApplications } from '../components/AdminSidebar'
import { useAuth } from '../contexts/AuthContext'
import { adminApplicationApi, ApiError, mediaUrl, type AdminStoreApplication } from '../services/api'
import '../styles/admin-dashboard.css'
import '../styles/admin-applications.css'

type ApplicationStatus = 'รออนุมัติ' | 'ใช้งาน' | 'ปฏิเสธ' | 'ถูกระงับ'
type ApplicationRecord = {
  id:number; logo:string; logoClass:string; logoUrl:string|null; shop:string; owner:string; phone:string;
  province:string; category:string; categoryClass:string; appliedAt:string; updatedAt:string;
  status:ApplicationStatus; email:string; address:string; description:string; website:string;
  lineId:string; facebookUrl:string; instagramUrl:string; tiktokUrl:string;
  documents:{id:string; label:string; filename:string; fileSize:number}[];
  otherShops:string;
}

const statusLabel:Record<AdminStoreApplication['status'], ApplicationStatus> = {
  PENDING_REVIEW:'รออนุมัติ', APPROVED:'ใช้งาน', REJECTED:'ปฏิเสธ', SUSPENDED:'ถูกระงับ',
}

function appliedLabel(value:string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  return new Intl.DateTimeFormat('th-TH', {day:'numeric', month:'short', year:'numeric', hour:'2-digit', minute:'2-digit'}).format(date)
}

function fileSize(bytes:number) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

function share(count:number, total:number) {
  if (!total) return '0.0'
  return ((count / total) * 100).toFixed(1)
}

function socialLink(value: string) {
  const text = value?.trim() || '-'
  if (text === '-') return { href: undefined, label: '-' }
  if (text.startsWith('http://') || text.startsWith('https://')) return { href: text, label: text }
  if (text.startsWith('@')) return { href: undefined, label: text }
  return { href: `https://${text}`, label: text }
}

function mapApplication(item:AdminStoreApplication):ApplicationRecord {
  return {
    id:item.shopId,
    logo:item.shopName.trim().charAt(0) || 'ร',
    logoClass:'garden',
    logoUrl:item.logoUrl || null,
    shop:item.shopName,
    owner:item.ownerName || '-',
    phone:item.phone || '-',
    province:item.province || '-',
    category:item.category || '-',
    categoryClass:'garden-cat',
    appliedAt:appliedLabel(item.appliedAt),
    updatedAt:appliedLabel(item.updatedAt),
    status:statusLabel[item.status],
    email:item.email || '-',
    address:item.address || '-',
    description:item.description || '-',
    website:item.website || '',
    lineId:item.lineId || '',
    facebookUrl:item.facebookUrl || '',
    instagramUrl:item.instagramUrl || '',
    tiktokUrl:item.tiktokUrl || '',
    documents:item.documents.map(document => ({id:document.id, label:document.label, filename:document.filename, fileSize:document.fileSize})),
    otherShops:(item.otherShops || []).map(shop => shop.shopName).join(', '),
  }
}

function statusPillClass(status:ApplicationStatus) {
  if (status === 'ใช้งาน') return 'active'
  if (status === 'รออนุมัติ') return 'waiting'
  if (status === 'ถูกระงับ') return 'blocked'
  return 'rejected'
}

function pageNumbers(current:number, count:number) {
  if (count <= 7) return Array.from({length:count}, (_, index) => index + 1)
  const start = Math.max(1, Math.min(current, count - 4))
  return Array.from({length:5}, (_, index) => start + index)
}

function ShopMark({item}:{item:ApplicationRecord}) {
  return <i className={item.logoClass}>{item.logoUrl ? <img src={mediaUrl(item.logoUrl)} alt="" /> : item.logo}</i>
}

export default function AdminApplicationsPage(){
  const {user,loading}=useAuth()
  const [params] = useSearchParams()
  const [records,setRecords]=useState<ApplicationRecord[]>([])
  const [loaded,setLoaded]=useState(false)
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
  const [query,setQuery]=useState('')
  const [status,setStatus]=useState('ทั้งหมด')
  const [category,setCategory]=useState('ทั้งหมด')
  const [province,setProvince]=useState('ทั้งหมด')
<<<<<<< HEAD
  const [selectedId,setSelectedId]=useState(1)
  const [selectedRows,setSelectedRows]=useState<number[]>([])
  const [tab,setTab]=useState<'ข้อมูลร้านค้า'|'เอกสารแนบ'|'ประวัติ'>('ข้อมูลร้านค้า')
  const [notice,setNotice]=useState('')
  const filtered=useMemo(()=>applications.filter(item=>`${item.shop} ${item.owner} ${item.email} ${item.phone}`.toLowerCase().includes(query.trim().toLowerCase())&&(status==='ทั้งหมด'||item.status===status)&&(category==='ทั้งหมด'||item.category===category)&&(province==='ทั้งหมด'||item.province===province)),[query,status,category,province])
  const selected=applications.find(item=>item.id===selectedId)??applications[0]

  if(loading)return <main className="auth-loading">กำลังตรวจสอบสิทธิ์ผู้ดูแลระบบ...</main>
=======
  const [selectedId,setSelectedId]=useState<number|null>(null)
  const [selectedRows,setSelectedRows]=useState<number[]>([])
  const [tab,setTab]=useState<'ข้อมูลร้านค้า'|'เอกสารแนบ'|'ประวัติ'>('ข้อมูลร้านค้า')
  const [notice,setNotice]=useState('')
  const [page,setPage]=useState(1)
  const [pageSize,setPageSize]=useState(10)
  const [busy,setBusy]=useState(false)
  const filtered=useMemo(()=>records.filter(item=>`${item.shop} ${item.owner} ${item.email} ${item.phone}`.toLowerCase().includes(query.trim().toLowerCase())&&(status==='ทั้งหมด'||item.status===status)&&(category==='ทั้งหมด'||item.category===category)&&(province==='ทั้งหมด'||item.province===province)),[records,query,status,category,province])
  const pageCount=Math.max(1, Math.ceil(filtered.length / pageSize))
  const safePage=Math.min(page, pageCount)
  const visible=filtered.slice((safePage - 1) * pageSize, safePage * pageSize)
  const canSelect=(item:ApplicationRecord)=>item.status==='รออนุมัติ'
  const pageTargets=visible.filter(item=>item.status==='รออนุมัติ')
  const selected=records.find(item=>item.id===selectedId)
  const pending=records.filter(item=>item.status==='รออนุมัติ').length
  const approved=records.filter(item=>item.status==='ใช้งาน').length
  const rejected=records.filter(item=>item.status==='ปฏิเสธ').length
  useEffect(() => {
    if (!user || user.role !== 'ADMIN') return
    let active = true
    adminApplicationApi.list().then(rows => {
      if (!active) return
      const mapped = rows.map(mapApplication)
      setRecords(mapped)
      setSelectedId(current => current ?? mapped[0]?.id ?? null)
    }).catch(error => {
      if (!active) return
      setRecords([])
      setNotice(error instanceof ApiError ? error.message : 'โหลดใบสมัครไม่สำเร็จ')
    }).finally(() => { if (active) setLoaded(true) })
    return () => { active = false }
  }, [user])

  useEffect(() => { setPage(1) }, [query, status, category, province, pageSize])
  useEffect(() => { if (page > pageCount) setPage(pageCount) }, [page, pageCount])
  useEffect(() => {
    const shop = Number(params.get('shop'))
    if (shop) setSelectedId(shop)
    const keyword = params.get('q')
    if (keyword != null) setQuery(keyword)
  }, [params])

  if(loading||(user?.role==='ADMIN'&&!loaded))return <main className="auth-loading">กำลังตรวจสอบสิทธิ์ผู้ดูแลระบบ...</main>
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
  if(!user){window.location.replace('/login');return null}
  if(user.role!=='ADMIN'){window.location.replace('/dashboard');return null}

  const notify=(text:string)=>{setNotice(text);window.setTimeout(()=>setNotice(''),1900)}
<<<<<<< HEAD
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
=======
  const reset=()=>{setQuery('');setStatus('ทั้งหมด');setCategory('ทั้งหมด');setProvince('ทั้งหมด')}
  const toggleRow=(item:ApplicationRecord)=>{
    if(selectedRows.includes(item.id)){setSelectedRows(rows=>rows.filter(id=>id!==item.id));return}
    if(!canSelect(item))return
    setSelectedRows(rows=>[...rows,item.id])
  }
  const togglePage=()=>{
    const ids=pageTargets.map(item=>item.id)
    if(!ids.length)return
    const allOn=ids.every(id=>selectedRows.includes(id))
    setSelectedRows(rows=>allOn?rows.filter(id=>!ids.includes(id)):[...new Set([...rows,...ids])])
  }
  const exportExcel=async()=>{
    try { await adminApplicationApi.exportExcel(); notify('ส่งออกรายการเป็น Excel แล้ว') }
    catch (error) { notify(error instanceof ApiError ? error.message : 'ส่งออก Excel ไม่สำเร็จ') }
  }
  const downloadDocument=async(documentId:string, filename:string)=>{
    if(!selected)return
    try { await adminApplicationApi.downloadDocument(selected.id, documentId, filename) }
    catch (error) { notify(error instanceof ApiError ? error.message : 'ดาวน์โหลดเอกสารไม่สำเร็จ') }
  }
  const replaceRecord=(item:AdminStoreApplication)=>{
    const next=mapApplication(item)
    setRecords(rows=>rows.map(row=>row.id===next.id?next:row))
    setSelectedId(next.id)
  }
  const decide=async(action:'approve'|'reject')=>{
    if(!selected||busy||selected.status!=='รออนุมัติ')return
    setBusy(true)
    try {
      const updated=action==='approve'
        ? await adminApplicationApi.approve(selected.id)
        : await adminApplicationApi.reject(selected.id)
      replaceRecord(updated)
      refreshAdminStoreApplications()
      notify(action==='approve'?`อนุมัติร้าน ${updated.shopName} แล้ว`:`ปฏิเสธร้าน ${updated.shopName} แล้ว`)
    } catch (error) {
      notify(error instanceof ApiError ? error.message : 'บันทึกสถานะไม่สำเร็จ')
    } finally {
      setBusy(false)
    }
  }
  const runBulk=async(action:'approve'|'reject')=>{
    if(!selectedRows.length||busy)return
    setBusy(true)
    try {
      const result=await adminApplicationApi.bulk(action, selectedRows)
      if(result.applications.length){
        const mapped=result.applications.map(mapApplication)
        setRecords(rows=>rows.map(row=>mapped.find(next=>next.id===row.id)??row))
      }
      setSelectedRows([])
      refreshAdminStoreApplications()
      const verb=action==='approve'?'อนุมัติ':'ปฏิเสธ'
      notify(result.updated?`${verb} ${result.updated} ร้านแล้ว`:`ไม่มีร้านที่${verb}ได้จากรายการที่เลือก`)
    } catch (error) {
      notify(error instanceof ApiError ? error.message : 'บันทึกสถานะไม่สำเร็จ')
    } finally {
      setBusy(false)
    }
  }
  const from=filtered.length?((safePage-1)*pageSize)+1:0
  const to=Math.min(safePage*pageSize, filtered.length)
  const pages=pageNumbers(safePage, pageCount)

  return <main className="admin-shell applications-shell">
    <AdminHeader user={user} query={query} onSearch={setQuery} />
    <AdminSidebar active="applications" className="applications-sidebar"/>

    <div className="admin-content applications-content">
      <section className="applications-heading"><div className="applications-title"><span><FileCheck2/></span><div><h1>ใบสมัครร้านค้า</h1><p>ตรวจสอบและอนุมัติใบสมัครร้านค้าบนแพลตฟอร์ม ThaiShopFun</p><small>“สนับสนุนร้านค้าไทย เพื่อธุรกิจไทยเติบโตไปด้วยกัน”</small></div></div><div className="application-tools"><button className="manual-store" onClick={exportExcel}><Download/>ส่งออก Excel</button><button className="manual-store" onClick={()=>notify('เปิดแบบฟอร์มเพิ่มร้านค้าด้วยตนเอง')}><Plus/>เพิ่มร้านค้าด้วยตนเอง</button></div></section>
      <section className="application-kpis">
        <article className="total"><span><Clock3/></span><div><b>ใบสมัครทั้งหมด</b><strong>{records.length} <em>{share(records.length, records.length)}%</em></strong><small>รายการในระบบ</small></div></article>
        <article className="review"><span><span className="hourglass">⌛</span></span><div><b>รออนุมัติ</b><strong>{pending} <em>{share(pending, records.length)}%</em></strong><small>คิดเป็น {share(pending, records.length)}%</small></div></article>
        <article className="approved"><span><Check/></span><div><b>ใช้งาน</b><strong>{approved} <em>{share(approved, records.length)}%</em></strong><small>คิดเป็น {share(approved, records.length)}%</small></div></article>
        <article className="rejected"><span><CircleX/></span><div><b>ปฏิเสธ</b><strong>{rejected} <em>{share(rejected, records.length)}%</em></strong><small>คิดเป็น {share(rejected, records.length)}%</small></div></article>
      </section>
      <section className="application-filters"><label className="application-search"><Search/><input value={query} onChange={event=>setQuery(event.target.value)} placeholder="ค้นหาร้านค้า (ชื่อร้าน, เจ้าของ, อีเมล, เบอร์โทร...)" aria-label="ค้นหาใบสมัคร"/></label><label><span>สถานะ</span><select value={status} onChange={event=>setStatus(event.target.value)}><option>ทั้งหมด</option><option>ใช้งาน</option><option>รออนุมัติ</option><option>ถูกระงับ</option><option>ปฏิเสธ</option></select></label><label><span>หมวดหมู่</span><select value={category} onChange={event=>setCategory(event.target.value)}><option>ทั้งหมด</option>{[...new Set(records.map(item=>item.category))].map(value=><option key={value}>{value}</option>)}</select></label><label><span>จังหวัด</span><select value={province} onChange={event=>setProvince(event.target.value)}><option>ทั้งหมด</option>{[...new Set(records.map(item=>item.province))].map(value=><option key={value}>{value}</option>)}</select></label><label className="date-filter"><span>ช่วงวันที่สมัคร</span><button onClick={()=>notify('เปิดปฏิทินเลือกช่วงวันที่')}><CalendarDays/>เลือกช่วงวันที่</button></label><button className="filter-search" onClick={()=>notify(`พบ ${filtered.length} ใบสมัคร`)}>ค้นหา</button><button className="filter-reset" onClick={reset}>รีเซ็ต</button></section>
      <section className="applications-table-card"><header><h2>รายการใบสมัครร้านค้า ({records.length} รายการ)</h2>{selectedRows.length>0&&<div className="bulk-actions"><span>เลือก {selectedRows.length} รายการ</span><button className="approve" disabled={busy} onClick={()=>runBulk('approve')}>อนุมัติที่เลือก</button><button className="reject" disabled={busy} onClick={()=>runBulk('reject')}>ปฏิเสธที่เลือก</button></div>}</header><div className="applications-table-scroll"><div className="applications-table"><div className="applications-tr applications-th"><input type="checkbox" aria-label="เลือกทั้งหมด" disabled={pageTargets.length===0} checked={pageTargets.length>0&&pageTargets.every(item=>selectedRows.includes(item.id))} onChange={togglePage}/><b>#</b><b>ชื่อร้านค้า</b><b>เจ้าของร้าน</b><b>จังหวัด</b><b>หมวดหมู่</b><b>วันที่สมัคร</b><b>สถานะ</b><b>การดำเนินการ</b><span/></div>{visible.map((item,index)=><div className={`applications-tr ${selectedId===item.id?'selected':''}`} key={item.id} onClick={()=>setSelectedId(item.id)}><input type="checkbox" aria-label={`เลือก ${item.shop}`} disabled={!selectedRows.includes(item.id)&&!canSelect(item)} checked={selectedRows.includes(item.id)} onClick={event=>event.stopPropagation()} onChange={()=>toggleRow(item)}/><span>{item.id}</span><div className="application-shop"><ShopMark item={item}/><b>{item.shop}</b></div><p><b>{item.owner}</b><span>{item.phone}</span></p><span>{item.province}</span><span><i className={`application-category ${item.categoryClass}`}>{item.category}</i></span><span className="applied-at">{item.appliedAt}</span><span><i className={`status-pill ${statusPillClass(item.status)}`}><small/>{item.status}</i></span><button className="review-button" onClick={event=>{event.stopPropagation();setSelectedId(item.id)}}>{item.status==='รออนุมัติ'?'ตรวจสอบ':'ดูรายละเอียด'}</button><button className="more-button" aria-label={`เมนู ${item.shop}`} onClick={event=>{event.stopPropagation();notify(`เมนูเพิ่มเติม: ${item.shop}`)}}><MoreVertical/></button></div>)}{filtered.length===0&&<div className="applications-empty">ไม่พบใบสมัครที่ตรงกับตัวกรอง</div>}</div></div><footer><div>แสดง <select aria-label="จำนวนรายการต่อหน้า" value={pageSize} onChange={event=>setPageSize(Number(event.target.value))}><option value={10}>10</option><option value={25}>25</option></select> รายการต่อหน้า</div><span>{from} - {to} จาก {filtered.length} รายการ</span><nav aria-label="แบ่งหน้า"><button disabled={safePage<=1} onClick={()=>setPage(value=>Math.max(1, value-1))}><ChevronLeft/></button>{pages.map(number=><button className={safePage===number?'current':''} key={number} onClick={()=>setPage(number)}>{number}</button>)}<button disabled={safePage>=pageCount} onClick={()=>setPage(value=>Math.min(pageCount, value+1))}><ChevronRight/></button></nav></footer></section>
    </div>

    <aside className="application-detail">
      <header><h2>รายละเอียดใบสมัคร</h2><button aria-label="ปิดรายละเอียด" onClick={()=>notify(selected?'เลือกใบสมัครอื่นเพื่อดูรายละเอียด':'ยังไม่มีใบสมัครร้านค้า')}><X/></button></header>
      {selected?<>
      <section className="detail-summary"><ShopMark item={selected}/><div><b>{selected.shop}</b><span className={`status-pill ${statusPillClass(selected.status)}`}><small/>{selected.status}</span><p>สมัครเมื่อ {selected.appliedAt}</p></div></section>
      <nav className="detail-tabs">{(['ข้อมูลร้านค้า','เอกสารแนบ','ประวัติ'] as const).map(value=><button className={tab===value?'active':''} onClick={()=>setTab(value)} key={value}>{value}</button>)}</nav>
      {tab==='ข้อมูลร้านค้า'?<div className="detail-body"><dl><div><dt><Home/>รหัสร้าน</dt><dd>{selected.id}</dd></div><div><dt><Home/>ชื่อร้านค้า</dt><dd>{selected.shop}</dd></div><div><dt><UsersRound/>เจ้าของร้าน</dt><dd>{selected.owner}{selected.otherShops ? <small className="owner-siblings">ร้านอื่นของเจ้าของนี้: {selected.otherShops}</small> : null}</dd></div><div><dt><Mail/>อีเมล</dt><dd>{selected.email}</dd></div><div><dt><Phone/>เบอร์โทร</dt><dd>{selected.phone}</dd></div><div><dt><MapPin/>จังหวัด</dt><dd>{selected.province}</dd></div><div><dt><MapPin/>ที่อยู่</dt><dd>{selected.address}</dd></div><div><dt><FileText/>หมวดหมู่สินค้า</dt><dd>{selected.category}</dd></div><div><dt><LockKeyhole/>คำอธิบายร้าน</dt><dd>{selected.description}</dd></div></dl><div className="social-row"><b><Building2/>ช่องทางติดต่อร้าน</b>{([['เว็บไซต์', selected.website], ['LINE', selected.lineId], ['Facebook', selected.facebookUrl], ['Instagram', selected.instagramUrl], ['TikTok', selected.tiktokUrl]] as const).map(([label, raw]) => { const link = socialLink(raw || ''); return <p key={label}><span>{label==='LINE'?<MessageCircle/>:label==='Facebook'?<Facebook/>:label==='Instagram'?<Instagram/>:null}{label}</span>{link.href ? <a href={link.href} target="_blank" rel="noreferrer">{link.label}</a> : <a>{link.label}</a>}</p> })}</div></div>:tab==='เอกสารแนบ'?<div className="detail-documents">{selected.documents.length?selected.documents.map(document=><p key={document.id}><FileCheck2/><span><b>{document.label}</b><small>{document.filename} · {fileSize(document.fileSize)}</small></span><button type="button" className="document-download" onClick={()=>downloadDocument(document.id, document.filename)}><Download/>ดาวน์โหลด</button></p>):<p><FileText/><span><b>ยังไม่มีเอกสารแนบ</b><small>-</small></span></p>}</div>:<div className="detail-history"><p><Clock3/><span><b>ส่งใบสมัครแล้ว</b><small>{selected.appliedAt}</small></span></p>{selected.status==='ใช้งาน'&&<p><FileCheck2/><span><b>ร้านใช้งานอยู่</b><small>{selected.updatedAt}</small></span></p>}{selected.status==='ปฏิเสธ'&&<p><CircleX/><span><b>ปฏิเสธใบสมัคร</b><small>{selected.updatedAt}</small></span></p>}{selected.status==='รออนุมัติ'&&<p><FileCheck2/><span><b>รออนุมัติ</b><small>ยังไม่มีการอนุมัติ</small></span></p>}{selected.status==='ถูกระงับ'&&<p><CircleX/><span><b>ระงับร้านค้าแล้ว</b><small>{selected.updatedAt}</small></span></p>}</div>}
      <footer><div>{selected.status==='รออนุมัติ'&&<><button className="approve" disabled={busy} onClick={()=>decide('approve')}><Check/>อนุมัติร้านค้า</button><button className="reject" disabled={busy} onClick={()=>decide('reject')}><CircleX/>ปฏิเสธ</button></>}</div><button className="contact" onClick={()=>{ if(selected.email&&selected.email!=='-') window.location.href=`mailto:${selected.email}`; else notify('ไม่พบอีเมลเจ้าของร้าน') }}>ติดต่อเจ้าของร้าน</button></footer>
      </>:<div className="detail-body"><p>ยังไม่มีใบสมัครร้านค้า</p></div>}
>>>>>>> 7f730e801d054ab68a85e68be85ba07ef123a2eb
    </aside>
    {notice&&<div className="admin-toast" role="status">{notice}</div>}
  </main>
}
