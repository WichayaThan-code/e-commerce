import { useCallback, useEffect, useRef, useState } from 'react'
import { Activity, Database, HardDrive, Mail, RefreshCw, Server } from 'lucide-react'
import AdminHeader from '../components/AdminHeader'
import AdminSidebar from '../components/AdminSidebar'
import { useAuth } from '../contexts/AuthContext'
import { usePermissions } from '../hooks/usePermissions'
import { hasPlatformAccess } from '../services/permissions'
import {
  ApiError,
  adminPlatformApi,
  type AdminSystemComponent,
  type AdminSystemOverall,
  type AdminSystemStatus,
} from '../services/api'
import '../styles/admin-dashboard.css'
import '../styles/admin-platform.css'
import '../styles/admin-system-status.css'

type ConnectionState = 'ONLINE' | 'NETWORK_OFFLINE' | 'BACKEND_UNREACHABLE' | 'BACKEND_ERROR' | 'SESSION_EXPIRED'

const FETCH_TIMEOUT_MS = 8_000
const NORMAL_POLL_MS = 30_000
const BACKOFF_MS = [5_000, 10_000, 20_000, 30_000] as const

function formatCheckedAt(iso: string) {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleTimeString('th-TH', { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false })
}

function formatDateTime(iso: string) {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleString('th-TH', {
    year: 'numeric', month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  })
}

function formatUptime(seconds: number) {
  const total = Math.max(0, Math.floor(seconds))
  const days = Math.floor(total / 86400)
  const hours = Math.floor((total % 86400) / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  const parts: string[] = []
  if (days > 0) parts.push(`${days} วัน`)
  if (hours > 0 || days > 0) parts.push(`${hours} ชม.`)
  parts.push(`${minutes} นาที`)
  return parts.join(' ')
}

function formatGb(bytes: number | null | undefined) {
  if (bytes == null || !Number.isFinite(bytes) || bytes < 0) return '—'
  return `${(bytes / (1024 ** 3)).toFixed(1)} GB`
}

function minutesAgoLabel(sinceMs: number, nowMs: number) {
  const minutes = Math.max(0, Math.floor((nowMs - sinceMs) / 60_000))
  if (minutes <= 0) return 'ไม่ถึง 1 นาทีที่แล้ว'
  return `${minutes} นาทีที่แล้ว`
}

function overallLabel(status: AdminSystemOverall) {
  if (status === 'UP') return 'ปกติ'
  if (status === 'DEGRADED') return 'บางส่วนมีปัญหา'
  return 'ระบบล่ม'
}

function componentLabel(key: string) {
  if (key === 'database') return 'ฐานข้อมูล'
  if (key === 'migrations') return 'Migration'
  if (key === 'mail') return 'ระบบอีเมล'
  if (key === 'storage') return 'พื้นที่จัดเก็บไฟล์'
  return key
}

function statusLabel(status: string) {
  if (status === 'UP') return 'ปกติ'
  if (status === 'DOWN') return 'ล่ม'
  if (status === 'UNKNOWN') return 'ไม่ทราบ'
  return status
}

function detailTh(component: AdminSystemComponent) {
  const detail = (component.detail ?? '').trim()
  if (component.key === 'mail') {
    if (detail === 'configured') return 'ตั้งค่าแล้ว'
    if (detail === 'not configured') return 'ยังไม่ได้ตั้งค่า'
  }
  if (component.key === 'storage') {
    if (detail === 'writable') return 'เขียนได้'
    if (detail === 'not writable') return 'เขียนไม่ได้'
  }
  if (component.key === 'migrations' && detail === 'n/a') return 'ไม่ใช้ในสภาพแวดล้อมนี้'
  if (detail === 'timeout') return 'หมดเวลา'
  if (detail === 'unreachable') return 'เชื่อมต่อไม่ได้'
  if (detail === 'unavailable') return 'ไม่พร้อมใช้งาน'
  return detail || '—'
}

function componentIcon(key: string) {
  if (key === 'database') return Database
  if (key === 'migrations') return Server
  if (key === 'mail') return Mail
  return HardDrive
}

function connectionMessage(connection: ConnectionState, detail: string) {
  if (connection === 'NETWORK_OFFLINE') return 'อินเทอร์เน็ตของคุณขาดการเชื่อมต่อ'
  if (connection === 'BACKEND_UNREACHABLE') return 'เชื่อมต่อเซิร์ฟเวอร์ไม่ได้ (backend อาจหยุดทำงาน)'
  if (connection === 'SESSION_EXPIRED') return 'เซสชันหมดอายุหรือระบบยืนยันตัวตนใช้งานไม่ได้'
  if (connection === 'BACKEND_ERROR') return detail || 'เซิร์ฟเวอร์ตอบกลับผิดพลาด'
  return ''
}

function classifyError(err: unknown): { connection: ConnectionState; detail: string; forbidden?: boolean } {
  if (typeof navigator !== 'undefined' && navigator.onLine === false) {
    return { connection: 'NETWORK_OFFLINE', detail: '' }
  }
  if (err instanceof DOMException && err.name === 'AbortError') {
    return { connection: 'BACKEND_UNREACHABLE', detail: '' }
  }
  if (err && typeof err === 'object' && 'name' in err && (err as { name?: string }).name === 'AbortError') {
    return { connection: 'BACKEND_UNREACHABLE', detail: '' }
  }
  if (err instanceof TypeError) {
    return { connection: 'BACKEND_UNREACHABLE', detail: '' }
  }
  if (err instanceof ApiError) {
    if (err.status === 0 || err.code === 'NETWORK_ERROR') {
      return { connection: 'BACKEND_UNREACHABLE', detail: '' }
    }
    if (err.status === 401) {
      return { connection: 'SESSION_EXPIRED', detail: '' }
    }
    if (err.status === 403) {
      return { connection: 'ONLINE', detail: 'ไม่มีสิทธิ์ดูหน้านี้', forbidden: true }
    }
    if (err.code === 'DATABASE_UNAVAILABLE' || (err.status === 503 && err.code === 'DATABASE_UNAVAILABLE')) {
      return { connection: 'BACKEND_ERROR', detail: 'ฐานข้อมูลใช้งานไม่ได้' }
    }
    if (err.status >= 500) {
      const codePart = err.code ? ` (${err.code})` : ''
      return { connection: 'BACKEND_ERROR', detail: `เซิร์ฟเวอร์ตอบกลับผิดพลาด (HTTP ${err.status})${codePart}` }
    }
    return { connection: 'BACKEND_ERROR', detail: err.message || `เซิร์ฟเวอร์ตอบกลับผิดพลาด (HTTP ${err.status})` }
  }
  return { connection: 'BACKEND_UNREACHABLE', detail: '' }
}

export default function AdminSystemStatusPage() {
  const { user, loading } = useAuth()
  const { can, deniedTitle } = usePermissions()
  const canRead = can('PLATFORM_SYSTEM_READ')

  const [lastGood, setLastGood] = useState<AdminSystemStatus | null>(null)
  const [connection, setConnection] = useState<ConnectionState>(() =>
    typeof navigator !== 'undefined' && navigator.onLine === false ? 'NETWORK_OFFLINE' : 'ONLINE',
  )
  const [errorDetail, setErrorDetail] = useState('')
  const [forbidden, setForbidden] = useState(false)
  const [fetching, setFetching] = useState(false)
  const [initialLoading, setInitialLoading] = useState(true)
  const [disconnectedSince, setDisconnectedSince] = useState<number | null>(null)
  const [toast, setToast] = useState('')
  const [nowTick, setNowTick] = useState(() => Date.now())

  const backoffIndexRef = useRef(0)
  const timerRef = useRef<number | null>(null)
  const toastTimerRef = useRef<number | null>(null)
  const abortRef = useRef<AbortController | null>(null)
  const inFlightRef = useRef(false)
  const unmountedRef = useRef(false)
  const cleanupAbortRef = useRef(false)
  const requestIdRef = useRef(0)
  const connectionRef = useRef(connection)
  const lastGoodRef = useRef(lastGood)
  const canReadRef = useRef(canRead)

  useEffect(() => { connectionRef.current = connection }, [connection])
  useEffect(() => { lastGoodRef.current = lastGood }, [lastGood])
  useEffect(() => { canReadRef.current = canRead }, [canRead])

  const clearTimer = () => {
    if (timerRef.current != null) {
      window.clearTimeout(timerRef.current)
      timerRef.current = null
    }
  }

  const clearToastTimer = () => {
    if (toastTimerRef.current != null) {
      window.clearTimeout(toastTimerRef.current)
      toastTimerRef.current = null
    }
  }

  const scheduleNext = useCallback((ok: boolean, advanceBackoff = true) => {
    if (unmountedRef.current) return
    clearTimer()
    if (!canReadRef.current) return
    const delay = ok
      ? NORMAL_POLL_MS
      : BACKOFF_MS[Math.min(backoffIndexRef.current, BACKOFF_MS.length - 1)]
    if (ok) backoffIndexRef.current = 0
    else if (advanceBackoff) {
      backoffIndexRef.current = Math.min(backoffIndexRef.current + 1, BACKOFF_MS.length - 1)
    }
    timerRef.current = window.setTimeout(() => {
      if (unmountedRef.current) return
      if (document.visibilityState === 'hidden') {
        scheduleNext(ok, false)
        return
      }
      void loadRef.current()
    }, delay)
  }, [])

  const loadRef = useRef<() => Promise<void>>(async () => {})

  const load = useCallback(async () => {
    if (unmountedRef.current) return
    cleanupAbortRef.current = false
    if (!canReadRef.current) {
      setForbidden(true)
      setInitialLoading(false)
      return
    }
    if (typeof navigator !== 'undefined' && navigator.onLine === false) {
      setConnection((prev) => {
        if (prev === 'ONLINE') setDisconnectedSince(Date.now())
        return 'NETWORK_OFFLINE'
      })
      setErrorDetail('')
      setInitialLoading(false)
      scheduleNext(false)
      return
    }

    if (inFlightRef.current) return
    inFlightRef.current = true
    const requestId = ++requestIdRef.current
    abortRef.current?.abort()
    const controller = new AbortController()
    abortRef.current = controller
    const timeout = window.setTimeout(() => controller.abort(), FETCH_TIMEOUT_MS)

    setFetching(true)
    try {
      const value = await adminPlatformApi.getSystemStatus({ signal: controller.signal })
      if (unmountedRef.current || cleanupAbortRef.current || requestId !== requestIdRef.current) return
      const wasOffline = connectionRef.current !== 'ONLINE'
      setLastGood(value)
      setConnection('ONLINE')
      setErrorDetail('')
      setForbidden(false)
      setDisconnectedSince(null)
      backoffIndexRef.current = 0
      if (wasOffline) {
        setToast('เชื่อมต่อกลับมาแล้ว')
        clearToastTimer()
        toastTimerRef.current = window.setTimeout(() => {
          if (!unmountedRef.current) setToast('')
        }, 3000)
      }
      scheduleNext(true)
    } catch (err) {
      if (unmountedRef.current || cleanupAbortRef.current || requestId !== requestIdRef.current) return
      const classified = classifyError(err)
      if (classified.forbidden) {
        setForbidden(true)
        setErrorDetail(classified.detail)
        setConnection('ONLINE')
      } else {
        setConnection((prev) => {
          if (prev === 'ONLINE') setDisconnectedSince(Date.now())
          return classified.connection
        })
        setErrorDetail(classified.detail)
      }
      scheduleNext(false)
    } finally {
      window.clearTimeout(timeout)
      if (requestId === requestIdRef.current) inFlightRef.current = false
      if (!unmountedRef.current && !cleanupAbortRef.current && requestId === requestIdRef.current) {
        setFetching(false)
        setInitialLoading(false)
      }
    }
  }, [scheduleNext])

  useEffect(() => { loadRef.current = load }, [load])

  useEffect(() => {
    unmountedRef.current = false
    cleanupAbortRef.current = false
    if (loading || !user || !hasPlatformAccess(user)) return
    void load()
    return () => {
      unmountedRef.current = true
      cleanupAbortRef.current = true
      clearTimer()
      clearToastTimer()
      abortRef.current?.abort()
      inFlightRef.current = false
    }
  }, [loading, user, load])

  useEffect(() => {
    const onOffline = () => {
      if (unmountedRef.current) return
      setConnection((prev) => {
        if (prev === 'ONLINE') setDisconnectedSince(Date.now())
        return 'NETWORK_OFFLINE'
      })
      setErrorDetail('')
      clearTimer()
      abortRef.current?.abort()
      scheduleNext(false)
    }
    const onOnline = () => {
      if (unmountedRef.current) return
      backoffIndexRef.current = 0
      void loadRef.current()
    }
    const onVisibility = () => {
      if (unmountedRef.current) return
      if (document.visibilityState === 'visible') {
        backoffIndexRef.current = 0
        void loadRef.current()
      }
    }
    window.addEventListener('offline', onOffline)
    window.addEventListener('online', onOnline)
    document.addEventListener('visibilitychange', onVisibility)
    return () => {
      window.removeEventListener('offline', onOffline)
      window.removeEventListener('online', onOnline)
      document.removeEventListener('visibilitychange', onVisibility)
    }
  }, [scheduleNext])

  useEffect(() => {
    if (connection === 'ONLINE' || !lastGood) return
    const timer = window.setInterval(() => {
      if (!unmountedRef.current) setNowTick(Date.now())
    }, 30_000)
    return () => window.clearInterval(timer)
  }, [connection, lastGood])

  if (loading) return <main className="auth-loading">กำลังตรวจสอบสิทธิ์…</main>
  if (!user) { window.location.replace('/login'); return null }
  if (!hasPlatformAccess(user)) { window.location.replace('/dashboard'); return null }

  const data = lastGood
  const overall = data?.overall ?? 'DOWN'
  const components = data?.components ?? []
  const stale = connection !== 'ONLINE' && !!data
  const bannerTone = connection !== 'ONLINE' ? 'offline' : overall.toLowerCase()
  const bannerTitle = connection !== 'ONLINE'
    ? connectionMessage(connection, errorDetail)
    : overallLabel(overall)
  const bannerSub = connection !== 'ONLINE'
    ? (disconnectedSince ? `ขาดการเชื่อมต่อตั้งแต่ ${formatCheckedAt(new Date(disconnectedSince).toISOString())}` : '')
    : `ตรวจล่าสุด ${data ? formatCheckedAt(data.checkedAt) : '—'}`
  const staleLabel = data && disconnectedSince
    ? `ข้อมูลล่าสุดเมื่อ ${formatCheckedAt(data.checkedAt)} (${minutesAgoLabel(Date.parse(data.checkedAt) || disconnectedSince, nowTick)})`
    : data
      ? `ข้อมูลล่าสุดเมื่อ ${formatCheckedAt(data.checkedAt)}`
      : ''

  return (
    <main className="admin-shell platform-shell">
      <AdminHeader user={user} />
      <AdminSidebar active="system-status" className="platform-sidebar" />
      <div className="admin-content platform-content sys-status">
        <section className="ap-heading">
          <span className="ap-heading-icon"><Activity /></span>
          <div>
            <h1>สถานะระบบ</h1>
            <p>ภาพรวมสุขภาพของแพลตฟอร์มสำหรับทีมแอดมิน</p>
          </div>
        </section>

        {!canRead || forbidden ? (
          <div className="ap-card">
            <p className="ap-empty" title={deniedTitle(false)}>{errorDetail || 'ไม่มีสิทธิ์ดูหน้านี้'}</p>
          </div>
        ) : (
          <>
            <section className={`sys-status-banner tone-${bannerTone}`} aria-live="polite">
              {initialLoading && !data ? (
                <div className="sys-status-skeleton banner" aria-hidden="true" />
              ) : (
                <>
                  <span className="sys-status-dot" aria-hidden="true" />
                  <div className="sys-status-banner-copy">
                    <strong>{bannerTitle}</strong>
                    {bannerSub ? <small>{bannerSub}</small> : null}
                    {stale && staleLabel ? <small className="sys-status-stale-label">{staleLabel}</small> : null}
                  </div>
                  {connection === 'SESSION_EXPIRED' ? (
                    <a className="ap-primary sys-status-refresh" href={`/login?next=${encodeURIComponent('/admin/system-status')}`}>
                      เข้าสู่ระบบใหม่
                    </a>
                  ) : (
                    <button
                      type="button"
                      className="ap-primary sys-status-refresh"
                      onClick={() => { backoffIndexRef.current = 0; void load() }}
                      disabled={fetching}
                    >
                      <RefreshCw size={16} className={fetching ? 'sys-status-spin' : undefined} /> ตรวจอีกครั้ง
                    </button>
                  )}
                </>
              )}
            </section>

            <section className={`sys-status-grid${stale ? ' is-stale' : ''}`} aria-busy={initialLoading && !data}>
              {initialLoading && !data
                ? Array.from({ length: 4 }, (_, index) => (
                    <article className="ap-card sys-status-card skeleton" key={`sk-${index}`} aria-hidden="true">
                      <div className="sys-status-skeleton line" />
                      <div className="sys-status-skeleton line short" />
                      <div className="sys-status-skeleton line" />
                    </article>
                  ))
                : components.map((component) => {
                    const Icon = componentIcon(component.key)
                    return (
                      <article
                        className={`ap-card sys-status-card ${stale ? 'tone-stale' : `tone-${component.status.toLowerCase()}`}`}
                        key={component.key}
                      >
                        <header>
                          <span><Icon size={18} /></span>
                          <h2>{componentLabel(component.key)}</h2>
                          <em>{stale ? 'ข้อมูลค้าง' : statusLabel(component.status)}</em>
                        </header>
                        <p>{detailTh(component)}</p>
                        {component.key === 'database' && component.latencyMs != null && (
                          <small>ความหน่วง {component.latencyMs} ms</small>
                        )}
                        {component.key === 'migrations' && (
                          <small>ค้าง {component.pending ?? 0} · ล้มเหลว {component.failed ?? 0}</small>
                        )}
                        {component.key === 'storage' && (
                          <small>ว่าง {formatGb(component.freeBytes)} / ทั้งหมด {formatGb(component.totalBytes)}</small>
                        )}
                      </article>
                    )
                  })}
            </section>

            <section className={`ap-card sys-status-app${stale ? ' is-stale' : ''}`}>
              <div className="ap-card-head">
                <h2>ข้อมูลแอป</h2>
              </div>
              {initialLoading && !data ? (
                <div className="ap-pad">
                  <div className="sys-status-skeleton line" />
                  <div className="sys-status-skeleton line short" />
                </div>
              ) : data ? (
                <dl className="sys-status-app-grid ap-pad">
                  <div><dt>เวอร์ชัน</dt><dd>{data.app.version}</dd></div>
                  <div><dt>เริ่มระบบ</dt><dd>{formatDateTime(data.app.startedAt)}</dd></div>
                  <div><dt>อัพไทม์</dt><dd>{formatUptime(data.app.uptimeSeconds)}</dd></div>
                  <div><dt>Java</dt><dd>{data.app.javaVersion}</dd></div>
                  <div><dt>โปรไฟล์</dt><dd>{data.app.profile}</dd></div>
                  {data.app.buildTime ? <div><dt>เวลาบิลด์</dt><dd>{formatDateTime(data.app.buildTime)}</dd></div> : null}
                </dl>
              ) : (
                <p className="ap-empty ap-pad">ยังไม่มีข้อมูล</p>
              )}
            </section>
          </>
        )}
        {toast && <div className="sys-status-toast" role="status">{toast}</div>}
      </div>
    </main>
  )
}
