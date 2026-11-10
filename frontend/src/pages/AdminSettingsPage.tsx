import { useCallback, useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react'
import { Link, useBlocker } from 'react-router-dom'
import { ExternalLink, Headphones, LoaderCircle, Mail, MessageCircle, Phone, Settings } from 'lucide-react'
import AdminHeader from '../components/AdminHeader'
import AdminSidebar from '../components/AdminSidebar'
import ConfirmDialog from '../components/form/ConfirmDialog'
import FieldError from '../components/form/FieldError'
import FormField from '../components/form/FormField'
import ToggleSwitch from '../components/form/ToggleSwitch'
import { LoadError, SkeletonForm } from '../components/feedback/Skeleton'
import { useDelayedLoading } from '../hooks/useDelayedLoading'
import { ShipTimePicker } from '../components/merchant/ShipDateTimeFields'
import { useAuth } from '../contexts/AuthContext'
import { usePermissions } from '../hooks/usePermissions'
import {
  ApiError,
  platformSettingsAdminApi,
  type SupportContactAdmin,
  type SupportContactDayHours,
} from '../services/api'
import { hasPlatformAccess } from '../services/permissions'
import { formatSupportHours } from '../utils/supportHoursFormat'
import '../styles/admin-dashboard.css'
import '../styles/admin-categories.css'
import '../styles/admin-platform.css'
import '../styles/admin-form-controls.css'
import '../styles/form-fields.css'
import '../styles/form-invalid.css'
import '../styles/order-management.css'
import '../styles/settings.css'
import '../styles/help.css'
import '../styles/product-management.css'
import '../styles/admin-settings.css'

type FieldErrors = Record<string, string>
type ChannelKey = 'chat' | 'line' | 'email' | 'phone'
type DraftContact = {
  chat: { enabled: boolean; url: string; hours: SupportContactDayHours[] }
  line: { enabled: boolean; id: string; url: string }
  email: { enabled: boolean; address: string }
  phone: { enabled: boolean; number: string; hours: SupportContactDayHours[] }
}

const DAY_NAMES = ['จันทร์', 'อังคาร', 'พุธ', 'พฤหัสบดี', 'ศุกร์', 'เสาร์', 'อาทิตย์'] as const
const DAY_SHORT = ['จ.', 'อ.', 'พ.', 'พฤ.', 'ศ.', 'ส.', 'อา.'] as const
const TIME_LABELS = {
  hourLabel: 'ชั่วโมง',
  minuteLabel: 'นาที',
  applyLabel: 'ใช้เวลานี้',
  periodLabels: { night: 'ดึก', morning: 'เช้า', afternoon: 'บ่าย', evening: 'เย็น' },
} as const

const defaultChatHours = (): SupportContactDayHours[] =>
  Array.from({ length: 7 }, (_, day) => ({ day, open: true, start: '08:00', end: '22:00' }))
const defaultPhoneHours = (): SupportContactDayHours[] =>
  Array.from({ length: 7 }, (_, day) => ({ day, open: day < 5, start: day < 5 ? '08:00' : '09:00', end: '18:00' }))

const emptyDraft = (): DraftContact => ({
  chat: { enabled: true, url: '', hours: defaultChatHours() },
  line: { enabled: true, id: '', url: '' },
  email: { enabled: true, address: '' },
  phone: { enabled: true, number: '', hours: defaultPhoneHours() },
})

function normalizeHours(hours: SupportContactDayHours[] | undefined, fallback: () => SupportContactDayHours[]) {
  if (!Array.isArray(hours) || hours.length !== 7) return fallback()
  const byDay = new Map(hours.map((row) => [row.day, row]))
  return Array.from({ length: 7 }, (_, day) => {
    const row = byDay.get(day)
    return {
      day,
      open: Boolean(row?.open),
      start: row?.start || '08:00',
      end: row?.end || '18:00',
    }
  })
}

function normalizeDraft(value: SupportContactAdmin | DraftContact): DraftContact {
  return {
    chat: {
      enabled: Boolean(value.chat?.enabled),
      url: value.chat?.url ?? '',
      hours: normalizeHours(value.chat?.hours, defaultChatHours),
    },
    line: {
      enabled: Boolean(value.line?.enabled),
      id: value.line?.id ?? '',
      url: value.line?.url ?? '',
    },
    email: {
      enabled: Boolean(value.email?.enabled),
      address: value.email?.address ?? '',
    },
    phone: {
      enabled: Boolean(value.phone?.enabled),
      number: value.phone?.number ?? '',
      hours: normalizeHours(value.phone?.hours, defaultPhoneHours),
    },
  }
}

function serialize(value: DraftContact) {
  return JSON.stringify(normalizeDraft(value))
}

function lineHref(raw: string): string | undefined {
  const v = raw.trim().replace(/\s+/g, '')
  if (!v) return undefined
  if (/^https?:\/\//i.test(v)) return v
  if (/^(line\.me|lin\.ee)\//i.test(v)) return `https://${v}`
  if (/^[a-z][a-z0-9+.-]*:/i.test(v)) return undefined
  return v.startsWith('@')
    ? `https://line.me/R/ti/p/${encodeURIComponent(v)}`
    : `https://line.me/ti/p/~${encodeURIComponent(v)}`
}

function telHref(raw: string) {
  const digits = raw.replace(/\D/g, '')
  return digits ? `tel:${digits}` : undefined
}

type HoursPreset = 'everyday' | 'weekdays' | 'monSat'

function applyHoursPreset(preset: HoursPreset, start: string, end: string): SupportContactDayHours[] {
  return Array.from({ length: 7 }, (_, day) => {
    const open = preset === 'everyday' ? true : preset === 'weekdays' ? day < 5 : day < 6
    return { day, open, start, end }
  })
}

function detectPreset(hours: SupportContactDayHours[]): HoursPreset | null {
  const openFlags = hours.map((h) => h.open)
  const openDays = hours.filter((h) => h.open)
  if (openDays.length === 0) return null
  const start = openDays[0].start
  const end = openDays[0].end
  if (!openDays.every((h) => h.start === start && h.end === end)) return null
  if (openFlags.every(Boolean)) return 'everyday'
  if (openFlags.every((open, day) => open === day < 5)) return 'weekdays'
  if (openFlags.every((open, day) => open === day < 6)) return 'monSat'
  return null
}

function HoursEditor({
  hours,
  disabled,
  prefix,
  errors,
  showError,
  onChange,
  onTouch,
}: {
  hours: SupportContactDayHours[]
  disabled: boolean
  prefix: string
  errors: FieldErrors
  showError: (field: string) => boolean
  onChange: (next: SupportContactDayHours[]) => void
  onTouch: (field: string) => void
}) {
  const preset = detectPreset(hours)
  const patch = (index: number, next: Partial<SupportContactDayHours>) => {
    onChange(hours.map((row, i) => (i === index ? { ...row, ...next } : row)))
  }
  const openSample = hours.find((h) => h.open)
  const start = openSample?.start || '08:00'
  const end = openSample?.end || '18:00'

  return (
    <div className="admin-settings-hours">
      <p className="admin-settings-hours-label">เวลาทำการ</p>
      <div className="om-ship-presets" role="group" aria-label="ตั้งค่าด่วน">
        <button type="button" className={preset === 'everyday' ? 'active' : undefined} disabled={disabled} onClick={() => onChange(applyHoursPreset('everyday', start, end))}>ทุกวัน</button>
        <button type="button" className={preset === 'weekdays' ? 'active' : undefined} disabled={disabled} onClick={() => onChange(applyHoursPreset('weekdays', start, end))}>จันทร์–ศุกร์</button>
        <button type="button" className={preset === 'monSat' ? 'active' : undefined} disabled={disabled} onClick={() => onChange(applyHoursPreset('monSat', start, end))}>จันทร์–เสาร์</button>
      </div>
      {showError(prefix) ? <FieldError text={errors[prefix]} /> : null}
      <ul className="hours-list">
        {DAY_NAMES.map((day, index) => {
          const item = hours[index]
          const startField = `${prefix}[${index}].start`
          const endField = `${prefix}[${index}].end`
          const rangeError = showError(endField) ? errors[endField] : showError(startField) ? errors[startField] : undefined
          return (
            <li key={day}>
              <label title={day}>
                <input
                  type="checkbox"
                  checked={item.open}
                  disabled={disabled}
                  aria-label={day}
                  onChange={() => {
                    onTouch(prefix)
                    patch(index, { open: !item.open })
                  }}
                />
                <b>{DAY_SHORT[index]}</b>
              </label>
              {item.open ? (
                <>
                  <ShipTimePicker
                    label={`เวลาเปิด${day}`}
                    value={item.start}
                    hideLabel
                    disabled={disabled}
                    error={showError(startField) ? errors[startField] : undefined}
                    onChange={(next) => {
                      onTouch(startField)
                      patch(index, { start: next })
                    }}
                    {...TIME_LABELS}
                  />
                  <span>ถึง</span>
                  <ShipTimePicker
                    label={`เวลาปิด${day}`}
                    value={item.end}
                    hideLabel
                    disabled={disabled}
                    error={showError(endField) ? errors[endField] : undefined}
                    onChange={(next) => {
                      onTouch(endField)
                      patch(index, { end: next })
                    }}
                    {...TIME_LABELS}
                  />
                </>
              ) : (
                <span className="admin-settings-hint hours-closed">ปิด</span>
              )}
              {rangeError ? <div className="admin-settings-hours-error"><FieldError text={rangeError} /></div> : null}
            </li>
          )
        })}
      </ul>
    </div>
  )
}

export default function AdminSettingsPage() {
  const { user, loading } = useAuth()
  const { can } = usePermissions()
  const allowed = !!user && hasPlatformAccess(user)
  const canManage = can('PLATFORM_SETTING_UPDATE')

  const [baseline, setBaseline] = useState<DraftContact>(() => emptyDraft())
  const [draft, setDraft] = useState<DraftContact>(() => emptyDraft())
  const [loaded, setLoaded] = useState(false)
  const [loadError, setLoadError] = useState('')
  const [reloadKey, setReloadKey] = useState(0)
  const [notice, setNotice] = useState('')
  const [saving, setSaving] = useState(false)
  const [errors, setErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState('')
  const [touched, setTouched] = useState<Record<string, boolean>>({})
  const [submitAttempt, setSubmitAttempt] = useState(0)
  const [previewLang, setPreviewLang] = useState<'th' | 'en' | 'zh'>('th')

  const dirty = useMemo(() => serialize(draft) !== serialize(baseline), [draft, baseline])
  const { showSkeleton, markHasData } = useDelayedLoading(!loaded && !loadError, 150, String(reloadKey))
  const blocker = useBlocker(({ currentLocation, nextLocation }) => dirty && currentLocation.pathname !== nextLocation.pathname)

  useEffect(() => {
    if (!dirty) return
    const warn = (event: BeforeUnloadEvent) => {
      event.preventDefault()
      event.returnValue = ''
    }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [dirty])

  useEffect(() => {
    if (!allowed) return
    let cancelled = false
    setLoadError('')
    setLoaded(false)
    platformSettingsAdminApi.contact()
      .then((data) => {
        if (cancelled) return
        const next = normalizeDraft(data)
        setBaseline(next)
        setDraft(next)
        setLoaded(true)
        markHasData()
      })
      .catch((error) => {
        if (cancelled) return
        setLoadError(error instanceof ApiError && error.message ? error.message : 'โหลดข้อมูลติดต่อไม่สำเร็จ')
        setLoaded(true)
      })
    return () => { cancelled = true }
  }, [allowed, reloadKey, markHasData])

  const notify = useCallback((message: string) => {
    setNotice(message)
    window.setTimeout(() => setNotice(''), 2400)
  }, [])

  const showError = (field: string) => Boolean(errors[field] && (touched[field] || submitAttempt > 0))
  const touch = (field: string) => setTouched((current) => ({ ...current, [field]: true }))

  const setChannel = <K extends ChannelKey>(key: K, patch: Partial<DraftContact[K]>) => {
    setDraft((current) => ({ ...current, [key]: { ...current[key], ...patch } }))
  }

  const dayLabels = useMemo(
    () => DAY_NAMES.map((_, i) => {
      if (previewLang === 'en') return ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'][i]
      if (previewLang === 'zh') return ['周一', '周二', '周三', '周四', '周五', '周六', '周日'][i]
      return ['จ.', 'อ.', 'พ.', 'พฤ.', 'ศ.', 'ส.', 'อา.'][i]
    }),
    [previewLang],
  )

  const cancel = () => {
    setDraft(normalizeDraft(baseline))
    setErrors({})
    setFormError('')
    setTouched({})
    setSubmitAttempt(0)
  }

  const save = async (event?: FormEvent) => {
    event?.preventDefault()
    if (!canManage || saving) return
    setSubmitAttempt((n) => n + 1)
    setSaving(true)
    setFormError('')
    setErrors({})
    try {
      const payload: SupportContactAdmin = {
        chat: {
          enabled: draft.chat.enabled,
          url: draft.chat.url.trim() || null,
          hours: draft.chat.hours,
        },
        line: {
          enabled: draft.line.enabled,
          id: draft.line.id.trim() || null,
          url: draft.line.url.trim() || null,
        },
        email: {
          enabled: draft.email.enabled,
          address: draft.email.address.trim(),
        },
        phone: {
          enabled: draft.phone.enabled,
          number: draft.phone.number.trim(),
          hours: draft.phone.hours,
        },
      }
      const saved = normalizeDraft(await platformSettingsAdminApi.updateContact(payload))
      setBaseline(saved)
      setDraft(saved)
      setTouched({})
      setSubmitAttempt(0)
      notify('บันทึกข้อมูลติดต่อแล้ว')
    } catch (error) {
      if (error instanceof ApiError && error.fieldErrors?.length) {
        const next: FieldErrors = {}
        for (const item of error.fieldErrors) {
          if (item.field) next[item.field] = item.message || item.code || 'ไม่ถูกต้อง'
        }
        setErrors(next)
        setFormError(error.message || 'ข้อมูลไม่ถูกต้อง')
        window.setTimeout(() => {
          document.querySelector('[aria-invalid="true"]')?.scrollIntoView({ behavior: 'smooth', block: 'center' })
        }, 0)
      } else {
        setFormError(error instanceof ApiError && error.message ? error.message : 'บันทึกไม่สำเร็จ')
      }
    } finally {
      setSaving(false)
    }
  }

  if (loading) return <main className="auth-loading">กำลังตรวจสอบสิทธิ์…</main>
  if (!allowed) {
    return (
      <main className="admin-shell category-shell">
        {user ? <AdminHeader user={user} /> : null}
        <AdminSidebar active="settings" className="category-sidebar" />
        <div className="admin-content category-content">
          <section className="ap-card"><h1>ไม่มีสิทธิ์เข้าถึง</h1><p>ต้องมีสิทธิ์ PLATFORM_SETTING_UPDATE</p></section>
        </div>
      </main>
    )
  }

  const previewCards = (
    [
      draft.chat.enabled ? {
        key: 'chat' as const,
        icon: <MessageCircle aria-hidden="true" />,
        title: 'แชทกับเรา',
        detail: formatSupportHours(draft.chat.hours, dayLabels),
        action: draft.chat.url.trim() ? { href: draft.chat.url.trim(), label: 'เริ่มแชทเลย →' } : null,
      } : null,
      draft.line.enabled ? {
        key: 'line' as const,
        icon: <b className="line-logo" aria-hidden="true">LINE</b>,
        title: 'LINE Official',
        detail: draft.line.id.trim() || draft.line.url.trim(),
        action: (() => {
          const href = lineHref(draft.line.url.trim() || draft.line.id.trim())
          return href ? { href, label: 'เพิ่มเพื่อน →' } : null
        })(),
      } : null,
      draft.email.enabled ? {
        key: 'email' as const,
        icon: <Mail aria-hidden="true" />,
        title: 'ส่งอีเมล',
        detail: draft.email.address.trim(),
        action: draft.email.address.trim() ? { href: `mailto:${draft.email.address.trim()}`, label: 'ส่งอีเมล →' } : null,
      } : null,
      draft.phone.enabled ? {
        key: 'phone' as const,
        icon: <Phone aria-hidden="true" />,
        title: 'โทรสอบถาม',
        detail: [draft.phone.number.trim(), formatSupportHours(draft.phone.hours, dayLabels)].filter(Boolean).join('\n'),
        action: (() => {
          const href = telHref(draft.phone.number)
          return href ? { href, label: 'โทรเลย →' } : null
        })(),
      } : null,
    ].filter(Boolean) as Array<{
      key: ChannelKey
      icon: ReactNode
      title: string
      detail: string
      action: { href: string; label: string } | null
    }>
  )

  return (
    <main className="admin-shell category-shell">
      <AdminHeader user={user!} />
      <AdminSidebar active="settings" className="category-sidebar" />
      <div className="admin-content category-content">
        <header className="ap-card-head admin-settings-head">
          <div>
            <p className="ap-kicker"><Settings aria-hidden="true" /> ตั้งค่าแพลตฟอร์ม</p>
            <h1>ข้อมูลติดต่อ</h1>
            <p>ค่าที่บันทึกจะแสดงในส่วน “ติดต่อเรา” ของหน้า <Link to="/help" target="_blank" rel="noreferrer">/help <ExternalLink aria-hidden="true" /></Link></p>
          </div>
          <div className="admin-settings-actions">
            <button type="button" className="ap-ghost" disabled={!dirty || saving} onClick={cancel}>ยกเลิก</button>
            <button type="button" className="ap-primary" disabled={!canManage || !dirty || saving} onClick={() => void save()}>
              {saving ? <LoaderCircle className="pm-spin" aria-hidden="true" /> : null}
              บันทึก
            </button>
          </div>
        </header>

        {loadError ? (
          <LoadError message={loadError} onRetry={() => setReloadKey((n) => n + 1)} retryLabel="ลองใหม่" />
        ) : showSkeleton || !loaded ? (
          <SkeletonForm fields={10} />
        ) : (
          <form className="admin-settings-layout" onSubmit={(event) => void save(event)}>
            {formError ? <p className="form-alert" role="alert">{formError}</p> : null}

            <div className="admin-settings-main">
              <section className={`ap-card admin-settings-card${!draft.line.enabled ? ' is-disabled-channel' : ''}`}>
                <header className="admin-settings-card-head">
                  <div className="admin-settings-card-copy">
                    <h3>LINE Official</h3>
                    <small>แสดงปุ่มเพิ่มเพื่อนในหน้า /help</small>
                  </div>
                  <ToggleSwitch checked={draft.line.enabled} label="เปิดช่องทาง LINE" onText="เปิด" offText="ปิด" disabled={!canManage} onChange={(enabled) => setChannel('line', { enabled })} />
                </header>
                <div className="admin-settings-card-body">
                  <div className="admin-settings-fields">
                    <FormField label="LINE ID" error={showError('line.id') ? errors['line.id'] : undefined}>
                      <input value={draft.line.id} disabled={!canManage || !draft.line.enabled} aria-invalid={showError('line.id') || undefined} onBlur={() => touch('line.id')} onChange={(event) => setChannel('line', { id: event.target.value })} placeholder="@thaishopfun" />
                    </FormField>
                    <FormField label="ลิงก์ LINE (ทางเลือก)" error={showError('line.url') ? errors['line.url'] : undefined}>
                      <input value={draft.line.url} disabled={!canManage || !draft.line.enabled} aria-invalid={showError('line.url') || undefined} onBlur={() => touch('line.url')} onChange={(event) => setChannel('line', { url: event.target.value })} placeholder="https://line.me/…" />
                    </FormField>
                  </div>
                </div>
              </section>

              <section className={`ap-card admin-settings-card${!draft.email.enabled ? ' is-disabled-channel' : ''}`}>
                <header className="admin-settings-card-head">
                  <div className="admin-settings-card-copy">
                    <h3><Mail aria-hidden="true" /> อีเมล</h3>
                    <small>แสดงปุ่มส่งอีเมลในหน้า /help</small>
                  </div>
                  <ToggleSwitch checked={draft.email.enabled} label="เปิดช่องทางอีเมล" onText="เปิด" offText="ปิด" disabled={!canManage} onChange={(enabled) => setChannel('email', { enabled })} />
                </header>
                <div className="admin-settings-card-body">
                  <FormField label="อีเมล" error={showError('email.address') ? errors['email.address'] : undefined}>
                    <input value={draft.email.address} disabled={!canManage || !draft.email.enabled} aria-invalid={showError('email.address') || undefined} onBlur={() => touch('email.address')} onChange={(event) => setChannel('email', { address: event.target.value })} placeholder="support@thaishopfun.com" />
                  </FormField>
                </div>
              </section>

              <section className={`ap-card admin-settings-card${!draft.chat.enabled ? ' is-disabled-channel' : ''}`}>
                <header className="admin-settings-card-head">
                  <div className="admin-settings-card-copy">
                    <h3><MessageCircle aria-hidden="true" /> แชท</h3>
                    <small>ถ้าไม่มี URL ปุ่มแชทจะไม่แสดง</small>
                  </div>
                  <ToggleSwitch checked={draft.chat.enabled} label="เปิดช่องทางแชท" onText="เปิด" offText="ปิด" disabled={!canManage} onChange={(enabled) => setChannel('chat', { enabled })} />
                </header>
                <div className="admin-settings-card-body">
                  <FormField label="ลิงก์แชท (https)" error={showError('chat.url') ? errors['chat.url'] : undefined}>
                    <input value={draft.chat.url} disabled={!canManage || !draft.chat.enabled} aria-invalid={showError('chat.url') || undefined} onBlur={() => touch('chat.url')} onChange={(event) => setChannel('chat', { url: event.target.value })} placeholder="https://…" />
                  </FormField>
                  <HoursEditor
                    hours={draft.chat.hours}
                    disabled={!canManage || !draft.chat.enabled}
                    prefix="chat.hours"
                    errors={errors}
                    showError={showError}
                    onChange={(hours) => setChannel('chat', { hours })}
                    onTouch={touch}
                  />
                </div>
              </section>

              <section className={`ap-card admin-settings-card${!draft.phone.enabled ? ' is-disabled-channel' : ''}`}>
                <header className="admin-settings-card-head">
                  <div className="admin-settings-card-copy">
                    <h3><Phone aria-hidden="true" /> โทรศัพท์</h3>
                    <small>แสดงเบอร์และเวลาทำการ</small>
                  </div>
                  <ToggleSwitch checked={draft.phone.enabled} label="เปิดช่องทางโทร" onText="เปิด" offText="ปิด" disabled={!canManage} onChange={(enabled) => setChannel('phone', { enabled })} />
                </header>
                <div className="admin-settings-card-body">
                  <FormField label="เบอร์โทร" error={showError('phone.number') ? errors['phone.number'] : undefined}>
                    <input value={draft.phone.number} disabled={!canManage || !draft.phone.enabled} aria-invalid={showError('phone.number') || undefined} onBlur={() => touch('phone.number')} onChange={(event) => setChannel('phone', { number: event.target.value })} placeholder="02-123-4567" />
                  </FormField>
                  <HoursEditor
                    hours={draft.phone.hours}
                    disabled={!canManage || !draft.phone.enabled}
                    prefix="phone.hours"
                    errors={errors}
                    showError={showError}
                    onChange={(hours) => setChannel('phone', { hours })}
                    onTouch={touch}
                  />
                </div>
              </section>
            </div>

            <aside className="admin-settings-side" aria-label="พรีวิวติดต่อเรา">
              <div className="ap-card admin-settings-preview">
                <header className="admin-settings-card-head">
                  <h3><Headphones aria-hidden="true" /> พรีวิว /help</h3>
                  <div className="admin-settings-lang-tabs" role="tablist" aria-label="ภาษาพรีวิว">
                    {(['th', 'en', 'zh'] as const).map((lang) => (
                      <button key={lang} type="button" className={previewLang === lang ? 'active' : ''} onClick={() => setPreviewLang(lang)}>
                        {lang.toUpperCase()}
                      </button>
                    ))}
                  </div>
                </header>
                <section className="contact-panel admin-settings-contact-preview">
                  <div className="help-section-title">
                    <div>
                      <h2>ติดต่อเรา</h2>
                      <p>หากยังไม่ได้คำตอบ ทีมงานพร้อมช่วยเหลือคุณ</p>
                    </div>
                  </div>
                  <div className="contact-grid" data-count={previewCards.length || 1}>
                    {previewCards.length === 0 ? (
                      <p className="admin-settings-hint">ยังไม่มีช่องทางที่เปิดใช้งาน</p>
                    ) : previewCards.map((card) => (
                      <article key={card.key} className={card.key === 'line' ? 'line' : card.key}>
                        {card.icon}
                        <div>
                          <b>{card.title}</b>
                          <small className="admin-settings-preview-detail">{card.detail || '—'}</small>
                          {card.action ? (
                            <a href={card.action.href} target={card.action.href.startsWith('http') ? '_blank' : undefined} rel="noreferrer">
                              {card.action.label}
                            </a>
                          ) : null}
                        </div>
                      </article>
                    ))}
                  </div>
                </section>
              </div>
            </aside>
          </form>
        )}
      </div>

      {notice ? <div className="admin-toast" role="status">{notice}</div> : null}
      {blocker.state === 'blocked' && (
        <ConfirmDialog
          title="ออกจากหน้านี้?"
          message="มีการแก้ไขที่ยังไม่ได้บันทึก หากออกตอนนี้การเปลี่ยนแปลงจะหายไป"
          cancelLabel="อยู่ต่อ"
          confirmLabel="ออกโดยไม่บันทึก"
          onCancel={() => blocker.reset()}
          onConfirm={() => blocker.proceed()}
        />
      )}
    </main>
  )
}
