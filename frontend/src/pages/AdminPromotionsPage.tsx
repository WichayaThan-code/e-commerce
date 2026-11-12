import { FormEvent, useEffect, useMemo, useState } from 'react'
import { Megaphone, Plus, Trash2 } from 'lucide-react'
import AdminHeader from '../components/AdminHeader'
import AdminSidebar from '../components/AdminSidebar'
import AdminSelect from '../components/admin/AdminSelect'
import ConfirmDialog from '../components/form/ConfirmDialog'
import { LoadError, SkeletonTable } from '../components/feedback/Skeleton'
import { useAuth } from '../contexts/AuthContext'
import { usePermissions } from '../hooks/usePermissions'
import {
  ApiError,
  dealAdminApi,
  type AdminDealCampaign,
  type DealCampaignKind,
  type DealCampaignWrite,
} from '../services/api'
import { hasPlatformAccess } from '../services/permissions'
import '../styles/admin-dashboard.css'
import '../styles/admin-promotions.css'
import '../styles/admin-platform.css'
import '../styles/admin-form-controls.css'
import '../styles/form-fields.css'
import '../styles/product-management.css'

const KIND_OPTIONS: { value: DealCampaignKind | ''; label: string }[] = [
  { value: '', label: 'ทุกชนิด' },
  { value: 'TODAY', label: 'ดีลวันนี้ (TODAY)' },
  { value: 'WEEKLY', label: 'ดีลทุกสัปดาห์ (WEEKLY)' },
  { value: 'FLASH', label: 'Flash Sale (FLASH)' },
  { value: 'BOGO', label: 'ซื้อ 1 แถม 1 (BOGO)' },
]

const emptyForm = (): DealCampaignWrite & { productIdsText: string } => ({
  kind: 'TODAY',
  titleTh: '',
  titleEn: '',
  titleZh: '',
  startsAt: '',
  endsAt: '',
  active: true,
  sortOrder: 0,
  productIdsText: '',
})

function toLocalInput(iso?: string | null) {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function fromLocalInput(value: string) {
  const trimmed = value.trim()
  return trimmed || undefined
}

export default function AdminPromotionsPage() {
  const { user, loading } = useAuth()
  const { can } = usePermissions()
  const canManage = can('PLATFORM_SETTING_UPDATE')
  const [kindFilter, setKindFilter] = useState<DealCampaignKind | ''>('')
  const [items, setItems] = useState<AdminDealCampaign[]>([])
  const [fetching, setFetching] = useState(true)
  const [loadError, setLoadError] = useState(false)
  const [notice, setNotice] = useState('')
  const [tone, setTone] = useState<'ok' | 'fail'>('ok')
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [detail, setDetail] = useState<AdminDealCampaign | null>(null)
  const [form, setForm] = useState(emptyForm())
  const [saving, setSaving] = useState(false)
  const [creating, setCreating] = useState(false)
  const [deleteId, setDeleteId] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const notify = (message: string, next: 'ok' | 'fail' = 'ok') => {
    setTone(next)
    setNotice(message)
    window.setTimeout(() => setNotice(''), 2200)
  }

  const reload = async () => {
    setFetching(true)
    setLoadError(false)
    try {
      const rows = await dealAdminApi.list(kindFilter || undefined)
      setItems(rows)
    } catch {
      setLoadError(true)
      setItems([])
    } finally {
      setFetching(false)
    }
  }

  useEffect(() => { void reload() }, [kindFilter])

  useEffect(() => {
    if (!selectedId) { setDetail(null); return }
    let cancelled = false
    dealAdminApi.get(selectedId).then((row) => {
      if (cancelled) return
      setDetail(row)
      setForm({
        kind: row.kind,
        titleTh: row.titleTh,
        titleEn: row.titleEn || '',
        titleZh: row.titleZh || '',
        startsAt: toLocalInput(row.startsAt),
        endsAt: toLocalInput(row.endsAt),
        active: row.active,
        sortOrder: row.sortOrder,
        productIdsText: '',
      })
      setCreating(false)
    }).catch(() => {
      if (!cancelled) { setDetail(null); notify('โหลดแคมเปญไม่สำเร็จ', 'fail') }
    })
    return () => { cancelled = true }
  }, [selectedId])

  const kpis = useMemo(() => {
    const now = Date.now()
    let active = 0, upcoming = 0, ended = 0
    for (const row of items) {
      const start = new Date(row.startsAt).getTime()
      const end = row.endsAt ? new Date(row.endsAt).getTime() : null
      if (!row.active || (end != null && end <= now)) ended += 1
      else if (start > now) upcoming += 1
      else active += 1
    }
    return { total: items.length, active, upcoming, ended }
  }, [items])

  if (loading) return <main className="auth-loading">กำลังตรวจสอบสิทธิ์ผู้ดูแลระบบ...</main>
  if (!user) { window.location.replace('/login'); return null }
  if (!hasPlatformAccess(user)) { window.location.replace('/dashboard'); return null }

  const startCreate = () => {
    setSelectedId(null)
    setDetail(null)
    setCreating(true)
    setForm(emptyForm())
  }

  const save = async (event: FormEvent) => {
    event.preventDefault()
    if (!canManage) return
    setSaving(true)
    try {
      const body: DealCampaignWrite = {
        kind: form.kind,
        titleTh: form.titleTh.trim(),
        titleEn: form.titleEn?.trim() || undefined,
        titleZh: form.titleZh?.trim() || undefined,
        startsAt: fromLocalInput(form.startsAt || '') || new Date().toISOString(),
        endsAt: fromLocalInput(form.endsAt || '') || null,
        active: form.active,
        sortOrder: Number(form.sortOrder) || 0,
      }
      let saved: AdminDealCampaign
      if (creating || !selectedId) {
        saved = await dealAdminApi.create(body)
        notify('สร้างแคมเปญแล้ว')
      } else {
        saved = await dealAdminApi.update(selectedId, body)
        notify('บันทึกแคมเปญแล้ว')
      }
      const ids = form.productIdsText
        .split(/[\s,;]+/)
        .map((x) => x.trim())
        .filter(Boolean)
      if (ids.length) {
        saved = await dealAdminApi.attachProducts(saved.id, ids)
        notify('บันทึกและแนบสินค้าแล้ว')
      }
      setCreating(false)
      setSelectedId(saved.id)
      setDetail(saved)
      await reload()
    } catch (error) {
      notify(error instanceof ApiError ? error.message : 'บันทึกไม่สำเร็จ', 'fail')
    } finally {
      setSaving(false)
    }
  }

  const detach = async (productId: string) => {
    if (!selectedId || !canManage) return
    setBusy(true)
    try {
      const saved = await dealAdminApi.detachProduct(selectedId, productId)
      setDetail(saved)
      notify('ถอดสินค้าแล้ว')
      await reload()
    } catch (error) {
      notify(error instanceof ApiError ? error.message : 'ถอดสินค้าไม่สำเร็จ', 'fail')
    } finally {
      setBusy(false)
    }
  }

  const confirmDelete = async () => {
    if (!deleteId || !canManage) return
    setBusy(true)
    try {
      await dealAdminApi.remove(deleteId)
      if (selectedId === deleteId) { setSelectedId(null); setDetail(null); setCreating(false) }
      setDeleteId(null)
      notify('ลบแคมเปญแล้ว')
      await reload()
    } catch (error) {
      notify(error instanceof ApiError ? error.message : 'ลบไม่สำเร็จ', 'fail')
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="admin-shell promotions-shell">
      <AdminHeader user={user} />
      <AdminSidebar active="promotions" className="promotions-sidebar" />

      <div className="admin-content promotions-content">
        <section className="promotions-heading">
          <div className="promotions-title">
            <span><Megaphone aria-hidden="true" /></span>
            <div>
              <h1>แคมเปญดีลพิเศษ</h1>
              <p>จัดการแคมเปญ TODAY / WEEKLY / FLASH / BOGO สำหรับหน้า /deals</p>
              <small>แท็บไม่เกิน 199 / ราคาพิเศษ / คูปอง ดึงจากแคตตาล็อกและโปรร้านโดยอัตโนมัติ</small>
            </div>
          </div>
          {canManage ? (
            <button type="button" className="create-promo" onClick={startCreate}><Plus aria-hidden="true" />สร้างแคมเปญ</button>
          ) : null}
        </section>

        {!canManage ? (
          <section className="ap-card"><h1>ไม่มีสิทธิ์เข้าถึง</h1><p>ต้องมีสิทธิ์ PLATFORM_SETTING_UPDATE</p></section>
        ) : (
          <>
            <section className="promotion-kpis">
              <article className="all"><div><b>แคมเปญทั้งหมด</b><strong>{kpis.total}</strong></div></article>
              <article className="running"><div><b>กำลังใช้งาน</b><strong>{kpis.active}</strong></div></article>
              <article className="waiting"><div><b>รอเริ่ม</b><strong>{kpis.upcoming}</strong></div></article>
              <article className="ended"><div><b>สิ้นสุด/ปิด</b><strong>{kpis.ended}</strong></div></article>
            </section>

            <div className="ap-card" style={{ marginBottom: 16 }}>
              <AdminSelect
                label="กรองชนิดแคมเปญ"
                value={kindFilter}
                onChange={(value) => setKindFilter(value as DealCampaignKind | '')}
                options={KIND_OPTIONS}
              />
            </div>

            {fetching ? <SkeletonTable rows={6} cols={5} /> : loadError ? (
              <LoadError message="โหลดแคมเปญไม่สำเร็จ" onRetry={() => void reload()} retryLabel="ลองใหม่" />
            ) : (
              <section className="ap-card">
                <div className="ap-card-head"><h2>รายการแคมเปญ</h2></div>
                <div className="pm-table-scroll">
                  <table>
                    <thead>
                      <tr>
                        <th>ชื่อ</th>
                        <th>ชนิด</th>
                        <th>ช่วงเวลา</th>
                        <th>สินค้า</th>
                        <th>สถานะ</th>
                        <th />
                      </tr>
                    </thead>
                    <tbody>
                      {items.length === 0 ? (
                        <tr><td colSpan={6}>ยังไม่มีแคมเปญ</td></tr>
                      ) : items.map((row) => (
                        <tr key={row.id} className={selectedId === row.id ? 'is-selected' : undefined}>
                          <td>{row.titleTh}</td>
                          <td>{row.kind}</td>
                          <td>
                            <small>{new Date(row.startsAt).toLocaleString('th-TH')}</small>
                            {row.endsAt ? <><br /><small>ถึง {new Date(row.endsAt).toLocaleString('th-TH')}</small></> : null}
                          </td>
                          <td>{row.productCount}</td>
                          <td>{row.active ? 'เปิด' : 'ปิด'}</td>
                          <td>
                            <button type="button" className="ap-ghost" onClick={() => setSelectedId(row.id)}>แก้ไข</button>
                            <button type="button" className="ap-danger" onClick={() => setDeleteId(row.id)} aria-label="ลบ"><Trash2 aria-hidden="true" /></button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </section>
            )}

            {(creating || detail) ? (
              <section className="ap-card" style={{ marginTop: 16 }}>
                <div className="ap-card-head">
                  <h2>{creating ? 'สร้างแคมเปญใหม่' : 'แก้ไขแคมเปญ'}</h2>
                </div>
                <form className="product-form-card" onSubmit={save}>
                  <div className="merchant-two">
                    <label>
                      <span className="pm-lang-label">ชนิด <em>*</em></span>
                      <select
                        value={form.kind}
                        onChange={(e) => setForm({ ...form, kind: e.target.value as DealCampaignKind })}
                        required
                      >
                        {KIND_OPTIONS.filter((x) => x.value).map((x) => (
                          <option key={x.value} value={x.value}>{x.label}</option>
                        ))}
                      </select>
                    </label>
                    <label>
                      <span className="pm-lang-label">ลำดับ</span>
                      <input className="num" inputMode="numeric" value={form.sortOrder ?? 0}
                        onChange={(e) => setForm({ ...form, sortOrder: Number(e.target.value) || 0 })} />
                    </label>
                  </div>
                  <label>
                    <span className="pm-lang-label">ชื่อ (ไทย) <em>*</em></span>
                    <input value={form.titleTh} required onChange={(e) => setForm({ ...form, titleTh: e.target.value })} />
                  </label>
                  <div className="merchant-two">
                    <label>
                      <span className="pm-lang-label">ชื่อ (EN)</span>
                      <input value={form.titleEn || ''} onChange={(e) => setForm({ ...form, titleEn: e.target.value })} />
                    </label>
                    <label>
                      <span className="pm-lang-label">ชื่อ (ZH)</span>
                      <input value={form.titleZh || ''} onChange={(e) => setForm({ ...form, titleZh: e.target.value })} />
                    </label>
                  </div>
                  <div className="merchant-two">
                    <label>
                      <span className="pm-lang-label">เริ่ม <em>*</em></span>
                      <input type="datetime-local" required value={form.startsAt || ''}
                        onChange={(e) => setForm({ ...form, startsAt: e.target.value })} />
                    </label>
                    <label>
                      <span className="pm-lang-label">สิ้นสุด</span>
                      <input type="datetime-local" value={form.endsAt || ''}
                        onChange={(e) => setForm({ ...form, endsAt: e.target.value })} />
                    </label>
                  </div>
                  <label>
                    <span className="pm-lang-label">สถานะ</span>
                    <select value={form.active ? '1' : '0'} onChange={(e) => setForm({ ...form, active: e.target.value === '1' })}>
                      <option value="1">เปิดใช้งาน</option>
                      <option value="0">ปิด</option>
                    </select>
                  </label>
                  <label>
                    <span className="pm-lang-label">แนบสินค้า (UUID คั่นด้วย comma/ขึ้นบรรทัดใหม่)</span>
                    <textarea value={form.productIdsText} rows={4}
                      onChange={(e) => setForm({ ...form, productIdsText: e.target.value })}
                      placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" />
                    <p className="pm-lang-hint">สินค้าต้องเป็นสถานะเผยแพร่แล้ว</p>
                  </label>
                  {!creating && detail?.products?.length ? (
                    <div className="pm-detail">
                      <h4>สินค้าในแคมเปญ ({detail.products.length})</h4>
                      <ul>
                        {detail.products.map((p) => (
                          <li key={p.productId} style={{ display: 'flex', gap: 12, alignItems: 'center', marginBottom: 8 }}>
                            <span>{p.nameTh} <small>({p.shopName || p.shopSlug})</small></span>
                            <button type="button" className="ap-danger" disabled={busy} onClick={() => void detach(p.productId)}>ถอด</button>
                          </li>
                        ))}
                      </ul>
                    </div>
                  ) : null}
                  <div className="pm-actions">
                    <button type="button" onClick={() => { setCreating(false); setSelectedId(null); setDetail(null) }}>ยกเลิก</button>
                    <button type="submit" className="primary" disabled={saving}>{saving ? 'กำลังบันทึก…' : 'บันทึก'}</button>
                  </div>
                </form>
              </section>
            ) : null}
          </>
        )}
      </div>

      {notice ? <div className={`admin-toast ${tone}`} role="status">{notice}</div> : null}
      {deleteId ? (
        <ConfirmDialog
          title="ลบแคมเปญ?"
          message="สินค้าที่แนบจะถูกถอดออกด้วย การลบนี้ย้อนกลับไม่ได้"
          cancelLabel="ยกเลิก"
          confirmLabel="ลบแคมเปญ"
          danger
          confirmDisabled={busy}
          onCancel={() => setDeleteId(null)}
          onConfirm={() => void confirmDelete()}
        />
      ) : null}
    </main>
  )
}
