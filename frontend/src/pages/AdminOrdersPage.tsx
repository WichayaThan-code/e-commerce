import { useEffect, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { ExternalLink, Search, ShoppingBag } from 'lucide-react'
import AdminHeader from '../components/AdminHeader'
import AdminSidebar from '../components/AdminSidebar'
import { LoadError, SkeletonForm, SkeletonTable } from '../components/feedback/Skeleton'
import ReasonDialog from '../components/admin/ReasonDialog'
import { Drawer, Pager, Pill, errorMessage, formatCount, statusTone, usePlatformList } from '../components/admin/PlatformBits'
import { formatBaht } from '../components/cart/cartFormat'
import { useAuth } from '../contexts/AuthContext'
import { usePermissions } from '../hooks/usePermissions'
import { hasPlatformAccess } from '../services/permissions'
import { formatDateTime } from '../services/adminFormat'
import {
  adminOrderApi, authorizedBlob, moneyValue,
  type AllowedTransition, type AllowedTransitions, type OrderDetail, type OrderSummary, type OrderTimelineEntry, type PaymentView,
  type RefundSummary, type RefundView, type ShipmentDetail, type ShipmentSummary,
} from '../services/api'
import '../styles/admin-dashboard.css'
import '../styles/admin-platform.css'

type Tab = 'orders' | 'shipments'
type TP = (key: string, options?: Record<string, unknown>) => string

const ORDER_STATUSES = ['PENDING_PAYMENT', 'PAID_AWAITING_MERCHANT_CONFIRM', 'MERCHANT_CONFIRMED', 'READY_TO_PACK', 'PACKING', 'PACKED', 'DISPATCHED', 'DELIVERED', 'COMPLETED', 'CANCELLED', 'REFUND_REQUESTED', 'REFUNDED']
const PAYMENT_STATUSES = ['UNPAID', 'PENDING', 'PAID', 'FAILED', 'PARTIALLY_REFUNDED', 'REFUNDED']
const FULFILLMENT_STATUSES = ['UNFULFILLED', 'PARTIAL', 'FULFILLED', 'RETURNED']
const SHIPMENT_STATUSES = ['READY_TO_PACK', 'PACKING', 'PACKED', 'DISPATCHED', 'IN_TRANSIT', 'OUT_FOR_DELIVERY', 'DELIVERED', 'DELIVERY_FAILED', 'CANCELLED', 'RETURNED']

const dash = (value: string | number | null | undefined) => (value === null || value === undefined || value === '' ? '—' : String(value))
const lab = (tp: TP, prefix: string, code: string | null | undefined) => (code ? tp(`${prefix}.${code}`, { defaultValue: code }) : '—')

type Pending =
  | { kind: 'transition'; transition: AllowedTransition }
  | { kind: 'confirmPayment'; payment: PaymentView }
  | { kind: 'rejectPayment'; payment: PaymentView }
  | { kind: 'collectCod' }
  | { kind: 'refund'; op: 'approve' | 'reject' | 'resolve'; refund: RefundView }

/* ------------------------------------------------------------------ action dialog */

type ActionDialogProps = {
  tp: TP
  localeTag: string
  order: OrderDetail
  pending: Pending
  onClose: () => void
  onDone: (message: string) => void
}

function ActionDialog({ tp, localeTag, order, pending, onClose, onDone }: ActionDialogProps) {
  const [carrierCode, setCarrierCode] = useState('')
  const [trackingNumber, setTrackingNumber] = useState('')
  const [restock, setRestock] = useState(false)
  const [acceptMismatch, setAcceptMismatch] = useState(false)
  const [amount, setAmount] = useState(String(moneyValue(order.grandTotal)))
  const [reference, setReference] = useState('')
  const [collectedAt, setCollectedAt] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const expectedVersion = typeof order.version === 'number' ? order.version : undefined

  const needsTracking = pending.kind === 'transition' && pending.transition.trackingRequired
  const transitionTo = pending.kind === 'transition' ? pending.transition.toStatus ?? null : null
  const amountNumber = Number(amount)
  const extraValid =
    (!needsTracking || (carrierCode.trim() !== '' && trackingNumber.trim() !== '')) &&
    (pending.kind !== 'collectCod' || (Number.isFinite(amountNumber) && amountNumber > 0))

  const copy = (() => {
    switch (pending.kind) {
      case 'transition': {
        const target = transitionTo ? lab(tp, 'orderStatus', transitionTo) : pending.transition.action
        return { title: tp('orders.dialog.transitionTitle'), message: tp('orders.dialog.transitionBody', { order: order.orderNumber, status: target }), danger: transitionTo === 'CANCELLED' || transitionTo === 'REFUNDED', done: tp('orders.done.transition', { status: target }) }
      }
      case 'confirmPayment':
        return { title: tp('orders.dialog.confirmPaymentTitle'), message: tp('orders.dialog.confirmPaymentBody', { order: order.orderNumber, amount: formatBaht(pending.payment.amount, localeTag) }), danger: false, done: tp('orders.done.confirmPayment') }
      case 'rejectPayment':
        return { title: tp('orders.dialog.rejectPaymentTitle'), message: tp('orders.dialog.rejectPaymentBody', { order: order.orderNumber }), danger: true, done: tp('orders.done.rejectPayment') }
      case 'collectCod':
        return { title: tp('orders.dialog.collectCodTitle'), message: tp('orders.dialog.collectCodBody', { order: order.orderNumber }), danger: false, done: tp('orders.done.collectCod') }
      case 'refund':
        return {
          title: tp(`orders.dialog.refund.${pending.op}Title`),
          message: tp(`orders.dialog.refund.${pending.op}Body`, { order: order.orderNumber, amount: formatBaht(pending.refund.amount, localeTag) }),
          danger: pending.op === 'reject',
          done: tp(`orders.done.refund.${pending.op}`),
        }
    }
  })()

  const submit = async (reason: string) => {
    setBusy(true)
    setError('')
    try {
      switch (pending.kind) {
        case 'transition':
          await adminOrderApi.transition(order.id, {
            status: pending.transition.toStatus ?? undefined,
            reason,
            carrierCode: needsTracking ? carrierCode.trim() : undefined,
            trackingNumber: needsTracking ? trackingNumber.trim() : undefined,
            restock: transitionTo === 'REFUNDED' ? restock : undefined,
            expectedVersion,
          })
          break
        case 'confirmPayment':
          await adminOrderApi.confirmPayment(order.id, pending.payment.id, { reason, acceptAmountMismatch: acceptMismatch || undefined, expectedVersion })
          break
        case 'rejectPayment':
          await adminOrderApi.rejectPayment(order.id, pending.payment.id, { reason, expectedVersion })
          break
        case 'collectCod':
          await adminOrderApi.collectCod(order.id, {
            amount: amountNumber,
            reason,
            reference: reference.trim() || undefined,
            collectedAt: collectedAt ? new Date(collectedAt).toISOString() : undefined,
            acceptAmountMismatch: acceptMismatch || undefined,
            expectedVersion,
          })
          break
        case 'refund':
          if (pending.op === 'approve') await adminOrderApi.approveRefund(order.id, pending.refund.id, { reason, restock })
          else if (pending.op === 'reject') await adminOrderApi.rejectRefund(order.id, pending.refund.id, { reason })
          else await adminOrderApi.resolveRefund(order.id, pending.refund.id, { reason })
          break
      }
      onDone(copy.done)
    } catch (failure) {
      setError(errorMessage(failure, tp('errors.action')))
      setBusy(false)
    }
  }

  const showRestock = transitionTo === 'REFUNDED' || (pending.kind === 'refund' && pending.op === 'approve')
  const showMismatch = pending.kind === 'confirmPayment' || pending.kind === 'collectCod'

  return (
    <ReasonDialog title={copy.title} message={copy.message} danger={copy.danger} busy={busy} error={error} extraValid={extraValid} onCancel={onClose} onConfirm={reason => void submit(reason)}>
      {needsTracking && (
        <div className="ap-dialog-fields">
          <label><span>{tp('orders.carrierCode')}</span><input value={carrierCode} onChange={event => setCarrierCode(event.target.value)} maxLength={50} required /></label>
          <label><span>{tp('orders.trackingNumber')}</span><input value={trackingNumber} onChange={event => setTrackingNumber(event.target.value)} maxLength={100} className="num" required /></label>
        </div>
      )}
      {pending.kind === 'collectCod' && (
        <div className="ap-dialog-fields">
          <label><span>{tp('orders.codAmount')}</span><input type="number" min="0" step="0.01" value={amount} onChange={event => setAmount(event.target.value)} className="num" required /></label>
          <label><span>{tp('orders.codReference')}</span><input value={reference} onChange={event => setReference(event.target.value)} maxLength={100} className="num" /></label>
          <label><span>{tp('orders.codCollectedAt')}</span><input type="datetime-local" value={collectedAt} onChange={event => setCollectedAt(event.target.value)} /></label>
        </div>
      )}
      {showRestock && <label className="ap-check"><input type="checkbox" checked={restock} onChange={event => setRestock(event.target.checked)} />{tp('orders.restock')}</label>}
      {showMismatch && <label className="ap-check"><input type="checkbox" checked={acceptMismatch} onChange={event => setAcceptMismatch(event.target.checked)} />{tp('orders.acceptMismatch')}</label>}
    </ReasonDialog>
  )
}

/* ------------------------------------------------------------------ order drawer */

type Section<T> = { data: T | null; error: string }
const empty = <T,>(): Section<T> => ({ data: null, error: '' })

function OrderDrawer({ orderId, tp, localeTag, onClose, onChanged }: { orderId: string; tp: TP; localeTag: string; onClose: () => void; onChanged: () => void }) {
  const { can, deniedTitle } = usePermissions()
  const canAct = can('PLATFORM_SHOP_SUSPEND')
  const [reloadKey, setReloadKey] = useState(0)
  const [loading, setLoading] = useState(true)
  const [detail, setDetail] = useState<Section<OrderDetail>>(empty)
  const [timeline, setTimeline] = useState<Section<OrderTimelineEntry[]>>(empty)
  const [transitions, setTransitions] = useState<Section<AllowedTransitions>>(empty)
  const [payments, setPayments] = useState<Section<PaymentView[]>>(empty)
  const [refunds, setRefunds] = useState<Section<RefundSummary>>(empty)
  const [pending, setPending] = useState<Pending | null>(null)
  const [fileBusy, setFileBusy] = useState('')
  const [fileError, setFileError] = useState('')
  const [notice, setNotice] = useState('')

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    const section = <T,>(result: PromiseSettledResult<T>): Section<T> =>
      result.status === 'fulfilled' ? { data: result.value, error: '' } : { data: null, error: errorMessage(result.reason, tp('errors.detail')) }
    Promise.allSettled([
      adminOrderApi.get(orderId), adminOrderApi.timeline(orderId), adminOrderApi.transitions(orderId),
      adminOrderApi.payments(orderId), adminOrderApi.refunds(orderId),
    ]).then(([d, tl, tr, pay, ref]) => {
      if (cancelled) return
      setDetail(section(d)); setTimeline(section(tl)); setTransitions(section(tr)); setPayments(section(pay)); setRefunds(section(ref))
      setLoading(false)
    })
    return () => { cancelled = true }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [orderId, reloadKey])

  const order = detail.data
  const openFile = async (path: string, key: string) => {
    const popup = window.open('', '_blank')
    setFileBusy(key)
    setFileError('')
    try {
      const blob = await authorizedBlob(path)
      const url = URL.createObjectURL(blob)
      if (popup) popup.location.href = url
      else window.open(url, '_blank')
      window.setTimeout(() => URL.revokeObjectURL(url), 120000)
    } catch (failure) {
      popup?.close()
      setFileError(errorMessage(failure, tp('errors.file')))
    } finally {
      setFileBusy('')
    }
  }
  const done = (message: string) => {
    setPending(null)
    setNotice(message)
    setReloadKey(key => key + 1)
    onChanged()
  }
  const denied = deniedTitle(canAct)
  const canCollectCod = !!order && order.paymentMethod === 'COD' && ['UNPAID', 'PENDING'].includes(order.paymentStatus) && !['CANCELLED', 'REFUNDED'].includes(order.status)

  return (
    <Drawer title={tp('orders.detailTitle')} subtitle={order ? <span className="num">{order.orderNumber}</span> : undefined} onClose={onClose} wide>
      {loading && !order && <SkeletonForm fields={6} />}
      {detail.error && <LoadError message={detail.error} onRetry={() => setReloadKey(key => key + 1)} />}
      {notice && <p className="ap-note" role="status">{notice}</p>}
      {order && <>
        <div className="ap-drawer-status">
          <Pill tone={statusTone(order.status)}>{lab(tp, 'orderStatus', order.status)}</Pill>
          <Pill tone={statusTone(order.paymentStatus)}>{lab(tp, 'paymentStatus', order.paymentStatus)}</Pill>
          {order.fulfillmentStatus && <Pill tone={statusTone(order.fulfillmentStatus)}>{lab(tp, 'fulfillment', order.fulfillmentStatus)}</Pill>}
        </div>

        <section className="ap-section">
          <h3>{tp('orders.summary')}</h3>
          <dl className="ap-fields">
            <div><dt>{tp('orders.col.shop')}</dt><dd>{order.shopName} <small className="num">#{order.shopId}</small></dd></div>
            <div><dt>{tp('orders.col.paymentMethod')}</dt><dd>{lab(tp, 'paymentMethod', order.paymentMethod ?? 'BANK_TRANSFER')}</dd></div>
            <div><dt>{tp('orders.col.placedAt')}</dt><dd className="num">{formatDateTime(order.placedAt)}</dd></div>
            <div><dt>{tp('orders.paidAt')}</dt><dd className="num">{formatDateTime(order.paidAt)}</dd></div>
            <div><dt>{tp('orders.subtotal')}</dt><dd className="num">{formatBaht(order.subtotal, localeTag)}</dd></div>
            <div><dt>{tp('orders.discount')}</dt><dd className="num">{formatBaht(order.discountTotal, localeTag)}</dd></div>
            <div><dt>{tp('orders.shippingFee')}</dt><dd className="num">{formatBaht(order.shippingFee, localeTag)}</dd></div>
            <div><dt>{tp('orders.col.grandTotal')}</dt><dd className="num"><b>{formatBaht(order.grandTotal, localeTag)}</b></dd></div>
            <div><dt>{tp('orders.paidAmount')}</dt><dd className="num">{formatBaht(order.paidAmount, localeTag)}</dd></div>
            <div><dt>{tp('orders.refundedAmount')}</dt><dd className="num">{formatBaht(order.refundedAmount, localeTag)}</dd></div>
            <div className="full"><dt>{tp('orders.shipTo')}</dt><dd>{order.shippingAddress ? [order.shippingAddress.recipientName, order.shippingAddress.district, order.shippingAddress.province, order.shippingAddress.postalCode].filter(Boolean).join(' · ') || '—' : '—'}</dd></div>
            {order.cancelReason && <div className="full"><dt>{tp('orders.cancelReason')}</dt><dd>{order.cancelReason}</dd></div>}
          </dl>
        </section>

        <section className="ap-section">
          <h3>{tp('orders.items')}</h3>
          {order.itemsHidden ? <p className="ap-muted">{tp('orders.itemsHidden')}</p>
            : (order.items ?? []).length === 0 ? <p className="ap-muted">{tp('orders.noItems')}</p> : (
              <div className="ap-table-scroll"><table className="ap-table compact">
                <thead><tr><th>{tp('orders.item')}</th><th>{tp('orders.unitPrice')}</th><th>{tp('orders.qty')}</th><th>{tp('orders.lineTotal')}</th></tr></thead>
                <tbody>{(order.items ?? []).map(item => (
                  <tr key={item.id}><td><b>{item.productName}</b>{item.optionLabel && <small>{item.optionLabel}</small>}</td>
                    <td className="num">{formatBaht(item.unitPrice, localeTag)}</td><td className="num">{item.quantity}</td><td className="num">{formatBaht(item.lineTotal, localeTag)}</td></tr>
                ))}</tbody>
              </table></div>
            )}
        </section>

        <section className="ap-section">
          <h3>{tp('orders.actions')}</h3>
          {transitions.error ? <p className="ap-error" role="alert">{transitions.error}</p>
            : (transitions.data?.transitions ?? []).length === 0 ? <p className="ap-muted">{tp('orders.noTransitions')}</p> : (
              <div className="ap-action-row">
                {transitions.data?.transitions.map(transition => (
                  <button key={`${transition.action}-${transition.toStatus ?? ''}`} type="button" className="ap-ghost" disabled={!canAct} title={denied}
                    onClick={() => setPending({ kind: 'transition', transition })}>
                    {transition.toStatus ? tp('orders.transitionTo', { status: lab(tp, 'orderStatus', transition.toStatus) }) : transition.action}
                  </button>
                ))}
              </div>
            )}
          {canCollectCod && <div className="ap-action-row"><button type="button" className="ap-ghost" disabled={!canAct} title={denied} onClick={() => setPending({ kind: 'collectCod' })}>{tp('orders.collectCod')}</button></div>}
        </section>

        <section className="ap-section">
          <h3>{tp('orders.payments')}</h3>
          {fileError && <p className="ap-error" role="alert">{fileError}</p>}
          {payments.error ? <p className="ap-error" role="alert">{payments.error}</p>
            : (payments.data ?? []).length === 0 ? <p className="ap-muted">{tp('orders.noPayments')}</p> : (
              <ul className="ap-list stack">
                {(payments.data ?? []).map(payment => {
                  const reviewable = payment.status === 'PENDING'
                  const hasReviewProof = (payment.proofs ?? []).some(proof => proof.status === 'PENDING_REVIEW')
                  return (
                    <li key={payment.id}>
                      <div className="ap-row-head">
                        <span><b>{lab(tp, 'paymentMethod', payment.provider)}</b><small className="num">{tp('orders.attempt', { no: payment.attemptNo })} · {formatBaht(payment.amount, localeTag)}</small></span>
                        <Pill tone={statusTone(payment.status)}>{lab(tp, 'paymentAttempt', payment.status)}</Pill>
                      </div>
                      {(payment.proofs ?? []).map(proof => (
                        <div key={proof.id} className="ap-proof">
                          <span>
                            <Pill tone={statusTone(proof.status)}>{lab(tp, 'proofStatus', proof.status)}</Pill>
                            {proof.proofType && <small>{proof.proofType}</small>}
                            {proof.referenceNo && <small className="num">{tp('orders.reference')}: {proof.referenceNo}</small>}
                            {proof.amountDeclared != null && <small className="num">{tp('orders.declared')}: {formatBaht(proof.amountDeclared, localeTag)}{proof.amountMismatch ? ` · ${tp('orders.mismatch')}` : ''}</small>}
                            {proof.rejectReason && <small>{tp('orders.rejectReason')}: {proof.rejectReason}</small>}
                          </span>
                          <button type="button" className="ap-link" disabled={fileBusy === proof.id} onClick={() => void openFile(adminOrderApi.proofFilePath(order.id, proof.id), proof.id)}>
                            <ExternalLink />{tp('orders.openSlip')}
                          </button>
                        </div>
                      ))}
                      {reviewable && (
                        <div className="ap-action-row">
                          {hasReviewProof && <button type="button" className="ap-primary" disabled={!canAct} title={denied} onClick={() => setPending({ kind: 'confirmPayment', payment })}>{tp('orders.confirmPayment')}</button>}
                          <button type="button" className="ap-danger" disabled={!canAct} title={denied} onClick={() => setPending({ kind: 'rejectPayment', payment })}>{tp('orders.rejectPayment')}</button>
                        </div>
                      )}
                    </li>
                  )
                })}
              </ul>
            )}
        </section>

        <section className="ap-section">
          <h3>{tp('orders.refunds')}</h3>
          {refunds.error ? <p className="ap-error" role="alert">{refunds.error}</p> : refunds.data && <>
            <p className="ap-muted num">{tp('orders.refundSummary', { paid: formatBaht(refunds.data.paidAmount, localeTag), refunded: formatBaht(refunds.data.refundedAmount, localeTag), refundable: formatBaht(refunds.data.refundableAmount, localeTag) })}</p>
            {(refunds.data.refunds ?? []).length === 0 ? <p className="ap-muted">{tp('orders.noRefunds')}</p> : (
              <ul className="ap-list stack">
                {refunds.data.refunds.map(refund => {
                  const open = refund.status === 'REQUESTED'
                  const resolvable = refund.status === 'DISPUTED' || refund.status === 'PROOF_UPLOADED'
                  return (
                    <li key={refund.id}>
                      <div className="ap-row-head">
                        <span><b className="num">{formatBaht(refund.amount, localeTag)}</b><small>{dash(refund.reason)}</small></span>
                        <Pill tone={statusTone(refund.status)}>{lab(tp, 'refundStatus', refund.status)}</Pill>
                      </div>
                      <small className="num">{tp('orders.requestedAt')}: {formatDateTime(refund.requestedAt)}{refund.requesterType ? ` · ${refund.requesterType}` : ''}</small>
                      {refund.rejectReason && <small>{tp('orders.rejectReason')}: {refund.rejectReason}</small>}
                      {refund.buyerAckNote && <small>{tp('orders.buyerNote')}: {refund.buyerAckNote}</small>}
                      {(refund.proofFileUrl || refund.proofUploadedAt) && (
                        <button type="button" className="ap-link" disabled={fileBusy === refund.id} onClick={() => void openFile(adminOrderApi.refundProofPath(order.id, refund.id), refund.id)}>
                          <ExternalLink />{tp('orders.openRefundProof')}
                        </button>
                      )}
                      {(open || resolvable) && (
                        <div className="ap-action-row">
                          {open && <button type="button" className="ap-primary" disabled={!canAct} title={denied} onClick={() => setPending({ kind: 'refund', op: 'approve', refund })}>{tp('orders.refundApprove')}</button>}
                          {open && <button type="button" className="ap-danger" disabled={!canAct} title={denied} onClick={() => setPending({ kind: 'refund', op: 'reject', refund })}>{tp('orders.refundReject')}</button>}
                          {resolvable && <button type="button" className="ap-ghost" disabled={!canAct} title={denied} onClick={() => setPending({ kind: 'refund', op: 'resolve', refund })}>{tp('orders.refundResolve')}</button>}
                        </div>
                      )}
                    </li>
                  )
                })}
              </ul>
            )}
          </>}
        </section>

        <section className="ap-section">
          <h3>{tp('orders.shipment')}</h3>
          {order.shipment ? (
            <dl className="ap-fields">
              <div><dt>{tp('common.status')}</dt><dd>{lab(tp, 'shipmentStatus', order.shipment.status)}</dd></div>
              <div><dt>{tp('orders.carrierCode')}</dt><dd className="num">{dash(order.shipment.carrierCode)}</dd></div>
              <div><dt>{tp('orders.trackingNumber')}</dt><dd className="num">{dash(order.shipment.trackingNumber)}</dd></div>
              <div><dt>{tp('orders.dispatchedAt')}</dt><dd className="num">{formatDateTime(order.shipment.dispatchedAt)}</dd></div>
              <div><dt>{tp('orders.deliveredAt')}</dt><dd className="num">{formatDateTime(order.shipment.deliveredAt)}</dd></div>
            </dl>
          ) : <p className="ap-muted">{tp('orders.noShipment')}</p>}
        </section>

        <section className="ap-section">
          <h3>{tp('orders.timeline')}</h3>
          {timeline.error ? <p className="ap-error" role="alert">{timeline.error}</p>
            : (timeline.data ?? []).length === 0 ? <p className="ap-muted">{tp('orders.noTimeline')}</p> : (
              <ol className="ap-timeline">
                {(timeline.data ?? []).map(entry => (
                  <li key={entry.id}>
                    <b>{entry.event}{entry.toStatus ? ` → ${lab(tp, 'orderStatus', entry.toStatus)}` : ''}</b>
                    <small className="num">{formatDateTime(entry.at)}{entry.actorType ? ` · ${entry.actorType}` : ''}</small>
                    {entry.reason && <small>{entry.reason}</small>}
                  </li>
                ))}
              </ol>
            )}
        </section>
      </>}
      {pending && order && <ActionDialog tp={tp} localeTag={localeTag} order={order} pending={pending} onClose={() => setPending(null)} onDone={done} />}
    </Drawer>
  )
}

/* ------------------------------------------------------------------ orders tab */

type OrderFilters = { shopId: string; status: string; paymentStatus: string; fulfillmentStatus: string; from: string; to: string; q: string }
const EMPTY_ORDER_FILTERS: OrderFilters = { shopId: '', status: '', paymentStatus: '', fulfillmentStatus: '', from: '', to: '', q: '' }

function appliedOrderFilters(filters: OrderFilters) {
  const shop = Number(filters.shopId)
  return {
    shopId: filters.shopId.trim() && Number.isInteger(shop) && shop > 0 ? shop : undefined,
    status: filters.status, paymentStatus: filters.paymentStatus, fulfillmentStatus: filters.fulfillmentStatus,
    from: filters.from, to: filters.to, q: filters.q.trim(),
    sort: 'placedAt', direction: 'desc',
  }
}

function OrdersTab({ tp, localeTag, notify }: { tp: TP; localeTag: string; notify: (message: string) => void }) {
  const [draft, setDraft] = useState(EMPTY_ORDER_FILTERS)
  const [filters, setFilters] = useState(EMPTY_ORDER_FILTERS)
  const list = usePlatformList<OrderSummary>(params => adminOrderApi.list(params), appliedOrderFilters(filters), { errorText: tp('errors.load') })
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const items = list.result?.items ?? []
  const set = (key: keyof OrderFilters, value: string) => setDraft(current => ({ ...current, [key]: value }))
  const submit = (event: FormEvent) => { event.preventDefault(); setFilters(draft); list.setPage(0) }
  const reset = () => { setDraft(EMPTY_ORDER_FILTERS); setFilters(EMPTY_ORDER_FILTERS); list.setPage(0) }
  const hasFilter = Object.values(filters).some(Boolean)

  return <>
    <form className="ap-filters" onSubmit={submit}>
      <label className="ap-search wide"><Search /><input value={draft.q} onChange={event => set('q', event.target.value)} placeholder={tp('orders.filters.q')} aria-label={tp('orders.filters.q')} maxLength={100} /></label>
      <label><span>{tp('orders.filters.shopId')}</span><input value={draft.shopId} inputMode="numeric" pattern="[0-9]*" className="num" onChange={event => set('shopId', event.target.value.replace(/\D/g, ''))} /></label>
      <label><span>{tp('orders.filters.status')}</span><select value={draft.status} onChange={event => set('status', event.target.value)}><option value="">{tp('common.all')}</option>{ORDER_STATUSES.map(value => <option key={value} value={value}>{lab(tp, 'orderStatus', value)}</option>)}</select></label>
      <label><span>{tp('orders.filters.paymentStatus')}</span><select value={draft.paymentStatus} onChange={event => set('paymentStatus', event.target.value)}><option value="">{tp('common.all')}</option>{PAYMENT_STATUSES.map(value => <option key={value} value={value}>{lab(tp, 'paymentStatus', value)}</option>)}</select></label>
      <label><span>{tp('orders.filters.fulfillmentStatus')}</span><select value={draft.fulfillmentStatus} onChange={event => set('fulfillmentStatus', event.target.value)}><option value="">{tp('common.all')}</option>{FULFILLMENT_STATUSES.map(value => <option key={value} value={value}>{lab(tp, 'fulfillment', value)}</option>)}</select></label>
      <label><span>{tp('common.from')}</span><input type="date" value={draft.from} max={draft.to || undefined} onChange={event => set('from', event.target.value)} /></label>
      <label><span>{tp('common.to')}</span><input type="date" value={draft.to} min={draft.from || undefined} onChange={event => set('to', event.target.value)} /></label>
      <div className="ap-filter-actions"><button type="submit" className="ap-primary">{tp('common.search')}</button><button type="button" className="ap-ghost" onClick={reset}>{tp('common.reset')}</button></div>
    </form>

    <section className="ap-card">
      <header className="ap-card-head"><h2>{tp('orders.listTitle')}</h2><span className="ap-kpi-inline" aria-live="polite">{tp('common.totalFromServer')}: <b className="num">{list.result ? formatCount(list.result.totalItems) : '—'}</b></span></header>
      <div className="ap-table-scroll">
        <table className="ap-table">
          <thead><tr><th>{tp('orders.col.orderNumber')}</th><th>{tp('orders.col.shop')}</th><th>{tp('common.status')}</th><th>{tp('orders.col.paymentStatus')}</th><th>{tp('orders.col.paymentMethod')}</th><th className="right">{tp('orders.col.grandTotal')}</th><th>{tp('orders.col.placedAt')}</th></tr></thead>
          <tbody>
            {items.map(row => (
              <tr key={row.id} className={`clickable${selectedId === row.id ? ' selected' : ''}`} tabIndex={0}
                onClick={() => setSelectedId(row.id)} onKeyDown={event => { if (event.key === 'Enter') setSelectedId(row.id) }}>
                <td className="num"><b>{row.orderNumber}</b></td>
                <td>{row.shopName}<small className="num">#{row.shopId}</small></td>
                <td><Pill tone={statusTone(row.status)}>{lab(tp, 'orderStatus', row.status)}</Pill></td>
                <td><Pill tone={statusTone(row.paymentStatus)}>{lab(tp, 'paymentStatus', row.paymentStatus)}</Pill></td>
                <td>{lab(tp, 'paymentMethod', row.paymentMethod ?? 'BANK_TRANSFER')}</td>
                <td className="num right">{formatBaht(row.grandTotal, localeTag)}</td>
                <td className="num nowrap">{formatDateTime(row.placedAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {list.loading && items.length === 0 && <SkeletonTable rows={8} cols={7} />}
        {!list.loading && list.error && <LoadError message={list.error} onRetry={list.reload} />}
        {!list.loading && !list.error && items.length === 0 && <p className="ap-empty">{hasFilter ? tp('orders.emptyFiltered') : tp('orders.empty')}</p>}
      </div>
      <Pager page={list.page} size={list.size} totalItems={list.result?.totalItems ?? 0} totalPages={list.result?.totalPages ?? 0} itemCount={items.length}
        loading={list.loading} onPage={list.setPage} onSize={value => { list.setSize(value); list.setPage(0) }} />
    </section>

    {selectedId && <OrderDrawer key={selectedId} orderId={selectedId} tp={tp} localeTag={localeTag} onClose={() => setSelectedId(null)} onChanged={() => { list.reload(); notify(tp('orders.updated')) }} />}
  </>
}

/* ------------------------------------------------------------------ shipments tab */

type ShipmentRow = ShipmentSummary & { shopName?: string | null; shopId?: number | null }
type ShipmentFilters = { shopId: string; status: string; q: string }
const EMPTY_SHIPMENT_FILTERS: ShipmentFilters = { shopId: '', status: '', q: '' }

function ShipmentDrawer({ shipmentId, tp, onClose }: { shipmentId: string; tp: TP; onClose: () => void }) {
  const [shipment, setShipment] = useState<ShipmentDetail | null>(null)
  const [error, setError] = useState('')
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let cancelled = false
    setError('')
    adminOrderApi.shipment(shipmentId)
      .then(value => { if (!cancelled) setShipment(value) })
      .catch(failure => { if (!cancelled) setError(errorMessage(failure, tp('errors.detail'))) })
    return () => { cancelled = true }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [shipmentId, reloadKey])

  return (
    <Drawer title={tp('orders.shipmentDetailTitle')} subtitle={shipment ? <span className="num">{shipment.orderNumber}</span> : undefined} onClose={onClose}>
      {error && <LoadError message={error} onRetry={() => setReloadKey(key => key + 1)} />}
      {!shipment && !error && <SkeletonForm fields={5} />}
      {shipment && <>
        <div className="ap-drawer-status"><Pill tone={statusTone(shipment.status)}>{lab(tp, 'shipmentStatus', shipment.status)}</Pill><Pill tone={statusTone(shipment.orderStatus)}>{lab(tp, 'orderStatus', shipment.orderStatus)}</Pill></div>
        <section className="ap-section">
          <dl className="ap-fields">
            <div><dt>{tp('orders.carrierCode')}</dt><dd className="num">{dash(shipment.carrierName ?? shipment.carrierCode)}</dd></div>
            <div><dt>{tp('orders.trackingNumber')}</dt><dd className="num">{dash(shipment.trackingNumber)}</dd></div>
            <div><dt>{tp('orders.shippedAt')}</dt><dd className="num">{formatDateTime(shipment.shippedAt)}</dd></div>
            <div><dt>{tp('orders.deliveredAt')}</dt><dd className="num">{formatDateTime(shipment.deliveredAt)}</dd></div>
            {shipment.failureReason && <div className="full"><dt>{tp('orders.failureReason')}</dt><dd>{shipment.failureReason}</dd></div>}
          </dl>
        </section>
        <section className="ap-section">
          <h3>{tp('orders.trackingEvents')}</h3>
          {(shipment.events ?? []).length === 0 ? <p className="ap-muted">{tp('orders.noTimeline')}</p> : (
            <ol className="ap-timeline">
              {(shipment.events ?? []).map(event => (
                <li key={event.id}>
                  <b>{lab(tp, 'shipmentStatus', event.status ?? event.eventType)}</b>
                  <small className="num">{formatDateTime(event.eventAt)}{event.location ? ` · ${event.location}` : ''}</small>
                  {event.note && <small>{event.note}</small>}
                </li>
              ))}
            </ol>
          )}
        </section>
      </>}
    </Drawer>
  )
}

function ShipmentsTab({ tp }: { tp: TP }) {
  const [draft, setDraft] = useState(EMPTY_SHIPMENT_FILTERS)
  const [filters, setFilters] = useState(EMPTY_SHIPMENT_FILTERS)
  const shop = Number(filters.shopId)
  const applied = { shopId: filters.shopId.trim() && Number.isInteger(shop) && shop > 0 ? shop : undefined, status: filters.status, q: filters.q.trim() }
  const list = usePlatformList<ShipmentRow>(params => adminOrderApi.shipments(params), applied, { errorText: tp('errors.load') })
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const items = list.result?.items ?? []
  const submit = (event: FormEvent) => { event.preventDefault(); setFilters(draft); list.setPage(0) }
  const reset = () => { setDraft(EMPTY_SHIPMENT_FILTERS); setFilters(EMPTY_SHIPMENT_FILTERS); list.setPage(0) }

  return <>
    <form className="ap-filters" onSubmit={submit}>
      <label className="ap-search wide"><Search /><input value={draft.q} onChange={event => setDraft({ ...draft, q: event.target.value })} placeholder={tp('orders.filters.shipmentQ')} aria-label={tp('orders.filters.shipmentQ')} maxLength={100} /></label>
      <label><span>{tp('orders.filters.shopId')}</span><input value={draft.shopId} inputMode="numeric" pattern="[0-9]*" className="num" onChange={event => setDraft({ ...draft, shopId: event.target.value.replace(/\D/g, '') })} /></label>
      <label><span>{tp('common.status')}</span><select value={draft.status} onChange={event => setDraft({ ...draft, status: event.target.value })}><option value="">{tp('common.all')}</option>{SHIPMENT_STATUSES.map(value => <option key={value} value={value}>{lab(tp, 'shipmentStatus', value)}</option>)}</select></label>
      <div className="ap-filter-actions"><button type="submit" className="ap-primary">{tp('common.search')}</button><button type="button" className="ap-ghost" onClick={reset}>{tp('common.reset')}</button></div>
    </form>

    <section className="ap-card">
      <header className="ap-card-head"><h2>{tp('orders.shipmentsTitle')}</h2><span className="ap-kpi-inline">{tp('common.totalFromServer')}: <b className="num">{list.result ? formatCount(list.result.totalItems) : '—'}</b></span></header>
      <div className="ap-table-scroll">
        <table className="ap-table">
          <thead><tr><th>{tp('orders.col.orderNumber')}</th><th>{tp('orders.col.shop')}</th><th>{tp('common.status')}</th><th>{tp('orders.carrierCode')}</th><th>{tp('orders.trackingNumber')}</th><th>{tp('orders.shippedAt')}</th><th>{tp('orders.deliveredAt')}</th></tr></thead>
          <tbody>
            {items.map(row => (
              <tr key={row.id} className={`clickable${selectedId === row.id ? ' selected' : ''}`} tabIndex={0}
                onClick={() => setSelectedId(row.id)} onKeyDown={event => { if (event.key === 'Enter') setSelectedId(row.id) }}>
                <td className="num"><b>{row.orderNumber}</b></td>
                <td>{dash(row.shopName)}{row.shopId != null && <small className="num">#{row.shopId}</small>}</td>
                <td><Pill tone={statusTone(row.status)}>{lab(tp, 'shipmentStatus', row.status)}</Pill></td>
                <td className="num">{dash(row.carrierCode)}</td>
                <td className="num">{dash(row.trackingNumber)}</td>
                <td className="num nowrap">{formatDateTime(row.shippedAt)}</td>
                <td className="num nowrap">{formatDateTime(row.deliveredAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {list.loading && items.length === 0 && <SkeletonTable rows={8} cols={7} />}
        {!list.loading && list.error && <LoadError message={list.error} onRetry={list.reload} />}
        {!list.loading && !list.error && items.length === 0 && <p className="ap-empty">{tp('orders.shipmentsEmpty')}</p>}
      </div>
      <Pager page={list.page} size={list.size} totalItems={list.result?.totalItems ?? 0} totalPages={list.result?.totalPages ?? 0} itemCount={items.length}
        loading={list.loading} onPage={list.setPage} onSize={value => { list.setSize(value); list.setPage(0) }} />
    </section>

    {selectedId && <ShipmentDrawer key={selectedId} shipmentId={selectedId} tp={tp} onClose={() => setSelectedId(null)} />}
  </>
}

/* ------------------------------------------------------------------ page */

export default function AdminOrdersPage() {
  const { t, i18n } = useTranslation('admin')
  const tp: TP = (key, options) => t(`adminPlatform.${key}`, options)
  const { user, loading } = useAuth()
  const { can, deniedTitle } = usePermissions()
  const canRead = can('PLATFORM_SHOP_READ')
  const [tab, setTab] = useState<Tab>('orders')
  const [notice, setNotice] = useState('')

  useEffect(() => {
    if (!notice) return
    const timer = window.setTimeout(() => setNotice(''), 3200)
    return () => window.clearTimeout(timer)
  }, [notice])

  if (loading) return <main className="auth-loading">{tp('common.checkingAccess')}</main>
  if (!user) { window.location.replace('/login'); return null }
  if (!hasPlatformAccess(user)) { window.location.replace('/dashboard'); return null }

  return (
    <main className="admin-shell platform-shell">
      <AdminHeader user={user} />
      <AdminSidebar active="orders" className="platform-sidebar" />
      <div className="admin-content platform-content">
        <section className="ap-heading">
          <span className="ap-heading-icon"><ShoppingBag /></span>
          <div><h1>{tp('orders.title')}</h1><p>{tp('orders.subtitle')}</p></div>
        </section>
        {!canRead ? <div className="ap-card"><p className="ap-empty" title={deniedTitle(false)}>{tp('errors.noPermission')}</p></div> : <>
          <nav className="ap-tabs" role="tablist" aria-label={tp('orders.title')}>
            <button type="button" role="tab" aria-selected={tab === 'orders'} className={tab === 'orders' ? 'active' : ''} onClick={() => setTab('orders')}>{tp('orders.tabOrders')}</button>
            <button type="button" role="tab" aria-selected={tab === 'shipments'} className={tab === 'shipments' ? 'active' : ''} onClick={() => setTab('shipments')}>{tp('orders.tabShipments')}</button>
          </nav>
          {tab === 'orders' ? <OrdersTab tp={tp} localeTag={i18n.language} notify={setNotice} /> : <ShipmentsTab tp={tp} />}
        </>}
      </div>
      {notice && <div className="ap-toast" role="status">{notice}</div>}
    </main>
  )
}
