import { useEffect, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  BarChart3, Bell, Check, ChevronDown, ChevronLeft, ChevronRight,
  CircleDollarSign, CircleX, ClipboardList, Clock3, Download, FileCheck2,
  FileText, Headphones, Home, LogOut, Megaphone, MoreVertical, Plus,
  ReceiptText, Search, Settings, ShoppingBag, SlidersHorizontal, Store,
  Tags, TrendingUp, UserCog, UsersRound, WalletCards,
} from 'lucide-react'
import AdminHeader from '../components/AdminHeader'
import AdminSidebar from '../components/AdminSidebar'
import { useAuth } from '../contexts/AuthContext'
import { usePermissions } from '../hooks/usePermissions'
import { adminApplicationApi, ApiError, mediaUrl, type AdminStoreApplication } from '../services/api'
import '../styles/admin-dashboard.css'
import '../styles/admin-stores.css'

type StoreStatus = 'ใช้งาน' | 'รออนุมัติ' | 'ถูกระงับ' | 'ปฏิเสธ'
type StoreRecord = {
  id: number
  logo: string
  logoUrl: string | null
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
  appeal: string
  otherShops: string
}

const statusLabel: Record<AdminStoreApplication['status'], StoreStatus> = {
  APPROVED: 'ใช้งาน',
  PENDING_REVIEW: 'รออนุมัติ',
  SUSPENDED: 'ถูกระงับ',
  REJECTED: 'ปฏิเสธ',
}

function appliedLabel(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  return new Intl.DateTimeFormat('th-TH', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }).format(date)
}

function share(count: number, total: number) {
  if (!total) return '0.0'
  return ((count / total) * 100).toFixed(1)
}

function mapStore(item: AdminStoreApplication): StoreRecord {
  return {
    id: item.shopId,
    logo: item.shopName.trim().charAt(0) || 'ร',
    logoUrl: item.logoUrl || null,
    logoClass: 'garden',
    name: item.shopName,
    handle: item.slug ? `@${item.slug}` : '-',
    products: 0,
    owner: item.ownerName || '-',
    phone: item.phone || '-',
    category: item.category || '-',
    categoryClass: 'home',
    province: item.province || '-',
    status: statusLabel[item.status],
    appeal: item.reinstatementMessage || '',
    otherShops: (item.otherShops || []).map((shop) => shop.shopName).join(', '),
    registeredAt: appliedLabel(item.appliedAt),
    sales: 0,
    trend: null,
  }
}

export default function AdminStoresPage(){
  const {user,loading}=useAuth()
  const {can,deniedTitle}=usePermissions()
  // pending/rejected stores open the application review; active stores are suspended, suspended ones restored
  const canManage=(store:StoreRecord)=>store.status==='รออนุมัติ'||store.status==='ปฏิเสธ'?can('PLATFORM_APPLICATION_READ'):can(store.status==='ถูกระงับ'?'PLATFORM_SHOP_RESTORE':'PLATFORM_SHOP_SUSPEND')
  const navigate=useNavigate()
  const [rows,setRows]=useState<StoreRecord[]>([])
  const [loaded,setLoaded]=useState(false)
  const [busyId,setBusyId]=useState<number|null>(null)
  const [query,setQuery]=useState('')
  const [status,setStatus]=useState('ทั้งหมด')
  const [category,setCategory]=useState('ทั้งหมด')
  const [province,setProvince]=useState('ทั้งหมด')
  const [notice,setNotice]=useState('')
  const [selected,setSelected]=useState<number[]>([])
  const [page,setPage]=useState(1)
  const [pageSize,setPageSize]=useState(10)

  useEffect(() => {
    if (!user || user.role !== 'ADMIN') return
    let active = true
    adminApplicationApi.list().then(data => {
      if (active) setRows(data.map(mapStore))
    }).catch(error => {
      if (!active) return
      setRows([])
      setNotice(error instanceof ApiError ? error.message : 'โหลดรายการร้านค้าไม่สำเร็จ')
    }).finally(() => { if (active) setLoaded(true) })
    return () => { active = false }
  }, [user])

  const filtered=useMemo(()=>rows.filter(store=>{
    const haystack=`${store.name} ${store.handle} ${store.owner} ${store.phone}`.toLowerCase()
    return haystack.includes(query.trim().toLowerCase())
      && (status==='ทั้งหมด'||store.status===status)
      && (category==='ทั้งหมด'||store.category===category)
      && (province==='ทั้งหมด'||store.province===province)
  }),[rows,query,status,category,province])
  const pageCount=Math.max(1, Math.ceil(filtered.length / pageSize))
  const safePage=Math.min(page, pageCount)
  const visible=filtered.slice((safePage - 1) * pageSize, safePage * pageSize)
  const activeCount=rows.filter(store=>store.status==='ใช้งาน').length
  const pendingCount=rows.filter(store=>store.status==='รออนุมัติ').length
  const suspendedCount=rows.filter(store=>store.status==='ถูกระงับ').length

  useEffect(() => { setPage(1) }, [query, status, category, province, pageSize])
  useEffect(() => { if (page > pageCount) setPage(pageCount) }, [page, pageCount])

  if(loading||(user?.role==='ADMIN'&&!loaded))return <main className="auth-loading">กำลังตรวจสอบสิทธิ์ผู้ดูแลระบบ...</main>
  if(!user){window.location.replace('/login');return null}
  if(user.role!=='ADMIN'){window.location.replace('/dashboard');return null}

  const notify=(text:string)=>{setNotice(text);window.setTimeout(()=>setNotice(''),1900)}
  const reset=()=>{setQuery('');setStatus('ทั้งหมด');setCategory('ทั้งหมด');setProvince('ทั้งหมด')}
  const toggleAll=()=>setSelected(selected.length===visible.length&&visible.length>0?[]:visible.map(row=>row.id))
  const toggle=(id:number)=>setSelected(items=>items.includes(id)?items.filter(item=>item!==id):[...items,id])
  const replaceStore=(item:AdminStoreApplication)=>{
    const next=mapStore(item)
    setRows(current=>current.map(row=>row.id===next.id?next:row))
  }
  const manage=async(store:StoreRecord)=>{
    if(store.status==='รออนุมัติ'||store.status==='ปฏิเสธ'){
      navigate(`/admin/store-applications?shop=${store.id}`)
      return
    }
    if(busyId)return
    const reactivate=store.status==='ถูกระงับ'
    const appealNote = reactivate && store.appeal ? `\n\nคำร้องปลดระงับ: ${store.appeal}` : ''
    if(!window.confirm(reactivate?`เปิดใช้งานร้าน ${store.name} อีกครั้ง?${appealNote}`:`ระงับร้าน ${store.name}?`))return
    setBusyId(store.id)
    try {
      const updated=reactivate?await adminApplicationApi.reactivate(store.id):await adminApplicationApi.suspend(store.id)
      replaceStore(updated)
      notify(reactivate?`เปิดใช้งานร้าน ${updated.shopName} แล้ว`:`ระงับร้าน ${updated.shopName} แล้ว`)
    } catch (error) {
      notify(error instanceof ApiError ? error.message : 'บันทึกสถานะร้านไม่สำเร็จ')
    } finally {
      setBusyId(null)
    }
  }
  const from=filtered.length?((safePage-1)*pageSize)+1:0
  const to=Math.min(safePage*pageSize, filtered.length)
  const pages=Array.from({length:pageCount},(_,index)=>index+1).filter(number=>number===1||number===pageCount||Math.abs(number-safePage)<=2)

  return <main className="admin-shell stores-shell">
    <AdminHeader user={user} />

    <AdminSidebar active="stores" className="stores-sidebar"/>

    <div className="admin-content stores-content">
      <section className="stores-heading">
        <div className="stores-title"><span><Store/></span><div><h1>ร้านค้า</h1><p>จัดการร้านค้าทั้งหมดบนแพลตฟอร์ม ThaiShopFun</p></div></div>
        <button className="add-store" onClick={()=>notify('เปิดแบบฟอร์มเพิ่มร้านค้า')}><Plus/>เพิ่มร้านค้า</button>
      </section>

      <section className="store-kpis" aria-label="ภาพรวมร้านค้า">
        <article className="all"><span><Store/></span><div><b>ร้านค้าทั้งหมด</b><strong>{rows.length.toLocaleString('th-TH')}</strong><small>รายการในระบบ</small></div></article>
        <article className="approved"><span><Check/></span><div><b>ร้านค้าที่อนุมัติแล้ว</b><strong>{activeCount.toLocaleString('th-TH')}</strong><small>คิดเป็น {share(activeCount, rows.length)}%</small></div></article>
        <article className="pending"><span><Clock3/></span><div><b>รอการตรวจสอบ</b><strong>{pendingCount.toLocaleString('th-TH')}</strong><small>คิดเป็น {share(pendingCount, rows.length)}%</small></div></article>
        <article className="suspended"><span><CircleX/></span><div><b>ร้านค้าที่ถูกระงับ</b><strong>{suspendedCount.toLocaleString('th-TH')}</strong><small>คิดเป็น {share(suspendedCount, rows.length)}%</small></div></article>
      </section>

      <section className="store-filters">
        <label className="store-search"><Search/><input value={query} onChange={event=>setQuery(event.target.value)} placeholder="ค้นหาร้านค้า (ชื่อร้าน, เจ้าของ, อีเมล, เบอร์โทร...)" aria-label="ค้นหาร้านค้า"/></label>
        <label><span>สถานะร้านค้า</span><select value={status} onChange={event=>setStatus(event.target.value)}><option>ทั้งหมด</option><option>ใช้งาน</option><option>รออนุมัติ</option><option>ถูกระงับ</option><option>ปฏิเสธ</option></select></label>
        <label><span>หมวดหมู่</span><select value={category} onChange={event=>setCategory(event.target.value)}><option>ทั้งหมด</option>{[...new Set(rows.map(row=>row.category))].map(value=><option key={value}>{value}</option>)}</select></label>
        <label><span>จังหวัด</span><select value={province} onChange={event=>setProvince(event.target.value)}><option>ทั้งหมด</option>{[...new Set(rows.map(row=>row.province))].map(value=><option key={value}>{value}</option>)}</select></label>
        <button className="reset-filter" onClick={reset}>รีเซ็ต</button>
        <button className="search-filter" onClick={()=>notify(`พบ ${filtered.length} ร้านค้า`)}>ค้นหา</button>
      </section>

      <section className="stores-table-card">
        <header><h2>รายการร้านค้า ({rows.length.toLocaleString('th-TH')} รายการ)</h2><div><button onClick={()=>notify('เตรียมส่งออกข้อมูลร้านค้า')}><Download/>ส่งออกข้อมูล<ChevronDown/></button><button onClick={()=>notify('เปิดการจัดการคอลัมน์')}><SlidersHorizontal/>จัดการคอลัมน์</button></div></header>
        <div className="stores-table-scroll"><div className="stores-table">
          <div className="stores-tr stores-th"><input type="checkbox" aria-label="เลือกร้านค้าทั้งหมด" checked={visible.length>0&&visible.every(store=>selected.includes(store.id))} onChange={toggleAll}/><b>#</b><b>ร้านค้า</b><b>เจ้าของร้าน</b><b>หมวดหมู่</b><b>จังหวัด</b><b>สถานะ</b><b>วันที่สมัคร</b><b>ยอดขาย (30 วัน)</b><b>การดำเนินการ</b><span/></div>
          {visible.map(store=><div className="stores-tr" key={store.id}>
            <input type="checkbox" aria-label={`เลือก ${store.name}`} checked={selected.includes(store.id)} onChange={()=>toggle(store.id)}/><span>{store.id}</span>
            <div className="store-identity"><i className={store.logoClass}>{store.logoUrl?<img src={mediaUrl(store.logoUrl)} alt="" />:store.logo}</i><p><b>{store.name}{store.status==='ใช้งาน'&&<small className="verified">✓</small>}</b><span>{store.handle} | {store.products} สินค้า</span>{store.appeal&&<em className="reinstatement-flag">มีคำร้องปลดระงับ</em>}</p></div>
            <p className="owner"><b>{store.owner}</b><span>{store.phone}</span>{store.otherShops ? <small className="owner-siblings">ร้านอื่น: {store.otherShops}</small> : null}</p>
            <span><i className={`category-pill ${store.categoryClass}`}>{store.category}</i></span><span>{store.province}</span>
            <span><i className={`status-pill ${store.status==='ใช้งาน'?'active':store.status==='รออนุมัติ'?'waiting':'blocked'}`}><small/>{store.status}</i></span>
            <span className="registered">{store.registeredAt}</span>
            <span className="sales"><b>฿{store.sales.toLocaleString()}</b>{store.trend!==null&&<em className={store.trend<0?'down':''}>{store.trend>0?'↑':'↓'} {Math.abs(store.trend)}%</em>}</span>
            <button className="manage" disabled={busyId===store.id||!canManage(store)} title={deniedTitle(canManage(store))} onClick={()=>void manage(store)}>{store.status==='รออนุมัติ'||store.status==='ปฏิเสธ'?'ตรวจสอบ':'จัดการ'}</button>
            <button className="more" aria-label={`เมนู ${store.name}`} onClick={()=>notify(`เมนูเพิ่มเติม: ${store.name}`)}><MoreVertical/></button>
          </div>)}
          {filtered.length===0&&<div className="stores-empty">ไม่พบร้านค้าที่ตรงกับตัวกรอง กรุณาลองเปลี่ยนคำค้นหา</div>}
        </div></div>
        <footer><div>แสดง <select aria-label="จำนวนรายการต่อหน้า" value={pageSize} onChange={event=>setPageSize(Number(event.target.value))}><option value={10}>10</option><option value={25}>25</option><option value={50}>50</option></select> รายการต่อหน้า</div><span>{from} - {to} จาก {rows.length.toLocaleString('th-TH')} รายการ</span><nav aria-label="แบ่งหน้า"><button disabled={safePage<=1} onClick={()=>setPage(value=>Math.max(1, value-1))}><ChevronLeft/></button>{pages.map(number=><button className={safePage===number?'current':''} key={number} onClick={()=>setPage(number)}>{number}</button>)}<button disabled={safePage>=pageCount} onClick={()=>setPage(value=>Math.min(pageCount, value+1))}><ChevronRight/></button></nav></footer>
      </section>
    </div>
    {notice&&<div className="admin-toast" role="status">{notice}</div>}
  </main>
}
