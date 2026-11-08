import { useEffect, useMemo, useRef, useState, type CSSProperties, type ReactNode } from 'react'
import { createPortal } from 'react-dom'
import { CalendarDays, ChevronDown, ChevronLeft, ChevronRight, X } from 'lucide-react'
import { useLanguage } from '../../contexts/LanguageContext'
import {
  MONTHS_EN,
  MONTHS_TH,
  WEEKDAYS_EN,
  WEEKDAYS_TH,
  buildMonthCells,
  parseIso,
  startOfDay,
  toIso,
  type MenuMode,
} from './dateUtils'
import '../../styles/date-picker.css'
import '../../styles/date-range-picker.css'

type RangeValue = { from: string; to: string }
type PanelPos = { top: number; left: number; width: number }

type DateRangePickerProps = {
  label: ReactNode
  from?: string
  to?: string
  onChange: (value: RangeValue) => void
  min?: string
  max?: string
  className?: string
  placeholder?: string
  clearLabel?: string
  applyLabel?: string
  presetsLabel?: { d7: string; d30: string; month: string }
  /** Render the calendar in document.body to avoid overflow clipping in cards/filters. */
  portal?: boolean
}

export default function DateRangePicker({
  label,
  from = '',
  to = '',
  onChange,
  min,
  max,
  className,
  placeholder,
  clearLabel,
  applyLabel,
  presetsLabel,
  portal = false,
}: DateRangePickerProps) {
  const { locale, localeTag: tag } = useLanguage()
  const isThai = locale === 'th'
  const containerRef = useRef<HTMLDivElement>(null)
  const triggerRef = useRef<HTMLButtonElement>(null)
  const panelRef = useRef<HTMLDivElement>(null)
  const yearListRef = useRef<HTMLDivElement>(null)
  const [open, setOpen] = useState(false)
  const [menu, setMenu] = useState<MenuMode>('none')
  const [draftFrom, setDraftFrom] = useState(from)
  const [draftTo, setDraftTo] = useState(to)
  const [hover, setHover] = useState<string | null>(null)
  const [panelPos, setPanelPos] = useState<PanelPos | null>(null)
  const fromDate = parseIso(draftFrom)
  const toDate = parseIso(draftTo)
  const minDate = min ? parseIso(min) : null
  const maxDate = max ? parseIso(max) : null
  const [view, setView] = useState(() => fromDate || toDate || maxDate || new Date())

  useEffect(() => {
    if (!open) {
      setMenu('none')
      setPanelPos(null)
      return
    }
    setDraftFrom(from)
    setDraftTo(to)
    setHover(null)
    setView(parseIso(from) || parseIso(to) || maxDate || new Date())
  }, [open, from, to, maxDate])

  useEffect(() => {
    if (!open || !portal) return
    const update = () => {
      const trigger = triggerRef.current
      if (!trigger) return
      const rect = trigger.getBoundingClientRect()
      const width = Math.min(Math.max(rect.width, 320), window.innerWidth - 16)
      const estimatedHeight = 420
      const below = rect.bottom + 6
      const top = below + estimatedHeight > window.innerHeight - 8
        ? Math.max(8, rect.top - estimatedHeight - 6)
        : below
      let left = rect.right - width
      if (left < 8) left = 8
      if (left + width > window.innerWidth - 8) left = Math.max(8, window.innerWidth - width - 8)
      setPanelPos({ top, left, width })
    }
    update()
    window.addEventListener('resize', update)
    window.addEventListener('scroll', update, true)
    return () => {
      window.removeEventListener('resize', update)
      window.removeEventListener('scroll', update, true)
    }
  }, [open, portal])

  useEffect(() => {
    if (!open) return
    const onPointer = (event: MouseEvent) => {
      const target = event.target as Node
      if (containerRef.current?.contains(target)) return
      if (panelRef.current?.contains(target)) return
      setOpen(false)
    }
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        if (menu !== 'none') setMenu('none')
        else {
          setOpen(false)
          triggerRef.current?.focus()
        }
      }
    }
    document.addEventListener('mousedown', onPointer)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onPointer)
      document.removeEventListener('keydown', onKey)
    }
  }, [open, menu])

  useEffect(() => {
    if (menu !== 'year' || !yearListRef.current) return
    const active = yearListRef.current.querySelector<HTMLElement>('[data-active="true"]')
    active?.scrollIntoView({ block: 'center' })
  }, [menu, view])

  const formatDay = (value: string) => {
    const date = parseIso(value)
    if (!date) return ''
    return date.toLocaleDateString(tag, { day: 'numeric', month: 'short', year: 'numeric' })
  }

  const displayValue = useMemo(() => {
    if (!from && !to) return ''
    if (from && to) return `${formatDay(from)} – ${formatDay(to)}`
    return formatDay(from || to)
  }, [from, to, tag])

  const weekdays = isThai ? WEEKDAYS_TH : WEEKDAYS_EN
  const months = isThai ? MONTHS_TH : MONTHS_EN
  const yearLabel = (year: number) => (isThai ? year + 543 : year)
  const yearOptions = useMemo(() => {
    const end = maxDate?.getFullYear() ?? new Date().getFullYear()
    const start = minDate?.getFullYear() ?? end - 100
    const years: number[] = []
    for (let y = end; y >= start; y -= 1) years.push(y)
    return years
  }, [minDate, maxDate])

  const cells = useMemo(() => buildMonthCells(view), [view])
  const today = startOfDay(new Date())
  const defaultPlaceholder = placeholder || (isThai ? 'เลือกช่วงวันที่' : locale === 'zh' ? '选择日期范围' : 'Select date range')

  const isDisabled = (date: Date) => {
    const day = startOfDay(date).getTime()
    if (minDate && day < startOfDay(minDate).getTime()) return true
    if (maxDate && day > startOfDay(maxDate).getTime()) return true
    return false
  }

  const shiftMonth = (delta: number) => {
    setMenu('none')
    setView((current) => new Date(current.getFullYear(), current.getMonth() + delta, 1))
  }

  const pick = (date: Date) => {
    if (isDisabled(date)) return
    const iso = toIso(date)
    if (!draftFrom || (draftFrom && draftTo)) {
      setDraftFrom(iso)
      setDraftTo('')
      setHover(null)
      return
    }
    if (iso < draftFrom) {
      setDraftTo(draftFrom)
      setDraftFrom(iso)
    } else {
      setDraftTo(iso)
    }
    setHover(null)
  }

  const inRange = (iso: string) => {
    const start = draftFrom
    const end = draftTo || (draftFrom && hover && !draftTo ? hover : '')
    if (!start || !end) return false
    const a = start <= end ? start : end
    const b = start <= end ? end : start
    return iso >= a && iso <= b
  }

  const apply = () => {
    onChange({ from: draftFrom, to: draftTo })
    setOpen(false)
    triggerRef.current?.focus()
  }

  const clear = () => {
    setDraftFrom('')
    setDraftTo('')
    onChange({ from: '', to: '' })
    setOpen(false)
    triggerRef.current?.focus()
  }

  const applyPreset = (days: number | 'month') => {
    const end = startOfDay(new Date())
    let start: Date
    if (days === 'month') {
      start = new Date(end.getFullYear(), end.getMonth(), 1)
    } else {
      start = new Date(end)
      start.setDate(end.getDate() - (days - 1))
    }
    const next = { from: toIso(start), to: toIso(end) }
    setDraftFrom(next.from)
    setDraftTo(next.to)
    onChange(next)
    setOpen(false)
    triggerRef.current?.focus()
  }

  const viewMonth = view.getMonth()
  const viewYear = view.getFullYear()
  const hasValue = Boolean(from || to)
  const portalStyle: CSSProperties | undefined = portal && panelPos
    ? { position: 'fixed', top: panelPos.top, left: panelPos.left, width: panelPos.width, right: 'auto', zIndex: 1200 }
    : undefined

  const panel = open && (!portal || panelPos) ? (
    <div
      ref={panelRef}
      className="date-picker-panel date-range-panel"
      role="dialog"
      aria-label={typeof label === 'string' ? label : undefined}
      style={portalStyle}
    >
      <div className="date-picker-toolbar">
        <button type="button" className="date-picker-nav" aria-label="Previous month" onClick={() => shiftMonth(-1)}>
          <ChevronLeft />
        </button>
        <div className="date-picker-heading">
          <button
            type="button"
            className={`date-picker-chip${menu === 'month' ? ' active' : ''}`}
            aria-expanded={menu === 'month'}
            onClick={() => setMenu((current) => (current === 'month' ? 'none' : 'month'))}
          >
            <span>{months[viewMonth]}</span>
            <ChevronDown />
          </button>
          <button
            type="button"
            className={`date-picker-chip year${menu === 'year' ? ' active' : ''}`}
            aria-expanded={menu === 'year'}
            onClick={() => setMenu((current) => (current === 'year' ? 'none' : 'year'))}
          >
            <span>{yearLabel(viewYear)}</span>
            <ChevronDown />
          </button>
        </div>
        <button type="button" className="date-picker-nav" aria-label="Next month" onClick={() => shiftMonth(1)}>
          <ChevronRight />
        </button>
      </div>

      {menu === 'month' ? (
        <div className="date-picker-menu months" role="listbox" aria-label="Month">
          {months.map((month, index) => (
            <button
              key={month}
              type="button"
              role="option"
              aria-selected={index === viewMonth}
              className={index === viewMonth ? 'active' : ''}
              onClick={() => {
                setView(new Date(viewYear, index, 1))
                setMenu('none')
              }}
            >
              {month}
            </button>
          ))}
        </div>
      ) : null}

      {menu === 'year' ? (
        <div className="date-picker-menu years" role="listbox" aria-label="Year" ref={yearListRef}>
          {yearOptions.map((year) => (
            <button
              key={year}
              type="button"
              role="option"
              data-active={year === viewYear ? 'true' : undefined}
              aria-selected={year === viewYear}
              className={year === viewYear ? 'active' : ''}
              onClick={() => {
                setView(new Date(year, viewMonth, 1))
                setMenu('none')
              }}
            >
              {yearLabel(year)}
            </button>
          ))}
        </div>
      ) : null}

      {menu === 'none' ? (
        <>
          <div className="date-range-presets">
            <button type="button" onClick={() => applyPreset(7)}>{presetsLabel?.d7 || (isThai ? '7 วัน' : '7 days')}</button>
            <button type="button" onClick={() => applyPreset(30)}>{presetsLabel?.d30 || (isThai ? '30 วัน' : '30 days')}</button>
            <button type="button" onClick={() => applyPreset('month')}>{presetsLabel?.month || (isThai ? 'เดือนนี้' : 'This month')}</button>
          </div>
          <div className="date-picker-weekdays">
            {weekdays.map((day) => <span key={day}>{day}</span>)}
          </div>
          <div className="date-picker-grid">
            {cells.map(({ date, inMonth }) => {
              const iso = toIso(date)
              const disabled = isDisabled(date)
              const isStart = draftFrom === iso
              const isEnd = draftTo === iso
              const selected = isStart || isEnd
              const ranged = inRange(iso)
              const isToday = toIso(today) === iso
              return (
                <button
                  key={`${iso}-${inMonth ? 'in' : 'out'}`}
                  type="button"
                  disabled={disabled}
                  aria-selected={selected}
                  aria-label={date.toLocaleDateString(tag, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}
                  className={[
                    'date-picker-day',
                    'num',
                    inMonth ? '' : 'muted',
                    selected ? 'selected' : '',
                    ranged ? 'in-range' : '',
                    isStart ? 'range-start' : '',
                    isEnd ? 'range-end' : '',
                    isToday ? 'today' : '',
                  ].filter(Boolean).join(' ')}
                  onMouseEnter={() => {
                    if (draftFrom && !draftTo) setHover(iso)
                  }}
                  onClick={() => pick(date)}
                >
                  {date.getDate()}
                </button>
              )
            })}
          </div>
        </>
      ) : null}

      <div className="date-picker-footer">
        <button type="button" className="date-picker-clear" onClick={clear}>
          {clearLabel || (isThai ? 'ล้าง' : 'Clear')}
        </button>
        <button type="button" className="date-picker-today" disabled={!draftFrom} onClick={apply}>
          {applyLabel || (isThai ? 'ตกลง' : 'Apply')}
        </button>
      </div>
    </div>
  ) : null

  return (
    <div className={['date-picker', 'date-range-picker', className].filter(Boolean).join(' ')} ref={containerRef}>
      <span className="date-picker-label">{label}</span>
      <div className="date-range-trigger-row">
        <button
          ref={triggerRef}
          type="button"
          className={`date-picker-trigger${open ? ' open' : ''}${hasValue ? '' : ' empty'}`}
          aria-haspopup="dialog"
          aria-expanded={open}
          onClick={() => setOpen((prev) => !prev)}
        >
          <span>{displayValue || defaultPlaceholder}</span>
          <CalendarDays aria-hidden="true" />
        </button>
        {hasValue ? (
          <button
            type="button"
            className="date-range-clear-btn"
            aria-label={clearLabel || (isThai ? 'ล้าง' : 'Clear')}
            onClick={(event) => {
              event.stopPropagation()
              clear()
            }}
          >
            <X size={16} aria-hidden="true" />
          </button>
        ) : null}
      </div>
      {portal ? (panel ? createPortal(panel, document.body) : null) : panel}
    </div>
  )
}
