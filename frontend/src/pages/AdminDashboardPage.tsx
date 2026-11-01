import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  CalendarDays, CircleDollarSign, FileCheck2, ShoppingBag, Store, UsersRound,
} from 'lucide-react'
import AdminHeader from '../components/AdminHeader'
import AdminSidebar from '../components/AdminSidebar'
import DateRangePicker from '../components/form/DateRangePicker'
import DashboardWelcome from '../components/dashboard/DashboardWelcome'
import { numberOf } from '../components/merchant/orderFormat'
import { useAuth } from '../contexts/AuthContext'
import { hasPlatformAccess } from '../services/permissions'
import {
  adminDashboardApi,
  type AdminDashboardPaymentMethod,
  type AdminDashboardRange,
  type AdminDashboardSummary,
} from '../services/api'
import { dashboardGreetingName } from '../utils/dashboardGreeting'
import { primaryRoleLabelTh } from '../utils/roleLabels'
import '../styles/admin-dashboard.css'

type RangeKey = AdminDashboardRange | 'custom'
const RANGES: [RangeKey, string, string][] = [
  ['today', 'วันนี้', 'วันนี้'],
  ['7d', '7 วัน', '7 วันล่าสุด'],
  ['30d', '30 วัน', '30 วันล่าสุด'],
  ['1y', '1 ปี', '1 ปีล่าสุด'],
  ['custom', 'กำหนดเอง', 'ช่วงที่เลือก'],
]
const bkkDate = (d: Date) => new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Bangkok' }).format(d)

const FEE_RATE = 0.05
const MEDALS = ['🥇', '🥈', '🥉', '4', '5'] as const
const ANNOUNCEMENTS_SAMPLE = [
  ['12 ก.ย. 2026', 'อัปเดตระบบการชำระเงิน (QR)'],
  ['10 ก.ย. 2026', 'เพิ่มช่องทางเชื่อมต่อ TikTok Shop'],
  ['5 ก.ย. 2026', 'ปรับปรุงหน้าการสมัครร้านค้า'],
] as const

const STATUS_TH: Record<string, string> = {
  PENDING_PAYMENT: 'รอชำระเงิน',
  PAID_AWAITING_MERCHANT_CONFIRM: 'ชำระแล้ว รอยืนยัน',
  MERCHANT_CONFIRMED: 'ยืนยันแล้ว',
  READY_TO_PACK: 'รอแพ็ก',
  PACKING: 'กำลังแพ็ก',
  PACKED: 'แพ็กแล้ว',
  DISPATCHED: 'กำลังจัดส่ง',
  DELIVERED: 'จัดส่งแล้ว',
  COMPLETED: 'สำเร็จ',
  CANCELLED: 'ยกเลิก',
  REFUNDED: 'คืนเงินแล้ว',
  REFUND_REQUESTED: 'ขอคืนเงิน',
}

const PAYMENT_STATUS_TH: Record<string, string> = {
  UNPAID: 'ยังไม่ชำระ',
  PENDING: 'รอชำระ',
  PAID: 'ชำระแล้ว',
  FAILED: 'ชำระไม่สำเร็จ',
  REFUNDED: 'คืนเงินแล้ว',
  PARTIALLY_REFUNDED: 'คืนเงินบางส่วน',
}

/** Stable colors from the original channel card (donut + legend). */
const PAYMENT_METHOD_COLORS: Record<string, string> = {
  PROMPTPAY: '#159df5',
  BANK_TRANSFER: '#12c98b',
  COD: '#9458ee',
  OMISE: '#ffae2f',
  '2C2P': '#f72b59',
}
const PAYMENT_METHOD_OTHER_COLOR = '#ff8a00'

function paymentMethodLabel(method: string): string {
  switch (method.trim().toUpperCase()) {
    case 'PROMPTPAY': return 'พร้อมเพย์'
    case 'BANK_TRANSFER': return 'โอนผ่านธนาคาร'
    case 'COD': return 'เก็บเงินปลายทาง'
    case 'OMISE':
    case '2C2P': return 'บัตรเครดิต'
    default: return 'อื่น ๆ'
  }
}

function paymentMethodColor(method: string): string {
  return PAYMENT_METHOD_COLORS[method.trim().toUpperCase()] ?? PAYMENT_METHOD_OTHER_COLOR
}

type PaymentShareRow = { key: string; label: string; color: string; sales: number; pct: number }

/** Merge unknown providers into "อื่น ๆ"; % rounded to integers with the last row making 100. */
function paymentShareRows(methods: AdminDashboardPaymentMethod[] | undefined, totalSales: number): PaymentShareRow[] {
  if (!methods?.length || totalSales <= 0) return []
  const known: PaymentShareRow[] = []
  let otherSales = 0
  let cardSales = 0
  for (const row of methods) {
    const code = (row.method || '').trim().toUpperCase()
    const sales = numberOf(row.amount)
    if (code === 'OMISE' || code === '2C2P') {
      cardSales += sales
      continue
    }
    if (!code || !(code in PAYMENT_METHOD_COLORS)) {
      otherSales += sales
      continue
    }
    known.push({ key: code, label: paymentMethodLabel(code), color: paymentMethodColor(code), sales, pct: 0 })
  }
  if (cardSales > 0) {
    known.push({ key: 'CARD', label: 'บัตรเครดิต', color: PAYMENT_METHOD_COLORS.OMISE, sales: cardSales, pct: 0 })
  }
  const rows = otherSales > 0
    ? [...known, { key: 'OTHER', label: 'อื่น ๆ', color: PAYMENT_METHOD_OTHER_COLOR, sales: otherSales, pct: 0 }]
    : known
  if (rows.length === 0) return []
  let allocated = 0
  return rows.map((row, index) => {
    const pct = index === rows.length - 1
      ? Math.max(0, 100 - allocated)
      : Math.round((row.sales / totalSales) * 100)
    if (index < rows.length - 1) allocated += pct
    return { ...row, pct }
  })
}

function paymentDonutBackground(rows: PaymentShareRow[]): string {
  let cursor = 0
  const stops: string[] = []
  for (const row of rows) {
    const next = cursor + row.pct
    stops.push(`${row.color} ${cursor}% ${next}%`)
    cursor = next
  }
  if (cursor < 100) stops.push(`#e8eef6 ${cursor}% 100%`)
  return `conic-gradient(${stops.join(',')})`
}

function Panel({ title, action, href, sample, children, className = '' }: {
  title: string; action?: string; href?: string; sample?: boolean; children: React.ReactNode; className?: string
}) {
  const navigate = useNavigate()
  return (
    <section className={`admin-panel ${className}`}>
      <header>
        <h2>
          {title}
          {sample ? <small style={{ marginLeft: 8, fontSize: 12, fontWeight: 600, color: '#8a94a6', background: '#eef2f7', borderRadius: 999, padding: '2px 8px', verticalAlign: 'middle' }}>ตัวอย่าง</small> : null}
        </h2>
        {action && !sample && href ? (
          <button type="button" onClick={() => navigate(href)}>{action} →</button>
        ) : action && !sample ? (
          <button type="button">{action} →</button>
        ) : action && sample ? (
          <button type="button" disabled>{action} →</button>
        ) : null}
      </header>
      {children}
    </section>
  )
}

function pctChange(cur: number, prev: number): number | null {
  if (prev === 0) return cur === 0 ? 0 : null
  return Math.round(((cur - prev) / prev) * 1000) / 10
}

function Trend({ cur, prev }: { cur: number; prev: number }) {
  const p = pctChange(cur, prev)
  if (p == null) return null
  return <i style={p < 0 ? { color: '#f32657' } : undefined}>{p > 0 ? '↑' : p < 0 ? '↓' : ''} {Math.abs(p) > 999 ? '999+' : Math.abs(p).toLocaleString('th-TH')}%</i>
}

const baht = (v: unknown) => '฿ ' + numberOf(v as number).toLocaleString('th-TH', { maximumFractionDigits: 0 })
const thaiDateTime = (v?: string | null) =>
  v ? new Intl.DateTimeFormat('th-TH', { timeZone: 'Asia/Bangkok', day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }).format(new Date(v)) : '—'
const thaiDay = (iso: string) =>
  new Intl.DateTimeFormat('th-TH', { timeZone: 'UTC', day: 'numeric', month: 'short' }).format(new Date(`${iso}T00:00:00Z`))
const thaiRangeLabel = (from: string, to: string) => {
  const fmt = new Intl.DateTimeFormat('th-TH', { timeZone: 'UTC', day: 'numeric', month: 'short', year: 'numeric' })
  const a = fmt.format(new Date(`${from}T00:00:00Z`))
  const b = fmt.format(new Date(`${to}T00:00:00Z`))
  return from === to ? a : `${a} - ${b}`
}

export default function AdminDashboardPage() {
  const navigate = useNavigate()
  const { user, loading } = useAuth()
  const [data, setData] = useState<AdminDashboardSummary | null>(null)
  const [dashLoading, setDashLoading] = useState(true)
  const [dashError, setDashError] = useState('')
  const [range, setRange] = useState<RangeKey>('7d')
  const [customFrom, setCustomFrom] = useState(() => bkkDate(new Date(Date.now() - 6 * 86400000)))
  const [customTo, setCustomTo] = useState(() => bkkDate(new Date()))
  const customValid = !!customFrom && !!customTo && customFrom <= customTo
  const period = range === 'custom' ? (customValid ? { from: customFrom, to: customTo } : null) : { range }
  const periodKey = period ? JSON.stringify(period) : ''

  useEffect(() => {
    if (range === 'custom' && !customValid) {
      setData(null)
      setDashLoading(false)
      setDashError(!customFrom || !customTo
        ? 'เลือกช่วงวันที่เพื่อดูภาพรวม'
        : 'ช่วงวันที่ไม่ถูกต้อง: วันเริ่มต้องไม่หลังวันสิ้นสุด')
      return
    }
    let alive = true
    setDashLoading(true)
    setDashError('')
    if (!period) return
    adminDashboardApi.summary(period)
      .then((row) => { if (alive) setData(row) })
      .catch(() => { if (alive) { setData(null); setDashError('โหลดข้อมูลแดชบอร์ดไม่สำเร็จ กรุณาลองใหม่อีกครั้ง') } })
      .finally(() => { if (alive) setDashLoading(false) })
    return () => { alive = false }
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [periodKey])

  if (loading) return <main className="auth-loading">กำลังตรวจสอบสิทธิ์ผู้ดูแลระบบ...</main>
  if (!user) { window.location.replace('/login'); return null }
  if (!hasPlatformAccess(user)) { window.location.replace('/dashboard'); return null }

  const sales = numberOf(data?.totalSales)
  const prevSales = numberOf(data?.previousSales)
  const shopsTotal = data?.counts.shopsTotal ?? null
  const pendingApps = data?.counts.pendingApplications ?? null
  const usersTotal = data?.counts.usersTotal ?? null
  const usersNew = data?.counts.usersNewInPeriod ?? null
  const feeEstimate = sales * FEE_RATE
  const prevFee = prevSales * FEE_RATE

  const series = data?.series ?? []
  const maxSales = Math.max(1, ...series.map((p) => numberOf(p.sales)))
  const yTicks = [maxSales, maxSales * 0.75, maxSales * 0.5, maxSales * 0.25, 0]
  const chartBars = series.map((p) => Math.max(4, Math.round((numberOf(p.sales) / maxSales) * 120)))
  const linePoints = series.map((p, index) => {
    const x = series.length <= 1 ? 350 : (index / (series.length - 1)) * 680 + 10
    const y = 100 - (numberOf(p.sales) / maxSales) * 90
    return `${x},${y}`
  }).join(' ')

  const kpi = (v: number | null) => (dashLoading ? '…' : v == null ? '—' : v.toLocaleString('th-TH'))
  const todayLabel = new Intl.DateTimeFormat('th-TH', { timeZone: 'Asia/Bangkok', day: 'numeric', month: 'long', year: 'numeric' }).format(new Date())
  const rangeLabel = RANGES.find((r) => r[0] === range)?.[2] ?? '7 วันล่าสุด'
  const chartBucket = data?.bucket === 'week' ? 'รายสัปดาห์' : data?.bucket === 'month' ? 'รายเดือน' : 'รายวัน'
  const greetingName = dashboardGreetingName(user)
  const roleLabel = primaryRoleLabelTh(user)
  const suspendedShops = data?.counts.shopsSuspended ?? null
  const usersCardTitle = range === 'today' ? 'ผู้ใช้งานใหม่ (วันนี้)'
    : range === '7d' ? 'ผู้ใช้งานใหม่ (7 วัน)'
      : range === '30d' ? 'ผู้ใช้งานใหม่ (30 วัน)'
        : range === '1y' ? 'ผู้ใช้งานใหม่ (1 ปี)'
          : data ? `ผู้ใช้งานใหม่ (${thaiRangeLabel(data.from, data.to)})` : 'ผู้ใช้งานใหม่'
  const paymentRows = paymentShareRows(data?.paymentMethods, sales)
  const fmt = (n: number) => n.toLocaleString('th-TH')
  const welcomeActions = [
    pendingApps != null && pendingApps > 0
      ? { label: `ใบสมัครรอตรวจ ${fmt(pendingApps)}`, count: pendingApps, to: '/admin/store-applications', tone: 'warn' as const }
      : null,
    suspendedShops != null && suspendedShops > 0
      ? { label: `ร้านถูกระงับ ${fmt(suspendedShops)}`, count: suspendedShops, to: '/admin/stores', tone: 'danger' as const }
      : null,
  ].filter((item): item is NonNullable<typeof item> => item != null)

  return (
    <main className="admin-shell">
      <AdminHeader user={user} />
      <AdminSidebar active="home" showHelp extended />
      <div className="admin-content">
        <section className="admin-welcome">
          <div>
            <DashboardWelcome
              greetingName={greetingName}
              loading={dashLoading}
              actions={welcomeActions}
              contextNode={(
                <>
                  <span>ภาพรวมแพลตฟอร์ม</span>
                  <span className="dash-welcome-role">{roleLabel}</span>
                </>
              )}
            />
            {dashError ? <p role="alert" style={{ color: '#f32657', marginTop: 6 }}>{dashError}</p> : null}
          </div>
          <div className="admin-range">
            <div className="admin-range-seg" role="tablist" aria-label="ช่วงเวลา">
              <CalendarDays />
              {RANGES.map(([key, label]) => (
                <button
                  key={key}
                  type="button"
                  role="tab"
                  aria-selected={range === key}
                  className={range === key ? 'active' : ''}
                  onClick={() => setRange(key)}
                >
                  {label}
                </button>
              ))}
            </div>
            {range === 'custom' ? (
              <DateRangePicker
                className="admin-range-custom"
                label="ช่วงวันที่"
                from={customFrom}
                to={customTo}
                clearLabel="ล้าง"
                applyLabel="ใช้ช่วงนี้"
                presetsLabel={{ d7: '7 วัน', d30: '30 วัน', month: 'เดือนนี้' }}
                onChange={({ from, to }) => {
                  setCustomFrom(from)
                  setCustomTo(to)
                }}
              />
            ) : (
              <small>
                {range === 'today' ? `วันนี้ · ${todayLabel}` : data ? thaiRangeLabel(data.from, data.to) : rangeLabel}
                {' · เวลาไทย'}
              </small>
            )}
          </div>
        </section>

        <section className="admin-kpis">
          <article className="blue">
            <span><Store /></span>
            <div>
              <small>ร้านค้าทั้งหมด</small>
              <b>{kpi(shopsTotal)}</b>
              <em>{dashLoading || !data ? '—' : `อนุมัติ ${data.counts.shopsApproved.toLocaleString('th-TH')} | รอตรวจ ${data.counts.shopsPendingReview.toLocaleString('th-TH')}`}</em>
            </div>
          </article>
          <article className="orange">
            <span><FileCheck2 /></span>
            <div>
              <small>ใบสมัครที่รอตรวจสอบ</small>
              <b>{kpi(pendingApps)}</b>
              <em>{dashLoading || !data ? '—' : `ปฏิเสธ ${data.counts.shopsRejected.toLocaleString('th-TH')} | ระงับ ${data.counts.shopsSuspended.toLocaleString('th-TH')}`}</em>
            </div>
          </article>
          <article className="green">
            <span><UsersRound /></span>
            <div>
              <small>ผู้ใช้งานทั้งหมด</small>
              <b>{kpi(usersTotal)}</b>
              <em>{dashLoading || usersNew == null ? '—' : `ใหม่ในช่วงนี้ ${usersNew.toLocaleString('th-TH')} คน`}</em>
            </div>
          </article>
          <article className="purple">
            <span><ShoppingBag /></span>
            <div>
              <small>ยอดขายรวม (GMV)</small>
              <b>{dashLoading ? '…' : !data ? '—' : <>{baht(sales)} {!dashLoading && data ? <Trend cur={sales} prev={prevSales} /> : null}</>}</b>
              <em>เทียบช่วงก่อนหน้า · เวลาไทย</em>
            </div>
          </article>
          <article className="red">
            <span><CircleDollarSign /></span>
            <div>
              <small>ค่าธรรมเนียม 5% <i style={{ fontStyle: 'normal', fontSize: 10, fontWeight: 700, color: '#8a94a6', background: '#eef2f7', borderRadius: 999, padding: '1px 6px', marginLeft: 4 }}>ตัวอย่าง</i></small>
              <b>{dashLoading ? '…' : !data ? '—' : <>{baht(feeEstimate)} <Trend cur={feeEstimate} prev={prevFee} /></>}</b>
              <em>ประมาณการจากยอดขาย · B7</em>
            </div>
          </article>
        </section>

        <section className="admin-grid top">
          <Panel title={`ยอดขาย (${chartBucket})`} action="ดูรายละเอียด">
            <div className="chart-legend"><span><i />ยอดขาย (บาท)</span></div>
            {dashLoading && !data ? <p style={{ padding: 24, textAlign: 'center', color: '#6881a6' }}>กำลังโหลด…</p>
              : !series.length ? <p style={{ padding: 24, textAlign: 'center', color: '#6881a6' }}>ยังไม่มียอดขายในช่วงนี้</p>
                : (
                  <div className="sales-chart">
                    <div className="y-labels">{yTicks.map((v, i) => <span key={i}>{Math.round(v).toLocaleString('th-TH')}</span>)}</div>
                    <div className="plot">
                      {chartBars.map((height, index) => (
                        <div className="bar-column" key={series[index].start}>
                          <i style={{ height }} title={`${baht(series[index].sales)} · ${series[index].orders} ออเดอร์`} />
                          <small>{thaiDay(series[index].start)}</small>
                        </div>
                      ))}
                      {linePoints ? (
                        <svg viewBox="0 0 700 105" preserveAspectRatio="none">
                          <polyline points={linePoints} />
                          {linePoints.split(' ').map((point, index) => {
                            const [cx, cy] = point.split(',')
                            return <circle key={index} cx={cx} cy={cy} r="4" />
                          })}
                        </svg>
                      ) : null}
                    </div>
                  </div>
                )}
          </Panel>
          <Panel title="สัดส่วนวิธีชำระเงิน" action="ดูทั้งหมด" href="/admin/orders">
            {dashLoading && !data ? <p style={{ padding: 24, textAlign: 'center', color: '#6881a6' }}>กำลังโหลด…</p>
              : paymentRows.length === 0 ? <p style={{ padding: 24, textAlign: 'center', color: '#6881a6' }}>ยังไม่มียอดขายในช่วงนี้</p>
                : (
                  <div className="channel-wrap">
                    <div className="donut" style={{ background: paymentDonutBackground(paymentRows) }}>
                      <b>{baht(sales)}<small>ยอดขายรวม</small></b>
                    </div>
                    <ul>
                      {paymentRows.map((row) => (
                        <li key={row.key} title={baht(row.sales)}>
                          <i style={{ background: row.color }} />
                          <span>{row.label}</span>
                          <b>{row.pct}%</b>
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
          </Panel>
        </section>

        <section className="admin-grid middle">
          <Panel title="คำสั่งซื้อล่าสุด" action="ดูทั้งหมด" href="/admin/orders">
            <div className="application-table">
              <div className="table-row head">
                <b>#</b><b>ร้านค้า</b><b>ยอด</b><b>สถานะ</b><b>การชำระ</b><b>เวลา</b><b></b>
              </div>
              {dashLoading && !data ? <p style={{ padding: 16, color: '#6881a6' }}>กำลังโหลด…</p>
                : !data?.recentOrders.length ? <p style={{ padding: 16, color: '#6881a6' }}>ยังไม่มีคำสั่งซื้อ</p>
                  : data.recentOrders.map((row) => (
                    <div className="table-row" key={`${row.shopId}-${row.orderNumber}`}>
                      <span>{row.orderNumber}</span>
                      <b><i className="store-avatar">🏪</i>{row.shopName}</b>
                      <span>{baht(row.grandTotal)}</span>
                      <em className={row.status === 'CANCELLED' || row.status === 'REFUNDED' ? 'waiting-doc' : ''}>{STATUS_TH[row.status] ?? row.status}</em>
                      <span>{PAYMENT_STATUS_TH[row.paymentStatus] ?? row.paymentStatus}</span>
                      <span>{thaiDateTime(row.placedAt)}</span>
                      <button
                        type="button"
                        onClick={() => navigate(`/admin/orders?q=${encodeURIComponent(row.orderNumber)}`)}
                      >
                        ดู
                      </button>
                    </div>
                  ))}
            </div>
          </Panel>
          <Panel title="ร้านค้ายอดนิยม (ตามยอดขาย)" action="ดูทั้งหมด" href="/admin/stores">
            {dashLoading && !data ? <p style={{ padding: 16, color: '#6881a6' }}>กำลังโหลด…</p>
              : !data?.topShops.length ? <p style={{ padding: 16, color: '#6881a6' }}>ยังไม่มีร้านมียอดขายในช่วงนี้</p>
                : (
                  <ol className="top-stores">
                    {data.topShops.map((row, index) => (
                      <li key={row.shopId}>
                        <i>{MEDALS[index] ?? String(index + 1)}</i>
                        <b>{row.shopName}</b>
                        <span>{baht(row.sales)}</span>
                        <em>{row.orderCount.toLocaleString('th-TH')} ออเดอร์</em>
                      </li>
                    ))}
                  </ol>
                )}
          </Panel>
        </section>

        <section className="admin-grid bottom">
          <Panel title={usersCardTitle} action="ดูทั้งหมด" href="/admin/users">
            {dashLoading && !data ? <p style={{ padding: 16, color: '#6881a6' }}>กำลังโหลด…</p>
              : !data?.recentUsers.length ? <p style={{ padding: 16, color: '#6881a6' }}>ยังไม่มีผู้ใช้ใหม่ในช่วงนี้</p>
                : (
                  <div className="mini-table">
                    <b>ชื่อผู้ใช้</b><b>อีเมล</b><b>ประเภท</b>
                    {data.recentUsers.flatMap((row) => [
                      <span key={`${row.emailMasked}-n`}>{row.displayName}</span>,
                      <span key={`${row.emailMasked}-e`}>{row.emailMasked}</span>,
                      <span key={`${row.emailMasked}-r`}>{row.role}</span>,
                    ])}
                  </div>
                )}
          </Panel>
          <Panel title="รายได้ค่าธรรมเนียม (5%)" action="ดูรายละเอียด" sample>
            <div className="fee-number">
              <b>{dashLoading ? '…' : baht(feeEstimate)} {data ? <Trend cur={feeEstimate} prev={prevFee} /> : null}</b>
              <small>ประมาณการจากยอดขาย · จะทำจริงใน B7</small>
              <div>
                <span style={{ height: 44 }}>{baht(feeEstimate * 0.6)}</span>
                <span style={{ height: 61 }}>{baht(feeEstimate * 0.8)}</span>
                <span style={{ height: 82 }}>{baht(feeEstimate || 1)}</span>
              </div>
            </div>
          </Panel>
          <Panel title="ประกาศจากผู้ดูแลระบบ" action="ดูทั้งหมด" sample>
            <ul className="announcements">
              {ANNOUNCEMENTS_SAMPLE.map(([date, text]) => <li key={date}><i />{date} <span>{text}</span></li>)}
            </ul>
          </Panel>
        </section>
      </div>
    </main>
  )
}
