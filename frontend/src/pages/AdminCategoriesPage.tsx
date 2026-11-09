import { useEffect, useMemo, useState } from 'react'
import {
  BarChart3, Box, CalendarDays, Car, CheckCircle2, Eye, FileSpreadsheet, FileText,
  Gamepad2, Gift, History, Home, Lightbulb, MoreVertical, Package, PawPrint, Plus,
  Search, Settings, Shirt, SlidersHorizontal, Sparkles, Store, Tags, Trophy, Utensils,
  Download, ChevronRight, CircleX,
} from 'lucide-react'
import AdminHeader from '../components/AdminHeader'
import AdminSidebar from '../components/AdminSidebar'
import AdminSidePanel from '../components/admin/AdminSidePanel'
import AdminDetailDrawer from '../components/admin/AdminDetailDrawer'
import AdminSelect from '../components/admin/AdminSelect'
import { Pager } from '../components/admin/PlatformBits'
import { useAuth } from '../contexts/AuthContext'
import { hasPlatformAccess } from '../services/permissions'
import '../styles/admin-dashboard.css'
import '../styles/admin-categories.css'
import '../styles/admin-platform.css'
import '../styles/admin-form-controls.css'
import '../styles/admin-side-panel.css'

const STATUS_OPTIONS = [
  { value: '', label: 'ทั้งหมด' },
  { value: 'ใช้งาน', label: 'ใช้งาน' },
  { value: 'ซ่อน', label: 'ซ่อน' },
] as const

const SORT_OPTIONS = [
  { value: 'วันที่สร้าง (ใหม่สุด)', label: 'วันที่สร้าง (ใหม่สุด)' },
  { value: 'จำนวนสินค้า (มากสุด)', label: 'จำนวนสินค้า (มากสุด)' },
  { value: 'ชื่อหมวดหมู่', label: 'ชื่อหมวดหมู่' },
] as const

type CategoryTab = 'info' | 'stats' | 'history'
type CategoryRow = {
  id: number
  name: string
  description: string
  products: number
  stores: number
  active: boolean
  created: string
  tone: string
  icon: React.ComponentType<{ size?: number }>
}

const categories: CategoryRow[] = [
  { id: 1, name: 'แฟชั่นและเสื้อผ้า', description: 'เสื้อผ้า รองเท้า และเครื่องประดับ', products: 1240, stores: 320, active: true, created: '1 ส.ค. 2024 10:15', tone: 'pink', icon: Shirt },
  { id: 2, name: 'อาหารและเครื่องดื่ม', description: 'อาหารสด อาหารแห้ง เครื่องดื่ม', products: 980, stores: 285, active: true, created: '1 ส.ค. 2024 10:12', tone: 'orange', icon: Utensils },
  { id: 3, name: 'บ้านและไลฟ์สไตล์', description: 'ของใช้ในบ้าน ตกแต่งบ้าน', products: 856, stores: 236, active: true, created: '28 ก.ค. 2024 16:20', tone: 'blue', icon: Home },
  { id: 4, name: 'ความงามและสุขภาพ', description: 'สกินแคร์ เครื่องสำอาง อุปกรณ์ดูแลสุขภาพ', products: 765, stores: 198, active: true, created: '28 ก.ค. 2024 14:33', tone: 'rose', icon: Sparkles },
  { id: 5, name: 'อิเล็กทรอนิกส์', description: 'มือถือ คอมพิวเตอร์ อุปกรณ์ไอที', products: 642, stores: 176, active: true, created: '25 ก.ค. 2024 09:21', tone: 'indigo', icon: Gamepad2 },
  { id: 6, name: 'แม่และเด็ก', description: 'สินค้าแม่และเด็ก ของเล่นเด็ก', products: 518, stores: 142, active: true, created: '20 ก.ค. 2024 11:10', tone: 'amber', icon: Package },
  { id: 7, name: 'กีฬาและกิจกรรมกลางแจ้ง', description: 'อุปกรณ์กีฬา กิจกรรมกลางแจ้ง', products: 430, stores: 118, active: true, created: '18 ก.ค. 2024 13:45', tone: 'green', icon: Trophy },
  { id: 8, name: 'ยานยนต์และอุปกรณ์', description: 'ชิ้นส่วนรถยนต์ อุปกรณ์ตกแต่ง', products: 388, stores: 105, active: false, created: '15 ก.ค. 2024 09:30', tone: 'navy', icon: Car },
  { id: 9, name: 'สัตว์เลี้ยง', description: 'อาหารสัตว์ อุปกรณ์สัตว์เลี้ยง', products: 362, stores: 98, active: true, created: '12 ก.ค. 2024 10:05', tone: 'red', icon: PawPrint },
  { id: 10, name: 'ของขวัญและงานฝีมือ', description: 'ของขวัญ งานฝีมือ สินค้าทำมือ', products: 258, stores: 76, active: true, created: '10 ก.ค. 2024 15:20', tone: 'magenta', icon: Gift },
]

export default function AdminCategoriesPage() {
  const { user, loading } = useAuth()
  const [query, setQuery] = useState('')
  const [status, setStatus] = useState('')
  const [sort, setSort] = useState('วันที่สร้าง (ใหม่สุด)')
  const [selected, setSelected] = useState<number[]>([])
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(10)
  const [notice, setNotice] = useState('')
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const [tab, setTab] = useState<CategoryTab>('info')

  const filtered = useMemo(
    () => categories
      .filter(item => `${item.name} ${item.description}`.toLowerCase().includes(query.trim().toLowerCase()) && (!status || (status === 'ใช้งาน' ? item.active : !item.active)))
      .sort((a, b) => (sort === 'จำนวนสินค้า (มากสุด)' ? b.products - a.products : sort === 'ชื่อหมวดหมู่' ? a.name.localeCompare(b.name, 'th') : a.id - b.id)),
    [query, status, sort],
  )
  const pageCount = Math.max(1, Math.ceil(filtered.length / pageSize) || 1)
  const safePage = Math.min(page, pageCount - 1)
  useEffect(() => { setPage(0) }, [query, status, sort, pageSize])
  useEffect(() => { if (page > pageCount - 1) setPage(Math.max(0, pageCount - 1)) }, [page, pageCount])

  if (loading) return <main className="auth-loading">กำลังตรวจสอบสิทธิ์ผู้ดูแลระบบ...</main>
  if (!user) { window.location.replace('/login'); return null }
  if (!hasPlatformAccess(user)) { window.location.replace('/dashboard'); return null }

  const notify = (message: string) => { setNotice(message); window.setTimeout(() => setNotice(''), 1900) }
  const toggle = (id: number) => setSelected(rows => rows.includes(id) ? rows.filter(row => row !== id) : [...rows, id])
  const reset = () => { setQuery(''); setStatus(''); setSort('วันที่สร้าง (ใหม่สุด)'); setPage(0) }
  const visible = filtered.slice(safePage * pageSize, (safePage + 1) * pageSize)
  const detail = categories.find(item => item.id === selectedId) ?? null
  const DetailIcon = detail?.icon
  const openDetail = (id: number) => { setSelectedId(id); setTab('info') }

  return (
    <main className="admin-shell category-shell">
      <AdminHeader user={user} />
      <AdminSidebar active="categories" className="category-sidebar" />

      <div className="admin-content category-content">
        <section className="category-heading">
          <div className="category-title">
            <span><Tags /></span>
            <div>
              <h1>หมวดหมู่สินค้า</h1>
              <p>จัดการหมวดหมู่สินค้า บนแพลตฟอร์ม ThaiShopFun</p>
              <small>“จัดหมวดหมู่ให้เป็นระบบ ค้นหาง่าย เพิ่มโอกาสการขาย”</small>
            </div>
          </div>
          <button className="add-category" onClick={() => notify('เปิดแบบฟอร์มเพิ่มหมวดหมู่')}><Plus />เพิ่มหมวดหมู่</button>
        </section>

        <section className="category-kpis">
          <article className="all"><span><Box /></span><div><b>หมวดหมู่ทั้งหมด</b><strong>24 <em>↑ 9%</em></strong><small>จากเดือนก่อน</small></div></article>
          <article className="visible"><span><Eye /></span><div><b>หมวดหมู่ที่ใช้งาน</b><strong>21 <em>↑ 11%</em></strong><small>คิดเป็น 87.5%</small></div></article>
          <article className="hidden"><span>Ⅱ</span><div><b>หมวดหมู่ที่ซ่อน</b><strong>3 <em>- 0%</em></strong><small>คิดเป็น 12.5%</small></div></article>
          <article className="products"><span><BarChart3 /></span><div><b>สินค้าในหมวดหมู่</b><strong>5,842 <em>↑ 18%</em></strong><small>จากเดือนก่อน</small></div></article>
        </section>

        <form className="ap-filters category-filters" onSubmit={event => { event.preventDefault(); notify(`พบ ${filtered.length} หมวดหมู่`) }}>
          <div className="ap-filter-search wide">
            <span className="ap-filter-label">ค้นหา</span>
            <label className="category-search adm-search ap-search"><Search /><input value={query} onChange={event => setQuery(event.target.value)} placeholder="ค้นหาหมวดหมู่ (ชื่อหมวดหมู่, คำอธิบาย...)" aria-label="ค้นหาหมวดหมู่" /></label>
          </div>
          <AdminSelect label="สถานะ" value={status} onChange={setStatus} options={[...STATUS_OPTIONS]} />
          <AdminSelect label="เรียงตาม" value={sort} onChange={setSort} options={[...SORT_OPTIONS]} />
          <div className="ap-filter-actions">
            <button type="button" className="ap-ghost category-reset" onClick={reset}>รีเซ็ต</button>
            <button type="submit" className="ap-primary category-find">ค้นหา</button>
          </div>
        </form>

        <div className="admin-split admin-split--with-aside">
          <section className="category-table-card">
            <header><h2>รายการหมวดหมู่สินค้า (24 รายการ)</h2></header>
            <div className="category-table-scroll">
              <div className="category-table">
                <div className="category-tr category-th">
                  <input type="checkbox" aria-label="เลือกทั้งหมด" checked={visible.length > 0 && selected.length === visible.length} onChange={() => setSelected(selected.length === visible.length ? [] : visible.map(item => item.id))} />
                  <b>#</b><b>ไอคอน</b><b>ชื่อหมวดหมู่</b><b>จำนวนสินค้า</b><b>ร้านค้าที่ใช้งาน</b><b>สถานะ</b><b>วันที่สร้าง</b><b>การดำเนินการ</b><span />
                </div>
                {visible.map(item => {
                  const Icon = item.icon
                  return (
                    <div
                      className={`category-tr${selectedId === item.id ? ' selected' : ''}`}
                      key={item.id}
                      role="button"
                      tabIndex={0}
                      onClick={() => openDetail(item.id)}
                      onKeyDown={event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); openDetail(item.id) } }}
                    >
                      <input type="checkbox" aria-label={`เลือก ${item.name}`} checked={selected.includes(item.id)} onClick={event => event.stopPropagation()} onChange={() => toggle(item.id)} />
                      <span>{item.id}</span>
                      <i className={`category-icon ${item.tone}`}><Icon /></i>
                      <div className="category-name"><b>{item.name}</b><small>{item.description}</small></div>
                      <strong>{item.products.toLocaleString()}</strong>
                      <strong>{item.stores}</strong>
                      <span><i className={`category-status ${item.active ? 'active' : 'hidden'}`}><small />{item.active ? 'ใช้งาน' : 'ซ่อน'}</i></span>
                      <span className="category-date">{item.created}</span>
                      <button className="category-manage" onClick={event => { event.stopPropagation(); openDetail(item.id) }}>จัดการ</button>
                      <button className="category-more" aria-label={`เมนู ${item.name}`} onClick={event => { event.stopPropagation(); notify(`เมนูเพิ่มเติม: ${item.name}`) }}><MoreVertical /></button>
                    </div>
                  )
                })}
                {filtered.length === 0 && <div className="category-empty">ไม่พบหมวดหมู่ที่ตรงกับตัวกรอง</div>}
              </div>
            </div>
            <Pager page={safePage} size={pageSize} totalItems={filtered.length} totalPages={pageCount} itemCount={visible.length} sizeOptions={[10, 25]} onPage={setPage} onSize={next => { setPageSize(next); setPage(0) }} />
          </section>

          <AdminSidePanel variant="aside" bare className="category-tools">
            <section className="popular-categories">
              <header><h2>หมวดหมู่ยอดนิยม</h2><button type="button" onClick={() => notify('แสดงหมวดหมู่ยอดนิยมทั้งหมด')}>ดูทั้งหมด →</button></header>
              <ol>{categories.slice(0, 5).map((item, index) => { const Icon = item.icon; return <li key={item.id}><b>{index + 1}</b><i className={`category-icon ${item.tone}`}><Icon /></i><span><strong>{item.name}</strong><small>{item.products.toLocaleString()} สินค้า</small></span></li> })}</ol>
            </section>
            <section className="extra-tools">
              <h2>เครื่องมือเพิ่มเติม</h2>
              {([[SlidersHorizontal, 'จัดเรียงลำดับหมวดหมู่', 'ลากและวางเพื่อจัดลำดับ'], [FileSpreadsheet, 'นำเข้าหมวดหมู่ (Excel)', 'เพิ่มหมวดหมู่จำนวนมาก'], [Download, 'ส่งออกข้อมูล', 'ดาวน์โหลดรายการหมวดหมู่'], [History, 'ประวัติการเปลี่ยนแปลง', 'ดูบันทึกการแก้ไข'], [Settings, 'ตั้งค่าการแสดงผล', 'จัดการการแสดงผลบนหน้าเว็บไซต์']] as const).map(([Icon, title, detailText], index) => (
                <button key={title} type="button" onClick={() => notify(title)}><i className={`tool-icon t${index}`}><Icon /></i><span><b>{title}</b><small>{detailText}</small></span><ChevronRight /></button>
              ))}
            </section>
            <section className="category-tip">
              <button type="button" aria-label="ปิดคำแนะนำ" onClick={() => notify('ปิดคำแนะนำแล้ว')}>×</button>
              <h2><Lightbulb />คำแนะนำ</h2>
              <p>หมวดหมู่ที่จัดอย่างเป็นระบบ<br />ช่วยให้ลูกค้าค้นหาสินค้าได้ง่ายขึ้น<br />และเพิ่มโอกาสในการขายของร้านค้า</p>
            </section>
          </AdminSidePanel>
        </div>
      </div>

      {detail ? (
        <AdminDetailDrawer
          className="application-drawer category-detail-drawer"
          title={detail.name}
          onClose={() => setSelectedId(null)}
          avatar={DetailIcon ? <DetailIcon /> : undefined}
          avatarClassName={`category-icon ${detail.tone}`}
          meta={
            <>
              <span className={`om-pill app-status-pill ${detail.active ? 'ok' : 'muted'}`}>
                {detail.active ? <CheckCircle2 aria-hidden /> : <CircleX aria-hidden />}
                {detail.active ? 'ใช้งาน' : 'ซ่อน'}
              </span>
              <span className="om-muted">สร้างเมื่อ {detail.created}</span>
            </>
          }
          tabs={[
            { id: 'info', label: 'ข้อมูล', icon: Tags },
            { id: 'stats', label: 'สถิติ', icon: BarChart3 },
            { id: 'history', label: 'ประวัติ', icon: History },
          ]}
          activeTab={tab}
          onTabChange={id => setTab(id as CategoryTab)}
          footerClassName="application-drawer-actions"
          footer={(
            <>
              <div className="application-drawer-actions__secondary">
                <button type="button" className="quiet" onClick={() => notify(`แก้ไขหมวดหมู่ ${detail.name}`)}><Settings aria-hidden /><span>แก้ไข</span></button>
                <button type="button" className="quiet" onClick={() => notify(`ตั้งค่าการแสดงผล ${detail.name}`)}><Eye aria-hidden /><span>การแสดงผล</span></button>
              </div>
              <div className="application-drawer-actions__primary">
                <button type="button" className={detail.active ? 'danger' : 'primary'} onClick={() => notify(detail.active ? `ซ่อนหมวดหมู่ ${detail.name}` : `เปิดใช้งาน ${detail.name}`)}>
                  {detail.active ? <CircleX aria-hidden /> : <CheckCircle2 aria-hidden />}
                  <span>{detail.active ? 'ซ่อนหมวดหมู่' : 'เปิดใช้งาน'}</span>
                </button>
              </div>
            </>
          )}
        >
          {tab === 'info' ? (
            <dl className="cm-contact">
              <div className="cm-contact-row"><span className="cm-icon-chip" aria-hidden="true"><Tags /></span><dt>รหัส</dt><dd className="num">#{detail.id}</dd></div>
              <div className="cm-contact-row"><span className="cm-icon-chip" aria-hidden="true"><Store /></span><dt>ชื่อหมวดหมู่</dt><dd>{detail.name}</dd></div>
              <div className="cm-contact-row"><span className="cm-icon-chip" aria-hidden="true"><FileText /></span><dt>คำอธิบาย</dt><dd>{detail.description}</dd></div>
              <div className="cm-contact-row"><span className="cm-icon-chip" aria-hidden="true"><CalendarDays /></span><dt>วันที่สร้าง</dt><dd>{detail.created}</dd></div>
              <div className="cm-contact-row"><span className="cm-icon-chip" aria-hidden="true"><Eye /></span><dt>สถานะ</dt><dd>{detail.active ? 'ใช้งาน' : 'ซ่อน'}</dd></div>
            </dl>
          ) : null}
          {tab === 'stats' ? (
            <div className="detail-documents">
              <p><Package /><span><b>สินค้าในหมวดหมู่</b><small>{detail.products.toLocaleString()} รายการ</small></span></p>
              <p><Store /><span><b>ร้านค้าที่ใช้งาน</b><small>{detail.stores.toLocaleString()} ร้าน</small></span></p>
              <p><BarChart3 /><span><b>สัดส่วนสินค้า</b><small>{((detail.products / 5842) * 100).toFixed(1)}% ของสินค้าทั้งหมด</small></span></p>
            </div>
          ) : null}
          {tab === 'history' ? (
            <div className="detail-history">
              <p><History /><span><b>สร้างหมวดหมู่</b><small>{detail.created}</small></span></p>
              <p><Settings /><span><b>อัปเดตล่าสุด</b><small>ยังไม่มีบันทึกเพิ่มเติม</small></span></p>
            </div>
          ) : null}
        </AdminDetailDrawer>
      ) : null}

      {notice && <div className="admin-toast" role="status">{notice}</div>}
    </main>
  )
}
